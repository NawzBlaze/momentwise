#!/usr/bin/env bash
# Runs on the GitHub-hosted emulator. Writes a log the workflow can quote if it fails.
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
LOG="${GITHUB_WORKSPACE:-/tmp}/emulator-test.log"
{
  echo "cwd=$(pwd)"
  echo "api-level=${API_LEVEL:-unknown}"
  ./gradlew :fixture:assembleDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
  adb install -r -t fixture/build/outputs/apk/debug/fixture-debug.apk
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk
  adb shell pm grant com.nawzblaze.momentwise android.permission.POST_NOTIFICATIONS || true
  adb shell appops set com.nawzblaze.momentwise GET_USAGE_STATS allow || true
  adb shell appops set com.nawzblaze.momentwise SYSTEM_ALERT_WINDOW deny || true
  ./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
} >"$LOG" 2>&1
code=$?
if [ "$code" -ne 0 ]; then
  {
    echo "--- androidTest failure text ---"
    find app/build -path '*androidTest*' -name '*.xml' -print 2>/dev/null |
      while read -r report; do
        grep -n -E 'failure|AssertionError|not posted|not delivered|not allowed|overlay|not blocked|USAGE_QUERY' "$report" | head -n 40
      done
  } >>"$LOG"
fi
tail -n 160 "$LOG"
exit "$code"
