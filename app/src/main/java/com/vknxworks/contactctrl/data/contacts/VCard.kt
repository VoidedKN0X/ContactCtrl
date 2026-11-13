package com.vknxworks.contactctrl.data.contacts

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Reading and writing .vcf files (vCard 2.1, 3.0 and 4.0), so contacts can move between phones without any
 * account or cloud.
 */
object VCard {
    /** Biggest file ContactCtrl reads: far beyond any real address book, and protects against huge files. */
    private const val MAX_BYTES = 64L * 1024 * 1024

    suspend fun read(resolver: ContentResolver, uri: Uri): List<ContactDraft> = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) error("File too big")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: error("Can't open the file")
        parse(String(bytes, Charsets.UTF_8))
    }

    fun parse(text: String): List<ContactDraft> {
        val lines = unfold(text)
        val out = ArrayList<ContactDraft>()
        var card: Builder? = null
        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper.startsWith("BEGIN:VCARD") -> card = Builder()
                upper.startsWith("END:VCARD") -> { card?.build()?.let { if (!it.isEmpty) out += it }; card = null }
                card != null -> card.add(line)
            }
        }
        return out
    }

    /**
     * Joins folded lines (a line starting with a space or tab continues the previous one; 2.1 also folds after "=").
     * Each line is built up in place, so a file with thousands of tiny continuation lines reads as fast as any other.
     */
    private fun unfold(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        var cur: StringBuilder? = null
        // Whether the current line mentions QUOTED-PRINTABLE so far (it only grows, so once true it stays true).
        var qp = false
        fun flush() {
            cur?.let { out += it.toString() }
            cur = null
        }
        for (l in raw) {
            val c = cur
            when {
                (l.startsWith(" ") || l.startsWith("\t")) && c != null -> {
                    val from = c.length
                    c.append(l, 1, l.length)
                    if (!qp) qp = mentionsQp(c, maxOf(0, from - QP.length))
                }
                c != null && c.endsWith("=") && qp -> {
                    c.setLength(c.length - 1)
                    c.append(l)
                }
                l.isNotEmpty() -> {
                    flush()
                    cur = StringBuilder(l)
                    qp = mentionsQp(l, 0)
                }
            }
        }
        flush()
        return out
    }

    private const val QP = "QUOTED-PRINTABLE"

    /** Whether [s] contains QUOTED-PRINTABLE (any case) starting at or after [from]. */
    private fun mentionsQp(s: CharSequence, from: Int): Boolean {
        var i = from
        while (i + QP.length <= s.length) {
            var k = 0
            while (k < QP.length && s[i + k].equals(QP[k], ignoreCase = true)) k++
            if (k == QP.length) return true
            i++
        }
        return false
    }

    private class Builder {
        var fn = ""
        var name = NameParts()
        var nickname = ""
        var company = ""
        var title = ""
        var note = ""
        val phones = ArrayList<Field>()
        val emails = ArrayList<Field>()
        val addresses = ArrayList<Field>()
        val websites = ArrayList<Field>()
        val events = ArrayList<Field>()
        var photo: ByteArray? = null

        fun add(line: String) {
            val colon = indexOfUnquoted(line, ':')
            if (colon < 0) return
            val head = line.substring(0, colon)
            var value = line.substring(colon + 1)
            val parts = head.split(';')
            // "item1.TEL" (Apple groups): drop the group.
            val prop = parts[0].substringAfterLast('.').uppercase()
            val params = parts.drop(1).map { it.uppercase() }
            val types = params.flatMap { p ->
                when {
                    p.startsWith("TYPE=") -> p.removePrefix("TYPE=").trim('"').split(',')
                    !p.contains('=') -> listOf(p)
                    else -> emptyList()
                }
            }.map { it.trim() }
            val qp = params.any { it == "ENCODING=QUOTED-PRINTABLE" || it == "QUOTED-PRINTABLE" }
            val b64 = params.any { it == "ENCODING=B" || it == "ENCODING=BASE64" || it == "BASE64" }
            val charset = params.firstOrNull { it.startsWith("CHARSET=") }?.removePrefix("CHARSET=")
            if (qp) value = decodeQp(value, charset)
            when (prop) {
                "FN" -> fn = unescape(value)
                "N" -> {
                    val n = splitValues(value)
                    name = NameParts(prefix = n.getOrElse(3) { "" }, given = n.getOrElse(1) { "" }, middle = n.getOrElse(2) { "" }, family = n.getOrElse(0) { "" }, suffix = n.getOrElse(4) { "" })
                }
                "NICKNAME" -> nickname = unescape(value)
                "ORG" -> company = splitValues(value).filter { it.isNotBlank() }.joinToString(", ")
                "TITLE" -> title = unescape(value)
                "NOTE" -> note = unescape(value)
                "TEL" -> {
                    val v = unescape(value).removePrefix("tel:").trim()
                    if (v.isNotBlank()) phones += Field(Kind.PHONE, v, phoneType(types), primary = "PREF" in types)
                }
                "EMAIL" -> {
                    val v = unescape(value).removePrefix("mailto:").trim()
                    if (v.isNotBlank()) emails += Field(Kind.EMAIL, v, emailType(types), primary = "PREF" in types)
                }
                "ADR" -> {
                    // PO box; extended; street; city; region; postcode; country
                    val a = splitValues(value)
                    val street = listOf(a.getOrElse(0) { "" }, a.getOrElse(1) { "" }, a.getOrElse(2) { "" }).filter { it.isNotBlank() }.joinToString(", ")
                    val cityLine = listOf(a.getOrElse(5) { "" }, a.getOrElse(3) { "" }).filter { it.isNotBlank() }.joinToString(" ")
                    val text = listOf(street, cityLine, a.getOrElse(4) { "" }, a.getOrElse(6) { "" }).filter { it.isNotBlank() }.joinToString("\n")
                    if (text.isNotBlank()) addresses += Field(Kind.ADDRESS, text, addressType(types))
                }
                "URL" -> unescape(value).trim().takeIf { it.isNotBlank() }?.let { websites += Field(Kind.WEBSITE, it, Website.TYPE_OTHER) }
                "BDAY" -> date(value)?.let { events += Field(Kind.EVENT, it, Event.TYPE_BIRTHDAY) }
                "ANNIVERSARY", "X-ANNIVERSARY" -> date(value)?.let { events += Field(Kind.EVENT, it, Event.TYPE_ANNIVERSARY) }
                "PHOTO" -> if (photo == null) {
                    val data = when {
                        value.startsWith("data:") -> value.substringAfter("base64,", "")
                        b64 -> value
                        else -> ""
                    }
                    if (data.isNotBlank()) photo = runCatching { Base64.decode(data.trim(), Base64.DEFAULT) }.getOrNull()?.takeIf { it.size < 4 * 1024 * 1024 }?.let(::safePhoto)
                }
            }
        }

        fun build(): ContactDraft {
            val n = if (name.isBlank && fn.isNotBlank()) NameParts(given = fn) else name
            return ContactDraft(
                name = n, nickname = nickname, company = company, title = title, note = note,
                phones = phones, emails = emails, addresses = addresses, websites = websites, events = events,
                newPhoto = photo,
            )
        }
    }

    /** Largest side a photo from a file is kept at; bigger ones are scaled down (contacts show at most 720 px). */
    private const val MAX_PHOTO_PX = 2048

    /**
     * A photo from a .vcf file, checked before it's used: something that isn't a picture is dropped, and a picture
     * with huge dimensions (a small file can claim tens of thousands of pixels, enough to run the phone out of
     * memory) is scaled down while it's read and saved again as a JPEG. Normal photos are kept exactly as they are.
     */
    fun safePhoto(bytes: ByteArray): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return@runCatching null
        if (maxOf(w, h) <= MAX_PHOTO_PX) return@runCatching bytes
        val bmp = decodeSampled(bytes, MAX_PHOTO_PX) ?: return@runCatching null
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** [bytes] as a picture no bigger than [maxPx] on its longest side, read without ever holding it at full size. */
    fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxPx) {
                val scale = maxPx.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
    }.getOrNull()

    private fun indexOfUnquoted(s: String, ch: Char): Int {
        var quoted = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> quoted = !quoted
                ch -> if (!quoted) return i
            }
        }
        return -1
    }

    /** Splits a structured value on unescaped semicolons. */
    private fun splitValues(v: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) { sb.append(c).append(v[i + 1]); i += 2; continue }
            if (c == ';') { out += unescape(sb.toString()); sb.clear() } else sb.append(c)
            i++
        }
        out += unescape(sb.toString())
        return out
    }

    private fun unescape(v: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (val n = v[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun decodeQp(v: String, charset: String?): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '=' && i + 2 < v.length) {
                val b = v.substring(i + 1, i + 3).toIntOrNull(16)
                if (b != null) { out.write(b); i += 3; continue }
            }
            out.write(c.code and 0xFF)
            i++
        }
        val cs = runCatching { Charset.forName(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(out.toByteArray(), cs)
    }

    /** vCard dates to Android's format: 1990-05-20, or --05-20 without a year. */
    private fun date(v: String): String? {
        val s = v.trim().substringBefore('T')
        val digits = s.filter { it.isDigit() }
        return when {
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(s) -> s
            Regex("--\\d{2}-?\\d{2}").matches(s) -> "--" + s.drop(2).filter { it.isDigit() }.let { it.take(2) + "-" + it.drop(2) }
            digits.length == 8 && !s.startsWith("--") -> "${digits.take(4)}-${digits.substring(4, 6)}-${digits.takeLast(2)}"
            else -> null
        }
    }

    private fun phoneType(t: List<String>): Int = when {
        "CELL" in t || "MOBILE" in t -> Phone.TYPE_MOBILE
        "FAX" in t && "WORK" in t -> Phone.TYPE_FAX_WORK
        "FAX" in t -> Phone.TYPE_FAX_HOME
        "PAGER" in t -> Phone.TYPE_PAGER
        "WORK" in t -> Phone.TYPE_WORK
        "HOME" in t -> Phone.TYPE_HOME
        "MAIN" in t -> Phone.TYPE_MAIN
        t.isEmpty() || "VOICE" in t -> Phone.TYPE_MOBILE
        else -> Phone.TYPE_OTHER
    }

    private fun emailType(t: List<String>): Int = when {
        "HOME" in t -> Email.TYPE_HOME
        "WORK" in t -> Email.TYPE_WORK
        "CELL" in t || "MOBILE" in t -> Email.TYPE_MOBILE
        else -> Email.TYPE_OTHER
    }

    private fun addressType(t: List<String>): Int = when {
        "WORK" in t -> StructuredPostal.TYPE_WORK
        "HOME" in t -> StructuredPostal.TYPE_HOME
        else -> StructuredPostal.TYPE_OTHER
    }

    /** The phone's own vCard of one contact (Android's contacts storage writes it). */
    fun export(resolver: ContentResolver, lookupKey: String): String? = runCatching {
        val uri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey)
        resolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
    }.getOrNull()
}
