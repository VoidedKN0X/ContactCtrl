package com.vknxworks.contactctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object ContactsRoute
@Serializable data object FavouritesRoute

// Opened from the profile picture: the You page, and Settings from there
@Serializable data object YouRoute
@Serializable data object SettingsRoute

// A contact, and editing one. id -1: a new contact. prefill: start from the draft another app sent.
@Serializable data class DetailRoute(val id: Long, val key: String)
@Serializable data class EditRoute(val id: Long = -1L, val key: String = "", val prefill: Boolean = false)

// "Add to a contact" from another app: pick a contact to add to, or make a new one.
@Serializable data object AddToRoute

// Importing a .vcf file
@Serializable data class ImportRoute(val uri: String)
