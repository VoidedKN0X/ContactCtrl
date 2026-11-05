package com.vknxworks.contactctrl

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import com.vknxworks.contactctrl.data.contacts.ContactDraft
import com.vknxworks.contactctrl.data.contacts.Field
import com.vknxworks.contactctrl.data.contacts.Kind
import com.vknxworks.contactctrl.data.contacts.NameParts

/** What another app asked ContactCtrl to open. */
sealed interface Launch {
    data class View(val uri: Uri) : Launch
    data class Edit(val uri: Uri) : Launch
    data class Insert(val draft: ContactDraft) : Launch
    data class InsertOrEdit(val draft: ContactDraft) : Launch
    data class ShowOrCreate(val value: String, val isEmail: Boolean, val draft: ContactDraft) : Launch
    data class Import(val uri: Uri) : Launch

    companion object {
        fun from(intent: Intent?): Launch? {
            intent ?: return null
            val data = intent.data
            return when (intent.action) {
                Intent.ACTION_VIEW -> when {
                    data == null -> null
                    // Only content:// links. ContactCtrl has no storage permission, so a file:// path could only
                    // point into its own private files.
                    isVCard(intent) -> if (data.scheme.equals("content", ignoreCase = true)) Import(data) else null
                    data.authority == ContactsContract.AUTHORITY -> View(data)
                    else -> null
                }
                ContactsContract.QuickContact.ACTION_QUICK_CONTACT, "com.android.contacts.action.QUICK_CONTACT" ->
                    data?.takeIf { it.authority == ContactsContract.AUTHORITY }?.let { View(it) }
                Intent.ACTION_EDIT -> data?.takeIf { it.authority == ContactsContract.AUTHORITY }?.let { Edit(it) }
                Intent.ACTION_INSERT -> Insert(draftFrom(intent))
                Intent.ACTION_INSERT_OR_EDIT -> InsertOrEdit(draftFrom(intent))
                ContactsContract.Intents.SHOW_OR_CREATE_CONTACT -> {
                    val uri = data ?: return null
                    val isEmail = uri.scheme == "mailto"
                    val value = uri.schemeSpecificPart?.substringBefore('?')?.trim().orEmpty()
                    if (value.isEmpty()) return null
                    val draft = draftFrom(intent).let { d ->
                        if (isEmail) d.copy(emails = listOf(Field(Kind.EMAIL, value, Email.TYPE_OTHER)) + d.emails)
                        else d.copy(phones = listOf(Field(Kind.PHONE, value, Phone.TYPE_MOBILE)) + d.phones)
                    }
                    ShowOrCreate(value, isEmail, draft)
                }
                else -> null
            }
        }

        private fun isVCard(intent: Intent): Boolean {
            val t = intent.type?.lowercase() ?: return intent.data?.lastPathSegment?.lowercase()?.endsWith(".vcf") == true
            return t == "text/x-vcard" || t == "text/vcard" || t == "text/directory"
        }

        /** The fields another app filled in (ContactsContract.Intents.Insert). */
        fun draftFrom(intent: Intent): ContactDraft {
            val e = intent.extras ?: return ContactDraft()
            fun s(key: String) = e.getCharSequence(key)?.toString()?.trim().orEmpty()
            val phones = ArrayList<Field>()
            val emails = ArrayList<Field>()
            val addresses = ArrayList<Field>()
            val websites = ArrayList<Field>()
            val events = ArrayList<Field>()
            fun phone(numKey: String, typeKey: String) {
                val n = s(numKey)
                if (n.isNotEmpty()) phones += Field(Kind.PHONE, n, typeFrom(e.get(typeKey), Phone.TYPE_MOBILE))
            }
            fun email(key: String, typeKey: String) {
                val v = s(key)
                if (v.isNotEmpty()) emails += Field(Kind.EMAIL, v, typeFrom(e.get(typeKey), Email.TYPE_HOME))
            }
            phone(ContactsContract.Intents.Insert.PHONE, ContactsContract.Intents.Insert.PHONE_TYPE)
            phone(ContactsContract.Intents.Insert.SECONDARY_PHONE, ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE)
            phone(ContactsContract.Intents.Insert.TERTIARY_PHONE, ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE)
            email(ContactsContract.Intents.Insert.EMAIL, ContactsContract.Intents.Insert.EMAIL_TYPE)
            email(ContactsContract.Intents.Insert.SECONDARY_EMAIL, ContactsContract.Intents.Insert.SECONDARY_EMAIL_TYPE)
            email(ContactsContract.Intents.Insert.TERTIARY_EMAIL, ContactsContract.Intents.Insert.TERTIARY_EMAIL_TYPE)
            s(ContactsContract.Intents.Insert.POSTAL).takeIf { it.isNotEmpty() }?.let {
                addresses += Field(Kind.ADDRESS, it, typeFrom(e.get(ContactsContract.Intents.Insert.POSTAL_TYPE), StructuredPostal.TYPE_HOME))
            }
            var name = s(ContactsContract.Intents.Insert.NAME).takeIf { it.isNotEmpty() }?.let { NameParts(given = it) } ?: NameParts()
            var nickname = ""
            var company = s(ContactsContract.Intents.Insert.COMPANY)
            var title = s(ContactsContract.Intents.Insert.JOB_TITLE)
            var note = s(ContactsContract.Intents.Insert.NOTES)

            // Full rows (ContactsContract.Intents.Insert.DATA): apps like messengers and browsers send these.
            val rows: List<ContentValues> = runCatching {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 33) e.getParcelableArrayList(ContactsContract.Intents.Insert.DATA, ContentValues::class.java)
                else e.getParcelableArrayList(ContactsContract.Intents.Insert.DATA)
            }.getOrNull().orEmpty().take(200)
            for (v in rows) {
                val d1 = v.getAsString("data1")?.trim().orEmpty()
                val t = v.getAsInteger("data2") ?: 0
                val label = v.getAsString("data3")
                when (v.getAsString(ContactsContract.Data.MIMETYPE)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (name.isBlank) name = NameParts(
                        prefix = v.getAsString(StructuredName.PREFIX).orEmpty(), given = v.getAsString(StructuredName.GIVEN_NAME).orEmpty().ifEmpty { d1 },
                        middle = v.getAsString(StructuredName.MIDDLE_NAME).orEmpty(), family = v.getAsString(StructuredName.FAMILY_NAME).orEmpty(),
                        suffix = v.getAsString(StructuredName.SUFFIX).orEmpty(),
                    )
                    Phone.CONTENT_ITEM_TYPE -> if (d1.isNotEmpty()) phones += Field(Kind.PHONE, d1, if (t == 0 && label == null) Phone.TYPE_MOBILE else t, label)
                    Email.CONTENT_ITEM_TYPE -> if (d1.isNotEmpty()) emails += Field(Kind.EMAIL, d1, if (t == 0 && label == null) Email.TYPE_HOME else t, label)
                    StructuredPostal.CONTENT_ITEM_TYPE -> if (d1.isNotEmpty()) addresses += Field(Kind.ADDRESS, d1, if (t == 0 && label == null) StructuredPostal.TYPE_HOME else t, label)
                    Website.CONTENT_ITEM_TYPE -> if (d1.isNotEmpty()) websites += Field(Kind.WEBSITE, d1, if (t == 0) Website.TYPE_OTHER else t, label)
                    Event.CONTENT_ITEM_TYPE -> if (d1.isNotEmpty()) events += Field(Kind.EVENT, d1, if (t == 0 && label == null) Event.TYPE_OTHER else t, label)
                    Nickname.CONTENT_ITEM_TYPE -> if (nickname.isEmpty()) nickname = d1
                    Organization.CONTENT_ITEM_TYPE -> if (company.isEmpty()) { company = d1; title = v.getAsString(Organization.TITLE).orEmpty() }
                    Note.CONTENT_ITEM_TYPE -> if (note.isEmpty()) note = d1
                }
            }
            return ContactDraft(
                name = name, nickname = nickname, company = company, title = title, note = note,
                phones = phones, emails = emails, addresses = addresses, websites = websites, events = events,
            )
        }

        private fun typeFrom(v: Any?, default: Int): Int = when (v) {
            is Int -> v
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }
}
