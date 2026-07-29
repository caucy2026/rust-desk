# KEMI-远程桌面 开发调试记录

> 基于 RustDesk 定制，日期 2026-07-26

## 十四、2026-07-29 PAD 双指滚轮冲突修复与操作栏压缩

### 14.1 交互修复

- 双指手势的首帧仅记录双指落点基线，不再把第二根手指落下造成的距离变化误判为缩放。
- 双指累计纵向移动超过 8px 后优先锁定为远端鼠标滚轮；缩放仅在相对首帧的缩放变化超过 5% 时触发。
- 第二根手指落下时立即释放先前可能开始的单指左键拖动，消除双指滚轮与远端拖动重叠。

### 14.2 操作栏与版本

- PAD 远控页底部操作栏高度从 68px 缩为 44px（减少 24px），并同步压缩图标按钮。
- `Cargo.toml` 与 `Cargo.lock` 升级为 `1.4.13`；`flutter/pubspec.yaml` 升级为 `1.4.13+71`，Android `versionCode` 为 `71`。

## 十三、2026-07-29 PAD 常规触摸手势与操作栏说明

### 13.1 手势

- 单指轻点、长按和拖动继续对应左键、右键和鼠标拖拽。
- 双指纵向滑动发送远端鼠标滚轮；双指捏合仅缩放本地远控画布。
- 三指滑动移动本地远控画布，替代旧的三指滚轮行为。
- 手势说明面板同步更新为上述约定。

### 13.2 操作栏

- PAD 远控页底部操作栏的图标下增加中文短标签：断开、显示、键盘、输入、说明、聊天、更多和收起。
- 新增“说明”按钮，位于已有操作图标之后，直接打开手势与输入模式说明。

### 13.3 版本

- `Cargo.toml` 与 `Cargo.lock` 升级为 `1.4.12`。
- `flutter/pubspec.yaml` 升级为 `1.4.12+70`，Android `versionCode` 为 `70`。

## 十二、2026-07-29 PAD 远端目录恢复优先级与版本 1.4.11

### 12.1 文件传输目录恢复

- 对方目录关闭时仍按设备保存为 `remote_dir`。
- PAD 再次打开文件传输时，优先读取并打开该 `remote_dir`。
- 若目录不存在、无权限或读取失败，则保留原有回退链：远端初始化目录、远端 home、最后的根目录。
- 本地 Android 的 Download 优先级保持不变。

### 12.2 版本

- `Cargo.toml` 与 `Cargo.lock` 的 RustDesk 版本升级为 `1.4.11`。
- `flutter/pubspec.yaml` 升级为 `1.4.11+69`，Android `versionCode` 为 `69`。

### 12.3 验证

- `flutter analyze`：无 error；仅保留既有 info 级弃用与风格提示。
- `flutter build apk --debug`：成功生成 Debug APK。
- PAD `192.168.1.10:5555`：卸载旧包后全新安装成功；`dumpsys package` 核验 `versionName=1.4.11`、`versionCode=69`、`lastUpdateTime=2026-07-29 16:54:19`。
- 通过 `am start` 启动 `MainActivity`，设备前台 Activity 为 `com.carriez.flutter_hbb/.MainActivity`。

## 十一、2026-07-29 工作区与文档整理

### 11.1 统一目录

- 客户端唯一目录调整为 `/Users/newlink/kemi/RustDesk/client`。
- 服务端唯一目录调整为 `/Users/newlink/kemi/RustDesk/server`。
- 不再使用旧的 `/Users/newlink/kemi/rust-desk` 整合副本或 `/Users/newlink/kemi/rusk-server` 服务端副本。

### 11.2 文档边界

- 新增 `kemi-docs/WORKSPACE.md`，说明两个仓库的职责、Git 边界和已退役目录。
- 新增 `kemi-docs/server-operations.md`，仅作为服务端构建、部署文档入口。
- 设备与历史架构资料迁入 `kemi-docs/reference/`；当前客户端事实仍以 `SESSION-HANDOFF.md` 为准。
- 服务端部署脚本和部署说明集中在 `server/bin/`，不再在客户端文档复制完整操作步骤。

## 一、Mac 权限流程

**文件：** `flutter/lib/desktop/pages/desktop_home_page.dart`

- 三合一权限卡片：screen + accessibility + input monitoring 合并为一个 Configure 按钮
- `_requestAllMissingMacPermissions()` 一键触发三个系统授权弹窗
- 首次启动自动弹出权限向导弹窗（`_showMacPermissionGuideDialog`）
- 本地 option 频控避免重复弹窗
- 底部版本号 `v1.0.4`

## 二、PAD 触摸精准点击

**文件：** `flutter/lib/models/model.dart` ~1444 行

**问题：** PAD 连接 Mac 时 `touchMode` 默认 false，点击走鼠标模式（点当前光标位，不跟随手指）。

**修复：** 移动端连非 Android 远端时默认开启触摸模式。

## 三、KEMI 品牌启动画面

**方案：** Android 原生静态 PNG（白底+蓝色圆框+KEMI文字）

**文件：**
- `android/.../drawable-nodpi/kemi_splash.png`（新增，Python PIL 生成）
- `android/.../drawable/launch_background.xml`（引用 PNG）
- `flutter/lib/mobile/widgets/kemi_splash.dart`（已删除，不再需要）

## 四、启动加速

**文件：** `flutter/lib/main.dart`

`runApp()` 不再等待缓存+网络加载，首帧渲染后后台异步执行。

## 五、Mac 命名

**文件：**
- `flutter/macos/Runner/Configs/AppInfo.xcconfig` → `PRODUCT_NAME = KEMI-远程桌面`
- `flutter/macos/Runner/Info.plist` → `CFBundleDisplayName = KEMI-远程桌面`

## 六、信令服务器

**目录：** `/Users/newlink/kemi/RustDesk/server`

- Mac ARM 交叉编译 Linux x86_64：zig + cargo-zigbuild
- `bin/` 目录含自编译版 + 官方预编译版

---

> 客户端 git commit: v1.0.4（16 files, 420+ insertions）  
> 服务器 git commit: 7 commits

---

## 七、2026-07-29 连接与文件传输回归修复（PAD）

### 7.1 改动文件

- `flutter/lib/common.dart`
- `flutter/lib/mobile/pages/remote_page.dart`
- `flutter/lib/mobile/pages/file_manager_page.dart`
- `flutter/lib/models/file_model.dart`
- `src/version.rs`

### 7.2 主要改动细节

1) 文件传输入口与权限流程稳定化（`common.dart`）

- Android 11+ 场景下，`MANAGE_EXTERNAL_STORAGE` 不再走 `request()`，改为跳系统设置页授权，避免回调不返回导致卡住。
- 权限检查失败场景统一降级为日志输出，不阻断页面跳转。

2) 远控页到文件页会话切换修复（`remote_page.dart`）

- 新增 `_openFileTransferFromRemote(...)`：
	- 先关闭远控会话，再 `pushReplacement` 到文件页。
	- 传递 `connToken` 和 `returnToRemoteOnClose: true`。
- 增加 `_handoffToFileTransfer` 保护，避免重复切换。
- 在 `dispose/close` 中按 handoff 状态分流，避免会话被误关或重复关。

3) 文件页返回共享桌面 + PAD 禁删（`file_manager_page.dart`）

- `FileManagerPage` 新增参数：`connToken`、`returnToRemoteOnClose`。
- 顶栏左上按钮：
	- 默认关闭行为保持不变；
	- 从远控进入时显示“返回共享桌面”行为（调用 `_backToRemoteDesktop()`）。
- Android 上删除能力禁用：
	- `_deleteEnabled => !isAndroid`
	- 条目菜单 Delete 置灰禁点（`enabled=false`）
	- 批量删除按钮在 Android 禁用
	- 增加运行时二次保护（即使误触发也直接 return）

4) 文件目录加载可靠性修复（`file_model.dart`）

- 增加 `_readyStarted`，避免 `onReady()` 重入。
- 目录打开引导增强：
	- 本地 Android 增加 `/storage/emulated/0` 兜底；
	- 远端/空路径场景兜底 `/`。
- 首次目录读取改为短周期重试（最多 8 次，每次 1 秒），降低“0 项目”卡住概率。
- 增加关键链路日志：`onReady`、`fetchDirectory`、`receiveFileDir`、`initDirAndHome`、`FileFetcher` 本地/远端读目录。

5) 版本号（`src/version.rs`）

- 仍为 `1.4.9`（本次以功能与稳定性修复为主，未切新版本）。

### 7.3 实机验证结果（2026-07-29）

- 设备：Android PAD（ADB: `192.168.1.10:5555`）
- 安装：`app-debug.apk` 覆盖安装成功
- 流程：共享桌面连接 -> 传输文件 -> 删除限制验证 -> 返回共享桌面

验证结论：

- 文件项菜单 Delete 已禁用（UI 树属性：`clickable=false, enabled=false`）
- 批量删除按钮已禁用（0 项与 1 项已选两种状态下均禁用）
- 文件页可通过左上返回按钮回到共享桌面会话上下文

### 7.4 暂未完成任务（Pending）

- [ ] 以同一流程再跑一遍"远端目录读写 + 小文件双向传输 + Hash 对比"并归档到文档
- [x] 对外交付版本已统一升级到 `1.4.10+68`（见第九节）

---

## 八、2026-07-29 文件传输返回远控黑屏修复

### 8.1 问题

从文件传输页点击左上"返回共享桌面"按钮后，回到 `RemotePage` 时画面黑屏。远控会话处于已连接状态但没有视频帧送达。

### 8.2 根因

`_backToRemoteDesktop()` 关闭文件传输会话后，`Navigator.pushReplacement` 到 `RemotePage`。此时 `RemotePage.initState()` 调用 `gFFI.start()` 没有传递 `connToken`，导致 Rust 侧需要通过 rendezvous 重新建立对等连接。与此同时，旧的文件传输会话仍在拆卸中，对等端（Mac server）无法正确处理新会话请求，视频帧不送达。

### 8.3 修复

- `_backToRemoteDesktop()`：关闭会话前获取 `connToken`，传给 `RemotePage`
- `RemotePage`：新增 `connToken` 可选参数，透传给 `gFFI.start()`
- `FileManagerPage.dispose()`：增加 `_navigatingBackToRemote` 守卫，防止回跳后 dispose 再次调用 `gFFI.close()` 造成重复关闭

### 8.4 验证结果（2026-07-29）

- 从文件传输页点击返回按钮，成功回到远控桌面，画面正常显示 Mac 桌面（非黑屏）
- 日志确认：文件传输会话关闭（`model 260262802 closed`）→ RemotePage 手势识别器初始化（`CustomTouchGestureRecognizer init`）

---

## 九、2026-07-29 文件传输并行浮窗与版本 1.4.10

### 9.1 最终交互

- 保留远控页三点菜单中的“传输文件”入口，不改入口位置和触发方式。
- 文件传输以居中的圆角浮窗显示，宽度和高度均为屏幕的 60%。
- 浮窗使用半透明遮罩，文件浏览、选择和传输均在浮窗内完成。
- Android 本地文件页默认优先打开 `/storage/emulated/0/Download`。

### 9.2 视频与文件传输并行

- 单个 RustDesk Session 的连接类型仍互斥：一个 Session 只能是远控或文件传输。
- 远控页继续使用原有 `gFFI` 视频 Session。
- 文件传输浮窗创建独立 UUID 和独立 `FFI`，建立第二个 `FILE_TRANSFER` Session。
- 打开浮窗时不再关闭远控 Session，关闭浮窗时只关闭文件传输 Session。
- 删除关闭浮窗后重新启动远控 Session 的旧逻辑，避免视频中断、黑屏和重复握手。

### 9.3 目录与版本

- Android home directory 设置为外部存储的 `Download` 目录。
- 本地目录初始化时，Android 的 home directory 优先级高于历史保存的 `local_dir`，避免旧路径覆盖默认 Download。
- 版本升级为 `1.4.10`，Android `versionCode` 升级为 `68`。
- 首页版本号改为读取 APK 的 `PackageInfo.version`，与设备包管理器显示保持一致。

### 9.4 验证结果

- `flutter analyze lib/mobile/pages/file_manager_page.dart`：无新增错误，仅有 3 条已有 API 弃用提示。
- `flutter build apk --debug`：构建成功。
- 设备 `192.168.1.10:5555` 覆盖安装成功。
- 设备包信息：`versionName=1.4.10`、`versionCode=68`。
- 设备安装更新时间：`2026-07-29 15:34:52`。

---

## 十、2026-07-29 跨会话接续文档与 GitHub 流程整理

### 10.1 接续文档

- 新增 `kemi-docs/SESSION-HANDOFF.md`，集中记录：
	- 可直接交给下一会话的完整提示词；
	- 当前提交、版本、远端和不可回退产品行为；
	- Flutter、Rust、Android Kotlin 三层目录和数据流；
	- 双屏、跨屏键盘和独立文件传输 Session 架构；
	- 当前工具链、Debug APK 构建、ADB 安装和实机验收命令；
	- 已知风险、常见误判和新会话启动检查清单。
- 新增 `.github/prompts/continue-kemi-rustdesk.prompt.md`，可在 VS Code Chat 中直接选择“继续 KEMI RustDesk 开发”。

### 10.2 文档导航和 GitHub 流程

- `kemi-docs/README.md` 更新到 `1.4.10+68`，加入接续手册和 prompt 导航。
- `kemi-docs/GIT-OPS.md` 删除旧的第二本地仓复制流程，改为当前真实流程：
	- 开发仓 `/Users/newlink/kemi/RustDesk/client`；
	- 官方上游 `origin` 只读；
	- KEMI 备份使用 `backup`；
	- 提交后执行 `fetch/rebase`、`git push backup master`；
	- 推送后使用 `git ls-remote` 核对远端完整哈希。

### 10.3 验证

- `git diff --check -- kemi-docs .github/prompts`：通过。
- 四份接续文档的 Markdown 代码围栏均成对。
- VS Code 对新 prompt 和三份 Markdown 文档诊断：零错误。
- 旧 `/tmp/flutter_sdk`、`192.168.0.111`、复制到 `rust-desk/client` 和 `git push origin main` 均未作为可执行命令保留。
