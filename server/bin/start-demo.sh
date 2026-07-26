#!/bin/bash
# RustDesk 自编译 demo 服务器启动脚本
# 用法: ./start-demo.sh [公网IP]

set -e
cd "$(dirname "$0")"

IP="${1:-}"
if [ -z "$IP" ]; then
    IP=$(curl -s ifconfig.me 2>/dev/null || echo "")
    if [ -z "$IP" ]; then
        echo "ERROR: 请提供公网IP: ./start-demo.sh 你的公网IP"
        exit 1
    fi
fi

echo "=== RustDesk Demo Server (自编译) ==="
echo "公网IP: $IP"
echo "端口: 21116 (TCP+UDP) / 21117 (TCP)"
echo ""

export IP
./rustdesk-server-selfbuilt-x86_64
