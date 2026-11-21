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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    /** Background work that outlives a screen. A failure there must never close the app. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

    val settings = SettingsRepository(context, appScope)
    val contacts = ContactsRepository(context, appScope)
    val writer = ContactWriter(context.contentResolver)

    /** The data key for files ContactCtrl keeps encrypted at rest (the shared Ctrl series helpers). */
    private val dataKeys = com.vknxworks.contactctrl.data.crypto.DataKeyStore(
        context.filesDir, com.vknxworks.contactctrl.data.crypto.KeystoreWrapper("contactctrl_data_at_rest"),
    )

    /** Private contacts: encrypted, inside ContactCtrl only. */
    val privateContacts = com.vknxworks.contactctrl.data.contacts.PrivateContactsStore(context, appScope, dataKeys)

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
        appScope.launch(Dispatchers.IO) { clearLeftoverSharedFiles() }
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
    }

    /** When this process started; shared files written before it are leftovers of an earlier run. */
    private val startedAt = System.currentTimeMillis()

    /**
     * Sharing a contact writes a plain .vcf to the private cache (`cacheDir/shared`), and it stayed there until
     * the next share. Leftovers are deleted at each start and when the app locks. Only files last written before
     * this start are touched, so a share that is in progress, or one the receiving app is still reading, is
     * never hit: a file shared in this run stays until the next share or the next start, as before.
     */
    private fun clearLeftoverSharedFiles() {
        runCatching {
            File(context.cacheDir, "shared").listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < startedAt) file.delete()
            }
        }
    }

    init {
        AppLock.onLocked = { onLocked() }
        appScope.launch(Dispatchers.IO) { clearLeftoverSharedFiles() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ContactCtrlApp).container
