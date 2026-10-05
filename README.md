# CallTags

<img alt="CallTags icon" src="graphics/icon.png" width="96" />

A minimal Android phone app that lets you label calls with free text and search them later.

- **Dialpad** and **History**, nothing else.
- Tap a call in History to add labels like `insurance` or `topic 2`.
- Search History by number (all calls with it and their labels) or by label (every call that got it).
- While a labelled number is calling, the in-call screen shows what earlier calls were about.
- History and labels live in the app's own database, so they survive clearing the system call log. Nothing leaves the device.

CallTags works as the default phone app (Settings → Apps → Default apps → Phone).

## Install

- **[Zapstore](https://zapstore.dev):** search for CallTags.
- **[Obtainium](https://obtainium.imranr.dev):** add `https://github.com/andunieee/calltags`, or tap [Get it on Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/andunieee/calltags) on your phone.
- **Manually:** download the APK from [Releases](https://github.com/andunieee/calltags/releases).

## Building

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
```

Release builds are made by GitHub Actions when you push a `v*` tag; see [PUBLISHING.md](PUBLISHING.md).

## License

GPL-3.0. CallTags is built on [Fossify Phone](https://github.com/FossifyOrg/Phone) and [Fossify Commons](https://github.com/FossifyOrg/Commons).
