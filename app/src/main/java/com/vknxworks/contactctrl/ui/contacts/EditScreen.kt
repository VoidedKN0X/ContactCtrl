package com.vknxworks.contactctrl.ui.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Event
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.contactctrl.EditActivity
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.data.contacts.Accounts
import com.vknxworks.contactctrl.data.contacts.ContactDetail
import com.vknxworks.contactctrl.data.contacts.ContactDraft
import com.vknxworks.contactctrl.data.contacts.ContactWriter
import com.vknxworks.contactctrl.data.contacts.Field
import com.vknxworks.contactctrl.data.contacts.Kind
import com.vknxworks.contactctrl.data.contacts.NameParts
import com.vknxworks.contactctrl.data.contacts.SaveAccount
import com.vknxworks.contactctrl.ui.common.ContactAvatar
import com.vknxworks.contactctrl.ui.common.dropPhoto
import com.vknxworks.contactctrl.ui.common.formatEventDate
import com.vknxworks.contactctrl.ui.common.typeChoices
import com.vknxworks.contactctrl.ui.common.typeLabel
import com.vknxworks.contactctrl.ui.components.ChoiceSheet
import com.vknxworks.contactctrl.ui.components.ConfirmDialog
import com.vknxworks.contactctrl.ui.components.CtrlBottomSheet
import com.vknxworks.contactctrl.ui.components.CtrlSquareButton
import com.vknxworks.contactctrl.ui.components.CtrlSwitch
import com.vknxworks.contactctrl.ui.components.CtrlTextField
import com.vknxworks.contactctrl.ui.components.Group
import com.vknxworks.contactctrl.ui.components.GroupChoice
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.components.NameDialog
import com.vknxworks.contactctrl.ui.components.ScreenTopBar
import com.vknxworks.contactctrl.ui.components.SectionLabel
import com.vknxworks.contactctrl.ui.lock.AppLock
import com.vknxworks.contactctrl.ui.lock.findActivity
import com.vknxworks.contactctrl.ui.nav.DetailRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import com.vknxworks.contactctrl.ui.theme.CtrlShapes
import com.vknxworks.contactctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Adding or editing a contact. id -1 is a new contact. Rows stored by an account that doesn't sync changes back
 * (messengers) aren't shown here, so they can never be changed or removed by accident.
 */
@Composable
fun EditScreen(id: Long, key: String, prefill: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val isNew = id < 0
    var original by remember { mutableStateOf<ContactDetail?>(null) }
    var start by remember { mutableStateOf<ContactDraft?>(null) }
    var draft by remember { mutableStateOf(ContactDraft()) }
    var loadFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(listOf(SaveAccount(null, null))) }
    var account by remember { mutableStateOf(SaveAccount(null, null)) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var moreName by remember { mutableStateOf(false) }
    val editActivity = context.findActivity() as? EditActivity

    LaunchedEffect(id, key) {
        if (isNew) {
            val fromApp = if (prefill) c.prefill.also { c.prefill = null } else null
            val d = fromApp ?: ContactDraft()
            draft = d
            start = ContactDraft()
            accounts = c.contacts.saveAccounts()
            val saved = c.settings.settings.value.defaultAccount
            account = accounts.firstOrNull { "${it.type}\u0000${it.name}" == saved } ?: accounts.first()
            if (!d.name.isBlank && (d.name.prefix + d.name.middle + d.name.suffix).isNotBlank()) moreName = true
        } else {
            val o = c.contacts.detail(id, key)
            if (o == null || !o.writable) { loadFailed = true; return@LaunchedEffect }
            val writable = o.raws.filter { it.writable }.map { it.id }.toSet()
            fun List<Field>.mine() = filter { it.rawContactId in writable }
            val o2 = o.copy(phones = o.phones.mine(), emails = o.emails.mine(), addresses = o.addresses.mine(), websites = o.websites.mine(), events = o.events.mine())
            original = o2
            val d = ContactDraft.from(o2).let { base ->
                // "Add to contact" from another app: append what it sent.
                val extra = if (prefill) c.prefill.also { c.prefill = null } else null
                if (extra == null) base else base.copy(
                    phones = base.phones + extra.phones, emails = base.emails + extra.emails,
                    addresses = base.addresses + extra.addresses, websites = base.websites + extra.websites,
                    events = base.events + extra.events,
                    company = base.company.ifBlank { extra.company }, title = base.title.ifBlank { extra.title },
                    note = base.note.ifBlank { extra.note },
                )
            }
            start = ContactDraft.from(o2)
            draft = d
            if ((d.name.prefix + d.name.middle + d.name.suffix).isNotBlank()) moreName = true
        }
    }

    val changed = start != null && draft != start
    /** Cancel: back to the previous screen (or, opened by another app, back to that app). */
    fun leave() = nav.back()
    BackHandler(enabled = changed && !saving) { confirmDiscard = true }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val jpeg = withContext(Dispatchers.IO) { photoBytes(context, uri) }
            if (jpeg == null) Toast.makeText(context, context.getString(R.string.photo_failed), Toast.LENGTH_SHORT).show()
            else draft = draft.copy(newPhoto = jpeg, removePhoto = false)
        }
    }

    fun save() {
        if (saving) return
        if (draft.isEmpty) {
            Toast.makeText(context, context.getString(R.string.edit_empty), Toast.LENGTH_SHORT).show()
            return
        }
        saving = true
        scope.launch {
            val clean = draft.copy(
                phones = draft.phones.filter { it.value.isNotBlank() || it.dataId != null },
                emails = draft.emails.filter { it.value.isNotBlank() || it.dataId != null },
                addresses = draft.addresses.filter { it.value.isNotBlank() || it.dataId != null },
                websites = draft.websites.filter { it.value.isNotBlank() || it.dataId != null },
                events = draft.events.filter { it.value.isNotBlank() || it.dataId != null },
            )
            val o = original
            val result = if (o == null) c.writer.create(clean, account) else c.writer.update(o, clean)
            saving = false
            when (result) {
                is ContactWriter.Result.Saved -> {
                    o?.photoUri?.let(::dropPhoto)
                    c.contacts.refreshNow()
                    if (editActivity != null) {
                        editActivity.done(result.uri)
                    } else if (isNew) {
                        val found = c.contacts.resolve(result.uri)
                        nav.back()
                        if (found != null) nav.navigate(DetailRoute(found.first, found.second))
                    } else {
                        nav.back()
                    }
                }
                ContactWriter.Result.ReadOnly -> Toast.makeText(context, context.getString(R.string.read_only_body), Toast.LENGTH_LONG).show()
                is ContactWriter.Result.Failed -> Toast.makeText(context, context.getString(R.string.save_failed, result.error), Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = stringResource(if (isNew) R.string.new_contact else R.string.edit_contact),
            showBack = false,
            actions = {
                CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.cancel), onClick = { if (changed) confirmDiscard = true else leave() })
                CtrlSquareButton(Icons.Outlined.Check, stringResource(R.string.save), onClick = ::save, tint = CtrlColors.AccentText, enabled = !saving && start != null)
            },
        )
        when {
            loadFailed -> MessageView(Icons.Outlined.Sync, stringResource(R.string.read_only), stringResource(R.string.read_only_body))
            start == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 32.dp),
            ) {
                // Photo
                Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                    val newPhoto = draft.newPhoto
                    val bmp = remember(newPhoto) { newPhoto?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    Box(Modifier.clip(RoundedCornerShape(36.dp)).clickable(onClickLabel = stringResource(R.string.photo_change)) { photoMenu = true }) {
                        when {
                            bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.size(112.dp).clip(RoundedCornerShape(36.dp)), contentScale = ContentScale.Crop)
                            draft.removePhoto || original?.photoUri == null -> Box(
                                Modifier.size(112.dp).clip(RoundedCornerShape(36.dp)).background(CtrlColors.Raised),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(36.dp), tint = CtrlColors.Muted) }
                            else -> ContactAvatar(original?.displayName.orEmpty(), original?.photoUri, size = 112.dp)
                        }
                    }
                }

                Group(stringResource(R.string.group_name)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (moreName) NameField(R.string.field_prefix, draft.name.prefix) { draft = draft.copy(name = draft.name.copy(prefix = it)) }
                        NameField(R.string.field_given, draft.name.given) { draft = draft.copy(name = draft.name.copy(given = it)) }
                        if (moreName) NameField(R.string.field_middle, draft.name.middle) { draft = draft.copy(name = draft.name.copy(middle = it)) }
                        NameField(R.string.field_family, draft.name.family) { draft = draft.copy(name = draft.name.copy(family = it)) }
                        if (moreName) {
                            NameField(R.string.field_suffix, draft.name.suffix) { draft = draft.copy(name = draft.name.copy(suffix = it)) }
                            NameField(R.string.field_nickname, draft.nickname) { draft = draft.copy(nickname = it) }
                        }
                        TextButton(onClick = { moreName = !moreName }) {
                            Icon(if (moreName) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = CtrlColors.AccentText)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (moreName) R.string.fewer_name_fields else R.string.more_name_fields), color = CtrlColors.AccentText)
                        }
                    }
                }

                FieldList(Kind.PHONE, R.string.group_phones, R.string.add_phone, draft.phones) { draft = draft.copy(phones = it) }
                FieldList(Kind.EMAIL, R.string.group_emails, R.string.add_email, draft.emails) { draft = draft.copy(emails = it) }
                FieldList(Kind.ADDRESS, R.string.group_addresses, R.string.add_address, draft.addresses) { draft = draft.copy(addresses = it) }

                Group(stringResource(R.string.field_work)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NameField(R.string.field_company, draft.company) { draft = draft.copy(company = it) }
                        NameField(R.string.field_title, draft.title) { draft = draft.copy(title = it) }
                    }
                }

                FieldList(Kind.EVENT, R.string.group_dates, R.string.add_date, draft.events) { draft = draft.copy(events = it) }
                FieldList(Kind.WEBSITE, R.string.group_websites, R.string.add_website, draft.websites) { draft = draft.copy(websites = it) }

                Group(stringResource(R.string.field_note)) {
                    CtrlTextField(
                        value = draft.note,
                        onValueChange = { draft = draft.copy(note = it.take(10_000)) },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    )
                }

                if (isNew && accounts.size > 1) {
                    Group(stringResource(R.string.save_to), footer = stringResource(R.string.save_to_footer)) {
                        GroupChoice(
                            stringResource(R.string.save_to),
                            accounts.map { it to accountName(context, it) },
                            account,
                            icon = if (account.isDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.Sync,
                        ) { account = it }
                    }
                }
            }
        }
    }

    if (photoMenu) {
        CtrlBottomSheet(onDismissRequest = { photoMenu = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                SheetAction(Icons.Outlined.Image, stringResource(R.string.photo_choose)) {
                    photoMenu = false
                    AppLock.expectReturn()
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (draft.newPhoto != null || (original?.photoUri != null && !draft.removePhoto)) {
                    SheetAction(Icons.Outlined.Delete, stringResource(R.string.photo_remove), danger = true) {
                        photoMenu = false
                        draft = draft.copy(newPhoto = null, removePhoto = original?.photoUri != null)
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            body = stringResource(R.string.discard_body),
            confirm = stringResource(R.string.discard),
            onDismiss = { confirmDiscard = false },
            onConfirm = { confirmDiscard = false; leave() },
        )
    }
}

private fun accountName(context: Context, a: SaveAccount): String =
    if (a.isDevice) context.getString(R.string.account_device) else "${Accounts.label(a.type)} · ${a.name.orEmpty()}"

@Composable
private fun NameField(label: Int, value: String, onChange: (String) -> Unit) {
    CtrlTextField(
        value = value,
        onValueChange = { onChange(it.take(200)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A list of phone numbers, emails, addresses, dates or websites, each with its type. */
@Composable
private fun FieldList(kind: Kind, title: Int, addLabel: Int, fields: List<Field>, onChange: (List<Field>) -> Unit) {
    val context = LocalContext.current
    var typeFor by remember { mutableStateOf<Int?>(null) }
    var customFor by remember { mutableStateOf<Int?>(null) }
    var dateFor by remember { mutableStateOf<Int?>(null) }
    fun set(i: Int, f: Field) = onChange(fields.toMutableList().also { it[i] = f })

    Group(stringResource(title)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fields.forEachIndexed { i, f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { typeFor = i }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                            SectionLabel(typeLabel(context, kind, f.type, f.label), color = CtrlColors.AccentText)
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = CtrlColors.AccentText)
                        }
                        if (kind == Kind.EVENT) {
                            Box(
                                Modifier.fillMaxWidth().clip(CtrlShapes.Field).background(CtrlColors.Surface)
                                    .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Field)
                                    .clickable { dateFor = i }.padding(16.dp),
                            ) {
                                Text(if (f.value.isBlank()) stringResource(R.string.pick_date) else formatEventDate(f.value), color = if (f.value.isBlank()) CtrlColors.Muted else CtrlColors.Text)
                            }
                        } else {
                            CtrlTextField(
                                value = f.value,
                                onValueChange = { v -> set(i, f.copy(value = v.take(if (kind == Kind.ADDRESS) 1000 else 300))) },
                                singleLine = kind != Kind.ADDRESS,
                                minLines = if (kind == Kind.ADDRESS) 2 else 1,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = when (kind) {
                                        Kind.PHONE -> KeyboardType.Phone
                                        Kind.EMAIL -> KeyboardType.Email
                                        Kind.WEBSITE -> KeyboardType.Uri
                                        else -> KeyboardType.Text
                                    },
                                    capitalization = if (kind == Kind.ADDRESS) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    CtrlSquareButton(Icons.Outlined.Delete, stringResource(R.string.remove), onClick = { onChange(fields.filterIndexed { j, _ -> j != i }) }, size = 40.dp, tint = CtrlColors.Muted)
                }
            }
            TextButton(onClick = {
                onChange(fields + Field(kind, "", typeChoices(kind).first { it != 0 && !(kind == Kind.EVENT && it == Event.TYPE_BIRTHDAY && fields.any { f -> f.type == Event.TYPE_BIRTHDAY }) }))
                if (kind == Kind.EVENT) dateFor = fields.size
            }) {
                Text("+ " + stringResource(addLabel), color = CtrlColors.AccentText)
            }
        }
    }

    typeFor?.let { i ->
        val f = fields.getOrNull(i)
        if (f == null) { typeFor = null; return@let }
        ChoiceSheet(
            stringResource(R.string.label_type),
            typeChoices(kind).map { t -> t to if (t == 0) stringResource(R.string.type_custom) else typeLabel(context, kind, t, null) },
            f.type,
            onDismiss = { typeFor = null },
        ) { t ->
            typeFor = null
            if (t == 0) customFor = i else set(i, f.copy(type = t, label = null))
        }
    }
    customFor?.let { i ->
        val f = fields.getOrNull(i)
        if (f == null) { customFor = null; return@let }
        NameDialog(stringResource(R.string.type_custom), f.label.orEmpty(), onDismiss = { customFor = null }) { label ->
            customFor = null
            set(i, f.copy(type = 0, label = label))
        }
    }
    dateFor?.let { i ->
        val f = fields.getOrNull(i)
        if (f == null) { dateFor = null; return@let }
        EventDateDialog(f.value, onDismiss = { dateFor = null }) { v ->
            dateFor = null
            set(i, f.copy(value = v))
        }
    }
}

/** Picks a date; "Year unknown" saves it as --MM-DD, the way Android stores birthdays without a year. */
@Composable
private fun EventDateDialog(value: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val noYearAtStart = value.startsWith("--")
    val initial = runCatching {
        val d = if (noYearAtStart) LocalDate.parse("2000-" + value.drop(2).take(5)) else LocalDate.parse(value.take(10))
        d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    var noYear by remember { mutableStateOf(noYearAtStart) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                val ms = state.selectedDateMillis ?: return@TextButton
                val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                onPick(if (noYear) "--%02d-%02d".format(d.monthValue, d.dayOfMonth) else d.toString())
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = CtrlColors.Text) } },
        colors = DatePickerDefaults.colors(containerColor = CtrlColors.Surface),
    ) {
        DatePicker(state = state, showModeToggle = true, colors = DatePickerDefaults.colors(containerColor = CtrlColors.Surface))
        Row(
            Modifier.fillMaxWidth().clickable { noYear = !noYear }.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.year_unknown), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            CtrlSwitch(noYear, onCheckedChange = null)
        }
    }
}

/** The chosen picture as a square JPEG of at most 720 px, small enough for every contacts account. */
private fun photoBytes(context: Context, uri: Uri): ByteArray? = runCatching {
    val src = ImageDecoder.createSource(context.contentResolver, uri)
    val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > 2000) {
            val scale = 2000f / longest
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
    }
    val side = minOf(bmp.width, bmp.height)
    val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
    val out = if (side > 720) Bitmap.createScaledBitmap(square, 720, 720, true) else square
    ByteArrayOutputStream().use { s ->
        out.compress(Bitmap.CompressFormat.JPEG, 88, s)
        s.toByteArray()
    }
}.getOrNull()
