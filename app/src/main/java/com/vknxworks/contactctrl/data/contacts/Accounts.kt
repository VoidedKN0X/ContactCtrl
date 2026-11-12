package com.vknxworks.contactctrl.data.contacts

import android.content.ContentResolver
import android.provider.ContactsContract

/**
 * Which accounts ContactCtrl may change. Contacts that come from an account that doesn't sync changes back
 * (WhatsApp, Signal, Telegram and other messengers) are shown but never edited: the next sync would undo the edit.
 */
object Accounts {
    /** Account types with a contacts sync adapter. */
    private fun adapters(): List<android.content.SyncAdapterType> =
        runCatching { ContentResolver.getSyncAdapterTypes().filter { it.authority == ContactsContract.AUTHORITY } }.getOrDefault(emptyList())

    /** Sync accounts that upload changes (DAVx5, Exchange, ...). */
    fun writableTypes(): Set<String> = adapters().filter { it.supportsUploading() }.map { it.accountType }.toSet()

    private fun readOnlyTypes(): Set<String> = adapters().filter { !it.supportsUploading() }.map { it.accountType }.toSet()

    /**
     * The phone's own contacts (no account, or a phone account without a sync adapter, like Samsung's or
     * OnePlus' "Phone" account) and accounts that sync back can be changed.
     */
    fun isWritable(type: String?, writable: Set<String> = writableTypes()): Boolean =
        type == null || type in writable || type !in readOnlyTypesCached

    private val readOnlyTypesCached: Set<String> by lazy { readOnlyTypes() }

    /** A short, human name for an account type. */
    fun label(type: String?): String = when {
        type == null -> "Phone"
        type.contains("whatsapp") -> "WhatsApp"
        type.contains("signal") -> "Signal"
        type.contains("telegram") -> "Telegram"
        type.contains("threema") -> "Threema"
        type.contains("davdroid") || type.contains("davx5") || type.contains("bitfire") -> "DAVx⁵"
        type.contains("exchange") || type.contains("eas") -> "Exchange"
        type.contains("google") -> "Google"
        type.contains("sim") -> "SIM"
        else -> type.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
