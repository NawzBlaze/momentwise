#!/usr/bin/env bash
# Runs on the GitHub-hosted emulator. Writes a log the workflow can quote if it fails.
set -u
cd "$(dirname "$0")"
LOG="${GITHUB_WORKSPACE:-/tmp}/emulator-test.log"
{
  echo "api-level=${API_LEVEL:-unknown}"
  ./gradlew :fixture:assembleDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
  adb install -r -t fixture/build/outputs/apk/debug/fixture-debug.apk
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk
  ./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
} >"$LOG" 2>&1
code=$?
tail -n 80 "$LOG"
exit "$code"
