package com.vknxworks.contactctrl.data.contacts

/**
 * A compact vCard 3.0 of a contact for a QR code: name, organisation, numbers, emails, addresses, websites,
 * birthday, nickname and (optionally) the note. No photo: a photo never fits in a QR code. Camera apps and
 * contacts apps read this as "add contact". Plain Kotlin (Android's type numbers are written out) so it is
 * unit-tested on the JVM.
 */
object QrVCard {
    fun build(d: ContactDetail, withNote: Boolean = true): String {
        val sb = StringBuilder()
        fun line(s: String) = sb.append(s).append("\r\n")
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        val n = d.name
        line("N:" + listOf(n.family, n.given, n.middle, n.prefix, n.suffix).joinToString(";") { esc(it.trim()) })
        line("FN:" + esc(d.displayName.ifBlank { n.display }))
        if (d.nickname.isNotBlank()) line("NICKNAME:" + esc(d.nickname.trim()))
        if (d.company.isNotBlank()) line("ORG:" + esc(d.company.trim()))
        if (d.title.isNotBlank()) line("TITLE:" + esc(d.title.trim()))
        for (f in d.phones) line("TEL" + phoneType(f.type) + ":" + esc(f.value.trim()))
        for (f in d.emails) line("EMAIL" + simpleType(f.type) + ":" + esc(f.value.trim()))
        // The address is kept as one block of text by ContactCtrl: it goes in the street part.
        for (f in d.addresses) line("ADR" + simpleType(f.type) + ":;;" + esc(f.value.trim()) + ";;;;")
        for (f in d.websites) line("URL:" + esc(f.value.trim()))
        d.events.firstOrNull { it.type == 3 }?.let { b ->
            // Event.TYPE_BIRTHDAY = 3. "--MM-DD" (no year) is valid in vCard 3.0 readers that matter.
            line("BDAY:" + b.value.trim())
        }
        if (withNote && d.note.isNotBlank()) line("NOTE:" + esc(d.note.trim()))
        line("END:VCARD")
        return sb.toString()
    }

    /** Backslash, comma, semicolon and line breaks escaped as vCard 3.0 asks. */
    fun esc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '\\' -> append("\\\\")
            ',' -> append("\\,")
            ';' -> append("\\;")
            '\n' -> append("\\n")
            '\r' -> Unit
            else -> append(ch)
        }
    }

    // Phone.TYPE_HOME 1, MOBILE 2, WORK 3, FAX_WORK 4, FAX_HOME 5, PAGER 6, MAIN 12
    private fun phoneType(t: Int): String = when (t) {
        1 -> ";TYPE=HOME"
        2 -> ";TYPE=CELL"
        3 -> ";TYPE=WORK"
        4 -> ";TYPE=WORK,FAX"
        5 -> ";TYPE=HOME,FAX"
        6 -> ";TYPE=PAGER"
        12 -> ";TYPE=PREF"
        else -> ""
    }

    // Email / StructuredPostal TYPE_HOME 1, TYPE_WORK 2
    private fun simpleType(t: Int): String = when (t) {
        1 -> ";TYPE=HOME"
        2 -> ";TYPE=WORK"
        else -> ""
    }
}
