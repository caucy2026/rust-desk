# RustDesk KEMI 定制版 — 开发调试全记录

> 日期：2026-07-26  
> 项目：基于 RustDesk 的 KEMI-远程桌面 客户端 + 信令服务器

---

## 一、Mac 端权限流程改造

**问题：** 用户安装后不会手动去系统设置授权，导致 PAD 连上 Mac 后无画面/不可控。

**根因：** macOS 的 Screen Recording / Accessibility / Input Monitoring 三项 TCC 权限必须用户手动授权，应用不能静默开启。

**修改文件：**
- `flutter/lib/desktop/pages/desktop_home_page.dart`

**改动内容：**
1. 将原来三个独立的权限卡片（config_screen / config_acc / config_input）合并为一个统一卡片
2. 新增 `_requestAllMissingMacPermissions()` 方法，一键触发三项系统授权弹窗
3. 新增首次启动权限向导弹窗 `_showMacPermissionGuideDialog()`，显示三项权限状态
4. 新增本地 option 频控（`mac-permission-guide-shown`），避免每次启动都弹
5. 新增 `_autoPromptMacPermissionsIfNeeded()` 自动触发流程
6. 权限卡片底部加版本号 `_kMacPermissionFlowVersion = v1.0.4`

**效果：** 用户首次打开 Mac 端 → 自动弹窗显示三项权限状态 → 点 Configure 一键触发系统授权 → 授权后重启即可。

---

## 二、PAD 触摸模式修复

**问题：** PAD 上手指点哪里，Mac 上没有在对应位置点击。

**根因：** 移动端连接 Mac 时 `touchMode` 默认为 false。
- `isPeerAndroid` 只判断远端是不是 Android（Mac 不是）→ 跳过自动启用
- local/session option 都没设过 → 走鼠标模式，点击当前光标位置而非手指位置

**修复文件：**
- `flutter/lib/models/model.dart`（`handlePeerInfo`，约 1444 行）

**改动：**
```dart
// Mobile clients default to touch mode for absolute tap-to-position,
// unless the user has explicitly set it to 'N' (mouse mode).
if (isMobile && optLocal != 'N') {
  _touchMode = true;
}
```

**效果：** PAD 连 Mac → 自动触摸模式 → 手指触点→远端坐标映射→精确点击。

---

## 三、KEMI 品牌启动画面

**问题：** 启动时 5 秒白屏，用户体验差。需要 KEMI 品牌 LOGO。

**方案演进：**
1. ❌ Flutter 动画 splash（Flutter 引擎初始化太慢~5s，动画只显示最后 2s）
2. ❌ FittedBox / AnimatedBuilder 方案（文字不显示、动画不播放）
3. ✅ **最终方案：Android 原生静态 PNG 启动图**

**最终实现：**
- Python PIL 生成 400x400 PNG：白底 + 蓝色圆框 + 蓝色 "KEMI" 文字
- 放入 `drawable-nodpi/kemi_splash.png`
- `launch_background.xml` 引用该 PNG，200dp 居中显示
- 删除所有 Flutter splash 代码
- `main.dart` home 路由恢复原始 `HomePage()`

**文件：**
- `android/app/src/main/res/drawable-nodpi/kemi_splash.png`（新增）
- `android/app/src/main/res/drawable-xxhdpi/kemi_splash.png`（新增）
- `android/app/src/main/res/drawable/launch_background.xml`（修改）
- `android/app/src/main/res/drawable-v21/launch_background.xml`（修改）
- `flutter/lib/mobile/widgets/kemi_splash.dart`（已删除）
- `flutter/lib/main.dart`（清理 import 和路由）

**效果：** 启动瞬间到首页，全程一张 KEMI LOGO 图。

---

## 四、启动优化

**问题：** `runMobileApp()` 在 `runApp()` 之前同步等待缓存加载 + 网络请求，导致白屏 5 秒。

**修复文件：**
- `flutter/lib/main.dart`（`runMobileApp()`）

**改动：**
```dart
// 修复前：同步等待
await Future.wait([loadCache, loadCache]);
gFFI.userModel.refreshCurrentUser();
runApp(App());

// 修复后：立即渲染，后台加载
runApp(App());
// ↓ 以下异步后台执行，不阻塞首帧
await Future.wait([loadCache, loadCache]);
stateGlobal.essentialDataLoaded.value = true;
gFFI.userModel.refreshCurrentUser();
```

**新增状态：**
- `flutter/lib/models/state_model.dart` → `essentialDataLoaded` + `splashFinished`

---

## 五、信令服务器编译（Mac ARM → Linux x86_64）

**目标：** 在 Mac ARM 上编译出 Ubuntu x86_64 可运行的二进制。

**源码仓库：** `https://github.com/rustdesk/rustdesk-server-demo`

**关键障碍与解决：**

| 问题 | 解决 |
|------|------|
| 无 Homebrew / Docker | 使用 `cargo-zigbuild`（自带交叉编译工具链） |
| zig 下载极慢（~10KB/s，90分钟） | `curl --retry 10` 后台下载，不限时 |
| `non-binding let` 编译错误 | 修改 `config.rs`：`let _=` → `let _lock=` |
| `libsodium` C 库链接失败 | 设置 `AR`=`zig ar` `RANLIB`=`zig ranlib` 替代 macOS 工具链 |

**最终编译命令：**
```bash
ZIG_DIR=$(ls ~/zig | head -1)
ZIG="$HOME/zig/$ZIG_DIR/zig"
export CC="$ZIG cc --target=x86_64-linux-gnu"
export AR="$ZIG ar"
export RANLIB="$ZIG ranlib"
cargo zigbuild --release --target x86_64-unknown-linux-gnu
```

**产物：**
- `target/x86_64-unknown-linux-gnu/release/rustdesk-server`（995KB，ELF x86-64）

---

## 六、bin/ 目录结构

```
rusk-server/bin/
├── README.md
├── rustdesk-server-selfbuilt-x86_64         ← 自己交叉编译（demo 源码）
├── rustdesk-hbbs-official-v1.1.16-x86_64     ← 官方预编译 hbbs
└── rustdesk-hbbr-official-v1.1.16-x86_64     ← 官方预编译 hbbr
```

---

## 七、关键技术点

| 知识 | 说明 |
|------|------|
| macOS TCC | `kTCCServiceScreenCapture` / `kTCCServiceAccessibility` / `kTCCServiceListenEvent` |
| 跨架构交叉编译 | Mac ARM → Linux x86_64 需要 zig 提供的 C 交叉编译器 |
| zig + cargo-zigbuild | 处理 Rust + C 混合项目的交叉编译（如 libsodium） |
| Flutter Android splash | `launch_background.xml` layer-list + PNG bitmap 是最简单可靠的方式 |
| RustDesk 协议 | 客户端与信令服务器之间是标准 protobuf，版本互不绑定 |

---

## 八、Git 备份

```
rusk-server/:  5 commits（clone → docs → cleanup → binary → bin/）
rustdesk/:     v1.0.4 commit（16 files, 420+ insertions）
```
