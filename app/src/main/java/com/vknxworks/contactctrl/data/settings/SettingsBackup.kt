package com.vknxworks.contactctrl.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ContactCtrl's settings as a JSON file you keep: list, new-contact, reminder and backup choices. Never in it:
 * the app lock's PIN or keys, private contacts, the backup folder (access to it belongs to this phone), merge
 * records or status. Every field is optional, so files from older or newer versions restore what they can.
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val appVersion: String? = null,
    val createdAt: String? = null,
    val nameOrder: String? = null,
    val sortBy: String? = null,
    val showNumbers: Boolean? = null,
    val tapToCall: Boolean? = null,
    val defaultAccount: String? = null,
    val lockTimeout: String? = null,
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
    val birthdayReminders: Boolean? = null,
    val birthdayHour: Int? = null,
    val backupEvery: String? = null,
    val backupKeep: Int? = null,
) {
    companion object {
        const val FORMAT = "contactctrl-settings"
        const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true; explicitNulls = false }

        fun of(s: AppSettings, appVersion: String, createdAt: String) = SettingsBackup(
            appVersion = appVersion, createdAt = createdAt,
            nameOrder = s.nameOrder.name, sortBy = s.sortBy.name, showNumbers = s.showNumbers, tapToCall = s.tapToCall,
            defaultAccount = s.defaultAccount, lockTimeout = s.lockTimeout.name,
            hideScreenshots = s.hideScreenshots, hideRecents = s.hideRecents,
            birthdayReminders = s.birthdayReminders, birthdayHour = s.birthdayHour,
            backupEvery = s.backupEvery.name, backupKeep = s.backupKeep,
        )

        fun encode(b: SettingsBackup): String = json.encodeToString(serializer(), b)

        /** [next]'s file-controlled settings on top of [cur] (what is saved when you confirm). */
        fun decodeApplied(cur: AppSettings, next: AppSettings): AppSettings = cur.copy(
            nameOrder = next.nameOrder, sortBy = next.sortBy, showNumbers = next.showNumbers, tapToCall = next.tapToCall,
            defaultAccount = next.defaultAccount, lockTimeout = next.lockTimeout,
            hideScreenshots = cur.hideScreenshots || next.hideScreenshots, hideRecents = cur.hideRecents || next.hideRecents,
            birthdayReminders = next.birthdayReminders, birthdayHour = next.birthdayHour,
            backupEvery = next.backupEvery, backupKeep = next.backupKeep,
        )

        /** The file's contents, or null when it isn't a ContactCtrl settings file (or is from a newer format). */
        fun decode(text: String): SettingsBackup? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
            ?.takeIf { it.format == FORMAT && it.version in 1..VERSION }
    }

    /**
     * [current] with this file applied. Safety rules: "Hide in screenshots" and "Hide in recent apps" can only be
     * turned on by a file, never off; birthday reminders are only turned on when [canNotify]; the backup folder and
     * everything outside this file stay as they are.
     */
    fun applyTo(current: AppSettings, canNotify: Boolean): AppSettings = current.copy(
        nameOrder = enumOr(nameOrder, current.nameOrder),
        sortBy = enumOr(sortBy, current.sortBy),
        showNumbers = showNumbers ?: current.showNumbers,
        tapToCall = tapToCall ?: current.tapToCall,
        defaultAccount = defaultAccount ?: current.defaultAccount,
        lockTimeout = enumOr(lockTimeout, current.lockTimeout),
        hideScreenshots = current.hideScreenshots || hideScreenshots == true,
        hideRecents = current.hideRecents || hideRecents == true,
        birthdayReminders = when (birthdayReminders) {
            true -> canNotify || current.birthdayReminders
            false -> false
            null -> current.birthdayReminders
        },
        birthdayHour = birthdayHour?.takeIf { it in 0..23 } ?: current.birthdayHour,
        backupEvery = enumOr(backupEvery, current.backupEvery),
        backupKeep = backupKeep?.takeIf { it in 1..50 } ?: current.backupKeep,
    )
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
