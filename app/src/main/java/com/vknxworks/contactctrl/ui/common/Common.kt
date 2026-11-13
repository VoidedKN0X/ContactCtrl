package com.vknxworks.contactctrl.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.Settings
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.data.contacts.Kind
import com.vknxworks.contactctrl.ui.components.MessageView
import com.vknxworks.contactctrl.ui.lock.AppLock
import com.vknxworks.contactctrl.ui.theme.CtrlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Context.hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/**
 * Shows [content] once the contacts permissions are granted; until then, a screen that explains why and asks.
 * After Android stops showing the question (denied twice), the button opens the app's settings instead.
 */
@Composable
fun ContactsPermissionGate(onGranted: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val perms = arrayOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS)
    var granted by remember { mutableStateOf(perms.all { context.hasPermission(it) }) }
    var asked by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        asked++
        granted = perms.all { context.hasPermission(it) }
        if (granted) onGranted()
    }
    if (granted) {
        content()
        return
    }
    val activity = context as? Activity
    val blocked = asked > 0 && activity != null && perms.none { activity.shouldShowRequestPermissionRationale(it) } && !granted
    MessageView(
        icon = Icons.Outlined.Contacts,
        title = stringResource(R.string.perm_title),
        body = stringResource(if (blocked) R.string.perm_body_settings else R.string.perm_body),
        actionLabel = stringResource(if (blocked) R.string.perm_open_settings else R.string.perm_allow),
        onAction = {
            if (blocked) {
                openAppSettings(context)
            } else {
                AppLock.expectReturn()
                launcher.launch(perms)
            }
        },
    )
}

fun openAppSettings(context: Context) {
    AppLock.expectReturn()
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Starts another app; that trip doesn't lock ContactCtrl. False when no app can do it. */
fun Context.launch(intent: Intent): Boolean = try {
    AppLock.expectReturn()
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Small cache of decoded contact photos, so scrolling the list doesn't decode the same picture again. */
private object PhotoCache {
    val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
}

@Composable
fun rememberPhoto(uri: String?, key: Int = 0): Bitmap? {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(initialValue = uri?.let { PhotoCache.cache.get(it) }, uri, key) {
        if (uri == null) { value = null; return@produceState }
        PhotoCache.cache.get(uri)?.let { if (key == 0) { value = it; return@produceState } }
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }?.also { PhotoCache.cache.put(uri, it) }
    }
    return photo
}

fun dropPhoto(uri: String?) {
    if (uri != null) PhotoCache.cache.remove(uri)
}

/** A contact's picture, or their initials on a plain square. */
@Composable
fun ContactAvatar(name: String, photoUri: String?, size: Dp = 44.dp, modifier: Modifier = Modifier, photoKey: Int = 0) {
    val photo = rememberPhoto(photoUri, photoKey)
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier.size(size).clip(shape).background(CtrlColors.Raised).border(1.dp, CtrlColors.Outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(
                initials(name),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold),
                color = CtrlColors.Text,
            )
        }
    }
}

fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it[0].isLetterOrDigit() }
    return when {
        words.isEmpty() -> name.trim().take(1).uppercase().ifEmpty { "#" }
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/** The label Android uses for a phone, email or address type ("Mobile", "Work", or the custom label). */
fun typeLabel(context: Context, kind: Kind, type: Int, label: String?): String = when (kind) {
    Kind.PHONE -> Phone.getTypeLabel(context.resources, type, label).toString()
    Kind.EMAIL -> Email.getTypeLabel(context.resources, type, label).toString()
    Kind.ADDRESS -> StructuredPostal.getTypeLabel(context.resources, type, label).toString()
    Kind.EVENT -> if (type == Event.TYPE_CUSTOM && !label.isNullOrBlank()) label else context.getString(Event.getTypeResource(type))
    Kind.WEBSITE -> when (type) {
        Website.TYPE_HOMEPAGE -> context.getString(R.string.web_homepage)
        Website.TYPE_BLOG -> context.getString(R.string.web_blog)
        Website.TYPE_WORK -> context.getString(R.string.web_work)
        Website.TYPE_HOME -> context.getString(R.string.web_home)
        Website.TYPE_CUSTOM -> label?.ifBlank { null } ?: context.getString(R.string.web_other)
        else -> context.getString(R.string.web_other)
    }
}

/** Types offered when adding or editing a field. 0 is "custom". */
fun typeChoices(kind: Kind): List<Int> = when (kind) {
    Kind.PHONE -> listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN, Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME, Phone.TYPE_PAGER, Phone.TYPE_OTHER, 0)
    Kind.EMAIL -> listOf(Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_MOBILE, Email.TYPE_OTHER, 0)
    Kind.ADDRESS -> listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER, 0)
    Kind.WEBSITE -> listOf(Website.TYPE_HOMEPAGE, Website.TYPE_WORK, Website.TYPE_BLOG, Website.TYPE_HOME, Website.TYPE_OTHER)
    Kind.EVENT -> listOf(Event.TYPE_BIRTHDAY, Event.TYPE_ANNIVERSARY, Event.TYPE_OTHER, 0)
}

fun defaultType(kind: Kind): Int = typeChoices(kind).first()

/** "1990-05-20" or "--05-20" as a date in the phone's language. */
fun formatEventDate(value: String): String = runCatching {
    val locale = java.util.Locale.getDefault()
    if (value.startsWith("--")) {
        val md = java.time.MonthDay.parse(value.take(7))
        md.format(java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale))
    } else {
        val d = java.time.LocalDate.parse(value.take(10))
        d.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale))
    }
}.getOrDefault(value)

@Composable
fun EmptyContacts(modifier: Modifier = Modifier) = MessageView(
    icon = Icons.Outlined.Contacts,
    title = stringResource(R.string.empty_title),
    body = stringResource(R.string.empty_body),
    modifier = modifier,
)
