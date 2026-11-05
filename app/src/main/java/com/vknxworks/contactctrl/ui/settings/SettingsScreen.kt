package com.vknxworks.contactctrl.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.Accounts
import com.vknxworks.contactctrl.data.contacts.SaveAccount
import com.vknxworks.contactctrl.data.lock.LockVault
import com.vknxworks.contactctrl.data.settings.AppSettings
import com.vknxworks.contactctrl.data.settings.LockTimeout
import com.vknxworks.contactctrl.data.settings.NameOrder
import com.vknxworks.contactctrl.data.settings.SortBy
import com.vknxworks.contactctrl.ui.common.hasPermission
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupChoice
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.components.GroupSwitch
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.contacts.writeVCards
import com.vknxworks.contactctrl.ui.lock.AppLock
import com.vknxworks.contactctrl.ui.lock.PinRequest
import com.vknxworks.contactctrl.ui.lock.findActivity
import com.vknxworks.contactctrl.ui.nav.ImportRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Settings, opened from the You page (tap the profile picture). ContactCtrl follows the phone's dark theme. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    fun set(transform: (AppSettings) -> AppSettings) {
        scope.launch { c.settings.update(transform) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 4.dp, bottom = 24.dp),
        ) {
            ListGroup(settings) { set(it) }
            NewContactsGroup(settings) { set(it) }
            ImportExportGroup()
            PrivacyAndSecurityGroup(settings)
        }
    }
}

@Composable
private fun ListGroup(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Group(stringResource(R.string.group_list)) {
        GroupChoice(
            stringResource(R.string.name_order),
            NameOrder.entries.map { it to stringResource(it.label) },
            settings.nameOrder,
            icon = Icons.Outlined.Badge,
        ) { v -> set { it.copy(nameOrder = v) } }
        GroupChoice(
            stringResource(R.string.sort_by),
            SortBy.entries.map { it to stringResource(it.label) },
            settings.sortBy,
            icon = Icons.Outlined.SortByAlpha,
        ) { v -> set { it.copy(sortBy = v) } }
        GroupSwitch(stringResource(R.string.show_numbers), stringResource(R.string.show_numbers_sub), settings.showNumbers, Icons.Outlined.Numbers) { on ->
            set { it.copy(showNumbers = on) }
        }
        GroupSwitch(stringResource(R.string.tap_to_call), stringResource(R.string.tap_to_call_sub), settings.tapToCall, Icons.Outlined.Call) { on ->
            set { it.copy(tapToCall = on) }
        }
    }
}

@Composable
private fun NewContactsGroup(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    var accounts by remember { mutableStateOf(listOf(SaveAccount(null, null))) }
    LaunchedEffect(Unit) { if (c.contacts.hasPermission()) accounts = c.contacts.saveAccounts() }
    val current = accounts.firstOrNull { "${it.type}\u0000${it.name}" == settings.defaultAccount } ?: accounts.first()
    Group(stringResource(R.string.group_new_contacts), footer = stringResource(R.string.save_to_footer)) {
        GroupChoice(
            stringResource(R.string.save_to),
            accounts.map { it to if (it.isDevice) context.getString(R.string.account_device) else "${Accounts.label(it.type)} · ${it.name.orEmpty()}" },
            current,
            icon = if (current.isDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.Sync,
        ) { a -> set { it.copy(defaultAccount = if (a.isDevice) "" else "${a.type}\u0000${a.name}") } }
    }
}

@Composable
private fun ImportExportGroup() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    var exporting by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) nav.navigate(ImportRoute(uri.toString()))
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        c.appScope.launch {
            val keys = c.contacts.contacts.value.map { it.lookupKey }
            val n = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { writeVCards(context, keys, it) } ?: -1
            }.getOrDefault(-1)
            exporting = false
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (n >= 0) context.resources.getQuantityString(R.plurals.export_done, n, n) else context.getString(R.string.export_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val granted = context.hasPermission(android.Manifest.permission.READ_CONTACTS)
    Group(stringResource(R.string.group_import_export), footer = stringResource(R.string.import_export_footer)) {
        GroupRow(
            stringResource(R.string.import_vcf),
            stringResource(R.string.import_vcf_sub),
            icon = Icons.Outlined.Download,
            showChevron = true,
            onClick = if (granted) ({
                AppLock.expectReturn()
                importPicker.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream"))
            }) else null,
        )
        GroupRow(
            stringResource(R.string.export_vcf),
            stringResource(if (exporting) R.string.export_running else R.string.export_vcf_sub),
            icon = Icons.Outlined.Upload,
            showChevron = true,
            onClick = if (granted && !exporting) ({
                AppLock.expectReturn()
                exportPicker.launch("contacts-${LocalDate.now()}.vcf")
            }) else null,
        )
    }
}

/** Privacy and security: the shared Ctrl series app lock, then hiding ContactCtrl in screenshots. */
@Composable
private fun PrivacyAndSecurityGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val lockOn = c.vault.enabled
    Group(stringResource(R.string.group_privacy), footer = stringResource(R.string.app_lock_footer)) {
        GroupSwitch(
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_subtitle),
            checked = lockOn,
            icon = Icons.Outlined.Lock,
        ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
        if (lockOn) {
            GroupRow(
                stringResource(R.string.lock_change_pin),
                stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                icon = Icons.Outlined.Password,
                showChevron = true,
                onClick = { AppLock.request = PinRequest.CHANGE },
            )
            GroupSwitch(
                stringResource(R.string.lock_fingerprint),
                stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                checked = c.vault.biometricEnabled,
                icon = Icons.Outlined.Fingerprint,
            ) { on ->
                if (!on) {
                    c.vault.disableBiometric()
                } else if (bioAvailable) {
                    val activity = context.findActivity()
                    val cipher = c.vault.biometricEnrolCipher()
                    if (activity == null || cipher == null) {
                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enable_title),
                            context.getString(R.string.lock_fingerprint_enable_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            GroupChoice(
                stringResource(R.string.lock_again),
                LockTimeout.entries.map { it to stringResource(it.label) },
                settings.lockTimeout,
                icon = Icons.Outlined.Timer,
            ) { t -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = t) } } }
        }
        GroupSwitch(
            stringResource(R.string.hide_screenshots),
            stringResource(R.string.hide_screenshots_subtitle),
            checked = settings.hideScreenshots,
            icon = Icons.Outlined.VisibilityOff,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
        GroupSwitch(
            stringResource(R.string.hide_recents),
            stringResource(R.string.hide_recents_subtitle),
            checked = settings.hideRecents,
            icon = Icons.Outlined.Layers,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
    }
}
