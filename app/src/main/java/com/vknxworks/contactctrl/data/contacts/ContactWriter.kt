package com.vknxworks.contactctrl.data.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves changes to Android's contacts storage. Only rows that changed are written, and only in raw contacts
 * ContactCtrl may change, so data from read-only accounts (messengers) is never touched.
 */
class ContactWriter(private val resolver: ContentResolver) {

    sealed interface Result {
        /** Saved; the contact it is now. */
        data class Saved(val uri: Uri) : Result
        data object ReadOnly : Result
        data class Failed(val error: String) : Result
    }

    suspend fun create(draft: ContactDraft, account: SaveAccount): Result = withContext(Dispatchers.IO) {
        runCatching {
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .build()
            fun insert(mime: String, values: ContentValues) {
                ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, mime)
                    .withValues(values)
                    .build()
            }
            if (!draft.name.isBlank) insert(StructuredName.CONTENT_ITEM_TYPE, nameValues(draft.name))
            if (draft.nickname.isNotBlank()) insert(Nickname.CONTENT_ITEM_TYPE, ContentValues().apply { put(Nickname.NAME, draft.nickname.trim()) })
            if (draft.company.isNotBlank() || draft.title.isNotBlank()) insert(Organization.CONTENT_ITEM_TYPE, orgValues(draft))
            if (draft.note.isNotBlank()) insert(Note.CONTENT_ITEM_TYPE, ContentValues().apply { put(Note.NOTE, draft.note.trim()) })
            for (f in draft.allFields()) if (f.value.isNotBlank()) insert(mimeOf(f.kind), fieldValues(f))
            draft.newPhoto?.let { insert(Photo.CONTENT_ITEM_TYPE, ContentValues().apply { put(Photo.PHOTO, it) }) }

            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawUri = results.firstOrNull()?.uri ?: error("No raw contact created")
            Result.Saved(RawContacts.getContactLookupUri(resolver, rawUri) ?: rawUri)
        }.getOrElse { Result.Failed(it.message ?: it.javaClass.simpleName) }
    }

    suspend fun update(original: ContactDetail, draft: ContactDraft): Result = withContext(Dispatchers.IO) {
        val writable = original.raws.filter { it.writable }.map { it.id }.toSet()
        if (writable.isEmpty()) return@withContext Result.ReadOnly
        runCatching {
            // New rows go to the raw contact that holds the name, if it can be changed; else the first one that can.
            val target = original.nameRawId?.takeIf { it in writable } ?: original.raws.first { it.writable }.id
            val ops = ArrayList<ContentProviderOperation>()

            fun insert(mime: String, values: ContentValues) {
                ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, target)
                    .withValue(Data.MIMETYPE, mime)
                    .withValues(values)
                    .build()
            }
            fun updateRow(id: Long, values: ContentValues) {
                ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, id)).withValues(values).build()
            }
            fun deleteRow(id: Long) {
                ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, id)).build()
            }
            /** A single-value field (name, nickname, company, note): update it, add it, or remove it. */
            fun single(dataId: Long?, rawId: Long?, changed: Boolean, blank: Boolean, mime: String, values: ContentValues) {
                if (!changed) return
                val ours = dataId != null && rawId in writable
                when {
                    ours && blank -> deleteRow(dataId!!)
                    ours -> updateRow(dataId!!, values)
                    !blank -> insert(mime, values)
                }
            }

            single(original.nameDataId, original.nameRawId, draft.name != original.name, draft.name.isBlank, StructuredName.CONTENT_ITEM_TYPE, nameValues(draft.name))
            single(original.nicknameDataId, original.nicknameRawId, draft.nickname.trim() != original.nickname, draft.nickname.isBlank(), Nickname.CONTENT_ITEM_TYPE, ContentValues().apply { put(Nickname.NAME, draft.nickname.trim()) })
            single(original.orgDataId, original.orgRawId, draft.company.trim() != original.company || draft.title.trim() != original.title, draft.company.isBlank() && draft.title.isBlank(), Organization.CONTENT_ITEM_TYPE, orgValues(draft))
            single(original.noteDataId, original.noteRawId, draft.note.trim() != original.note, draft.note.isBlank(), Note.CONTENT_ITEM_TYPE, ContentValues().apply { put(Note.NOTE, draft.note.trim()) })

            // Lists: changed rows are updated, removed rows deleted, new rows added.
            fun list(before: List<Field>, after: List<Field>) {
                val afterIds = after.mapNotNull { it.dataId }.toSet()
                for (old in before) {
                    val id = old.dataId ?: continue
                    if (old.rawContactId !in writable) continue
                    if (id !in afterIds) deleteRow(id)
                }
                for (f in after) {
                    val old = before.firstOrNull { it.dataId != null && it.dataId == f.dataId }
                    when {
                        old == null -> if (f.value.isNotBlank()) insert(mimeOf(f.kind), fieldValues(f))
                        old.rawContactId !in writable -> Unit
                        f.value.isBlank() -> deleteRow(old.dataId!!)
                        old.value != f.value || old.type != f.type || old.label != f.label -> updateRow(old.dataId!!, fieldValues(f, update = true))
                    }
                }
            }
            list(original.phones, draft.phones)
            list(original.emails, draft.emails)
            list(original.addresses, draft.addresses)
            list(original.websites, draft.websites)
            list(original.events, draft.events)

            if (draft.removePhoto || draft.newPhoto != null) {
                // Replace the photo in every raw contact we may change, so the new one is the one that shows.
                for (raw in writable) {
                    ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(raw.toString(), Photo.CONTENT_ITEM_TYPE))
                        .build()
                }
                draft.newPhoto?.let { insert(Photo.CONTENT_ITEM_TYPE, ContentValues().apply { put(Photo.PHOTO, it) }) }
            }

            if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Result.Saved(Contacts.getLookupUri(original.id, original.lookupKey))
        }.getOrElse { Result.Failed(it.message ?: it.javaClass.simpleName) }
    }

    suspend fun setStarred(id: Long, lookupKey: String, starred: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Contacts.lookupContact(resolver, Contacts.getLookupUri(id, lookupKey)) ?: return@runCatching false
            resolver.update(uri, ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }, null, null) > 0
        }.getOrDefault(false)
    }

    suspend fun setRingtone(id: Long, lookupKey: String, ringtone: String?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Contacts.lookupContact(resolver, Contacts.getLookupUri(id, lookupKey)) ?: return@runCatching false
            resolver.update(uri, ContentValues().apply { put(Contacts.CUSTOM_RINGTONE, ringtone) }, null, null) > 0
        }.getOrDefault(false)
    }

    /** Deletes the whole contact, in every account. */
    suspend fun delete(id: Long, lookupKey: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(Contacts.getLookupUri(id, lookupKey), null, null) > 0 }.getOrDefault(false)
    }

    suspend fun deleteMany(contacts: List<ContactSummary>): Int = withContext(Dispatchers.IO) {
        contacts.count { c -> runCatching { resolver.delete(Contacts.getLookupUri(c.id, c.lookupKey), null, null) > 0 }.getOrDefault(false) }
    }

    private fun ContactDraft.allFields(): List<Field> = phones + emails + addresses + websites + events

    private fun nameValues(n: NameParts) = ContentValues().apply {
        put(StructuredName.DISPLAY_NAME, n.display)
        put(StructuredName.PREFIX, n.prefix.trim().ifEmpty { null })
        put(StructuredName.GIVEN_NAME, n.given.trim().ifEmpty { null })
        put(StructuredName.MIDDLE_NAME, n.middle.trim().ifEmpty { null })
        put(StructuredName.FAMILY_NAME, n.family.trim().ifEmpty { null })
        put(StructuredName.SUFFIX, n.suffix.trim().ifEmpty { null })
    }

    private fun orgValues(d: ContactDraft) = ContentValues().apply {
        put(Organization.COMPANY, d.company.trim().ifEmpty { null })
        put(Organization.TITLE, d.title.trim().ifEmpty { null })
        put(Organization.TYPE, Organization.TYPE_WORK)
    }

    private fun fieldValues(f: Field, update: Boolean = false) = ContentValues().apply {
        put(Data.DATA1, f.value.trim())
        put(Data.DATA2, f.type)
        put(Data.DATA3, if (f.type == 0) f.label?.trim()?.ifEmpty { null } else null)
        if (f.kind == Kind.ADDRESS && update) {
            // The address is edited as one block of text; drop the old parts so nothing stale stays behind.
            for (col in listOf(StructuredPostal.STREET, StructuredPostal.POBOX, StructuredPostal.NEIGHBORHOOD, StructuredPostal.CITY, StructuredPostal.REGION, StructuredPostal.POSTCODE, StructuredPostal.COUNTRY)) putNull(col)
        }
    }

    companion object {
        fun mimeOf(kind: Kind): String = when (kind) {
            Kind.PHONE -> Phone.CONTENT_ITEM_TYPE
            Kind.EMAIL -> Email.CONTENT_ITEM_TYPE
            Kind.ADDRESS -> StructuredPostal.CONTENT_ITEM_TYPE
            Kind.WEBSITE -> Website.CONTENT_ITEM_TYPE
            Kind.EVENT -> Event.CONTENT_ITEM_TYPE
        }
    }
}
