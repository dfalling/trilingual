# Trilingual

Native Android app (Kotlin, Jetpack Compose) for writing in one language and reading it back in
several others at once, e.g. type in English and see Spanish and Portuguese together.
Translations come from Claude using your own Anthropic API key. Distributed through GitHub
Releases (works with [Obtainium](https://github.com/ImranR98/Obtainium)), with no Google Play
Services dependency.

## Using it

- **Languages:** the chips above the text box are your active languages (English and Spanish
  by default). Tap one to make it the language you're writing in; everything you send is
  translated into all the others. **+ Languages** adds or removes languages (minimum two).
- **History:** every translation is saved on the device. The feed scrolls like a chat, newest
  at the bottom. Tap a translation to copy it. The ⋮ menu copies the original, puts it back in
  the composer to edit and resend, or deletes it.
- **Settings:** API key (encrypted with an Android Keystore key, never backed up). **Get a key**
  opens the Console's API keys page, **Paste** fills the field from the clipboard, and saving
  checks the key against Anthropic's free model-list endpoint first, so a bad key is caught
  right away. Settings also has the model (Opus 5.5 by default; Sonnet 5.5 and Haiku 5.5 are
  faster and cheaper) and clear history.

## How translation works

`translate/Translator.kt` calls the Messages API through the official
[Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java). One request covers all
target languages. Structured outputs (`output_config.format` with a JSON schema whose
`language` enum is the target codes) make the reply parseable, and effort is set to `low`
because translation doesn't need deep reasoning. On Opus and Sonnet, the request opts into
server-side refusal fallbacks (`server-side-fallback-2026-07-01`). Prompt and parsing live in
`translate/TranslationPrompt.kt` and are unit-tested.

## Setup

1. Install [mise](https://mise.jdx.dev), then run `mise install` (JDK 25, sops and age, pinned in `mise.toml`).
2. Install the Android SDK with platform 37 and point `local.properties` at it
   (`sdk.dir=/path/to/Android/Sdk`), or open the project in Android Studio.
3. `git config core.hooksPath .githooks` runs lint and unit tests before each push.

```sh
./gradlew installDebug                                     # build and install on a connected device
./gradlew lintDebug testDebugUnitTest assembleDebug        # what CI runs
```

### Emulator (headless, scriptable)

```sh
scripts/emulator.sh                 # boot the Pixel_7 AVD headless and wait for it
scripts/emulator.sh --window        # or with a normal window, to use it by hand
./gradlew installDebug
adb exec-out screencap -p > screen.png
scripts/emulator.sh --stop
```

The script runs the emulator with `-gpu swangle_indirect`. The emulator's default software
renderer (`gles_swiftshader`) segfaults on Fedora with Mesa 26, and `-gpu host` needs a window.
`--window` uses `-gpu host`; it needs `DISPLAY` set, so run it from a desktop terminal.

## Releasing

`.github/workflows/release.yml` builds a signed APK on every push to `main` (versioned
`0.0.<commit count>`) and on every `v*` tag (versioned from the tag), and attaches it to a GitHub
Release. `versionCode` is the commit count, so it only goes up. `workflow_dispatch` runs build an
APK as a workflow artifact without cutting a release.

### Signing secrets (SOPS + age)

The upload keystore and its passwords live in `release-secrets.enc.yaml`, encrypted with SOPS to
the age recipient in `.sops.yaml` (the same key as culpeos-android). CI decrypts it with one GitHub
Actions secret:

| Secret         | Value                                              |
| -------------- | -------------------------------------------------- |
| `SOPS_AGE_KEY` | the age private key (`AGE-SECRET-KEY-1…`)          |
| `RENOVATE_TOKEN` | token for the Renovate workflow                  |

This encrypted file is the only copy of the signing key. If it's lost, existing installs can't
take updates, so keep the age key backed up.

```sh
sops release-secrets.enc.yaml    # view or edit (needs the age key in ~/.config/sops/age/keys.txt)
```

Local signed build, the same way CI does it:

```sh
sops -d --output-type json release-secrets.enc.yaml > /tmp/s.json
jq -r .android_keystore_base64 /tmp/s.json | base64 -d > /tmp/release.keystore
RELEASE_STORE_FILE=/tmp/release.keystore \
RELEASE_STORE_PASSWORD=$(jq -r .android_keystore_password /tmp/s.json) \
RELEASE_KEY_ALIAS=$(jq -r .android_key_alias /tmp/s.json) \
RELEASE_KEY_PASSWORD=$(jq -r .android_key_password /tmp/s.json) \
./gradlew assembleRelease
rm /tmp/s.json /tmp/release.keystore
```

Without those variables, `assembleRelease` signs with the debug key. That's useful for checking
that R8 minification still works.

## Repo layout

```
app/src/main/java/com/dfalling/trilingual/
  MainActivity.kt, TrilingualApp.kt    entry point, manual dependency wiring
  data/        Language catalog, DataStore settings, encrypted API key, Room history
  translate/   Anthropic SDK call, prompt, schema, response parsing
  ui/          Compose screens and the ViewModel
gradle/libs.versions.toml              dependency versions (Renovate keeps them current)
release-secrets.enc.yaml               SOPS-encrypted signing keystore and passwords
```
