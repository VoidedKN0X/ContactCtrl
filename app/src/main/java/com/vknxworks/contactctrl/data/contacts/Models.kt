package com.vknxworks.contactctrl.data.contacts

import java.text.Normalizer

/** One contact in the list. Phones and emails are kept for search and the list's second line. */
data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    /** "First Last" */
    val name: String,
    /** "Last, First" */
    val altName: String,
    val sortKey: String,
    val altSortKey: String,
    val photoUri: String?,
    val starred: Boolean,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
) {
    /** Lower-case, accent-free name text for search ("Zoë" matches "zoe"). */
    val searchName: String = fold("$name $altName")
    val searchDigits: List<String> = phones.map(::digits)
    val searchEmails: List<String> = emails.map { it.lowercase() }

    fun matches(query: String): Boolean {
        val q = fold(query.trim())
        if (q.isEmpty()) return true
        // Every word of the query has to start a word of the name ("jo sm" finds "John Smith").
        val words = searchName.split(' ', '-', ',', '.').filter { it.isNotEmpty() }
        if (q.split(' ').filter { it.isNotEmpty() }.all { part -> words.any { it.startsWith(part) } }) return true
        if (searchName.contains(q)) return true
        val qd = digits(query)
        if (qd.length >= 3 && searchDigits.any { it.contains(qd) || it.contains(qd.trimStart('0')) }) return true
        return searchEmails.any { it.contains(q) }
    }

    companion object {
        fun fold(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

        fun digits(s: String): String = s.filter { it.isDigit() }
    }
}

/** A raw contact: the part of a contact stored by one account (the phone itself, a sync account, a messenger). */
data class RawInfo(
    val id: Long,
    val accountType: String?,
    val accountName: String?,
    /** ContactCtrl may change it: the phone's own contacts, or an account that syncs changes back. */
    val writable: Boolean,
)

enum class Kind { PHONE, EMAIL, ADDRESS, WEBSITE, EVENT }

/** One phone number, email, address, website or date of a contact. */
data class Field(
    val kind: Kind,
    val value: String,
    /** The Android type constant (Phone.TYPE_MOBILE, Email.TYPE_HOME, ...). */
    val type: Int,
    /** The label when type is TYPE_CUSTOM. */
    val label: String? = null,
    val dataId: Long? = null,
    val rawContactId: Long? = null,
    val primary: Boolean = false,
)

data class NameParts(
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
) {
    val display: String get() = listOf(prefix, given, middle, family, suffix).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    val isBlank: Boolean get() = display.isBlank()
}

data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val name: NameParts,
    val nameDataId: Long?,
    val nameRawId: Long?,
    val nickname: String,
    val nicknameDataId: Long?,
    val nicknameRawId: Long?,
    val company: String,
    val title: String,
    val orgDataId: Long?,
    val orgRawId: Long?,
    val note: String,
    val noteDataId: Long?,
    val noteRawId: Long?,
    val phones: List<Field>,
    val emails: List<Field>,
    val addresses: List<Field>,
    val websites: List<Field>,
    val events: List<Field>,
    val photoUri: String?,
    val starred: Boolean,
    val ringtone: String?,
    val raws: List<RawInfo>,
) {
    val writable: Boolean get() = raws.any { it.writable }
    val readOnlyAccounts: List<String> get() = raws.filter { !it.writable }.mapNotNull { it.accountType }.distinct()
}

/** What the edit screen holds; saved by [ContactWriter]. */
data class ContactDraft(
    val name: NameParts = NameParts(),
    val nickname: String = "",
    val company: String = "",
    val title: String = "",
    val note: String = "",
    val phones: List<Field> = emptyList(),
    val emails: List<Field> = emptyList(),
    val addresses: List<Field> = emptyList(),
    val websites: List<Field> = emptyList(),
    val events: List<Field> = emptyList(),
    /** New photo as JPEG bytes, or null to keep it. */
    val newPhoto: ByteArray? = null,
    val removePhoto: Boolean = false,
) {
    val isEmpty: Boolean
        get() = name.isBlank && nickname.isBlank() && company.isBlank() &&
            (phones + emails + addresses + websites).all { it.value.isBlank() }

    companion object {
        fun from(d: ContactDetail) = ContactDraft(
            name = d.name,
            nickname = d.nickname,
            company = d.company,
            title = d.title,
            note = d.note,
            phones = d.phones,
            emails = d.emails,
            addresses = d.addresses,
            websites = d.websites,
            events = d.events,
        )
    }

    override fun equals(other: Any?): Boolean = other is ContactDraft && name == other.name && nickname == other.nickname &&
        company == other.company && title == other.title && note == other.note && phones == other.phones &&
        emails == other.emails && addresses == other.addresses && websites == other.websites && events == other.events &&
        removePhoto == other.removePhoto && (newPhoto === other.newPhoto || (newPhoto != null && other.newPhoto != null && newPhoto.contentEquals(other.newPhoto)))

    override fun hashCode(): Int = listOf(name, nickname, company, title, note, phones, emails, addresses, websites, events, removePhoto).hashCode()
}

/** Where a new contact is saved: the phone itself (null), or a sync account. */
data class SaveAccount(val type: String?, val name: String?) {
    val isDevice: Boolean get() = type == null
}
