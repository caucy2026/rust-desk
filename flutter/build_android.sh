#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
flutter_bin="$script_dir/../.toolchains/flutter/bin/flutter"
if [[ ! -x "$flutter_bin" ]] || ! "$flutter_bin" --version | grep -q 'Flutter 3.24.5'; then
  echo "KEMI requires project Flutter 3.24.5 at $flutter_bin; global Flutter is forbidden." >&2
  exit 1
fi

MODE=${MODE:=release}
$ANDROID_NDK_HOME/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android-strip android/app/src/main/jniLibs/arm64-v8a/*
"$flutter_bin" build apk --target-platform android-arm64,android-arm --${MODE} --obfuscate --split-debug-info ./split-debug-info
"$flutter_bin" build apk --split-per-abi --target-platform android-arm64,android-arm --${MODE} --obfuscate --split-debug-info ./split-debug-info
"$flutter_bin" build appbundle --target-platform android-arm64,android-arm --${MODE} --obfuscate --split-debug-info ./split-debug-info

# build in linux
# $ANDROID_NDK/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android-strip android/app/src/main/jniLibs/arm64-v8a/*
