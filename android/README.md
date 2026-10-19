# Momentwise for Android

Native Android app for Momentwise. The public site is the product page. The
browser files under `prototype/` are a design reference, not the app.

Proposed application ID: `com.nawzblaze.momentwise`.
This build is not published under that ID.

## What this module is

A single-module Jetpack Compose app:

- Kotlin, Material 3 with the Momentwise palette (not dynamic colour).
- System theme by default. Light and Dark are available from the first screen.
- Welcome, Home, Apps, Configure Rule, opening-notice preview, reminder preview, Settings.
- Per-app rules in DataStore, keyed by package name. Validation, rule isolation, the usage-event reducer, and the reminder session clock have JVM unit tests.
- Launchable apps from the device. Foundation sample ids are dropped, not copied onto real apps.
- Usage Access, display over other apps, and notifications are explained before a rule is enabled. Decline leaves monitoring off.
- A `specialUse` foreground service and a pause (overlay, or a notification if overlay is refused). Quit is a home intent. This has not been run on a device here.

The intervention design, and what is still unverified on a device, is in
[`ANDROID_IMPLEMENTATION_PLAN.md`](../ANDROID_IMPLEMENTATION_PLAN.md).

## Build

Requires JDK 17, Android SDK 35, and Android Gradle Plugin 8.7.3 (Gradle 8.11.1
via the wrapper).

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions workflow: [`.github/workflows/android.yml`](../.github/workflows/android.yml).
That is the reproducible build for this milestone. A local SDK is not assumed.
A green unit-test run means `testDebugUnitTest`, `assembleDebug`, `assembleRelease`,
`assembleDebugAndroidTest`, and `lintDebug` succeeded on Ubuntu with JDK 17 and
SDK 35. `assembleRelease` without signing secrets produces an unsigned APK. That
is not a public release. A separate emulator workflow runs
`connectedDebugAndroidTest` on API 34 and API 35 when it is green. A green
emulator run is still not a physical-device result, and it does not prove that a
pause appears over another app.

## Signed release

The release workflow is `.github/workflows/android-release.yml`. It runs on a
`v*` tag or by hand. It publishes nothing unless these Actions secrets exist:

- `MOMENTWISE_KEYSTORE_BASE64` — base64 of a `.jks` created once and backed up outside Git
- `MOMENTWISE_KEYSTORE_PASSWORD`
- `MOMENTWISE_KEY_ALIAS`
- `MOMENTWISE_KEY_PASSWORD`

Do not commit the keystore or the passwords. Do not generate a new key on every
build. After a signed `Momentwise.apk` is attached to a GitHub Release and the
URL below downloads, the website can point at it:

`https://github.com/NawzBlaze/momentwise/releases/latest/download/Momentwise.apk`

That file is not published yet.

## Layout

```text
android/app/src/main/java/com/nawzblaze/momentwise/
├── data/            Rule model, validation, repositories
├── data/local/      DataStore + JSON mapping
├── navigation/      Screen routes
├── ui/theme/        Tokens ported from prototype/styles.css
├── ui/components/   Shared controls
└── ui/screens/      The seven product screens
```

The approved mark is `res/drawable/ic_logo.xml`, converted from `assets/logo.svg`
without redrawing the geometry.
