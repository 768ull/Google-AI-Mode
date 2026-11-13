#!/usr/bin/env bash
set -euo pipefail

INPUT_APK="${1:?Uso: sign-release.sh app-release-unsigned.apk [app-release.apk]}"
OUTPUT_APK="${2:-${INPUT_APK%-unsigned.apk}.apk}"
BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
APKSIGNER="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner"

: "${ANDROID_KEYSTORE_PATH:?Defina ANDROID_KEYSTORE_PATH}"
: "${ANDROID_KEYSTORE_PASSWORD:?Defina ANDROID_KEYSTORE_PASSWORD}"
: "${ANDROID_KEY_ALIAS:?Defina ANDROID_KEY_ALIAS}"
: "${ANDROID_KEY_PASSWORD:?Defina ANDROID_KEY_PASSWORD}"

[[ -x "$APKSIGNER" ]] || { echo "apksigner não encontrado: $APKSIGNER" >&2; exit 1; }
[[ -f "$INPUT_APK" ]] || { echo "APK de entrada não encontrado: $INPUT_APK" >&2; exit 1; }
[[ -s "$ANDROID_KEYSTORE_PATH" ]] || { echo "Keystore vazio ou inexistente" >&2; exit 1; }

# The app's minSdk is 26 (Android 8.0+). V2/V3 are the installable APK
# signatures used on supported Android versions. V4 is emitted as a sidecar.
# V1 is enabled as requested, alongside V2/V3 and the V4 sidecar.
"$APKSIGNER" sign \
  --ks "$ANDROID_KEYSTORE_PATH" \
  --ks-type PKCS12 \
  --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
  --key-pass env:ANDROID_KEY_PASSWORD \
  --min-sdk-version 26 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled true \
  --out "$OUTPUT_APK" \
  "$INPUT_APK"

[[ -s "$OUTPUT_APK.idsig" ]] || { echo "Assinatura V4 (.idsig) não foi gerada" >&2; exit 1; }

VERIFY_OUTPUT="$("$APKSIGNER" verify --verbose --print-certs \
  --min-sdk-version 26 \
  --v4-signature-file "$OUTPUT_APK.idsig" \
  "$OUTPUT_APK")"
printf '%s\n' "$VERIFY_OUTPUT"

for SCHEME in v1 v2 v3 v4; do
  if ! grep -F "Verified using $SCHEME scheme" <<<"$VERIFY_OUTPUT" | grep -Fq "true"; then
    echo "A assinatura $SCHEME não foi validada; o APK não será publicado." >&2
    exit 1
  fi
done

printf '\nAPK assinado e verificado: %s\nAssinatura V4: %s.idsig\n' "$OUTPUT_APK" "$OUTPUT_APK"
