# KEMI远程办公第一阶段研发总结与封版归档

> 封版日期：2026-08-08
>
> 客户端封版提交：`9b4d8c58536d186cce53aa4d439d3ca376c9db1e`
>
> GitHub备份：`caucy2026/rust-desk` 的 `master` 分支
>
> PAD正式版本：`1.4.78+185`
>
> 归档性质：客户端第一阶段功能和本地发布制品封版；不代表Newlink云盘已经完成上传，也不代表服务端第二阶段账号平台已经封版。

## 1. 文档用途与事实优先级

本文是第一阶段结束后的总入口，用于回答四个问题：项目在哪里、已经做成什么、正式包和源码
如何对应、下一阶段开发不能破坏什么。

发生冲突时按下面的顺序判断当前事实：

1. `BIN/release/release-manifest.json`和真实文件哈希决定当前本地发布制品；
2. 本文决定第一阶段功能边界、风险和下一阶段起点；
3. `LOCAL-CHANGE-REVIEW.md`、`client-distribution.md`及各专项文档决定模块细节；
4. `CHANGELOG-KEMI.md`用于追溯过程；
5. `SESSION-HANDOFF.md`、历史章节和上游RustDesk文档中的旧版本数字只作历史参考。

不得因为旧文档仍出现`1.4.46`、`1.4.49`或`1.4.62`，就覆盖本文确认的第一阶段封版事实。

## 2. 项目目标与第一阶段范围

KEMI远程办公基于RustDesk开源客户端和开源服务端定制，主要面向KEMI双屏PAD，同时提供
macOS、Windows和Linux客户端。第一阶段目标是形成一套可实际使用、可追溯发布、可自建服务、
可在局域网或云端分发客户端的远程办公基础产品。

第一阶段已经覆盖：

- KEMI品牌、固定服务器和固定公钥；
- PAD单屏/双屏远程控制、键盘、触摸和物理鼠标；
- 双向文件传输、传输记录、本地文件分享和APK安装；
- PAD资源监控、连接记录和VP9硬件解码；
- macOS远控权限、开机自启、状态/抓屏诊断和Developer ID正式发布；
- Windows/Linux同源码客户端候选；
- `hbbs + hbbr + hbbc`自建服务架构；
- PAD局域网HTTP分发、hbbc云端下载页和Newlink HTTPS资源体系；
- Android固定包名/签名、macOS签名/公证、四端清单和GitHub备份规范。

账号、微信登录、捐赠等级、使用计时和通用App渠道后台属于第二阶段服务端工作，不属于本次
客户端封版完成项。

## 3. 唯一工作区与仓库边界

```text
/Users/newlink/kemi/RustDesk/
├── client/      KEMI客户端唯一源码仓库
├── server/      hbbs、hbbr、hbbc服务端独立源码仓库
├── BIN/         本地已验证制品归档，不是Git仓库
│   ├── release/ 固定文件名的云盘上传区
│   └── server/  服务端部署包归档
├── signing/     Android等本机受控签名材料，不进入Git
└── rustdesk/    历史残留目录，不是当前客户端源码入口
```

### 3.1 客户端仓库

```text
/Users/newlink/kemi/RustDesk/client
```

| 远端 | 地址 | 规则 |
|---|---|---|
| `origin` | `git@github.com:rustdesk/rustdesk.git` | 官方上游，只拉取参考，禁止推送KEMI代码 |
| `backup` | `git@github.com:caucy2026/rust-desk.git` | KEMI备份；正式候选和封版推`master` |

第一阶段客户端封版提交已经推送并回读：

```text
9b4d8c58536d186cce53aa4d439d3ca376c9db1e
```

### 3.2 服务端仓库

```text
/Users/newlink/kemi/RustDesk/server
```

服务端是独立Git仓库，不能跟客户端一起`git add`或一起推送。本文编写时，服务端存在hbbc
下载站点、账号平台原型、安装脚本和OSS构建补丁等未提交改动，因此服务端尚未达到与客户端
相同的第一阶段Git封版状态。后续必须在服务端仓库单独审计、构建、部署验证和备份，不能用
客户端提交号证明服务端已经归档。

### 3.3 BIN的职责

- `BIN/`根目录保存带版本号、不可变的历史归档；
- `BIN/release/`只保存当前准备上传Newlink云盘的固定名称文件；
- `BIN`不进入Git，Git提交不能代替二进制归档；
- 制品必须用版本、大小、SHA-256、签名状态和来源commit建立对应关系；
- 禁止用新文件名包装旧二进制。

## 4. 客户端架构

```text
Flutter界面
  ├── mobile/：PAD主页、远控、文件传输、客户端分发
  ├── desktop/：macOS/Windows/Linux主页、权限和连接管理
  └── models/common：会话、连接记录、资源、文件和共享逻辑
          ↓ MethodChannel / FFI
Android Kotlin / macOS原生桥接
          ↓
Rust核心
  ├── rendezvous：ID注册、打洞和中继协商
  ├── client/server：远控会话、输入、文件和音视频
  ├── scrap：屏幕采集与编解码
  └── hbb_common：服务器、公钥、协议和共享配置
          ↓
自建hbbs / hbbr
```

关键目录：

| 目录 | 用途 |
|---|---|
| `flutter/lib/mobile/` | Android/PAD界面和交互 |
| `flutter/lib/desktop/` | macOS、Windows、Linux界面 |
| `flutter/android/app/src/main/kotlin/` | Android双屏、键盘、文件窗口、鼠标、HTTP服务和升级 |
| `src/` | Rust客户端、被控端、网络、输入和会话核心 |
| `libs/scrap/` | 抓屏、编码与Android MediaCodec |
| `libs/hbb_common/` | 默认服务器、公钥和协议共享层 |
| `kemi-docs/` | KEMI当前设计、验收和发布文档 |

## 5. 自建服务架构

| 服务 | 端口 | 作用 | 边界 |
|---|---:|---|---|
| `hbbs` | 21115、21116 TCP/UDP等 | ID注册、在线状态、NAT测试和打洞协商 | 不等于账号后台 |
| `hbbr` | 21117 TCP等 | P2P失败后的流量中继 | 会消耗服务器带宽 |
| `hbbc` | 21120 TCP | 多项目下载页、资源解析、管理原型 | 独立进程，不影响hbbs/hbbr |

当前客户端固定配置：

```text
服务器：kemi-chat.newlinksz.com
服务器IP：119.96.24.110
公钥：gGsFBYJT34y1PIRgE+kBFOIH+MDkOadi4Or6tlwQ3jE=
```

当前使用开源自建`hbbs/hbbr`，没有RustDesk官方账号管理和`21114` Pro API。客户端不得显示
无效登录入口，也不得把“就绪”解释为已登录账号。

hbbc当前下载入口为HTTP，资源元数据和实际文件仍使用Newlink HTTPS。`/kemi-desk`为KEMI
远程办公下载页，`/kemi-send`为配置驱动的KEMI快传站点。管理令牌不得通过不可信公网HTTP
明文传输；管理端正式使用时应放在HTTPS反向代理、VPN或SSH隧道之后。

## 6. 第一阶段正式版本与制品

本地发布批次：

```text
1.4.78+185-pad-phase-1-final
```

| 平台 | 版本 | 架构 | 固定文件 | SHA-256 | 发布状态 |
|---|---|---|---|---|---|
| PAD/Android | `1.4.78+185` | arm64-v8a | `KEMI-PAD.apk` | `f52f72dbe4a1673ad8462335acab81de800d3ef649451a3e7e3df408926e534e` | Newlink固定签名 |
| macOS | `1.4.75+182` | arm64 | `KEMI-macOS.zip` | `a0b51eeaca4284fc171cbe39d5cc227938d450c3363b8631b61c44233da2b206` | Developer ID、Apple公证、staple完成 |
| Windows | `1.4.75` | x86_64 | `KEMI-Windows.exe` | `84d78eec4c8e78afd55691c53e73ecb0ed14a1dbb4797e6c15afac40af0f41d9` | GitHub构建，未做Authenticode签名 |
| Linux | `1.4.75` | x86_64 | `KEMI-Linux.AppImage` | `19eb461779aff801d54667eb6525f30df8fb80c2ab28278d50b4e41a2ac05e53` | GitHub构建，未做发行签名 |

PAD带版本归档：

```text
BIN/KEMI-远程办公-PAD-1.4.78+185-release.apk
```

Android正式身份：

```text
applicationId：com.newlinksz.kemi.remote
versionName：1.4.78
versionCode：185
签名证书SHA-256：8546d03e51d09dfa17dbcf432f84bccf74bd2d9fde1cff981ff202f8871871a2
```

macOS正式身份：

```text
Bundle ID：com.newlinksz.kemi.remote
签名：Developer ID Application: zhen ji (26T5WV4GLP)
公证提交：b2746a22-151d-42c5-9bad-9876a936a083
结果：Accepted，票据已装订
```

当前`release-manifest.json`仍为`pending-manual-upload`。这表示本地六文件已经对齐，不表示
Newlink云盘已经发布1.4.78。管理员仍需按PAD、SHA256SUMS、最后release-manifest的顺序上传，
再回读固定接口并完成PAD远程升级验收。

## 7. 已完成的产品能力

### 7.1 PAD产品界面

- 应用、安装器和权限页统一显示`KEMI远程办公`；
- 顶栏显示`KEMI远程办公 v1.4.78`，版本来自实际APK；
- 底部服务器绿色状态统一显示“就绪”；
- 未就绪时保留真实的连接中/离线提示和“重连”，不能伪装成绿色；
- 文件传输从“更多”独立到操作栏；按钮打开时高亮，再次点击关闭；
- 最近访问、收藏、已发现、地址簿、可访问设备和连接记录形成主页功能区。

### 7.2 单屏与双屏

双屏能力只在存在有效、点亮、带`FLAG_PRESENTATION`的非默认显示器时成立。单屏设备的：

- 远程ID数字键盘；
- 连接密码键盘；
- 远控会话键盘；
- 文件传输窗口；

全部留在当前屏幕。真实双屏PAD继续将键盘和工具窗口放到对面屏。禁止再次使用硬编码
Display 2作为唯一判断，也禁止使用当前Android SDK不可稳定访问的`Display.TYPE_EXTERNAL`。

### 7.3 跨屏键盘

- 双屏PAD远控画面在副屏时，键盘可以在主屏打开；
- 主屏HOME后代理进入可恢复状态，副屏再次点击可重新打开；
- 鼠标左键和触摸远控画面不再自动关闭用户主动打开的键盘；
- 远程ID使用数字模式，密码使用独立本地密码会话，认证完成后才切换远控输入；
- 源Activity不得重新设置`FLAG_NOT_FOCUSABLE`，否则会触发InputDispatcher ANR、键盘或右键失效；
- 文件传输关闭后，单屏不得预创建多余键盘代理。

完整状态机以`cross-display-keyboard.md`为准。

### 7.4 触摸与物理鼠标

- 单指点击对应远端左键；
- 双指共同、同向纵向移动对应滚轮；一指按住、另一指移动不应误触发滚轮；
- 触摸模式和鼠标模式保留各自移动语义；
- 外接鼠标右键兼容`BUTTON_SECONDARY`和鼠标来源`KEYCODE_BACK`；
- 右键只在当前显示远控画面的Activity生效，不能影响另一屏主页或其他App；
- 丢失release事件时进行状态自校准，避免远端右键卡住直到系统超时。

### 7.5 显示和解码

- 支持适应窗口、原始尺寸和全屏等显示方式；
- 原始尺寸按远端物理像素点对点居中，不能把1920×1080内容错误拉伸到1920×1280；
- Android接入MediaCodec VP9硬解，运行时按真实stride、crop和颜色格式处理；
- 硬解失败必须自动回退软件解码，不能以黑屏换低CPU；
- 资源窗口显示CPU、内存、编码/解码后端和连接信息，CPU超过100%表示多核累计口径。

### 7.6 文件传输

- 文件传输使用独立FFI Session，与远控视频并行；
- 双屏PAD使用独立非模态窗口，窗口外桌面仍可操作；单屏回退当前页浮窗；
- 左右双栏支持双向浏览和传输，不要求反复选择方向；
- 对方目录优先恢复上一次存在的位置，不存在时回退初始目录；
- 传输记录按方向、源和目标去重，跨进程、跨开机保存，可“再次传输”；
- 源或目标不存在时在原位置显示错误；
- 左侧本地普通文件可调用系统分享，APK可调用系统安装器，文件夹保持原逻辑；
- 只允许删除成功传回PAD并登记授权的本地文件或目录；目录可以递归删除，但不能扩大到父目录、PAD原有目录或远端文件系统；
- Android目标空间可计算时，文件超过目标剩余空间一半则禁止传输；Mac侧难以可靠计算时不强行使用该限制；
- Android Download列表受分区存储影响，受控设备需正确授予“所有文件访问”，不能把权限缺失误判为文件不存在。

### 7.7 连接、P2P和中继

- 四端默认使用KEMI服务器和公钥；
- “就绪”只表示ID/信令服务器已连接，不代表已有远程连接，也不代表正在抓屏；
- 连接、抓屏和就绪是三个独立状态；远程断开后必须停止抓屏并停止增加抓屏次数；
- KEMI自有双屏OEM设备可以使用完整能力；其他Android安装默认P2P-only，不允许客户端伪造本地标记开放中继；
- UDP/TCP直连由NAT能力和协议协商决定，同一局域网走TCP直连并不等于失败；
- 对称NAT、运营商CGNAT、多级路由没有公网映射时，代码不能保证P2P成功；
- 固定21118映射只有在最外层公网网关真实可达并向对端发布正确候选时才有意义，不能把“调用UPnP成功”当成“公网已打通”。

现场结论和实验回退记录见`p2p-field-failure-cases-2026-08-06-07.md`。

### 7.8 macOS

- 远控必需权限收敛为屏幕录制和辅助功能；输入监控不是PAD点击控制Mac的必需条件；
- 授权入口保持可见，每项独立申请，系统窗口和KEMI窗口恢复到前台；
- 固定Bundle ID和Developer ID身份，避免每次构建后TCC看似授权、实际失效；
- 开机自启使用`SMAppService.mainApp`，UI统一显示“开机自启”；
- 主页默认显示主页和设置，不再依赖点击密码后才出现；
- 设置蒙板只在远程会话真实进行时出现，并说明远端不可修改本机设置；本机操作不能被错误永久锁住；
- 就绪、连接、抓屏三个指示独立，抓屏状态可查看累计帧次数；
- 远程断开后不得继续抓屏，避免锁屏过夜持续占用WindowServer、显示链和电源断言。

### 7.9 客户端分发

- PAD“客户端”页进入时启动本地HTTP服务，离开时关闭；
- 页面显示真实Wi-Fi、动态局域网地址和二维码；浏览器输入与扫码二选一；
- 本地下载和hbbc云端下载互补；云备份只在前两种失效时使用；
- PAD开机空闲或进入页面时读取Newlink固定HTTPS接口，解析动态CDN地址并校验manifest、SHA和MD5；
- 大文件不递归打入PAD APK；本机版本一致时可直接复用已安装APK，不会出现APK无限包含自身；
- 云端版本严格高于本机且文件校验通过时才显示更新；安装前可选择备份当前APK，无论用户是否选择备份都进入升级流程；
- 上传顺序必须最后更新manifest，防止客户端看到半批次。

## 8. 构建与发布强制门禁

### 8.1 项目独立Flutter

唯一允许的Flutter：

```text
/Users/newlink/kemi/RustDesk/client/.toolchains/flutter/bin/flutter
Flutter 3.24.5 / Dart 3.5.4
```

禁止使用全局`flutter`、其他项目SDK或PATH中偶然找到的工具。升级Flutter必须独立分支完成
四端回归后再切换。

### 8.2 Android正式包

Android不能只执行一个看似成功的Gradle命令。当前发布链必须按顺序完成：

1. 同步`Cargo.toml`、`Cargo.lock`、`src/version.rs`和`flutter/pubspec.yaml`；
2. 执行`flutter/ndk_arm64.sh`构建ARM64 Rust核心；
3. 把`target/aarch64-linux-android/release/liblibrustdesk.so`复制到
   `flutter/android/app/src/main/jniLibs/arm64-v8a/librustdesk.so`；
4. 比较复制前后哈希，禁止新Flutter外壳打入旧Rust核心；
5. 使用项目Flutter执行`flutter build apk --release --target-platform android-arm64 --no-pub`；
6. 从最终APK内部回读`versionName/versionCode`，检查只有arm64-v8a；
7. 验证zipalign、v1/v2固定签名、证书指纹、包内Rust版本、大小和SHA-256；
8. 全部通过后才覆盖`BIN/release/KEMI-PAD.apk`并生成带版本归档；
9. 更新SHA256SUMS和manifest，最后提交并回读GitHub。

旧APK留在`build/`不代表本次构建成功。构建命令退出码、APK内部版本和生成时间必须同时通过。
直接运行Gradle可能混入armeabi-v7a/x86_64 Flutter库，不能作为PAD纯arm64正式包。

### 8.3 Android签名

- keystore固定为受控的`kemi-release-2026.p12`；
- 密码只从macOS钥匙串注入环境变量；
- release缺少任一签名变量必须失败，禁止回退debug签名；
- 不得重新生成同名密钥；密钥丢失会导致已安装用户无法覆盖升级。

详细流程见`android-release-signing.md`。

### 8.4 macOS公开发布

每个新的macOS正式包都必须：

1. 使用有效的Developer ID Application证书签名；
2. 使用`KEMI_NOTARY`提交Apple公证；
3. 等待Apple返回`Accepted`；
4. 对App执行`stapler staple`和`stapler validate`；
5. 重新生成最终ZIP，再计算哈希和更新release。

已有`1.4.75+182`包不因会员续费或证书自然到期重新签名。只有发布新二进制时才用当时有效
证书重新走完整流程。受限沙箱的`Authority=(unavailable)`不能单独判定正式包损坏。

### 8.5 Windows与Linux

- 本地能编译的优先本地；当前Mac无法原生生成最终Windows/Linux时使用GitHub focused workflow；
- 云端构建必须绑定明确源码提交和run ID，构建期间的新开发不能移动候选身份；
- Windows当前没有Authenticode签名，客户可能看到SmartScreen信誉提醒；正式公开发布需要公司代码签名证书或可信签名服务；
- Linux AppImage当前没有发行签名，应通过SHA-256和受控下载源校验。

## 9. Git、子模块和文档注意事项

- 不允许向`origin`推送KEMI代码，只推`backup`；
- 不使用`git add -A`无脑提交，必须明确暂存文件；
- 不使用`git reset --hard`或`git checkout --`清理用户改动；
- `libs/hbb_common`本地显示dirty是KEMI服务器/公钥受控补丁工作副本；主仓通过
  `.github/patches/kemi_hbb_common_server.diff`复现，不能推送无法被GitHub获取的私有子模块gitlink；
- `DeviceRole.kt`第一阶段封版使用公开SDK可编译的非默认显示、状态和Presentation判断；
  `Display.TYPE_EXTERNAL`误改已经从工作树清除，禁止重新加入；
- 每次用户可见改动必须更新`CHANGELOG-KEMI.md`，每次发布必须更新版本、哈希、清单和来源commit；
- 文档写“已完成”必须有代码、构建或实机证据；没有实机证据时写“待验收”。

## 10. 当前未闭环事项与风险

### 10.1 发布侧

- 本地`BIN/release`已对齐，但Newlink云盘尚待管理员上传和回读；
- PAD `1.4.78+185`尚需通过云端发现、下载、双哈希校验、可选备份、系统升级和新版本启动闭环；
- 普通单屏Android设备的ID键盘、密码键盘、远控键盘和文件传输仍需要真实单屏设备验收；
- Windows包未做Authenticode签名；Linux包未做发行签名。

### 10.2 服务端

- 服务端仓库当前有未提交的hbbc、账号平台、安装脚本和OSS构建改动；
- `hbbs/hbbr`线上服务不能因为调试hbbc而重装或重启；三个systemd服务分别维护；
- hbbc账号、等级和统计目前是JSON原型设计，微信登录、支付回调、PostgreSQL迁移和完整统计尚未完成；
- 管理员令牌只能保存在服务器权限文件和`/Users/newlink/kemi/priv`受控说明中，不能进入Git或本文。

### 10.3 网络与稳定性

- CGNAT、对称NAT和多级路由仍可能只能中继；不能承诺所有热点P2P成功；
- P2P优化必须基于两端同一次会话日志，不得用单端“监听成功”推断公网可达；
- macOS锁屏过夜场景仍需长期观察WindowServer稳定性；断连抓屏停止和状态指示是不可回退门禁；
- VP9硬解必须保留软件回退和实际Decoder显示，不能只看“硬解开关已打开”。

## 11. 第一阶段封版检查单

客户端第一阶段归档时应看到：

```text
源码提交：9b4d8c58536d186cce53aa4d439d3ca376c9db1e
GitHub backup/master：同一完整提交
PAD版本：1.4.78+185
PAD SHA-256：f52f72dbe4a1673ad8462335acab81de800d3ef649451a3e7e3df408926e534e
release批次：1.4.78+185-pad-phase-1-final
macOS：1.4.75+182，已签名、公证、staple
Windows/Linux：1.4.75，文件哈希与manifest一致
cloud_urls_status：pending-manual-upload
```

允许的客户端工作树特殊状态只有已知的`libs/hbb_common`受控补丁副本。出现其他源码修改时，
必须先判断来源，不能把脏工作树直接称为第一阶段封版。

## 12. 第二阶段建议起点

按优先级建议：

1. 把Android九步发布链封装为一个失败即停止的项目脚本，自动复制Rust核心并拒绝旧APK/多ABI；
2. 管理员上传1.4.78三项文件，完成云端回读和PAD自升级闭环；
3. 找一台真实单屏Android设备完成四项同屏验收；
4. 在服务端仓库单独审计、构建和提交当前hbbc改动，再部署验证三个独立service；
5. 为Windows引入Authenticode正式签名；
6. 继续P2P候选上报、外部映射真实性验证和跨网络日志闭环；
7. 在不影响下载站点的前提下推进通用App账号、渠道、等级和使用统计平台。

## 13. 后续开发文档路由

| 任务 | 必读文档 |
|---|---|
| 工作区/仓库 | `WORKSPACE.md`、`GIT-OPS.md` |
| Android构建/签名 | `build-toolchain-policy.md`、`android-release-signing.md` |
| 四端发布/云盘 | `client-distribution.md`、`ci-build.md` |
| 双屏/键盘 | `dual-screen-port.md`、`cross-display-keyboard.md` |
| 文件传输 | `file-transfer-history.md` |
| 物理鼠标 | `android-physical-mouse.md` |
| P2P/热点/级联网络 | `p2p-network-debug-and-optimization.md`、`p2p-field-failure-cases-2026-08-06-07.md` |
| macOS权限与发布 | `macos-configuration.md`、`macos-developer-id-release.md` |
| 服务端部署 | `server-operations.md`以及服务端仓库文档 |
| hbbc跨项目下载 | `KEMI-SEND-CROSS-PLATFORM-CLIENT-DISTRIBUTION.md` |

第一阶段后新成员应先读本文，再进入对应专项文档，不应从长对话或旧版本流水账反推当前架构。
