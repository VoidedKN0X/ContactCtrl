package com.vknxworks.contactctrl.data.duplicates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNormalizerTest {
    private fun n(s: String, region: String? = "BE") = PhoneNormalizer.normalize(s, region)

    @Test
    fun belgianNumbersInEveryWriting() {
        val want = "+32470123456"
        for (s in listOf("0470 12 34 56", "0470/12.34.56", "+32 470 12 34 56", "+32 (0)470 12 34 56", "0032 470 123456", "+32-470-123-456", " 0470123456 ")) {
            assertEquals(s, want, n(s))
        }
    }

    @Test
    fun foreignNumbersKeepTheirCountry() {
        assertEquals("+31612345678", n("+31 6 12345678"))
        assertEquals("+31612345678", n("0031 6 12345678"))
        // Dialled from Belgium without a country code: a Belgian number.
        assertEquals("+3221234567", n("02 123 45 67"))
    }

    @Test
    fun northAmerica() {
        val want = "+14155550123"
        for (s in listOf("(415) 555-0123", "415.555.0123", "1-415-555-0123", "+1 415 555 0123", "011 1 415 555 0123")) {
            assertEquals(s, want, n(s, "US"))
        }
        assertEquals("+442079460000", n("011 44 20 7946 0000", "US"))
    }

    @Test
    fun italyKeepsItsLeadingZero() {
        assertEquals("+390612345678", n("06 1234 5678", "IT"))
        assertEquals("+390612345678", n("+39 06 1234 5678", "IT"))
        assertEquals("+393471234567", n("347 123 4567", "IT"))
    }

    @Test
    fun otherTrunkPrefixes() {
        assertEquals("+79161234567", n("8 916 123-45-67", "RU"))
        assertEquals("+36201234567", n("06 20 123 4567", "HU"))
        assertEquals("+34612345678", n("612 34 56 78", "ES"))
        assertEquals("+447911123456", n("07911 123456", "GB"))
        assertEquals("+4915112345678", n("0151 12345678", "DE"))
    }

    @Test
    fun extensionsAndPausesAreCutOff() {
        assertEquals("+3221234567", n("+32 2 123 45 67 ext. 12"))
        assertEquals("+3221234567", n("+32 2 123 45 67;99"))
        assertEquals("+3221234567", n("+32 2 123 45 67,,1"))
        assertEquals("+3221234567", n("+32 2 123 45 67 x12"))
    }

    @Test
    fun whatIsntAFullNumber() {
        assertNull(n("112"))
        assertNull(n("*21#"))
        assertNull(n("#31#0470123456"))
        assertNull(n(""))
        assertNull(n("voicemail"))
        assertNull(n("+1234"))
        assertNull(n("+1234567890123456789"))
    }

    @Test
    fun unknownRegionStillMatchesTheSameNationalNumber() {
        assertEquals("n:0470123456", n("0470 12 34 56", null))
        assertEquals(n("0470/12.34.56", "XX"), n("0470 123 456", "XX"))
        assertEquals("+32470123456", n("+32 470 12 34 56", null))
    }

    @Test
    fun callingCodes() {
        assertEquals("32", PhoneNormalizer.callingCode("be"))
        assertEquals("1", PhoneNormalizer.callingCode("CA"))
        assertNull(PhoneNormalizer.callingCode("ZZ"))
    }
}
