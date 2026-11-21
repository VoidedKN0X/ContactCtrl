package com.vknxworks.contactctrl.data.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A label (Android calls them groups). Every label belongs to one account, like the raw contacts in it: a
 * DAVx⁵ label can only hold DAVx⁵ contacts, a label on the phone only contacts saved on the phone.
 */
data class ContactGroup(
    val id: Long,
    val title: String,
    val accountType: String?,
    val accountName: String?,
    /** A label the account made itself ("My Contacts", "Starred in Android"): shown, never renamed or deleted. */
    val system: Boolean,
    /** The account doesn't allow changing it. */
    val readOnly: Boolean,
) {
    /** Same format as [AppSettings.defaultAccount] and [ContactSummary.accounts]: "type\u0000name", "" for the phone. */
    val accountKey: String get() = accountKey(accountType, accountName)
    val editable: Boolean get() = !system && !readOnly
}

fun accountKey(type: String?, name: String?): String = if (type == null) "" else "$type\u0000${name.orEmpty()}"

/** One label a contact is in: the membership row and the raw contact (account part) it belongs to. */
data class Membership(val dataId: Long, val rawContactId: Long, val groupId: Long)

/** Reads and changes labels. Membership is always written into the raw contact of the label's own account. */
class GroupsStore(private val resolver: ContentResolver) {

    /** Every label that isn't deleted, sorted by name. */
    fun loadAll(): List<ContactGroup> {
        val out = ArrayList<ContactGroup>()
        val proj = arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME, Groups.SYSTEM_ID, Groups.GROUP_IS_READ_ONLY)
        resolver.query(Groups.CONTENT_URI, proj, "${Groups.DELETED}=0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1)?.trim().orEmpty()
                if (title.isEmpty()) continue
                out += ContactGroup(
                    id = c.getLong(0),
                    title = title,
                    accountType = c.getString(2),
                    accountName = c.getString(3),
                    system = !c.isNull(4),
                    readOnly = c.getInt(5) != 0,
                )
            }
        }
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        return out.sortedWith { a, b -> collator.compare(a.title, b.title) }
    }

    /** contact id -> the label ids it is in (over all its accounts). */
    fun membershipsByContact(): Map<Long, Set<Long>> {
        val out = HashMap<Long, MutableSet<Long>>()
        resolver.query(
            Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, GroupMembership.GROUP_ROW_ID),
            "${Data.MIMETYPE}=?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.isNull(1)) continue
                out.getOrPut(c.getLong(0)) { HashSet() } += c.getLong(1)
            }
        }
        return out
    }

    suspend fun create(title: String, accountType: String?, accountName: String?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val v = ContentValues().apply {
                put(Groups.TITLE, title.trim())
                put(Groups.ACCOUNT_TYPE, accountType)
                put(Groups.ACCOUNT_NAME, accountName)
                put(Groups.GROUP_VISIBLE, 1)
            }
            resolver.insert(Groups.CONTENT_URI, v) != null
        }.getOrDefault(false)
    }

    suspend fun rename(group: ContactGroup, title: String): Boolean = withContext(Dispatchers.IO) {
        if (!group.editable) return@withContext false
        runCatching {
            resolver.update(ContentUris.withAppendedId(Groups.CONTENT_URI, group.id), ContentValues().apply { put(Groups.TITLE, title.trim()) }, null, null) > 0
        }.getOrDefault(false)
    }

    /** Deletes the label itself. The contacts in it stay exactly as they are; only their membership goes. */
    suspend fun delete(group: ContactGroup): Boolean = withContext(Dispatchers.IO) {
        if (!group.editable) return@withContext false
        runCatching { resolver.delete(ContentUris.withAppendedId(Groups.CONTENT_URI, group.id), null, null) > 0 }.getOrDefault(false)
    }

    /**
     * Puts the contact in [group] (or takes it out), in the contact's writable raw contacts of the label's
     * account. False when the contact has no part in that account that ContactCtrl may change.
     */
    suspend fun setMember(detail: ContactDetail, group: ContactGroup, member: Boolean): Boolean = withContext(Dispatchers.IO) {
        val raws = detail.raws.filter { it.writable && accountKey(it.accountType, it.accountName) == group.accountKey }
        if (raws.isEmpty() || group.system || group.readOnly) return@withContext false
        runCatching {
            val ops = ArrayList<ContentProviderOperation>()
            if (member) {
                if (detail.memberships.any { it.groupId == group.id && raws.any { r -> r.id == it.rawContactId } }) return@runCatching true
                ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, raws.first().id)
                    .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(GroupMembership.GROUP_ROW_ID, group.id)
                    .build()
            } else {
                for (m in detail.memberships) {
                    if (m.groupId == group.id && raws.any { it.id == m.rawContactId }) {
                        ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, m.dataId)).build()
                    }
                }
            }
            if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        }.getOrDefault(false)
    }

    /** Labels [detail] could be put in: those of the accounts it has a writable part in. */
    fun assignable(detail: ContactDetail, all: List<ContactGroup>): List<ContactGroup> {
        val keys = detail.raws.filter { it.writable }.map { accountKey(it.accountType, it.accountName) }.toSet()
        return all.filter { it.editable && it.accountKey in keys }
    }
}
