#!/usr/bin/env bash
# Sign a KEMI macOS test bundle with the persistent local test identity.
# This identity is intentionally for local/team testing only, not public release.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
app_path="${1:-$repo_root/flutter/build/macos/Build/Products/Release/KEMI-远程桌面.app}"
signing_identity="${KEMI_MACOS_CODESIGN_IDENTITY:-KEMI Local App Signing 2026}"

if [[ ! -d "$app_path" ]]; then
  echo "KEMI macOS app bundle not found: $app_path" >&2
  exit 1
fi

if ! security find-identity -v -p codesigning | grep -Fq "$signing_identity"; then
  echo "Required codesigning identity is unavailable: $signing_identity" >&2
  echo "Import and trust the local KEMI test signing certificate on this build Mac first." >&2
  exit 1
fi

codesign --force --deep --sign "$signing_identity" "$app_path"
codesign --verify --deep --strict --verbose=2 "$app_path"
codesign -d -r- "$app_path" 2>&1 | grep 'designated =>'
