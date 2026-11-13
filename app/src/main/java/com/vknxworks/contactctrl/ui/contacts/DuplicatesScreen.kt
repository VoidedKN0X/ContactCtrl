package com.vknxworks.contactctrl.ui.contacts

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.Accounts
import com.vknxworks.contactctrl.data.duplicates.DupContact
import com.vknxworks.contactctrl.data.duplicates.DupGroup
import com.vknxworks.contactctrl.data.duplicates.DupReason
import com.vknxworks.contactctrl.data.duplicates.DuplicateFinder
import com.vknxworks.contactctrl.data.duplicates.MergeRecord
import com.vknxworks.contactctrl.data.duplicates.Merger
import com.vknxworks.contactctrl.data.duplicates.RawRef
import com.vknxworks.contactctrl.ui.common.ContactAvatar
import com.vknxworks.contactctrl.ui.components.CtrlButton
import com.vknxworks.contactctrl.ui.components.CtrlCard
import com.vknxworks.contactctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.contactctrl.ui.components.CtrlSquareButton
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.components.SectionLabel
import com.vknxworks.contactctrl.ui.components.topDivider
import com.vknxworks.contactctrl.ui.nav.DetailRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import com.vknxworks.contactctrl.ui.theme.CtrlType
import com.vknxworks.contactctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Likely duplicates on the phone, grouped, with why they look like the same person. Each group can be merged
 * (joined into one contact, nothing deleted, undo right away or later from the contact's page) or marked
 * "not duplicates", which is remembered.
 */
@Composable
fun DuplicatesScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val all by c.contacts.contacts.collectAsStateWithLifecycle()
    val loaded by c.contacts.loaded.collectAsStateWithLifecycle()
    val version by c.contacts.version.collectAsStateWithLifecycle()
    val dismissed by c.merger.dismissed.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf<String?>(null) }
    var lastMerge by remember { mutableStateOf<MergeRecord?>(null) }

    val groups by produceState<List<DupGroup>?>(null, all, dismissed, loaded) {
        if (!loaded) return@produceState
        value = withContext(Dispatchers.Default) {
            val region = c.merger.region()
            DuplicateFinder.find(
                all.map { s ->
                    // A contact without a name is listed under its number or email: that isn't a name to compare.
                    val name = s.name.takeUnless { n -> s.phones.any { it == n } || s.emails.any { it == n } }.orEmpty()
                    DupContact(s.id, s.lookupKey, name, s.phones, s.emails)
                },
                region,
                dismissed,
            )
        }
    }
    val raws by produceState<Map<Long, List<RawRef>>>(emptyMap(), version) { value = c.merger.rawsByContact() }

    fun undo(record: MergeRecord) {
        scope.launch {
            val ok = c.merger.undo(record)
            Toast.makeText(context, context.getString(if (ok) R.string.separated else R.string.separate_failed), Toast.LENGTH_SHORT).show()
            if (ok) lastMerge = null
            c.contacts.refreshNow()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.duplicates_title))
        Box(Modifier.fillMaxSize()) {
            val list = groups
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
                list.isEmpty() -> MessageView(Icons.Outlined.TaskAlt, stringResource(R.string.duplicates_none), stringResource(R.string.duplicates_none_body))
                else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
                    item(key = "intro") {
                        Text(
                            pluralStringResource(R.plurals.duplicates_found, list.size, list.size) + " " + stringResource(R.string.duplicates_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = CtrlColors.Muted,
                            modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter + 4.dp, bottom = 6.dp),
                        )
                    }
                    items(list, key = { it.key }) { g ->
                        GroupCard(
                            g, raws, busy = busy != null,
                            onOpen = { m -> nav.navigate(DetailRoute(m.id, m.lookupKey)) },
                            onMerge = {
                                busy = g.key
                                scope.launch {
                                    when (val r = c.merger.merge(g.members.map { it.id }, g.members.first().name)) {
                                        is Merger.Result.Merged -> lastMerge = r.record
                                        Merger.Result.Failed -> Toast.makeText(context, context.getString(R.string.merge_failed), Toast.LENGTH_SHORT).show()
                                    }
                                    c.contacts.refreshNow()
                                    busy = null
                                }
                            },
                            onDismiss = { scope.launch { c.merger.dismiss(g) } },
                        )
                    }
                }
            }
            val m = lastMerge
            if (m != null) {
                UndoBar(
                    stringResource(R.string.merged_into, m.name),
                    onUndo = { undo(m) },
                    onClose = { lastMerge = null },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Gutter),
                )
            }
        }
    }
}

@Composable
private fun GroupCard(g: DupGroup, raws: Map<Long, List<RawRef>>, busy: Boolean, onOpen: (DupContact) -> Unit, onMerge: () -> Unit, onDismiss: () -> Unit) {
    val readOnly = g.members.flatMap { raws[it.id].orEmpty() }.filter { !it.writable }.map { Accounts.label(it.accountType) }.distinct()
    CtrlCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), padding = PaddingValues(0.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            g.reasons.take(4).forEach { r -> SectionLabel(reasonText(r), color = CtrlColors.AccentText) }
            if (g.reasons.size > 4) SectionLabel(pluralStringResource(R.plurals.reason_more, g.reasons.size - 4, g.reasons.size - 4))
        }
        g.members.forEach { m -> MemberRow(m, raws[m.id].orEmpty(), onClick = { onOpen(m) }) }
        if (readOnly.isNotEmpty()) {
            Text(
                stringResource(R.string.merge_read_only_note, readOnly.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CtrlButton(stringResource(R.string.merge), onClick = onMerge, modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.CallMerge, enabled = !busy)
            CtrlSecondaryButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 56.dp), enabled = !busy) {
                Text(stringResource(R.string.not_duplicates), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun reasonText(r: DupReason): String = when (r.kind) {
    DupReason.Kind.PHONE -> stringResource(R.string.reason_phone, r.value)
    DupReason.Kind.EMAIL -> stringResource(R.string.reason_email, r.value)
    DupReason.Kind.NAME -> stringResource(R.string.reason_name, r.value)
}

@Composable
private fun MemberRow(m: DupContact, raws: List<RawRef>, onClick: () -> Unit) {
    val context = LocalContext.current
    val accounts = raws.map { r ->
        val label = if (r.accountType == null) context.getString(R.string.account_device) else Accounts.label(r.accountType)
        if (r.writable) label else "$label (" + context.getString(R.string.read_only_short) + ")"
    }.distinct()
    Row(
        Modifier.fillMaxWidth().topDivider(CtrlColors.Outline).clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(m.name.ifBlank { m.phones.firstOrNull() ?: m.emails.firstOrNull().orEmpty() }, null, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                m.name.ifBlank { m.phones.firstOrNull() ?: m.emails.firstOrNull().orEmpty() },
                style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(m.phones.firstOrNull(), m.emails.firstOrNull()).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (accounts.isNotEmpty()) Text(accounts.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    CtrlCard(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onUndo) { Text(stringResource(R.string.undo), color = CtrlColors.AccentText) }
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = onClose, size = 36.dp, tint = CtrlColors.Muted)
        }
    }
}
