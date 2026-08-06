# KEMI 本地未提交改动与风险复核

## 0. 当前发布快照：1.4.62+167（2026-08-06）

> 比较基线：GitHub `backup/master = af4cd82db0878e1533af6d1f0ac2c698970288fd`；目标为本文所在最终提交。推送完成后两端应完全同哈希、工作树应为空。

相对该云端基线，本地包含跨屏键盘HOME宿主复用、首页数字ID键盘、连接密码对屏输入和`1.4.60`自升级前可选备份；当前在已冻结的`1.4.61`基础上完成文件按钮状态同步与非模态跨屏窗口，并独立提升为`1.4.62+167`。文件分享、手动安装其他APK、传输策略、VP9、鼠标协议和桌面客户端逻辑均未改写。

| 模块 | 本地相对云端变化 | 风险与当前结论 |
|---|---|---|
| 文件传输 | 左右双栏、持久记录、清除陈旧选择、远端传入文件/目录安全删除、PAD目标50%空间门禁 | 高；只授权成功接收的本地目标。收到目录允许递归删除且包含后来放入的内容，删除后清除整棵授权。8项策略测试通过，仍需远端真实大目录回归 |
| 跨屏文件窗口 | 文件按钮开关和颜色随`open/hidden/closed`同步；原生窗口90%×78%且`NOT_TOUCH_MODAL`；关闭先释放文件FFI | 高；主屏窗口外已可操作，副屏双击和主屏关闭均完成状态闭环；后续不得改回全屏透明Activity或直接杀Activity跳过会话关闭 |
| Android更新 | 数值版本比较、自下载/哈希校验/PAD行显示本机版本；系统安装前可选把当前APK备份到公共下载目录 | 高；只有已验证的新包才询问。未知来源授权往返保留选择；选“是”备份失败则不升级，选“否”不写文件但仍升级。真实正向升级待云端发布后验收，任何失败不得删除已安装App |
| 本地APK安装 | 左侧PAD文件列表只对`.apk`增加安装入口；原生复核文件、APK结构和FileProvider路径后交系统安装器 | 中；右侧远端文件不显示，不能静默安装。双屏独立Activity已注册同一桥接，正式APK编译通过；仍需真机点选安装确认页 |
| VP9解码 | Android MediaCodec能力探测、真实厂商VP9硬解、异常自动回退软件 | 高；副屏已观察到厂商组件和正常画面，不兼容时必须保留libvpx回退 |
| 连接与资源 | 持久连接记录；PAD/Mac CPU、内存、编码器/解码器状态 | 中；统计不能冒充协议状态，断开记录和进程重启持久化已有真机证据 |
| 跨屏键盘/鼠标 | 代理Activity状态机、首页ID对屏数字模式、认证前不抢InputConnection、源窗口保持可聚焦、右键丢release自校准 | 高；HOME后保留非交互宿主并复用同一task，页面退出才完整release；后续不得重新加入源窗口`FLAG_NOT_FOCUSABLE` |
| 连接密码键盘 | 首页数字模式关闭后保留宿主；密码焦点以独立`local_password`伪session接收文字/退格；第一帧后才切远程模式 | 高；密码事件只修改本地TextEditingController，严禁提前发送远端；对话框关闭必须解绑回调，宿主模式交接不得重新创建Activity |
| 分享与安装 | 左侧普通文件可分享、APK还可安装；右侧文件和文件夹不增加操作 | 中；系统Chooser/Installer拥有最终确认权，FileProvider只授予单URI读取；正式构建通过，待真机验证分享目标过滤 |
| HOME重开 | 键盘和文件宿主HOME后均停驻复用；双屏设备一次性检查跨屏恢复权限 | 高；Android 12 HOME会触发全局app-switch限制，缺少`SYSTEM_ALERT_WINDOW`时Native必须拒绝并引导授权，不能伪报已打开；最终包键盘task 554与文件task 555重开已通过 |
| 构建发布 | 项目隔离Flutter、固定NDK、Android签名门禁、源码产品1.4.62/PAD build167、显示名KEMI远程办公 | 高；全局Flutter会污染`.dart_tool`，分析/pub/build必须使用同一项目工具链。包名和证书未变，桌面三个固定包保持原字节，正式PAD发布只更新PAD与两份清单 |

验证：1.4.59跨屏HOME闭环和用户确认的1.4.61密码对屏输入基线继续有效。本轮按固定NDK重编Android arm64核心，未解析`sodium_*`门禁通过；最终`1.4.62+167`固定签名Release为24,578,271字节、SHA-256 `edc393c3b498818ffecf2a585a7d537c16b9a427dc100668c1869acb2385e926`，包名`com.newlinksz.kemi.remote`、`minSdk=22`、v1/v2签名有效，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。真机副屏完成文件按钮开/关、主屏关闭回传、HOME隐藏同步和窗口外桌面点击四条回归，测试期间远控画面保持连接。遵照效率优先，本轮不重复运行无关全量回归；自升级备份“是/否”与Downloads真实落盘在云端存在更高版本后做正向升级验收。

发布状态：本轮形成PAD正式热修订混合快照。`BIN/release/KEMI-PAD.apk`更新为`1.4.62+167`，Mac/Windows/Linux三个固定包保持已验收的原字节；`SHA256SUMS.txt`和`release-manifest.json`随PAD重新生成并明确记录混合批次。管理员依次上传`KEMI-PAD`、`SHA256SUMS`、`release-manifest`后，再以低版本PAD完成远端发现、下载、备份选择和系统安装闭环。

## 历史候选快照：1.4.51+156（2026-08-04）

> 当前源码基线：本文所在提交；云端正式六文件仍为`1.4.49+154 / ed615c7fb / run 30891539907`。
>
> 当前PAD单端候选：`1.4.51+156`。以下第1节起的`1.4.48+129`内容是上一轮完整审计历史，保留用于追溯，不代表当前未提交文件。

本轮功能改动边界：

| 文件 | 改动 | 风险与结论 |
|---|---|---|
| `flutter/lib/mobile/pages/remote_page.dart` | 删除认证前跨屏代理prepare，统一在`pi.isSet`后创建 | 高风险输入链路；现场根因已由`inactive InputConnection`确认，主屏/副屏认证都必须复测，用户真实密码验收尚待完成 |
| `ClientPackageSync.kt` | Newlink清单三次重试；拒绝旧GitHub回退；全量刷新可抢占旧大文件下载 | 中风险并发与缓存；真机从1.4.46旧缓存恢复到Newlink 1.4.49，四资产解析成功；仍需后续观察断点续传和六文件新批次切换 |
| `ClientDistributionServer.kt` | 新增`/clients`直达页；二维码和复制地址统一带该路径；目标版本与缓存版本分开显示 | 低到中风险HTTP兼容；保留`/`旧入口，Mac真实请求`/clients`返回四平台下载项且无1.4.46 |
| `KeyboardProxyManager.kt` | 不再把跨屏键盘的远控源窗口设为`NOT_FOCUSABLE` | 高风险输入焦点修复；现场多次ANR均为源MainActivity没有焦点窗口，撤销后19次右键无新增ANR且键盘继续提交文本，用户确认相对稳定 |
| `PhysicalMouseRightButtonForwarder.kt` | 右键状态与下一鼠标事件的`buttonState`自校准 | 低风险兜底；补发up时不消费普通hover/move，显式release仍正常消费；特殊右键拖动仍需长期观察固件是否错误清空buttonState |
| 版本入口 | Cargo、Flutter、CI、PKGBUILD、RPM统一到`1.4.51`，PAD build 156 | Windows/Linux/Mac尚未生成1.4.51二进制，因此不得覆盖`BIN/release`正式六文件 |

当前构建与交付证据：

- Flutter定向analyze无error；Debug APK与固定签名arm64 Release均构建成功。
- APK：24,149,566字节；SHA-256 `3da2ff1a1ea985dfbea388430e680d2a069f413bd79de8f1055a5871d276d7c9`；包名`com.newlinksz.kemi.remote`；版本`1.4.51+156`；固定证书v1/v2校验通过。
- 真机`192.168.3.63:5555`已保留数据覆盖安装；清空旧日志后19次物理右键全部`down/up`成对、没有新增焦点ANR，键盘仍可提交文本，用户确认相对稳定。候选归档为`BIN/KEMI-远程桌面-PAD-1.4.51+156-release.apk`。
- 发布门禁：本轮只生成源码和PAD候选，不能把单个1.4.51 APK复制到`BIN/release`。PAD相对稳定不等于四端正式批次完成；获得Mac/Windows/Linux同版本候选后，才重新生成六文件正式快照。

> 快照日期：2026-08-03
>
> 源码候选与云端基线：`backup/master = eef2e0c0222c0701c3fea6137907d933c8da8921`
>
> 候选版本：`1.4.48+129`
>
> 用途：提交、四端构建和发布前的唯一改动清单。完成提交后保留本文作为该批次审计记录，不用“最新版”代替 commit 和哈希。

## 1. 本批次边界

本批次只包含以下六组行为：

1. PAD 首页显示真实 ID 服务器连接状态；
2. PAD 双屏远控断开时完整关闭 Rust peer、Flutter route 和 `RemoteActivity`；
3. Mac 设置页在远程输入时显示说明蒙层，本地物理鼠标可解除；
4. 修复 Android 本地 Rust 编译的 libsodium 工具链和 macOS libvpx 绑定缓存问题；
5. KEMI 四端固定禁用账户登录 UI，因为开源 `hbbs/hbbr` 不提供 Pro 账户 API；
6. 版本和文档统一到 `1.4.48+129` 候选批次。

不在本批次内：服务端协议修改、服务器密钥重生成、包名或签名迁移、文件传输 UI 尺寸调整、手势规则改写。

## 2. 逐文件改动清单

| 文件 | 改动目的 | 影响平台 | 风险与复核结论 |
|---|---|---|---|
| `Cargo.toml` | 将 `portable-pty` 限制到非 Android/iOS，避免移动端无用依赖进入 Rust 构建 | PAD，间接影响桌面依赖解析 | 低风险；桌面仍保留终端依赖，移动端功能不使用 PTY |
| `flutter/ndk_arm64.sh` | Android 构建强制使用 NDK `llvm-ar/llvm-ranlib`，并只构建 library | PAD | 中风险；需以完整 arm64 Rust 核心和 APK 构建验证，防止 libsodium 未解析符号 |
| `libs/scrap/build.rs` | 给 Cargo 重建指令补 `cargo:` 前缀，确保 libvpx 头文件变化后重新生成 ABI 绑定 | Mac、Windows、Linux | 中风险但必要；修复 `VPX_CODEC_ABI_MISMATCH`，需以 Mac 首帧和云端两端构建验证 |
| `libs/hbb_common/src/config.rs` | 默认服务器改为 `kemi-chat.newlinksz.com`，固定服务器公钥，并把产品服务器作为初始值 | 四端 | 高风险配置；域名和公钥必须逐字核对，云端 fresh clone 必须应用受控 patch |
| `src/common.rs` | 单服务器也执行连通性探测；移动端保留探测结果 | PAD/iOS | 中风险；只改变状态判断，不改变远控连接目标。绿色仅表示 ID 服务器可达，不代表视频首帧成功 |
| `src/flutter_ffi.rs` | Android 每 10 秒节流刷新服务器状态；移动端显式断开时移除整个 peer；四端固定禁用账户 UI | 四端 | 高风险；账户禁用符合当前后端能力。peer 级关闭只用于移动端显式断开，必须回归副屏断开重连 |
| `src/flutter.rs` | 新增按 session ID 找到并移除完整移动 peer | PAD/iOS | 高风险；解决多 Flutter engine 遗留 socket，不能扩展到桌面多窗口会话 |
| `flutter/lib/mobile/pages/home_page.dart` | 底部导航上方增加 24px 服务器状态条 | PAD | 低风险；必须检查五个首页 tab 不溢出、不遮挡原导航 |
| `src/lang/cn.rs`、`src/lang/en.rs` | 新增服务器状态和远程设置蒙层文案 | 四端 | 低风险；只新增 KEMI 使用键，不改模板和既有翻译 |
| `flutter/lib/common.dart` | 页面关闭前先关闭 native session；等待期间不再复用可能失效的 route context，直接通过根 Navigator 返回首 route；设置蒙层增加提示；鼠标进入、移动和按下时重新判断输入来源 | PAD、Mac/桌面 | 高风险；避免残留连接、断开后停在最后一帧和本地鼠标被永久蒙住。需分别验证远端鼠标仍被拦截、本地鼠标能解除 |
| `flutter/lib/desktop/pages/desktop_setting_page.dart` | 删除仅凭 `videoConnCount` 的永久黑遮罩，改为输入来源判断和透明提示 | Mac/Windows/Linux | 高风险；必须验证远程会话中远端不能修改、本机仍可操作，断线后不残留遮罩 |
| `flutter/lib/main.dart` | 副屏保留根 route；统一打开和有序关闭流程；防止重复 route；主屏转发虚拟键时保留 `down/up` 状态 | PAD | 高风险；必须验证主屏/副屏、取消连接、无首帧和正常断开四条路径；丢失按键状态会把按下、释放各发送一次完整按键，现已修正参数透传 |
| `flutter/lib/mobile/pages/remote_page.dart` | Rust peer 关闭后通知原生状态并结束副屏 Activity | PAD | 中风险；调用顺序不得反转，否则会再次出现 UI 已消失但 socket 仍存活 |
| `MainActivity.kt` | 主屏关闭副屏时不提前 reset；若副屏通道已经丢失则清理残留状态 | PAD | 中风险；正常路径由 `RemoteActivity.onDestroy` reset，异常路径有显式兜底 |
| `RemoteActivity.kt` | Activity 销毁时先通知断开，再 reset 全局状态 | PAD | 低风险；通知只影响主屏显示状态，不发送远控协议消息 |
| `flutter/pubspec.yaml` | build 从 125 提升到 129 | PAD、Mac 包版本 | 低风险；禁止把 build 125 的 Windows/Linux 旧产物写入 build 129 manifest |
| `src/version.rs` | 更新本批次构建日期，产品版本保持 `1.4.48` | 四端 | 低风险；Windows/Linux UI 仍显示产品版本 1.4.48，批次身份由 build、commit、run ID 和 SHA 共同确定 |

## 3. Review 中已处理的问题

### 3.1 账户按钮偶发出现

根因是 UI 依赖运行时 `disable-account` 硬配置。桌面首帧构建时该配置可能尚未加载，于是账户 tab/登录按钮先按“可用”渲染。`src/flutter_ffi.rs::is_disable_account()` 现固定返回 `true`，四个 Flutter 客户端行为一致，不再依赖本地状态或 21114 探测结果。

### 3.2 双屏异常关闭残留状态

正常路径必须先关闭 Rust peer，再结束 Flutter route，最后由 `RemoteActivity.onDestroy` 清理原生状态。Review 发现副屏 MethodChannel 已为空时主屏旧代码不会 reset；现已增加仅用于异常路径的清理兜底。

### 3.3 macOS Pod 锁文件噪声

本机 CocoaPods 1.15.2 生成了与云端 1.16.2 不同的插件 checksum，但插件版本没有变化。这不是功能改动，已从候选 diff 中剔除，避免无意义的依赖锁漂移。

### 3.4 Mac 运行中覆盖 App

2026-08-03 无画面的直接原因是磁盘 App 在 11:56 被覆盖，但 10:26 启动的旧进程仍在运行。旧进程出现 `CGDisplayStreamCreateWithDispatchQueue returned null`；完整退出并从新 App 启动后，PAD 连续两次连接均恢复首帧。以后安装必须执行“结束旧进程 → 替换 App → 校验签名 → 启动”，禁止运行中覆盖。

### 3.5 断开后残留最后一帧

真机首轮 `+129` 回归发现：确认断开后 native peer 和 `RemoteActivity` 已关闭，但异步回调仍用 `globalKey.currentContext` 重新查找 Navigator，双屏生命周期切换时该 context 可能已经不再对应当前 route 栈，主屏因此停在最后一帧。现改为直接使用 `globalKey.currentState` 并返回首 route；此问题必须在重新打包后完成“连接出首帧 → 确认断开 → 回主页 → 立即重连”闭环。

## 4. 发布前风险门禁

| 风险 | 必须完成的验证 | 当前状态 |
|---|---|---|
| KEMI 服务器/公钥在四端一致 | 二进制检索域名与公钥；运行时配置回读 | Mac/PAD 二进制已逐字检出；Windows/Linux由同commit focused workflow应用受控服务器patch并构建成功，真实系统运行时回读仍待跨平台验收 |
| Mac/PAD 登录 UI 消失 | 首页、设置页检查无账户 tab/登录按钮 | Mac `1.4.48 (129)` 首页实测只保留“主页/设置”；共用 FFI 固定禁用账户，待 Windows/Linux 包启动复核 |
| PAD 双屏断开不残留连接 | 连接、断开、立即重连；Mac 日志无旧 connection count | 待 build 129 真机验证 |
| Mac 设置蒙层不锁死本机 | 远端进入设置、本地鼠标解除；远端鼠标仍不可修改 | 待 build 129 双端验证 |
| PAD 状态条不误导 | 服务器断网显示离线；服务器在线显示就绪；文档注明绿色不等于视频已建立 | `192.168.43.11` 实测从橙色探测切到绿色“服务器已连接”；断网路径待测 |
| libvpx 绑定一致 | Mac 建立视频并持续出帧，无 ABI mismatch | Mac/PAD `+129` 实际连接已出首帧，无 ABI mismatch；持续会话仍需发布后抽测 |
| Android native 库完整 | `readelf`/APK 构建无 sodium 未解析符号，固定签名不变 | arm64 Rust Release 与 APK 构建通过；无 sodium 未解析符号；证书 SHA-256 仍为 `8546d03e…1871a2` |
| Windows/Linux 与同一源码对应 | focused run 绑定唯一 commit，下载 artifact 后核对 manifest/SHA | run `30795669077`成功；manifest绑定`eef2e0c02`，Windows SHA `ab1c63e…e24f`，Linux SHA `dfa36bd…cfc2` |
| release 六文件不混批 | 四端齐备后才重写 manifest/SHA，`release-manifest` 最后上传 | 四端同批次文件、SHA清单和manifest已整体生成；等待用户按顺序上传，manifest必须最后 |

## 5. 已执行的构建与静态检查

- `cargo check --locked --lib --features flutter`：通过；使用项目既有 `vcpkg/installed`，仅有上游既有警告。
- Flutter 定向 analyze：无 error；保留既有 deprecated/unused warning，未用批量修复扩大本批次。
- Android arm64 Rust Release、固定签名 APK：通过；包内版本 `1.4.48+129`、包名 `com.newlinksz.kemi.remote`、v1/v2 签名有效。
- macOS arm64 Rust Release、Flutter App、固定本地证书深度签名：通过；包内版本 `1.4.48 (129)`，主程序、`service`、Rust dylib 均为 arm64。
- Mac/PAD 实际连通：PAD 状态条变绿并成功取得 Mac 首帧；断开导航边界在 review 中进一步改为直接操作根 Navigator，最终包需再做断开重连抽测。
- GitHub Actions：focused run `30795669077`已成功完成bridge、Windows x64、Linux x86_64、AppImage和最终manifest；发布tag为`kemi-client-eef2e0c0222c0701c3fea6137907d933c8da8921`。

## 6. 提交与发布规则

- 代码 review 和本地预检通过后形成唯一候选 commit；Windows/Linux 必须从该 commit 构建。
- Mac/PAD 本地包也必须记录同一 commit。若本地构建发生在 commit 前，最终提交后必须确认源码树除文档/产物记录外没有改变，否则重建。
- `BIN/release` 固定只有六个名称；四端尚未齐备时不得先覆盖稳定 manifest。
- 旧 `1.4.48+125` 文件保留为历史归档，但不能进入 `1.4.48+129` 清单。
- 本文的“待验证”只有拿到对应平台证据后才能改成“通过”。
