#!/usr/bin/env bash
# Decode the existing keystore secret. Does not generate a key.
set -euo pipefail

umask 077
install -d -m 700 "$RUNNER_TEMP/signing"
keystore="$RUNNER_TEMP/signing/momentwise-release.jks"
printf '%s' "$MOMENTWISE_KEYSTORE_BASE64" | tr -d '[:space:]' | base64 -d > "$keystore"

python3 - "$keystore" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
data = path.read_bytes()
if data.startswith(b"\xfe\xed\xfe\xed"):
    kind = "JKS"
elif data.startswith(b"\x30"):
    kind = "PKCS12"
else:
    sys.exit(
        "Decoded MOMENTWISE_KEYSTORE_BASE64 is not a JKS or PKCS12 keystore. "
        "Encode the existing keystore with standard Base64. Do not generate a new one."
    )
print(f"Decoded existing keystore ({kind}, {len(data)} bytes). No new keystore was generated.")
PY
chmod 600 "$keystore"
