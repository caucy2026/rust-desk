# KEMI-远程桌面 开发调试记录

> 基于 RustDesk 定制，日期 2026-07-26

## 四十九、2026-07-30 BIN 双端交付与 Windows VSCode 构建提示词（1.4.44+102）

- 本机重新生成 PAD debug APK，核验 `versionName=1.4.44`、`versionCode=102`，APK v1/v2签名通过；版本化副本放入根目录 `BIN/KEMI-远程桌面-PAD-1.4.44+102-debug.apk`，SHA-256为 `61d0d32bc5493d6dc7683c2d87ce3f867ed1fbc3bc3964c19122bb5fdc661fd6`。
- `BIN/KEMI-远程桌面.app` 与当前 macOS release构建的主程序、`service`、版本和 CDHash完全一致，继续保留原固定签名身份；新增 `BIN/KEMI-远程桌面-macOS-arm64-1.4.44+102.zip`，压缩包校验通过，SHA-256为 `a98ed5a211bde86cbe805029d4aa5616ab1c977bc145c2a9dda075b93201d1a3`。
- 当前登录钥匙串仍显示 `0 valid identities`，现有固定自签名链在本机构建环境显示 `CSSMERR_TP_NOT_TRUSTED`；因此没有用未签名新包覆盖BIN App。恢复原固定证书/私钥之前，可以复用当前已固定签名包，但不能宣称完成新的固定签名。
- 新增 `windows-vscode-build-prompt.md`：Windows同事可把完整提示词交给VSCode AI，按固定 Visual Studio、LLVM 15、Rust 1.75、Flutter 3.24.5、自定义engine、default bridge和vcpkg commit构建Windows x64便携客户端，并强制记录commit、版本、哈希、签名与启动结果。当前Windows资源仍可能显示RustDesk，仅重命名EXE不算完成KEMI品牌化，提示词要求构建后如实核验并单独登记品牌化任务。
- GitHub focused run `30541481850` 的default bridge和TopMost x64成功，但Windows x64与Linux x64均在`Build rustdesk`失败；本次只生成`bridge-artifact`和`topmostwindow-artifacts-x64`，没有Windows/Linux最终客户端，不能把中间产物当成交付包。

## 四十八、2026-07-30 本地/云端分工与精简制品流水线（1.4.44+102）

- 云端审计确认远端最新提交仍为 `262bbedef0d6dc9df39b85c12b315458dcef4117`；`Full Flutter CI #30518880603` 与 `CI #30518880357` 均已失败，并非仍在排队。默认 bridge 成功，但 Windows x64、Linux x64、Android 与 macOS 均在 Build 步骤失败，只留下 bridge 中间 artifact，没有最终 Windows EXE 或 Linux AppImage。
- 根因之一是普通 `flutter-ci.yml` 固定 `upload-artifact: false`，即使成功也不会交付最终安装包；而 nightly/tag 会启动约二十多个无关平台/架构 job，Windows x64 和 Linux x64还分别受到 ARM 矩阵失败连带阻断。
- 新增 `.github/workflows/kemi-distribution.yml`，master 非纯文档 push 或手动触发时，仅运行默认 bridge、Windows x64 与 Linux x86_64 → AppImage。新提交会取消同分支旧 run；Windows/Linux 最终包保留 14 天 artifact，并由单一汇总 job生成 `manifest.json`、`SHA256SUMS.txt` 和唯一 `kemi-<commit>` 候选 prerelease。
- `flutter-ci.yml` 改为 PR/手动完整验证，不再在每次 master push 启动全平台大矩阵。`ci-build.md` 已重写为本地/云端职责、2–3 分钟持续监控、失败分类、`Cloud Ready → Imported → Delivery Done` 三阶段门禁和最终 PAD 集成的唯一说明。
- 本次只调整研发/构建流程与文档，产品版本保持 `1.4.44+102`。在新 commit 推送并取得 focused run 成功产物前，Windows/Linux 仍保持“待导入”。

## 四十七、2026-07-30 PAD 自分发与四端安装包流程归档（1.4.44+102）

- 将局域网“客户端”入口的实现、需求和交付顺序收敛到 `kemi-docs/client-distribution.md`。明确 Android APK 并非构建时再嵌入 assets，而是运行中从已安装 App 的 `applicationInfo.sourceDir` 以固定下载路由流式提供；因此下载包与当前 PAD 已安装包的内容、版本和签名一致，也不会使 APK 因复制自身而翻倍。
- macOS ZIP、Windows x64 EXE、Linux x86_64 AppImage 则属于构建前置入 `assets/client-dist/` 的静态包，会随最终 PAD APK 一起编入。文档已固定三者的文件名、架构、导入时机和“存在才可下载”的白名单规则，避免将旧包或未验证 CI artifact 误交付。
- 新增四端共同的版本、来源 commit、哈希、签名/安装核验要求，以及“候选制品 → 记录核验 → 导入 assets → 构建最终 PAD → 同网下载安装”的顺序。Windows/Linux 当前仍待导入，不因本文档变更伪造可下载版本。
- 本节只整理交付流程与文档，产品版本保持 `1.4.44+102`，不产生新的安装包。

## 四十六、2026-07-30 macOS 登录项、主页标签与产品版本统一（1.4.44+102）

- 已澄清并修复目标：Dock 右键 App 的「选项 → 登录时打开」是 macOS 系统菜单，应用不能改写其文案；之前修改的是 KEMI 自己设置页，因此没有影响该菜单。macOS 13+ 现改用官方 `SMAppService.mainApp` 读取、注册和取消注册，KEMI 的“开机自启”开关与该系统项共用同一状态；旧 `~/Library/LaunchAgents/com.carriez.kemi-remote-desktop.plist` 已迁移删除。
- 实机验收显示系统后台项为 `enabled, allowed, notified`，Bundle Identifier 为 `com.carriez.rustdesk`，URL 已修复为 `/Applications/KEMI-远程桌面.app`；不会再指向临时构建目录。低于 macOS 13 的兼容分支仍保留旧 LaunchAgent 实现。
- 根因：`DesktopTabPage` 启动时只添加主页；密码编辑图标调用 `DesktopSettingPage.switch2page(SettingsTabKey.safety)` 才临时添加设置页。Mac 现在启动即创建“主页 / 设置”标签并默认停留在主页，实机截图已核验。
- 关于页的“版本”已从 Rust `mainGetVersion()` 改为 Flutter `PackageInfo`，显示 `KEMI-远程桌面 v<version> (<build>)`，避免内核版本与交付 App 版本混淆。
- Mac `1.4.44+102` 已固定签名并部署到 `/Applications` 与 `BIN`；PAD `192.168.1.10:5555` 已实机安装 `1.4.44+102`。PAD 内置 Mac ZIP 为 25,919,077 字节，SHA-256 `61b1db19981123698a3b3f83b24732f3911adb6318030add7ae53bc25222d29d`。

## 四十五、2026-07-30 授权卡片内部版本号移除（1.4.42+100）

- 实机复现确认：BIN 的旧交付包虽已显示中文授权卡片，却把开发期内部标记“授权流程 v1.0.11”直接显示在用户界面，容易被误认为应用仍是旧版本；该标记不是 App 版本，也不参与任何授权判断。
- 已删除该硬编码标记及 `footerText` 传递，未改动屏幕录制、辅助功能的申请流程、状态判断或“权限配置”入口。新 App 授权卡片仅保留用户可理解的说明与按钮。
- 曾有一次 Flutter 命令在仓库根目录执行，报 `No pubspec.yaml file found`，因此没有生成新 App；现已改在 `client/flutter` 正确目录重新构建。`/Applications/KEMI-远程桌面.app`、`BIN/KEMI-远程桌面.app` 均为 `1.4.42+100`，固定签名深度校验通过；PAD `192.168.1.10:5555` 已安装 `1.4.42+100`。新版 Mac ZIP 为 25,917,797 字节，SHA-256 `5d9359609dbbbbd42018ff90c898fa35e11604510a011247fb91954b2b89bcf4`，已内置到 PAD 分发包。

## 四十四、2026-07-30 Mac “开机自启”交付包同步（Mac 1.4.40+98；PAD 1.4.41+99）

- 根因已确认：源码自 `1.4.38+96` 已将 macOS 登录项主标题翻译为“开机自启”，但 `/Applications`、`BIN/` 与 PAD assets 仍是旧的 `1.4.35+93` App，实际运行的旧二进制当然不会显示新文案。
- 已用当前源码重建 macOS Apple Silicon release App，复制 `service`，以固定 `KEMI Local App Signing 2026` 身份重签。`/Applications/KEMI-远程桌面.app` 与 `BIN/KEMI-远程桌面.app` 现均为 `1.4.40+98`，`codesign --verify --deep --strict` 均通过；选项含义仍是“登录当前 Mac 后自动启动 KEMI”，不是系统启动前运行。
- 已将该 App 重新封装为 PAD assets 的 macOS ZIP；ZIP 为 25,918,660 字节，SHA-256 `19d01ea988d09efec7e55ed7315068de9aee4ad537fa81d4004a277da9253ba7`，解压后版本核验为 `1.4.40`。因 PAD 内置分发资源变更，PAD 交付版本提升为 `1.4.41+99`。

## 四十三、2026-07-30 Wi-Fi 定位设置回跳与 Apple 图标（1.4.40+98）

- Wi-Fi 名称权限已授予但系统定位服务关闭时，客户端页可直接进入系统定位设置；用户打开后返回 KEMI，页面自动重新读取并显示真实 SSID。
- macOS 下载项改为系统 Apple 标志，避免原认证素材的圆形底色掩盖标志轮廓。

## 四十二、2026-07-30 真实 Wi-Fi 名称与下载平台图标（1.4.39+97）

- Android 读取已连接 Wi-Fi 的 SSID 受系统位置权限保护。客户端页现在明确提供“授权显示当前 Wi-Fi 名称”按钮；用户同意后才显示真实 SSID。权限仅用于本机读取名称，不上传位置；若系统定位服务关闭，会提示用户打开后重进页面。
- 可下载客户端按平台使用对应标志：Android 机器人、Apple、Windows 窗格和 Linux 企鹅；无安装包的平台仍保持灰色“待导入”状态。

## 四十一、2026-07-30 客户端页引导、PAD 悬浮入口与 Mac 开机自启文案（1.4.38+96）

- 客户端页不再把 Android 无法读取 SSID 的状态伪装为“当前 Wi-Fi”。现在始终明确要求 PAD 与下载设备位于同一 Wi-Fi，只有拿到真实 SSID 时才额外显示名称。
- 页面明确说明：在下载设备浏览器输入网址，或扫描右侧二维码，二选一即可；二维码下方同步显示同样提示。
- PAD `MainActivity` 停止后不再启动 `FloatingWindowService`，并主动停止已有服务。该悬浮入口没有有效操作且会遮挡客户端下载页，现已从用户流程移除。
- macOS 设置中的选项仍是“登录当前 Mac 后启动 KEMI”（每个用户的登录项），中文主标题统一为“开机自启”，说明文字保留真实触发时机，避免误解为系统未登录前启动。

## 四十、2026-07-30 客户端下载布局与多平台状态（1.4.37+95）

- 客户端页在横向 PAD 上改为左右布局：Wi-Fi 和短网址固定在左半屏，二维码移到右侧；窄屏仍自动上下排列，避免挤压地址。
- 下载列表和 HTTP 网页固定展示 PAD / Android、macOS（Apple 芯片）、Windows（x64）、Linux（x86_64）。只有实际存在且核验过的包可点下载；Windows、Linux 当前清楚标为“待导入”，不会返回空文件或假链接。
- 已将 `BIN/KEMI-远程桌面.app`（`1.4.35+93`、固定签名）用 `ditto` 打为 macOS 离线 ZIP 并放入 Android assets；解压后的 `codesign --verify --deep --strict` 已通过。PAD `192.168.1.10:5555` 已安装 `1.4.37+95`，网页确认四个平台均显示；Mac ZIP（25,917,281 字节）的 PAD 下载副本与 assets SHA-256 一致：`fe34c2e36c803bcaa2ccf8de12f23b1fa9c158df23805d76b60900ae761ecce7`。未导入的 Windows/Linux 下载路由均返回 404。

## 三十九、2026-07-30 PAD 局域网客户端下载（1.4.36+94）

- 首页底部新增与现有导航一致的“客户端”图标（`devices_outlined`）。进入页面立即启动轻量 HTTP 服务，显示同一 Wi-Fi 提示、简短的 `http://IP:8686` 地址、复制按钮与二维码；离开页面或销毁 App 时立即关闭服务，不后台常驻。
- HTTP 服务只允许 `GET /`、`GET /health` 和固定的下载路由；不提供目录浏览、任意文件读取或上传。当前 PAD APK 从应用自身已安装包提供，保证版本相同；Windows、macOS、Linux 包仅在构建 APK 前已放入固定 assets 路径且存在时显示，不能伪造下载项。
- 新增 `kemi-docs/client-distribution.md` 作为唯一说明，明确同一 Wi-Fi 要求、安装包命名、构建前导入和下载验收规则。`client-download-preview.html` 保留为网页视觉原型。
- 已用本机 Flutter 3.29 构建 PAD debug APK 并覆盖安装到 `192.168.1.10:5555`；系统确认 `versionName=1.4.36`、`versionCode=94`。同网 Mac 访问 `/health` 返回 `ok`，网页和 APK 下载均为 `200`，最终下载文件（156,990,133 字节）与 PAD 已安装 `base.apk` 的 SHA-256 一致：`d69da2ba93a0f46401a093bdbe90173ec6b9aadc8c291eb7d3680d5322d8794b`；切换到“设置”后端口立即拒绝连接。构建后已恢复仓库固定 Flutter 3.24.5 锁文件基线。

## 三十八、2026-07-30 构建策略收敛（1.4.35+93）

- 固定执行原则为“本地优先、云端兜底”：PAD/Android 与 macOS 必须先在本机完成构建、安装或签名核验；Linux 若当前 Mac 可可靠交叉编译也先本构建。只有受宿主、CPU、GUI/自定义 engine 或工具链限制而本地不能可靠产出的目标（当前主要为 Windows）才使用 GitHub Actions。
- GitHub 推送仅是代码备份，不把自动触发的 Actions 视为交付动作；任务 `queued` 或 `in_progress` 均不算成品，必须对应平台 job 成功且 artifact 可取得才可报告云端客户端已产出。
- 规则已收敛到 `kemi-docs/ci-build.md`，文档入口同步更新；本节不改变产品代码或安装包，版本继续为 `1.4.35+93`。

## 三十七、2026-07-30 单屏设备软键盘兼容（1.4.35+93）

- 目标规则保持为“双屏时弹到对面屏；仅一块可用屏幕时弹回当前屏”。本次将单屏回退从“把 `launchDisplayId=0` 交给 ROM 处理”改为由当前 Activity 直接启动键盘代理，避免部分 ROM 对默认 Display 路由不稳定而不显示键盘。
- `KeyboardProxyManager` 在没有可用副屏时记录并明确选择源 Display；`KeyboardProxyActivity` 仅当源/目标不同才使用 `ActivityOptions.launchDisplayId`。因此双屏 `0 ↔ 2` 的对向键盘逻辑、状态机和输入转发保持不变。
- 已完成 Kotlin、Rust release 与 PAD debug APK 构建；PAD `192.168.1.10:5555` 已安装并由系统确认 `versionName=1.4.35`、`versionCode=93`，APK v1/v2 签名有效。当前测试机为双屏，已核对其 Display 0（内置）与 Display 2（HDMI）在线；单屏分支需在单屏设备上做最终实机显示验证。
- macOS release App 已构建、固定签名并部署到 `/Applications` 与 `BIN/`，两份均为 `1.4.35+93` 且通过深度校验。旧 `1.4.34+92` 两份 App 已移入废纸篓，可恢复。

## 三十六、2026-07-30 Android 共享屏幕直接录屏授权（1.4.34+92）

- 启动共享屏幕时移除防诈骗倒计时和“服务即将启动”的应用内警告；“服务未运行”卡片及“屏幕录制”权限行现在都直接进入同一启动链路。
- `ServerModel.toggleService()` 不再在共享屏幕启动前请求通知、文件访问或“显示在其他应用上层”权限。它们保留在权限配置中，按各自功能单独申请，避免把悬浮窗设置页插入录屏流程。
- 新增中文操作简介：点击“启动服务”后，在 Android 系统录屏确认中选择“允许”或“立即开始”，完成后即可由其他设备连接。系统录屏确认是 Android 的强制安全确认，应用不能代替用户自动同意。
- 已实机验证 PAD `192.168.1.10:5555`：点击后焦点直接进入 `MediaProjectionPermissionActivity`，显示“要开始使用 KEMI远程桌面录制或投射内容吗？”和“立即开始”，无悬浮窗设置跳转。PAD 系统确认 `versionName=1.4.34`、`versionCode=92`；APK v1/v2 签名有效。
- Rust release 与 macOS release App 均构建成功；`/Applications` 与 `BIN/` 的 Mac App 均升级至 `1.4.34+92`，使用固定 `KEMI Local App Signing 2026` 深度签名并通过校验。旧 `1.4.33+91` 两份 App 已移入废纸篓，可恢复。

## 三十五、2026-07-30 双指滚轮必须双指共同移动（1.4.33+91）

- 现象：一根手指按住不动、另一根手指纵向滑动时，PAD 已错误发送远端鼠标滚轮；这不符合“双指同时纵向滑动”的约定。
- 根因：`remote_input.dart` 原始指针路径只要检测到两个触点仍在屏幕上，就按两点焦点的位移发送滚轮。静止触点与移动触点的焦点仍会移动一半；同时，`ScaleGestureRecognizer` 的高层路径也以焦点位移独立判定滚动，导致同一错误有两条触发链。
- 修复：原始指针路径成为唯一滚轮发送方。它在第二根手指落下时记录两点起点，只有两根手指都纵向移动至少 6px、累计和最近位移方向一致、且最近移动相隔不超过 80ms 时才开始滚动；滚动中任一手指停住或方向不一致即停止累计。高层 Scale 路径只保留双指捏合缩放，永不再发送滚轮。
- 版本统一升级为 `1.4.33+91`。Rust release、PAD debug APK 与 macOS release App 均构建成功；PAD `192.168.1.10:5555` 系统确认 `versionName=1.4.33`、`versionCode=91`；`/Applications` 与 `BIN/` 的 Mac App 均为 `1.4.33+91` 并通过固定证书深度校验。旧 `1.4.32+90` 两份 App 已移入废纸篓，可恢复。

## 三十四、2026-07-30 首页快捷功能选中说明（1.4.32+90）

- 主页中间的“最近访问、收藏、已发现、地址簿、可访问设备”实际由 `flutter/lib/common/widgets/peer_tab_page.dart` 共享渲染，PAD 主页与 macOS 桌面主页均使用这一组件；因此只在共用组件实现，不复制两套行为。
- 图标栏下新增常驻的当前选中说明行，显示“功能名称：用途备注”，点击任一图标时以短动画立即切换。说明分别解释最近重连、常用收藏、局域网发现、地址簿同步和账号可访问设备，窄屏时单行省略，不挤压图标操作区。
- KEMI 的目标测试界面为中文，说明集中在 `PeerTabModel` 的共用 UI 数据中；不新增上游 RustDesk 翻译键，避免破坏 `src/lang/template.rs` 的键清单。多选操作期间保持原有多选工具栏，不显示说明行，避免干扰批量操作。
- 版本统一升级为 `1.4.32+90`：Cargo、Flutter、Windows/Linux CI、AppImage、rpm、Arch 均同步。已用 Flutter `3.24.5` 构建 PAD debug APK，APK 内部核验为 `com.carriez.flutter_hbb`、`versionName=1.4.32`、`versionCode=90`、v1/v2 签名有效，并已覆盖安装到 `192.168.1.10:5555`；系统 `dumpsys` 确认实际版本为 `1.4.32+90`。
- macOS release App 已构建并用固定 `KEMI Local App Signing 2026` 深度签名，`CFBundleIdentifier=com.carriez.rustdesk`、`CFBundleShortVersionString=1.4.32`、`CFBundleVersion=90`，两份交付包（`/Applications`、`BIN/`）均通过 `codesign --verify --deep --strict`。旧 `1.4.31+89` 两份 App 已移入废纸篓，可恢复；新进程已从 `/Applications/KEMI-远程桌面.app` 启动。
- 当前 Apple Silicon 系统未提供 Rosetta，Flutter `3.24.5` 的 Intel macOS AOT 工具无法本机执行；因此 macOS 包使用本机原生 Flutter `3.29` 构建。仅在本机 Pub 缓存为 `extended_text 14.0.0` 补齐两个新版 Flutter 选择接口，仓库 `pubspec.yaml`、锁文件和 CI 基线仍保持 Flutter `3.24.5` / `extended_text 14.0.0`，没有将该缓存修补提交进仓库。

## 三十三、2026-07-30 macOS 配置审计与 GitHub bridge 基线修复（1.4.31+89）

- 新增 `kemi-docs/macos-configuration.md`，集中维护当前 App bundle、固定证书链、两项远控权限、授权窗口恢复、TCC 重置边界、交付核验和开机自启动；不再把操作规则散落在历史记录中。审计确认 `/Applications/KEMI-远程桌面.app` 为 arm64、`com.carriez.rustdesk`、`1.4.31+89`、`LSUIElement=1`，签名链为固定叶证书 `KEMI Local App Signing 2026` → 本地测试根证书；`codesign --verify --deep --strict` 已通过。
- 代码逻辑复核：屏幕录制负责画面；辅助功能是 `handle_mouse_simulation_()` 与 `handle_key_()` 注入事件的唯一准入条件。输入监控只服务 Mac 作为控制端时的可选键盘输入源，不能再加入 PAD 控制的必需权限、弹窗或状态判断。详细调用链和验证顺序已写入 macOS 配置文档。
- 云端失败根因：2026-07-23 的 Flutter 3.29 本地依赖解析把 `extended_text` 提升为 `^15.0.2`，但默认 GitHub CI 仍使用 Flutter 3.24.5／bridge 3.22.3；3.22 因依赖版本不兼容而失败，3.44 专用补丁又因其只匹配 `14.0.0` 而失败。恢复仓库基线为精确 `extended_text 14.0.0`，重新执行 `flutter pub get` 锁定到 14.0.0；3.22 临时降为 13，3.44 临时升为 15 的既有策略恢复可用。
- `bridge.yml` 的工具安装已补充 Cargo sparse registry、10 次内部网络重试和 600 秒 HTTP 超时，外层保留 3 次短重试及按工具版本缓存，避免短暂 crates.io 波动同时击穿两条 bridge 作业。新增 `kemi-docs/ci-build.md` 记录作业依赖、版本策略、云端查询和验收规则。
- 本节不产生新的安装包，产品版本保持 `1.4.31+89`；后续若重新构建并交付 App，必须重新执行 macOS 配置文档第 4 节的固定签名与核验，不能让源码、版本号和交付包失配。

## 三十二、2026-07-30 macOS 必需权限收敛为两项（1.4.31+89）

- 复核结论：PAD 远程查看本机只依赖屏幕录制；PAD 鼠标、滚轮和键盘控制本机只由辅助功能授权拦截。`ensure_remote_input_permissions()` 的实际判断只有辅助功能。
- 输入监控仅在 Mac 本机作为控制端时，用于 RustDesk 的本地键盘输入源抓取；未授予时程序会自动回退另一输入源。它不应阻止 PAD 控制这台 Mac，也不应出现在“远程查看和控制本机”的必需授权清单。
- 日志证据：TCC 一直把调用主体识别为 `com.carriez.rustdesk` 和 `/Applications/KEMI-远程桌面.app`；输入监控设置页中的无主体请求来自 macOS 系统设置扩展，非 KEMI App。因此继续引导用户在该页面授权既无必要，也无法解决远控功能。
- 修复：权限卡片、首次启动引导、PAD 新连接提醒、完成状态和 PAD 端“需要 Mac 输入权限”说明均收敛为“屏幕录制 + 辅助功能”两项；移除第三项按钮及其 `Privacy_ListenEvent` 调用，杜绝再打开含空白项目的系统页。输入监控的底层状态检查仍保留给可选的本地键盘输入源，不参与远控准入。
- 版本统一升级为 `1.4.31+89`：Cargo、Flutter、Windows/Linux CI、AppImage、rpm、Arch 均同步。Rust release 与 macOS release 构建已通过；`/Applications/KEMI-远程桌面.app` 和 `/Users/newlink/kemi/RustDesk/BIN/KEMI-远程桌面.app` 均已用固定 `KEMI Local App Signing 2026` 重签，并通过 `codesign --verify --deep --strict`，版本均为 `1.4.31+89`。旧 `1.4.30+88` 两份 App 已移入废纸篓，可恢复；已精确重置 KEMI 的 TCC 记录，等待人工重新确认两项授权。

## 三十一、2026-07-30 macOS 输入监控登记接口修正（1.4.30+88）

- 现象：`1.4.29+87` 中“输入监控”仍经由 IOHID 原始设备监听接口申请；系统隐私页会出现空白项目，用户无法判断该为哪个 App 授权。
- 根因：IOHID 的 `kIOHIDRequestTypeListenEvent` 面向 `IOHIDManager/IOHIDDevice` 原始设备访问；KEMI 需要的是全局 `CGEvent` 监听授权。macOS SDK 明确提供 `CGPreflightListenEventAccess` 与 `CGRequestListenEventAccess` 作为该场景的查询、申请接口。
- 修复：Flutter 前台 Runner 的第三项“申请授权”改用 `CGRequestListenEventAccess`；Rust 侧状态检查改用 `CGPreflightListenEventAccess`，不再调用 IOHID 原始设备授权。前台 App 保持为唯一申请者，失败或已拒绝时仍精确打开 `Privacy_ListenEvent` 设置页。
- 版本统一升级为 `1.4.30+88`：Cargo、Flutter、Windows/Linux CI、AppImage、rpm、Arch 均同步。已完成 Rust release 与 macOS release 构建；`/Applications/KEMI-远程桌面.app`、`/Users/newlink/kemi/RustDesk/BIN/KEMI-远程桌面.app` 已用固定 `KEMI Local App Signing 2026` 签名并通过 `codesign --verify --deep --strict`，版本均为 `1.4.30+88`。旧 `1.4.29+87` 两份 App 已移入废纸篓，可恢复。
- 验证准备：仅执行 `tccutil reset ListenEvent com.carriez.rustdesk`，屏幕录制、辅助功能均未清除；从新的 `/Applications` App 点击“输入监控 → 申请授权”后，需人工确认系统隐私列表中显示 `KEMI-远程桌面`，再刷新 KEMI 状态。

## 三十、2026-07-30 macOS 授权完成后主窗口恢复（1.4.29+87）

- 现象：同意首项“屏幕录制”授权后，KEMI 窗口从前台消失，视觉上像程序退出。
- 诊断：无崩溃报告；主进程与 `--cm` 连接进程仍在运行，KEMI 主窗口仍存在但 `frontmost=false`。根因是 `LSUIElement` 代理型 App 在系统授权/系统设置取得焦点后未重新激活。
- 修复：Flutter 每秒状态检查只在某一项 TCC 权限实际变为已授权后，调用原生 Runner 的 `activateMainAppWindow`；该方法通过 `NSApp.activate`、`makeKeyAndOrderFront` 和 `orderFrontRegardless` 恢复主窗口。授权尚未完成时绝不执行前置，不会遮挡系统确认窗口。
- 版本统一升级为 `1.4.29+87`：Cargo、Flutter、Windows/Linux CI、AppImage、rpm、Arch 均同步。
- 已成功执行 Rust release 构建与 macOS release 构建，使用固定 `KEMI Local App Signing 2026` 重签、`codesign --verify --deep --strict` 核验通过。`/Applications/KEMI-远程桌面.app` 与 `/Users/newlink/kemi/RustDesk/BIN/KEMI-远程桌面.app` 均已替换为 `1.4.29+87`；旧 `1.4.28+86` 两份 App 已移入废纸篓，可恢复。屏幕录制授权保留，未再次重置任何 TCC 项。

## 二十九、2026-07-30 macOS 输入监控名称登记修复（1.4.28+86）

- 现象：点击“输入监控”的“申请授权”后，macOS 隐私设置出现一个无名称项目，而不是 `KEMI-远程桌面`。
- 修复：新增 macOS Runner 主线程通道 `requestInputMonitoring`。只有前台 KEMI App 调用 `IOHIDRequestAccess`；Rust FFI 的同名接口降为只读状态检查，绝不再从底层桥接发起申请。申请失败或此前已拒绝时仍打开 `Privacy_ListenEvent` 精确系统设置页。
- 这样 TCC 的登记主体与 `com.carriez.rustdesk` 主 App bundle 一致；已存在的空白历史记录必须在验证前单独重置 `ListenEvent` 后才会消失。
- 版本统一升级为 `1.4.28+86`：Cargo、Flutter、Windows/Linux CI、AppImage、rpm、Arch 均同步。
- 已成功执行 Rust release 构建与 macOS release 构建，使用固定 `KEMI Local App Signing 2026` 重签、`codesign --verify --deep --strict` 核验通过。`/Applications/KEMI-远程桌面.app` 与 `/Users/newlink/kemi/RustDesk/BIN/KEMI-远程桌面.app` 均已替换为 `1.4.28+86`；旧 `1.4.27+85` 两份 App 已移入废纸篓，可恢复。
- 验证前已精确执行 `tccutil reset ListenEvent com.carriez.rustdesk`，只清除输入监控历史条目，屏幕录制和辅助功能授权保持不变；新 App 从 `/Applications` 启动，等待在权限页进行一次人工确认。

## 二十八、2026-07-30 macOS 永久权限配置入口（1.4.27+85）

- 根因：首页左下“权限设置”卡片仍然只在三项原生检查任一项返回未授权时显示；TCC 状态缓存或短暂误判为已授权时，用户会失去进入权限配置的唯一入口。
- 修复：macOS 首页始终显示“权限设置”卡片。未完成授权时按钮名为“去授权”；三项都显示已授权时按钮名为“权限配置”，两种状态都进入同一个独立三项授权窗口。
- 已完整构建并固定签名安装 `1.4.27+85` 到 `/Applications` 和根目录 `BIN`；旧 `1.4.26+84` 两份 App 已移入废纸篓，可恢复。

## 二十七、2026-07-30 macOS 独立授权按钮与开机自启动（1.4.26+84）

### 27.1 权限申请改为独立操作

- 取消“申请全部授权”的串行定时器。此前最后一项输入监控会因前项状态等待、系统弹窗未显示或 TCC 状态未及时刷新而没有任何可操作反馈。
- 权限引导现在在“屏幕录制、辅助功能、输入监控”每行显示自身状态与独立“申请授权”按钮；每次点击只调用对应 macOS API，三项互不阻塞。
- 输入监控在 `IOHIDRequestAccess` 未显示或返回失败时，自动打开 `Privacy_ListenEvent` 系统设置页，确保用户始终能手动完成这一项。
- 为清除过去多次临时签名遗留的 TCC 状态，已在当前测试 Mac 执行精确 `tccutil reset All com.carriez.rustdesk`；它只撤销 KEMI 的权限，当前固定签名包需重新逐项授权。

### 27.2 macOS 开机自启动

- 通用设置新增“开机自启动 / 登录此 Mac 后自动启动 KEMI”。启用时仅在当前用户的 `~/Library/LaunchAgents/` 写入 `com.carriez.kemi-remote-desktop.plist`，用当前 App 可执行文件作为启动参数；关闭时删除该文件。
- 不要求管理员权限、不安装系统级 daemon，也不会影响 RustDesk 服务端。

### 27.3 版本、构建与交付

- 版本统一升级为 `1.4.26+84`：Cargo、Flutter、Windows/Linux CI、AppImage、rpm、Arch 均同步。
- `cargo build --locked --features flutter --release` 与 `flutter build macos --release` 均成功；Flutter 静态检查无新增 error（仅既有 plugin 与弃用提示）。
- `/Applications/KEMI-远程桌面.app` 和 `/Users/newlink/kemi/RustDesk/BIN/KEMI-远程桌面.app` 已替换为固定签名的 `1.4.26+84`，指定签名规则保持 `com.carriez.rustdesk + 固定应用证书`。旧 `1.4.25+83` 两份 App 已移入废纸篓，可恢复。

## 二十六、2026-07-30 macOS 前台串行授权引导与 PAD 重连提醒（1.4.25+83）

### 26.1 根因与重构

- 旧流程显示说明后立即写入“已提示”标记，并同时调用屏幕录制、辅助功能、输入监控三个 macOS 系统申请；系统设置抢到前台后说明窗口被遮挡，且一次未完成授权会导致以后不再提示。
- `ensure_remote_input_permissions()` 过去在首个远程输入事件中申请系统权限，并用进程级开关抑制后续提示；PAD 再次连接同一 Mac 进程时不会有任何新的授权提醒。
- 新流程删除持久“已显示”标记和输入事件内的系统申请。KEMI 主窗口成为唯一的授权入口；用户点击“一键申请全部授权”后自动串行请求所有缺项：上一项实际授权完成后才请求下一项，每次请求前恢复 KEMI 到前台，避免多个 macOS 系统窗口互相遮挡。申请后不关闭说明，用户回到 KEMI 可刷新三项状态。
- 每次 PAD 新建连接且仍有缺项时，主窗口与授权引导重新置前（5 秒限频），说明明确告知缺少授权会造成无画面、无法点击、滚动或输入。

### 26.2 PAD 提示与版本

- PAD 端“远端 Mac 没有画面”与“需要 Mac 输入权限”说明改为“返回 KEMI 刷新状态后重新连接”；等待画面弹窗按钮改为中文“Mac 无画面帮助 / 取消连接”。
- 版本统一升级为 `1.4.25+83`：Cargo、Flutter、Windows/Linux CI、AppImage、rpm、Arch 均同步；PAD 实机安装与 Mac 完整构建结果在本节后续补记。

### 26.3 macOS 固定测试签名与 TCC 授权身份

- 根因确认：此前本机构建使用 `codesign --sign -` 的 ad-hoc 签名。该签名的 designated requirement 依赖每次构建变化的 `cdhash`，macOS TCC 会将升级包视为新的权限主体。因此系统设置中旧包看似已经打开三项开关，当前包刷新仍会显示未授权。
- 在当前用户登录钥匙串建立仅供测试的固定证书链：`KEMI Local Development Code Signing 2026` 为本机构建信任根；`KEMI Local App Signing 2026` 为应用签名证书，明确包含 `Key Usage: Digital Signature` 与 `Extended Key Usage: Code Signing`。私钥不上传、不用于正式发布。
- 新增 `res/sign-kemi-local-macos.sh`。脚本拒绝找不到固定身份的构建机，签署后验证 bundle，并输出 designated requirement。已对构建产物和 `/Applications/KEMI-远程桌面.app` 的 `1.4.25+83` 执行验证；规则固定为 `com.carriez.rustdesk + 证书指纹`，不再是 ad-hoc `cdhash`。
- 首次从旧 ad-hoc 包切换到固定签名包时，每台测试 Mac 需要重新确认一次屏幕录制、辅助功能和输入监控；此后必须保持同一证书签名，且测试机不得二次签名或解包改动应用。测试机不需要导入或信任该根证书。
- 该方案只解决团队测试的签名稳定性。正式官网/DMG 分发必须改用 Apple `Developer ID Application` 证书并公证；Mac App Store 则使用 `Apple Distribution`。

## 二十五、2026-07-30 macOS 授权中文化与桌面端版本 1.4.22+80

### 25.1 macOS 授权引导中文化

- `desktop_home_page.dart` 的权限卡片、三项授权清单、授权状态、操作按钮和完成提示全部改为中文：屏幕录制、辅助功能、输入监控、已授权/未授权、去授权、查看说明、申请授权。
- 授权引导的本地“已显示”标记升级为 `v1.0.5`，已运行旧版的设备会自动再显示一次中文引导；新标记仍保证同一版本只显示一次。
- `model.dart` 的“远端 Mac 无法注入输入”和“远端 Mac 无画面”异常提示也改为中文，并统一使用 `KEMI-远程桌面` 名称。
- macOS 系统设置页及 Apple 原生弹窗的语言由系统语言决定，应用无法强制替换；本次修改覆盖 KEMI 应用自身此前写死的英文说明列表。

### 25.2 版本与 macOS 验证

- `Cargo.toml`、`Cargo.lock`、`src/version.rs`：`1.4.22`。
- `flutter/pubspec.yaml`：`1.4.22+80`。
- Windows/Linux 的 CI、AppImage、rpm 与 Arch 打包配置同步为 `1.4.22`，避免跨平台产物沿用旧的 `1.4.9` 文件名或元数据。
- 已构建、安装并启动 macOS arm64 版；`Info.plist` 核验 `CFBundleShortVersionString=1.4.22`、`CFBundleVersion=80`，`codesign --verify --deep --strict` 通过。
- 旧版 `1.4.21+79` 已移入废纸篓，保留可恢复副本；Android PAD 当前已安装包仍为 `1.4.21+79`，尚未重新打包部署。

### 25.3 Windows / Linux 终端范围

- Windows 与 Linux 使用同一套 `flutter/lib/` 界面及 `src/` Rust 远控、文件传输协议，实现上与 macOS 共用远程控制、剪贴板和文件传输能力，不需要功能分支。
- Windows 正式产物由 Windows/MSVC 构建机生成（x64、ARM64）；Linux Flutter 产物由 Linux 构建机生成（x86_64、aarch64），可封装为 deb/rpm/AppImage。当前 Apple Silicon Mac 不具备 Windows Flutter/MSVC 或 Linux Flutter 打包运行环境，不能可靠地产出可交付的 Windows/Linux GUI 包。
- 工程已有 GitHub Actions 的对应矩阵；生成 Windows/Linux 成品需将本次源码同步到可执行 Actions 的仓库后手动触发构建，并下载其 artifacts。执行外部同步前必须重新取得用户明确确认。

## 二十四、2026-07-30 macOS 完整 Release 构建（1.4.21+79）

### 24.1 构建修复

- `libs/scrap/Cargo.toml`：将 `log` 放入通用依赖。macOS Quartz 采集路径会调用 `log::error!`，原先它仅在 Android target 依赖中声明，导致 macOS 编译缺少 crate。
- `src/server/video_service.rs`：`Capturer::new(display)` 会取得 `display` 所有权；错误上下文闭包此前仍读取该对象的显示器信息，触发 Rust 所有权编译错误。现在先缓存宽高、在线和主屏状态，再创建 Capturer，运行时行为不变。
- 本机构建依赖使用未纳入仓库的 `vcpkg/`（`libyuv`、`libvpx`）和当前用户的 CocoaPods 1.15.2；`vcpkg/` 已加入根目录忽略规则，避免把工具链混入业务提交。

### 24.2 完整包验证

- Rust：`VCPKG_ROOT=/Users/newlink/kemi/RustDesk/client/vcpkg cargo build --locked --features flutter --release` 成功。
- Flutter：`flutter build macos --release` 成功；缺失的 `macos/Runner/bridge_generated.h` 由项目既有 `flutter_rust_bridge_codegen` 命令生成。
- 产物：`flutter/build/macos/Build/Products/Release/KEMI-远程桌面.app`，大小约 60.4MB，包含 arm64 主程序、`liblibrustdesk.dylib` 和 `Contents/MacOS/service`。
- `Info.plist` 已核验 `CFBundleShortVersionString=1.4.21`、`CFBundleVersion=79`；`codesign --verify --deep --strict` 通过。
- 本地包为 ad-hoc 签名（`Identifier=com.carriez.rustdesk`、无 TeamIdentifier），仅用于本机测试；尚未替换 `/Applications` 内旧包，也不是可对外分发的 Developer ID 签名。

### 24.3 版本

- 本次构建继续使用 `1.4.21+79`，没有新增产品功能或 Android APK；`src/version.rs` 的构建日期已随本次 Release 构建更新。

## 二十三、2026-07-29 macOS 远程输入权限健康检查

### 23.1 根因与修复

- 实机日志确认旧 Mac 被控端存在 `screen_recording=false`，并连续报 `CGDisplayStreamCreateWithDispatchQueue returned null stream`；其隐私权限并未完整授予。
- macOS 的鼠标点击、滚轮和键盘注入依赖“辅助功能（Accessibility）”。此前接收远程鼠标/键盘事件的路径没有权限健康检查，未授权时系统会静默丢弃 CGEvent，PAD 端会误以为手势失效。
- 现在在首次远程鼠标或键盘输入时统一检查并请求屏幕录制、辅助功能、输入监控；系统提示每个进程最多触发一次，避免每个事件抢焦点。
- 仅“辅助功能”会阻止鼠标/键盘注入：授权后即允许单击和滚轮继续工作；屏幕录制与输入监控仍会在同一流程中申请并写入状态日志。

### 23.2 版本

- `Cargo.toml` 与 `Cargo.lock`：`1.4.21`。
- `flutter/pubspec.yaml`：`1.4.21+79`，Android `versionCode=79`；macOS 同步使用该 Flutter build name/build number。
- 对应关系维护在 `kemi-docs/README.md` 的“当前版本对应关系”。

## 二十二、2026-07-29 PAD 直接指针触摸兜底

### 22.1 触摸修复

- 单指点击：若 Flutter 手势竞技场未回调标准 Tap，在 120ms 后以原始触摸坐标补发一次左键点击；标准 Tap 已处理时按序号去重。
- 双指滚轮：直接跟踪两指中心点，纵向位移每累计 4px 向远端发送一个鼠标滚轮事件；双指纵向动作不再依赖 Scale 更新回调。
- 双指纵向滚动被识别后，缩放处理不再接管该次手势，避免滚轮和本地画布拖动重叠。

### 22.2 版本

- `Cargo.toml` 与 `Cargo.lock`：`1.4.20`。
- `flutter/pubspec.yaml`：`1.4.20+78`，Android `versionCode=78`。
- 版本与功能对应表维护在 `kemi-docs/README.md` 的“当前版本对应关系”。

## 二十一、2026-07-29 当前交互规则归档

- `README.md` 补充远端目录记忆与远控操作栏的导读。
- `SESSION-HANDOFF.md` 归并当前有效规则：44px × 48px 中文操作栏及整格点击反馈、唯一“输入”说明入口、单/双/三指约定、`remote_dir` 恢复优先级和 Android Download 例外。
- 本节仅整理文档，不修改版本或功能代码。

## 二十、2026-07-29 PAD 操作栏去重

- 移除与“输入”重复的“说明”入口；“输入”继续打开手势说明，其他操作项保留整格水波纹和高亮点击动画。
- `Cargo.toml` 与 `Cargo.lock` 升级为 `1.4.19`；`flutter/pubspec.yaml` 升级为 `1.4.19+77`，Android `versionCode` 为 `77`。

## 十九、2026-07-29 PAD 操作栏统一点击反馈

- 底栏每个“图标＋文字”操作项统一为整格 `InkWell`：点击时显示相同的水波纹与高亮动画。
- 反馈覆盖断开、显示、键盘、工具/输入、说明、聊天、更多和收起；禁用项不显示点击动画。
- `Cargo.toml` 与 `Cargo.lock` 升级为 `1.4.18`；`flutter/pubspec.yaml` 升级为 `1.4.18+76`，Android `versionCode` 为 `76`。

## 十八、2026-07-29 恢复操作栏与文件传输，修复单点状态

- 恢复操作栏中文标签、“说明”入口和远控页文件传输浮窗/连接令牌逻辑。
- 多指识别器仅在最后一根手指抬起时清空状态，避免双指操作后的独立单点继承旧状态。
- `Cargo.toml` 与 `Cargo.lock` 升级为 `1.4.17`；`flutter/pubspec.yaml` 升级为 `1.4.17+75`，Android `versionCode` 为 `75`。

## 十六、2026-07-29 PAD 手势说明翻译回退

- Android Flutter 打包使用预编译 Rust 桥接库，新增 Rust 翻译词条未必随 APK 即时重编。
- 手势说明对 `Two-Finger vertically` 增加 Flutter 中文回退：原生翻译未命中且系统语言为简体/繁体中文时，分别显示“双指上下滑动”/“雙指上下滑動”。
- `Cargo.toml` 与 `Cargo.lock` 升级为 `1.4.15`；`flutter/pubspec.yaml` 升级为 `1.4.15+73`，Android `versionCode` 为 `73`。

## 十五、2026-07-29 PAD 双指滚轮短滑动与中文说明修复

### 15.1 手势修复

- 双指手势首个更新帧也纳入纵向移动判断，避免短距离滑动只有一个事件帧时完全不触发滚轮。
- 双指累计纵向位移超过 2px 且明显大于横向位移时即锁定为远端鼠标滚轮；首帧仍只作为缩放基线，不会误触发缩放。
- 对照 2026-07-28 的 Git 基线，单指点击的事件发送路径未改动；保留 ADB 日志继续核查远端权限与手势竞技场状态。

### 15.2 文案与版本

- 为简体中文、繁体中文和英文词表补充 `Two-Finger vertically`，简体中文显示为“双指上下滑动”。
- `Cargo.toml` 与 `Cargo.lock` 升级为 `1.4.14`；`flutter/pubspec.yaml` 升级为 `1.4.14+72`，Android `versionCode` 为 `72`。

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
