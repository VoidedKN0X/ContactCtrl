package com.vknxworks.contactctrl.ui.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.nav.EditRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator

/** "Add to contacts" from another app: add the details to an existing contact, or make a new one. */
@Composable
fun AddToScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val all by c.contacts.contacts.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val sorted = remember(all, settings.sortBy) { all.sortedFor(settings) }
    val shown = remember(sorted, query) { if (query.isBlank()) sorted else sorted.filter { it.matches(query) } }
    // Keep what the other app sent for whichever edit screen opens next.
    val sent = remember { c.prefill }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.add_to_title))
        SearchField(query) { query = it }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Group {
                    GroupRow(stringResource(R.string.new_contact), icon = Icons.Outlined.PersonAdd, showChevron = true, onClick = {
                        c.prefill = sent
                        nav.navigate(EditRoute(prefill = true))
                    })
                }
            }
            items(shown, key = { it.id }) { contact ->
                ContactRow(contact, settings, selected = false, selecting = false, onClick = {
                    c.prefill = sent
                    nav.navigate(EditRoute(contact.id, contact.lookupKey, prefill = true))
                }, onLongClick = null)
            }
        }
    }
}
