package com.vknxworks.contactctrl.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class BirthdayDateTest {
    @Test fun formats() {
        assertEquals(BirthdayDate(MonthDay.of(5, 20), 1990), BirthdayDate.parse("1990-05-20"))
        assertEquals(BirthdayDate(MonthDay.of(5, 20), null), BirthdayDate.parse("--05-20"))
        assertEquals(BirthdayDate(MonthDay.of(5, 20), null), BirthdayDate.parse("--0520"))
        assertEquals(BirthdayDate(MonthDay.of(5, 20), 1990), BirthdayDate.parse("19900520"))
        assertEquals(BirthdayDate(MonthDay.of(5, 20), 1990), BirthdayDate.parse("1990-05-20T00:00:00Z"))
        assertEquals(BirthdayDate(MonthDay.of(5, 20), null), BirthdayDate.parse("1604-05-20"))
        assertNull(BirthdayDate.parse("1990-13-01"))
        assertNull(BirthdayDate.parse("next tuesday"))
        assertNull(BirthdayDate.parse(null))
    }

    @Test fun onTheDayAndAge() {
        val b = BirthdayDate.parse("1990-05-20")!!
        assertTrue(b.isOn(LocalDate.of(2026, 5, 20)))
        assertFalse(b.isOn(LocalDate.of(2026, 5, 21)))
        assertEquals(36, b.ageOn(LocalDate.of(2026, 5, 20)))
        assertNull(BirthdayDate.parse("--05-20")!!.ageOn(LocalDate.of(2026, 5, 20)))
    }

    @Test fun leapDay() {
        val b = BirthdayDate.parse("2000-02-29")!!
        assertTrue(b.isOn(LocalDate.of(2027, 2, 28)))
        assertFalse(b.isOn(LocalDate.of(2028, 2, 28)))
        assertTrue(b.isOn(LocalDate.of(2028, 2, 29)))
    }
}
