package com.vknxworks.contactctrl

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.contactctrl.ui.lock.AppLock
import kotlinx.coroutines.launch

class ContactCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Birthday reminders and the scheduled backup are set again at every start (an alarm doesn't survive
        // being force-stopped).
        container.appScope.launch {
            runCatching { com.vknxworks.contactctrl.background.BirthdayReminders.schedule(this@ContactCtrlApp) }
            runCatching { com.vknxworks.contactctrl.background.BackupScheduler.schedule(this@ContactCtrlApp) }
        }
        // The app lock: locks after the chosen time away ("Lock again").
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(container.vault.enabled, container.settings.settings.value.lockTimeout.millis)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLock.onBackground(container.vault.enabled, container.settings.settings.value.lockTimeout.millis)
            }
        })
    }
}
