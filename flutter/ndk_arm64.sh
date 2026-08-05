#!/usr/bin/env bash

set -euo pipefail

# Prefer this repository's pinned native dependencies so the build does not
# depend on whichever vcpkg happens to be configured in the caller's shell.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_vcpkg="$script_dir/../vcpkg"
if [[ -z "${VCPKG_ROOT:-}" && -d "$repo_vcpkg" ]]; then
    export VCPKG_ROOT="$repo_vcpkg"
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
