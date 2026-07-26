# Rust Desk — KEMI 远程桌面

## 目录结构

```
rust-desk/
├── PROJECT.md          ← 架构文档 & 新人开发指南
├── client/             ← KEMI-远程桌面 客户端 (Flutter + Rust)
│   ├── flutter/        ← Flutter UI
│   ├── src/            ← Rust 核心
│   └── libs/scrap/     ← 编解码/MediaCodec硬解
└── server/             ← 信令/中继服务器 (Rust)
    └── bin/            ← 完整部署包（拷走即用）
```

## 快速开始

```bash
# 部署服务器
cd server/bin && cat DEPLOY.md

# 编译客户端
cd client/flutter && flutter build apk --debug

# 新人必读
cat PROJECT.md
```

## bin/ 部署包

```bash
scp -r server/bin/ root@你的IP:~/rustdesk-server/
ssh root@你的IP "cd ~/rustdesk-server/bin && chmod +x *.sh && ./start-server.sh start 你的公网IP"
```

## 版本

客户端 v1.0.4 | 服务器 v1.1.16
