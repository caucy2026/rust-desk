#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
flutter_bin="$repo_root/.toolchains/flutter/bin/flutter"
if [[ ! -x "$flutter_bin" ]] || ! "$flutter_bin" --version | grep -q 'Flutter 3.24.5'; then
  echo "KEMI requires project Flutter 3.24.5 at $flutter_bin; global Flutter is forbidden." >&2
  exit 1
fi

echo $MACOS_CODESIGN_IDENTITY
cargo install flutter_rust_bridge_codegen --version 1.80.1 --features uuid --locked
cd flutter; "$flutter_bin" pub get; cd -
~/.cargo/bin/flutter_rust_bridge_codegen --rust-input ./src/flutter_ffi.rs --dart-output ./flutter/lib/generated_bridge.dart --c-output ./flutter/macos/Runner/bridge_generated.h
./build.py --flutter
rm rustdesk-$VERSION.dmg
# security find-identity -v
codesign --force --options runtime -s $MACOS_CODESIGN_IDENTITY --deep --strict ./flutter/build/macos/Build/Products/Release/RustDesk.app -vvv
create-dmg --icon "RustDesk.app" 200 190 --hide-extension "RustDesk.app" --window-size 800 400 --app-drop-link 600 185 rustdesk-$VERSION.dmg ./flutter/build/macos/Build/Products/Release/RustDesk.app
codesign --force --options runtime -s $MACOS_CODESIGN_IDENTITY --deep --strict rustdesk-$VERSION.dmg -vvv
# notarize the rustdesk-${{ env.VERSION }}.dmg
rcodesign notary-submit --api-key-path ~/.p12/api-key.json  --staple rustdesk-$VERSION.dmg
