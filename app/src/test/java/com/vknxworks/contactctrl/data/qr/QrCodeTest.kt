package com.vknxworks.contactctrl.data.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The encoder's output was also checked by decoding it with OpenCV's QR reader for every version 1-40 at both
 * error-correction levels (full-capacity random text, all 80 decoded back exactly). These tests keep the
 * structure in place.
 */
class QrCodeTest {
    private fun capacity(ver: Int, ecc: QrCode.Ecc): Int = (QrCode.dataCodewords(ver, ecc) * 8 - 4 - if (ver <= 9) 8 else 16) / 8

    @Test fun knownCapacities() {
        // Byte-mode capacities from the QR Code standard's tables.
        assertEquals(14, capacity(1, QrCode.Ecc.MEDIUM))
        assertEquals(17, capacity(1, QrCode.Ecc.LOW))
        assertEquals(213, capacity(10, QrCode.Ecc.MEDIUM))
        assertEquals(2331, capacity(40, QrCode.Ecc.MEDIUM))
        assertEquals(2953, capacity(40, QrCode.Ecc.LOW))
    }

    @Test fun smallestVersionThatFits() {
        assertEquals(21, QrCode.encode(ByteArray(14))!!.size)
        assertEquals(25, QrCode.encode(ByteArray(15))!!.size)
    }

    @Test fun fallsBackToLowThenGivesUp() {
        assertNotNull(QrCode.encode(ByteArray(2900)))
        assertNull(QrCode.encode(ByteArray(QrCode.MAX_BYTES + 1)))
    }

    @Test fun finderPatternsInThreeCorners() {
        val q = QrCode.encode("BEGIN:VCARD".toByteArray())!!
        for ((x0, y0) in listOf(0 to 0, q.size - 7 to 0, 0 to q.size - 7)) {
            for (i in 0 until 7) {
                assertTrue(q.dark(x0 + i, y0))
                assertTrue(q.dark(x0, y0 + i))
            }
            assertTrue(q.dark(x0 + 3, y0 + 3))
        }
    }
}
