<h1 align="center">CallTags</h1>

<p align="center">
<img alt="CallTags icon" src="graphics/icon.png" width="96" />
</p>

A minimal Android phone app that lets you label calls with free text and search them later.

Because maintaining an updated list of contacts was always an awful idea.

- Dialpad and History are all you need.
- Tap a call in History to add labels like `insurance` or `bob's second phone`.
- Search History by number (all calls with it and their labels) or by label (every call that got it).
- While a labelled number is calling, the in-call screen shows what earlier calls were about.
- Give labels properties on the label properties screen (the sliders icon in History): block calls from numbers with a label, ring only for some labels, limit either to certain hours, highlight a label on the incoming call screen, hide labelled numbers from History, suggest a label on every call, or give it its own color. Blocking is just a `Blocked` label that blocks calls.
- Nothing leaves the device.

CallTags works as the default phone app (Settings → Apps → Default apps → Phone).

<p align="center">
<img alt="Edit a call's labels" src="https://github.com/user-attachments/assets/6f9eaedd-6868-453e-a454-5386a5a3a8a2" width="24%" />
<img alt="Suggestions from history" src="https://github.com/user-attachments/assets/046a017c-cfd6-4971-adc2-3274b70e76e4" width="24%" />
<img alt="History with labels" src="https://github.com/user-attachments/assets/513116d5-62cc-46b8-8ec4-cb0945e5d156" width="24%" />
<img alt="Search by label" src="https://github.com/user-attachments/assets/c77b07f9-48a7-467e-87be-04b18b881e1f" width="24%" />
</p>

I made it because I've been using my history of calls as my contact list, by memorizing numbers or parts of numbers, helped by the history. This method would benefit greatly by some labelling of past calls, even if the labels are just someone's name. It is also great for when you want to keep track of some phone number but you don't want that person to be forever in your canonical contact list, as your interaction with them will last for only a few days or weeks.

Other people may find it useful for labeling other things, like the topic of each call. It's a very simple functionality, but the possibilities are many, I assume.

## Install

- **[Obtainium](https://obtainium.imranr.dev):** add `https://github.com/andunieee/calltags`, or tap [Get it on Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/andunieee/calltags) on your phone.
- **Zapstore:** install [Zapstore](https://zapstore.dev) and search for CallTags.
- **Manually:** download the APK from [Releases](https://github.com/andunieee/calltags/releases).

## Building

```sh
./gradlew assembleDebug
```

## Releasing

Pushing a `v1.2.3` tag runs `.github/workflows/release.yml`, which builds and signs the APK, creates the GitHub release and publishes it to Zapstore (`zapstore.yaml`). It needs these repository secrets:

- `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`: the release keystore, base64-encoded, and its passwords.
- `ZAPSTORE_OWNER_NSEC`: the `nsec` (or a `bunker://` URL) of the Nostr key that owns the Zapstore listing. The publish step (`packaging/zapstore`) signs the NIP-C1 certificate proof with the release keystore, so it has to be the same key that signed the APK.

## License

GPL-3.0. CallTags is a fork of [Fossify Phone](https://github.com/FossifyOrg/Phone).
