# KEMI-远程桌面 开发调试记录

> 基于 RustDesk 定制，日期 2026-07-26

---

## 2026-07-27（跨屏键盘 + Android 硬解扩展）

### 1) 跨屏键盘代理重构（Android）

- 新增 `KeyboardProxyManager` 作为进程级状态源，统一管理 `hidden/opening/visible/closing`。
- 新增 `KeyboardProxyActivity`，通过 `ActivityOptions.launchDisplayId` 在对面屏幕建立原生输入连接。
- `MainActivity` 与 `RemoteActivity` 新增 `keyboard_proxy_prepare/open/close/release` 协议接入。
- `remote_page.dart` Android 分支切换为代理状态驱动：
	- 过渡态禁点 + loading 指示。
	- `onPointerDown` 捕获关闭意图，修复连点/显隐竞态导致的反转点击。
	- `resizeToAvoidBottomInset=false`，避免键盘触发当前页面布局变化。
- 新增文档 `docs/cross-display-keyboard.md`，记录需求、状态机、实现对照、调试实录与遗留项。

### 2) 当天排障结论（摘要）

- 现象不是“完全不弹”，而是“跨屏后可弹但慢”。
- 代理 Activity 启动本身约 0.4-0.5 秒，不是主耗时。
- 主要延迟来自跨屏迁移期间输入法 Service 重建引发的引擎停启链路。
- 详细时间线、失败尝试和最终处理见 `docs/cross-display-keyboard.md` 第 15、16 节。

### 3) Android MediaCodec 解码扩展

- `libs/scrap` 增加 VP9 / AV1 的 Android MediaCodec 解码路径和能力探测分支。

### 4) 源仓对应提交（/Users/newlink/kemi/RustDesk/rustdesk）

- `64b696f` docs: 记录 2026-07-27 跨屏键盘调试全过程
- `74beb57` feat(android): 实现跨屏键盘代理状态机与 Flutter 接入
- `3060399` feat(scrap): 补充 Android MediaCodec VP9/AV1 解码路径

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

**目录：** `/Users/newlink/kemi/rusk-server`

- Mac ARM 交叉编译 Linux x86_64：zig + cargo-zigbuild
- `bin/` 目录含自编译版 + 官方预编译版

---

> 客户端 git commit: v1.0.4（16 files, 420+ insertions）  
> 服务器 git commit: 7 commits
