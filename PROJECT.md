# RustDesk KEMI — 项目架构与开发指南

> 仓库：https://github.com/caucy2026/rust-desk  
> 用途：KEMI-远程桌面 客户端 + 信令服务器，统一管理

---

## 一、目录架构

```
rust-desk/
├── README.md                      ← 项目总览
├── PROJECT.md                     ← 本文档：架构与开发指南
├── client/                        ← 客户端源码（RustDesk 定制版）
│   ├── flutter/                   ← Flutter UI 层 (Dart)
│   │   ├── lib/                   ←   业务逻辑、页面、组件
│   │   ├── android/               ←   Android 原生配置
│   │   └── macos/                 ←   macOS 原生配置
│   ├── src/                       ← Rust 核心层
│   │   ├── server/                ←   被控端（视频采集、输入注入）
│   │   ├── client.rs              ←   主控端（连接管理）
│   │   └── platform/              ←   平台适配（macOS TCC 权限等）
│   └── libs/                      ← 共享库
│       └── scrap/                 ←   采集/编解码/MediaCodec 硬解
└── server/                        ← 信令服务器
    ├── src/main.rs                ← 服务器入口（hbbs + hbbr）
    ├── libs/hbb_common/           ← 协议库（protobuf）
    ├── .cargo/config.toml         ← 交叉编译链接器配置
    ├── build.sh                   ← 一键编译脚本
    └── bin/                       ← 可部署包（拷走即用）
        ├── README.md              ←   快速开始
        ├── DEPLOY.md              ←   完整部署指南
        ├── BUILD.md               ←   编译指南
        ├── start-server.sh        ←   主管理脚本
        └── *.bin                  ←   预编译二进制
```

---

## 二、本地开发环境（本机 Mac ARM）

| 组件 | 路径 | 用途 |
|------|------|------|
| 客户端主目录 | `/Users/newlink/kemi/RustDesk/rustdesk/` | 日常开发、编译 APK |
| 服务器主目录 | `/Users/newlink/kemi/rusk-server/` | 日常开发、交叉编译 Linux |
| GitHub 备份 | `/Users/newlink/kemi/rust-desk/` | 只做 git push，不直接编译 |

## 三、客户端编译

### 环境依赖
- Flutter SDK 3.29+ (`~/flutter/bin/flutter`)
- Android SDK + NDK 27 (`~/android-sdk/`)
- Java 17 (`~/jdk/jdk-17.0.19+10/`)
- Rust 工具链 (`rustup`)

### 编译 Android APK
```bash
cd /Users/newlink/kemi/RustDesk/rustdesk/flutter
flutter build apk --debug
# 产物: build/app/outputs/flutter-apk/app-debug.apk
```

### 编译 macOS 应用（需要 Xcode）
```bash
cd /Users/newlink/kemi/RustDesk/rustdesk/flutter
flutter build macos --debug
```

---

## 四、服务器编译

### 环境依赖
- Rust 工具链 (`rustup`)
- **zig** 交叉编译器（Mac ARM → Linux x86_64）
- **cargo-zigbuild**（Rust 交叉编译工具）

### 安装 zig（一次性）
```bash
curl -L --retry 10 -o /tmp/zig.tar.xz \
  "https://ziglang.org/download/0.13.0/zig-macos-aarch64-0.13.0.tar.xz"
mkdir -p ~/zig && tar -xf /tmp/zig.tar.xz -C ~/zig
echo 'export PATH="$HOME/zig/$(ls ~/zig | head -1):$PATH"' >> ~/.zshrc
source ~/.zshrc
zig version  # 验证
```

### 安装 cargo-zigbuild
```bash
cargo install cargo-zigbuild
```

### 编译 Linux x86_64 二进制
```bash
cd /Users/newlink/kemi/rusk-server
./build.sh linux
# 产物: target/x86_64-unknown-linux-gnu/release/rustdesk-server
```

### 编译 + 自动上传
```bash
./build.sh linux scp 用户名@服务器IP
```

### 直接在本机 Ubuntu 上编译（无需 zig）
```bash
ssh 用户名@服务器IP
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
git clone https://github.com/caucy2026/rust-desk
cd rust-desk/server
cargo build --release
# 产物: target/release/rustdesk-server
```

---

## 五、新人 clone 后快速上手

```bash
# 1. 克隆仓库
git clone https://github.com/caucy2026/rust-desk
cd rust-desk

# 2. 查看架构
cat PROJECT.md

# 3. 部署服务器（直接用预编译二进制）
cd server/bin
# 详细步骤见 DEPLOY.md

# 4. 编译服务器（从源码）
cd server
cat bin/BUILD.md    # 查看编译步骤
./build.sh linux     # 交叉编译

# 5. 编译客户端
cd client/flutter
flutter build apk --debug
```

---

## 六、关键技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| UI | Flutter + Dart | 跨平台界面（Android / macOS / iOS） |
| 核心 | Rust | 网络通信、视频编解码、权限管理 |
| 编解码 | MediaCodec / VideoToolbox / libvpx | 硬件优先，软件兜底 |
| 协议 | protobuf | 客户端-服务器通信 |
| 交叉编译 | zig + cargo-zigbuild | Mac ARM → Linux x86_64 |
| 服务器 | Rust | 信令/中继一体 |

---

## 七、版本记录

| 版本 | 日期 | 主要改动 |
|------|------|---------|
| v1.0.4 | 2026-07-26 | KEMI 品牌、触摸精准点击、Mac 权限流程、启动加速、全格式硬解 |
