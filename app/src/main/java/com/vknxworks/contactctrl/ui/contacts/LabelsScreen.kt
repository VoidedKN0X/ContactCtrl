package com.vknxworks.contactctrl.ui.contacts

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.ContactGroup
import com.vknxworks.contactctrl.data.contacts.SaveAccount
import com.vknxworks.contactctrl.data.contacts.accountKey
import com.vknxworks.contactctrl.ui.components.ChoiceSheet
import com.vknxworks.contactctrl.ui.components.ConfirmDialog
import com.vknxworks.contactctrl.ui.components.CtrlSquareButton
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupRow
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.components.NameDialog
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import kotlinx.coroutines.launch

/**
 * Labels (Android's contact groups), per account: add one, rename it, delete it. Deleting a label never
 * deletes the contacts in it. Labels the account made itself are listed but can't be changed.
 */
@Composable
fun LabelsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val groups by c.contacts.groups.collectAsStateWithLifecycle()
    val all by c.contacts.contacts.collectAsStateWithLifecycle()
    var accounts by remember { mutableStateOf(listOf(SaveAccount(null, null))) }
    var pickAccount by remember { mutableStateOf(false) }
    var newIn by remember { mutableStateOf<SaveAccount?>(null) }
    var renaming by remember { mutableStateOf<ContactGroup?>(null) }
    var deleting by remember { mutableStateOf<ContactGroup?>(null) }
    LaunchedEffect(Unit) { accounts = c.contacts.saveAccounts() }

    fun done(ok: Boolean) {
        if (!ok) Toast.makeText(context, context.getString(R.string.labels_failed), Toast.LENGTH_SHORT).show()
        c.contacts.refreshNow()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.labels_title), actions = {
            CtrlSquareButton(Icons.Outlined.Add, stringResource(R.string.labels_new), onClick = {
                if (accounts.size == 1) newIn = accounts.first() else pickAccount = true
            })
        })
        if (groups.isEmpty()) {
            MessageView(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.labels_empty), stringResource(R.string.labels_empty_body))
        } else {
            val byAccount = remember(groups) { groups.groupBy { it.accountKey } }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                for ((key, list) in byAccount) {
                    Group(accountTitle(context, key)) {
                        list.forEach { g ->
                            val n = all.count { g.id in it.groups }
                            GroupRow(
                                g.title,
                                pluralStringResource(R.plurals.labels_members, n, n) + if (!g.editable) " · " + stringResource(R.string.labels_fixed) else "",
                                icon = Icons.AutoMirrored.Outlined.Label,
                                trailing = if (g.editable) ({
                                    androidx.compose.foundation.layout.Row {
                                        CtrlSquareButton(Icons.Outlined.Edit, stringResource(R.string.labels_rename), onClick = { renaming = g }, size = 40.dp)
                                        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                                        CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.delete), onClick = { deleting = g }, size = 40.dp, tint = CtrlColors.Error)
                                    }
                                }) else null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickAccount) {
        ChoiceSheet(
            stringResource(R.string.labels_new_in),
            accounts.map { it to accountTitle(context, accountKey(it.type, it.name)) },
            null,
            onDismiss = { pickAccount = false },
        ) { a -> pickAccount = false; newIn = a }
    }
    newIn?.let { a ->
        NameDialog(stringResource(R.string.labels_new), "", confirm = stringResource(R.string.labels_add), onDismiss = { newIn = null }) { title ->
            newIn = null
            scope.launch { done(c.contacts.groupsStore.create(title, a.type, a.name)) }
        }
    }
    renaming?.let { g ->
        NameDialog(stringResource(R.string.labels_rename), g.title, onDismiss = { renaming = null }) { title ->
            renaming = null
            scope.launch { done(c.contacts.groupsStore.rename(g, title)) }
        }
    }
    deleting?.let { g ->
        ConfirmDialog(
            title = stringResource(R.string.labels_delete_title, g.title),
            body = stringResource(R.string.labels_delete_body),
            confirm = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                scope.launch { done(c.contacts.groupsStore.delete(g)) }
            },
        )
    }
}
