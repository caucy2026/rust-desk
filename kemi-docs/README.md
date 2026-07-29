# KEMI-远程桌面 — 开发文档

## 项目简介

**KEMI-远程桌面** 是基于 [RustDesk](https://github.com/rustdesk/rustdesk) (AGPL-3.0) 定制的远程桌面客户端，面向 Android PAD 与 macOS 双端协同场景。当前 KEMI 客户端版本为 **1.4.10+68**。

### 核心定制功能

| 功能 | 说明 |
|------|------|
| **双屏适配** | Android PAD 主屏键控 + 副屏远控画面，跨屏触摸与键盘转发 |
| **文件传输** | 60% × 60% 圆角浮窗，独立 Session 与远控视频并行，Android 默认 Download |
| **品牌定制** | KEMI 启动画面、应用命名、权限引导流程 |
| **信令服务** | 自建 rendezvous 服务器，Mac ARM 交叉编译 Linux x86_64 |

### 技术栈

- **客户端 UI**: Flutter（`flutter/`）
- **Rust 核心**: 屏幕采集、编解码、输入控制、网络协议（`src/`、`libs/`）
- **原生桥接**: Android Kotlin MethodChannel / macOS Swift
- **信令服务**: Rust（`/Users/newlink/kemi/rusk-server/`）

---

## 文档目录

```
kemi-docs/
├── README.md                    ← 本文件（项目简介与文档导航）
├── SESSION-HANDOFF.md           ← 跨会话接续手册、完整架构与构建部署流程
├── CHANGELOG-KEMI.md            ← 开发调试记录
├── GIT-OPS.md                   ← Git 操作与 GitHub 备份指南
├── cross-display-keyboard.md    ← 跨屏软键盘需求与设计
└── dual-screen-port.md          ← 安卓双屏移植总体架构

.github/prompts/
└── continue-kemi-rustdesk.prompt.md  ← VS Code 中可直接运行的接续提示词
```

---

## 文档说明

### README.md（本文件）

项目简介、文档目录导航、快速入门指引。**新成员入职第一份阅读材料。**

### SESSION-HANDOFF.md

**跨会话接续的第一事实入口**：

- 可直接复制给新会话的完整提示词
- 当前 Git 基线、不可回退行为和三层目录架构
- 双屏、键盘、并行文件传输 Session 的数据流
- Flutter/Java/Rust/ADB 环境与 Debug APK 构建、部署、验收命令
- 当前真实 GitHub `backup/master` 推送与恢复流程

> 新会话必须先读本文件，再根据任务进入具体模块文档。

### CHANGELOG-KEMI.md

**开发调试记录**，按时间倒序记录每次功能改动：

- 每节包含：问题、根因、修复文件、改动细节、验证结果
- 末尾维护"暂未完成任务"清单
- 当前最新：第十节（跨会话接续手册、VS Code prompt 和真实 GitHub 备份流程）

> 每次提交代码前应同步更新本文档。

### GIT-OPS.md

**Git 操作与 GitHub 备份指南**，独立于外部对话记录：

- 真实远端地图（官方 `origin` vs KEMI `backup`）
- 日常备份流程（提交 → fetch/rebase → `git push backup master`）
- 从备份仓恢复的完整步骤
- 分叉处理、冲突解决和远端完整哈希核验

> 不再复制文件到第二个本地仓库，也不再推送旧的 `origin main`。

### cross-display-keyboard.md

**跨屏软键盘需求与设计**，跨屏键盘功能的唯一规格：

- 键盘显示位置规则（App 所在屏幕的对面屏）
- 键盘按钮状态模型（hidden / showing / 用户手动关闭）
- 文本提交、删除、去重转发机制
- Flutter 与 Android 原生层的职责边界
- 当前已知限制与后续重构方向

> 现有实现若与本文冲突，以本文为准。

### dual-screen-port.md

**安卓双屏移植总体架构**：

- Display 0（主屏）与 Display 2（副屏）的职责划分
- MainActivity / RemoteActivity 双 Activity 架构
- MethodChannel 跨屏通信机制
- 编译配置与签名方案

> 键盘实现细节以 `cross-display-keyboard.md` 为准，本文仅保留架构说明。

---

## 快速入门

### 新成员上手顺序

1. **README.md**（本文件）— 了解项目全貌
2. **SESSION-HANDOFF.md** — 核对当前事实、基线、环境和不可回退行为
3. **CHANGELOG-KEMI.md** — 了解近期改动与当前待办
4. **dual-screen-port.md** — 理解双屏架构（历史命令以接续手册为准）
5. **cross-display-keyboard.md** — 理解键盘模块
6. **GIT-OPS.md** — 掌握代码提交与 GitHub 备份流程

### 日常开发速查

```bash
# 构建 PAD APK
export PATH=/Users/newlink/flutter/bin:$PATH
cd /Users/newlink/kemi/RustDesk/rustdesk/flutter
flutter build apk --debug

# 安装到设备
adb -s 192.168.1.10:5555 install -r -d build/app/outputs/flutter-apk/app-debug.apk

# 查看改动
cd /Users/newlink/kemi/RustDesk/rustdesk
git status --short
git diff --stat

# 备份到 GitHub
git add <files>
git commit -m "feat(xxx): 描述"
git push backup master

# 核对远端与本地哈希
git ls-remote backup refs/heads/master
git rev-parse HEAD
```

---

> 最后更新：2026-07-29
> 维护：KEMI 远程桌面团队
