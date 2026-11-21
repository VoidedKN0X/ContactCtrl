package com.vknxworks.contactctrl.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrVCardTest {
    private fun detail(note: String = "") = ContactDetail(
        id = 1, lookupKey = "k", displayName = "Zoë Peeters", name = NameParts(given = "Zoë", family = "Peeters"),
        nameDataId = null, nameRawId = null, nickname = "", nicknameDataId = null, nicknameRawId = null,
        company = "Acme, Inc.", title = "", orgDataId = null, orgRawId = null, note = note, noteDataId = null, noteRawId = null,
        phones = listOf(Field(Kind.PHONE, "+32 470 12 34 56", 2)), emails = emptyList(), addresses = emptyList(),
        websites = emptyList(), events = listOf(Field(Kind.EVENT, "--05-20", 3)), photoUri = null, starred = false, ringtone = null, raws = emptyList(),
    )

    @Test fun escapes() {
        assertEquals("a\\,b\\;c\\\\d\\ne", QrVCard.esc("a,b;c\\d\ne"))
    }

    @Test fun buildsAVCard() {
        val v = QrVCard.build(detail())
        assertTrue(v.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(v.contains("N:Peeters;Zoë;;;\r\n"))
        assertTrue(v.contains("ORG:Acme\\, Inc.\r\n"))
        assertTrue(v.contains("TEL;TYPE=CELL:+32 470 12 34 56\r\n"))
        assertTrue(v.contains("BDAY:--05-20\r\n"))
        assertTrue(v.endsWith("END:VCARD\r\n"))
    }

    @Test fun noteOnlyWhenAsked() {
        assertTrue(QrVCard.build(detail("hi")).contains("NOTE:hi"))
        assertFalse(QrVCard.build(detail("hi"), withNote = false).contains("NOTE"))
    }
}
