# KEMI 本地未提交改动与风险复核

> 快照日期：2026-08-03
>
> 云端基线：`backup/master = 8b5debdaceccebcbf4d6913e155cec03b82d44c5`
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
| KEMI 服务器/公钥在四端一致 | 二进制检索域名与公钥；运行时配置回读 | Mac/PAD 二进制已逐字检出，Windows/Linux 待同 commit 云端产物 |
| Mac/PAD 登录 UI 消失 | 首页、设置页检查无账户 tab/登录按钮 | Mac `1.4.48 (129)` 首页实测只保留“主页/设置”；共用 FFI 固定禁用账户，待 Windows/Linux 包启动复核 |
| PAD 双屏断开不残留连接 | 连接、断开、立即重连；Mac 日志无旧 connection count | 待 build 129 真机验证 |
| Mac 设置蒙层不锁死本机 | 远端进入设置、本地鼠标解除；远端鼠标仍不可修改 | 待 build 129 双端验证 |
| PAD 状态条不误导 | 服务器断网显示离线；服务器在线显示就绪；文档注明绿色不等于视频已建立 | `192.168.43.11` 实测从橙色探测切到绿色“服务器已连接”；断网路径待测 |
| libvpx 绑定一致 | Mac 建立视频并持续出帧，无 ABI mismatch | Mac/PAD `+129` 实际连接已出首帧，无 ABI mismatch；持续会话仍需发布后抽测 |
| Android native 库完整 | `readelf`/APK 构建无 sodium 未解析符号，固定签名不变 | arm64 Rust Release 与 APK 构建通过；无 sodium 未解析符号；证书 SHA-256 仍为 `8546d03e…1871a2` |
| Windows/Linux 与同一源码对应 | focused run 绑定唯一 commit，下载 artifact 后核对 manifest/SHA | 待候选提交与云端构建 |
| release 六文件不混批 | 四端齐备后才重写 manifest/SHA，`release-manifest` 最后上传 | 待四端齐备 |

## 5. 已执行的构建与静态检查

- `cargo check --locked --lib --features flutter`：通过；使用项目既有 `vcpkg/installed`，仅有上游既有警告。
- Flutter 定向 analyze：无 error；保留既有 deprecated/unused warning，未用批量修复扩大本批次。
- Android arm64 Rust Release、固定签名 APK：通过；包内版本 `1.4.48+129`、包名 `com.newlinksz.kemi.remote`、v1/v2 签名有效。
- macOS arm64 Rust Release、Flutter App、固定本地证书深度签名：通过；包内版本 `1.4.48 (129)`，主程序、`service`、Rust dylib 均为 arm64。
- Mac/PAD 实际连通：PAD 状态条变绿并成功取得 Mac 首帧；断开导航边界在 review 中进一步改为直接操作根 Navigator，最终包需再做断开重连抽测。
- GitHub Actions 推送前检查：上一 focused run `30731531135` 已成功结束，当前没有进行中任务，不会因新提交取消旧构建。

## 6. 提交与发布规则

- 代码 review 和本地预检通过后形成唯一候选 commit；Windows/Linux 必须从该 commit 构建。
- Mac/PAD 本地包也必须记录同一 commit。若本地构建发生在 commit 前，最终提交后必须确认源码树除文档/产物记录外没有改变，否则重建。
- `BIN/release` 固定只有六个名称；四端尚未齐备时不得先覆盖稳定 manifest。
- 旧 `1.4.48+125` 文件保留为历史归档，但不能进入 `1.4.48+129` 清单。
- 本文的“待验证”只有拿到对应平台证据后才能改成“通过”。
