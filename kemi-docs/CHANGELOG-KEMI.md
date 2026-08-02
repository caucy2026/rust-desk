# KEMI-远程桌面 开发调试记录

> 基于 RustDesk 定制，日期 2026-07-26

## 六十八、2026-08-02 PAD服务器可见性与公司官网（PAD 1.4.48+125）

- 当前服务端是开源`hbbs/hbbr 1.1.16`，负责ID注册、发现、UDP打洞协商和中继，不包含RustDesk Pro账户/API服务；因此`21114`未监听属于部署能力边界，不是`hbbs`或`hbbr`漏启动。客户端实际远控链路仍以`21116`和`21117`为准。
- PAD“设置”首页的“ID/中继服务器”条目新增当前运行时地址摘要，用户不进入编辑弹窗即可看到`kemi-chat.newlinksz.com`；点击该条目仍可查看完整ID服务器、中继服务器、API服务器和公钥。
- PAD“关于”区域新增可选择复制的“ID服务器”行，值读取`custom-rendezvous-server`当前运行时配置；仅当配置为空时才回退显示KEMI产品默认服务器，避免用静态装饰文字掩盖实际配置。
- 关于区域版本号旁的网站由`rustdesk.com`改为`newlink-sz.com`，点击打开`https://www.newlink-sz.com/`。旧的备用`showAbout`弹窗同步修改；MAC关于页源码也同步展示当前ID服务器并把网站入口切到Newlink，待下一次MAC正常发布时随包交付。
- PAD版本升级为`1.4.48+125`，固定签名Release已覆盖安装到`192.168.3.46:5555`。现场截图确认设置首页、服务器详情弹窗和关于区域分别显示正确地址、公钥、`newlink-sz.com`与版本；APK SHA-256为`5f7eb1315ad411fe9d0d5382c64bd575281a874982b9fa056cbd88092120755f`，固定证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 已更新`BIN/KEMI-远程桌面-PAD-1.4.48+125-release.apk`、`BIN/release/KEMI-PAD.apk`、`release-manifest.json`及`SHA256SUMS.txt`；Windows、Linux和MAC稳定文件未改。

## 六十七、2026-08-02 自建服务器固定配置与 MAC/PAD 交付（1.4.48+124）

- KEMI 客户端统一使用 ID 服务器 `kemi-chat.newlinksz.com`（当前解析为 `119.96.24.110`）和服务器公钥 `gGsFBYJT34y1PIRgE+kBFOIH+MDkOadi4Or6tlwQ3jE=`；私钥仍只允许保存在服务器 `/var/lib/kemi-rustdesk-server/id_ed25519`，没有进入源码、BIN或文档。
- `flutter/lib/main.dart` 在全局 FFI 初始化后、桌面服务启动前执行统一配置。升级旧安装时先写 `key`，再写 `custom-rendezvous-server`；Android 随服务器项更新重启 rendezvous mediator，避免使用旧公钥连接新服务器。配置一致时直接返回，不在每次启动无意义重启。
- 完整 Rust 默认值也同步写入 `libs/hbb_common/src/config.rs`，供以后全量编译的 Windows、Linux、MAC、PAD 核心使用。本次 MAC/PAD 交付复用已验证 Rust 动态库，只重打 Flutter 外壳即可生效，不再把单纯服务器配置变更错误扩大成 vcpkg/AOM 全依赖重建。
- 网络验证：DNS、TCP `21115`～`21119`均可达；MAC `1.4.48+124`启动后配置文件精确回读地址与公钥，进程到 `119.96.24.110:21116` 为 `ESTABLISHED`，`21117`连通。PAD `1.4.48+124`启动日志实际请求 `kemi-chat.newlinksz.com`，证明运行时配置已生效；PAD无共享服务或远控会话时不会维持 `21116`长连接。
- 开源服务端未提供 `21114`账户/API服务，因此客户端的账户刷新/心跳会记录 `Connection refused`；这不影响 hbbs `21116`注册和 hbbr `21117`中继。后续应把账户功能与开源服务端能力解耦，避免无意义警告，但不得把该警告误判为远控服务器离线。
- PAD已覆盖安装到 `192.168.3.46:5555`，系统回读 `com.newlinksz.kemi.remote / 1.4.48 / 124`，固定签名证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。APK SHA-256为`44912ebcd37a083808cc4067b9a5db806f17f89ed995f8fb6801f405f8f41d60`。
- MAC App已安装到`/Applications/KEMI-远程桌面.app`并运行，版本`1.4.48 (124)`，固定本地证书签名深度校验通过。`BIN/release/KEMI-macOS.zip` SHA-256为`f57c25b0cf258123dd1a2930550337586e37a6507fde8e52f6b891a18bddbcc4`；ZIP重新解包后版本和深度签名再次通过。
- 固定交付文件已更新为`BIN/release/KEMI-PAD.apk`和`BIN/release/KEMI-macOS.zip`；版本化归档为`BIN/KEMI-远程桌面-PAD-1.4.48+124-release.apk`及`BIN/KEMI-远程桌面-macOS-arm64-1.4.48+124.zip`。`release-manifest.json`和`SHA256SUMS.txt`同步更新，四个平台校验均通过。

## 六十六、2026-08-02 全端真实版本显示与UDP打洞客户端默认开启（源码 1.4.48 / PAD 1.4.48+124）

- 全端源码版本统一提升到`1.4.48`，Android/PAD构建号为`+124`。`Cargo.toml`、`Cargo.lock`、`src/version.rs`、Flutter `pubspec.yaml`、GitHub工作流及Linux RPM/PKGBUILD入口保持一致；本节记录的是源码候选，未重新构建、签名和验收的安装包不得仅凭文件名宣称为`1.4.48`。
- Windows、macOS、Linux共用的主页“控制远程桌面”标题后增加实际运行包版本，使用较小、较淡字体，版本值来自`PlatformFFI.getVersion()`最终读取的平台包信息；PAD首页继续读取`PackageInfo.fromPlatform()`，并把版本号拆成11px辅助文字，避免与产品标题争夺视觉层级。
- UDP打洞默认值属于客户端行为，不增加服务端开关。旧客户端在自建 rendezvous 服务器下会把空的`enable-udp-punch`强制返回`N`，导致新装客户端即使没有关闭过该选项也不发起UDP打洞。`1.4.48`移除这条例外：空值按既有`enable-*`规则解释为开启，用户明确保存的`N`仍保持关闭，IPv6打洞的旧默认策略不变。
- 服务端只需保证`hbbs`正常提供UDP协调并开放`21116/UDP`；服务端没有“替客户端打开UDP打洞”的配置。本次不修改`hbbs`、`hbbr`、systemd或服务器部署文件。
- Flutter定向分析完成，只有项目既有/兼容旧Flutter基线而保留的弃用提示，没有新增语法或类型错误；PAD Debug APK完整构建成功，包内回读为`com.newlinksz.kemi.remote / versionName 1.4.48 / versionCode 124`，SHA-256为`fad74126d62f8a242d99285911e58580312109b13aaa4e7e631e67517e14fc87`。`rustfmt --edition 2021 --check src/common.rs`通过；根Rust `cargo check --lib`因本机缺少`cmake`停止于可选音频依赖`libsamplerate-sys`，关闭默认音频特性后又因本机缺少Homebrew `libyuv`停止于`scrap`构建脚本，均未进入本次逻辑的编译报错。全仓`cargo fmt --check`还会命中仓库既有格式差异，未批量格式化无关文件。

## 六十五、2026-08-01 PAD物理鼠标右键原生兼容（PAD 1.4.46+123）

- 远控协议和被控端原本已支持`right`按下/抬起；问题位于Android输入入口。部分PAD会把物理鼠标右键上报为原生`ACTION_BUTTON_PRESS/RELEASE + BUTTON_SECONDARY`，或转换成鼠标来源的`KEYCODE_BACK`，未必生成Flutter现有监听依赖的`PointerDownEvent(buttons=2)`，因此切换触摸/鼠标输入模式都不能恢复。
- 新增Android原生右键兼容层，同时覆盖单屏`MainActivity`和副屏`RemoteActivity`。远控页进入时才激活当前Activity的拦截，退出立即关闭并补发必要的右键释放；PAD主页、设置、另一块物理屏幕及其他应用仍保留Android原行为。
- 原生层只识别鼠标来源的次键，分别转发`down/up`，支持右键菜单和右键拖动；Flutter输入模型同步维护`kSecondaryMouseButton`状态，避免后续移动事件再次生成重复按下。右键释放使用已记录的同一远控会话，即使期间权限状态变化也不会留下远端按键卡住。
- 每次原生右键转发记录`[PhysicalMouse] right down/up on display N`，便于真机失败时通过`adb logcat`区分PAD没有上报、原生已捕获或远端执行失败。用户已在`+123`真机确认物理鼠标右键能够传到远端，功能闭环通过；完整输入链路和回归清单见`kemi-docs/android-physical-mouse.md`。
- 构建号升级为`1.4.46+123`。Release APK为26,534,833字节，SHA-256 `51d23973073c84708ee28e958e736f9dd92ef895212b91cb91e4fc915540d2f6`；包名`com.newlinksz.kemi.remote`、仅`arm64-v8a`，zipalign通过、v1/v2签名有效，固定证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。已保存到`BIN/KEMI-远程桌面-PAD-1.4.46+123-release.apk`并覆盖`BIN/release/KEMI-PAD.apk`，六文件SHA256清单一致。

## 六十四、2026-08-01 PAD 文件传输记录与再次传输（PAD 1.4.46+122）

- 文件传输页新增“记录”入口。记录按“传输方向 + 源目录 + 目标目录”归组，同一组内按完整源路径去重；重复发送同一路径只更新次数、时间和状态，不新增重复行。每项提供明确的“再次传输”和删除记录按钮。
- 用户切到记录页后不清楚如何返回的问题，改为右上角箭头加“返回文件”，记录列表顶部固定提示“点击右上角‘返回文件’继续浏览和传输”；空记录页也保留同样引导，系统返回键优先退回文件页。
- 完成传输后没有记录的根因是旧方案把“创建历史记录”依赖在异步任务创建/完成回调上。小文件可能在UI绑定任务前就完成，回调找不到记录项，最终持久化仍是空数组。新流程在调用`sendFiles`前逐项`registerTransfer`并同步落盘，随后只用任务ID绑定完成/失败状态；因此无论文件多小，发送动作一旦确认就已有可追踪记录。
- “再次传输”不复用旧文件元数据直接盲发：先实时读取目标目录，再读取源目录并按完整路径寻找源项。目标目录无效或源文件不存在时不启动任务，在该记录行原位显示明确错误；校验期间按钮显示转圈且禁止重复点击。
- 状态持久化在对应peer的Flutter option `kemi-transfer-history-v1`。`+122`取消原先50组/每组100项的自动淘汰：用户不主动删除记录、不清除App数据且不卸载App时，关闭页面、结束进程和PAD重新开机后都继续保留；上次退出时仍为“传输中”的项，重进时标记为“上次传输未完成，可点击再次传输”。
- 真机`192.168.3.46:5555`实测：从PAD `/storage/emulated/0/Download`向本机`/Users/newlink`复制8,483,380字节文件，日志先出现`[TransferHistory] registered`，文件SHA-256为`ecd611bfe949a643d44a16e4fece9400894a6f809843e34cf3ccfb371275ec24`；记录页立即显示“PAD → 对方 / 已完成 / 已传输1次 / 再次传输”。强制停止并重新打开KEMI后记录仍存在，同时“返回文件”和顶部引导均可见。测试生成的Mac副本和PAD临时测试文件均已清理，PAD原始源文件未改。
- `+122` Release APK为26,533,162字节，SHA-256 `5c4affe9f0ded75999593d79bea7fec76af5ce8cfba815a9f8c5a89116f13fa5`；`versionName=1.4.46 / versionCode=122`，包名`com.newlinksz.kemi.remote`、仅`arm64-v8a`，zipalign通过、v1/v2签名有效，固定证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。已覆盖安装到`192.168.3.46:5555`且系统回读版本正确；整机重启命令已执行，但设备开机后尚未恢复Wi-Fi/无线ADB，开机后记录页回读仍待设备重新上线完成。

## 六十三、2026-08-01 PAD 副屏键盘偶发跑到副屏（PAD 1.4.46+120）

- 现场现象不是 Display 检测随机错误。真机日志在每次点击时都正确计算出 `source=2 / target=0`，但旧实现为了恢复已停放代理的焦点，用 `PendingIntent` 对 `singleInstance` 的 `KeyboardProxyActivity` 执行 `NEW_TASK | REORDER_TO_FRONT | SINGLE_TOP` 自启动。该厂商 Android 12 ROM 会把这次“从副屏发起的重新排序”解释为任务迁移，把原本位于主屏的代理 task 重新挂到 Display 2；Manager 仍保存 target=0，于是状态记录与真实窗口位置分离，键盘表现为有时在主屏、有时在副屏。
- 修复不再重新启动代理 Activity。复用路径只调用 `ActivityManager.moveTaskToFront(taskId, MOVE_TASK_NO_USER_ACTION)`，把已经存在的任务置前，避免生成新的 Activity launch/reparent 请求。代理停放、IME 真实隐藏门禁、HOME 销毁重建和 Android 12 跨屏启动许可逻辑保持不变。
- 在每次 `showSoftInput()` 前增加最后一道真实屏幕校验：`activity.displayId` 必须等于创建时记录的 `expectedDisplayId`。如 ROM 或后续代码仍导致任务跑屏，立即记录 `display_mismatch`、释放代理且不显示键盘，禁止错误屏幕上的偶发弹出。
- `192.168.3.46:5555` 覆盖安装后，系统回读 `versionName=1.4.46 / versionCode=120`，且从设备回读的 `base.apk` 与 `BIN/release/KEMI-PAD.apk` SHA-256完全一致。副屏 Display 2 发起的 request 2～16 连续 15 次均记录 `Moved keyboard task ... display=0 expected=0`，每轮都到达 `visible`；其间覆盖输入法收起后复用同一 task，并在 request 5、12、14 后三次覆盖 HOME 销毁和主屏新建 task，全部保持在 Display 0，未出现 `Refuse IME` 或 `display_mismatch`。
- Release APK 为 26,517,870 字节，SHA-256 `1279ba88bfa54f482246b17da48e1e8decdd7a7b9f117772a6ce24f4bec030e6`；包名 `com.newlinksz.kemi.remote`、仅 arm64-v8a，zipalign通过，v1/v2签名有效，固定证书SHA-256仍为 `8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。

## 六十二、2026-08-01 Windows默认主页/设置与自安装来源（Windows 1.4.46候选）

- Windows只显示“主页”、点击密码编辑后才出现“设置”的根因位于`DesktopTabPage`构造流程：主页始终创建，但此前KEMI新增的设置预创建条件写死为`isMacOS`。密码编辑只是间接调用同一个`onAddSetting()`，不是设置页真实初始化依赖。
- 最小修复将条件扩大为`isMacOS || isWindows`；Windows普通完整客户端首次启动直接得到“主页/设置”，并跳回主页作为默认选中项。`incoming-only`和`disable-settings`限制保持原样，Linux及其他逻辑未改。
- 本地Flutter静态分析未发现本次新增语法或类型问题；只保留该文件既有的`ColorScheme.background`弃用提示。修复以独立commit `0594554b47b6d9b6ee61bc6ee96d6457abe2153d`推送到`caucy2026/rust-desk`，由`KEMI Focused Client Artifacts` run `30688275054`构建Windows候选，避免混入本地尚未提交的PAD改动。
- 当前Windows下载物是KEMI源码构建的单文件便携包。`generate.py`把本次构建的完整Windows目录压入EXE；运行时本地解包，点击“安装”后`install_me()`复制当前解包程序并创建服务、快捷方式及卸载项，不请求RustDesk官网下载另一个客户端。云端产物完成后仍必须在真实Windows上覆盖“便携首次启动→默认主页/设置→点击安装→安装版再次默认主页/设置”的闭环，未完成前不替换`BIN/release/KEMI-Windows.exe`。

## 六十一、2026-08-01 HTTP页本地/HTTPS双路径与真实Wi-Fi（PAD 1.4.46+119）

- 局域网HTTP页顶部增加独立Wi-Fi网络卡，读取并显示PAD当前真实SSID；本次真机显示`KEMI-T1`。若系统没有授予Wi-Fi名称权限，不猜测、不展示伪名称，而是明确提示回PAD客户端页授权。
- 每个平台下载卡现在同时提供“从PAD下载”和“HTTPS云端下载”。前者只发送PAD已完成大小、SHA-256和MD5门禁的本地缓存；后者直接使用本次固定Newlink接口解析出的实际CDN地址，适合不想等待PAD中转或希望自行在浏览器下载的用户。
- 实际HTTPS地址显示在浅色地址框，并提供“复制地址”。复制逻辑兼容局域网HTTP页面的非安全上下文：优先使用Clipboard API，不可用时回退临时textarea与`execCommand('copy')`。页面不接受用户输入URL，也不生成开放代理；只有`https`且主机精确为`cdn.newlink-sz.com`的解析结果才展示云端入口。
- 页面文案明确两种方式的取舍：同一Wi-Fi优先“从PAD下载”以获得已校验副本；“HTTPS云端下载”可直接下载或复制真实地址到其他浏览器。Windows继续作为推荐项，macOS/Linux/Android维持响应式双列，窄屏自动单列。
- 真机`192.168.3.46:5555`安装后，系统回读`versionName=1.4.46`、`versionCode=119`，固定签名证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。Mac以Chrome实际渲染`http://192.168.3.46:8686/`，确认真实SSID、四组双下载按钮、四个CDN地址和复制按钮均存在。
- Mac分别从`http://192.168.3.46:8686/download/KEMI-macOS.zip`和页面解析出的`https://cdn.newlink-sz.com/.../KEMI-macOS.zip`下载，二者均为25,874,761字节，SHA-256均为`a69a61fefa8ddc8c1d5c9c3124ed50d393cca22da2e55bad2c50e3d2f167b03d`且逐字节一致。PAD Release为26,517,708字节，SHA-256为`22294e0572c546b151b0c7a975e4ab13c9b529ea9a8272a7c9dae3660d13cdcc`，zipalign及v1/v2签名校验通过，APK未内置四端客户端大文件。
- 当前云端PAD仍是`1.4.46+117`，因此HTTP页如实显示云端稳定清单版本，不用`+119`文件名包装旧字节。发布本次PAD需最后上传`KEMI-PAD.apk`、`release-manifest.json`与`SHA256SUMS.txt`三个固定名文件。
- `client-distribution.md`补齐Newlink云端人工交接：管理入口固定为`https://www.newlinksz.cn/screensaver/main/configPlug/Common`，账号密码向公司后台负责人申请且不进入仓库；用户只负责在`Common`项目按顺序覆盖六个固定项，开发流程负责构建、生成六文件、回读六个固定查询地址、PAD同步和跨设备闭环。四端二进制先上传，`SHA256SUMS`倒数第二，`release-manifest`最后作为发布完成标志。

## 六十、2026-08-01 Newlink HTTPS实时地址与PAD本地分发闭环（PAD 1.4.46+118）

- `ClientPackageSync`将Newlink固定接口设为主源：先解析`release-manifest`与`SHA256SUMS`动态URL，逐项核对两份清单，再通过`KEMI-PAD/KEMI-Windows/KEMI-macOS/KEMI-Linux`固定接口取得当前CDN URL和MD5。仅允许`www.newlinksz.cn`元数据和`cdn.newlink-sz.com`资产的HTTPS链路；动态CDN URL不硬编码。
- 每次进入“客户端”页立即重新解析六个固定接口，页面保持打开时每5分钟再刷新。任一云盘元数据异常时自动回退raw GitHub/jsDelivr清单；两类源都失败时不删除上次校验成功缓存。
- 固定云盘文件名不再直接作为PAD缓存路径；本地使用`SHA-256前16位 + 固定文件名`隔离新旧版本。下载先写`.part`，大小、清单SHA-256、接口MD5全部通过后才原子替换并生成`.sha256`侧车；HTTP服务只暴露通过门禁的文件。
- PAD页面和局域网网页都显示当前上游为“Newlink HTTPS实时地址”，但客户实际下载仍由PAD本地HTTP提供，便于PAD在出错时立即显示进度/校验/失败状态。离开客户端页`8686`立即关闭，重新进入重新解析实时地址。
- 真机`192.168.3.46:5555`安装`1.4.46+118`后，日志在1秒内解析出四端`cdn.newlink-sz.com`地址，随后完成四端约151MiB的HTTPS下载和双哈希校验。当前Mac从`http://192.168.3.46:8686/download/KEMI-macOS.zip`下载25,874,761字节，SHA-256为`a69a61fefa8ddc8c1d5c9c3124ed50d393cca22da2e55bad2c50e3d2f167b03d`，与`BIN/release`完全一致；解包回读`1.4.46+110`。离页后Mac连接`8686`失败，重进后HTTP恢复且日志再次记录四个实时URL，完整闭环通过。
- PAD Release为26,515,706字节，SHA-256 `e8e83d3fb2096f74c290485d65cb330a1ef327fcdb72d82dac5b596bb98f2d3f`；`versionName=1.4.46`、`versionCode=118`、仅arm64-v8a，zipalign通过，v1/v2签名有效，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。云端当前仍为PAD `+117`；必须重新上传`KEMI-PAD.apk`、`release-manifest.json`和`SHA256SUMS.txt`后才让新安装包成为云端稳定版。

## 五十九、2026-08-01 云盘固定文件名发布区（产品版本不变）

- 新增根目录`BIN/release/`作为唯一云盘上传区。四端名称永久固定为`KEMI-PAD.apk`、`KEMI-macOS.zip`、`KEMI-Windows.exe`、`KEMI-Linux.AppImage`；后续升级只覆盖对应内容，不添加版本号、不改大小写、不换名。`release-manifest.json`与`SHA256SUMS.txt`同样使用固定名称。
- `BIN/`根目录仍保留所有带版本号的不可变历史归档，本轮不删除、不重命名旧包。固定云盘名不承担版本判断，必须通过清单中的平台、架构、包内版本、字节数和SHA-256验证。
- 当前待上传内容为：PAD `1.4.46+117`（26,505,768字节，SHA-256 `87622e4b00c20139b5576fd27dde99937e40ed10e2efad505626274b49c52661`）；macOS arm64 `1.4.46+110`（25,874,761字节，`a69a61fefa8ddc8c1d5c9c3124ed50d393cca22da2e55bad2c50e3d2f167b03d`）；Windows x64 `1.4.46`（22,637,056字节，`9cc13f6780a39388d590b2f7dc575b1e42712da630f7ae801947d4465867d6ad`）；Linux x86_64 `1.4.46`（82,983,416字节，`2276a6860c482b21f6ab4d9bb2502c5dadd69d2a140ef038506c638eebe5fa44`）。
- 云盘直链尚未提供，因此本轮只准备文件与本地校验清单，不提前改动PAD下载代码。收到地址后必须验证为无登录、不过期的HTTPS文件直链，回读四个文件的大小/SHA-256一致后才写入代码，GitHub源在新链路验收前保持不变。

## 五十八、2026-08-01 PAD 副屏键盘强制收起后无法再次打开（PAD 1.4.46+112 → +117）

- 真机日志确认根因：键盘被系统返回键/输入法收起按钮强制关闭后，`KeyboardProxyActivity`会被销毁；副屏再次点击键盘时，Android 12把从`displayId=2`新建主屏`displayId=0`代理窗口判定为后台跨屏启动，并明确输出`Abort background activity starts`。Flutter按钮已经进入`opening`，但原生Activity根本没有创建，因此等待超时后仍无法弹出；这不是远程会话、输入权限或键盘按钮坐标问题。
- 补全原先只有返回值、没有任何准备动作的`keyboard_proxy_prepare`：远程页面进入前台时提前在对面屏幕建立键盘代理容器；单屏设备仍在用户点击时使用当前屏幕，不额外创建容器。
- 用户主动关闭或系统强制收起输入法后，状态机仍完整发送`visible → closing → hidden`，但不再销毁跨屏代理Activity；代理窗口改为透明、不可触摸、不可聚焦的停放状态，不遮挡本地屏幕。再次点击时清除停放标记、恢复焦点并直接复用同一Activity，不再触发Android的跨屏启动限制。
- 退出远程页面、App退到后台、目标显示器断开或Activity被系统销毁时仍执行真正释放，避免无会话时长期保留代理窗口。Android构建号由`1.4.46+111`升级为`1.4.46+112`，用于区分本轮键盘生命周期修复。
- 真机`192.168.3.46:5555`（Android 12）覆盖安装后，从副屏进入已保存Mac会话时成功在主屏预创建代理。首次打开及三轮“主屏返回键强制收起→副屏再次点击键盘”均通过：请求号`2→3→4→5`每轮完整到达`visible → closing(user_hidden) → hidden → visible`，系统始终复用同一`ActivityRecord b74a269 / task 1453`，未再次出现`Abort background activity starts`；最终输入法回读`displayId=0 / mInputShown=true`。
- 用户随后按主屏输入法真实收起控件测试，`+112`仍复现。未清日志现场确认第二根因：系统先回调`visible=false`完成停放，随后又为旧窗口延迟回调`visible=true/bottom=741`；下一次请求更新了`requestId`后立即读取到这份旧Insets，Manager在没有真正调用成功`showSoftInput()`的情况下错误发布`visible`，所以按钮看似切换而键盘没有出现。
- `+115`为每次激活增加`imeShowAccepted=false`门禁：在本次`showSoftInput()`返回接受之前，所有`visible=true`一律记录为旧状态并忽略，也不能取消IME重试；接受后重新请求Insets，只有真实可见才发布`visible`。复用代理的焦点恢复改由副屏按钮这次明确用户操作触发的`PendingIntent`完成；Android 12不允许主屏已主动回到桌面后的后台拉起，该路径会保持真实`opening`并超时回到`hidden`，不再伪报已显示。
- 真机按截图确认的主屏左下角输入法收起按钮坐标完成最终验收：`request=2`首次显示，收起后完整到达`closing(user_hidden) → hidden`；副屏再次点击产生`request=3`，`showSoftInput accepted=true`后才进入`visible`。系统最终回读`displayId=0 / mInputShown=true`，代理始终为同一`task=1461`。
- 用户进一步确认主屏HOME是明确的“关闭”操作，不应继续保留后台代理。`+117`在`KeyboardProxyActivity.onUserLeaveHint()`识别HOME，立即发布`closing(home_pressed)`、隐藏IME、`finish()`代理并完成`hidden(home_pressed)`资源清理；副屏下次点键盘时一定新建主屏Activity，不复用已被HOME退到后台的旧任务。输入法自身的收起按钮仍按`user_hidden`停放复用，两种用户动作不再混淆。
- Android 12默认会拒绝副屏App在主屏HOME后再跨屏启动Activity；因此该设备已按用户明确授权将`SYSTEM_ALERT_WINDOW`设为`allow`。此权限在本功能中只用作Android的后台Activity启动例外，代码不创建悬浮窗或悬浮图标。普通新设备需由用户授予一次“允许显示在其他应用上层”，受管PAD可由MDM/预装策略授予；不得在未授权设备上伪报键盘已打开。
- 真机`192.168.3.46:5555`在`+117`上连续多轮验证通过：HOME时旧`task 1465/1466/1467`依次到达`closing(home_pressed) → hidden(home_pressed)`并移除；副屏再点键盘后新建`task 1466/1467/1468`，系统日志明确记录`allowed because SYSTEM_ALERT_WINDOW permission is granted`，随后`showSoftInput accepted=true → visible`。最终回读`displayId=0 / mInputShown=true`。
- `+113/+114/+116`仅为现场诊断中间包，未归档为交付版本；其中默认主屏不支持`Presentation`并返回`InvalidDisplayException`，该实验代码已完全移除。最终Release为26,505,768字节，SHA-256 `87622e4b00c20139b5576fd27dde99937e40ed10e2efad505626274b49c52661`；包名`com.newlinksz.kemi.remote`、`versionName=1.4.46`、`versionCode=117`、仅`arm64-v8a`，zipalign通过，v1/v2签名有效，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`，APK未内置`assets/client-dist`。

## 五十七、2026-08-01 PAD 原始尺寸与适应窗口修复（PAD 1.4.46+111）

- 根因不是`+106 → +110`的显示代码回归：`toolbarViewStyle`从KEMI `1.4.9+67`到`1.4.46+110`字节一致，`+106 → +110`的显示菜单、画布模型和远控页面也没有源码差异。当前Mac输出`1920×1080`，PAD副屏为`1920×1280`、App可用区约`1920×1192`，原始尺寸与适应窗口在水平方向都得到1920px，因此旧问题主要表现为垂直偏移。
- `CanvasModel.getSize()`此前按整个系统窗口1280px高计算，而远控画布实际位于44px底栏和安全区上方。原始`1920×1080`画面因此按`(1280-1080)/2=100px`定位，而不是在约1192px真实画布中按`(1192-1080)/2≈56px`居中，造成原始尺寸垂直方向的视觉排布不符合点对点预期；本问题不涉及当前已正确工作的鼠标点击映射。
- 移动端远控画布现在由`LayoutBuilder`把当前实际布局尺寸写入`CanvasModel`；原始尺寸保持远端1个物理像素对应PAD 1个物理像素，并在实际画布内居中。底栏收起/展开、安全区或双屏布局变化会触发重新计算；适应窗口也使用同一真实画布边界。
- 重新点击当前已选显示模式会强制清除手势产生的平移/缩放状态并重新计算，不再因`ViewStyle`字段相同提前返回。显示模式保存改为等待完成后再刷新画布，避免异步时序导致读取旧值。
- Android构建号升级为`1.4.46+111`。Debug与Release构建均成功；Release为26,503,841字节，SHA-256 `1ec129978652cf2c72c641d7422484af1a59d7c6714601026dd3a60803a5785d`，包名`com.newlinksz.kemi.remote`、`versionName=1.4.46`、`versionCode=111`，v1/v2签名有效，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`，且未误内置`assets/client-dist`。
- `192.168.3.46:5555`已使用`install -r`保留配置覆盖安装并在副屏启动，系统回读`1.4.46(111)`且进程正常。App自动恢复当前Mac会话后，真机截图确认远端`1920×1080`画面从错误的约100px顶部偏移改为在底栏上方`1920×1192`画布内上下各约56px；显示菜单回读“原始尺寸”已选中，点对点画面和现有鼠标点击映射均保持正常。

## 五十六、2026-07-31 四端制品仓与PAD空闲同步（PAD 1.4.46+107 → +110）

- 四端制品交付基准统一为项目根目录`BIN/`；每批必须包含PAD APK、macOS arm64 ZIP、Windows x64 EXE、Linux x86_64 AppImage、版本清单和SHA256SUMS。远端统一使用`caucy2026/common-data`：普通Git只保存稳定清单和说明，大文件使用不可变GitHub Release，先传完并回读校验四端资产，最后更新`stable/manifest.json`。
- PAD不再把macOS/Windows/Linux约125MiB静态包编入APK。新增`ClientPackageSync.kt`，从固定GitHub清单读取四端真实版本、大小和SHA-256；仅接受白名单平台、文件名和HTTPS GitHub下载域名。下载写`.part`并支持Range续传，长度和SHA-256全部通过后才原子替换正式缓存；失败不覆盖最后成功文件。
- 新增`ClientPackageSyncJobService.kt`：开机后和App启动时登记JobScheduler任务，仅在非计费网络及系统空闲时执行，周期为6小时；该任务与远控服务的“开机自启”选项相互独立。用户明确点击缺失客户端时立即下载，不等待空闲窗口。
- 客户端页增加1秒状态刷新、行内下载进度和点击弹窗。缺包点击后显示圆形动画、百分比、SHA校验阶段、错误原因和重试按钮；用户可关闭弹窗让原生任务继续。HTTP服务只提供`ready`文件，永不暴露`.part`；离开页面仍只关闭LAN服务，不清缓存和后台同步。
- Android远端版本与当前安装`versionName+versionCode`一致且字节校验通过时继续直接服务`applicationInfo.sourceDir`，不额外缓存自身；远端PAD更新时才缓存新版APK供局域网下载，因此没有APK递归嵌套。
- `1.4.46+107`首次真机强制任务成功读取raw稳定清单，但四端均未生成`.part`；PAD和本机进一步验证`raw.githubusercontent.com`、`api.github.com`可达，而`github.com/releases/...`HTTPS首跳20秒超时。Release资产本身大小正确，根因是下载入口不适合当前网络，不是文件或JobScheduler错误。修正版`+108`将清单URL改为GitHub官方Release Assets API，客户端显式发送二进制Accept/API版本头并允许`api.github.com`；API再跳转到可达的`release-assets.githubusercontent.com`。同时`ensurePackage`失败现在记录平台、版本和完整异常，避免后台只显示error却没有log。
- `+108`真机证明Assets API可达，但其Accept同时列出`application/octet-stream,application/json`，GitHub返回约1.6KiB资产元数据JSON，长度门禁正确拒绝三个文件。`+109`对`api.github.com`只发送精确二进制Accept，遇到JSON Content-Type立即拒绝，并在续传前识别和清理旧版误存的JSON `.part`，避免错误临时内容参与Range续传。
- 按`/Users/newlink/kemi/kemi-rd/md/github-cloud-resource-guide.md`补齐双源策略：小型stable manifest先读raw GitHub（4秒连接超时），失败自动读`jsDelivr @main`并使用分钟级cache-bust（8秒连接超时），两者失败继续使用磁盘最后成功清单。四端二进制中Linux约79MiB，超过指南建议的jsDelivr 50MiB范围，因此大文件仍使用GitHub Release Assets API，不错误套用小数据CDN方案。
- PAD列表下载状态由右侧“下载中”文字改为42px圆形进度环，环内显示当前整数百分比，SHA校验阶段显示“校验”；移除重复的行内横向进度条。局域网HTTP网页不再使用临时简版CSS，按`client-download-preview.html`还原960px双面板、渐变hero、绿色服务徽标、深色地址栏、Windows推荐横跨卡片、双列下载卡片、平台符号图标、黄色排障提示和移动端单列布局，动态填入真实Wi-Fi、地址、版本和可用状态。
- `client-distribution.md`已重写为上述流程的唯一维护文档，详细定义版本号、BIN文件名、common-data Release/tag/manifest、云构建未完成时的隔离规则、安全边界、失败回退和逐项验收。
- 交付源码为`1618ab449e5791b5280528623c6cddffcbec7fd4`。PAD Release为26,502,744字节，SHA-256 `561623f9cddf41a1fd82a8818c7c046b51a33e365a53bffec3e7342478aa0c53`；包名`com.newlinksz.kemi.remote`、`versionName=1.4.46`、`versionCode=110`、固定签名证书SHA-256 `8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`均正确，APK中没有`assets/client-dist`。
- macOS arm64 App与ZIP已写入`BIN/`，Info.plist为`1.4.46+110`，复制同版本`service`后ad-hoc重签并通过`codesign --verify --deep --strict`；ZIP为25,874,761字节，SHA-256 `a69a61fefa8ddc8c1d5c9c3124ed50d393cca22da2e55bad2c50e3d2f167b03d`。本机仍为`0 valid identities`，因此该包明确只供内部测试，未冒充Developer ID签名或Apple公证包。Windows与Linux继续复用已验收的1.4.46候选，SHA-256分别为`9cc13f6780a39388d590b2f7dc575b1e42712da630f7ae801947d4465867d6ad`和`2276a6860c482b21f6ab4d9bb2502c5dadd69d2a140ef038506c638eebe5fa44`。
- 四端已发布为`common-data`不可变Release tag `kemi-rustdesk-v1.4.46-build110`，Actions run `30639274659`一次成功；稳定清单提交为`7460ba5`，四个URL均使用实际Assets API ID。raw GitHub、jsDelivr与PAD磁盘清单三处回读均为`1.4.46+110 / 1618ab449`。
- 真机`192.168.1.142:5555`已卸载109并全新安装110，系统回读版本正确。授予Wi-Fi名称权限后页面真实显示`caucyniu2025-5G`；Windows下载从0开始并在列表右侧实测显示圆形进度环和环内百分比（截取2%、无障碍层回读20%），证明动画随原生下载进度刷新。局域网`http://192.168.1.142:8686/`已用Chrome无界面截图回读，确认DEMO的双面板、推荐卡、四个平台符号图标、地址复制区与排障提示均生效；未完成文件保持不可下载状态，Android当前包可下载。

## 五十五、2026-07-31 Mac同事本地构建说明与华为规范PAD重发（PAD 1.4.46+106）

- 按`/Users/newlink/kemi/kemi-rd/md/huawei-apk-trust-rustdesk.md`复核发布边界：华为可信度不能靠频繁换包名或签名规避。PAD继续使用正式包名`com.newlinksz.kemi.remote`和固定Newlink release证书，只将Android build number从105递增到106；业务代码、权限、HTTP服务及内置Windows/Mac/Linux客户端字节均未改变。
- 新增`kemi-docs/macos-local-build.md`，只覆盖同事从`https://github.com/caucy2026/rust-desk`的`master` fresh clone并生成macOS客户端：递归子模块、固定vcpkg baseline、Flutter 3.29.3、codegen 1.80.1、Rust release、Flutter release、service装包、本地ad-hoc重签和逐项验收。文档明确开发者账号不需要进入Git；本机编译包与`Developer ID Application + notarization`外发包不能混称。
- 已在Apple Silicon本机按该链路实测：`flutter_rust_bridge_codegen 1.80.1`生成成功，`cargo build --locked --features flutter --release`成功，`flutter build macos --release --no-pub`成功生成60.3MB的`KEMI-远程桌面.app`。主程序、`service`和`liblibrustdesk.dylib`均为arm64，Info.plist为`com.carriez.rustdesk / 1.4.46 / 106`；复制service后按本地验收规则ad-hoc重签，`codesign --verify --deep --strict`通过。本机当前`0 valid identities`，该临时App未写入BIN、未冒充可外发Developer ID包。
- 首次Rust重编在最终归档阶段因磁盘只剩197MiB失败；只执行`cargo clean --profile dev`删除本项目约10GB可重建debug缓存后，同一源码完整通过。该事件不是代码或依赖失败，不需要修改业务源码。
- 使用Flutter 3.29.3 / Dart 3.7.2和既有固定keystore构建PAD release。最终文件为`BIN/KEMI-远程桌面-PAD-1.4.46+106-release.apk`，156,039,231字节，SHA-256 `d3ca5841ada8a7b354076dc85073b9c02b5647ad78ca33b5047410b6b6fc77ec`；包名、`versionName=1.4.46`、`versionCode=106`、仅arm64均正确，zipalign通过，v1/v2签名有效，Signer SHA-256继续为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- APK内三端候选的SHA-256保持：Windows `9cc13f6780a39388d590b2f7dc575b1e42712da630f7ae801947d4465867d6ad`、Mac `b0a826644814c488e2861d66ecd49b56983b270174e3de8de895b8f6ae06c2c4`、Linux `2276a6860c482b21f6ab4d9bb2502c5dadd69d2a140ef038506c638eebe5fa44`。
- 测试PAD`192.168.1.10:5555`已卸载105并全新安装106，启动事件注入成功；设备报告`versionCode=106`、`versionName=1.4.46`、`primaryCpuAbi=arm64-v8a`、`lastUpdateTime=2026-07-31 09:37:37`。设备实际`base.apk`与BIN交付包SHA-256完全一致。对应可复现源码基线提交为`fb61a9572`。

## 五十四、2026-07-31 Windows/Linux客户端与四端PAD分发交付（1.4.46；PAD 1.4.46+105）

- GitHub额度恢复后，focused run `30590581209` 在源码commit `4e30063b9a0b293eca18a355264fbbe6852be84e` 上完整运行。Windows x64越过Flutter SDK瞬时断线恢复、`mozjpeg-sys`旧Rust兼容和AOM `input_texture()`旧失败点，job `91032135590`最终成功；下载artifact `8778930483`得到22,637,056字节PE32+ x86-64便携EXE，SHA-256为`9cc13f6780a39388d590b2f7dc575b1e42712da630f7ae801947d4465867d6ad`。
- 同一run的Linux主job `91032135578`成功并上传可复用DEB。AppImage job首次失败不是镜像内容错误：构建器已生成目标同名文件，工作流仍执行`mv file file`而返回1；独立补包run `30593011026`随后确认第二个命令问题是`sudo appimage-builder`产物归root、普通`chmod`无权修改。工作流永久修复为“文件名已一致则不移动”和`sudo chmod +x`。
- 为避免重编已成功的Windows/Linux主程序，使用隔离分支`ci/appimage-rescue-1.4.46`直接复用run `30590581209`的DEB；run `30593128802`、job `91039510783`成功上传artifact `8779097294`。最终AppImage为82,983,416字节，ELF x86-64、AppImage v2魔数正确、可执行位有效，SHA-256为`2276a6860c482b21f6ab4d9bb2502c5dadd69d2a140ef038506c638eebe5fa44`。
- 发现旧自动release `kemi-4e300...`虽以源码SHA命名，实际tag错误指向旧默认分支`main`的`778d0e299`，原因是Arch中间包发布步骤未受`publish-release:false`约束，且最终release未显式设置`target_commitish`。不移动或覆盖该历史tag；新建正确tag `kemi-client-4e30063b9a0b293eca18a355264fbbe6852be84e`并核验指向`4e30063b9`，release内Windows/Linux、`manifest.json`、`SHA256SUMS.txt`的GitHub服务器digest与本地一致。工作流永久改为中间Arch包受发布开关约束，最终tag使用`kemi-client-<sha>`并显式指向`${{ github.sha }}`。
- 两份客户端已写入`BIN/KEMI-远程桌面-Windows-x64-1.4.46.exe`、`BIN/KEMI-远程桌面-Linux-x86_64-1.4.46.AppImage`，并以同一字节导入Android固定assets文件名；PAD内原Mac ZIP保持25,918,515字节、SHA-256 `b0a826644814c488e2861d66ecd49b56983b270174e3de8de895b8f6ae06c2c4`。
- 因新增两份内置客户端会改变APK字节，Android build number由104升为105，产品版本保持四端共同的`1.4.46`。本机共享Flutter已是3.32.4，直接构建会触发`DialogThemeData/TabBarThemeData`不兼容；3.24.5又缺`extended_text 14`使用的selection API。最终按项目Android文档基线使用隔离Flutter `3.29.3`（Dart `3.7.2`）构建，未改业务代码和共享SDK；磁盘不足时只清理本轮临时SDK/worktree/下载ZIP及可重建的Flutter输出。
- 最终PAD为`BIN/KEMI-远程桌面-PAD-1.4.46+105-release.apk`，大小156,039,230字节，SHA-256为`5ddb58965700676599dedbe248b3da97d9e0c8fd2ecb907d773c339cd80e0124`。包名`com.newlinksz.kemi.remote`、`versionName=1.4.46`、`versionCode=105`、仅arm64；v1/v2签名均有效，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 测试PAD`192.168.1.10:5555`已卸载104并全新安装105，系统和首页确认`1.4.46(105)`及`KEMI远程桌面PAD版 v1.4.46`。客户端页启动8686服务，页面真实显示Android、Windows、macOS、Linux且无“待导入”；从PAD HTTP实际下载Windows/Linux/Mac的哈希分别与上述三份源文件一致，离开页面后服务立即关闭。
- Windows EXE没有Authenticode签名，Linux AppImage也未签名；二者明确标记为团队测试候选，Windows/Linux目标系统上的GUI启动、远控和文件传输仍需分别实机验收，不能把云构建和PAD下载通过写成公开发布已通过。
- 永久工作流修复、PAD build number 105和统一文档位于交付代码提交`fe058d6fc91ca0866b6d713141e99074473f7a9a`并已推送`backup/master`。该推送自动触发的focused run `30595250999`和普通CI `30595250723`只会重建已经完成的同版本候选，确认head SHA精确匹配后均主动取消；Windows/Linux大文件不提交普通Git历史，由正确的GitHub prerelease与本地`BIN`保存，Android assets按文档从release导入。

## 五十三、2026-07-31 Windows/Linux 云端构建根因修复（1.4.46）

- 读取 focused run `30541481850` 的 Windows、Linux 完整日志后确认，两端并非分别出现平台问题，而是共同在 `mozjpeg-sys 2.2.3` 构建脚本失败：该版本调用较新 Rust 才提供的 `Option::is_none_or`，工作流固定的 Rust `1.75` 因此报 `E0599`。
- vcpkg 缓存 `400`、Linux 容器缓存未命中以及 Windows 构建目录不存在均是伴随警告或编译失败后的连带现象，不是第一根因；继续盲目重跑同一 commit 不可能成功。
- 按当前 RustDesk 上游锁文件的兼容选择，将上层 `mozjpeg` 精确回退并锁定为 `0.10.11`、`mozjpeg-sys` 精确回退并锁定为 `2.2.2`，保留 Flutter 桌面/Sciter 的 Rust `1.75` 基线，避免为修一个传递依赖而扩大工具链升级范围。
- 新 run `30585850076` 验证 Linux 主构建成功并越过旧依赖错误；Windows 随后暴露第二个独立问题：7月25日为 Mac 本地调试引入的 AOM stub 没有实现 `vram` 特性要求的 `input_texture()`。该 stub 只接受内存 YUV 输入，按 VPX/HW-RAM 的同类语义补充返回 `false`。
- Linux AppImageBuilder 已成功生成镜像，但 recipe 仍硬编码 `1.4.35`，上传 glob 只匹配当前 `1.4.46`，导致上传步骤警告“未找到文件”却把 job 标为成功。现改为构建前注入当前 `VERSION`，构建后必须找到 AppImage 并统一重命名；缺文件会立即失败，不再允许假成功。
- run `30588285080` 再次确认 Linux 主构建成功；加强后的检查同时揭示 AppImageBuilder fork 实际只留下 `AppDir.squashfs`，没有执行最后的 runtime 合成。工作流现优先接收构建器产物，若缺失则下载官方 type2 runtime 并与 squashfs 合成可执行 AppImage，最后以非空文件校验作为上传门禁。
- 同一 run 的 Windows 在下载 Flutter SDK 时遇到 `Recv failure: Connection was reset`，属于 GitHub runner 的瞬时网络中断，不是源码编译错误。工作流在安装动作后增加 SDK 完整性检查；若缓存缺少 `flutter.bat`，自动用带重试的官方 SDK 下载恢复，再进入自定义 engine 和 Rust 构建。
- `cargo +1.75 metadata --locked --no-deps` 已通过；Windows x64 EXE、Linux x86_64 AppImage和最终PAD分发验收结果见第五十四节。

## 五十二、2026-07-30 Mac与PAD内置下载包同步交付（1.4.46+104）

- 本轮将最新macOS Apple Silicon客户端重新构建并内置到最终PAD release。因为原`1.4.45+103`已经存在正式Android制品，而替换内置Mac ZIP会改变APK内容，为避免“同版本、不同字节”的不可追踪状态，源码、Mac和PAD统一提升到`1.4.46+104`；Windows/Linux构建元数据同步为`1.4.46`。
- macOS先完成Rust release和Flutter release构建，再复制同版本`service`；主程序、`service`和`liblibrustdesk.dylib`均核验为arm64。整个App使用固定测试身份`KEMI Local App Signing 2026`签名，原包和ZIP解压副本均通过`codesign --verify --deep --strict`。
- 最终Mac ZIP为`BIN/KEMI-远程桌面-macOS-arm64-1.4.46+104.zip`，大小`25,918,515`字节，SHA-256为`b0a826644814c488e2861d66ecd49b56983b270174e3de8de895b8f6ae06c2c4`；同一字节副本已写入`assets/client-dist/KEMI-remote-desktop-macos-arm64.zip`。
- 最终PAD为`BIN/KEMI-远程桌面-PAD-1.4.46+104-release.apk`，大小`52,000,697`字节，SHA-256为`3ef9b5b33042d73f4571a8f97727765010f221cc4ee3c29b4adc89a4e081944d`。包名`com.newlinksz.kemi.remote`、`versionName=1.4.46`、`versionCode=104`，只包含arm64 ABI；v1/v2签名均通过，签名证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 测试PAD`192.168.1.10:5555`已卸载中间版本并全新安装最终release；系统和首页分别确认`1.4.46(104)`及`KEMI远程桌面PAD版 v1.4.46`。进入“客户端”页后日志确认HTTP服务在`8686`启动，`/health`返回`ok`。
- 从PAD真实HTTP地址下载Mac ZIP后，哈希与BIN/asset完全一致，解压显示`1.4.46+104`且深度签名校验通过；从同一页面下载Android APK后，与BIN正式包及设备已安装`base.apk`三者SHA-256完全一致。该结果证明PAD实际分发的不是构建目录旧缓存。
- 本轮只更新`BIN`与PAD内置Mac包，未替换`/Applications/KEMI-远程桌面.app`，本机已安装副本仍为`1.4.44+102`。Mac交付包仍是本地自签名测试包，没有Apple Developer ID和公证票据；浏览器下载到其他Mac后仍可能被Gatekeeper阻止，正式外发必须完成Developer ID签名与notarization。
- 为避免误用，构建过程中的`1.4.45`中间Mac ZIP和被覆盖的同名PAD中间包已移出`BIN`到临时目录；`BIN`只将`1.4.46+104`作为本轮最新双端交付。

## 五十一、2026-07-30 Android正式包名、固定签名与Release交付（1.4.45+103）

- 公司域名`www.newlink-sz.com`对应的Android正式applicationId固定为`com.newlinksz.kemi.remote`；Kotlin namespace暂保留`com.carriez.flutter_hbb`，避免全量移动原生代码引入功能回归。旧测试包和新包属于两个独立应用，首次迁移必须卸载旧包并重新授权。
- 删除release使用`~/.android/debug.keystore`和明文默认密码的配置。Gradle改为只从`KEMI_ANDROID_KEYSTORE`、`KEMI_ANDROID_STORE_PASSWORD`、`KEMI_ANDROID_KEY_ALIAS`、`KEMI_ANDROID_KEY_PASSWORD`读取正式签名，缺任一项时release任务直接失败，不能回退debug证书。
- 在源码仓外创建固定PKCS12密钥`/Users/newlink/kemi/RustDesk/signing/android/kemi-release-2026.p12`，RSA 3072、SHA256withRSA、有效期10000天；随机密码仅存当前用户钥匙串服务`KEMI Android Release Keystore 2026`。证书SHA-256为`85:46:D0:3E:51:D0:9D:FA:17:DB:CF:43:2F:84:BC:CF:74:BD:2D:9F:DE:1C:FF:98:1F:F2:02:F8:87:18:71:A2`。
- release首次AOT失败根因是Flutter 3.24.5缓存的`gen_snapshot`为x64而本机缺Rosetta；安装Apple Rosetta 2后AOT通过。随后dex失败根因是`src/main`中的Flutter 3.29兼容空桩与当前3.24.5 release engine真实`PluginRegistry`重复；将兼容桩限制到`src/debug`，release使用engine真实类。根因修复后恢复R8/minify与资源裁剪，压缩版构建、安装和基础回归通过。
- Cargo、Flutter、Windows/Linux workflow、RPM和Arch版本统一升级为`1.4.45`，Android build number为`103`。arm64 release APK构建成功，包名`com.newlinksz.kemi.remote`、`versionName=1.4.45`、`versionCode=103`、v1/v2签名有效、证书指纹一致、非debuggable，只包含arm64 ABI。
- 最终R8优化APK大小`52,002,623`字节，SHA-256为`bd4527adad9653e28d28430a483c7f342b2545e66111dbeaad71842f05662244`；已复制到`BIN/KEMI-远程桌面-PAD-1.4.45+103-release.apk`。
- 测试PAD`192.168.1.10:5555`已卸载旧`com.carriez.flutter_hbb`并安装新release；系统确认`1.4.45(103)`、冷启动成功、`run-as`拒绝调试。首页截图确认显示`KEMI远程桌面PAD版 v1.4.45`。
- “客户端”页HTTP服务回归通过：进入页面端口8686启动，离开页面停止，再进入重新启动；从PAD下载的`KEMI-remote-desktop-1.4.45.apk`与本地release SHA-256完全一致。macOS ZIP和原有敏感权限均保留，功能未通过删减换取告警变化。
- 当前ADB设备厂商为`HL2.0`、型号`huanglong`，不是华为手机；华为“诈骗应用”告警是否消除仍须在原华为设备上安装同一SHA-256 APK并记录完整提示、HarmonyOS版本和安装来源。
- 新增`android-release-signing.md`作为Android正式身份、密钥、构建和迁移的唯一操作文档；Mac源码版本已同步，但本次未重建Mac，BIN与Mac已安装包仍为`1.4.44+102`。

## 五十、2026-07-30 通用备份与云构建协调文档（1.4.44+102）

- 将`ci-build.md`重构为“通用方法 + KEMI特例”两部分。通用部分明确区分开发备份、候选构建、Actions artifact和候选发布，新增交付分支、`wip/*`、文档路径与tag的职责模型。
- 补齐“旧run未完成但新代码必须备份”的流程：未完成代码推`wip/*`，不移动交付分支；纯文档只有在workflow真实配置`paths-ignore`且没有混入源码时才可安全推交付分支；准备生成下一轮客户端时才推交付分支。
- 新增推送决策表、并发组设计、候选唯一身份、失败重试/主动替换条件、`Backed Up → Candidate Submitted → Cloud Ready → Imported → Delivery Done`状态模型，以及默认分支错位、子模块/LFS、本地BIN、workflow变更、紧急修复与monorepo等特例。
- KEMI特例单列`backup/master`、`wip/*`、focused workflow、Windows/Linux矩阵、API查询和run `30541481850`真实结果；同步更新`WORKSPACE.md`、`GIT-OPS.md`、`SESSION-HANDOFF.md`、README和Windows构建提示词。
- `.github/prompts/**`当前不在focused workflow的文档忽略路径内；本次没有混入该目录修改，避免纯说明提交触发新一轮Windows/Linux构建。接续prompt的同义规则留到下一次真实代码候选一起更新。
- GitHub当前default branch仍是旧`main`，真实客户端和文档位于`master`；修正仓库设置前，Windows同事必须使用`git clone --recursive --branch master ...`，避免下载旧monorepo。

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
