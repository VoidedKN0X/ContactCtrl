package com.vknxworks.contactctrl.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.background.BackupScheduler
import com.vknxworks.contactctrl.background.BirthdayReminders
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.settings.AppSettings
import com.vknxworks.contactctrl.data.settings.BackupEvery
import com.vknxworks.contactctrl.ui.common.hasPermission
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupChoice
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.components.GroupSwitch
import com.vknxworks.contactctrl.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Birthday reminders: on/off and the hour. */
@Composable
fun BirthdayGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    fun save(t: (AppSettings) -> AppSettings) {
        c.appScope.launch {
            c.settings.update(t)
            BirthdayReminders.schedule(context)
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save { it.copy(birthdayReminders = true) }
        else Toast.makeText(context, context.getString(R.string.birthday_no_permission), Toast.LENGTH_LONG).show()
    }
    Group(stringResource(R.string.group_birthdays), footer = stringResource(R.string.birthday_footer)) {
        GroupSwitch(stringResource(R.string.birthday_reminders), stringResource(R.string.birthday_reminders_sub), settings.birthdayReminders, Icons.Outlined.Cake) { on ->
            if (!on) save { it.copy(birthdayReminders = false) }
            else if (context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) save { it.copy(birthdayReminders = true) }
            else {
                AppLock.expectReturn()
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (settings.birthdayReminders) {
            val hours = listOf(7, 8, 9, 10, 12, 18, 20)
            val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
            GroupChoice(
                stringResource(R.string.birthday_time),
                (hours + settings.birthdayHour).distinct().sorted().map { h ->
                    h to fmt.format(Date(java.time.LocalDate.now().atTime(h, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
                },
                settings.birthdayHour,
                icon = Icons.Outlined.Schedule,
            ) { h -> save { it.copy(birthdayHour = h) } }
        }
    }
}

/** The scheduled .vcf backup: folder, how often, how many to keep, back up now, last result. */
@Composable
fun BackupGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    var running by remember { mutableStateOf(false) }
    fun save(t: (AppSettings) -> AppSettings) {
        c.appScope.launch {
            c.settings.update(t)
            BackupScheduler.schedule(context)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (!ok) {
            Toast.makeText(context, context.getString(R.string.backup_folder_failed), Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        // Let go of the folder picked before, if it was another one.
        val old = settings.backupFolder
        if (old.isNotEmpty() && old != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        save { it.copy(backupFolder = uri.toString(), backupOn = true) }
    }
    fun pickFolder() {
        AppLock.expectReturn()
        runCatching { folderPicker.launch(null) }
    }
    val folderName = remember(settings.backupFolder) {
        settings.backupFolder.takeIf { it.isNotEmpty() }?.let { s ->
            runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(s)).substringAfter(':').ifBlank { "/" } }.getOrNull()
        }
    }
    Group(stringResource(R.string.group_backup), footer = stringResource(R.string.backup_footer)) {
        GroupSwitch(stringResource(R.string.backup_scheduled), stringResource(R.string.backup_scheduled_sub), settings.backupOn, Icons.Outlined.Backup) { on ->
            if (on && settings.backupFolder.isEmpty()) pickFolder() else save { it.copy(backupOn = on) }
        }
        if (settings.backupOn) {
            GroupRow(stringResource(R.string.backup_folder), folderName ?: stringResource(R.string.backup_folder_none), icon = Icons.Outlined.Folder, showChevron = true, onClick = ::pickFolder)
            GroupChoice(
                stringResource(R.string.backup_every),
                BackupEvery.entries.map { it to stringResource(it.label) },
                settings.backupEvery,
                icon = Icons.Outlined.Update,
            ) { e -> save { it.copy(backupEvery = e) } }
            GroupChoice(
                stringResource(R.string.backup_keep),
                listOf(1, 3, 5, 10, 20).map { it to context.resources.getQuantityString(R.plurals.backup_keep_n, it, it) },
                settings.backupKeep,
                icon = Icons.Outlined.Inventory2,
            ) { n -> save { it.copy(backupKeep = n) } }
            val last = when {
                running -> stringResource(R.string.backup_running)
                settings.lastBackupFailed -> stringResource(R.string.backup_last_failed)
                settings.lastBackupAt > 0 -> stringResource(R.string.backup_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt)))
                else -> stringResource(R.string.backup_never)
            }
            GroupRow(
                stringResource(R.string.backup_now),
                last,
                icon = Icons.Outlined.History,
                onClick = if (running || settings.backupFolder.isEmpty()) null else ({
                    running = true
                    c.appScope.launch {
                        val ok = BackupScheduler.runOnce(context)
                        withContext(Dispatchers.Main) {
                            running = false
                            Toast.makeText(context, context.getString(if (ok) R.string.backup_done else R.string.backup_last_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }),
            )
        }
    }
}
