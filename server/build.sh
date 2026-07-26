#!/bin/bash
set -e
cd "$(dirname "$0")"
ZIG_DIR=$(ls ~/zig 2>/dev/null | head -1)
if [ -z "$ZIG_DIR" ]; then
    echo "ERROR: zig not found in ~/zig/. Download first:"
    echo "  curl -L --retry 10 -o /tmp/zig.tar.xz https://ziglang.org/download/0.13.0/zig-macos-aarch64-0.13.0.tar.xz"
    echo "  mkdir -p ~/zig && tar -xf /tmp/zig.tar.xz -C ~/zig"
    exit 1
fi
ZIG="$HOME/zig/$ZIG_DIR/zig"
export PATH="$HOME/zig/$ZIG_DIR:$PATH"
export CC="$ZIG cc --target=x86_64-linux-gnu"
export AR="$ZIG ar"
export RANLIB="$ZIG ranlib"

case "${1:-linux}" in
    linux)
        echo "=== Cross-compiling for Linux x86_64 ==="
        cargo zigbuild --release --target x86_64-unknown-linux-gnu
        BIN="target/x86_64-unknown-linux-gnu/release/rustdesk-server"
        ls -lh "$BIN"
        file "$BIN"
        [ -n "$2" ] && scp "$BIN" "$2:~/"
        ;;
    native)
        echo "=== Native build (macOS ARM) ==="
        cargo build --release
        ls -lh target/release/rustdesk-server
        ;;
esac
