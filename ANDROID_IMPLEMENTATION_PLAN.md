# Momentwise — Android implementation plan

This document records the platform constraints for Momentwise's core behaviour
(detecting that a chosen app was opened, showing a configured pause, and
checking in later) and the least intrusive design that can satisfy it.

It is a plan, not a claim that the behaviour already works on a device.
The intervention code is now in the app. A signed public APK is not published:
the release workflow refuses to upload one until a stable keystore is stored as
Actions secrets, outside Git. The website does not link a download that does
not exist. Treat every runtime claim below as unverified until it is run on
hardware.

## Intervention status (19 October 2026)

Implemented in the project, not device-proven:

- Launchable apps from `PackageManager` (`MAIN` / `LAUNCHER`). Saved rules stay keyed by package name. Foundation sample ids (`instagram`, `tiktok`, `x`, `youtube`, `reddit`, `threads`) are dropped and are not copied onto real packages.
- Disclosure before a rule is enabled, with a decline path. Usage Access, display over other apps, and notifications are explained separately. Overlay can be skipped; the pause then uses a notification.
- Monitoring foreground service (`specialUse`) polls `queryEvents` only while a rule is enabled and Usage Access plus notifications are allowed. The event reducer is unit-tested: empty window, self-package ignored while a pause is showing, self-package as a leave otherwise, duplicate resume, app switch, launcher leave.
- Pause UI: opaque overlay when display-over-other-apps is allowed, otherwise a notification that opens `PauseActivity` in its own task. Quit is `ACTION_MAIN` + `CATEGORY_HOME`, sent before the overlay is removed. It does not force-stop. Opening Momentwise itself counts as leaving, so the next open of a guarded app is a new pause. While the pause is showing, Momentwise's own package is ignored so the pause does not retrigger.
- Reminder session and dismiss-for-today are persisted. A boot receiver drops an in-progress session and can restart monitoring only when a rule is enabled, grants remain, and monitoring is not paused. After process death, an unanswered opening or a due reminder is shown again if that app is still in front. An empty usage window does not end the session. That decision is unit-tested, not exercised on a device.
- Settings can pause monitoring without deleting rules. Revoking Usage Access or notifications also stops the monitor.

Not done, on purpose:

- No privacy policy and no Play declaration package. Those wait until the pause is demonstrated on at least one API 34 device and one API 35 device.
- No Accessibility Service, no exact alarm, no battery-optimization prompt, no `QUERY_ALL_PACKAGES`.
- No claim that the overlay exemption works on Android 15. The overlay is the pause itself, so a background activity start is not required for that path. That choice is still unverified on hardware.

The foundation section below describes what the first milestone shipped. Later sections are still the constraints this code is trying to honour.

Proposed application ID: `com.nawzblaze.momentwise`.
This ID is a proposal for the unpublished foundation build. The app is not
published under it, and it should not be treated as a registered Play listing.

Sources checked while writing this (19 October 2026):

- [Restrictions on starting activities from the background](https://developer.android.com/guide/components/activities/background-starts) and the current [Activity security](https://developer.android.com/guide/components/activities/secure-bal) page
- [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491) (Play Console Help)
- [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241)
- Usage events behaviour reported against `UsageStatsManager.queryEvents` (the `MOVE_TO_FOREGROUND` / `ACTIVITY_RESUMED` split, and empty query windows)

Those pages can change. Re-read them before the intervention milestone, and
treat any device result below as unverified until it is run on hardware.

## What this milestone does and does not do

Implemented, on device, with no special permissions:

- Welcome, Home, Apps, Configure Rule, opening-notice preview, reminder preview, and Settings.
- System theme as the default, with Light and Dark overrides.
- A typed per-app rule: opening notice, two response labels bound to fixed actions, reminder frequency, reminder notice, and the three advanced reminder actions.
- Local persistence (DataStore) and validation. Rules are independent of each other.
- In-app previews of the saved copy. They are labelled as previews.

Not implemented, and not pretended:

- Detecting that another app opened.
- Drawing over another app.
- Scheduling a reminder while the user is inside another app.
- Returning the user home from another app.
- Listing the apps actually installed on the device. The Apps screen is a labelled sample catalog, matching the browser prototype's seed, until a later milestone replaces it.

The sample catalog is configuration, not usage. Home does not show invented screen-time statistics.

## Product constraints that the platform has to honour

From the approved prototype and the product brief:

- Opening notice default: `What brought you here?`
- Option 1 default: `Quit` — leave the distracting app.
- Option 2 default: `Anyways` — continue into it.
- Reminder frequencies: Never, 5, 10, 15, 20 minutes, or Custom (1–300 minutes, matching `prototype/app.js`).
- Reminder notice default: `You're still here?`
- Advanced actions, collapsed by default:
  - `Okay, I'm quitting` — leave the distracting app.
  - `Wait` — snooze, 10 minutes by default (1–60).
  - `Dismiss reminder` — no further reminders for that app for the rest of the local day. The rule stays enabled.
- Every app has its own rule. System theme is the default.
- Quit must not claim to force-stop another app. Android does not allow a normal app to do that, and Momentwise should not want to.

## Why an ordinary background service is not enough

Android does not offer a public broadcast for "the user opened another app".

The older process APIs do not fill the gap:

- `ActivityManager.getRunningTasks()` has been limited to the caller's own tasks since Android 5.
- `ActivityManager.getRunningAppProcesses()` has, since Android 8, generally returned only the caller's own process.
- A started or bound background service cannot see other apps' activities, and since Android 8 it will not stay running just because it was started.
- `WorkManager` periodic work cannot run more often than about 15 minutes, and is deferred further under Doze. That cannot show a pause at the moment an app opens, and it cannot reliably deliver a 5-minute check-in.

So detection and the pause UI are two separate problems, and both need a supported, user-granted capability. They should be built only after the disclosures below are in the product, not bolted on to make a demo look finished.

## Recommended detection mechanism

**Use Usage Access (`UsageStatsManager.queryEvents`), polled from a foreground service, and only while at least one rule is enabled.**

Do not use an Accessibility Service as the primary mechanism. Reasons below.

### How detection would work

1. The user grants Usage Access in system Settings. This is not a runtime permission dialog. The app can only send the user to the Usage Access screen and then check `AppOpsManager` for `OPSTR_GET_USAGE_STATS`.
2. A foreground service with a persistent notification polls `queryEvents` over a short trailing window (on the order of one second, tuned on device).
3. The event that means "an activity came to the foreground" is `ACTIVITY_RESUMED` on API 29 and later. `MOVE_TO_FOREGROUND` is the older constant and is deprecated from API 29. Both have to be handled while `minSdk` is 26.
4. The service keeps the last package it actually observed. An empty result is a gap in the event log, not proof that nothing is in the foreground. Resetting state on an empty window makes the pause flicker. This is the usual bug in this approach.
5. Ignore Momentwise's own package, the launcher, and the system UI, or the pause will retrigger on itself.
6. When the foreground package matches an enabled rule, and a pause is not already showing for that opening, show the intervention UI (next section).

### Limitations of Usage Access

- It is not instant. A poll interval around one second means the other app can be visible briefly before the pause. That is acceptable for a mindfulness pause. It is not acceptable to describe it as instant.
- Event delivery timing differs by OEM and Android version. API 29+ devices should be tested specifically; some API 34 reports show `queryEvents` returning nothing unless the event types and the time window are right.
- It reports usage events. It does not see overlay windows, and it does not tell us *why* the user opened the app.
- The grant can be revoked at any time. The service must notice and stop claiming it is guarding apps.
- Play requires an honest disclosure of this access. It is device-usage data even if it never leaves the phone.

### Why not an Accessibility Service

An `AccessibilityService` listening for `TYPE_WINDOW_STATE_CHANGED` is the low-latency alternative. It is the wrong default for Momentwise.

- Momentwise is not an accessibility tool. Play allows `isAccessibilityTool="true"` only for apps whose purpose is to help people with disabilities. Setting that flag to dodge the disclosure would be a policy violation. [Play's AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491) requires every other app to complete a declaration, ship a demo video, and show a prominent in-app disclosure with affirmative consent before the service is enabled.
- The same policy forbids using the API to change settings without permission, to work around Android's privacy controls, or to drive the UI deceptively. A service that reads window content it does not need would fail that test. Momentwise would have to request only `TYPE_WINDOW_STATE_CHANGED`, ignore content, and still might be rejected because Usage Access can do the job.
- The October 2025 policy clarification prohibits accessibility use that lets an app autonomously plan and execute actions. [Android 17 reporting](https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html) describes further platform blocking of non-accessibility apps when Advanced Protection is on. Screen-time and monitoring apps are explicitly outside the accessibility-tool category. Building the product on this API would make it fragile on the versions Momentwise will ship into.
- Enabling the service is a manual Settings step, and a visible overlay can block the user from confirming that screen. That is a known trap.

Accessibility stays off the table unless a later, documented review shows Usage Access cannot meet the pause on a specific Android version, and Play approval is obtained first. This milestone does not declare an accessibility service.

## How the pause UI can appear

Once detection fires, Momentwise is in the background. Since Android 10 (API 29) an app in that state generally cannot start an activity. A foreground service does **not** count as foreground for this rule. The [current platform page](https://developer.android.com/guide/components/activities/secure-bal) still lists a user-granted `SYSTEM_ALERT_WINDOW` permission as an exception that allows a background activity start. A notification tap is the other ordinary exception.

Recommended UI, in order:

1. **Preferred: Momentwise's own activity, brought forward after the user has granted "Display over other apps".** The activity is the pause itself: the saved notice, Quit, and Anyways. It is not a transparent tapjacking layer. It should use its own task (`taskAffinity` plus a new task, not the distracting app's task) so it cannot be inserted into another app's stack. That matters: Android's task-hijacking rules exist specifically to stop a pause-like activity being sandwiched into another app.
2. **Degraded, if overlay permission is refused: a high-priority notification** the user can tap to open the same screen. Say so in the UI. Do not pretend the pause appeared on its own.
3. **Do not use `USE_FULL_SCREEN_INTENT`.** Since Android 14 that capability is restricted, and Play expects it for calling and alarm apps. A mindfulness check-in is not that.
4. **Do not draw a click-through overlay over the other app's controls.** An overlay that still lets taps reach the app underneath is the tapjacking pattern the platform is trying to stop. If an overlay is used at all, it should be opaque, limited to the pause, and dismissed by one of the two real choices.

`SYSTEM_ALERT_WINDOW` is also a manual Settings grant (`Settings.canDrawOverlays`). Request it only when the user enables a rule, after a screen that explains why, with a way to decline and keep using Momentwise as a configuration app.

This exemption must be re-tested on API 34, 35, and 36 before it is treated as reliable. Secondary reports say that on some Android 15 builds the overlay permission only allows a background start when an overlay is already visible. If that is what the devices do, the implementation falls back to showing a `TYPE_APPLICATION_OVERLAY` window that *is* the pause (already visible, so no background activity start is required) and documents the difference. Either way, the overlay must not be a permanent invisible window used only to hold a privilege.

## Quit, continue, snooze, dismiss

| Choice | Intended behaviour | Supported mechanism |
| --- | --- | --- |
| Quit / "Okay, I'm quitting" | Leave the distracting app | `Intent.ACTION_MAIN` + `CATEGORY_HOME`, so the home screen comes forward and the other app stops being the foreground task. Then finish the pause. |
| Anyways | Continue, and start the reminder interval if one is set | Finish the pause so the existing task of the other app is visible again. Do not re-launch it. Record the session start. |
| Wait | Snooze this check-in | Persist `nextReminderAt` for that package as now plus the configured snooze minutes (default 10). |
| Dismiss reminder | No more reminders for this app today | Persist the local date and package. Opening pause still runs on the next launch. Clear the flag on the next local day. |

`forceStopPackage` is not available to third-party apps (`FORCE_STOP_PACKAGES` is signature/privileged). Killing another process is not a goal. The settings copy already says this, and the native Quit path must keep saying it.

"Continue" has a sequencing consequence: Usage Access only notices the other app *after* it has opened. The pause therefore appears over an app that is already running, rather than instead of launching it. Quit sends the user home; it does not rewind the launch. That is the honest behaviour, and the preview already describes it that way.

## Permissions, and why each one would be asked

None of these are in the foundation manifest.

| Permission or special access | When | Why | What we do not use it for |
| --- | --- | --- | --- |
| Usage Access (`PACKAGE_USAGE_STATS`) | User enables at least one rule | Know which package is in the foreground | Reading notification text, building a usage dashboard, anything off-device |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Same moment, if they want the pause to appear by itself | The documented way to present our own pause while another app is in front | Click-through overlays, hiding the system UI, trapping the user |
| Notifications (`POST_NOTIFICATIONS`, API 33+) | Before the monitoring service starts | The foreground-service notification, and the degraded "tap to open the pause" path | Marketing, streaks, guilt copy |
| Foreground service (`FOREGROUND_SERVICE` and, on API 34+, `FOREGROUND_SERVICE_SPECIAL_USE`) | While any rule is enabled | Keep detection alive. `specialUse` is the honest type; it needs a Play declaration and a user-visible reason | A service that runs when no rule is enabled |
| Boot completed (`RECEIVE_BOOT_COMPLETED`) | If rules are enabled | Start monitoring again after reboot | Waking the app for analytics |
| Package visibility for launcher apps (`<queries>` for `MAIN` / `LAUNCHER`) | When the sample catalog is replaced | List apps the user can actually open, without `QUERY_ALL_PACKAGES` | Inventorying every package, including non-launchable ones |

Not requested, on purpose:

- `QUERY_ALL_PACKAGES` — a launcher-activity query is enough for the picker and is easier to justify.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — a check-in is not an alarm clock. Play restricts exact alarms. Inexact timing plus a foreground-service clock is the right trade.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` as a first step — Play discourages this prompt. Offer it only if device testing shows the service is killed without it, and only with a separate explanation and a decline path.
- Accessibility, notification listener, device admin, VPN. None of them match the job as well as Usage Access, and each is more invasive.

Every special grant gets its own in-app explanation, in the flow where the user is about to enable it, with a decline path. That is Play's prominent-disclosure rule, and it is also the product's "helpful, not controlling" rule. The disclosure has to live in the app, not only on the website.

## Reminder timing, process death, and reboot

While the user has chosen Continue and the guarded app is still in the foreground:

- Keep the session in the monitoring service: package, session start, next reminder instant, snooze-until, dismissed-for-date.
- Compare `SystemClock.elapsedRealtime()` (or a stored wall-clock instant) with `nextReminderAt`. A one-second tick is enough; the reminder is allowed to be a few seconds late.
- On fire, show the same pause UI with the reminder notice and the three advanced actions.
- Snooze rewrites `nextReminderAt`. Dismiss writes the local date. Leaving the app (foreground package changes) ends the session. Opening it again starts a new one, including a new opening pause.

Persistence, because the process will die:

- Write the session to DataStore whenever it changes: package, wall-clock `nextReminderAt`, dismissed-for date, and whether a session was active.
- `elapsedRealtime` resets on reboot, so do not persist it alone. Persist a wall-clock instant and recompute after boot.
- The service returns `START_STICKY`. On recreate, reload the session, ask Usage Access whether that package is still in front, and either resume the clock or end the session.
- A boot receiver starts the service only if at least one rule is enabled. An in-progress session does not survive reboot as a reliable "they were still in the app" fact — after boot, wait for the next observed opening. Say that in the UI if a session is dropped.
- If the user force-stops Momentwise, nothing runs until they open Momentwise again. That is a platform rule, not a bug to route around.
- Doze and OEM battery managers (Xiaomi, Oppo, vivo, Huawei, some Samsung builds) can delay or kill a foreground service despite the notification. A watchdog alarm (`setAndAllowWhileIdle`, inexact) can restart the service, not guarantee the original deadline. Document the drift; do not promise a 5-minute reminder on every phone.

Dismiss-for-today is local-date based, stored per app, and cleared by date comparison on the next read. It must not disable the rule.

## What works with no special permissions

- The whole configuration app: onboarding, rules, validation, theme, previews, reset.
- Storing rules only on the device. No account, no network, no analytics SDK.
- Explaining, in Settings, what the native app will and will not do.

That is the entire foundation milestone, by design.

## What cannot be guaranteed

- That the pause appears on every device, or within a fixed number of milliseconds.
- That a reminder fires at exactly 5, 10, 15, or 20 minutes after OEM battery restrictions, Doze, or a force-stop.
- That a session resumes after reboot or process death with an accurate "time already spent".
- That Quit closes the other app's process, stops its audio, or clears its back stack. Home is the supported outcome.
- Behaviour in work profiles, secondary users, multi-window, picture-in-picture, or on foldables, until those are tested.
- That Play will accept a `specialUse` foreground service and Usage Access. The declaration, the in-app disclosure, and a privacy policy have to exist before any listing. This repository does not have a privacy policy yet; one is required before publishing, even though data stays on device.
- Custom ROMs that do not emit usage events.

## Versions and devices to test before calling intervention done

`minSdk` is 26. `targetSdk` and `compileSdk` are 35. The risky boundaries are behavioural, not compile-time:

| API | Why it matters |
| --- | --- |
| 26–28 | Background execution limits. `MOVE_TO_FOREGROUND` rather than `ACTIVITY_RESUMED`. Notification channels. |
| 29 | Background activity starts begin. Usage event constant changes. |
| 31 | Foreground-service start restrictions. Exact-alarm changes (we should not need them). |
| 33 | Notification runtime permission. |
| 34 | Foreground-service types. Full-screen intent restriction. PendingIntent BAL opt-in. |
| 35 | Target SDK of this project. PendingIntent creator BAL opt-in. Re-test the overlay exemption. |
| 36+ | `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` and any further overlay tightening. Test before raising target. |

Devices: a Pixel on stock Android, plus at least one Samsung, one Xiaomi or Redmi, and one Oppo, OnePlus, or vivo. Those OEMs are where foreground services and usage events diverge from AOSP. Also: gesture navigation, split screen, and a work profile if one is available.

No emulator in this workspace was available to run any of that. See the milestone report for what was actually executed.

## Play policy checklist for the intervention milestone

Before a listing, not before this foundation:

- Data safety form: usage data collected on device, not shared, not used for ads.
- A privacy policy URL, even for an on-device app.
- Foreground-service declaration for `specialUse`, with a short video if Play asks.
- Usage Access disclosed in the store listing and in the app, before the Settings redirect.
- No accessibility declaration, because the service will not be present unless the review above changes that.
- No exact-alarm declaration.
- Prominent disclosure screens with a real decline path. Not bundled into the theme picker or a terms checkbox.

## What is still next

Items 1–5 of the intervention milestone are coded. They are not done until a device says so.

1. Run the pause on at least one API 34 device and one API 35 device. Confirm Usage Access events, the overlay path, the notification fallback, home-intent Quit, and that an empty event window does not flicker the pause.
2. Kill the process during an active session and confirm the clock resumes only if that package is still in front. Reboot and confirm the session is dropped and the note appears.
3. Only after that, write the privacy policy and the Play `specialUse` / Usage Access declaration notes next to this file.

Do not treat a green GitHub Actions run as that proof.
