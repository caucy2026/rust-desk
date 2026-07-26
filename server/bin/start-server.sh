#!/bin/bash
# RustDesk 服务器一键启动/停止脚本
# 用法:
#   ./start-server.sh start 1.2.3.4      # 启动（传入公网IP）
#   ./start-server.sh start 1.2.3.4 demo # 启动自编译demo版
#   ./start-server.sh stop               # 停止
#   ./start-server.sh status             # 查看状态
#   ./start-server.sh log                # 查看日志

set -e
cd "$(dirname "$0")"
BIN_DIR="$(pwd)"

PID_DIR="$BIN_DIR"
HBBS_PID="$PID_DIR/hbbs.pid"
HBBR_PID="$PID_DIR/hbbr.pid"
DEMO_PID="$PID_DIR/demo.pid"

HBBS_LOG="$BIN_DIR/../hbbs.log"
HBBR_LOG="$BIN_DIR/../hbbr.log"
DEMO_LOG="$BIN_DIR/../demo.log"

start_hbbs() {
    local IP="$1"
    if [ -z "$IP" ]; then
        echo "ERROR: 请提供公网IP"
        echo "用法: $0 start 你的公网IP"
        exit 1
    fi
    echo "=== 启动 hbbs (ID服务器) ==="
    echo "公网IP: $IP"
    nohup ./rustdesk-hbbs-official-v1.1.16-x86_64 -r "$IP" >> "$HBBS_LOG" 2>&1 &
    echo $! > "$HBBS_PID"
    echo "hbbs PID: $(cat $HBBS_PID)"
}

start_hbbr() {
    echo "=== 启动 hbbr (中继服务器) ==="
    nohup ./rustdesk-hbbr-official-v1.1.16-x86_64 >> "$HBBR_LOG" 2>&1 &
    echo $! > "$HBBR_PID"
    echo "hbbr PID: $(cat $HBBR_PID)"
}

start_demo() {
    local IP="$1"
    if [ -z "$IP" ]; then
        echo "ERROR: 请提供公网IP"
        exit 1
    fi
    echo "=== 启动 Demo Server (自编译版) ==="
    echo "公网IP: $IP"
    export IP
    nohup ./rustdesk-server-selfbuilt-x86_64 >> "$DEMO_LOG" 2>&1 &
    echo $! > "$DEMO_PID"
    echo "Demo PID: $(cat $DEMO_PID)"
}

stop_all() {
    echo "=== 停止所有服务 ==="
    for pid_file in "$HBBS_PID" "$HBBR_PID" "$DEMO_PID"; do
        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                kill "$pid" 2>/dev/null && echo "已停止 PID: $pid ($(basename $pid_file))"
            fi
            rm -f "$pid_file"
        fi
    done
    # 确保清理
    pkill -f "rustdesk-hbbs-official" 2>/dev/null || true
    pkill -f "rustdesk-hbbr-official" 2>/dev/null || true
    pkill -f "rustdesk-server-selfbuilt" 2>/dev/null || true
    echo "已停止"
}

status_all() {
    echo "=== 服务状态 ==="
    local any=0
    for name in "hbbs" "hbbr" "demo"; do
        local pid_file="$BIN_DIR/${name}.pid"
        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                echo "✅ $name 运行中 (PID: $pid)"
                any=1
            else
                echo "❌ $name 已停止 (stale PID: $pid)"
                rm -f "$pid_file"
            fi
        else
            echo "⚫ $name 未启动"
        fi
    done
    echo ""
    echo "端口监听:"
    ss -tlnp 2>/dev/null | grep -E "21115|21116|21117" || echo "  (无)"
}

case "${1:-}" in
    start)
        IP="${2:-}"
        TYPE="${3:-pro}"
        case "$TYPE" in
            demo)
                start_demo "$IP"
                ;;
            *)
                start_hbbs "$IP"
                sleep 1
                start_hbbr
                ;;
        esac
        echo ""
        echo "=========================================="
        echo "服务器已启动！客户端配置:"
        echo "  ID 服务器:     $IP"
        echo "  中继服务器:     $IP"
        echo "=========================================="
        ;;
    stop)
        stop_all
        ;;
    status)
        status_all
        ;;
    log)
        echo "=== hbbs log (tail) ===" && tail -20 "$HBBS_LOG" 2>/dev/null || echo "无日志"
        echo "=== hbbr log (tail) ===" && tail -20 "$HBBR_LOG" 2>/dev/null || echo "无日志"
        ;;
    restart)
        stop_all
        sleep 2
        IP="${2:-}"
        TYPE="${3:-pro}"
        if [ "$TYPE" = "demo" ]; then
            start_demo "$IP"
        else
            start_hbbs "$IP"
            sleep 1
            start_hbbr
        fi
        ;;
    *)
        echo "RustDesk 信令服务器管理脚本"
        echo ""
        echo "用法:"
        echo "  $0 start 你的公网IP        启动官方Pro版 (hbbs+hbbr)"
        echo "  $0 start 你的公网IP demo   启动自编译demo版"
        echo "  $0 stop                    停止所有服务"
        echo "  $0 status                  查看状态"
        echo "  $0 log                     查看日志"
        echo "  $0 restart 你的公网IP       重启"
        echo ""
        echo "示例:"
        echo "  $0 start 119.96.24.110"
        ;;
esac
