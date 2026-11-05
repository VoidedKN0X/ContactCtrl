# ContactCtrl

Contacts app in the VKNXWorks Ctrl series. Kotlin, Jetpack Compose, Material 3, manual DI (`AppContainer`), DataStore for settings. Reads and writes Android's ContactsContract provider; keeps no copy of contacts.

## Layout

- `data/contacts/ContactsRepository.kt`: loads the list (one query each for contacts, phones and emails) and a contact's detail rows; reloads on a ContentObserver.
- `data/contacts/ContactWriter.kt`: saves edits as a diff of data rows, only in raw contacts whose account syncs changes back (`Accounts.isWritable`).
- `data/contacts/VCard.kt`: vCard 2.1/3.0/4.0 parser for import; export uses the provider's own vCard.
- `Launch.kt`: parses intents from other apps (VIEW, QUICK_CONTACT, INSERT, INSERT_OR_EDIT, EDIT, SHOW_OR_CREATE_CONTACT, .vcf files).
- `ImportActivity.kt`: takes .vcf links from other apps (`requireContentUriPermissionFromCaller="read"`, so Android 15+ refuses links the sender can't read) and hands them to MainActivity.
- `EditActivity.kt`: add/edit for other apps (not single-task, so the caller gets the result). `PickerActivity.kt`: PICK / GET_CONTENT pickers.

## Hard rules

- **No `INTERNET` permission, ever** (the manifest removes it; CI fails the build if the APK has any network permission).
- **Google-free:** no Google libraries, services or SDKs, including transitive ones (CI checks `releaseRuntimeClasspath`). No WebView. Fonts are bundled.
- **Telemetry-free:** no analytics or crash reporting.
- arm64-v8a only. Every user-facing string goes in `strings.xml` (shared Ctrl strings in `strings_ctrl.xml`).
- Settings open only from the profile picture. No settings tab, menu or gear icon, no You tab.
- Must work as a normal user app on any ROM (Android 13+), not only when built into the system.
- Never write to raw contacts of read-only accounts (messengers), and never rewrite rows that didn't change.
- Pickers return exactly one row URI with a read grant; never return more than the user tapped.

## Dependency verification

Gradle checks every downloaded library and plugin against the SHA-256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or Gradle version changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/ContactCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (between the BEGIN/END markers, `base64 -d | gunzip`), check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push as the change. Never turn verification off or make it lenient.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "ContactCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- Push straight to `main`; no branches or pull requests.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
