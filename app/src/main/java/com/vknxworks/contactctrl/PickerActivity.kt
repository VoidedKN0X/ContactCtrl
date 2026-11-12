package com.vknxworks.contactctrl

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Contacts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.contactctrl.data.contacts.ContactSummary
import com.vknxworks.contactctrl.data.contacts.Kind
import com.vknxworks.contactctrl.ui.common.ContactAvatar
import com.vknxworks.contactctrl.ui.common.ContactsPermissionGate
import com.vknxworks.contactctrl.ui.common.typeLabel
import com.vknxworks.contactctrl.ui.components.CtrlSquareButton
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.contacts.SearchField
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import com.vknxworks.contactctrl.ui.theme.CtrlType
import com.vknxworks.contactctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The contact picker other apps open ("choose a contact", "choose a phone number"). Only the one row you tap
 * is shared with that app, for that one time.
 */
class PickerActivity : ComponentActivity() {

    enum class Mode { CONTACT, PHONE, EMAIL, POSTAL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = modeOf(intent)
        setContent {
            CtrlWindow(this) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ContactsPermissionGate(onGranted = {}) {
                        PickerScreen(mode, onCancel = { setResult(RESULT_CANCELED); finish() }) { uri ->
                            setResult(RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun modeOf(intent: Intent): Mode {
        val t = (intent.type ?: intent.data?.let { runCatching { contentResolver.getType(it) }.getOrNull() } ?: "").lowercase()
        val path = intent.data?.toString().orEmpty()
        return when {
            t.endsWith("/phone_v2") || t.endsWith("/phone") || path.startsWith(Phone.CONTENT_URI.toString()) -> Mode.PHONE
            t.endsWith("/email_v2") || path.startsWith(Email.CONTENT_URI.toString()) -> Mode.EMAIL
            t.endsWith("/postal-address_v2") || path.startsWith(StructuredPostal.CONTENT_URI.toString()) -> Mode.POSTAL
            else -> Mode.CONTACT
        }
    }
}

private data class PickRow(val uri: Uri, val name: String, val detail: String?, val photo: String?)

@Composable
private fun PickerScreen(mode: PickerActivity.Mode, onCancel: () -> Unit, onPick: (Uri) -> Unit) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<PickRow>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(mode) {
        rows = withContext(Dispatchers.IO) { runCatching { load(context, mode) }.getOrDefault(emptyList()) }
    }
    val shown = remember(rows, query) {
        val all = rows.orEmpty()
        if (query.isBlank()) all else {
            val q = ContactSummary.fold(query.trim())
            val qd = ContactSummary.digits(query)
            all.filter { r ->
                ContactSummary.fold(r.name).contains(q) || (r.detail?.lowercase()?.contains(q) == true) ||
                    (qd.length >= 3 && ContactSummary.digits(r.detail.orEmpty()).contains(qd))
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.cancel), onClick = onCancel)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(
                    when (mode) {
                        PickerActivity.Mode.PHONE -> R.string.pick_phone
                        PickerActivity.Mode.EMAIL -> R.string.pick_email
                        PickerActivity.Mode.POSTAL -> R.string.pick_address
                        PickerActivity.Mode.CONTACT -> R.string.pick_contact
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        SearchField(query) { query = it }
        val list = rows
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            shown.isEmpty() -> MessageView(Icons.Outlined.Search, stringResource(if (list.isEmpty()) R.string.empty_title else R.string.search_none, query.trim()))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(shown, key = { it.uri.toString() }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(r.uri) }.heightIn(min = 64.dp).padding(horizontal = Gutter, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContactAvatar(r.name, r.photo)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!r.detail.isNullOrBlank()) Text(r.detail, style = CtrlType.Data, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun load(context: android.content.Context, mode: PickerActivity.Mode): List<PickRow> {
    val r = context.contentResolver
    val out = ArrayList<PickRow>()
    val collator = java.text.Collator.getInstance()
    when (mode) {
        PickerActivity.Mode.CONTACT -> r.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI),
            null, null, Contacts.SORT_KEY_PRIMARY,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(2) ?: continue
                out += PickRow(Contacts.getLookupUri(c.getLong(0), c.getString(1) ?: continue), name, null, c.getString(3))
            }
        }
        else -> {
            val (uri, kind) = when (mode) {
                PickerActivity.Mode.PHONE -> Phone.CONTENT_URI to Kind.PHONE
                PickerActivity.Mode.EMAIL -> Email.CONTENT_URI to Kind.EMAIL
                else -> StructuredPostal.CONTENT_URI to Kind.ADDRESS
            }
            r.query(
                uri,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME_PRIMARY, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3, ContactsContract.Data.PHOTO_THUMBNAIL_URI),
                null, null, ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            )?.use { c ->
                while (c.moveToNext()) {
                    val value = c.getString(2) ?: continue
                    val label = typeLabel(context, kind, c.getInt(3), c.getString(4))
                    out += PickRow(ContentUris.withAppendedId(uri, c.getLong(0)), c.getString(1) ?: value, "$value · $label", c.getString(5))
                }
            }
        }
    }
    return out.sortedWith { a, b -> collator.compare(a.name, b.name) }
}
