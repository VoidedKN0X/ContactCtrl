package com.vknxworks.contactctrl.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.ContactSummary
import com.vknxworks.contactctrl.data.settings.AppSettings
import com.vknxworks.contactctrl.data.settings.NameOrder
import com.vknxworks.contactctrl.data.settings.SortBy
import com.vknxworks.contactctrl.ui.common.ContactAvatar
import com.vknxworks.contactctrl.ui.common.EmptyContacts
import com.vknxworks.contactctrl.ui.components.ConfirmDialog
import com.vknxworks.contactctrl.ui.components.CtrlSquareButton
import com.vknxworks.contactctrl.ui.components.CtrlTextField
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.components.SectionLabel
import com.vknxworks.contactctrl.ui.components.TabTitle
import com.vknxworks.contactctrl.ui.components.bottomClearance
import com.vknxworks.contactctrl.ui.components.topDivider
import com.vknxworks.contactctrl.ui.nav.DetailRoute
import com.vknxworks.contactctrl.ui.nav.EditRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import com.vknxworks.contactctrl.ui.theme.CtrlType
import com.vknxworks.contactctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** The name as the settings want it shown. */
fun ContactSummary.shown(s: AppSettings): String = if (s.nameOrder == NameOrder.LAST_FIRST) altName else name

fun List<ContactSummary>.sortedFor(s: AppSettings): List<ContactSummary> {
    val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
    return sortedWith { a, b ->
        val ka = if (s.sortBy == SortBy.LAST_NAME) a.altSortKey else a.sortKey
        val kb = if (s.sortBy == SortBy.LAST_NAME) b.altSortKey else b.sortKey
        val letterA = ka.firstOrNull()?.isLetter() == true
        val letterB = kb.firstOrNull()?.isLetter() == true
        // Names that start with a letter first, then numbers and symbols ("#").
        if (letterA != letterB) (if (letterA) -1 else 1) else collator.compare(ka, kb)
    }
}

/** The letter a contact is listed under. */
fun ContactSummary.section(s: AppSettings): String {
    val key = if (s.sortBy == SortBy.LAST_NAME) altSortKey else sortKey
    val ch = key.firstOrNull() ?: return "#"
    if (!ch.isLetter()) return "#"
    return ContactSummary.fold(ch.toString()).uppercase().take(1)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val all by c.contacts.contacts.collectAsStateWithLifecycle()
    val loaded by c.contacts.loaded.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sorted = remember(all, settings.sortBy) { all.sortedFor(settings) }
    val shown = remember(sorted, query) { if (query.isBlank()) sorted else sorted.filter { it.matches(query) } }
    val selecting = selected.isNotEmpty()
    val listState = rememberLazyListState()

    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && query.isNotEmpty()) { query = "" }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onStar = {
                    val chosen = all.filter { it.id in selected }
                    val star = chosen.any { !it.starred }
                    scope.launch { chosen.forEach { c.writer.setStarred(it.id, it.lookupKey, star) } }
                    selected = emptySet()
                },
                onShare = {
                    val chosen = all.filter { it.id in selected }
                    scope.launch { shareContacts(context, chosen.map { it.lookupKey }, chosen.singleOrNull()?.name) }
                    selected = emptySet()
                },
                onDelete = { confirmDelete = true },
            )
        } else {
            TabTitle(
                stringResource(R.string.tab_contacts),
                label = if (loaded) pluralStringResource(R.plurals.contact_count, all.size, all.size) else null,
                trailing = {
                    CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.add_contact), onClick = { nav.navigate(EditRoute()) })
                },
            )
        }
        SearchField(query, onChange = { query = it })
        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            all.isEmpty() -> EmptyContacts(Modifier.bottomClearance())
            shown.isEmpty() -> MessageView(Icons.Outlined.Search, stringResource(R.string.search_none, query.trim()), modifier = Modifier.bottomClearance())
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Favourites first when not searching, like a phone book's front page.
                val favourites = if (query.isBlank()) shown.filter { it.starred } else emptyList()
                if (favourites.isNotEmpty()) {
                    stickyHeader(key = "h-fav") { LetterHeader(stringResource(R.string.tab_favourites)) }
                    items(favourites, key = { "f${it.id}" }) { contact ->
                        ContactRow(contact, settings, selected = contact.id in selected, selecting = selecting,
                            onClick = { if (selecting) selected = selected.toggle(contact.id) else nav.navigate(DetailRoute(contact.id, contact.lookupKey)) },
                            onLongClick = { selected = selected.toggle(contact.id) })
                    }
                }
                val groups = shown.groupBy { it.section(settings) }
                for ((letter, group) in groups) {
                    stickyHeader(key = "h-$letter") { LetterHeader(letter) }
                    items(group, key = { it.id }) { contact ->
                        ContactRow(contact, settings, selected = contact.id in selected, selecting = selecting,
                            onClick = { if (selecting) selected = selected.toggle(contact.id) else nav.navigate(DetailRoute(contact.id, contact.lookupKey)) },
                            onLongClick = { selected = selected.toggle(contact.id) })
                    }
                }
                item { Spacer(Modifier.bottomClearance(0.dp)) }
            }
        }
    }
    if (confirmDelete) {
        val n = selected.size
        ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_many_title, n, n),
            body = stringResource(R.string.delete_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                val chosen = all.filter { it.id in selected }
                scope.launch { c.writer.deleteMany(chosen) }
                selected = emptySet()
                confirmDelete = false
            },
        )
    }
}

@Composable
fun FavouritesScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val all by c.contacts.contacts.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val favourites = remember(all, settings.sortBy) { all.filter { it.starred }.sortedFor(settings) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_favourites))
        if (favourites.isEmpty()) {
            MessageView(Icons.Outlined.StarBorder, stringResource(R.string.fav_empty), stringResource(R.string.fav_empty_body), modifier = Modifier.bottomClearance())
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(favourites, key = { it.id }) { contact ->
                    ContactRow(contact, settings, selected = false, selecting = false,
                        onClick = { nav.navigate(DetailRoute(contact.id, contact.lookupKey)) }, onLongClick = null)
                }
                item { Spacer(Modifier.bottomClearance(0.dp)) }
            }
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
fun SearchField(query: String, onChange: (String) -> Unit) {
    CtrlTextField(
        value = query,
        onValueChange = { onChange(it.take(100)) },
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.clear), onClick = { onChange("") }, size = 36.dp, tint = CtrlColors.Muted)
        }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
    )
}

@Composable
fun LetterHeader(text: String) {
    Box(Modifier.fillMaxWidth().background(CtrlColors.Background).padding(horizontal = Gutter, vertical = 8.dp)) {
        SectionLabel(text, color = CtrlColors.AccentText)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactRow(
    contact: ContactSummary,
    settings: AppSettings,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    subtitle: String? = if (settings.showNumbers) contact.phones.firstOrNull() else null,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) CtrlColors.Raised else CtrlColors.Background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { lc -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); lc() } },
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting && selected) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CheckCircle, stringResource(R.string.selected), Modifier.size(30.dp), tint = CtrlColors.AccentText)
            }
        } else {
            ContactAvatar(contact.shown(settings), contact.photoUri)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(contact.shown(settings), style = MaterialTheme.typography.bodyLarge, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (contact.starred && !selecting) Icon(Icons.Outlined.Star, null, Modifier.size(18.dp), tint = CtrlColors.Muted)
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onStar: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).topDivider(CtrlColors.Background).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.cancel), onClick = onClose)
        Text(pluralStringResource(R.plurals.selected_count, count, count), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 6.dp))
        CtrlSquareButton(Icons.Outlined.Star, stringResource(R.string.favourite_toggle), onClick = onStar)
        CtrlSquareButton(Icons.Outlined.Share, stringResource(R.string.share), onClick = onShare)
        CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = onDelete, tint = CtrlColors.Error)
    }
}
