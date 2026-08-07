#!/usr/bin/env bash
# Sign a KEMI macOS bundle for direct distribution. This script signs nested
# code explicitly before the outer app; it deliberately never installs apps.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
app_path="${1:-$repo_root/flutter/build/macos/Build/Products/Release/KEMI远程办公.app}"
signing_identity="${KEMI_MACOS_DEVELOPER_ID_IDENTITY:-Developer ID Application: zhen ji (26T5WV4GLP)}"
entitlements="$repo_root/flutter/macos/Runner/Release.entitlements"
timestamp_mode="${KEMI_MACOS_TIMESTAMP:-required}"

if [[ ! -d "$app_path" ]]; then
  echo "KEMI macOS app bundle not found: $app_path" >&2
  exit 1
fi
if [[ "$app_path" == /Applications/* ]]; then
  echo "Refusing to sign an installed app. Sign a build or staged archive only." >&2
  exit 1
fi
if ! security find-identity -v -p codesigning | grep -Fq "$signing_identity"; then
  echo "Required Developer ID identity is unavailable: $signing_identity" >&2
  exit 1
fi

timestamp_args=(--timestamp)
if [[ "$timestamp_mode" == "off" ]]; then
  timestamp_args=(--timestamp=none)
elif [[ "$timestamp_mode" != "required" ]]; then
  echo "KEMI_MACOS_TIMESTAMP must be required or off." >&2
  exit 2
fi

sign() {
  codesign --force "${timestamp_args[@]}" --options runtime --sign "$signing_identity" "$1"
}

# The service is copied into the bundle after Flutter/Xcode builds it and must
# be signed as an independent executable. Sign dylibs/frameworks first, then
# the outer bundle with its explicit Release entitlements.
if [[ -x "$app_path/Contents/MacOS/service" ]]; then
  sign "$app_path/Contents/MacOS/service"
fi
while IFS= read -r -d '' dylib; do
  sign "$dylib"
done < <(find "$app_path/Contents/Frameworks" -type f -name '*.dylib' -print0)
while IFS= read -r -d '' framework; do
  sign "$framework"
done < <(find "$app_path/Contents/Frameworks" -type d -name '*.framework' -prune -print0)

codesign --force "${timestamp_args[@]}" --options runtime --entitlements "$entitlements" \
  --sign "$signing_identity" "$app_path"
codesign --verify --deep --strict --verbose=2 "$app_path"
codesign -dv --verbose=4 "$app_path" 2>&1 | grep -E 'Identifier=|Authority=|TeamIdentifier=|Runtime Version='
