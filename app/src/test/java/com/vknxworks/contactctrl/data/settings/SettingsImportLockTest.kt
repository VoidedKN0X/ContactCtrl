package com.vknxworks.contactctrl.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsImportLockTest {
    @Test fun aFileCanMakeTheLockSooner() {
        assertEquals(LockTimeout.IMMEDIATELY, stricterLockTimeout(LockTimeout.MINUTES_5, LockTimeout.IMMEDIATELY))
        assertEquals(LockTimeout.HOUR_1, stricterLockTimeout(LockTimeout.RESTART, LockTimeout.HOUR_1))
    }

    @Test fun aFileCanNeverMakeTheLockLater() {
        assertEquals(LockTimeout.IMMEDIATELY, stricterLockTimeout(LockTimeout.IMMEDIATELY, LockTimeout.RESTART))
        assertEquals(LockTimeout.MINUTE_1, stricterLockTimeout(LockTimeout.MINUTE_1, LockTimeout.MINUTES_15))
        assertEquals(LockTimeout.MINUTES_15, stricterLockTimeout(LockTimeout.MINUTES_15, LockTimeout.RESTART))
    }

    @Test fun applyToKeepsTheStricterChoice() {
        val current = AppSettings(lockTimeout = LockTimeout.IMMEDIATELY)
        val applied = SettingsBackup(lockTimeout = LockTimeout.RESTART.name).applyTo(current, canNotify = false)
        assertEquals(LockTimeout.IMMEDIATELY, applied.lockTimeout)
    }
}
