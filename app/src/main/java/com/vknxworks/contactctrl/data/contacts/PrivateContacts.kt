package com.vknxworks.contactctrl.data.contacts

import android.content.Context
import com.vknxworks.contactctrl.data.crypto.DataKeyStore
import com.vknxworks.contactctrl.data.crypto.SealedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class PrivateField(val kind: String, val value: String, val type: Int = 0, val label: String? = null)

/** A private contact: kept only inside ContactCtrl, encrypted, never in Android's shared contacts storage. */
@Serializable
data class PrivateContact(
    val id: String,
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val company: String = "",
    val title: String = "",
    val note: String = "",
    val fields: List<PrivateField> = emptyList(),
    val starred: Boolean = false,
    val changedAt: Long = 0L,
) {
    val name: NameParts get() = NameParts(prefix, given, middle, family, suffix)

    /** The name to show: the full name, else the company, a number or an email. */
    val displayName: String
        get() = name.display.ifBlank { company.ifBlank { fields.firstOrNull { it.kind == Kind.PHONE.name || it.kind == Kind.EMAIL.name }?.value.orEmpty() } }

    fun toDraft(): ContactDraft {
        fun of(k: Kind) = fields.filter { it.kind == k.name }.map { Field(k, it.value, it.type, it.label) }
        return ContactDraft(
            name = name, nickname = nickname, company = company, title = title, note = note,
            phones = of(Kind.PHONE), emails = of(Kind.EMAIL), addresses = of(Kind.ADDRESS), websites = of(Kind.WEBSITE),
            events = of(Kind.EVENT), relations = of(Kind.RELATION), ims = of(Kind.IM),
        )
    }

    /** Same shape as a phone contact's details, for the screens that show one (read-only, no accounts). */
    fun toDetail(): ContactDetail {
        val d = toDraft()
        return ContactDetail(
            id = -1, lookupKey = id, displayName = displayName, name = name, nameDataId = null, nameRawId = null,
            nickname = nickname, nicknameDataId = null, nicknameRawId = null, company = company, title = title,
            orgDataId = null, orgRawId = null, note = note, noteDataId = null, noteRawId = null,
            phones = d.phones, emails = d.emails, addresses = d.addresses, websites = d.websites, events = d.events,
            relations = d.relations, ims = d.ims, photoUri = null, starred = starred, ringtone = null, raws = emptyList(),
        )
    }

    fun summary(): ContactSummary = ContactSummary(
        id = id.hashCode().toLong(), lookupKey = id, name = displayName,
        altName = listOf(family, listOf(prefix, given, middle).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", ").ifBlank { displayName },
        sortKey = displayName, altSortKey = family.ifBlank { displayName }, photoUri = null, starred = starred,
        phones = fields.filter { it.kind == Kind.PHONE.name }.map { it.value },
        emails = fields.filter { it.kind == Kind.EMAIL.name }.map { it.value },
    )

    companion object {
        fun from(draft: ContactDraft, id: String = UUID.randomUUID().toString(), starred: Boolean = false): PrivateContact {
            val all = draft.phones + draft.emails + draft.addresses + draft.websites + draft.events + draft.relations + draft.ims
            return PrivateContact(
                id = id,
                prefix = draft.name.prefix.trim(), given = draft.name.given.trim(), middle = draft.name.middle.trim(),
                family = draft.name.family.trim(), suffix = draft.name.suffix.trim(),
                nickname = draft.nickname.trim(), company = draft.company.trim(), title = draft.title.trim(), note = draft.note.trim(),
                fields = all.filter { it.value.isNotBlank() }.map { PrivateField(it.kind.name, it.value.trim(), it.type, it.label) },
                starred = starred,
                changedAt = System.currentTimeMillis(),
            )
        }
    }
}

@Serializable
private data class PrivateFileV1(val version: Int = 1, val contacts: List<PrivateContact> = emptyList())

/**
 * The private contacts, in one file in ContactCtrl's private folder, always encrypted with the app's data key
 * ([DataKeyStore] + [SealedFile], AES-256-GCM, key wrapped by the Android Keystore). Other apps can't read the
 * folder, and Android's contacts storage never sees these contacts.
 *
 * Safety rules: nothing is ever written unencrypted (no key, no save); a file that exists but can't be opened
 * (the Keystore key is gone, the file is damaged) is never overwritten or deleted, and the store stays
 * read-only until that is sorted out.
 */
class PrivateContactsStore(context: Context, private val scope: CoroutineScope, private val keys: DataKeyStore) {
    sealed interface State {
        data object Loading : State
        data class Ready(val contacts: List<PrivateContact>) : State
        /** The file is there but can't be opened; nothing is changed. */
        data object Unreadable : State
    }

    private val file = File(context.filesDir, FILE)
    private val tmp = File(context.filesDir, "$FILE.tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    fun load() {
        if (_state.value is State.Ready) return
        scope.launch { mutex.withLock { _state.value = withContext(Dispatchers.IO) { read() } } }
    }

    private fun read(): State {
        if (!file.exists()) return State.Ready(emptyList())
        return try {
            val bytes = file.readBytes()
            if (!SealedFile.isSealed(bytes)) return State.Unreadable
            val key = keys.get(create = false) ?: return State.Unreadable
            val plain = SealedFile.open(key, bytes)
            State.Ready(json.decodeFromString(PrivateFileV1.serializer(), plain.decodeToString()).contacts)
        } catch (e: Throwable) {
            State.Unreadable
        }
    }

    /** Replaces the list with [transform]'s result. False when nothing could be saved (no key, unreadable file, disk). */
    private suspend fun change(transform: (List<PrivateContact>) -> List<PrivateContact>): Boolean = mutex.withLock {
        val current = (_state.value as? State.Ready ?: withContext(Dispatchers.IO) { read() }.also { _state.value = it } as? State.Ready)
            ?: return@withLock false
        val next = transform(current.contacts)
        val ok = withContext(Dispatchers.IO) {
            try {
                val key = keys.get(create = true) ?: return@withContext false
                val plain = json.encodeToString(PrivateFileV1.serializer(), PrivateFileV1(contacts = next)).encodeToByteArray()
                val sealed = SealedFile.sealChecked(key, plain) ?: return@withContext false
                SealedFile.writeAtomic(file, tmp, sealed)
                true
            } catch (e: Throwable) {
                runCatching { tmp.delete() }
                false
            }
        }
        if (ok) _state.value = State.Ready(next)
        ok
    }

    fun get(id: String): PrivateContact? = (state.value as? State.Ready)?.contacts?.firstOrNull { it.id == id }

    /** Saves a new contact (or replaces the one with the same id). Returns its id, or null when it couldn't be saved. */
    suspend fun save(contact: PrivateContact): String? =
        if (change { list -> if (list.any { it.id == contact.id }) list.map { if (it.id == contact.id) contact else it } else list + contact }) contact.id else null

    suspend fun delete(id: String): Boolean = change { list -> list.filterNot { it.id == id } }

    suspend fun setStarred(id: String, starred: Boolean): Boolean = change { list -> list.map { if (it.id == id) it.copy(starred = starred) else it } }

    companion object {
        const val FILE = "private_contacts.bin"
    }
}
