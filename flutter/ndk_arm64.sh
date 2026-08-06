#!/usr/bin/env bash

set -euo pipefail

# Prefer this repository's pinned native dependencies so the build does not
# depend on whichever vcpkg happens to be configured in the caller's shell.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_vcpkg="$script_dir/../vcpkg"
if [[ -z "${VCPKG_ROOT:-}" && -d "$repo_vcpkg" ]]; then
    export VCPKG_ROOT="$repo_vcpkg"
fi

# CI is pinned to NDK r28c. Local builds must resolve the same NDK before
# invoking cargo-ndk, because libsodium's configure step needs llvm-ar and
# llvm-ranlib from the start. cargo-ndk discovering an NDK later is too late.
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    kemi_ndk_revision="${KEMI_ANDROID_NDK_REVISION:-28.2.13676358}"
    android_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [[ -z "$android_sdk_root" && -f "$script_dir/android/local.properties" ]]; then
        android_sdk_root="$(sed -n 's/^sdk\.dir=//p' "$script_dir/android/local.properties" | head -n 1)"
    fi
    if [[ -n "$android_sdk_root" && -d "$android_sdk_root/ndk/$kemi_ndk_revision" ]]; then
        export ANDROID_NDK_HOME="$android_sdk_root/ndk/$kemi_ndk_revision"
    else
        echo "Android NDK $kemi_ndk_revision not found; set ANDROID_NDK_HOME explicitly." >&2
        exit 1
    fi
fi

# libsodium's autotools build reads the generic AR/RANLIB variables instead of
# Cargo's target-scoped variables.  On macOS, falling back to /usr/bin/ar
# creates an empty archive from Android ELF objects and leaves unresolved
# sodium symbols in librustdesk.so.  Always use the NDK tools for this target.
if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    if ! command -v cmake >/dev/null 2>&1; then
        android_sdk_root="$(cd "$ANDROID_NDK_HOME/../.." && pwd)"
        sdk_cmake="$(find "$android_sdk_root/cmake" -path '*/bin/cmake' -type f 2>/dev/null | sort | tail -n 1)"
        if [[ -n "$sdk_cmake" ]]; then
            export CMAKE="$sdk_cmake"
            sdk_cmake_bin="$(dirname "$sdk_cmake")"
            if [[ -x "$sdk_cmake_bin/ninja" ]]; then
                export PATH="$sdk_cmake_bin:$PATH"
                export CMAKE_GENERATOR="Ninja"
            fi
        fi
    fi
    # cmake-rs already passes CMAKE_SYSTEM_PROCESSOR=aarch64. Exposing the NDK
    # through ANDROID_NDK lets CMake select arm64 without the toolchain file's
    # default armeabi-v7a ABI overriding Cargo's target.
    export ANDROID_NDK="$ANDROID_NDK_HOME"
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

cargo ndk --platform 21 --target aarch64-linux-android build --lib --locked --release --features flutter,hwcodec,mediacodec

# Android has no system libsodium. Any unresolved sodium symbol makes the APK
# pass Gradle packaging but crash in MainApplication before Flutter starts.
case "$(uname -s)" in
    Darwin) ndk_host="darwin-x86_64" ;;
    Linux) ndk_host="linux-x86_64" ;;
    *) ndk_host="" ;;
esac
rust_core="$script_dir/../target/aarch64-linux-android/release/liblibrustdesk.so"
llvm_readelf="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$ndk_host/bin/llvm-readelf"
if [[ -z "$ndk_host" || ! -x "$llvm_readelf" ]]; then
    echo "NDK llvm-readelf not found; cannot validate Android Rust core." >&2
    exit 1
fi
if "$llvm_readelf" -Ws "$rust_core" | grep -Eq 'UND[[:space:]]+sodium_'; then
    echo "Android Rust core contains unresolved libsodium symbols; refusing to package." >&2
    "$llvm_readelf" -Ws "$rust_core" | grep -E 'UND[[:space:]]+sodium_' >&2
    exit 1
fi
echo "Android Rust core sodium linkage verified."
