#!/usr/bin/env bash
# Confirm the release APK is signed, then record package and version.
# Does not print keystore material or passwords.
set -euo pipefail

bt="${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools/35.0.0"
test -x "$bt/apksigner"
test -x "$bt/aapt"
apk="android/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$apk" ]; then
  echo "Signed release APK is missing. Refusing to upload an unsigned file."
  if [ -f android/app/build/outputs/apk/release/app-release-unsigned.apk ]; then
    echo "Gradle produced app-release-unsigned.apk, so the release signing config was not applied."
  fi
  exit 1
fi

verify="$("$bt/apksigner" verify --verbose --print-certs "$apk")"
printf '%s\n' "$verify"
if ! printf '%s\n' "$verify" | grep -Eq 'Verified using v[0-9.]+ scheme \([^)]+\): true'; then
  echo "apksigner did not report a verified signature scheme."
  exit 1
fi
if printf '%s\n' "$verify" | grep -q 'CN=Android Debug'; then
  echo "Refusing an APK signed with the Android debug certificate."
  exit 1
fi
if ! printf '%s\n' "$verify" | grep -q 'Signer #1 certificate SHA-256 digest:'; then
  echo "apksigner did not print a signer certificate."
  exit 1
fi

badging="$("$bt/aapt" dump badging "$apk")"
python3 - "$badging" <<'PY'
import re, sys
text = sys.argv[1]
line = next((ln for ln in text.splitlines() if ln.startswith("package: ")), "")
match = re.search(
    r"name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'",
    line,
)
if not match:
    sys.exit("Could not read package id and version from the APK.")
package, version_code, version_name = match.group(1), match.group(2), match.group(3)
print(f"package={package}")
print(f"versionCode={version_code}")
print(f"versionName={version_name}")
if package != "com.nawzblaze.momentwise":
    sys.exit(f"Unexpected package id: {package}")
if version_name != "1.0.0":
    sys.exit(f"Unexpected versionName: {version_name}")
if version_code != "12":
    sys.exit(f"Unexpected versionCode: {version_code}")
PY

install -d signed-artifact
cp "$apk" signed-artifact/momentwise-1.0.0-signed.apk
sha256sum signed-artifact/momentwise-1.0.0-signed.apk | tee signed-artifact/momentwise-1.0.0-signed.apk.sha256
digest="$(printf '%s\n' "$verify" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2; exit}')"
expected="b550cab577b31a5cec5a543cc6e3a8c2b68684798ba10efcae43be43c385b8e8"
normalized="$(printf '%s' "$digest" | tr -d '[:space:]' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
if [ "$normalized" != "$expected" ]; then
  echo "Signer certificate does not match the existing Momentwise release identity."
  echo "Refusing this APK. The keystore was not printed."
  exit 1
fi
cat > signed-artifact/signing-verification.txt <<EOF
package=com.nawzblaze.momentwise
versionName=1.0.0
versionCode=12
apksigner=verified
certificate_sha256=$normalized
expected_certificate_sha256=$expected
$(cat signed-artifact/momentwise-1.0.0-signed.apk.sha256)
EOF

{
  echo "## Signed APK verification"
  echo
  echo '```'
  cat signed-artifact/signing-verification.txt
  echo '```'
  echo
  echo "This CI artifact is not a GitHub Release and is not the website download."
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

if [ -n "${PR_NUMBER:-}" ] && [ -n "${GH_TOKEN:-}" ]; then
  gh pr comment "$PR_NUMBER" --body "$(printf '%s\n' '<!-- momentwise-signed-verify -->' 'Signed release APK verified with apksigner. Not published.' '' '```' "$(cat signed-artifact/signing-verification.txt)" '```')" || echo "PR comment failed"
fi

if [ -n "${HEAD_SHA:-}" ] && [ -n "${GH_TOKEN:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
  python3 - <<'PY'
import json, os, pathlib, subprocess
text = pathlib.Path("signed-artifact/signing-verification.txt").read_text()
payload = {
    "name": "Signed APK verification",
    "head_sha": os.environ["HEAD_SHA"],
    "status": "completed",
    "conclusion": "success",
    "output": {
        "title": "apksigner verified com.nawzblaze.momentwise 1.0.0",
        "summary": text + "\n\nCI artifact only. Not a GitHub Release and not the website download.",
    },
}
repo = os.environ["GITHUB_REPOSITORY"]
proc = subprocess.run(
    ["gh", "api", f"repos/{repo}/check-runs", "--input", "-"],
    input=json.dumps(payload),
    text=True,
    capture_output=True,
)
if proc.returncode != 0:
    print(proc.stderr[-2000:])
    print("signed verification check-run failed")
else:
    print("signed verification check-run published")
PY
fi
