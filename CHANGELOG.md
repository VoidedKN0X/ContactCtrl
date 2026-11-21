# Changelog

What changed in each ContactCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ContactCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Changes on `main` that aren't in a release yet. They ship with the next version.

### New

- **Birthday reminders.** Settings → Birthdays → *Birthday reminders* sends a notification on the day of a contact's birthday ("Turns 36 today" when the year is known), at the hour you pick (9:00 by default). Tapping it opens the contact. Someone born on 29 February is reminded on 28 February in other years. ContactCtrl asks for permission to show notifications when you turn this on. It is checked on the phone once a day with an ordinary (inexact) alarm, so Android may send it up to about an hour late to save battery; it is set again when the phone restarts, after an app update and when the clock or time zone changes. On the lock screen the notification only says "A birthday today"; the name shows once the phone is unlocked. Not yet tried on a phone.
- **Scheduled backup.** Settings → Scheduled backup → *Back up automatically* saves every contact in one .vcf file (the same as Export) in a folder you pick, every day or every week, and keeps the newest 1, 3, 5, 10 or 20 backups. *Back up now* makes one straight away and the row shows when the last backup was made, or that it failed. Android's job scheduler runs it when the battery isn't low, and keeps it through restarts. Only ContactCtrl's own backup files (named `contactctrl-backup-<date>-<time>.vcf`) are ever deleted; nothing else in the folder is touched. The file isn't encrypted, like an export. Not yet tried on a phone.

### Permissions

- **Show notifications** (`POST_NOTIFICATIONS`), only asked for when you turn on birthday reminders.
- **Run at startup** (`RECEIVE_BOOT_COMPLETED`), so the birthday check and the scheduled backup are set again after the phone restarts. Granted at install, no prompt. ContactCtrl still has no internet permission.
- **Import from the SIM card.** Settings → Import and export has *Import from the SIM card*. It reads the contacts saved on your SIM (name, number, and extra numbers and emails where the SIM has them), shows how many there are and the first few, and imports them to this phone or the account you pick, skipping contacts you already have if you leave that switch on, exactly like importing a .vcf file. It uses Android's own SIM phone book, which the contacts permission ContactCtrl already has allows, so no new permission. On a dual-SIM phone it reads the SIM your phone uses by default (choosing the other SIM would need the phone-state permission). Nothing on the SIM is changed. Not yet tried on a phone.
- **Show a contact as a QR code.** On a contact's page, the menu has *Show as QR code*. Another phone can scan it with its camera to add the contact: name, company and job title, numbers, emails, addresses, websites, birthday, nickname and note (vCard 3.0). The photo is never included, and when the note makes it too big for a QR code it is left out (the dialog says so); a contact that is still too big gets a message to share it as a file instead. The code is drawn black on white in every theme so all scanners read it. ContactCtrl makes the code itself, without a new library and without going online. The encoder was checked on a computer by reading back codes of every QR size at both error-correction levels with a separate QR reader; not yet tried on a phone.
- **Show one account or one label.** The new filter button next to + on the Contacts tab lets you show all contacts, only the contacts of one account (this phone, a DAVx⁵ or Exchange account, WhatsApp, Signal, ...) or only the contacts with one label. A line under the search box says what is shown and how many; its × shows everything again. Search works inside the filter. The choice lasts while the app runs.
- **Labels.** Labels group contacts (Family, Work, ...). They are Android's own contact groups, so labels from your sync accounts show up and changes sync back where the account supports it. On a contact's page, *Labels* shows which labels it has; tap it to switch labels on or off. *Manage labels* (from there or from the filter) adds a label (in the account you pick), renames it or deletes it after you confirm. Deleting a label never deletes the contacts in it. A label belongs to one account and can only hold contacts saved in that account; labels an account made itself (such as "My Contacts") are shown but can't be renamed or deleted. Not yet tried on a phone.
- **Messaging handles and relations on a contact.** When you add or edit a contact there are two new sections. *Messaging* holds handles such as a Signal number, a Matrix ID or a Telegram username, each with its service: Signal, WhatsApp, Telegram, Matrix, Threema, XMPP, SIP, or a name you type. *Relations* holds the people a contact is related to (spouse, partner, child, mother, father, sister, brother, friend, manager, assistant, "referred by" and more, or your own label). Both show on the contact's page under About; long-press one to copy it. They are saved in Android's own contacts storage the standard way (a messaging handle with the service name in Android's "custom protocol" field, as Android has asked since version 12), so other contacts apps and sync accounts that support these fields see them too. Handles saved by older apps with one of Android's retired service numbers (AIM, Skype, Jabber, ...) show with that name and are only rewritten if you change them. Not yet tried on a phone.

### Project

- **Checksum list regenerated without the old entries.** `gradle/verification-metadata.xml` was regenerated on CI with the fixed workflow and committed exactly as printed (its SHA-256 matches the log). It now lists only the 378 components the build downloads today; the leftovers from earlier library and build-tool versions are gone.
- **The checksum list no longer collects old entries.** The hand-started workflow that regenerates `gradle/verification-metadata.xml` used to add the new checksums to the existing file, so entries for library versions that are no longer used stayed in it. It now clears the old entries first and writes only what the build uses, and prints the file's SHA-256 so the copy that gets committed can be checked against the log. Nothing in the app changes.
## v0.3.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published. It is the first release built with the new build tools (Android Gradle Plugin 9, stricter code shrinking), so update over your installed version and check that your data and settings are still there.

### Privacy

- **A shared contact no longer stays in the app's cache until the next share.** Sharing a contact writes a plain `.vcf` file to ContactCtrl's private cache so the app you picked can read it. That file used to stay there until you shared something else, which could be months. Files left from an earlier run are now deleted in the background every time ContactCtrl starts, and again whenever the app lock locks. Only files written before the app last started are removed, so a share that is in progress, or one the other app is still reading, is never touched: a file shared since the app started stays exactly as long as before (until the next share or the next start). Sharing itself works the same.

### Faster

- **Searching a big address book does less work per letter.** For every contact, on every letter typed, the app prepared the search text again (accents removed, split into words, digits picked out) and split the contact's name into words again. The search text is now prepared once per letter for the whole list, and each contact's name is split once when the contacts load. This applies to the Contacts tab and to "Add to a contact". The results are exactly the same (checked on a computer by comparing the old and new matching over 46,800 name, number, email and search-text combinations).
- **Reloading contacts is a little lighter.** While reading phone numbers, the digits of every number already kept for a contact were worked out again for each further number of that contact, to skip repeats. They are now remembered. The same numbers are kept, in the same order.
- **Scrolling the contact list reuses rows better.** Rows and letter headings are now marked as two different kinds of list item, so a row scrolled off the screen is reused for another row instead of being rebuilt as a heading (and the other way round). Nothing looks different.

### Changed

- **Newer build tools and libraries; nothing changes in how ContactCtrl looks or works.** ContactCtrl is now built with the current stable toolchain: Android Gradle Plugin 8.11.1 → 9.5.1, Gradle 8.14.3 → 9.9.2, Kotlin 2.2.0 → 2.4.21 (with its Compose and serialization compiler plugins) and Compose BOM 2025.07.00 → 2026.09.00 (Compose 1.12.1, Material 3 1.4.0). The libraries move to the versions the newest Ctrl apps use: activity-compose 1.10.1 → 1.13.0, core-ktx 1.16.0 → 1.18.0, lifecycle 2.9.1 → 2.10.0, navigation-compose 2.9.1 → 2.9.8, datastore-preferences 1.1.7 → 1.3.0, kotlinx-serialization-json 1.9.0 → 1.13.0 and kotlinx-coroutines-android 1.10.2 → 1.11.0. No library was added, and the app is still Google-free (CI checks it).
- **Compiled against the Android 17 SDK.** Compose 1.12 needs `compileSdk` 37, so that goes from 36 to 37. The Android versions ContactCtrl runs on and targets are unchanged (`minSdk` 35, `targetSdk` 36), so Android treats the app exactly as before.
- **The app is shrunk by a newer R8.** Android Gradle Plugin 9 compiles Kotlin itself (the separate Kotlin Android plugin is gone from the build files) and brings the R8 version that understands Kotlin 2.4. The release APK is about 3.1% larger (3.50 MB → 3.61 MB). The newer R8 shrinks more strictly than the old one, so the release build needs a check on a phone before the next version is published. ContactCtrl's own keep rules are unchanged.
- The checksums Gradle verifies every download against (`gradle/verification-metadata.xml`) were regenerated on CI for the new versions. Built and checked by CI only; not yet run on a phone.

### Project

- **The signing key is no longer available to the build.** Until now the release key and its passwords were present in the same GitHub Actions job that runs Gradle, so any build plugin or library could in principle have read them. The build job now has no secrets at all and produces an unsigned APK. A separate job, which checks out no code and runs no Gradle, signs it with `apksigner`, and then refuses to hand it on unless it is signed by exactly the certificate published in SECURITY.md and README.md and its contents are identical to what the build job made. Same key and same signature scheme (v2) as before, so updates will install as usual. This changes how ContactCtrl is built and signed, not the app itself. Seen working on CI for builds that don't publish; the publishing step runs with it for the first time at the next release.
- **The build checks that the published fingerprint and the enforced one are the same.** If `build.yml`, SECURITY.md and README.md ever disagree about the signing certificate, the build fails.
- **Builds no longer use a saved Gradle cache, and the Gradle plugin portal is no longer a plugin source.** Other Ctrl apps had builds fail with "Dependency verification failed ... from repository Gradle Central Plugin Repository", and keep failing until the caches were deleted by hand. The cause (found on DateCtrl): when Maven Central can't be reached for a moment, Gradle takes the Kotlin plugin "marker" file from the Gradle plugin portal instead, the portal serves a different file under the same name, verification rightly refuses it, and the failed run then saved that state as the cache for every later run. Now every build and the code scan start from a clean Gradle home, and `settings.gradle.kts` lists only Maven Central and Google's Maven for plugins. Dependency verification itself is unchanged and as strict as before. Builds take a minute or two longer.

## v0.2.1 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Faster

- **Searching no longer stutters with a big address book.** For every contact, on every letter you typed, the app rebuilt the small text pattern it uses to ignore accents ("zoe" finds "Zoë"). It is now built once and reused. Search results are exactly the same as before.
- **Scrolling past contacts without a picture is lighter.** The pattern used to work out a contact's initials was rebuilt for every such row each time it was drawn; it is now built once. The initials are the same.
- **Ticking contacts in a long list is smoother.** Favourites and the A-Z sections were worked out again every time the list was rebuilt, for example on each contact you ticked while selecting. They are now worked out only when the contacts, the search text or the sort order change.
- **Looking for duplicates is a little quicker.** The three text patterns used to compare names (accents, punctuation, extra spaces) were rebuilt for every contact; they are now built once. The duplicate groups found are the same.

### Privacy

- **The app is hidden from screenshots from its very first frame when it opens locked.** The "not in screenshots or screen recordings" protection was switched on a moment after the first frame was drawn, so the very first frame of the lock screen was not covered. It is now set before anything is drawn when the app lock is on (and when "Hide in screenshots" is already known to be on), in the main window and in the windows other apps open to pick, add or edit a contact. Nothing changes while the app is open.

### Not changed

- ContactCtrl has no home-screen widgets and no Quick Settings tiles, so there was nothing to remove.

## Project updates (2026-11-19)

### Project

- **The weekly code scan no longer fails at random.** CodeQL has to watch the app being compiled, but its build could take the compiled code from Gradle's build cache instead, and then the scan stopped with "CodeQL detected code written in Java/Kotlin but this run didn't build any of it" (and GitHub mailed a failed run). The scan's build now runs with `--no-build-cache`, so the code is always compiled. No app changes, so no new version.

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
