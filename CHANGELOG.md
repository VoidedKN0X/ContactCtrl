# Changelog

What changed in each ContactCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ContactCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.2.0 (2026-11-13)

Find duplicate contacts and merge them, without deleting anything.

### New

- **Find duplicates.** The new button next to + on the Contacts tab lists contacts that are probably the same person, in groups, with the reason: the same phone number (also when it's written differently, such as "0470 12 34 56" and "+32 470 12 34 56", or with an extension), the same email address (upper or lower case doesn't matter), or the same name (case, accents, punctuation, extra spaces and the order of first and last name don't matter, so "Smith, John" matches "john smith"). A number or email shared by many contacts, like an office switchboard, doesn't count. Tap a contact in a group to check it first.
- **Merge.** Merge joins a group into one contact the way Android itself links contacts: every account keeps its own copy of its details (this phone, DAVx⁵, Exchange, messengers like WhatsApp), nothing is deleted or rewritten, and the merged contact shows everything together. Contacts from accounts ContactCtrl can't change, such as messengers, can be merged too: they stay exactly as they are.
- **Undo.** Right after a merge, Undo splits the contacts again. Later, a merged contact's page has "Separate merged contacts" under More, which splits it back into the contacts it was made from.
- **Not duplicates.** Tell ContactCtrl a group isn't the same person and it won't suggest those contacts together again. The answer is kept on this phone, in ContactCtrl's own settings.

## v0.1.8 (2026-11-13)

### Security

- **Contact photos from .vcf files are checked before they're used.** A photo in a file that isn't really a picture is now left out, and a picture with enormous dimensions (a small file can claim tens of thousands of pixels, enough to run the phone out of memory and crash ContactCtrl) is scaled down while it's read. Normal photos are imported exactly as before.
- **The build tool download is checked.** The exact Gradle version used to build ContactCtrl is now verified against its published checksum before it's used.

### Fixed

- **Big or oddly made .vcf files import quickly.** A file with many folded lines (some apps split long values, such as photos, over thousands of short lines) took far longer to read than its size warranted. Files are now read in one quick pass, however they're laid out.
- **The photo on the edit screen is read at the size it's shown at**, so a large photo no longer takes extra memory or time there.

## v0.1.7 (2026-11-12)

### Changed

- **Slightly smaller download.** The bundled JetBrains Mono font (numbers, readouts and labels) no longer carries the Cyrillic, Vietnamese and maths-alphabet letters ContactCtrl never shows itself, which takes about 0.1 MB off the download and 0.2 MB off the installed app. Everything looks the same; if a name or a log line shown in that font uses one of those scripts, those letters are now drawn with your phone's own font.

## v0.1.6 (2026-11-05)

ContactCtrl now needs Android 15 or newer.

### Changed

- **Needs Android 15 or newer.** Android 13 no longer gets security updates and Android 14's end early 2027, so ContactCtrl now supports Android 15, 16 and 17 only. On older phones the version you have keeps working, but it won't get updates.

## v0.1.5 (2026-11-05)

Confirmations are readable by accessibility apps again.

### Fixed

- "Can't be undone" confirmations (such as deleting a contact) can be read again by apps that use accessibility, such as password managers and automation apps. 0.1.4 hid them from those apps; now only the lock screen, the PIN screens and "Forgot PIN?" are hidden from them. Other apps' floating windows are still kept off these confirmations, and screen readers such as TalkBack keep working everywhere.

## v0.1.4 (2026-11-05)

A security update for the app lock and for requests from other apps. Nothing changes in how you use ContactCtrl.

### Security

- **No floating windows over the app lock.** While the lock screen, a PIN screen (turning the lock on or off, changing the PIN), "Forgot PIN?" or a delete or reset confirmation is showing, other apps' floating windows (chat heads, bubbles, overlays) are hidden on top of ContactCtrl (Android 12 and newer), so no app can draw over the PIN pad or trick you into tapping a button. They come back as soon as that screen closes, and everywhere else in the app they keep working. This uses a new permission, "Hide other apps' floating windows", which Android grants at install without asking.
- **Lock and PIN screens are hidden from apps that misuse accessibility.** On Android 14 and newer, the same screens are marked sensitive, so only real accessibility tools such as screen readers (TalkBack) can read them, not other apps that use the accessibility API to watch the screen.
- **Stricter requests from other apps.** On Android 16 and newer, other apps can only open ContactCtrl in the ways it offers (the launcher, opening or adding a contact, picking a contact, opening a .vcf file and "show contact for this number"). A request aimed directly at a part of ContactCtrl that doesn't accept it is now refused by Android.
- **.vcf files must really belong to the app that sends them.** On Android 15 and newer, when another app opens or shares a .vcf file with ContactCtrl, Android first checks that the sending app can read that file itself, and refuses the request if it can't. So no app can use ContactCtrl to read a file it has no access to. Opening .vcf files from your file manager, email or messaging app works as before.

### Project

- **Check a download:** the release notes now list the APK's SHA-256 checksum, and the APK has a signed build provenance attestation: `gh attestation verify ContactCtrl-v0.1.4.apk -R VoidedKN0X/ContactCtrl` proves it was built by this repo's build workflow from the tagged commit. The signing certificate fingerprint in the README still applies.
- **Checked dependencies:** the build now checks every library and Gradle plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml`, and stops if one doesn't match.
- README: the new permission is in the permissions table, with a "Check a download" note and how dependency verification works.

## Project updates (2026-11-05)

### Project

- **Release checks:** from the next release, the release notes list the APK's SHA-256 checksum, and every release APK gets a signed build provenance attestation, so anyone can check with `gh attestation verify` that it was built by this repo's workflow from the tagged commit.
- **Workflow security:** a new zizmor check scans the GitHub Actions workflows on every change to them, and every workflow now starts with no permissions, giving each job only what it needs.
- **Code scanning:** CodeQL scans the code every week and on every app change; results go to the repo's Security tab.
- **Dependency checksums:** a new manual workflow writes the SHA-256 checksums of every library and Gradle plugin the build downloads, ready for the build to check them in the next release.

## v0.1.3 (2026-11-05)

A small security update for the app lock. Contacts and settings are unchanged.

### Security

- **"Forgot PIN?" now asks for your phone's screen lock.** Before, anyone holding your unlocked phone could tap "Forgot PIN?" on the lock screen and turn ContactCtrl's app lock off without knowing the PIN. Now the reset first asks for your phone's own PIN, pattern, password or fingerprint. If you cancel or it fails, nothing is reset. If your phone has no screen lock at all, the reset works as before.

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
