# Momentwise for Android

Native foundation for Momentwise. The website and the browser prototype at the
repository root are unchanged and remain the visual reference.

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

The intervention design, and why it is not built yet, is in
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
A green run means `testDebugUnitTest`, `assembleDebug`, and `lintDebug` succeeded
on Ubuntu with JDK 17 and SDK 35. It is not a device or emulator result. It does
not prove that a pause appears, that Quit returns home, or that a session
survives process death.

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
