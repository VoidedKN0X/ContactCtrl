package com.vknxworks.contactctrl.ui.contacts

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.ContactSummary
import com.vknxworks.contactctrl.data.contacts.ContactWriter
import com.vknxworks.contactctrl.data.contacts.PrivateContactsStore
import com.vknxworks.contactctrl.data.contacts.SaveAccount
import com.vknxworks.contactctrl.ui.common.formatEventDate
import com.vknxworks.contactctrl.ui.common.hasPermission
import com.vknxworks.contactctrl.ui.components.ConfirmDialog
import com.vknxworks.contactctrl.ui.components.CtrlBottomSheet
import com.vknxworks.contactctrl.ui.components.CtrlSquareButton
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.components.TabTitle
import com.vknxworks.contactctrl.ui.components.bottomClearance
import com.vknxworks.contactctrl.ui.nav.EditRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import com.vknxworks.contactctrl.ui.nav.PrivateDetailRoute
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

/**
 * The Private tab: contacts kept only inside ContactCtrl, encrypted, never in Android's shared contacts storage.
 * Other apps (dialler, messengers, pickers) don't see them, so they don't show a name for these numbers.
 */
@Composable
fun PrivateScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val state by c.privateContacts.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { c.privateContacts.load() }
    val ready = state as? PrivateContactsStore.State.Ready
    val all = remember(ready) { ready?.contacts.orEmpty().map { it.summary() }.sortedFor(settings) }
    val shown = remember(all, query) { if (query.isBlank()) all else ContactSummary.Query.of(query).let { q -> all.filter { it.matches(q) } } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(
            stringResource(R.string.tab_private),
            label = ready?.let { pluralStringResource(R.plurals.contact_count, it.contacts.size, it.contacts.size) },
            trailing = {
                if (ready != null) CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.private_new), onClick = { nav.navigate(EditRoute(private = true)) })
            },
        )
        when {
            state is PrivateContactsStore.State.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            state is PrivateContactsStore.State.Unreadable -> MessageView(Icons.Outlined.Lock, stringResource(R.string.private_unreadable), stringResource(R.string.private_unreadable_body), modifier = Modifier.bottomClearance())
            all.isEmpty() -> MessageView(Icons.Outlined.Lock, stringResource(R.string.private_empty), stringResource(R.string.private_empty_body), modifier = Modifier.bottomClearance())
            else -> {
                SearchField(query, onChange = { query = it })
                if (shown.isEmpty()) {
                    MessageView(Icons.Outlined.Search, stringResource(R.string.search_none, query.trim()), modifier = Modifier.bottomClearance())
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(shown, key = { it.lookupKey }, contentType = { "contact" }) { contact ->
                            ContactRow(contact, settings, selected = false, selecting = false,
                                onClick = { nav.navigate(PrivateDetailRoute(contact.lookupKey)) }, onLongClick = null)
                        }
                        item { Spacer(Modifier.bottomClearance(0.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivateDetailScreen(id: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.privateContacts.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmMove by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { c.privateContacts.load() }
    val contact = (state as? PrivateContactsStore.State.Ready)?.contacts?.firstOrNull { it.id == id }
    val d = remember(contact) { contact?.toDetail() }
    fun dial(number: String) = call(context, number, direct = settings.tapToCall && context.hasPermission(android.Manifest.permission.CALL_PHONE))

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "", actions = {
            if (contact != null) {
                CtrlSquareButton(
                    if (contact.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    stringResource(if (contact.starred) R.string.unfavourite else R.string.favourite),
                    onClick = { scope.launch { c.privateContacts.setStarred(contact.id, !contact.starred) } },
                    tint = if (contact.starred) CtrlColors.AccentText else CtrlColors.Text,
                )
                CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.edit), onClick = { nav.navigate(EditRoute(private = true, privateId = contact.id)) })
                CtrlSquareButton(Icons.Outlined.MoreVert, stringResource(R.string.more), onClick = { menu = true })
            }
        })
        when {
            state is PrivateContactsStore.State.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            state is PrivateContactsStore.State.Unreadable -> MessageView(Icons.Outlined.Lock, stringResource(R.string.private_unreadable), stringResource(R.string.private_unreadable_body))
            d == null -> MessageView(Icons.Outlined.PersonOff, stringResource(R.string.contact_not_found))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                Header(d, 0)
                QuickActions(
                    phone = d.phones.firstOrNull()?.value,
                    email = d.emails.firstOrNull()?.value,
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
                if (d.company.isNotBlank() || d.title.isNotBlank() || d.nickname.isNotBlank() || d.websites.isNotEmpty() || d.events.isNotEmpty() || d.ims.isNotEmpty() || d.relations.isNotEmpty()) {
                    Group(stringResource(R.string.group_about_contact)) {
                        if (d.company.isNotBlank() || d.title.isNotBlank()) {
                            GroupRow(listOf(d.title, d.company).filter { it.isNotBlank() }.joinToString(" · "), stringResource(R.string.field_work), icon = Icons.Outlined.Work)
                        }
                        if (d.nickname.isNotBlank()) GroupRow(d.nickname, stringResource(R.string.field_nickname))
                        d.events.forEach { f -> FieldRow(f, Icons.Outlined.Cake, display = formatEventDate(f.value), onClick = null) }
                        d.websites.forEach { f -> FieldRow(f, Icons.Outlined.Language, onClick = { web(context, f.value) }) }
                        d.ims.forEach { f -> FieldRow(f, Icons.Outlined.Forum, onClick = null) }
                        d.relations.forEach { f -> FieldRow(f, Icons.Outlined.People, onClick = null) }
                    }
                }
                if (d.note.isNotBlank()) Group(stringResource(R.string.field_note)) { CopyableText(d.note, Icons.AutoMirrored.Outlined.StickyNote2) }
                Text(
                    stringResource(R.string.private_detail_footer),
                    style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = com.vknxworks.contactctrl.ui.theme.Gutter + 4.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (menu && contact != null) {
        CtrlBottomSheet(onDismissRequest = { menu = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                SheetAction(Icons.Outlined.QrCode2, stringResource(R.string.qr_share)) { menu = false; showQr = true }
                if (context.hasPermission(android.Manifest.permission.WRITE_CONTACTS)) {
                    SheetAction(Icons.Outlined.LockOpen, stringResource(R.string.private_move_out)) { menu = false; confirmMove = true }
                }
                SheetAction(Icons.Outlined.Delete, stringResource(R.string.delete), danger = true) { menu = false; confirmDelete = true }
            }
        }
    }
    if (showQr && d != null) ContactQrDialog(d, onDismiss = { showQr = false })
    if (confirmMove && contact != null) {
        ConfirmDialog(
            title = stringResource(R.string.private_move_out_title, contact.displayName),
            body = stringResource(R.string.private_move_out_body),
            confirm = stringResource(R.string.private_move_out),
            onDismiss = { confirmMove = false },
            onConfirm = {
                confirmMove = false
                if (busy) return@ConfirmDialog
                busy = true
                scope.launch {
                    // Saved in the phone's contacts first; only once that worked is the private copy removed.
                    val accounts = c.contacts.saveAccounts()
                    val saved = c.settings.settings.value.defaultAccount
                    val account = accounts.firstOrNull { "${it.type}\u0000${it.name}" == saved } ?: SaveAccount(null, null)
                    val result = c.writer.create(contact.toDraft(), account)
                    val ok = result is ContactWriter.Result.Saved && c.privateContacts.delete(contact.id)
                    busy = false
                    if (result is ContactWriter.Result.Saved) c.contacts.refreshNow()
                    Toast.makeText(context, context.getString(if (ok) R.string.private_moved_out else R.string.private_move_failed), Toast.LENGTH_LONG).show()
                    if (ok) nav.back()
                }
            },
        )
    }
    if (confirmDelete && contact != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title, contact.displayName),
            body = stringResource(R.string.private_delete_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    if (c.privateContacts.delete(contact.id)) nav.back()
                    else Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
