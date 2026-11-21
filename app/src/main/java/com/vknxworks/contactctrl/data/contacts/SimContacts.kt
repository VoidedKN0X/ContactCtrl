package com.vknxworks.contactctrl.data.contacts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone

/**
 * The contacts stored on the SIM card (its ADN phone book), read through Android's own `icc` provider
 * (AOSP `IccProvider`: `content://icc/adn`, columns `name`, `number`, `emails`, `anrs`, `_id`; readable
 * with READ_CONTACTS, which ContactCtrl already has). That is the SIM of the phone's default subscription;
 * picking another SIM would need the phone-state permission, so ContactCtrl doesn't.
 */
object SimContacts {
    const val SOURCE = "sim"
    private val ADN: Uri = Uri.parse("content://icc/adn")

    /** The SIM's contacts as drafts, or throws when the SIM can't be read (no SIM, no telephony, locked). */
    fun read(resolver: ContentResolver): List<ContactDraft> {
        val out = ArrayList<ContactDraft>()
        val cursor = resolver.query(ADN, null, null, null, null) ?: throw IllegalStateException("no SIM phone book")
        cursor.use { c ->
            val name = c.getColumnIndex("name")
            val number = c.getColumnIndex("number")
            val emails = c.getColumnIndex("emails")
            val anrs = c.getColumnIndex("anrs")
            while (c.moveToNext()) {
                val n = if (name >= 0) c.getString(name)?.trim().orEmpty() else ""
                val phones = buildList {
                    if (number >= 0) c.getString(number)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(Field(Kind.PHONE, it, Phone.TYPE_MOBILE)) }
                    if (anrs >= 0) splitList(c.getString(anrs)).forEach { add(Field(Kind.PHONE, it, Phone.TYPE_OTHER)) }
                }
                val mails = if (emails >= 0) splitList(c.getString(emails)).map { Field(Kind.EMAIL, it, Email.TYPE_OTHER) } else emptyList()
                if (n.isEmpty() && phones.isEmpty() && mails.isEmpty()) continue
                out += ContactDraft(name = NameParts(given = n), phones = phones, emails = mails)
            }
        }
        return out
    }

    /** The provider joins several emails or extra numbers with commas. */
    private fun splitList(s: String?): List<String> =
        s.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
