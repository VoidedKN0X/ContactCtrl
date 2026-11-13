# Security policy

Thank you for helping keep ContactCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/ContactCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of ContactCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

ContactCtrl has no server and no internet permission: everything stays on your phone. So these count:

- Any way for ContactCtrl to send your contacts off the phone, or to an app you didn't choose
- Another app on the phone getting more than the one contact, number or address you picked for it
- Another app being able to make ContactCtrl change or delete contacts without you confirming it
- Ways around the app lock, or protected screens showing up in screenshots when they shouldn't
- Weaknesses in the app lock PIN
- Anything that lets someone run code or read files they shouldn't

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
B5:30:D1:03:42:50:CC:75:E1:1D:13:9C:26:3B:95:C8:13:13:1F:4A:8E:6D:56:F0:4D:5C:FE:80:8B:29:B9:55
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
