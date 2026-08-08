# KEMI-远程桌面 开发调试记录

> 基于 RustDesk 定制，日期 2026-07-26

## 一百零四、2026-08-08 1.4.76+183 PAD自有设备权限与固定工具链发布

- 本轮功能、版本、KEMI服务器受控补丁和审计文档提交为`bacea56df`；`libs/hbb_common`仍跟随公开上游gitlink，KEMI服务器与公钥由主仓`.github/patches/kemi_hbb_common_server.diff`复现，避免推送云端无法获取的私有子模块提交。
- 本轮只更新Android PAD；macOS、Windows和Linux二进制保持`1.4.75`批次原字节不变。项目固定使用`client/.toolchains/flutter`中的Flutter 3.24.5以及现有NDK、Gradle和Cargo缓存，不读取或切换全局Flutter。
- 将“双屏能力”与“KEMI自有设备权限”拆开：普通双屏判断继续服务跨屏键盘和窗口；中继权限要求设备同时具备`huanglong.product.type.stb`系统特性、`BRAND=huanglong`、`DEVICE=hi3781v730`，并存在1920×1280、支持Presentation的活动非默认屏幕。其他Android安装默认只允许P2P，不因本地伪造`access_token`开放中继。
- 首轮Kotlin编译发现`Display.type/TYPE_EXTERNAL`属于当前SDK不可见API；保持产品属性条件不变，改用公开的`displayId != Display.DEFAULT_DISPLAY`和`state != STATE_OFF`判断非默认活动屏，随后固定签名Release构建通过。
- Android ARM64 Rust核心完整重编，包内核心确认包含新的P2P-only拦截逻辑且不存在未解析`sodium_*`符号。正式APK为24,590,842字节、SHA-256 `fd969b4b2ca3f9de3ab8f7a8e4e7a76cb6d9c83e37957c2e94a15e2bd53b31b6`；包名`com.newlinksz.kemi.remote`、版本`1.4.76+183`、仅arm64-v8a、zipalign及v1/v2签名有效，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 带版本归档为`BIN/KEMI-远程办公-PAD-1.4.76+183-release.apk`；固定上传文件`BIN/release/KEMI-PAD.apk`、`SHA256SUMS.txt`和`release-manifest.json`已更新并通过四端交叉校验。测试PAD`192.168.3.63`当前ADB连接超时，尚未安装真机，不能把构建通过写成真机验收完成。
- 复核macOS正式包时曾在受限自动化隔离环境执行`codesign`，该环境无法访问完整钥匙串和macOS Security/trustd服务，输出`Authority=(unavailable)`并误报`invalid signature`。对相同`BIN/release/KEMI-macOS.zip`在正常系统安全上下文重新解压复验后，Developer ID深度签名、安全时间戳、`stapler validate`和Gatekeeper全部通过，结果仍为`accepted / Notarized Developer ID`；文件大小和SHA-256始终未变化。因此`1.4.75+182`Mac正式包从未损坏，也未在Apple等待期间被修改。
- 后续macOS发布门禁增加环境有效性判断：出现`Authority=(unavailable)`或钥匙串身份不可见时，只能记为“当前环境无法验签”，禁止宣布制品损坏；必须在完整macOS用户安全上下文联合执行`codesign --verify --deep --strict`、`xcrun stapler validate`和`spctl --assess`。同时比较压缩前后关键二进制SHA-256，只有完整环境仍失败或字节确实变化才判定损坏。
- 误判期间额外生成了未安装、未进入release的macOS `1.4.76+183`候选，并提交公证请求`c240c04b-7833-4853-933a-9954a9d4d70e`；截至记录时状态为`In Progress`。该请求不会修改本地App，也不具备自动发布权限，无论Apple后续结果如何都不得自动替换已验证的`1.4.75+182`正式包；只有用户另行决定发布Mac新版本并完成完整验收后才能晋级。

## 一百零三、2026-08-08 1.4.75+182 四端正式包与macOS公证闭环

- 功能发布提交为`62473ba7f7d31c2e6971b5ada127cd4f4a49d77f`；云端构建兼容提交`66c71a888e5ba0ee0c25ad5223e2a3805cc7749e`只把`igd-next`固定到Rust 1.75可编译的`0.16.1`，解决`attohttpc 0.30/indexmap 2.14`导致bridge任务失败的问题，不改变对外版本和连接策略。
- GitHub focused run `31184071063`全部通过：bridge、Windows x64、Linux x86_64 AppImage和汇总manifest均成功。Windows为22,720,512字节、SHA-256 `84d78eec4c8e78afd55691c53e73ecb0ed14a1dbb4797e6c15afac40af0f41d9`；Linux为77,785,592字节、SHA-256 `19eb461779aff801d54667eb6525f30df8fb80c2ab28278d50b4e41a2ac05e53`。
- macOS `1.4.75+182`使用`Developer ID Application: zhen ji (26T5WV4GLP)`、Hardened Runtime和安全时间戳签名；公证请求`b2746a22-151d-42c5-9bad-9876a936a083`返回`Accepted`。App完成`stapler staple/validate`、深度签名和Gatekeeper复验，结果为`accepted / Notarized Developer ID`；最终ZIP为22,650,917字节、SHA-256 `a0b51eeaca4284fc171cbe39d5cc227938d450c3363b8631b61c44233da2b206`。
- PAD固定签名包为`1.4.75+182`、24,591,455字节、SHA-256 `660c2612264a318629ae870ecd5128c6d123a10a0c9ad073b1c9fe41f468bb32`；包名`com.newlinksz.kemi.remote`，zipalign、v1/v2签名有效，证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 四端带版本不可变归档已写入`BIN/`，四个固定客户端、`SHA256SUMS.txt`和`release-manifest.json`已整体覆盖到`BIN/release/`。旧六文件完整备份在`BIN/release/candidates/release-backup-before-1.4.75-20260808/`；四端哈希、manifest大小/哈希和SHA清单交叉门禁全部通过。
- manifest批次为`1.4.75+182-four-client-notarized`，云端状态暂记`pending-manual-upload`。管理员必须依次上传PAD、macOS、Windows、Linux、SHA256SUMS，最后上传release-manifest；上传和回读完成前不能宣称Newlink云端已经发布1.4.75。

## 一百零二、2026-08-07 1.4.75+182 P2P实验回退与两日现场故障归档

- 根据同网、电信热点和联通热点的真实会话日志，新增`p2p-field-failure-cases-2026-08-06-07.md`，逐项记录STUN映射、MAC候选、最终连接类型、耗时、失败原因和网络处置方法。
- 明确电信热点两次成功使用普通随机端口`TCP with UDP punch`，并非固定21118；联通热点现场映射变化明显且为`ASYMMETRIC`，普通随机端口和固定21118均快速失败。该差异属于当次运营商NAT池行为，不能按运营商品牌硬编码。
- 现场确认MAC网络为`192.168.3.65 → 192.168.3.1 → 172.21.16.1 → 公网`双层NAT。MAC监听21118正常，但UPnP仅返回下级路由私网WAN `172.21.16.142:21118`；上级TP-Link的PCP/NAT-PMP和UPnP端口均关闭，必须由管理员配置最外层端口转发、DMZ或桥接。
- 回退`1.4.73+180`的4次/650ms重试，以及`1.4.74+181`的3次/250ms重试、提前第二轮TCP打洞和延迟中继实验。现场证明这些修改增加约2秒等待，却不能改变不可达的上级NAT。
- `src/client.rs`恢复为提交`3c177be17`的快速并行基线；保留同socket STUN、固定21118候选、MAC映射真实性校验、中继政策和会话内远程诊断。仅为诊断过滤增加固定候选关键词，不改变连接时序。
- 修正版提升为`1.4.75+182`，已完成ARM64核心、签名APK和包内核心一致性校验，候选包保存到`BIN/release/candidates/KEMI-远程办公-PAD-1.4.75+182-p2p-fast-baseline.apk`；尚待安装并完成同网/电信/联通三类回归。设备上的`1.4.74+181`为实验包，不得进入正式release。

## 一百零一、2026-08-07 1.4.72+179 MAC复制按钮与弹窗操作区统一

- 根据现场结果确认：弹窗本身支持剪贴板，右下角“知道了”可点击，但放在`content`地址行内的自定义手势没有稳定进入复制回调。
- 地址行恢复为纯展示的灰色样式；“复制”改到`AlertDialog.actions`，与已验证可点击的“知道了”使用同一类`TextButton`、同一布局和同一事件通道。
- 复制后立即从macOS系统剪贴板回读；只有读回内容与云端地址完全一致时，按钮才显示“已复制”，否则显示失败提示。
- `1.4.72+179`已安装到`/Applications/KEMI远程办公.app`，旧`+178`已移入`BIN/release/candidates/installed-backups`。候选App、Rust核心和Info.plist分别回读`1.4.72 / 1.4.72 / 179`，Developer ID深层签名通过。
- 2026-08-07用户在Mac本机鼠标操作下现场验收，确认“复制”可正常点击并写入地址。`+178`的失效不能归因于远程注入：现有证据只能确认`content`内自定义地址行未命中复制回调，而同一弹窗`actions`内的标准按钮正常；没有事件日志证明更深层原因时，不再猜测输入来源。此类关键操作应优先复用已验证的标准弹窗操作区，并以真实本机点击和系统剪贴板结果为闭环依据。
- Mac Developer ID构建脚本取消每次无条件`flutter clean`：项目已绑定自有Flutter 3.24.5，常规版本只需增量构建；仅在新鲜克隆、`.dart_tool/package_config.json`缺失时执行离线`pub get`。SDK/Xcode/插件迁移后如需强制全清理，显式使用`KEMI_MACOS_FORCE_CLEAN=1`；避免删除健康的项目依赖图后反复修复全局Git缓存。

## 一百、2026-08-07 1.4.72+178 MAC复制底层指针事件修复

- 按用户要求恢复灰色地址行样式，不再用独立蓝色按钮改变页面视觉；复制图标和小字仍位于地址行右侧。
- 当时尝试将`TextButton/InkWell onTap`改为整行`Listener onPointerDown`，但随后的Mac本机鼠标测试仍失败，证明“远程注入在Flutter手势竞技场中丢失点击”的当时假设不成立。该尝试在`+179`中已废弃，改用弹窗标准`actions`按钮。
- 本轮只增加macOS构建号到`+178`，Rust核心语义版本保持已真实重编并验证的`1.4.72`，避免为纯Flutter点击修复重复进行无意义的核心全量版本升级。

## 九十九、2026-08-07 1.4.72+177 MAC复制实体按钮与核心混装修复

- 下载地址与复制操作彻底分离：左侧灰色地址框只显示地址，右侧为独立蓝色实体`ElevatedButton`，包含复制图标、`复制/已复制`文字和显式手型光标，不再使用看似普通文本的整行手势。
- 保留剪贴板写入后的即时回读校验；按钮只有在系统剪贴板确实等于`http://kemi-chat.newlinksz.com:21120/kemi-desk`时才变为“已复制”。
- 排查发现macOS候选构建脚本只执行Cargo `--bins`，没有重编Xcode实际嵌入的`target/release/liblibrustdesk.dylib`；因此此前App的Flutter界面已更新，但动态库仍返回旧核心版本`1.4.64`。构建现改为`--lib --bins`并强制检查动态库存在，禁止再次产生新外壳配旧核心的混装包。

## 九十八、2026-08-07 1.4.71+176 MAC就绪页复制真实闭环

- 现场确认点击后系统剪贴板仍保留旧链接，说明`1.4.70+175`的紧凑`TextButton + SelectableText`组合没有稳定命中远程鼠标事件，并非仅缺少视觉提示。
- 删除地址行中的独立小按钮和可选择文本手势，改为整个地址框统一响应点击；框内仍显示小字号“复制”，命中面积覆盖整行，适合PAD远程鼠标操作。
- 写入剪贴板后立即回读校验：成功时框内变为勾选图标和“已复制”，失败时明确提示“复制失败，请重试”，避免再用无反馈的代码推断功能已经生效。

## 九十七、2026-08-07 1.4.70+175 MAC就绪页按钮防重叠

- 修正`1.4.69+174`中“复制”位于内容区右下角、与弹窗操作区“知道了”发生视觉重叠的问题。
- “复制”改为地址框内部右侧的小文字按钮，与下载地址处于同一行；弹窗右下角只保留“知道了”，同时减少整体高度并继续保证无需滚动即可完整显示。

## 九十六、2026-08-07 1.4.69+174 MAC就绪页固定完整显示

- “云端下载总地址”精简为“下载地址”，“复制云端地址”精简为“复制”；地址与按钮字号缩小，避免喧宾夺主。
- 移除弹窗内容滚动容器，按当前固定内容的自然高度一次完整展示；二维码、下载地址与复制按钮均无需滚动即可看到。

## 九十五、2026-08-07 1.4.68+173 MAC就绪页云端总地址明确化

- MAC首页“就绪”弹窗的唯一入口明确命名为“云端客户端下载”，直接显示“云端下载总地址”`http://kemi-chat.newlinksz.com:21120/kemi-desk`，二维码与该地址完全一致。
- 将原来的无文字复制图标改为独立“复制云端地址”按钮；复制成功后提示已复制。页面不再显示平台直达链接或其他下载入口。

## 九十三、2026-08-07 1.4.66+171 MAC应用名与授权记录统一

- macOS Bundle 的产品名、显示名、可执行文件名统一为`KEMI远程办公`；Bundle ID仍固定为`com.newlinksz.kemi.remote`，因此固定Developer ID签名身份、服务器配置和后续升级链保持不变。
- 按用户要求执行`tccutil reset All com.newlinksz.kemi.remote`，仅清除KEMI自身的TCC隐私授权记录。新包首次使用时需重新授予“屏幕录制”和“辅助功能”；不再将输入监控视为PAD控制必需权限。
- 版本统一提升为Cargo/Rust `1.4.66`、Flutter/macOS产品 `1.4.66+171`，避免旧名称包和新名称包使用相同更新版本。

## 九十四、2026-08-07 1.4.67+172 MAC就绪页下载入口收敛

- MAC首页“就绪”弹窗只保留一个云端客户端下载页`http://kemi-chat.newlinksz.com:21120/kemi-desk`，删除Windows、Linux、PAD/Android三条平台直达链接，避免用户面对重复入口。
- 弹窗重新排列为：服务器就绪说明、下载用途说明、居中二维码、完整可选择/复制的云端地址；云端页面继续负责选择不同平台客户端。

## 九十二、2026-08-07 MAC手机热点P2P候选修正与就绪下载地址

- 源码与MAC测试候选版本提升为 `1.4.65+170`；本轮只准备MAC端验证包，不覆盖正在运行的MAC应用，也不提前同步PAD、Windows和Linux。
- MAC“就绪”状态弹窗增加“其他客户端下载地址”，列出Windows、Linux、PAD/Android和云端下载页；每项支持复制，云端下载页提供二维码。地址使用hbbc稳定路由，不把实际动态文件URL硬编码进客户端。
- MAC端固定TCP 21118的PCP/NAT-PMP/UPnP映射增加就绪状态：只有最近一次固定端口映射成功时，MAC才跳过对称NAT的提前中继分支，允许PAD继续尝试认证直连。
- 直连候选不再仅因远端NAT标签为`ASYMMETRIC`才尝试固定端口；非局域网会并行尝试普通TCP、UDP和MAC公网IPv4:21118，映射不可达时快速失败，不改变已有成功候选。
- 该修正针对PAD手机热点/级联Wi-Fi的P2P失败链路，不能绕过运营商CGNAT、外层路由未转发或PAD封锁出站21118等客观限制；首轮只验证MAC与真实日志，其他客户端待MAC确认后再同步。
- Rust核心本地检查被本机缺少`cmake`阻塞（`libsamplerate-sys`构建依赖），不是本次代码编译错误；Flutter静态分析工具未安装，UI需在项目固定Flutter环境中继续验证。

## 九十一、2026-08-06 1.4.62+167 文件按钮与非模态跨屏窗口闭环

- 以已经冻结的`1.4.61+166`密码键盘稳定提交为基线继续开发，因此本轮独立提升为`1.4.62+167`；不复用旧版本号，不让客户端更新比较遇到同版本不同APK。
- 远控底栏“文件”按钮新增独立`closed/opening/open/closing/hidden`状态机：打开时图标使用与键盘开启相同的绿色，并显示半透明绿色背景；关闭或被主屏其他应用覆盖时恢复白色和透明背景，切换中显示圆形进度并禁止重复点击。
- 同一按钮现在是严格切换开关：第一次点击在另一屏打开文件窗口，第二次点击先通知独立文件FFI关闭传输会话，再结束`FileTransferActivity`；没有用强制结束Activity代替会话释放。主屏文件窗口自己的关闭图标同样发布`closing → closed`，副屏无需轮询即可同步刷新。
- 新增Native到各FlutterEngine的文件窗口状态广播，并保留`get_file_transfer_window_state`初始化查询，防止页面晚于窗口创建或引擎切换时漏掉事件。HOME或用户在主屏操作其他应用使文件任务停驻时发布`hidden`，副屏按钮恢复未开启状态；再次点击仍可沿用既有跨屏恢复策略。
- 根因修复：旧文件卡片虽然视觉上只有90%×78%，Android Activity仍是全屏透明输入层，透明区域会遮住主屏桌面。现在原生窗口本身固定为显示屏90%×78%、居中、透明且带`FLAG_NOT_TOUCH_MODAL`；Flutter文件卡片填满该窗口，视觉尺寸不变，窗口外触摸直接交给主屏桌面或其他应用。
- 真机`192.168.3.63:5555`副屏完成三条闭环：第一次点击文件后按钮绿色带背景，原生Frame为`[96,93]-[1824,1091]`即1728×998且窗口flag包含`NOT_TOUCH_MODAL`；第二次点击日志为`toolbar_toggle → close_button → activity_destroyed`；主屏点击关闭图标日志为`close_button → activity_destroyed`且副屏按钮恢复白色。主屏启动Krita也证明窗口外区域未被KEMI截获，文件窗口相应进入`hidden`。测试期间远控画面保持连接。
- 按固定NDK重编Android arm64 Rust核心并通过libsodium符号门禁，固定签名Release构建成功。最终APK为24,578,271字节，SHA-256 `edc393c3b498818ffecf2a585a7d537c16b9a427dc100668c1869acb2385e926`；包名`com.newlinksz.kemi.remote`、版本`1.4.62+167`、`minSdk=22`，v1/v2签名有效，固定证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。本轮正式归档到`BIN/`并只替换`BIN/release/KEMI-PAD.apk`，桌面三端保持原字节；重新生成`SHA256SUMS.txt`与`release-manifest.json`后，管理员只需依次上传`KEMI-PAD`、`SHA256SUMS`、`release-manifest`。
- Newlink三个固定接口已回读到本批次：线上APK、`SHA256SUMS.txt`和`release-manifest.json`与`BIN/release`逐字节一致，manifest批次为`1.4.62+167-pad-file-window`并绑定源码`5f4779b424ac22c7aa2fbd10916bf595cdde0237`。真机先安装固定签名`1.4.61+166`，客户端发现云端`1.4.62+167`后选择“是，先备份”，系统安装完成并回读`1.4.62+167`；公共Download生成24,579,994字节的`KEMI远程办公-1.4.61+166-备份-*.apk`，与1.4.61归档大小一致。至此“发现更新→下载校验→备份旧包→系统安装→新版本启动”闭环通过；“否，直接升级”仍留作下一批次独立验收。
- 升级后现场发现KEMI的Download列表只有3个目录，系统文件应用却能看到5项。ADB证据为系统目录存在3个目录、备份APK和XML，而`FileModel`同路径只返回3项，`MANAGE_EXTERNAL_STORAGE` AppOps为`default`；这不是缓存或文件丢失，而是Android 11+分区存储过滤其他普通文件。按`priv/xtqx.md`的方案A对真实包名执行`adb shell appops set com.newlinksz.kemi.remote MANAGE_EXTERNAL_STORAGE allow`，保持既有`SYSTEM_ALERT_WINDOW: allow`；关闭并重开文件窗口后日志从`entries=3`恢复为`entries=5`，XML和备份APK均可见。该命令是受控PAD的设备部署步骤，没有修改APK签名、包名或源码。

## 九十、2026-08-06 1.4.61+166 连接密码对屏输入稳定收口

- 用户确认当前真机版本相对稳定。本轮把此前尚未提交的“连接密码对屏输入”完整纳入版本记录，并单独提升为`1.4.61+166`，避免它与已存在的`1.4.60+165`自升级备份版本出现相同版本号、不同代码和不同APK哈希。
- 双屏PAD从首页点击连接时，先关闭首页`numeric_id`数字键盘并等待Manager真实回到`hidden`，但保留另一屏已经创建的宿主Activity。密码对话框聚焦后使用新的`inputMode=local_password`复用该宿主，不再依赖厂商ROM重新跨屏创建Activity，解决连接后密码框键盘不出现或输入落入失效InputConnection的问题。
- `local_password`使用独立伪session、独立Controller和独立原生回调：普通文字按当前选区写入本地密码`TextEditingController`，退格删除选区或光标前字符；不会调用远端`sessionInputString`，不会把密码内容提前发送给尚未认证的远端会话。对话框关闭后等待键盘隐藏并解除回调，防止密码处理器泄漏到下一次连接。
- 认证期继续保留Flutter密码框自己的InputConnection；远程会话键盘宿主只在收到第一帧后切换到正常远程模式。远程页退出改为释放当前实际宿主所有权，兼容宿主从首页数字模式、密码模式再切到远程模式的生命周期，避免用已经变化的session ID漏掉残留宿主。
- 本轮不修改鼠标左右键、触摸、文件传输、VP9、自升级备份和服务器协议。`1.4.60+165`真机已安装包与11:05构建包逐字节一致：24,579,999字节，SHA-256为`027a10ee9581a1d45b3d322192a9d96eb709137c27e2c3b1d2718fc60835e535`，固定签名证书不变；它作为用户确认稳定行为的原始证据保留，正式归档使用唯一的新版本号`1.4.61+166`。
- `1.4.61+166`按固定NDK重编Android arm64 Rust核心并通过libsodium符号门禁，固定签名Release构建成功。最终归档`BIN/KEMI-远程办公-PAD-1.4.61+166-release.apk`为24,579,994字节，SHA-256 `29a2ee1c6610fdcec847c8130fbc952bec2f12f8135dd16a8f9ebb2405f02e6d`；包内版本和构建号回读正确，v1/v2签名有效，证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。`BIN/release`未覆盖。

## 八十九、2026-08-06 1.4.60+165 Android自升级前可选备份

- PAD客户端发现云端Android版本严格高于本机、且新APK已经下载并通过大小与SHA-256校验后，点击“更新”会先询问“升级前备份当前版本？”。对话框只提供“否，直接升级”和“是，先备份”两项，不能误触空白处关闭；两种选择最终都进入原有Android系统安装确认流程。
- 选择“是”时，原生层在打开系统安装器之前读取当前已安装应用的`sourceDir`，将完整APK备份到系统公共“下载”目录，文件名包含当前版本、构建号和时间，例如`KEMI远程办公-1.4.59+164-备份-20260806-101500.apk`。Android 10及以上通过`MediaStore.Downloads + RELATIVE_PATH`写入，不依赖旧式绝对路径；Android 9及以下沿用公共Downloads目录。
- 未知来源权限尚未开启时不会提前复制APK。用户进入系统权限页再返回后，页面保留刚才的备份选择并继续同一升级请求，避免重复询问和重复备份。选择“是”但当前包不可读、属于拆分APK或下载目录写入失败时，明确显示错误并停止本次升级，防止用户误以为已经留存备份；选择“否”不执行任何备份写入。
- 该逻辑只作用于客户端下载页的“本机自更新”，不改变文件传输列表中用户手动选择其他APK的“安装”，也不删除当前App、缓存更新包或已有下载文件。系统安装器、包名和固定签名校验仍是最后安全边界。
- 源码与PAD版本提升为`1.4.60+165`。项目隔离Flutter对目标页面定向分析通过；Android arm64 Rust核心以`flutter,hwcodec,mediacodec`增量重编并通过libsodium未解析符号门禁；固定签名Release构建成功，大小24,581,112字节，SHA-256为`890a47efa5922af59068f202ee1615cbf5d44340b1384cc54a42e6515e32d065`，证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- APK已保留数据覆盖安装到`192.168.3.63:5555`，系统回读`1.4.60+165`，只在Display 2启动；进程存活、MainActivity为副屏Resumed/Focused，启动日志没有FATAL。为符合效率优先，本轮没有运行与自更新无关的全量测试；真实“低版本PAD读取更高云端版本 → 选择备份/不备份 → 安装”仍需在下一次云端发布时各验收一次。
- 本轮只形成源码和本机测试APK，未覆盖`BIN/release`六文件、未上传Newlink云盘、未推送GitHub；避免未完成远端升级验收时改变正式发布批次。

## 八十八、2026-08-06 1.4.59+164 首页数字键盘与HOME后宿主复用

- 双屏PAD首页点击“远程 ID”后不再在当前屏请求普通文本键盘。首页确认设备为双屏后，先在另一屏预创建不可聚焦、不可触摸的键盘宿主；点击输入框时以`numeric_id`模式激活，Android上报`inputType=2`，数字只回填本机ID控制器，不会误发给远端会话。单屏设备继续在本屏使用普通数字键盘。
- 修正`1.4.57`对HOME问题的不完整处理：删除`singleInstance`键盘任务虽然能清除busy状态，但厂商Android会拒绝随后从Display 2重建Display 0 Activity，于是新请求连续`open_timeout`。现在HOME只把现有宿主停驻为非交互状态，Manager同步回到`hidden`并保留Activity/channel/owner；下一次点击复用同一task。仅真正退出页面、进程后台、显示移除或显式release才销毁宿主。
- `KeyboardProxyActivity.onStop()`只处理已激活宿主，预创建但尚未显示的宿主不会再被误删除；HOME路径不调用`finishAndRemoveTask()`，也不会在HOME手势内把透明任务重新抢到前台。
- 实机进一步确认HOME会把Android 12厂商ROM的全局`appSwitchAllowed`短暂置为false；此时`startActivity`、`PendingIntent`和`moveTaskToFront`都会被静默拦截。双屏设备现在一次性检查`SYSTEM_ALERT_WINDOW`，未授权时显示中文用途说明并引导用户授权；该权限只作为跨屏键盘/文件任务恢复的后台启动例外，不创建悬浮图标。Native层在缺少权限时返回`cross_display_permission_required`，避免Flutter误报打开中；IME与文件任务仍保留有限重试处理生命周期竞态。
- 文件传输`singleInstance`宿主也改为HOME后停驻，下一次点击复用同一peer、同一Display和同一task；只更新连接参数并把原任务置前，不重复创建FlutterEngine或独立FFI会话。显式关闭文件页仍完整释放资源。
- 密码对话框仍保持“记住密码”位于密码框下方，但把原48px以上的`CheckboxListTile`改为36px紧凑整行可点区域，减少自动聚焦密码框后复选项被滚出默认视口的概率；语义和默认值不变。
- 远控底栏“文件”图标单独从默认24px缩小为18px，按钮宽度、文字、排序和点击区域均不变。
- Android Gradle显式锁定NDK `28.2.13676358`，与`ndk_arm64.sh`和CI一致。现场确认全局Flutter 3.41会污染`.dart_tool`并与Gradle 7.6.4不兼容；本次最终构建固定使用项目隔离Flutter 3.24.5，重新生成依赖索引后再以`--no-pub`打包，未提交离线解析造成的锁文件降级。
- 真机`192.168.3.63:5555`覆盖安装并只在Display 2冷启动，系统回读`1.4.59+164`。未授权状态首次点击会在副屏显示中文说明，进入厂商权限列表选择“KEMI远程办公”后，原点击自动继续并在主屏显示键盘。最终包中主屏HOME后0.4秒从副屏点击键盘，原`task=554`在约0.20秒内恢复为`visible`；文件页HOME后同样零等待点击，原`task=555`一次置前成功。两条路径均无`open_timeout`、重复Activity或FATAL。
- 固定签名arm64 APK为24,578,154字节，SHA-256为`0a8b787e776565be64159c01ddf0d82cb4692bf9dc1fb35a854a88cdb86e79c6`；包名`com.newlinksz.kemi.remote`，显示名`KEMI远程办公`，`minSdk=22`，v1/v2签名有效，证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。真机`base.apk`哈希与交付APK完全一致。

## 八十七、2026-08-06 1.4.58+163 Android启动崩溃修复与原生符号门禁

- PAD `1.4.57+162`真机启动100%崩溃，Java栈为`UnsatisfiedLinkError: cannot locate symbol sodium_base64_encoded_len`，发生在`MainApplication.onCreate()`加载`librustdesk.so`时，早于Flutter页面和本轮分享/HOME功能执行。
- 根因是`flutter/ndk_arm64.sh`虽然已经要求libsodium使用NDK `llvm-ar/llvm-ranlib`，但只在调用方提前设置`ANDROID_NDK_HOME`时生效。本地版本重链接时没有该变量，macOS归档工具生成了缺少libsodium对象的缓存；Cargo与Gradle允许共享库携带未解析符号，所以编译、签名和APK内外哈希比较均通过，直到Android动态加载才失败。
- 脚本现在会在Cargo启动前从`flutter/android/local.properties`解析SDK目录并锁定与CI一致的NDK r28c（`28.2.13676358`）；CI或显式环境仍可提供`ANDROID_NDK_HOME`。找不到固定NDK时直接失败，不再静默回退宿主机归档工具。
- Rust核心构建结束后强制用NDK `llvm-readelf`扫描动态符号表；只要存在任意`UND sodium_*`，构建立即失败并禁止进入Flutter/Gradle打包。发布验证必须包含真机冷启动，不能再用“APK构建成功、签名正确、内外so哈希一致”代替动态加载验证。
- 版本提升为`1.4.58+163`，只修复构建链和重打PAD，不改`1.4.57`已经完成的文件分享、HOME重开、工具栏及产品名逻辑。
- 修复版固定签名arm64 APK为24,664,810字节，SHA-256为`1180d3d4ff2c1f9387665dcabcc502cf08d1ec1b3f3f9ed4be452629f433c536`；APK内Rust核心SHA-256为`ac30f9d82f548a1d514c29652487db0bbc50539320b22058aa9a85df88fafa77`，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 已覆盖安装到`192.168.3.63:5555`并在Display 2冷启动。系统回读`1.4.58+163`，进程持续存活、Activity为Resumed、约888ms完成Fully drawn，日志无`UnsatisfiedLinkError`、FATAL或native crash。

## 八十六、2026-08-05 1.4.57+162 文件分享、跨屏重开与远控工具栏调整

- 文件传输左侧PAD本地列表对所有普通文件增加“分享”，根据扩展名解析MIME并通过Android `ACTION_SEND`系统选择器交给支持该类型的应用；文件夹、右侧远端文件和原有传输/删除/安装操作保持不变。
- 分享前原生层重新校验文件存在、可读和FileProvider授权范围，只向本次系统分享Intent授予单个URI读权限；没有兼容应用时在原位提示，不扩大目录访问权限。
- 修复另一个屏幕按HOME后键盘无法再次打开：停止的键盘宿主不再进入等待IME回调的`closing`死锁，而是同步发布`hidden`、清理Display监听与所有权并结束独立任务；下一次点击创建干净宿主。该处理同时覆盖已激活和仅预创建的键盘Activity。
- 文件传输独立Activity进入后台后立即结束自身任务和独立FFI会话，避免`singleInstance`隐藏任务拦截后续启动；远控视频Session不受影响，下一次点击可重新在对屏打开。
- 远控底栏新增独立“文件”按钮，固定排在“更多”之前；“更多”菜单删除重复文件传输入口，资源占用及其他动作保持不变。
- Android应用显示名称改为“KEMI远程办公”，辅助功能名称同步为“KEMI远程办公 输入服务”。包名`com.newlinksz.kemi.remote`和固定签名不变，可覆盖升级且保留应用数据与权限身份。
- 版本提升为`1.4.57+162`。Dart定向检查无error/warning，仅5条既有弃用info；Android Debug Kotlin与正式Release构建通过。固定签名arm64 APK为24,594,997字节，SHA-256为`c80314376828cc40a7834c1804af6946076f7649a22bc591aec665d6a76cba0e`；APK内Rust核心SHA-256为`2d32ba315620c816032d4e1cc11d5f2ad0c8bb6b728657a827fb992311c7fc70`，与本轮目标文件一致。

## 八十五、2026-08-05 1.4.56+161 PAD本地APK直接安装

- 文件传输双栏仅在左侧PAD本地列表识别普通`.apk`文件，并在原菜单顶部增加“安装”；右侧对方文件、目录和其他扩展名保持原操作，不允许未经传回本机直接安装。
- 点击后重新校验文件存在、可读、扩展名及Android包结构，再通过私有`FileProvider`只授权当前URI给Android系统安装器。KEMI不静默安装、不绕过系统确认；未授予“安装未知应用”时打开KEMI专属授权页，返回后再次点击安装。
- 主界面同屏文件窗口和双屏独立`FileTransferActivity`均注册同一原生方法，避免副屏/对屏文件窗口出现`MissingPluginException`。
- 版本提升为`1.4.56+161`。本批次只重新构建并替换PAD发布文件；Mac、Windows、Linux固定包继续保留1.4.49原字节，manifest必须继续标明PAD热修订混合批次。
- Android arm64 Rust核心从仓库根目录完整重编译，APK内`librustdesk.so`与本轮目标文件SHA-256均为`2c7d1796fda5e621ef4bcfe00685e95a31ee965a75a2d907aa881fa3cd999092`；避免只更新APK外壳而继续携带旧核心。
- 固定签名Release为24,593,361字节，SHA-256为`d383d1f06e783a1e463dba02aa81b42bbd5c7606c4c241ad7d2f476fa76a29a5`；包名`com.newlinksz.kemi.remote`、仅arm64-v8a、v1/v2签名有效，证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 构建期间发现全局Gradle 7.6.4 ZIP损坏，已保留到临时目录并从腾讯镜像重下后通过`unzip -t`；国内Flutter存储返回HTML伪POM，最终切换已实测返回正确510字节POM的官方`storage.googleapis.com`完成构建。两项均为缓存/镜像问题，不是源码冲突。

## 八十四、2026-08-05 1.4.55+160 PAD文件安全删除、空间门禁与自更新标识

- 文件传输改为PAD/对方左右双栏浏览，目录加载成功或页面关闭时强制清空旧选择，修复切目录后不可见项目仍被带入下一次传输的问题；Android“所有文件访问”改为打开专用授权页。
- 新增持久化的“远端传入本机”登记。只有传输成功并登记的PAD本地文件或文件夹显示删除入口；PAD原有内容和对方设备内容始终不可删除。文件必须路径、类型、大小和可用mtime仍匹配；文件夹以成功接收的根目录为授权边界，允许递归删除其全部内容。
- 删除收到的文件夹后，同时清除该根目录及所有子项的删除授权；以后即使同路径被本机重新创建，也不能继续删除。明确边界：用户后来放入该“已接收文件夹”的新内容也会随根目录递归删除，这符合“远端传来的整个目录可删除”的产品语义。
- 对方传到PAD前递归计算源大小，并读取Android目标卷可用空间；传输量大于剩余空间一半时禁止发送，恰好一半允许。无法可靠测量时PAD端拒绝；PAD传Mac及Mac作为目标时不应用此限制。
- 客户端页只在云端Android版本严格高于已安装版本时显示“更新”；PAD/Android行在云端版本旁新增小字“（本机版本 xxxxx）”。安装仍使用固定签名APK和Android系统安装器。
- 产品版本统一为`1.4.55`，PAD build为`160`。固定签名arm64 APK为25,202,278字节，SHA-256为`706e6d73f11ff2e002812923f180490f85283395088e1e33a3f00e99b8da581c`；包名`com.newlinksz.kemi.remote`，v1/v2签名有效，证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 8项文件策略单测全部通过；版本比较4个正反用例通过；定向Flutter analyze无error/warning，仅保留3条既有弃用info。上传前云端仍为`1.4.49+154`，因此当前更高版本本机不出现升级按钮是正确结果；上传后的正向下载、安装和重启回读留给远端设备闭环。
- `BIN/release`按PAD热修订批次更新：只替换真实重建的`KEMI-PAD.apk`，Mac/Windows/Linux继续保持已验收的1.4.49字节，清单明确标注混合批次，禁止把旧桌面包冒充1.4.55。

## 八十三、2026-08-05 1.4.53+158 Android VP9 MediaCodec硬件解码

- 根因确认：此前PAD虽然系统声明支持VP9，但Android原生库只使用`flutter,hwcodec`构建；该路径只覆盖H264/H265，VP9固定进入libvpx软件解码，所以“硬解设置已开启”和“设备支持VP9”都不能改变实际后端。
- Android `MediaCodecList`能力桥接加入`video/x-vnd.on2.vp9`，并上报解码器名称、真实硬件标记及I420/NV12/Flexible/Surface输出能力。Rust只选择系统确认的硬件组件和可安全读取的I420/NV12 ByteBuffer，不把`OMX.google`软件组件或无法判断像素布局的Surface/Flexible组件冒充硬解。
- 重写实验性的MediaCodec解码路径：按实际屏幕尺寸配置厂商组件，处理输出格式变化、stride、slice-height和crop，支持I420/NV12到Flutter ARGB/ABGR转换，传递每帧PTS，并处理一个消息中的全部视频帧。
- VP9硬解创建或运行失败时自动释放MediaCodec并切回libvpx，避免不兼容设备黑屏；资源监控上报实际创建的`Android MediaCodec hardware (<组件名>)`或`Software VP9`，不再根据开关猜测。
- Android arm64 Rust Release使用`flutter,hwcodec,mediacodec`完整编译通过；固定Flutter 3.22.3、Gradle 7.6.4和KEMI正式签名构建`1.4.53+158`成功。包名为`com.newlinksz.kemi.remote`，仅包含arm64-v8a，v1/v2签名有效，证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 真机只在`192.168.3.63`的Display 2副屏验收：实际远程流为VP9，系统创建`OMX.uapi.video.decoder.vp9`，配置输入1920×1280、实际输出1920×1080 NV12（color-format 21），画面尺寸、颜色与动态刷新正常，证明不是仅完成能力枚举而是真正进入厂商硬解。
- 连续观察期间设备记录外部PID执行`Force stopping com.newlinksz.kemi.remote`，同一时刻另一外部PID也强停KEMI Email，Android crash缓冲区为空；这是设备外部测试/管理动作，不计为MediaCodec崩溃。硬解被强停前持续正常，系统随后按固件策略预启动新进程。
- 详细实现、回退边界和验收证据同步到`connection-history-and-resource-monitor.md`。最终候选为`BIN/KEMI-远程桌面-PAD-1.4.53+158-release.apk`，大小24,545,396字节，SHA-256为`46da46668335d7baff995ad01fb7ccad022ab9ce72b86b764ef0c59d3a0c6945`；本轮不覆盖尚未同源码重建的`BIN/release`正式四端批次。

## 八十二、2026-08-05 1.4.52+157 首页入口、连接记录与真实解码监控

- 源码、Flutter、GitHub四端工作流、PKGBUILD和RPM版本统一升级为`1.4.52`，PAD构建号为`157`，不再用相同的`1.4.51+156`文件名覆盖不同代码。
- 修复无账号模式把通讯录和可访问设备入口一并隐藏的问题；保留两个入口，当前开源服务端下显示能力说明，不恢复无效登录按钮。旧五标签的可见性和排序可无损迁移，第六项“连接记录”默认追加到末尾。
- 新增跨重启持久化连接记录，记录发起时间、远端ID/主机名、连接状态、`connection_ready`确认的P2P/中继及TCP/UDP等实际流类型、时长和失败原因；支持单条删除与二次确认清空，最多200条。
- PAD资源监控把CPU拆分为整机占比和多核累计，明确162%表示约1.62个核心；新增协商编码、硬解设置和实际Decoder后端，避免把“开关已开”误认为“当前必然硬解”。
- Rust macOS `cargo check --locked --features flutter --lib`与Android arm64 `flutter,hwcodec` Release原生库编译通过；真机验收固定只在`192.168.3.63`的Display 2副屏进行。
- 固定签名arm64 Release APK为24,535,635字节，SHA-256为`b9c902c7541e3cd47bbb9ec81d7cc1e4909376962fede34820ff4a0223b9720e`；包名`com.newlinksz.kemi.remote`、版本`1.4.52+157`、v1/v2签名有效，固定证书SHA-256为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- APK覆盖安装后明确使用`am start --display 2`启动，系统回读Display 2为当前焦点且版本为`1.4.52+157`。副屏连接本机`238638760`实测为`P2P直连·TCP`，H265实际Decoder为`FFmpeg hardware`；资源窗口同时显示整机CPU 2.5%和多核累计20.1%。断开后记录主机名、开始时间、两分钟时长和`P2P·TCP`，强制结束应用并重新启动到Display 2后记录仍完整保留。
- 候选归档为`BIN/KEMI-远程桌面-PAD-1.4.52+157-release.apk`，不覆盖`BIN/release/KEMI-PAD.apk`，避免在Mac、Windows、Linux尚未按同一源码重建前制造半批次。
- 详细设计和验收口径见`connection-history-and-resource-monitor.md`。

## 八十一、2026-08-04 1.4.51相对稳定候选收口与KEMI-SEND地址约定

- PAD `1.4.51+156`固定签名arm64 Release构建成功，文件为24,149,566字节，SHA-256为`3da2ff1a1ea985dfbea388430e680d2a069f413bd79de8f1055a5871d276d7c9`；包名`com.newlinksz.kemi.remote`、版本码156、v1/v2签名有效，证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。候选归档为`BIN/KEMI-远程桌面-PAD-1.4.51+156-release.apk`，没有单独覆盖正式`BIN/release`六文件。
- 保留数据覆盖安装到`192.168.3.63:5555`并清空旧日志后，跨屏键盘打开期间连续采集19次物理右键，全部严格形成一组`down/up`；测试窗口内没有新增`Application does not have a focused window`或`Input dispatching timed out`，键盘代理仍持续产生文本提交。用户确认本版本相对稳定，作为后续开发和回归测试的新PAD候选基线。
- 右键修复的稳定边界明确为：远控源Activity永远保持可聚焦；键盘保持由IME代理状态机负责；厂商固件漏发release时按下一次无次键状态的鼠标事件补发`up`。剩余需长期观察的是特殊鼠标右键拖动时错误上报`buttonState=0`，不能通过重新加入`FLAG_NOT_FOCUSABLE`规避任何键盘问题。
- `KEMI-SEND-CROSS-PLATFORM-CLIENT-DISTRIBUTION.md`补充地址控制权：hbbc页面和平台稳定路由由JSON控制，可在客户端构建前确定；Newlink固定资源查询地址由管理员后台的`projectName + name`决定，也推荐提前约定；上传后返回的实际CDN `data[0].url`仍是动态值，由hbbc每600秒解析，禁止写死。
- KEMI-SEND推荐提前冻结`Common`项目、`/kemi-send`页面及六个`KEMI-Send-*`固定name。资源未就绪时保持`resolve_enabled:false`；六文件上传并回读验收后改为`true`，以后版本只覆盖同名资源，不重编hbbc、不修改客户端和用户入口。

## 八十、2026-08-04 物理右键ANR根因修复（PAD 1.4.51+156 候选）

- 真机日志确认右键协议层正常：每次物理右键均产生一组`[PhysicalMouse] right down/up on display 2`，不是MAC端执行慢，也不是右键释放通常丢失。
- PAD在16:53、19:35、19:38、19:51、19:53、19:54和19:58多次记录`Input dispatching timed out (Application does not have a focused window)`，ANR归属`com.newlinksz.kemi.remote/MainActivity`。
- 根因是跨屏键盘保持逻辑在键盘打开时给远控源窗口添加`FLAG_NOT_FOCUSABLE`。窗口仍接收物理鼠标目标事件，却不具备焦点，Android InputDispatcher等待焦点窗口后触发系统超时。右键因同时经过MotionEvent/KeyEvent兼容入口，更容易暴露该问题，但不是协议死锁。
- `KeyboardProxyManager`不再改变远控源窗口的focusable属性；键盘是否保持改由既有指针时间戳、IME可见性分类和代理Activity恢复状态机处理，保证远控画面始终是合法输入窗口。
- `PhysicalMouseRightButtonForwarder`增加状态自校准：若厂商固件在跨屏焦点变化时漏发`BUTTON_RELEASE`，下一次不含次键状态的鼠标事件会补发`up`，避免远端永久保持右键按下；显式release仍由原事件消费。
- Kotlin定向编译`:app:compileDebugKotlin`通过；固定签名Release和真机证据见第八十一节。键盘打开期间连续右键已经跨过原5秒ANR窗口且没有新增系统超时，用户确认本版相对稳定。

## 七十九、2026-08-04 副屏认证输入、旧清单回退与局域网直达页（PAD 1.4.50+155 候选）

- 现场复现“密码键盘已显示但字符输入不进去”时，远控认证页位于Display 2，而`KeyboardProxyActivity`被提前创建在Display 0。系统输入法仍显示`mInputShown=true`、Flutter密码框仍持有视觉焦点，但日志连续出现`commitText on inactive InputConnection`。根因不是输入法或Mac权限，而是`deferDefaultDisplay`只保护Display 0认证；副屏认证绕过保护后，代理Activity使原Flutter密码框的InputConnection失效。
- 删除进入远程页后的认证前`keyboard_proxy_prepare`。现在无论远程页位于主屏还是副屏，只有`pi.isSet=true`确认认证完成后才允许预创建跨屏键盘代理；认证密码始终由当前Flutter页面自己的输入连接接收。连接成功后的对屏键盘、触摸保持、物理鼠标左右键和单屏回退逻辑不变。
- PAD“可下载客户端”显示旧版并非后台文件没有更新。18:30真机日志明确记录Newlink `release-manifest`与`SHA256SUMS`的Android哈希暂时不一致，客户端立即回退并落盘GitHub `1.4.46+110`清单；随后按单线程顺序下载PAD、Windows、Mac、Linux旧包，新的刷新请求因`syncing=true`被拒绝，导致页面长时间保留旧版本。
- Newlink清单改为最多三次短间隔重试；GitHub备用清单如果Android版本早于当前已安装PAD则拒绝落盘，不能把新版PAD降回旧发布批次。重复全量刷新不再返回失败，而是设置高优先级刷新标记；正在下载的大文件在每个数据块后检查该标记，保留可续传`.part`并中断旧批次，先重新读取云端清单。单项下载被全量刷新打断时也会让位，避免Linux/Mac大文件长期锁住元数据。
- 局域网二维码过去编码`http://PAD-IP:8686`根页，浏览器先看到与PAD原生“客户端”页相似的双通道说明，四平台下载项在下方，用户会判断为进入错误页面。新增专用`/clients`路由；PAD显示、复制和二维码统一使用`http://PAD-IP:8686/clients`，该路由直接呈现Wi-Fi说明和四个平台下载项，不重复双通道入口。根路径继续保留完整导读页，兼容已保存的旧网址。
- 当前Newlink云端六文件仍是已发布的`1.4.49+154`批次，所以修复后四个平台页面显示`1.4.49 / 1.4.49+154`是正确结果；本轮`1.4.50+155`仅为PAD源码和真机候选，未用单个APK覆盖`BIN/release`，避免再次制造APK、SHA清单与manifest半批次不一致。
- 定向Flutter analyze无error，仅有项目既有弃用info；Debug与固定签名arm64 Release均构建成功。Release APK为24,149,535字节，SHA-256为`7894cc6c19ebd812948d7144eebbe5c55060b0e5a668a9530436aa448845a0fe`；包名`com.newlinksz.kemi.remote`、版本`1.4.50+155`、v1/v2签名有效，证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- 候选先覆盖安装验证，再按用户要求卸载后全新安装到`192.168.3.63:5555`的Display 2；系统回读`1.4.50+155`且旧客户端缓存目录不存在。进入“客户端”页后约0.2秒解析四个Newlink HTTPS资产并保存`1.4.49+154`云端清单；从Mac请求`http://192.168.3.63:8686/clients`只检出四平台选择和当前1.4.49版本，不再出现1.4.46或重复双通道。候选已归档为`BIN/KEMI-远程桌面-PAD-1.4.50+155-release.apk`，字节与构建输出SHA完全一致；用户仍需按首次流程验收密码实际输入。

## 七十八、2026-08-04 PAD服务器手动重连与全端1.4.49正式对齐（PAD/macOS 1.4.49+154）

- PAD主页服务器状态未就绪时，状态条后显示同风格的小型“重连”按钮；就绪后自动隐藏。点击只重启当前自建服务器的rendezvous注册链路，不重启共享屏幕、不重新申请MediaProjection，也不影响正在工作的录屏会话。按钮在重连中显示转圈，连接恢复后立即消失；10秒仍无结果则恢复可点击，用户无需再重启整个App。
- 重连复用当前`custom-rendezvous-server`并通过既有原生option处理触发`RendezvousMediator::restart()`：原socket循环退出、跳过普通退避后重新注册。Flutter侧禁止在已就绪或正在重连时重复触发，并由既有连接状态轮询收口UI状态。
- 产品版本统一为`1.4.49`，PAD/macOS构建号统一为`154`；Cargo、Flutter、GitHub工作流、PKGBUILD和RPM入口全部对齐。功能提交先冻结为`7eabfe8022f5dab5e15005034e1d4f4f4c3ab551`，PAD与Mac均由本机全量构建；随后云端暴露`client_download_page.dart`使用了Flutter新API`Color.withValues`，而正式Windows/Linux工作流固定Flutter 3.22.3。兼容修复仅将该处改为`withOpacity(.12)`，最终四端功能提交为`ed615c7fb17d72b5c5d69731a2e3c8d208fda7e6`，focused run为`30891539907`。
- 最终功能提交后重新全量打包PAD Flutter层，固定签名arm64 APK为24,148,348字节，SHA-256为`a815f7d84ba90e4abaf5640cb4c112bed31be58ceec64e3b7b820b912f2aaa9e`；包名`com.newlinksz.kemi.remote`、系统回读`1.4.49+154`、v1/v2签名有效，证书SHA-256为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。最终包已覆盖安装到`192.168.3.63:5555`并回读版本；当前另一屏正在运行其他应用，因此未强行修改服务器制造离线状态，避免中断用户现场。
- 最终功能提交后重新全量打包macOS Flutter层并固定签名；arm64 ZIP为25,997,857字节，SHA-256为`ca7f170c81edfc697372d3cee7f7d8ad7d43b45f5f5d760915cc26c5758f6d94`。App及ZIP解包副本均回读`1.4.49 (154)`，主程序、service和动态库均为arm64，固定本地测试证书深层校验通过，内置Rust核心逐字检出`1.4.49`和`kemi-chat.newlinksz.com`。该证书不是Apple Developer ID且包未公证，只用于公司测试机分发。
- focused run `30891539907`最终全绿：Windows x64 EXE为22,686,720字节，SHA-256为`e6f950941a2359f6b3944ee651e3eb282afedf7b1d03faab4b34e44b06e9e759`；Linux x86_64 AppImage为77,711,864字节，SHA-256为`47dec309fc9e93e8a2962ff6b8234a9504877c05949d1411b8b7bc094bdd456f`。两项本地哈希、GitHub Release资产摘要和云端`SHA256SUMS`三方一致，云端manifest精确绑定`1.4.49 / ed615c7fb / 30891539907`。
- 四端最终二进制全部从功能提交`ed615c7fb`构建后，才覆盖`BIN/release`固定文件并最后生成`SHA256SUMS.txt`与`release-manifest.json`。本轮审计特意重打了最初在兼容提交前生成的PAD/Mac，避免“功能等价但源码commit不精确”的可追溯性缺口；禁止拿旧二进制冒充1.4.49。

## 七十七、2026-08-04 PAD触摸键盘保持、工具栏收起与桌面状态跨平台对齐（PAD 1.4.49+153 候选）

- 物理鼠标点击远程画面时键盘保持显示已经闭环，但PAD触摸仍会关闭键盘。根因是原生焦点保护只识别`InputDevice.SOURCE_MOUSE`，触摸事件使用`SOURCE_TOUCHSCREEN`，完全绕过了源屏指针时间戳、代理任务前置和IME恢复保护。`+150`把该入口统一为源屏指针事件：鼠标主键和触摸按下/抬起共用保护，鼠标右键仍明确取消保护，避免破坏已验收的右键down/up转发。
- 底部工具栏点击“收起”后，展开入口固定显示在远程画面的右下角，并使用50%透明度，降低遮挡；收起动作完成后若键盘尚未打开，会自动执行现有键盘打开流程。键盘已经处于`opening/visible/closing`时不重复发送请求，展开工具栏也不主动关闭键盘。
- 首次安装后从主屏连接并在密码框输入时，旧逻辑会在认证完成前预创建对屏`KeyboardProxyActivity`，可能抢走首次Flutter密码框的输入连接，表现为键盘已弹出但字符进不去；副屏路径因Display调度不同没有稳定复现。新逻辑仅在默认主屏认证阶段延迟预创建，等`pi.isSet`确认认证完成后再创建；从副屏启动仍保留认证前预创建，规避Android跨屏后台Activity启动限制。
- 今天新增的桌面端“就绪 / 连接 / 抓屏 / 抓屏次数”状态实现位于共享`connection_page.dart`、`src/ipc.rs`、`src/ui_interface.rs`和`src/server/video_service.rs`，没有macOS平台条件，Windows与Linux构建天然使用同一逻辑。Mac `+141`已完成实机验证；Windows/Linux二进制在本轮PAD功能验收、代码提交并推送后使用各自构建环境生成，发布前必须回读三项状态和断开后抓屏归零。
- `+150`真机触摸点击和500毫秒滑动后，原生日志已确认`sourcePointer=true`且最终`mInputShown=true`，但过程中仍出现一次`IME visible=false→true`，肉眼可能看到短暂闪烁，不能作为最终完成。`+151`在代理激活和源屏指针保护期间把窗口模式从`STATE_ALWAYS_HIDDEN`切为`STATE_ALWAYS_VISIBLE`；近期源屏触摸导致Insets隐藏时立即恢复，不再叠加120毫秒分类和80毫秒恢复等待。用户主动关闭、停放代理时再切回`STATE_ALWAYS_HIDDEN`，避免键盘无法按意图收起。
- `+151`复测仍能捕获一次系统级`visible=false`，证明键盘宿主自身持续请求可见只能缩短恢复，不能阻止源屏触摸切换窗口焦点。`+152`在双屏键盘`opening/visible`期间把远程源Activity窗口设为`FLAG_NOT_FOCUSABLE`但不设`FLAG_NOT_TOUCHABLE`：触摸、滑动和鼠标MotionEvent继续进入远程画面，源窗口却不再夺走目标Display的IME焦点；键盘关闭、打开失败、Display移除、App后台或会话释放时统一清除该标志。该方案只在`sourceDisplayId != targetDisplayId`时启用，单屏回退不受影响。
- `+152`在`192.168.3.63:5555`完成真机闭环：收起工具栏后键盘自动在Display 2进入`visible`，Display 0右下角显示50%透明展开按钮；注入真实touchscreen点击和500毫秒滑动均进入Flutter，期间`IME insets visible=false`计数为0，结束后`mInputShown=true`。展开工具栏并点击键盘后状态依次为`closing→hidden`，日志确认`source window focusable=true`且`mShowRequested=false`，说明保持方案没有破坏主动关闭。
- 随后卸载原包并全新安装`+152`，清空当前PAD的KEMI最近访问、设置和客户端缓存，按真实首次启动从Display 0输入Mac ID进入密码框。日志只有`Defer keyboard proxy preparation on default display until authentication`，未创建代理Activity；密码框UI层实际回读测试文本`12KemiTest152`，证明首次主屏密码输入已恢复。测试密码未提交。
- `flutter analyze --no-pub`检查PAD远程页、共享桌面状态页和键盘状态模型无新增错误；`cargo check --locked --features flutter`成功，只有项目已有警告。固定签名arm64 APK为24,151,376字节，SHA-256为`a25794e296f11c61f92e876ccac489105a41f36d42c621bf10ef30ec5c0743a4`，包名`com.newlinksz.kemi.remote`、版本`1.4.49+152`、v1/v2签名有效，证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`；候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+152-candidate.apk`。
- `+153`进一步去掉Flutter `endFloat`默认保留的右侧和底部外边距。收起后的半透明展开按钮改为按Scaffold实际可绘制宽高减去按钮宽高计算坐标，右边和底边均为0；它贴紧应用显示区边缘，但不会侵入Android系统导航栏。收起前先读取键盘代理状态，只有明确为`hidden`才打开键盘；`opening/visible/closing`均只收起工具栏，不调用重开或`restartInput`，保留当前输入连接和中文组合态。
- `+153`固定签名arm64 Release构建成功，APK为24,147,427字节，SHA-256为`991a9e32d43d220371009321ac2d4795c0203a0c54ba950543ef91e6a9a3e12b`；包名`com.newlinksz.kemi.remote`、版本`1.4.49+153`、v1/v2签名有效，证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+153-candidate.apk`，并已覆盖安装到当前DHCP地址`192.168.3.62:5555`的同一台`huanglong` PAD，系统回读`versionCode=153`。

## 七十六、2026-08-04 KEMI快传跨平台客户端下载闭环规范（文档）

- 新增`KEMI-SEND-CROSS-PLATFORM-CLIENT-DISTRIBUTION.md`，以KEMI快传`kemi-send`为可直接执行的实例，把“客户端页面展示→四端构建→六个发布文件→Newlink Common固定name→六个plugData查询地址→两份清单→hbbc JSON→服务器独立部署→发现API反馈客户端→PAD本地HTTP→云端下载→云备份→日常升级”串成完整闭环。
- KEMI-SEND固定云端项目明确为`KEMI-Send-PAD/Windows/macOS/Linux/SHA256SUMS/release-manifest`，页面固定ID为`kemi-send`。文档给出可解析的manifest JSON、hbbc站点JSON、管理员上传顺序、交接模板、四个平台稳定路由、服务器检查命令和分角色验收清单。
- 规范区分“云后台固定name”与“实际上传文件名”：固定name永久不变，实际客户端文件建议带版本号，使四端上传中间态与旧manifest产生文件名不匹配，从而让hbbc继续使用上一完整缓存；新manifest最后上传后才整体切换。日常升级不再重新生成JSON或更新客户端URL，只有首次接入或平台结构变化才修改hbbc配置。
- 明确hbbc的安全边界：它检查元数据HTTPS和域名白名单、plugData文件名、MD5格式及manifest/SHA256SUMS一致性，但不下载四个大文件重新计算哈希；实际文件大小、MD5、SHA-256和平台签名必须由发布流程下载回读验证。对应hbbc源码说明和release部署说明同步纠正，release/server校验清单重新计算并全项通过。
- 本节只完成设计与运维规范，没有生成KEMI快传四端二进制，也没有把占位`resolve_enabled:false`切换为正式发布；必须收到并验证KEMI-SEND六个真实文件后才可启用`/kemi-send`下载路由。

## 七十五、2026-08-04 PAD hbbc HTTP 备用下载第二通道（PAD 1.4.49+147 候选）

- PAD“客户端”页保持两条互补下载链路。第一通道仍是进入页面后启动的 PAD 局域网 HTTP 服务，优先提供 PAD 已完成大小、SHA-256 和 MD5 校验的本地缓存；第二通道改为 hbbc 独立提供的固定 HTTP 地址，供 AP 隔离、访客网络或终端之间无法直接访问 PAD IP 时使用。
- 四个平台的固定备用地址统一为 `http://kemi-chat.newlinksz.com:21120/kemi-desk/download/{android|windows|macos|linux}`。PAD Flutter 页面保留备用地址、复制和二维码，PAD 本地下载网页中每个平台的云端路由使用同一组地址；不再使用已经废弃的 `/kemi/download/{平台}` 路径。
- hbbc 页面本身使用 HTTP，是为了保持当前服务器部署简单且不依赖 Nginx、证书或其他系统配置；平台下载路由由 hbbc 根据 JSON 配置解析后返回 302，真正的文件仍来自新智联云盘 HTTPS 地址。hbbc 与 `hbbs`、`hbbr`分别运行，备用下载异常不会影响远程控制信令和中继。
- 外部链接实施精确白名单：只允许 `http`、主机 `kemi-chat.newlinksz.com`、端口 `21120`及 `/kemi-desk/download/`路径进入按钮、二维码和本地网页，避免任意状态数据成为外部跳转。客户界面统一显示“备用版本清单”和“云端备用下载”，不再把内部应急元数据源直接写成 GitHub。
- 源码版本从 PAD 候选`1.4.48+140`提升为`1.4.49+142`，与已完成的 Mac `+141`区分。定向`flutter analyze`无问题，固定签名 arm64 Release 构建成功；APK为24,148,163字节，SHA-256为`721ca7fb8d2552e10379a56454d4f2b93e31c7a335899319cf9d4fface4913d0`，包名`com.newlinksz.kemi.remote`、版本`1.4.49+142`、仅含`arm64-v8a`、zipalign及v1/v2签名有效，固定证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。
- hbbc 四个平台固定路由从本机逐项回读均为`302 Found`，并分别跳转到新智联云盘当前的正确 HTTPS 文件。候选已覆盖安装到`192.168.3.63:5555`，系统回读`1.4.49+142`；真机进入“客户端”页后，本地第一通道为`http://192.168.3.63:8686`，四个平台切换后第二通道逐项生成正确的 hbbc 地址。从 Mac 访问该 PAD 的8686网页，也同时检出四条 hbbc 地址、“云端备用下载”和“固定备用地址”。候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+142-candidate.apk`；用户验收前不覆盖`BIN/release/KEMI-PAD.apk`和云端正式清单。
- `+142`真机截图暴露出展示问题：两个通道虽然都已生成，但原先按上下两张大卡排列，1920×1280设备首屏只完整显示PAD局域网通道，云端备用通道的按钮、地址和二维码必须向下滚动后才能看到，用户会合理地判断“第一项没有两个通道”。`+143`在PAD宽屏上改为同一行并排显示“通道一·PAD局域网下载”和“通道二·云端备用下载”，窄屏设备仍自动上下排列；每个通道保留各自地址、复制、二维码和适用网络说明。
- `+143`第一次真机回读发现PAD物理宽度1920像素，但Flutter布局使用约960逻辑像素，原1200逻辑像素断点没有触发，两个标题虽然明确却仍上下排列。`+144`把宽屏双列断点调整为840逻辑像素；必须以真机首屏截图同时出现左右两个通道为通过标准，不能只根据源码阈值推断。
- `+144`已覆盖安装到`192.168.3.63:5555`，真机首屏截图确认左半屏完整显示“通道一·PAD局域网下载”及`http://192.168.3.63:8686`，右半屏同时显示“通道二·云端备用下载”、四平台选择、打开按钮及`http://kemi-chat.newlinksz.com:21120/kemi-desk/download/windows`，无需滚动才能知道存在第二通道。APK为24,148,735字节，SHA-256为`5ccfeea76c913511eb0ab42b8719dfea47f88bdc7263e869063b8e706713eb81`，版本`1.4.49+144`、固定签名v1/v2有效；候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+144-candidate.apk`，正式release仍等待用户验收。
- 用户进一步明确“两个连接”属于浏览器访问PAD `8686`后看到的网页，不是PAD原生“客户端”页。`+145`撤销`+143/+144`原生页左右分栏，恢复原生页既有布局；8686网页顶部改为两个主入口：方式一显示当前PAD动态局域网地址并进入本地客户端列表，方式二固定打开`http://kemi-chat.newlinksz.com:21120/kemi-desk`云端完整下载页。各平台卡片恢复Newlink HTTPS实际文件的“云备份下载”，并明确标注它仅作为上面两种下载均失效情况下的备案。
- `+145`已覆盖安装到`192.168.3.63:5555`并从Mac Chrome真实访问`http://192.168.3.63:8686`截图验收：网页首屏左右显示两个主入口，第二入口实测返回HTTP 200；Windows、macOS、Linux、Android四项均生成“从PAD下载”和“云备份下载”，备份地址严格为当前`https://cdn.newlink-sz.com/...`，抽测Windows备份返回200及`application/x-msdownload`。APK为24,148,370字节，SHA-256为`b536881f6abec59112a5176227bfe56b0615c240557b8da63938997a4a1cc8fd`；候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+145-candidate.apk`，正式release仍等待用户验收。
- `+145`虽然功能存在，但实际视觉验收仍未满足最终要求：两个主入口各自有独立边框，“仅作为……”说明位于列表顶部而非备份按钮后，且每个平台继续显示冗余的备份实际地址和复制操作。`+146`把“同局域网下载”和“云端下载”合并进同一个完整边框，中间仅用分隔线区分；地址本身可点击，不再显示额外“打开备用地址”按钮。各平台只保留“从PAD下载”“云备份下载”，并在每一个云备份按钮后紧跟小字“仅作为上面两种下载均失效情况下的备案”，删除备份长地址和复制按钮。
- `+146`已覆盖安装到`192.168.3.63:5555`并完成真实Chrome宽屏、窄屏截图验收：宽屏两项共享一个外边框，窄屏在同一外框内改为上下排列；HTML回读为一个`channels`入口框、四个云备份按钮、四条逐项小字，冗余“复制地址/打开备用/应急云备份地址”为0。APK为24,147,638字节，SHA-256为`bb107ad08a2d43f4e96155c79717d0f0ea2059ace993c7def658f8dd0385b4db`，固定证书v1/v2签名有效；候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+146-candidate.apk`，正式release仍等待用户验收。
- 全局复核发现PAD原生“客户端”页仍残留“打开备用下载”按钮，虽然不属于8686网页，但会造成验收界面不一致。`+147`删除该按钮及不再使用的外部浏览器启动依赖；原生页保留固定地址、复制和二维码，8686网页的两个主入口与逐平台备份逻辑不变。
- `+147`已完成定向静态分析、固定签名Release构建及真机闭环。APK为24,146,773字节，SHA-256为`28424721e37893b30e51c2b83dacb188275cd99a47d37c6c85b7b4d2e9a85b6d`，包名`com.newlinksz.kemi.remote`、版本`1.4.49+147`、v1/v2签名有效，固定证书SHA-256仍为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。覆盖安装到`192.168.3.63:5555`后，真机原生页截图确认无“打开备用下载”按钮；运行中的8686页面回读为1个统一主入口外框、4个云备份按钮、4条逐项备案小字、0个冗余打开按钮。候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+147-candidate.apk`，正式release继续等待用户验收后再覆盖。
- 用户验收指出`+147`只闭环了8686网页，PAD原生“客户端”页的第一框仍只有局域网地址和一个二维码，主云端入口被错误拆到第二框；同时第二框把hbbc平台路由误称为云端备用，说明也没有使用约定原文。`+148`统一两层页面语义：原生第一框同时展示动态`http://PAD-IP:8686`和固定`http://kemi-chat.newlinksz.com:21120/kemi-desk`，各有独立地址、复制和二维码；第二框才是逐平台Newlink HTTPS“云备份下载”，说明严格统一为“仅作为上面两种下载均失效情况下的备案”。
- `+148`首次真机截图确认两个地址和两个二维码均已生成，但双栏使用`IntrinsicHeight`后，在PAD约960逻辑像素宽度下没有把二维码完整高度计入第一张卡片，二维码下半部溢出到“云备份下载”卡片。`+149`改为由两个下载通道的真实内容直接决定行高，并只保留固定高度的中间分隔线；交付标准增加“第一框背景完整包住两个二维码及文字，不与第二框重叠”。
- `+149`已覆盖安装到`192.168.3.63:5555`并完成上下两屏真机截图验收：第一框左侧完整显示动态`http://192.168.3.63:8686`及局域网二维码，右侧完整显示`http://kemi-chat.newlinksz.com:21120/kemi-desk`及云端二维码；第二框标题为“云备份下载”，逐字显示“仅作为上面两种下载均失效情况下的备案”，平台切换后展示的是`https://cdn.newlink-sz.com/...`当前实际备份文件。APK为24,147,303字节，SHA-256为`19a7e03dacede489980821f9c3abcc9c9691b50adf9d6b06e90b3ec21c0d3d27`，包名`com.newlinksz.kemi.remote`、版本`1.4.49+149`、固定证书v1/v2签名有效；候选归档为`BIN/KEMI-远程桌面-PAD-1.4.49+149-candidate.apk`。

## 七十四、2026-08-04 Mac 远控连接与真实抓屏状态可视化（macOS 1.4.48+141）

- Mac 主页底部原“就绪”只表示 ID/信令服务可用，不能代表有没有远控设备，也不能证明屏幕采集已经释放。本版把`就绪`、`连接`、`抓屏`统一为同一套紧凑圆点、圆角边框、字号、间距和点击反馈；绿色表示对应状态真实存在，灰色表示不存在。三项都可点击，`就绪`弹窗明确说明它不等于远控连接或抓屏。
- `连接`不使用页面、TCP socket或服务器在线状态推测，直接统计服务端`AUTHED_CONNS`中`AuthConnType::Remote`的已认证远控会话；文件传输等非远控连接不会误点亮。点击后会显示当前会话数，以及绿色/灰色各自含义。
- `抓屏`新增独立原子生命周期计数。只有显示器 capturer 与 encoder 都创建成功、视频服务正式进入取帧循环后才增加；摄像头服务不计入。远端断开会从视频服务删除订阅者，`ServiceTmpl::ok()`立即变为 false，循环退出时 RAII guard 必然递减计数并记录`screen capture stopped`；因此该灯描述的是抓屏循环真实存活状态，而不是“曾经创建过视频服务”。同时新增当前抓屏会话的真实抓帧次数：仅当`c.frame()`返回有效显示画面时加1，编码器在`WouldBlock`时重复发送上一帧不计数，从完全空闲进入新抓屏会话时重新从0开始。点击绿色“抓屏”会显示“已成功抓取N次画面”，这里明确不是屏幕数量。
- 两盏灯可以直接诊断异常：`连接灰 + 抓屏绿`表示远控会话已归零但屏幕采集仍未释放，必须按异常处理；`连接绿 + 抓屏灰`只允许在连接初始化的短暂阶段出现，持续存在表示采集器或编码器启动失败；正常空闲为两灯灰，正常远控为两灯绿。
- Mac UI每秒经现有主进程IPC同时读取远控会话数、抓屏循环数和有效抓帧次数。状态说明弹窗明确展示实时含义，不改变底栏高度。真机`192.168.3.63:5555`连接时三项均为绿色，Mac存在到PAD的直连TCP并持有抓屏休眠断言；PAD主屏回HOME而副屏仍显示远控画面时连接/抓屏保持绿色是正确状态，不再误判成断开。副屏真正退出远控后，直连TCP消失、连接和抓屏同时归灰、`PreventUserIdleDisplaySleep`归0，仅保留正常的ID服务器`21116`连接。
- 源码构建号从`1.4.48+140`提升为`1.4.48+141`。`cargo build --locked --features flutter --release`、`flutter build macos --release --no-pub`均成功；完整App回读`1.4.48 (141)`，主程序、service和`liblibrustdesk.dylib`均为arm64，固定本地测试证书深层签名校验通过。新版已安装到`/Applications/KEMI-远程桌面.app`，被替换的`1.4.48 (128)`旧App临时保存在`/private/tmp/KEMI-远程桌面-pre141.app`。

## 七十三、2026-08-04 本地 HTTP 与 hbbc 云端下载互补（PAD 1.4.48+140 候选）

> 本节记录最初的HTTPS/Nginx候选设计；实际部署已改为hbbc自身提供HTTP服务，当前有效地址与验收结论以第七十五节为准。

- PAD“客户端”页继续在进入页面时启动`http://PAD-IP:8688`，用于同一局域网且设备可以互访时下载PAD已校验并缓存的四端文件；离开页面仍关闭本地HTTP服务。该路径不依赖云端页面，适合现场高速分发。
- 新增固定云端备用路径`https://kemi-chat.newlinksz.com/kemi/download/{android|windows|macos|linux}`。平台选择、打开浏览器、复制地址和二维码都使用固定hbbc路由，不再把每次上传后变化的`cdn.newlink-sz.com`长地址编码进客户端。
- hbbc每600秒读取JSON并解析Newlink六个固定资源，完成manifest、SHA256SUMS、文件名、MD5、HTTPS及域名白名单交叉校验后，才把固定路由302到当前真实文件。云盘更新只需维护六项资源和服务器JSON；无需更新PAD二维码或为了换URL重新打包APK。
- 本地下载和云端下载互不替代：局域网客户端优先使用PAD地址；AP隔离、访客网络或设备互相不通时使用云端HTTPS。hbbc异常不会影响`hbbs/hbbr`远控服务，本地8688也仍可使用已缓存文件。
- 客户端状态新增`cloudPortalUrl`，保留原`cloudUrl`作为PAD后台同步真实资源的内部信息。UI只接受`kemi-chat.newlinksz.com/kemi/download/`白名单固定地址，避免任意状态值进入二维码或外部浏览器。
- `client_download_page.dart`定向`flutter analyze`无问题；固定签名arm64 Release完整构建通过。候选APK为24,149,095字节，SHA-256为`c74c94c03a6811e898f8deb879daed4fd838dbb0fbc173c1156ec25b371f8fe5`，包名`com.newlinksz.kemi.remote`，版本`1.4.48+140`，v1/v2签名有效，证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。版本化文件为`BIN/KEMI-远程桌面-PAD-1.4.48+140-candidate.apk`；尚未覆盖固定`BIN/release/KEMI-PAD.apk`，需在hbbc部署后真机闭环再晋升。

## 七十二、2026-08-03 文件传输窗口跨屏独立显示（PAD 1.4.48+138）

- 原实现从远控页调用 `showGeneralDialog`，文件传输浮窗属于当前远控 Activity 的 Flutter 路由，因此只能覆盖在远控画面上；Flutter 单个 Activity 无法把同一路由绘制到另一块物理屏幕，这就是此前“文件窗口叠在当前屏”的根本原因。
- 双屏 Android 现在由当前远控 Activity 请求原生 `FileTransferActivity`，并通过 `ActivityOptions.launchDisplayId` 启动到对面屏：远控位于 Display 0 时文件传输去当前开启的副屏，远控位于副屏时文件传输回 Display 0。目标屏不存在或系统拒绝跨屏启动时，保留原 60% 同屏浮窗作为单屏兼容路径。
- `FileTransferActivity` 使用独立 FlutterEngine 和专用入口 `crossDisplayFileTransferMain`，但复用现有 `FileManagerPage` 界面、传输记录和目录逻辑；文件传输建立独立 UUID/FFI 会话，远控视频 Session 不关闭、不重连，两个窗口的生命周期互不覆盖。
- 关闭跨屏文件窗口时严格按“停止任务回调 → 关闭 FileModel → 关闭文件 FFI → 结束 Activity”执行，并用状态位阻止 `dispose` 重复关闭。打开前先释放跨屏键盘代理，避免键盘窗口、文件窗口争夺对面屏焦点。
- 真机 `192.168.3.63:5555` 双向验证通过：Display 0 远控可将文件窗口打开到 Display 2，Display 2 远控也可将文件窗口打开到 Display 0；Activity 状态和两屏截图确认没有叠加。实测浏览 Mac 远端目录并下载文件成功，关闭后日志出现独立文件模型 `closed`，副屏 `MainActivity` 仍保持 RESUMED、远程画面继续显示。
- 版本升级为 `1.4.48+138`，源码冻结提交为 `9c251b1dc5a5b8c614b68492fc608ddaca3122b6`。固定签名 Release APK 为 24,121,777 字节，SHA-256 为 `dad1908b6b8ca90081d7a0be95729628eb543382b2dca3e5df60ea4c6b4c0e87`；包名 `com.newlinksz.kemi.remote`、仅 `arm64-v8a`，v1/v2 签名有效，固定证书 SHA-256 保持 `8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。

## 七十一、2026-08-03 跨屏键盘与物理鼠标最终闭环（PAD 1.4.48+137）

- 最终需求是：软键盘显示在远控画面对面屏幕后，用户在远控画面使用物理鼠标左键、移动和右键都不能关闭或闪动键盘；只有键盘按钮、输入法收起键、HOME、会话结束等明确动作可以关闭。`+130`只在IME已经隐藏后延迟恢复，避免了永久关闭，却仍会出现一次肉眼可见的“隐藏→重开”；因此原先把`+130`写成闭环通过是不准确的，本节以`+137`结论取代。
- 根因分为两层：厂商Android 12双屏系统在另一显示收到鼠标点击时会临时撤销IME host焦点；而Insets约20～40ms内已进入隐藏动画，旧代码到120ms后才根据鼠标时间戳恢复，逻辑状态虽然仍是`visible`，画面仍然会闪。最终方案不再等待隐藏发生，而是在物理左键手势开始时提前保持键盘代理task、EditText焦点与现有输入连接。
- `MainActivity`和`RemoteActivity`在事件进入Flutter前识别当前源显示的`SOURCE_MOUSE`。PAD实测和ADB注入进一步证明部分`ACTION_DOWN`的`buttonState/actionButton`均为0，因此`+137`将“`ACTION_DOWN`且未明确标记为secondary”视为主键按下；`ACTION_BUTTON_PRESS + BUTTON_PRIMARY`继续按标准路径识别。事件必须同时满足Manager处于`opening/visible`且显示ID等于`sourceDisplayId`，另一显示和触摸事件不会启动保护。
- `KeyboardProxyActivity`只在左键手势期间启用短时IME守护：按下后最多650ms、每48ms维持既有task和焦点；收到抬起后缩短为180ms。守护仅调用`showSoftInput()`保持当前输入连接，不调用`restartInput()`，避免破坏中文组合态。退出、隐藏、停放和销毁都会移除Runnable，防止跨会话残留。
- 右键保持完全独立。PAD右键可能以`MotionEvent.BUTTON_SECONDARY`上报，也可能被系统转换为鼠标来源的`KEYCODE_BACK`；两条路径都由`PhysicalMouseRightButtonForwarder`转成远端`right down/up`。一旦识别secondary，立即取消左键守护；避免`+134`中对所有鼠标事件反复`moveTaskToFront()`打断右键抬起，造成远端长期停留在按下状态。
- 调试版本结论：`+130`延迟恢复但仍闪；`+131/+132`仅调整窗口/焦点标志未解决系统IME隐藏；`+133/+134`扩大主动抢焦范围，其中`+134`引入右键卡住回归；`+135`撤销全事件抢焦并恢复右键完整down/up，但左键仍隐藏后恢复；`+136`实现按键选择性守护并通过实体鼠标初测；`+137`补齐无按钮位的主键DOWN兼容，作为最终验收版本。
- 真机`192.168.3.63:5555`分别覆盖了远控在Display 0、键盘在Display 2，以及远控在Display 2、键盘在Display 0的方向。最终日志中左键产生`onPointDownImage`且IME在`visible`后不再出现`IME insets visible=false`；右键连续产生完整的`on_physical_mouse_button right down/up`，用户现场确认左键不影响键盘、右键功能正常，输入提交和退格仍可继续使用。
- 同批UI细节：桌面主页和PAD共享屏幕页的“一次性密码”后增加小字“（推荐使用固定密码）”；Windows/桌面连接页移除“如果需要更快连接速度，可以选择自建服务器”；PAD客户端下载弹窗根据实际状态显示“新智联云盘”或“GitHub备用源”，不再把Newlink HTTPS下载误写成GitHub。
- 本地源码冻结提交为`a601b988e57e117c6e2761ff217877749f6276d3`。`+137`固定签名APK为24,115,046字节，SHA-256为`15675062daa80fb9fbbca8a017c7775ad524009f329178172fdbbe6e41d1a36f`，仅含`arm64-v8a`，v1/v2签名有效，固定证书SHA-256保持`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。版本化归档为`BIN/KEMI-远程桌面-PAD-1.4.48+137-release.apk`，固定上传文件为`BIN/release/KEMI-PAD.apk`；清单批次明确标记`1.4.48+137-pad-hotfix`，其他三端仍保留各自现有版本。

## 七十、2026-08-03 服务器状态、会话收尾、Mac 设置保护与账户入口收敛（候选 1.4.48+129）

- KEMI 后端使用开源 `hbbs/hbbr 1.1.16`，不提供 Pro 账户和 `21114` API。此前账户 UI 依赖运行时 `disable-account`，桌面首帧若早于硬配置加载会偶发显示“登录”。四端共用的 Flutter FFI 入口现固定报告账户功能禁用，Mac、Windows、Linux、PAD 的账户页、登录按钮和依赖账户的备注入口保持一致隐藏；不删除远控目标系统的 OS 账号/密码输入功能。
- PAD 首页五个 tab 的底部导航上方新增 24px 状态条。单个自建服务器也会进行 TCP 探测，Android 最多每 10 秒刷新一次并保留移动端结果；绿色表示 ID 服务器可达、可以发起或接收连接，不代表视频首帧已经成功，视频异常仍按会话和 Mac 采集日志诊断。
- 双屏断开顺序调整为“关闭完整 Rust peer/socket → 通知原生断开 → 退出 Flutter route → `RemoteActivity.onDestroy` reset”。移动端显式断开按 session 找到并移除完整 peer，解决 UI 已回主页但旧 TCP 会话仍存活；主屏在副屏 MethodChannel 已异常丢失时新增状态清理兜底，避免下一次连接继承旧 sessionId。
- Mac 设置页不再仅凭可能延迟归零的 `videoConnCount` 永久覆盖黑色蒙层。远端输入时显示“远程会话进行中，远端不可修改本机设置”；鼠标进入、移动或按下会重新判断输入来源，因此远端仍不能修改，本机物理鼠标可以解除。等待 120ms 的检查改为真正 await，避免异步 Timer 在页面生命周期外回写状态。
- macOS 视频构建修复 Cargo 重建指令缺少 `cargo:` 前缀导致 libvpx 头文件更新后继续使用旧 ABI 绑定的问题；Android arm64 脚本固定使用 NDK `llvm-ar/llvm-ranlib`，避免 macOS `/usr/bin/ar` 生成空 libsodium archive。移动端不再解析桌面专用 `portable-pty`。
- 2026-08-03 一次“服务器绿色但无画面”并非服务器故障：磁盘 App 在 11:56 被覆盖，10:26 启动的旧进程仍在运行，CoreGraphics 采集流持续返回 null。12:08 完整退出旧进程并启动新 App 后，PAD 连续两次断开重连均正常出画面。Mac 部署流程新增硬性要求：先结束旧进程，再替换 App、校验签名并启动，禁止运行中覆盖。
- Review 剔除了仅由本机 CocoaPods 版本差异造成的 `Podfile.lock` checksum 噪声，并补齐副屏通道为空时的异常清理。完整 18 个源码项、子模块默认服务器改动、风险等级和发布门禁见 `kemi-docs/LOCAL-CHANGE-REVIEW.md`。
- 真机首帧与断开复核还发现异步 `sessionClose` 等待期间不应继续复用旧 route context；移动端确认断开现直接通过根 Navigator 返回首 route，避免 native 会话已经关闭但主屏仍停在最后一帧工具栏。主屏转发副屏虚拟键时同步保留 `down/up`，不再把按下和释放各转换成一次完整按键。
- 候选构建号提升到 `1.4.48+129`并冻结源码为`eef2e0c0222c0701c3fea6137907d933c8da8921`；该提交已推送`backup/master`。PAD固定签名Release为24,115,306字节，SHA-256为`0f64c0b58160e061f22bc7e11aab1ef1bd7c9a6fbb41b9d5d6c892d2ded1c6d3`；macOS arm64 ZIP为25,936,352字节，SHA-256为`f8a2b9b78cd957ad6380e7e71658ba6d69668bc35701bdc2e4a26847a5a614f6`，解包后固定本地证书深层签名和`1.4.48 (129)`版本均通过。
- GitHub focused run `30795669077`在同一源码上一次通过bridge、Windows x64、Linux x86_64、AppImage与最终manifest，发布tag为`kemi-client-eef2e0c0222c0701c3fea6137907d933c8da8921`。Windows EXE为22,641,152字节、PE32+ x86-64，SHA-256为`ab1c63e193f706605152218523d3ac331cd1d0e3200869bb89472746f3cae24f`；Linux AppImage为82,979,320字节、x86-64 AppImage v2，SHA-256为`dfa36bd8ad8369b0239228e8c7746bb04bd8157f9d3884187af53b86a744cfc2`。真实Windows/Linux机器上的GUI、远控与文件传输仍属于发布后跨平台验收，不把云端编译成功等同于功能实机通过。
- `BIN/release`六个固定文件已整体对齐到该批次并重新生成SHA清单和manifest；用户只需登录Newlink云端`Common`项目，依次覆盖PAD、macOS、Windows、Linux、`SHA256SUMS`，最后覆盖`release-manifest`。上传前客户端不会看到本批次；上传后必须回读六个固定HTTPS接口并验证PAD自动同步与局域网HTTP下载哈希。

## 六十九、2026-08-02 新服务器四端重编译与PAD自动更新验证（全端 1.4.48 / build 125）

- 本批次冻结源码为`a5ff428b53f93a78ec0b02d794ecbbe6fd629bd5`，远端`backup/master`已核对同哈希。四端统一使用ID服务器`kemi-chat.newlinksz.com`、IP`119.96.24.110`和公钥`gGsFBYJT34y1PIRgE+kBFOIH+MDkOadi4Or6tlwQ3jE=`。Flutter启动仍先写公钥再写服务器；云端新clone在checkout后应用受版本控制的`.github/patches/kemi_hbb_common_server.diff`，解决KEMI默认值此前只存在于本机dirty子模块、Windows/Linux云构建可能退回公共RustDesk默认服务器的风险。
- macOS完成Rust核心和Flutter App全量本地重编译，补齐AOM、FFmpeg、libvpx、libyuv、Opus等arm64依赖后，`cargo build --locked --features flutter --release`及`flutter build macos --release --no-pub`成功。App为`1.4.48 (125)`、三个主二进制均为arm64，固定本地测试证书深层签名通过；App内Rust动态库逐字检出服务器和公钥。安装到`/Applications/KEMI-远程桌面.app`后进程实际建立`192.168.3.51 → 119.96.24.110:21116 ESTABLISHED`连接，本地配置回读地址与公钥一致。`KEMI-macOS.zip`为25,922,525字节，SHA-256为`2bc730e72f54db28226e4da6bace5d0defe70af302136cb8e338ae708c755e12`；该包是固定本地测试签名、未Apple公证，不得冒充正式外发Developer ID包。
- GitHub focused run `30731531135`在同一候选提交上一次成功完成default bridge、TopMostWindow x64、Windows x64、Linux x86_64、AppImage和最终manifest；候选prerelease为`kemi-client-a5ff428b53f93a78ec0b02d794ecbbe6fd629bd5`。Windows EXE为22,642,176字节、PE32+ x86-64，SHA-256为`ba5e6dd0ede56f369c7096aa8aea6b5b98598c8c7388591a15f3eff91c9358cb`；Linux AppImage为77,494,776字节、ELF x86-64且`AI 02`魔数正确，SHA-256为`66affa2de063f94fa50422dc8e4ee02d63ae2a9290c274dd4e4b347b97a89d4a`。两者均通过云端SHA清单回读，但真实Windows/Linux机器的GUI启动、远控和文件传输仍待对应系统验收。
- PAD继续使用已安装的`1.4.48+125`固定签名Release：系统回读`versionCode=125`；APK Flutter AOT库检出同一服务器和公钥，v1/v2签名有效，固定证书SHA-256为`8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2`。APK为26,539,559字节，SHA-256为`5f7eb1315ad411fe9d0d5382c64bd575281a874982b9fa056cbd88092120755f`。
- PAD“客户端”页真机验证：进入页面立即解析Newlink六个固定HTTPS接口，日志在约0.2秒内取得四个平台CDN地址并生成四目标manifest。为排除“只读已有缓存”，临时移走旧云端Windows缓存后重新进入页面，程序自动重新下载22,637,056字节文件并恢复绿色就绪；新文件与备份SHA-256均为`9cc13f6780a39388d590b2f7dc575b1e42712da630f7ae801947d4465867d6ad`，测试备份随后清理。`JobScheduler`真机回读确认任务每6小时、仅非计费网络、设备空闲、重启后保留；页面代码进入即刷新、保持页面时每5分钟刷新，离页只关闭HTTP服务，不取消已经开始的同步。
- 上述PAD测试命中的是Newlink云端当前仍在发布的旧`1.4.46`清单，证明自动解析、下载、校验和恢复流程有效；本次`1.4.48`六文件只有用户按顺序上传到`Common`项目后才会被已安装PAD发现。上传前不能宣称PAD已经下载到本批次；上传后应最后覆盖`release-manifest`，再由开发流程回读六个固定接口并验证PAD缓存与局域网HTTP下载哈希。
- `/Users/newlink/kemi/RustDesk/BIN/release`已严格整理为六个固定文件：`KEMI-PAD.apk`、`KEMI-macOS.zip`、`KEMI-Windows.exe`、`KEMI-Linux.AppImage`、`SHA256SUMS.txt`和`release-manifest.json`。清单批次为`1.4.48+125`并绑定上述候选commit/run；四个SHA-256逐项校验、清单大小核对和六文件数量检查全部通过。`BIN/`根目录另存四端带真实版本号的不可变归档，用户只需上传`release/`中的六个固定名称。

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
