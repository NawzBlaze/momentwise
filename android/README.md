# Momentwise for Android

Native Android app for Momentwise. The public site is the product page. The
browser files under `prototype/` are a design reference. They are not published, and they are not the app.

Proposed application ID: `com.nawzblaze.momentwise`.
This build is not published under that ID.

## What this module is

A single-module Jetpack Compose app:

- Kotlin, Material 3 with the Momentwise palette (not dynamic colour).
- System theme by default. Light and Dark are available from the first screen.
- Welcome, Home, Apps, Configure Rule, opening-notice preview, reminder preview, Settings.
- Per-app rules in DataStore, keyed by package name. Validation, rule isolation, the usage-event reducer, and the reminder session clock have JVM unit tests.
- Launchable apps from the device. Foundation sample ids are dropped, not copied onto real apps.
- Usage Access, display over other apps, and notifications are explained before a rule is enabled. Each grant is one control. It says Allow until Android reports the grant, then Allowed. Returning from Settings re-reads Android. A tap does not mark a grant. Decline leaves monitoring off and does not delete rules.
- A new rule's pause asks "Before you scroll". Leave goes home. Continue intentionally stays, and its supporting line uses the saved interval of foreground use. Never does not promise a reminder. Dismiss for today is on the opening card and the reminder card. It suppresses that app until the next local day and keeps the rule enabled. The in-app preview uses that same card and does not start a session. A saved custom prompt is kept.
- A reminder counts accumulated time while that guarded app is in the foreground, the screen is on, and Momentwise is not covering it. Leaving the app pauses the timer. Time away, screen-off, and Momentwise UI are not counted. A gap longer than 2.5 seconds is not credited. Reboot keeps completed use and does not invent the missing gap.
- Each opening has a visit id. A callback from an older card cannot dismiss or suppress a newer visit. While the card is up, a launcher or other-package usage event does not end the visit. After Continue, a usage event timestamped while the card was up is not a new opening. A real leave after that ends the visit, so the next open can show once. The overlay stays focusable so its buttons can be tapped. This has not been confirmed on a physical phone.
- A `specialUse` foreground service and a pause. With display-over-other-apps, that pause is a visible middle card and scrim. Without it, it is a notification that does not block the other app. Leave is a home intent, not a force-stop. The one-second poll is unchanged. A shorter usage-event window during a steady poll is not a phone lag or battery measurement. This has not been run on a physical phone here.
- After process death, an unanswered opening or a due reminder is shown again if Usage Access still sees that app. An empty event window does not end the session. This decision is unit-tested, not device-tested.

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
SDK 35. The debug APK artifact is named `momentwise-debug-apk`. It is a zip of
`app-debug.apk`, not a signed release. The unit-test job may compile an
unsigned release APK and then deletes that file. It is not uploaded. A separate emulator
workflow runs `connectedDebugAndroidTest` on API 34 and API 35 when it is green.
That run includes a test which starts `com.nawzblaze.momentwise.fixture`, a
separate package, and reads the opening notice from the system notification
dump with overlay denied. It is not Instagram, not a full-screen overlay, and
not a physical-device result.

## Signed release

`.github/workflows/android.yml` does not read signing secrets. It runs tests,
lint, and the unsigned/debug build. The only workflow that reads signing
secrets is `.github/workflows/android-release.yml`, and only as a manual
`workflow_dispatch` whose ref is `refs/heads/main`. A dispatch of any other
branch fails in a job that does not read secrets. That workflow does not run
on pull requests, does not use `pull_request_target`, does not generate a
keystore, and does not publish a GitHub Release. It reads only these Actions
secrets:

- `MOMENTWISE_KEYSTORE_BASE64` — standard Base64 of the existing keystore file, one line, no `data:` prefix. Linux: `base64 -w 0 release.jks`. macOS: `base64 -b 0 -i release.jks`.
- `MOMENTWISE_KEYSTORE_PASSWORD`
- `MOMENTWISE_KEY_ALIAS`
- `MOMENTWISE_KEY_PASSWORD`

The workflow decodes that keystore into the runner temp directory, passes the
path and passwords to Gradle as environment variables, and deletes the file
afterward. It does not run `keytool -genkeypair` and does not commit the
keystore. If any secret is missing, the signing job exits 1 before Gradle and
uploads nothing. It does not print secret values.

A successful job runs `apksigner verify --verbose --print-certs`, requires
package `com.nawzblaze.momentwise` and versionName `1.0.0`, and uploads the
distinct artifact `momentwise-signed-apk`. That artifact is not a GitHub
Release. This workflow does not publish one, and the website download stays off.

## Why a configured phone can still miss the pause

This is a code diagnosis, not a log from the phone that failed. Lag and battery drain have not been measured on a phone. A JVM reducer timing and an emulator `queryEvents` sample are not that measurement.

- Closing the pause without Leave or Continue used to set an in-memory “cleared” flag. Leave then dismissed the pause, which set the flag again. The next opening saw the flag and did not show. That close is not Continue, does not start a reminder, and does not suppress the next visit. A stop that ends a usage window is a leave even if the launcher event is missing. A stop and resume in the same window is not a new visit. A pause activity can stop the app underneath, so that stop is held and a resume within 3 seconds is the same visit.
- Continue schedules from the package on the pause, not only from the stored session. A lost opening write must not drop the interval. Never still shows the opening and schedules nothing. Dismiss-for-today stops reminders until the next local day and does not disable the rule. Snooze rewrites the due time. Clearing a reminder without a choice does not cancel the timer; the poll may show it again after a 60-second hold, which is not an exact alarm. Turning monitoring off, or losing a required grant, ends the visit so a stale timer does not fire later. Saved rules stay.

- Usage events are often written several seconds after a resume. The old query advanced to "now" and looked back one second, so a late Instagram resume was never read. The monitor now re-reads a bounded 20-second window and ignores events it has already applied.
- If the process is dead when the guarded app opens, a later start looks at the last 60 seconds. If that app is still the last resume and no opening was shown, the opening is shown then. It is not a second opening after Continue.
- While the screen is off, the monitor does not query UsageStats. A screen-on broadcast wakes it. Grant checks are cached for 5 seconds. The session is not read from DataStore on every tick. The one-second poll remains while the screen is on. A debug build appends `usageQuery` timing every 30 queries to `files/debug-status.txt`. That is not a battery percentage.
- Home used to say monitoring was on whenever a rule was enabled and the grants were present. The guarding notification can be gone while that is still true. Home and Settings now use a service heartbeat. A stale heartbeat is shown as stopped, with a start-again action. There is no restart loop.
- The guarding channel was low importance, and Android will not raise an existing channel. New installs use a default-importance silent channel so the ongoing notification is less likely to be hidden. That has not been confirmed on the phone that failed.
- A one-second poll remains. It is the latency bound, not a scan of usage history. Usage Access has no resume callback, and events are often written late, so a shorter interval would not remove that delay. Each tick reads a bounded 20-second window and drops timestamps already applied. The launcher package is cached for 30 seconds. A unit test times the pure reducer on a dense 200-event window and requires the mean to stay under that one-second interval. That number is JVM reducer cost, not UsageStats IPC, not frame time, and not a phone.

## On-device intervention checklist

Not run on a physical device in this workspace. There is no phone here. A welcome-screen test does not prove any of the following. The emulator test, when green, launches `com.nawzblaze.momentwise.fixture`, not Instagram. It checks the notification fallback with overlay denied, and a visible overlay window with overlay allowed. That is not a phone result. On one API 34 device and one API 35 device, record the Android version, the permissions actually granted, the target app, and what happened.

1. Install a debug build. Do not treat it as the public release.
2. Decline Usage Access. Enable a rule only after the explanation. Confirm the app does not crash and does not pretend the pause will appear.
3. Allow Usage Access and notifications. Skip display-over-other-apps. Open a selected app. A notification should appear and say the app is not blocked. The other app should still be usable until you tap the notification. Tapping it opens the pause. Do not treat the notification itself as a cover.
4. Allow display-over-other-apps. Leave the app and open it again. A card should cover about the middle of the screen, with the other app visible behind a scrim. Taps on the scrim should do nothing. It should not be an invisible layer.
5. Tap Leave. The launcher should come forward. The other app must still be in recents. The pause should not immediately reopen over the same visit. Open the app again after leaving: the opening pause should show again.
5a. Close the pause without tapping Leave, Continue, or Dismiss for today (Home, or swipe the pause notification away). That is not Continue. No reminder should start. The same visit should not immediately show the pause again. Leave the app, then open it again: the opening pause should show. A no-choice close must not permanently suppress later openings.
6. Tap Continue. The other app should continue without Momentwise relaunching it. Leave one second later and stay away for longer than the interval. No reminder should appear. Return, continue again, and the reminder should wait until the saved minutes of use have added up. A new rule defaults to 5 minutes of use. With Never, no reminder should appear. The opening pause should still appear.
7. On the reminder, tap Wait. That app's next check-in should move by the snooze. Another guarded app must keep its own interval.
8. Tap Dismiss for today on the opening card or the reminder. That app should not pause or check in again until the next local day. Its rule stays enabled. A second guarded app should still check in.
9. Pause monitoring in Settings. Saved rules must remain. Opening a guarded app should not show a pause. Turn monitoring back on, leave, and open again: the pause should return.
10. Revoke Usage Access while a rule is enabled. Monitoring should stop without deleting rules.
11. Force-stop Momentwise while an opening pause is unanswered and the guarded app is still in front. Open Momentwise or let the service restart. The same pause should come back, not a second independent one.
11a. After the guarding notification disappears, open Momentwise. Home should say monitoring is not running, not that it is on. Start monitoring again, leave, and open the guarded app.
12. Reboot. An in-progress session should be dropped. The next open should be a new pause. Dismiss-for-today should still apply if the day has not changed.
13. On a debug build, after the screen has been on and guarding for about half a minute, `adb shell run-as com.nawzblaze.momentwise cat files/debug-status.txt` can show `usageQuery` mean time. That is UsageStats query time on that device, not a daily battery percentage. A release build does not write that file.

Expected limits: the monitor polls about once a second while the screen is on, so the pause is not instant. Vendor battery limits can stop the service. Leave cannot force-stop another app. No battery-percentage claim has been measured. The ongoing notification title is “Momentwise is on” and the body is “Watching your selected apps.” Android requires that notification for this foreground service. It is silent and is not updated on every poll. It cannot be removed while monitoring stays reliable.

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

The approved mark is `res/drawable-nodpi/ic_logo.png`, the supplied icon.
The launcher foreground is that same icon.
