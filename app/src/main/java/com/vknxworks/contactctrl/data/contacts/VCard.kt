package com.vknxworks.contactctrl.data.contacts

import android.content.ContentResolver
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

    /** Joins folded lines (a line starting with a space or tab continues the previous one; 2.1 also folds after "="). */
    private fun unfold(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        for (l in raw) {
            val prev = out.lastOrNull()
            when {
                (l.startsWith(" ") || l.startsWith("\t")) && prev != null -> out[out.size - 1] = prev + l.substring(1)
                prev != null && prev.endsWith("=") && prev.uppercase().contains("QUOTED-PRINTABLE") -> out[out.size - 1] = prev.dropLast(1) + l
                l.isNotEmpty() -> out += l
            }
        }
        return out
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
                    if (data.isNotBlank()) photo = runCatching { Base64.decode(data.trim(), Base64.DEFAULT) }.getOrNull()?.takeIf { it.size < 4 * 1024 * 1024 }
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
