package com.vknxworks.contactctrl.data.contacts

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads the phone's contacts (Android's contacts storage, shared with every app and sync account).
 * ContactCtrl keeps no copy of its own: the list is read again whenever the contacts change.
 */
class ContactsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val resolver: ContentResolver = context.contentResolver

    private val _contacts = MutableStateFlow<List<ContactSummary>>(emptyList())
    val contacts: StateFlow<List<ContactSummary>> = _contacts

    /** False until the first load finished, so the list doesn't flash "no contacts". */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Bumped whenever the contacts change, so open detail screens reload. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private var observing = false
    private var reloadJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun canWrite(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Starts watching the contacts (once the permission is granted) and loads them. */
    fun start() {
        if (!hasPermission()) return
        if (!observing) {
            runCatching { resolver.registerContentObserver(Contacts.CONTENT_URI, true, observer) }
            observing = true
        }
        scheduleReload(0)
    }

    /** Several changes in a row (a sync, an import) cause one reload. */
    private fun scheduleReload(delayMs: Long = 400) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(delayMs)
            val list = withContext(Dispatchers.IO) { runCatching { loadAll() }.getOrNull() }
            if (list != null) _contacts.value = list
            _loaded.value = true
            _version.value++
        }
    }

    fun refreshNow() = scheduleReload(0)

    private fun loadAll(): List<ContactSummary> {
        val phones = HashMap<Long, MutableList<String>>()
        resolver.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val num = c.getString(1) ?: continue
                val list = phones.getOrPut(c.getLong(0)) { mutableListOf() }
                if (list.none { ContactSummary.digits(it) == ContactSummary.digits(num) }) list += num
            }
        }
        val emails = HashMap<Long, MutableList<String>>()
        resolver.query(Email.CONTENT_URI, arrayOf(Email.CONTACT_ID, Email.ADDRESS), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val e = c.getString(1) ?: continue
                emails.getOrPut(c.getLong(0)) { mutableListOf() } += e
            }
        }
        val out = ArrayList<ContactSummary>()
        val proj = arrayOf(
            Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.DISPLAY_NAME_ALTERNATIVE,
            Contacts.SORT_KEY_PRIMARY, Contacts.SORT_KEY_ALTERNATIVE, Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED,
        )
        resolver.query(Contacts.CONTENT_URI, proj, null, null, Contacts.SORT_KEY_PRIMARY)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2).orEmpty().ifBlank { phones[id]?.firstOrNull() ?: emails[id]?.firstOrNull() ?: "" }
                if (name.isBlank()) continue
                out += ContactSummary(
                    id = id,
                    lookupKey = c.getString(1) ?: continue,
                    name = name,
                    altName = c.getString(3).orEmpty().ifBlank { name },
                    sortKey = c.getString(4).orEmpty().ifBlank { name },
                    altSortKey = c.getString(5).orEmpty().ifBlank { name },
                    photoUri = c.getString(6),
                    starred = c.getInt(7) != 0,
                    phones = phones[id].orEmpty(),
                    emails = emails[id].orEmpty(),
                )
            }
        }
        return out
    }

    /** The contact a lookup URI or id points to now (contacts can be re-joined, so the lookup key is tried first). */
    suspend fun resolve(uri: Uri): Pair<Long, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val contactUri = when {
                uri.authority != ContactsContract.AUTHORITY -> return@runCatching null
                uri.pathSegments.firstOrNull() == "raw_contacts" ->
                    RawContacts.getContactLookupUri(resolver, uri) ?: return@runCatching null
                uri.pathSegments.firstOrNull() == "data" -> {
                    resolver.query(uri, arrayOf(Data.CONTACT_ID), null, null, null)?.use { c ->
                        if (c.moveToFirst()) Contacts.getLookupUri(resolver, android.content.ContentUris.withAppendedId(Contacts.CONTENT_URI, c.getLong(0))) else null
                    } ?: return@runCatching null
                }
                else -> uri
            }
            val real = Contacts.lookupContact(resolver, contactUri) ?: contactUri
            resolver.query(real, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) to (c.getString(1) ?: "") else null
            }
        }.getOrNull()
    }

    /** Finds a contact with this phone number or email. */
    suspend fun findByPhoneOrEmail(value: String, isEmail: Boolean): Pair<Long, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = if (isEmail) Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(value))
            else Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(value))
            val cols = if (isEmail) arrayOf(Email.CONTACT_ID, Email.LOOKUP_KEY) else arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY)
            resolver.query(uri, cols, null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) to (c.getString(1) ?: "") else null }
        }.getOrNull()
    }

    suspend fun detail(id: Long, lookupKey: String): ContactDetail? = withContext(Dispatchers.IO) {
        runCatching { loadDetail(id, lookupKey) }.getOrNull()
    }

    private fun loadDetail(id: Long, lookupKey: String): ContactDetail? {
        val lookupUri = Contacts.getLookupUri(id, lookupKey)
        val contactUri = Contacts.lookupContact(resolver, lookupUri) ?: return null
        var contactId = id
        var key = lookupKey
        var displayName = ""
        var photo: String? = null
        var starred = false
        var ringtone: String? = null
        resolver.query(contactUri, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI, Contacts.STARRED, Contacts.CUSTOM_RINGTONE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            contactId = c.getLong(0)
            key = c.getString(1) ?: key
            displayName = c.getString(2).orEmpty()
            photo = c.getString(3)
            starred = c.getInt(4) != 0
            ringtone = c.getString(5)
        } ?: return null

        val writableTypes = Accounts.writableTypes()
        val raws = ArrayList<RawInfo>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(1)
                raws += RawInfo(c.getLong(0), type, c.getString(2), Accounts.isWritable(type, writableTypes))
            }
        }
        val writableRaw = raws.filter { it.writable }.map { it.id }.toSet()

        var name = NameParts(); var nameId: Long? = null; var nameRaw: Long? = null
        var nickname = ""; var nickId: Long? = null; var nickRaw: Long? = null
        var company = ""; var title = ""; var orgId: Long? = null; var orgRaw: Long? = null
        var note = ""; var noteId: Long? = null; var noteRaw: Long? = null
        val phones = ArrayList<Field>(); val emails = ArrayList<Field>(); val addresses = ArrayList<Field>()
        val websites = ArrayList<Field>(); val events = ArrayList<Field>()

        val proj = arrayOf(
            Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.IS_PRIMARY,
            Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6,
        )
        resolver.query(Data.CONTENT_URI, proj, "${Data.CONTACT_ID}=?", arrayOf(contactId.toString()), "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID}")?.use { c ->
            while (c.moveToNext()) {
                val dataId = c.getLong(0)
                val raw = c.getLong(1)
                val primary = c.getInt(3) != 0
                fun s(i: Int) = c.getString(i).orEmpty()
                // Prefer rows ContactCtrl can edit for the single-value fields.
                fun better(current: Long?, currentRaw: Long?): Boolean =
                    current == null || (currentRaw !in writableRaw && raw in writableRaw)
                when (c.getString(2)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (better(nameId, nameRaw)) {
                        // DATA4 prefix, DATA2 given, DATA5 middle, DATA3 family, DATA6 suffix
                        val parts = NameParts(s(7), s(5), s(8), s(6), s(9))
                        if (!parts.isBlank || nameId == null) {
                            name = if (parts.isBlank) NameParts(given = s(4)) else parts
                            nameId = dataId; nameRaw = raw
                        }
                    }
                    Nickname.CONTENT_ITEM_TYPE -> if (better(nickId, nickRaw) && s(4).isNotBlank()) { nickname = s(4); nickId = dataId; nickRaw = raw }
                    Organization.CONTENT_ITEM_TYPE -> if (better(orgId, orgRaw) && (s(4).isNotBlank() || c.getString(7) != null)) {
                        company = s(4); title = s(7); orgId = dataId; orgRaw = raw
                    }
                    Note.CONTENT_ITEM_TYPE -> if (better(noteId, noteRaw) && s(4).isNotBlank()) { note = s(4); noteId = dataId; noteRaw = raw }
                    Phone.CONTENT_ITEM_TYPE -> if (s(4).isNotBlank()) {
                        val f = Field(Kind.PHONE, s(4), c.getInt(5), c.getString(6), dataId, raw, primary)
                        if (phones.none { ContactSummary.digits(it.value) == ContactSummary.digits(f.value) }) phones += f
                    }
                    Email.CONTENT_ITEM_TYPE -> if (s(4).isNotBlank() && emails.none { it.value.equals(s(4), true) }) emails += Field(Kind.EMAIL, s(4), c.getInt(5), c.getString(6), dataId, raw, primary)
                    StructuredPostal.CONTENT_ITEM_TYPE -> if (s(4).isNotBlank()) addresses += Field(Kind.ADDRESS, s(4), c.getInt(5), c.getString(6), dataId, raw, primary)
                    Website.CONTENT_ITEM_TYPE -> if (s(4).isNotBlank()) websites += Field(Kind.WEBSITE, s(4), c.getInt(5), c.getString(6), dataId, raw, primary)
                    Event.CONTENT_ITEM_TYPE -> if (s(4).isNotBlank()) events += Field(Kind.EVENT, s(4), c.getInt(5), c.getString(6), dataId, raw, primary)
                }
            }
        }
        return ContactDetail(
            id = contactId, lookupKey = key, displayName = displayName.ifBlank { name.display },
            name = name, nameDataId = nameId, nameRawId = nameRaw,
            nickname = nickname, nicknameDataId = nickId, nicknameRawId = nickRaw,
            company = company, title = title, orgDataId = orgId, orgRawId = orgRaw,
            note = note, noteDataId = noteId, noteRawId = noteRaw,
            phones = phones, emails = emails, addresses = addresses, websites = websites, events = events,
            photoUri = photo, starred = starred, ringtone = ringtone, raws = raws,
        )
    }

    /** Accounts new contacts can be saved to: the phone, and sync accounts that already hold contacts and sync back. */
    suspend fun saveAccounts(): List<SaveAccount> = withContext(Dispatchers.IO) {
        val writable = Accounts.writableTypes()
        val seen = LinkedHashSet<SaveAccount>()
        seen += SaveAccount(null, null)
        runCatching {
            resolver.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
                "${RawContacts.DELETED}=0 AND ${RawContacts.ACCOUNT_TYPE} IS NOT NULL", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0) ?: continue
                    if (type in writable) seen += SaveAccount(type, c.getString(1))
                }
            }
        }
        seen.toList()
    }

    fun close() {
        if (observing) runCatching { resolver.unregisterContentObserver(observer) }
        observing = false
    }
}

internal fun Cursor.str(col: String): String? = getColumnIndex(col).let { if (it >= 0) getString(it) else null }
