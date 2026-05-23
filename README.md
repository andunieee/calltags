# CallTags

<img alt="CallTags icon" src="graphics/icon.png" width="96" />

A minimal Android phone app that lets you label calls with free text and search them later.

Because maintaining an updated list of contacts was always an awful idea.

- Dialpad and History, nothing else, yet it replaces your contacts list.
- Tap a call in History to add labels like `insurance` or `bob's second phone`.
- Search History by number (all calls with it and their labels) or by label (every call that got it).
- While a labelled number is calling, the in-call screen shows what earlier calls were about.
- Nothing leaves the device.

CallTags works as the default phone app (Settings → Apps → Default apps → Phone).

I made it because I've been using my history of calls as my contact list, by memorizing numbers or parts of numbers, helped by the history. This method would
benefit greatly by some labelling of past calls, even if the labels are just someone's name. It is also great for when you want to keep track of some phone
number but you don't want that person to be forever in your canonical contact list, as your interaction with them will last for only a few days or weeks.

Other people may find it useful for labeling other things, like the topic of each call. It's a very simple functionality, but the possibilities are many, I
assume.

## Install

- **[Zapstore](https://zapstore.dev):** search for CallTags.
- **[Obtainium](https://obtainium.imranr.dev):** add `https://github.com/andunieee/calltags`, or tap [Get it on Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/andunieee/calltags) on your phone.
- **Manually:** download the APK from [Releases](https://github.com/andunieee/calltags/releases).

## Building

```sh
./gradlew assembleDebug
```

## License

GPL-3.0. CallTags is a fork of [Fossify Phone](https://github.com/FossifyOrg/Phone).
