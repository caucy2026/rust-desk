#!/bin/bash
# RustDesk hbbs 启动脚本
# 用法: ./start-hbbs.sh [公网IP]
#       ./start-hbbs.sh 1.2.3.4 -k _

set -e
cd "$(dirname "$0")"

IP="${1:-}"
if [ -z "$IP" ]; then
    IP=$(curl -s ifconfig.me 2>/dev/null || echo "")
    if [ -z "$IP" ]; then
        echo "ERROR: 请提供公网IP: ./start-hbbs.sh 你的公网IP"
        exit 1
    fi
fi

shift 2>/dev/null || true

echo "=== RustDesk hbbs (ID/会合服务器) ==="
echo "公网IP: $IP"
echo ""

./rustdesk-hbbs-official-v1.1.16-x86_64 -r "$IP" "$@"
