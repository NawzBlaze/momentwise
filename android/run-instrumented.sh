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
{
  echo "--- debug-status ---"
  adb shell run-as com.nawzblaze.momentwise cat files/debug-status.txt || true
  echo "--- momentwise-log ---"
  adb logcat -d -t 40 -s MomentwiseDebug:I Momentwise:I || true
} > "${GITHUB_WORKSPACE:-/tmp}/emulator-measure.txt" 2>&1 || true
if [ "$code" -ne 0 ]; then
  {
    echo "--- androidTest failure text ---"
    cat "${GITHUB_WORKSPACE:-/tmp}/emulator-measure.txt" || true
    python3 - <<'PY'
import pathlib
import re
root = pathlib.Path("app/build")
found = False
for report in root.rglob("*.xml"):
    path = str(report)
    if "androidTest" not in path and "androidTests" not in path:
        continue
    text = report.read_text(errors="replace")
    if "<failure" not in text and "<error" not in text:
        continue
    found = True
    print(path)
    names = re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<(?:failure|error)', text)
    print("FAILED TESTS:", ", ".join(names) if names else "(none parsed)")
    for name, body in re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<(?:failure|error)[^>]*>(.*?)</(?:failure|error)>', text, re.S):
        msg = re.sub(r"\s+", " ", body)
        print(f"FAILED {name}: ...{msg[-500:]}")
if not found:
    print("No instrumentation failure element was found.")
PY
  } >>"$LOG"
fi
tail -n 80 "$LOG"
exit "$code"
