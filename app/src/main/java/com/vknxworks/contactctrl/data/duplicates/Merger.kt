package com.vknxworks.contactctrl.data.duplicates

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.RawContacts
import android.telephony.TelephonyManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.contactctrl.data.contacts.Accounts
import com.vknxworks.contactctrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** A merge ContactCtrl made: which raw contacts belonged to which contact before, so it can be undone. */
@Serializable
data class MergeRecord(
    val id: String,
    /** The merged contact's name, for the undo message. */
    val name: String,
    /** The raw contact ids of each contact that was merged, one list per former contact. */
    val groups: List<List<Long>>,
    val at: Long,
) {
    /** This merge joined at least two of these raw contacts (those of the contact on screen). */
    fun covers(rawIds: Collection<Long>): Boolean {
        val set = rawIds.toSet()
        return groups.count { g -> g.any { it in set } } >= 2
    }
}

/** One raw contact of a contact: which account it's from, and whether ContactCtrl may change that account. */
data class RawRef(val id: Long, val contactId: Long, val accountType: String?, val accountName: String?, val writable: Boolean)

/**
 * Merging and separating contacts with Android's own aggregation exceptions: KEEP_TOGETHER joins the raw contacts
 * of several contacts (from any accounts) into one contact, KEEP_SEPARATE splits them again. Nothing is deleted
 * and no account's data is changed, so this works with read-only accounts (messengers) too: their details just
 * show in the merged contact. "Not duplicates" answers and the merges made here (for undo) are kept in
 * ContactCtrl's own settings, never in the contacts.
 */
class Merger(private val context: Context, scope: CoroutineScope) {
    private val resolver = context.contentResolver
    private val json = Json { ignoreUnknownKeys = true }
    private val dismissedKey = stringPreferencesKey("dup_dismissed")
    private val mergesKey = stringPreferencesKey("dup_merges")

    private val prefs = context.appDataStore.data.catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }

    /** Pairs of contacts (lookup keys, see [DuplicateFinder.pairKey]) marked "not duplicates". */
    val dismissed: StateFlow<Set<String>> = prefs
        .map { p -> p[dismissedKey]?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }.orEmpty().toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Merges made in ContactCtrl, newest first. */
    val merges: StateFlow<List<MergeRecord>> = prefs
        .map { p -> p[mergesKey]?.let { runCatching { json.decodeFromString(ListSerializer(MergeRecord.serializer()), it) }.getOrNull() }.orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The phone's country, for phone numbers written without a country code (no permission needed). */
    fun region(): String? {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val fromPhone = runCatching { tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() } }.getOrNull()
        return (fromPhone ?: Locale.getDefault().country).takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
    }

    /** Remembers that the contacts of a group are not the same person. */
    suspend fun dismiss(group: DupGroup) = edit { p ->
        val now = p[dismissedKey]?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }.orEmpty()
        // At most a few thousand answers are kept; the oldest go first.
        p[dismissedKey] = json.encodeToString(ListSerializer(String.serializer()), (now + DuplicateFinder.pairsOf(group)).distinct().takeLast(5_000))
    }

    /** Every raw contact, by contact (one query). */
    suspend fun rawsByContact(): Map<Long, List<RawRef>> = withContext(Dispatchers.IO) {
        val writable = Accounts.writableTypes()
        val out = HashMap<Long, MutableList<RawRef>>()
        runCatching {
            resolver.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
                "${RawContacts.DELETED}=0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.isNull(1)) continue
                    val type = c.getString(2)
                    out.getOrPut(c.getLong(1)) { ArrayList() } += RawRef(c.getLong(0), c.getLong(1), type, c.getString(3), Accounts.isWritable(type, writable))
                }
            }
        }
        out
    }

    private fun rawIdsOf(contactId: Long): List<Long> {
        val out = ArrayList<Long>()
        resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null)?.use { c ->
            while (c.moveToNext()) out += c.getLong(0)
        }
        return out
    }

    private fun existingRawIds(ids: Collection<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val out = HashSet<Long>()
        ids.chunked(400).forEach { chunk ->
            resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), "${RawContacts._ID} IN (${chunk.joinToString(",")}) AND ${RawContacts.DELETED}=0", null, null)?.use { c ->
                while (c.moveToNext()) out += c.getLong(0)
            }
        }
        return out
    }

    /** One exception for every pair of raw contacts from different groups. */
    private fun setPairs(groups: List<List<Long>>, type: Int) {
        val ops = ArrayList<ContentProviderOperation>()
        for (i in groups.indices) for (j in i + 1 until groups.size) for (a in groups[i]) for (b in groups[j]) {
            ops += ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                .withValue(AggregationExceptions.TYPE, type)
                .withValue(AggregationExceptions.RAW_CONTACT_ID1, a)
                .withValue(AggregationExceptions.RAW_CONTACT_ID2, b)
                .build()
        }
        ops.chunked(300).forEach { resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(it)) }
    }

    sealed interface Result {
        data class Merged(val record: MergeRecord) : Result
        data object Failed : Result
    }

    /** Joins these contacts into one. Their raw contacts all stay, in their own accounts. */
    suspend fun merge(contactIds: List<Long>, name: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val groups = contactIds.distinct().map { rawIdsOf(it) }.filter { it.isNotEmpty() }
            if (groups.size < 2 || groups.sumOf { it.size } > MAX_RAWS) return@runCatching Result.Failed
            setPairs(groups, AggregationExceptions.TYPE_KEEP_TOGETHER)
            val record = MergeRecord(UUID.randomUUID().toString(), name.take(200), groups, System.currentTimeMillis())
            edit { p -> p[mergesKey] = json.encodeToString(ListSerializer(MergeRecord.serializer()), (listOf(record) + mergesNow(p)).take(MAX_RECORDS)) }
            Result.Merged(record)
        }.getOrDefault(Result.Failed)
    }

    /** Splits a merge made here back into the contacts it joined (raw contacts deleted meanwhile are left out). */
    suspend fun undo(record: MergeRecord): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val alive = existingRawIds(record.groups.flatten())
            val groups = record.groups.map { g -> g.filter { it in alive } }.filter { it.isNotEmpty() }
            if (groups.size >= 2) setPairs(groups, AggregationExceptions.TYPE_KEEP_SEPARATE)
            edit { p -> p[mergesKey] = json.encodeToString(ListSerializer(MergeRecord.serializer()), mergesNow(p).filter { it.id != record.id }) }
            true
        }.getOrDefault(false)
    }

    private fun mergesNow(p: androidx.datastore.preferences.core.Preferences): List<MergeRecord> =
        p[mergesKey]?.let { runCatching { json.decodeFromString(ListSerializer(MergeRecord.serializer()), it) }.getOrNull() }.orEmpty()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.appDataStore.edit { block(it) }
        } catch (_: IOException) {
            // The answer isn't kept; nothing else changes.
        }
    }

    companion object {
        /** Merges of more raw contacts than this at once are refused (the provider would need thousands of pairs). */
        const val MAX_RAWS = 60
        const val MAX_RECORDS = 100
    }
}
