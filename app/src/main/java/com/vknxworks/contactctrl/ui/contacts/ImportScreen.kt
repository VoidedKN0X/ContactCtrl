package com.vknxworks.contactctrl.ui.contacts

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.Accounts
import com.vknxworks.contactctrl.data.contacts.ContactDraft
import com.vknxworks.contactctrl.data.contacts.ContactSummary
import com.vknxworks.contactctrl.data.contacts.SaveAccount
import com.vknxworks.contactctrl.data.contacts.VCard
import com.vknxworks.contactctrl.ui.components.CtrlButton
import com.vknxworks.contactctrl.ui.components.CtrlProgressBar
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupChoice
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.components.GroupSwitch
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import com.vknxworks.contactctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** Imports the contacts in a .vcf file, after showing what's in it. */
@Composable
fun ImportScreen(uriString: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf<List<ContactDraft>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf(listOf(SaveAccount(null, null))) }
    var account by remember { mutableStateOf(SaveAccount(null, null)) }
    var skipDuplicates by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(-1) }
    var skipped by remember { mutableIntStateOf(0) }
    var progress by remember { mutableIntStateOf(0) }

    LaunchedEffect(uriString) {
        runCatching { VCard.read(context.contentResolver, Uri.parse(uriString)) }
            .onSuccess { cards = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        accounts = c.contacts.saveAccounts()
        val saved = c.settings.settings.value.defaultAccount
        account = accounts.firstOrNull { "${it.type}\u0000${it.name}" == saved } ?: accounts.first()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.import_title))
        val list = cards
        when {
            error != null -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.import_failed), error)
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            list.isEmpty() -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.import_none))
            done >= 0 -> MessageView(
                Icons.Outlined.CheckCircle,
                pluralStringResource(R.plurals.import_done, done, done),
                if (skipped > 0) pluralStringResource(R.plurals.import_skipped, skipped, skipped) else null,
                actionLabel = stringResource(R.string.done),
                onAction = { nav.back() },
            )
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                Group(pluralStringResource(R.plurals.import_found, list.size, list.size)) {
                    list.take(8).forEach { d ->
                        GroupRow(d.name.display.ifBlank { d.company.ifBlank { d.phones.firstOrNull()?.value ?: d.emails.firstOrNull()?.value.orEmpty() } }, d.phones.firstOrNull()?.value)
                    }
                    if (list.size > 8) GroupRow(stringResource(R.string.import_and_more, list.size - 8))
                }
                Group(footer = stringResource(R.string.import_footer)) {
                    if (accounts.size > 1) GroupChoice(
                        stringResource(R.string.save_to),
                        accounts.map { it to if (it.isDevice) context.getString(R.string.account_device) else "${Accounts.label(it.type)} · ${it.name.orEmpty()}" },
                        account,
                        icon = if (account.isDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.Sync,
                    ) { account = it }
                    GroupSwitch(stringResource(R.string.import_skip_dupes), stringResource(R.string.import_skip_dupes_sub), skipDuplicates, Icons.Outlined.ContentCopy) { skipDuplicates = it }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (importing) {
                        CtrlProgressBar(progress / list.size.coerceAtLeast(1).toFloat(), color = CtrlColors.Accent)
                        Text(stringResource(R.string.import_progress, progress, list.size), color = CtrlColors.Muted)
                    } else {
                        CtrlButton(pluralStringResource(R.plurals.import_button, list.size, list.size), onClick = {
                            importing = true
                            scope.launch {
                                val existing = c.contacts.contacts.value
                                var ok = 0
                                var skip = 0
                                list.forEachIndexed { i, d ->
                                    progress = i
                                    if (skipDuplicates && isDuplicate(d, existing)) { skip++; return@forEachIndexed }
                                    if (c.writer.create(d, account) is com.vknxworks.contactctrl.data.contacts.ContactWriter.Result.Saved) ok++
                                }
                                progress = list.size
                                skipped = skip
                                done = ok
                                importing = false
                                c.contacts.refreshNow()
                            }
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Same name, and (when the card has numbers) a number in common: the contact is already on the phone. */
private fun isDuplicate(d: ContactDraft, existing: List<ContactSummary>): Boolean {
    val name = ContactSummary.fold(d.name.display.ifBlank { d.company }).replace(" ", "")
    if (name.isEmpty()) return false
    val digits = d.phones.map { ContactSummary.digits(it.value).takeLast(9) }.filter { it.length >= 6 }
    return existing.any { e ->
        ContactSummary.fold(e.name).replace(" ", "") == name &&
            (digits.isEmpty() || e.phones.any { p -> ContactSummary.digits(p).takeLast(9) in digits })
    }
}
