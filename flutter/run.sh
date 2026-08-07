#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
flutter_bin="$script_dir/../.toolchains/flutter/bin/flutter"
if [[ ! -x "$flutter_bin" ]] || ! "$flutter_bin" --version | grep -q 'Flutter 3.24.5'; then
  echo "KEMI requires project Flutter 3.24.5 at $flutter_bin; global Flutter is forbidden." >&2
  exit 1
fi

cargo install flutter_rust_bridge_codegen --version 1.80.1 --features uuid --locked
"$flutter_bin" pub get
~/.cargo/bin/flutter_rust_bridge_codegen --rust-input ../src/flutter_ffi.rs --dart-output ./lib/generated_bridge.dart --c-output ./macos/Runner/bridge_generated.h
# call `flutter clean` if cargo build fails
# export LLVM_HOME=/Library/Developer/CommandLineTools/usr/
cargo build --locked --features flutter
"$flutter_bin" run "$@"
