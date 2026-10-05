# Building and publishing CallTags

CallTags is distributed through **GitHub releases** (which [Obtainium](https://obtainium.imranr.dev) follows) and **[Zapstore](https://zapstore.dev)**.

## How releases work

| Workflow                        | Trigger                       | Does                                                                                     |
| ---                             | ---                           | ---                                                                                      |
| `.github/workflows/ci.yml`      | push to `main`, pull requests | builds a debug APK, runs lint, uploads the APK as an artifact                            |
| `.github/workflows/release.yml` | pushing a tag `vX.Y.Z`        | builds a signed APK, creates a GitHub release with it, publishes the release to Zapstore |

The version comes from the tag: `v1.2.3` → versionName `1.2.3`, versionCode `10203`. Minor and patch must stay below 100. Nothing needs to be bumped by hand. Add a `## [1.2.3]` section to `CHANGELOG.md` before tagging; Zapstore shows it as the release notes.

```sh
git tag v1.0.0
git push origin v1.0.0
```

## 1. Create the signing key (once)

```sh
keytool -genkeypair -v -keystore calltags-release.jks -alias release \
  -keyalg RSA -keysize 4096 -validity 10000
```

**Back this file and its passwords up somewhere safe, outside the repo** (`*.jks` is git-ignored). There's no store holding a copy for you. Android only installs an update when it is signed with the same key as the installed app, so if you lose the key, every user has to uninstall (and lose their labels) before they can install a build signed with a new one.

## 2. Add the signing secrets to GitHub (once)

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret                   | Value                                        |
| ---                      | ---                                          |
| `SIGNING_KEYSTORE_BASE64` | output of `base64 -w0 calltags-release.jks` |
| `SIGNING_KEY_ALIAS`       | `release`                                   |
| `SIGNING_KEY_PASSWORD`    | the key password                            |
| `SIGNING_STORE_PASSWORD`  | the keystore password                       |

From now on every `v*` tag produces a signed GitHub release, and Obtainium users get it.

To build a signed release locally instead, copy `keystore.properties_sample` to `keystore.properties`, fill it in, and run `./gradlew assembleRelease`.

## 3. Obtainium

Nothing to set up. Users add the app in Obtainium with the repository URL `https://github.com/andunieee/calltags` (or the "Get it on Obtainium" link in the README). Obtainium picks up each new GitHub release.

## 4. Zapstore (once)

Zapstore listings are Nostr events signed by your Nostr key. The relay whitelists you the first time you publish, after checking that the `pubkey` in this repo's `zapstore.yaml` matches the key that signed the events.

1. **Put your npub in `zapstore.yaml`** (replace `npub1REPLACE_WITH_YOUR_NPUB`) and commit and push it **before** the first Zapstore publish.
2. **Give CI a way to sign.** Add a secret `ZAPSTORE_SIGN_WITH` holding one of:
   - a NIP-46 **bunker URL** (`bunker://…`). Recommended: CI never sees your private key, and you can revoke it.
   - your **nsec**. This works, but anyone with write access to the repo's workflows could read it.

   If the secret is missing, the workflow skips Zapstore and still makes the GitHub release.
3. **Link your APK signing key to your Nostr identity** (recommended, run once locally). This proves to Zapstore users that the APKs published under your npub are signed by your key:
   ```sh
   # zsp releases: https://github.com/zapstore/zsp/releases
   SIGN_WITH=<bunker-url-or-nsec> zsp identity --link-key calltags-release.jks
   ```

After that, each `v*` tag publishes to Zapstore too: `zsp` takes the APK from the GitHub release and the listing (name, description, icon, tags, license) from `zapstore.yaml`.

To check the config without publishing anything:

```sh
zsp publish --check zapstore.yaml   # needs at least one GitHub release with an APK
```

Screenshots are optional; to add them, list image paths under `images:` in `zapstore.yaml`. Use made-up numbers and labels, not your real call history.

## Icon and artwork

The source artwork is `graphics/icon.svg`, rendered to `graphics/icon.png` (used as the Zapstore icon). The launcher icon itself is the vector `app/src/main/res/drawable/ic_launcher_foreground.xml` on the amber background from `res/values/colors.xml`.
