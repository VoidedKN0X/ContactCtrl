# Changelog

What changed in each ContactCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ContactCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.2 (2026-11-05)

A small security update. Contacts, settings and the app lock are unchanged.

### Security

- **.vcf files from other apps only as shared links.** Opening a .vcf file from another app still imports it, but ContactCtrl now only accepts the file as a `content://` link the other app shares with it (which is how file managers, mail and chat apps send files). Plain `file://` paths are refused: ContactCtrl has no storage permission, so such a path could only have pointed into ContactCtrl's own private files.

### Privacy

- **No debug logging in release builds.** Debug, verbose and info messages from the libraries ContactCtrl uses are now stripped from the app, so nothing can end up in the phone's system log. ContactCtrl's own code already logged nothing.

### Project

- The build now also checks that the Gradle wrapper is an official Gradle release before building.
- `.gitignore` also covers `google-services.json`, so it can't be committed by accident.
- README: every permission in the app is listed with its Android name and what it's for, including the "dynamic receiver" permission AndroidX adds, and the Ctrl series list now has all 28 apps.

## v0.1.1 (2026-11-05)

### Fixed

- Opening a contact, a phone number link or a .vcf file from another app while ContactCtrl was fully closed could close the app instead of showing it. It now waits until it's ready and then opens what was asked for.

## v0.1.0 (2026-11-05)

The first release of ContactCtrl: a private, offline contacts app in the Ctrl series, made to replace your phone's contacts app on any Android 13+ phone.

### New

- **Contact list** with letter headers, your favourites on top, and each contact's photo or initials and first phone number. Choose first-name or last-name order and sorting in Settings.
- **Search** across names, nicknames, phone numbers (any part, with or without country code) and email addresses, ignoring accents.
- **Contact page** with big Call, Message and Email buttons; every phone number (tap to call, bubble to text), email, address (opens your maps app), website, birthday and other dates, company, job title, nickname and notes. Long-press any of them to copy it; copied details are hidden from the clipboard preview.
- **Add and edit contacts:** name with prefix, middle name, suffix and nickname; phone numbers, emails and addresses with labels such as mobile and work or your own; company and job title; dates with or without a year; websites; notes; and a photo chosen with Android's photo picker, cropped square and resized on the phone.
- **Careful saving:** only the details you changed are written. Contacts that also exist in messengers like WhatsApp or Signal keep those parts untouched, and contacts only from such apps are shown as read-only.
- **Choose where new contacts are saved:** this phone only, or a sync account such as DAVx⁵ that already holds contacts.
- **Favourites:** star a contact on its page, or several at once; the Favourites tab lists them.
- **Ringtone per contact**, using your phone's ringtone picker.
- **Select several contacts** with a long-press to share, favourite or delete them together.
- **Share a contact** as a .vcf file with the app you pick.
- **Import .vcf files** (vCard 2.1, 3.0 and 4.0, with photos) from Settings or by opening the file from your file manager. Shows what's in the file first, and skips contacts you already have.
- **Export all contacts** to one .vcf file wherever you choose.
- **Works with your other apps:** "Add to contacts" (into a new or an existing contact), "Edit contact" and "Show contact" from other apps, returning to the app that asked when you're done; and the contact, phone number, email and address pickers that messengers and other apps open.
- **App lock** with its own PIN (at least 6 digits) and optional fingerprint unlock, also covering the pickers; **Hide in screenshots** and **Hide in recent apps**, both off by default.
- **Ctrl / Signal look** in dark and light, following your phone's theme, with layouts for phones, tablets and foldables.
