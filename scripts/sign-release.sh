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

"$APKSIGNER" sign \
  --ks "$ANDROID_KEYSTORE_PATH" \
  --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
  --key-pass env:ANDROID_KEY_PASSWORD \
  --min-sdk-version 24 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled true \
  --out "$OUTPUT_APK" \
  "$INPUT_APK"

"$APKSIGNER" verify --verbose --print-certs --min-sdk-version 24 "$OUTPUT_APK"
printf '\nAPK assinado: %s\nAssinatura V4: %s.idsig\n' "$OUTPUT_APK" "$OUTPUT_APK"
