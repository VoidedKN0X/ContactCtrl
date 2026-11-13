package com.vknxworks.contactctrl.data.duplicates

/**
 * Turns a phone number as people type it into an E.164-like form ("+32470123456"), so the same number written
 * in different ways compares equal: "0470 12 34 56", "+32 (0)470 12 34 56" and "0032 470 123456" in Belgium, or
 * "(415) 555-0123" and "1-415-555-0123" in the US. A small table of calling codes and trunk prefixes, no
 * libphonenumber: good enough to find duplicates, not to validate numbers.
 */
object PhoneNormalizer {

    /** A country's calling code and the trunk prefix dialled before national numbers ("0" in most of Europe). */
    private data class Country(val code: String, val trunk: String?, val keepTrunk: Boolean = false)

    private val NANP = Country("1", "1")
    private val ZERO = { cc: String -> Country(cc, "0") }
    private val NONE = { cc: String -> Country(cc, null) }

    private val COUNTRIES: Map<String, Country> = buildMap {
        for (r in listOf("US", "CA", "PR", "DO", "JM", "TT", "BS", "BB", "GU", "VI")) put(r, NANP)
        // Trunk prefix 0
        mapOf(
            "GB" to "44", "DE" to "49", "FR" to "33", "NL" to "31", "BE" to "32", "CH" to "41", "AT" to "43", "IE" to "353",
            "SE" to "46", "FI" to "358", "SK" to "421", "SI" to "386", "HR" to "385", "RS" to "381", "RO" to "40", "BG" to "359",
            "TR" to "90", "IL" to "972", "AE" to "971", "SA" to "966", "EG" to "20", "ZA" to "27", "NG" to "234", "KE" to "254",
            "MA" to "212", "IN" to "91", "PK" to "92", "BD" to "880", "CN" to "86", "JP" to "81", "KR" to "82", "TW" to "886",
            "MY" to "60", "TH" to "66", "VN" to "84", "PH" to "63", "ID" to "62", "AU" to "61", "NZ" to "64", "BR" to "55",
            "AR" to "54", "PE" to "51", "UA" to "380", "BA" to "387", "MK" to "389", "AL" to "355", "ME" to "382", "CY" to "357",
        ).forEach { (r, cc) -> put(r, ZERO(cc)) }
        // No trunk prefix: national numbers are dialled as they are
        mapOf(
            "ES" to "34", "PT" to "351", "NO" to "47", "DK" to "45", "IS" to "354", "GR" to "30", "PL" to "48", "CZ" to "420",
            "LU" to "352", "EE" to "372", "LV" to "371", "MT" to "356", "HK" to "852", "SG" to "65", "MX" to "52", "CL" to "56",
            "CO" to "57", "MO" to "853",
        ).forEach { (r, cc) -> put(r, NONE(cc)) }
        // Trunk prefix 8
        mapOf("RU" to "7", "KZ" to "7", "BY" to "375", "LT" to "370").forEach { (r, cc) -> put(r, Country(cc, "8")) }
        put("HU", Country("36", "06"))
        // Italy, San Marino and the Vatican keep the leading 0 after the country code.
        put("IT", Country("39", "0", keepTrunk = true))
        put("SM", Country("378", null))
        put("VA", Country("39", "0", keepTrunk = true))
    }

    /** The calling code of [region] ("BE" -> "32"), or null if it isn't in the table. */
    fun callingCode(region: String?): String? = region?.uppercase()?.let { COUNTRIES[it]?.code }

    /**
     * The number in "+<country code><number>" form, or null for what can't be a full phone number (service codes
     * like "*21#", short numbers like "112", or numbers far too long). [region] is the phone's ISO country ("BE"),
     * used for numbers written without a country code; with an unknown region such numbers stay national
     * ("n:0470123456"), which still matches the same national number.
     */
    fun normalize(raw: String, region: String?): String? {
        var s = raw.trim()
        if (s.isEmpty() || s[0] == '*' || s[0] == '#') return null
        // An extension or dialling pause ends the number: "+32 2 123 45 67 ext. 12", "555-0123;9", "x12".
        val cut = s.indexOfFirst { it == ';' || it == ',' || it == '#' || it == '*' || it.isLetter() }
        if (cut >= 0) s = s.substring(0, cut)
        // "+32 (0)470 ..." : the national 0 in brackets isn't dialled from abroad.
        s = s.replace("(0)", "")
        val plus = s.trimStart().startsWith("+")
        val digits = s.filter { it in '0'..'9' }
        if (digits.isEmpty()) return null
        val country = region?.uppercase()?.let { COUNTRIES[it] }
        val international: String? = when {
            plus -> digits
            digits.startsWith("00") -> digits.drop(2)
            country == NANP && digits.startsWith("011") -> digits.drop(3)
            else -> null
        }
        val full = international ?: run {
            if (digits.length < 6) return null
            if (country == null) return "n:$digits".takeIf { digits.length <= 15 }
            val national = when {
                country == NANP -> when {
                    digits.length == 11 && digits.startsWith("1") -> digits.drop(1)
                    else -> digits
                }
                country.trunk != null && !country.keepTrunk && digits.startsWith(country.trunk) -> digits.drop(country.trunk.length)
                else -> digits
            }
            country.code + national
        }
        if (full.length !in 7..15) return null
        return "+$full"
    }
}
