package com.vknxworks.contactctrl

import android.content.Context
import com.vknxworks.contactctrl.data.contacts.ContactDraft
import com.vknxworks.contactctrl.data.contacts.ContactWriter
import com.vknxworks.contactctrl.data.contacts.ContactsRepository
import com.vknxworks.contactctrl.data.lock.LockVault
import com.vknxworks.contactctrl.data.settings.AppSettings
import com.vknxworks.contactctrl.data.settings.SettingsRepository
import com.vknxworks.contactctrl.ui.lock.AppLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /** Background work that outlives a screen. A failure there must never close the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)
    val contacts = ContactsRepository(context, appScope)
    val writer = ContactWriter(context.contentResolver)

    /** Merging and separating duplicates, and the "not duplicates" answers. */
    val merger by lazy { com.vknxworks.contactctrl.data.duplicates.Merger(context, appScope) }

    /** What another app asked to open; the main screen picks it up. */
    val pending = MutableStateFlow<Launch?>(null)

    /** Fields another app filled in, for the edit screen ("Add to contacts"). */
    var prefill: ContactDraft? = null

    /** The app lock's PIN and fingerprint key (the shared Ctrl series lock). */
    val vault = LockVault(context, "contactctrl")

    suspend fun enableLock(pin: String) {
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
    }

    fun disableLock() {
        vault.disable()
    }

    /**
     * "Forgot PIN?": the PIN can't be recovered. Your contacts belong to the phone (every app and account shares
     * them), so they are left alone; ContactCtrl's own settings are reset and the lock turned off.
     */
    suspend fun forgetEverything() {
        settings.update { AppSettings() }
        vault.disable()
        AppLock.markUnlocked()
    }

    fun onLocked() {
        vault.close()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
    }

    init {
        AppLock.onLocked = { onLocked() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ContactCtrlApp).container
