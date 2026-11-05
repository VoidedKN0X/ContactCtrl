# ContactCtrl

**A private, offline contacts app that replaces your phone's contacts app.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ContactCtrl?label=download)](https://github.com/VoidedKN0X/ContactCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) ![Offline](https://img.shields.io/badge/internet-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ContactCtrl shows, searches, adds and edits the contacts on your phone and in your accounts (the phone itself, DAVx⁵, Exchange and others), imports and exports .vcf files, and gives other apps the contact pickers they expect. It never goes online and keeps no copy of your contacts of its own.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ContactCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Works fully offline.** ContactCtrl doesn't have the internet permission and never goes online. It reads and writes Android's own contacts storage, which every app and sync account shares, so nothing is duplicated
- **Fast search** over names, nicknames, phone numbers (any part, with or without the country code) and email addresses. Accents don't matter: "zoe" finds "Zoë"
- **Alphabetical list** with letter headers and your favourites on top; first or last name first, sorted by first or last name
- **Contact page** with one-tap call, message and email, every number, email, address (opens in your maps app), website, date and note. Long-press anything to copy it
- **Add and edit** names (with prefix, middle name, suffix and nickname), phone numbers, emails and addresses with their labels (mobile, work, your own label), company and job title, birthdays and other dates (with or without the year), websites, notes and a photo (cropped and resized on the phone)
- **Safe with every account:** only what changed is written, and only to accounts that sync changes back. Contacts from messengers like WhatsApp or Signal are shown but never changed, so a sync can't undo or duplicate your edits
- **Choose where new contacts go:** this phone only, or a sync account like DAVx⁵
- **Favourites** tab, a ringtone per contact, and sharing a contact as a .vcf file
- **Select several contacts** (long-press) to share, favourite or delete them at once
- **Import and export .vcf files** (vCard 2.1, 3.0 and 4.0, photos included), with duplicates skipped on import. Opening a .vcf file from your file manager imports it too
- **Works with your other apps:** "add to contacts" and "edit contact" from other apps, and the contact, phone number, email and address pickers apps like messengers use. The app that asked gets only the one contact or number you tap
- **App lock** with its own PIN and optional fingerprint unlock, and **Hide in screenshots** / **Hide in recent apps** (both off until you turn them on)
- **Ctrl / Signal look**, shared by every Ctrl app: dark and light, following your phone's dark theme setting, high contrast, one violet accent, Space Grotesk and JetBrains Mono; landscape, tablet and foldable layouts

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Works on any Android ROM as a normal app: stock Android, LineageOS, GrapheneOS, CalyxOS, /e/OS and the phone makers' own systems. It doesn't need root or to be built into the system.
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google libraries, and the app never contacts Google.

## Install

1. Open the [latest release](https://github.com/VoidedKN0X/ContactCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `ContactCtrl-v0.1.0.apk`). The "Source code" zip and tar.gz files are not the app; you don't need them.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons above.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds ContactCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/ContactCtrl` in Obtainium by hand.

**Make it your contacts app:** ContactCtrl works next to your phone's own contacts app. To use it instead, put ContactCtrl on your home screen and hide the old app: long-press it and choose **Uninstall** or **Disable** (some phones only allow disabling). Other apps then use ContactCtrl to add, edit and pick contacts. Your contacts themselves don't move: they stay in Android's contacts storage and your accounts.

## Getting started

- **Contacts:** search at the top, tap a contact to open it, long-press to select several. The + button adds a contact.
- **A contact:** call, message or email with the big buttons; tap a number to call it, the speech bubble to text it. The star makes it a favourite, the pencil edits it, the menu shares or deletes it.
- **Favourites:** your starred contacts.
- **Import and export:** in Settings, import a .vcf file from another phone or app, or export every contact to one file.

Settings and About are behind your profile picture at the top right of every tab.

## Is it safe to install?

- **Open source.** All of ContactCtrl's code is in this repo, under the GPL-3.0 licence. Anyone can read it.
- **Built by GitHub, not on a personal computer.** Every release on the Releases page is built by [the build workflow](.github/workflows/build.yml) on GitHub Actions, straight from the code in this repo. Each build also checks that the Gradle wrapper is an official Gradle release, that the app has no network permission and that it has no Google libraries.
- **Always signed with the same key.** Android refuses to install an update signed with a different key over ContactCtrl, and the CtrlF-Droid repo only accepts APKs signed with this key. The SHA-256 fingerprint of ContactCtrl's signing certificate is:

  ```
  B5:30:D1:03:42:50:CC:75:E1:1D:13:9C:26:3B:95:C8:13:13:1F:4A:8E:6D:56:F0:4D:5C:FE:80:8B:29:B9:55
  ```

  Advanced users can check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Check a download.** Every release lists the APK's SHA-256 in its release notes, so you can compare it with `sha256sum ContactCtrl-vX.Y.Z.apk`. Each release APK also has a signed build provenance attestation: with the [GitHub CLI](https://cli.github.com/), `gh attestation verify ContactCtrl-vX.Y.Z.apk -R VoidedKN0X/ContactCtrl` proves it was built by this repo's build workflow from the tagged commit. The signing certificate check above still applies.
- **Checked dependencies and workflows.** Every library and Gradle plugin the build downloads is checked against the SHA-256 in [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml), so a changed download fails the build. The workflows are checked with [zizmor](https://docs.zizmor.sh/), each job gets only the permissions it needs, and every action is pinned to a commit. [CodeQL](https://codeql.github.com/) scans the code every week.

**Permissions.** ContactCtrl has **no internet permission**: it can't go online at all. It asks only for what it needs, when it needs it:

| Permission | What ContactCtrl uses it for |
| --- | --- |
| Read your contacts (`READ_CONTACTS`) | Showing and searching your contacts. |
| Modify your contacts (`WRITE_CONTACTS`) | Adding, editing, favouriting, importing and deleting contacts when you ask. |
| Directly call phone numbers (`CALL_PHONE`) | Optional: tapping a number calls it straight away. Without it, numbers open in your phone app first. Turn this off in Settings with *Tap a number to call*. |
| Use biometric hardware (`USE_BIOMETRIC`) | Optional fingerprint unlock for the app lock. |
| Hide other apps' floating windows (`HIDE_OVERLAY_WINDOWS`) | Hide other apps' floating windows over the PIN screen (and the app lock's other screens and delete confirmations), so nothing can be drawn over them. Granted at install, no prompt. |

Android also lists a "dynamic receiver" permission (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) that AndroidX adds to every app so only ContactCtrl itself can send it internal broadcasts; it gives the app no access to anything. ContactCtrl has no internet, location, storage, microphone or camera permissions.

See [Privacy](#privacy) for what happens to your data.

## App lock

Turn it on under **Settings > Privacy and security > App lock** (tap your profile picture at the top right, then **Settings**). It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, checked by the phone's secure hardware. The PIN is still needed after the phone restarts and at least every 72 hours.
- **The PIN is never stored.** It's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts.
- **Forgot your PIN?** It can't be recovered. The lock screen offers to reset ContactCtrl's own settings and turn the lock off, after you confirm with your phone's own screen lock (PIN, pattern, password or fingerprint), so someone holding your unlocked phone can't simply reset it. On a phone without a screen lock the reset needs no confirmation. Your contacts are not deleted: they belong to your phone and accounts, not to ContactCtrl.
- The contact pickers other apps open are behind the app lock too.

**Hide in screenshots** and **Hide in recent apps** (both off by default): the first keeps ContactCtrl out of screenshots and screen recordings, the second shows a blank card in the recent-apps view while screenshots still work. With the app lock on, the recent-apps view always shows a blank card.

## Privacy

**Telemetry-free.** ContactCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It has **no internet permission**, works fully offline and never connects to the internet. Every build checks that the app has no network permission and no Google libraries.

- Your contacts stay in Android's contacts storage and in the accounts you chose. ContactCtrl keeps no copy of them; its own files hold only its settings.
- A contact you share becomes a .vcf file in ContactCtrl's private cache, readable only by the app you share it with, and only for that share.
- Exporting writes a file only to the place you choose. Nothing is uploaded.
- Other apps can hand ContactCtrl a .vcf file to import only as a `content://` link they share with it; ContactCtrl always shows what's in the file and imports nothing until you tap the Import button.
- Copied numbers and addresses are marked as sensitive, so Android 13+ doesn't show them in the clipboard preview.
- ContactCtrl is left out of Android's cloud backup and its phone-to-phone transfer.
- The Support, Report a bug, Source code and Licence links on the You page open in your browser, only when you tap them.

## How ContactCtrl is made

ContactCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/ContactCtrl.git
   cd ContactCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

**Dependency verification.** Gradle checks every library and plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml`, and the build fails if one doesn't match or is missing. When you add or update a dependency or plugin, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), decode the file from its log (between the BEGIN and END markers: `base64 -d | gunzip`), check the diff and commit it together with the dependency change.

## Support the project

ContactCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, tap your profile picture: **About** at the bottom of the You page has **Support ContactCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/ContactCtrl/issues). Please report bugs and ideas with the [bug report and feature request forms](https://github.com/VoidedKN0X/ContactCtrl/issues/new/choose). Starring the repo helps too.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| **ContactCtrl** (this app) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

ContactCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

Fonts, bundled in the app (nothing is downloaded): [Space Grotesk](https://github.com/floriankarsten/space-grotesk) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), both under the SIL Open Font License 1.1 ([FONT-LICENSE-SpaceGrotesk.txt](FONT-LICENSE-SpaceGrotesk.txt), [FONT-LICENSE-JetBrainsMono.txt](FONT-LICENSE-JetBrainsMono.txt)).
