#!/bin/bash
# RustDesk hbbr 启动脚本
# 用法: ./start-hbbr.sh

set -e
cd "$(dirname "$0")"

echo "=== RustDesk hbbr (中继服务器) ==="
./rustdesk-hbbr-official-v1.1.16-x86_64 "$@"
