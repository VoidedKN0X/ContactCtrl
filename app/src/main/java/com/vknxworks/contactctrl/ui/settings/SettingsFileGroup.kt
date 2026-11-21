package com.vknxworks.contactctrl.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.contactctrl.BuildConfig
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.background.BackupScheduler
import com.vknxworks.contactctrl.background.BirthdayReminders
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.settings.AppSettings
import com.vknxworks.contactctrl.data.settings.SettingsBackup
import com.vknxworks.contactctrl.ui.common.hasPermission
import com.vknxworks.contactctrl.ui.components.CtrlAlertDialog
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.lock.AppLock
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime

/** Export ContactCtrl's settings to a file, or import them from one after showing what would change. */
@Composable
fun SettingsFileGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    var pending by remember { mutableStateOf<Pair<AppSettings, List<String>>?>(null) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.appScope.launch {
            val text = SettingsBackup.encode(SettingsBackup.of(c.settings.current(), BuildConfig.VERSION_NAME, OffsetDateTime.now().toString()))
            val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(if (ok) R.string.settings_file_saved else R.string.settings_file_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.appScope.launch {
            val backup = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // A settings file is a few hundred bytes; refuse anything big.
                    val bytes = input.readNBytes(64 * 1024 + 1)
                    if (bytes.size > 64 * 1024) null else SettingsBackup.decode(bytes.decodeToString())
                }
            }.getOrNull()
            val current = c.settings.current()
            val next = backup?.applyTo(current, canNotify = context.hasPermission(Manifest.permission.POST_NOTIFICATIONS))
            withContext(Dispatchers.Main) {
                when {
                    next == null -> Toast.makeText(context, context.getString(R.string.settings_file_invalid), Toast.LENGTH_LONG).show()
                    next == current -> Toast.makeText(context, context.getString(R.string.settings_file_same), Toast.LENGTH_SHORT).show()
                    else -> pending = next to changedNames(context, current, next)
                }
            }
        }
    }

    Group(stringResource(R.string.group_settings_file), footer = stringResource(R.string.settings_file_footer)) {
        GroupRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_sub), icon = Icons.Outlined.FileUpload, showChevron = true, onClick = {
            AppLock.expectReturn()
            exportPicker.launch("contactctrl-settings-${LocalDate.now()}.json")
        })
        GroupRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_sub), icon = Icons.Outlined.FileDownload, showChevron = true, onClick = {
            AppLock.expectReturn()
            importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        })
    }

    pending?.let { (next, names) ->
        CtrlAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.settings_import_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.settings_import_body, names.joinToString("\n") { "• $it" }), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    c.appScope.launch {
                        // Applied to what is saved at this moment, so nothing outside the file is undone.
                        c.settings.update { cur -> SettingsBackup.decodeApplied(cur, next) }
                        BirthdayReminders.schedule(context)
                        BackupScheduler.schedule(context)
                        withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.settings_imported), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(stringResource(R.string.settings_import_replace), color = CtrlColors.AccentText) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        )
    }
}

/** The settings [next] changes, by their names in Settings. */
private fun changedNames(context: android.content.Context, a: AppSettings, b: AppSettings): List<String> = buildList {
    fun add(changed: Boolean, res: Int) { if (changed) add(context.getString(res)) }
    add(a.nameOrder != b.nameOrder, R.string.name_order)
    add(a.sortBy != b.sortBy, R.string.sort_by)
    add(a.showNumbers != b.showNumbers, R.string.show_numbers)
    add(a.tapToCall != b.tapToCall, R.string.tap_to_call)
    add(a.defaultAccount != b.defaultAccount, R.string.save_to)
    add(a.lockTimeout != b.lockTimeout, R.string.lock_again)
    add(a.hideScreenshots != b.hideScreenshots, R.string.hide_screenshots)
    add(a.hideRecents != b.hideRecents, R.string.hide_recents)
    add(a.birthdayReminders != b.birthdayReminders, R.string.birthday_reminders)
    add(a.birthdayHour != b.birthdayHour, R.string.birthday_time)
    add(a.backupEvery != b.backupEvery, R.string.backup_every)
    add(a.backupKeep != b.backupKeep, R.string.backup_keep)
}
