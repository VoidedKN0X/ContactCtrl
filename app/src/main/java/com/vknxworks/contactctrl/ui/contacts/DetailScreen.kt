package com.vknxworks.contactctrl.ui.contacts

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.Accounts
import com.vknxworks.contactctrl.data.contacts.ContactDetail
import com.vknxworks.contactctrl.data.contacts.Field
import com.vknxworks.contactctrl.data.contacts.Kind
import com.vknxworks.contactctrl.ui.common.ContactAvatar
import com.vknxworks.contactctrl.ui.common.formatEventDate
import com.vknxworks.contactctrl.ui.common.hasPermission
import com.vknxworks.contactctrl.ui.common.launch
import com.vknxworks.contactctrl.ui.common.typeLabel
import com.vknxworks.contactctrl.ui.components.ConfirmDialog
import com.vknxworks.contactctrl.ui.components.CtrlBottomSheet
import com.vknxworks.contactctrl.ui.components.CtrlSquareButton
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.components.topDivider
import com.vknxworks.contactctrl.ui.nav.EditRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import com.vknxworks.contactctrl.ui.theme.CtrlShapes
import com.vknxworks.contactctrl.ui.theme.CtrlType
import com.vknxworks.contactctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(id: Long, key: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val version by c.contacts.version.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val merges by c.merger.merges.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<ContactDetail?>(null) }
    var gone by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingCall by remember { mutableStateOf<String?>(null) }

    // Reloads whenever the contacts change (an edit here, a sync, another app).
    LaunchedEffect(id, key, version) {
        val d = c.contacts.detail(id, key)
        if (d == null) gone = true else { detail = d; gone = false }
    }

    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCall?.let { call(context, it, direct = granted) }
        pendingCall = null
    }
    fun dial(number: String) {
        if (!settings.tapToCall) call(context, number, direct = false)
        else if (context.hasPermission(android.Manifest.permission.CALL_PHONE)) call(context, number, direct = true)
        else { pendingCall = number; callPermission.launch(android.Manifest.permission.CALL_PHONE) }
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val d = detail ?: return@rememberLauncherForActivityResult
            val picked: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            scope.launch { c.writer.setRingtone(d.id, d.lookupKey, picked?.toString()) }
        }
    }

    val d = detail
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "",
            actions = {
                if (d != null) {
                    CtrlSquareButton(
                        if (d.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        stringResource(if (d.starred) R.string.unfavourite else R.string.favourite),
                        onClick = { scope.launch { c.writer.setStarred(d.id, d.lookupKey, !d.starred) } },
                        tint = if (d.starred) CtrlColors.AccentText else CtrlColors.Text,
                    )
                    if (d.writable) CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.edit), onClick = { nav.navigate(EditRoute(d.id, d.lookupKey)) })
                    CtrlSquareButton(Icons.Outlined.MoreVert, stringResource(R.string.more), onClick = { menu = true })
                }
            },
        )
        when {
            gone -> MessageView(Icons.Outlined.PersonOff, stringResource(R.string.contact_not_found))
            d == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = CtrlColors.AccentText)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                Header(d, version)
                QuickActions(
                    phone = d.phones.firstOrNull { it.primary }?.value ?: d.phones.firstOrNull()?.value,
                    email = d.emails.firstOrNull { it.primary }?.value ?: d.emails.firstOrNull()?.value,
                    onCall = ::dial,
                    onText = { sms(context, it) },
                    onEmail = { email(context, it) },
                )
                if (d.phones.isNotEmpty()) Group(stringResource(R.string.group_phones)) {
                    d.phones.forEach { f ->
                        FieldRow(f, Icons.Outlined.Call, onClick = { dial(f.value) }, trailingIcon = Icons.Outlined.ChatBubbleOutline,
                            trailingLabel = stringResource(R.string.action_text), onTrailing = { sms(context, f.value) })
                    }
                }
                if (d.emails.isNotEmpty()) Group(stringResource(R.string.group_emails)) {
                    d.emails.forEach { f -> FieldRow(f, Icons.Outlined.Email, onClick = { email(context, f.value) }) }
                }
                if (d.addresses.isNotEmpty()) Group(stringResource(R.string.group_addresses)) {
                    d.addresses.forEach { f -> FieldRow(f, Icons.Outlined.Place, onClick = { map(context, f.value) }) }
                }
                if (d.company.isNotBlank() || d.title.isNotBlank() || d.nickname.isNotBlank() || d.websites.isNotEmpty() || d.events.isNotEmpty()) {
                    Group(stringResource(R.string.group_about_contact)) {
                        if (d.company.isNotBlank() || d.title.isNotBlank()) {
                            GroupRow(listOf(d.title, d.company).filter { it.isNotBlank() }.joinToString(" · "), stringResource(R.string.field_work), icon = Icons.Outlined.Work)
                        }
                        if (d.nickname.isNotBlank()) GroupRow(d.nickname, stringResource(R.string.field_nickname))
                        d.events.forEach { f -> FieldRow(f, Icons.Outlined.Cake, display = formatEventDate(f.value), onClick = null) }
                        d.websites.forEach { f -> FieldRow(f, Icons.Outlined.Language, onClick = { web(context, f.value) }) }
                    }
                }
                if (d.note.isNotBlank()) Group(stringResource(R.string.field_note)) {
                    CopyableText(d.note, Icons.AutoMirrored.Outlined.StickyNote2)
                }
                Group(stringResource(R.string.group_more), footer = accountsFooter(context, d)) {
                    GroupRow(
                        stringResource(R.string.ringtone),
                        ringtoneName(context, d.ringtone),
                        icon = Icons.Outlined.LibraryMusic,
                        showChevron = true,
                        onClick = {
                            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, d.ringtone?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                            com.vknxworks.contactctrl.ui.lock.AppLock.expectReturn()
                            runCatching { ringtonePicker.launch(i) }
                        },
                    )
                    if (!d.writable) GroupRow(stringResource(R.string.read_only), stringResource(R.string.read_only_body), icon = Icons.Outlined.Lock)
                    // Merged here from several contacts: split it back (Android's "keep separate", nothing deleted).
                    val record = remember(d.raws, merges) { merges.firstOrNull { it.covers(d.raws.map { r -> r.id }) } }
                    if (record != null) {
                        GroupRow(
                            stringResource(R.string.separate),
                            pluralStringResource(R.plurals.separate_sub, record.groups.size, record.groups.size),
                            icon = Icons.AutoMirrored.Outlined.CallSplit,
                            onClick = {
                                scope.launch {
                                    val ok = c.merger.undo(record)
                                    Toast.makeText(context, context.getString(if (ok) R.string.separated else R.string.separate_failed), Toast.LENGTH_SHORT).show()
                                    c.contacts.refreshNow()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (menu && d != null) {
        CtrlBottomSheet(onDismissRequest = { menu = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                SheetAction(Icons.Outlined.Share, stringResource(R.string.share)) {
                    menu = false
                    scope.launch { shareContacts(context, listOf(d.lookupKey), d.displayName) }
                }
                SheetAction(Icons.Outlined.Delete, stringResource(R.string.delete), danger = true) {
                    menu = false
                    confirmDelete = true
                }
            }
        }
    }
    if (confirmDelete && d != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title, d.displayName),
            body = stringResource(R.string.delete_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    if (c.writer.delete(d.id, d.lookupKey)) nav.back()
                    else Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun Header(d: ContactDetail, version: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ContactAvatar(d.displayName, d.photoUri, size = 112.dp, photoKey = version)
        Text(d.displayName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val sub = listOf(d.title, d.company).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = CtrlType.Data, color = CtrlColors.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuickActions(phone: String?, email: String?, onCall: (String) -> Unit, onText: (String) -> Unit, onEmail: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickAction(Icons.Outlined.Call, stringResource(R.string.action_call), phone != null, Modifier.weight(1f), accent = true) { phone?.let(onCall) }
        QuickAction(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.action_text), phone != null, Modifier.weight(1f)) { phone?.let(onText) }
        QuickAction(Icons.Outlined.Email, stringResource(R.string.action_email), email != null, Modifier.weight(1f)) { email?.let(onEmail) }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        !enabled -> CtrlColors.Surface
        accent -> CtrlColors.Accent
        else -> CtrlColors.Surface
    }
    val fg = when {
        !enabled -> CtrlColors.Inactive
        accent -> CtrlColors.OnAccent
        else -> CtrlColors.Text
    }
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(CtrlShapes.Button)
            .background(bg)
            .border(1.dp, if (accent && enabled) bg else CtrlColors.Outline, CtrlShapes.Button)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 10.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = fg)
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldRow(
    f: Field,
    icon: ImageVector,
    display: String = f.value,
    onClick: (() -> Unit)?,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .topDivider(CtrlColors.Outline)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    copy(context, f.value)
                },
            )
            .heightIn(min = 62.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = CtrlColors.Text)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(display, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
            Text(
                typeLabel(context, f.kind, f.type, f.label) + if (f.primary && f.kind != Kind.EVENT) " · " + stringResource(R.string.default_marker) else "",
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
            )
        }
        if (trailingIcon != null && onTrailing != null) {
            CtrlSquareButton(trailingIcon, trailingLabel, onClick = onTrailing, size = 40.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableText(text: String, icon: ImageVector) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { copy(context, text) }).padding(16.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = CtrlColors.Text)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text)
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    GroupRow(label, icon = icon, iconTint = if (danger) CtrlColors.Error else CtrlColors.Text, titleColor = if (danger) CtrlColors.Error else CtrlColors.Text, onClick = onClick)
}

private fun accountsFooter(context: Context, d: ContactDetail): String {
    val names = d.raws.map { Accounts.label(it.accountType) + if (!it.writable) " (" + context.getString(R.string.read_only_short) + ")" else "" }.distinct()
    return context.getString(R.string.stored_in, names.joinToString(", "))
}

private fun ringtoneName(context: Context, uri: String?): String {
    if (uri == null) return context.getString(R.string.ringtone_default)
    if (uri.isEmpty()) return context.getString(R.string.ringtone_silent)
    return runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull() ?: context.getString(R.string.ringtone_custom)
}

fun call(context: Context, number: String, direct: Boolean) {
    val uri = Uri.fromParts("tel", number, null)
    val ok = context.launch(Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    if (!ok && direct) context.launch(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun sms(context: Context, number: String) {
    if (!context.launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) noApp(context)
}

fun email(context: Context, address: String) {
    if (!context.launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) noApp(context)
}

fun map(context: Context, address: String) {
    if (!context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address.replace('\n', ' ')))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) noApp(context)
}

fun web(context: Context, url: String) {
    val u = if (url.contains("://")) url else "https://$url"
    val parsed = Uri.parse(u)
    if (parsed.scheme !in listOf("http", "https") || !context.launch(Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) noApp(context)
}

private fun noApp(context: Context) = Toast.makeText(context, context.getString(R.string.no_app), Toast.LENGTH_SHORT).show()

fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("", text)
    // Phone numbers and addresses are personal: keep them out of the clipboard preview on Android 13+.
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
}
