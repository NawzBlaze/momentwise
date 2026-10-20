#!/usr/bin/env bash
# Fail closed before Gradle. Prints secret names only, never values.
set -euo pipefail

missing=0
status_file="${RUNNER_TEMP}/missing-secrets.txt"
: > "$status_file"

for name in MOMENTWISE_KEYSTORE_BASE64 MOMENTWISE_KEYSTORE_PASSWORD MOMENTWISE_KEY_ALIAS MOMENTWISE_KEY_PASSWORD; do
  eval "value=\${$name-}"
  if [ -z "$value" ]; then
    echo "Missing required secret: $name"
    echo "::error::Missing required secret: $name"
    echo "$name" >> "$status_file"
    missing=1
  fi
done

if [ "$missing" -ne 0 ]; then
  echo "Signed release failed: the four signing secrets must already exist."
  echo "This job does not generate a keystore and did not build an APK."
  echo "Unit tests and the debug APK do not use these secrets."
  {
    echo "## Signed release blocked"
    echo
    echo "A required signing secret is missing. No keystore was generated and no APK was uploaded."
    echo
    echo '```'
    cat "$status_file"
    echo '```'
  } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  exit 1
fi

# Mask credential values before any later tool can print them.
echo "::add-mask::${MOMENTWISE_KEYSTORE_PASSWORD}"
echo "::add-mask::${MOMENTWISE_KEY_ALIAS}"
echo "::add-mask::${MOMENTWISE_KEY_PASSWORD}"
echo "Signing secrets are present. Values were not printed."
