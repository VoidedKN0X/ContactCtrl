package com.vknxworks.contactctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.contactctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "contactctrl")

/** How long ContactCtrl can be in the background before the app lock asks again (-1: only after a restart). */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

/** How names are shown: "John Smith" or "Smith, John". */
enum class NameOrder(@StringRes val label: Int) {
    FIRST_LAST(R.string.name_order_first),
    LAST_FIRST(R.string.name_order_last),
}

/** What the list is sorted by. */
enum class SortBy(@StringRes val label: Int) {
    FIRST_NAME(R.string.sort_first),
    LAST_NAME(R.string.sort_last),
}

/** How often the scheduled .vcf backup runs. */
enum class BackupEvery(@StringRes val label: Int, val days: Int) {
    DAY(R.string.backup_daily, 1),
    WEEK(R.string.backup_weekly, 7),
}

data class AppSettings(
    val nameOrder: NameOrder = NameOrder.FIRST_LAST,
    val sortBy: SortBy = SortBy.FIRST_NAME,
    /** The list shows each contact's first phone number under the name. */
    val showNumbers: Boolean = true,
    /** Where new contacts are saved: "" for the phone itself, or "type\u0000name" of a sync account. */
    val defaultAccount: String = "",
    /** Tapping a number calls it right away (true) or opens it in the phone app first (false). */
    val tapToCall: Boolean = true,
    /** App lock: when it asks again after ContactCtrl was in the background. */
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** "Hide in screenshots": FLAG_SECURE on the app's windows (screenshots, recordings and the Recents preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the Recents view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
    /** A notification on the morning of a contact's birthday. */
    val birthdayReminders: Boolean = false,
    /** The hour (0-23) the birthday notification comes, give or take the phone's battery saving. */
    val birthdayHour: Int = 9,
    /** The scheduled .vcf backup to [backupFolder]. */
    val backupOn: Boolean = false,
    /** The folder the backup goes to: a document-tree URI you picked, "" for none yet. */
    val backupFolder: String = "",
    val backupEvery: BackupEvery = BackupEvery.WEEK,
    /** How many backup files ContactCtrl keeps in the folder; older ones it made itself are deleted. */
    val backupKeep: Int = 5,
    /** When the last backup finished (ms), 0 for never. Status, not a setting: never exported. */
    val lastBackupAt: Long = 0L,
    /** The last backup run failed (the folder is gone or not writable). Status, not a setting. */
    val lastBackupFailed: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val nameOrder = stringPreferencesKey("name_order")
        val sortBy = stringPreferencesKey("sort_by")
        val showNumbers = booleanPreferencesKey("show_numbers")
        val defaultAccount = stringPreferencesKey("default_account")
        val tapToCall = booleanPreferencesKey("tap_to_call")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        val birthdayReminders = booleanPreferencesKey("birthday_reminders")
        val birthdayHour = intPreferencesKey("birthday_hour")
        val backupOn = booleanPreferencesKey("backup_on")
        val backupFolder = stringPreferencesKey("backup_folder")
        val backupEvery = stringPreferencesKey("backup_every")
        val backupKeep = intPreferencesKey("backup_keep")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val lastBackupFailed = booleanPreferencesKey("last_backup_failed")
    }

    private val prefs: Flow<Preferences> = context.appDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private val current = MutableStateFlow(AppSettings())
    private val read = MutableStateFlow(false)

    /** The settings; the defaults until the saved ones have been read (see [loaded]). */
    val settings: StateFlow<AppSettings> = current.asStateFlow()

    /**
     * True once the saved settings have been read from storage. Always set after [settings] got its first
     * saved value, so whoever sees `true` here also sees the saved settings.
     */
    val loaded: StateFlow<Boolean> = read.asStateFlow()

    init {
        scope.launch {
            prefs.map { it.toSettings() }.collect { s ->
                current.value = s
                read.value = true
            }
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        nameOrder = enumOr(this[K.nameOrder], NameOrder.FIRST_LAST),
        sortBy = enumOr(this[K.sortBy], SortBy.FIRST_NAME),
        showNumbers = this[K.showNumbers] ?: true,
        defaultAccount = this[K.defaultAccount] ?: "",
        tapToCall = this[K.tapToCall] ?: true,
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        hideScreenshots = this[K.hideScreenshots] ?: false,
        hideRecents = this[K.hideRecents] ?: false,
        birthdayReminders = this[K.birthdayReminders] ?: false,
        birthdayHour = (this[K.birthdayHour] ?: 9).coerceIn(0, 23),
        backupOn = this[K.backupOn] ?: false,
        backupFolder = this[K.backupFolder] ?: "",
        backupEvery = enumOr(this[K.backupEvery], BackupEvery.WEEK),
        backupKeep = (this[K.backupKeep] ?: 5).coerceIn(1, 50),
        lastBackupAt = this[K.lastBackupAt] ?: 0L,
        lastBackupFailed = this[K.lastBackupFailed] ?: false,
    )

    /** The saved settings, read from disk (for background work, where [settings] may not have loaded yet). */
    suspend fun current(): AppSettings = prefs.first().toSettings()

    /** Saves a change. If the phone's storage fails, the change is dropped instead of closing the app. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            context.appDataStore.edit { p ->
                val s = transform(p.toSettings())
                p[K.nameOrder] = s.nameOrder.name
                p[K.sortBy] = s.sortBy.name
                p[K.showNumbers] = s.showNumbers
                p[K.defaultAccount] = s.defaultAccount
                p[K.tapToCall] = s.tapToCall
                p[K.lockTimeout] = s.lockTimeout.name
                p[K.hideScreenshots] = s.hideScreenshots
                p[K.hideRecents] = s.hideRecents
                p[K.birthdayReminders] = s.birthdayReminders
                p[K.birthdayHour] = s.birthdayHour
                p[K.backupOn] = s.backupOn
                p[K.backupFolder] = s.backupFolder
                p[K.backupEvery] = s.backupEvery.name
                p[K.backupKeep] = s.backupKeep
                p[K.lastBackupAt] = s.lastBackupAt
                p[K.lastBackupFailed] = s.lastBackupFailed
            }
        } catch (e: IOException) {
            // Nothing to do: the settings stay as they were.
        }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
