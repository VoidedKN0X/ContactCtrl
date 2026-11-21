package com.vknxworks.contactctrl.background

import java.time.LocalDate
import java.time.MonthDay

/** A birthday as Android stores it: "1990-05-20", "--05-20" (no year), "19900520" or "1990-05-20T00:00:00Z". */
data class BirthdayDate(val monthDay: MonthDay, val year: Int?) {

    /** True when this birthday falls on [day]. 29 February is celebrated on 28 February in other years. */
    fun isOn(day: LocalDate): Boolean {
        if (monthDay.monthValue == 2 && monthDay.dayOfMonth == 29 && !day.isLeapYear) {
            return day.monthValue == 2 && day.dayOfMonth == 28
        }
        return MonthDay.from(day) == monthDay
    }

    /** The age turned on [day], when the year is known and plausible. */
    fun ageOn(day: LocalDate): Int? = year?.let { day.year - it }?.takeIf { it in 1..130 }

    companion object {
        private val FULL = Regex("^(\\d{4})-?(\\d{2})-?(\\d{2})")
        private val NO_YEAR = Regex("^--(\\d{2})-?(\\d{2})")

        fun parse(value: String?): BirthdayDate? {
            val s = value?.trim().orEmpty()
            NO_YEAR.find(s)?.let { m -> return of(m.groupValues[1], m.groupValues[2], null) }
            FULL.find(s)?.let { m ->
                val y = m.groupValues[1].toInt()
                // Some apps store an unknown year as 0000 or 1604 (Apple's convention).
                return of(m.groupValues[2], m.groupValues[3], y.takeIf { it > 1604 })
            }
            return null
        }

        private fun of(month: String, day: String, year: Int?): BirthdayDate? = runCatching {
            BirthdayDate(MonthDay.of(month.toInt(), day.toInt()), year)
        }.getOrNull()
    }
}
