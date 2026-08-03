#!/usr/bin/env bash

set -euo pipefail

# libsodium's autotools build reads the generic AR/RANLIB variables instead of
# Cargo's target-scoped variables.  On macOS, falling back to /usr/bin/ar
# creates an empty archive from Android ELF objects and leaves unresolved
# sodium symbols in librustdesk.so.  Always use the NDK tools for this target.
if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    case "$(uname -s)" in
        Darwin) ndk_host="darwin-x86_64" ;;
        Linux) ndk_host="linux-x86_64" ;;
        *) ndk_host="" ;;
    esac
    if [[ -n "$ndk_host" ]]; then
        export AR="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$ndk_host/bin/llvm-ar"
        export RANLIB="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$ndk_host/bin/llvm-ranlib"
    fi
fi

cargo ndk --platform 21 --target aarch64-linux-android build --lib --locked --release --features flutter,hwcodec
