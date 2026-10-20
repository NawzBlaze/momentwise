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
adb logcat -d -s MomentwiseDebug:I > "${GITHUB_WORKSPACE:-/tmp}/emulator-measure.txt" || true
if [ "$code" -ne 0 ]; then
  python3 - "$LOG" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
log = pathlib.Path(sys.argv[1])
root = pathlib.Path("app/build")
lines = ["--- androidTest failure text ---"]
for report in root.rglob("*.xml"):
    if "androidTest" not in str(report) and "androidTests" not in str(report):
        continue
    try:
        tree = ET.parse(report)
    except ET.ParseError:
        continue
    for failure in tree.iter("failure"):
        message = (failure.get("message") or "").replace("\n", " ")
        lines.append(message[:1200])
        text = (failure.text or "").replace("\n", " ")
        if text and text[:200] not in message:
            lines.append(text[:400])
if len(lines) == 1:
    lines.append("No instrumentation failure element was found.")
with log.open("a", encoding="utf-8", errors="replace") as handle:
    handle.write("\n".join(lines) + "\n")
PY
fi
tail -n 80 "$LOG"
exit "$code"
