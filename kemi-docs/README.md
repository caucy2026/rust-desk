# KEMI-远程桌面 — 开发文档

## 项目简介

**KEMI-远程办公** 是基于 [RustDesk](https://github.com/rustdesk/rustdesk) (AGPL-3.0) 定制的远程桌面客户端，面向 Android PAD 与多平台远控场景。当前PAD正式发布为 **1.4.62+167**；桌面三端仍保持各自已验收版本，Newlink云端批次以`release-manifest.json`实际内容为准。`BIN/`根目录保存带版本号的不可变归档，`BIN/release/`保存无版本号、永久固定名称的云盘上传副本。PAD以Newlink固定HTTPS元数据接口为主源，每次进入客户端页重新解析动态CDN地址；GitHub只在不造成版本倒退时作为备用源。Android使用Newlink正式applicationId与固定release签名，Mac当前使用固定本地测试签名。

## 当前项目总结（2026-08-06）

- 客户端源码位于本仓；服务端`hbbs/hbbr/hbbc`独立部署和维护。客户端统一使用`kemi-chat.newlinksz.com`与固定服务器公钥，无账户后台，因此不显示登录入口。
- Android PAD是当前主要实机：Display 0/Display 2双屏运行，远程控制、文件传输独立会话、跨屏键盘、触摸、物理鼠标右键、共享屏幕、服务器状态和局域网客户端分发均已有专项文档与真机基线。
- 当前PAD源码`1.4.62+167`在稳定鼠标/跨屏键盘基线上，加入连接记录、资源监控、Android真实VP9硬解、双栏文件传输、远端传入文件/目录安全删除、PAD目标空间门禁、客户端自更新前可选备份当前APK、本地APK安装与普通文件系统分享；首页远程ID和连接密码均可使用另一屏键盘，数字ID、本地密码和远程输入三种事件严格隔离。文件按钮与另一屏窗口状态同步，打开为绿色带背景，再点关闭；文件窗口是90%×78%的真实非模态窗口，窗口外主屏桌面仍可操作。Android 12双屏设备需一次性授予“显示在其他应用上层”，这里只把它作为跨屏任务恢复许可，不创建悬浮图标。详细边界分别见`connection-history-and-resource-monitor.md`、`file-transfer-history.md`、`cross-display-keyboard.md`和`client-distribution.md`。
- Newlink云端已发布PAD `1.4.62+167`，三个固定接口回读文件与本地release逐字节一致；真机从1.4.61选择备份后升级成功。桌面三端仍保持各自已验收的`1.4.49`字节，清单明确标为PAD热修订混合快照；后续完整四端发布仍需同一提交重新构建Mac/Windows/Linux。受控PAD的Download完整访问部署步骤见`file-transfer-history.md`第8节。
- GitHub `backup/master`保存KEMI源码和文档；`origin`只跟踪RustDesk上游，禁止推送定制代码。二进制历史位于项目`BIN/`，固定云盘上传快照位于`BIN/release/`，两者不以Git提交代替。

### 核心定制功能

| 功能 | 说明 |
|------|------|
| **双屏适配** | Android PAD 主屏键控 + 副屏远控画面，跨屏触摸与键盘转发 |
| **文件传输** | 双屏PAD使用90% × 78%真实非模态窗口，单屏保留原浮窗回退；独立Session与远控视频并行，Android默认Download |
| **远端目录记忆** | 再次打开文件传输时，优先恢复对方上次可用目录；无效时按初始目录与根目录回退 |
| **传输记录** | 按方向、源目录和目标目录归组；同一路径不重复，完成后可“再次传输”，源或目标失效时原位提示 |
| **远控操作栏** | 44px 高、48px 宽的中文图文按钮；“输入”打开手势说明，整格按钮统一水波纹与高亮反馈 |
| **物理鼠标右键** | Android原生捕获次键及鼠标来源Back事件，只在当前显示远控页的物理屏幕转发右键按下/抬起 |
| **共享屏幕授权** | 点击启动后直接进入 Android 原生录屏确认；通知、文件访问和悬浮窗权限按功能独立申请 |
| **局域网客户端下载** | 页面显示真实Wi-Fi；四端可选PAD已校验本地下载或实时HTTPS云端下载，并可复制实际云端地址 |
| **品牌定制** | KEMI 启动画面、应用命名、权限引导流程 |
| **信令服务** | 自建 rendezvous 服务器，Mac ARM 交叉编译 Linux x86_64 |

### 当前版本对应关系

| 客户端版本 | 对应功能 |
|---|---|
| `PAD 1.4.53+158候选；全端源码1.4.53` | Android arm64接入真实MediaCodec VP9硬解；按厂商组件和具体I420/NV12输出选择，处理stride/crop/动态格式变化，失败自动回退libvpx；副屏实测进入`OMX.uapi.video.decoder.vp9`。当前未切换正式六文件批次 |
| `PAD 1.4.52+157候选；全端源码1.4.52` | 恢复首页后两个入口；新增连接记录、删除/清空和跨重启持久化；CPU拆分整机/多核口径并上报实际Decoder后端。当前未切换正式六文件批次 |
| `PAD 1.4.51+156候选；全端源码1.4.51` | 修复跨屏键盘打开时源Activity被设为`NOT_FOCUSABLE`造成的InputDispatcher ANR；远控窗口始终保持合法焦点能力；右键缺失release时按下一鼠标状态自动补发up。当前未切换正式六文件批次 |
| `PAD 1.4.50+155候选；全端源码1.4.50` | 副屏认证完成前禁止跨屏代理，修复`inactive InputConnection`；Newlink清单重试、拒绝旧GitHub回退并允许新清单中断旧下载；局域网二维码直达`/clients`四平台下载区。当前未切换正式六文件批次 |
| `全端 1.4.49 / PAD、macOS build 154` | PAD服务器未就绪时显示“重连”，点击后仅重启rendezvous注册链路；断开后抓屏状态闭环、跨屏键盘/物理鼠标和桌面状态同步进入同一正式候选；Windows/Linux由focused run `30891539907`生成并与功能提交`ed615c7fb`绑定 |
| `全端 1.4.48 / PAD、macOS build 125` | 四端统一绑定候选`a5ff428b5`及新服务器/固定公钥；macOS本地全量重编译、固定签名并实测连接`119.96.24.110:21116`，Windows/Linux focused run `30731531135`成功；六个固定发布文件统一在`BIN/release/` |
| `PAD 1.4.48+125` | 设置首页和关于区域直接显示当前ID服务器；关于页网站改为`newlink-sz.com`；开源服务端`21114`账户API缺失明确视为正常能力边界 |
| `1.4.48 / PAD 1.4.48+124` | Windows、macOS、Linux和PAD主页显示实际运行包版本；新客户端默认启用UDP打洞；MAC/PAD已固定使用`kemi-chat.newlinksz.com`及当前服务器公钥，MAC实测与`21116`建立长连接 |
| `1.4.46+123` | PAD外接鼠标右键增加Android原生兜底，兼容`BUTTON_SECONDARY`和鼠标来源`KEYCODE_BACK`；仅当前远控页所在屏幕响应，离页不影响PAD系统右键行为 |
| `1.4.46+122` | PAD文件传输新增持久化传输记录、目录归组、同路径去重、“再次传输”和源/目标失效提示；记录页固定显示“返回文件”与返回说明；改为发送前落盘，并取消自动数量淘汰，用户不删除就跨进程、跨开机长期保留 |
| `Windows 1.4.48 / a5ff428b5` | Windows首次启动和macOS一致，默认直接显示“主页/设置”；focused构建与候选发布均成功，当前固定EXE来自本次KEMI源码，不是官网下载安装器 |
| `1.4.46+120` | 修复副屏反复开关软键盘时代理任务偶发迁移回副屏：复用改为仅置前原任务，并在每次IME请求前校验真实Display；副屏连续15轮、含三次HOME重建均保持键盘在主屏 |
| `1.4.46+119` | HTTP下载页增加真实Wi-Fi网络卡；四个平台均提供“从PAD下载”“HTTPS云端下载”和“复制地址”，只展示本次解析且通过域名白名单的实际CDN地址 |
| `1.4.46+118` | 客户端分发改为Newlink固定HTTPS接口为主：进页实时解析四端CDN地址，核对manifest/SHA256SUMS/MD5/SHA-256后才由PAD HTTP提供；按SHA隔离新旧缓存，GitHub自动回退 |
| `1.4.46+117` | 主屏按HOME后立即隐藏IME、关闭并销毁键盘代理；副屏再点键盘时新建主屏代理。通过已授权的“允许显示在其他应用上层”能力满足Android 12跨屏后台启动限制，不创建悬浮图标 |
| `1.4.46+115` | 修复主屏输入法真实收起后副屏键盘按钮无法再次打开：过滤跨请求残留的IME可见Insets，只有新`showSoftInput`被接受后才允许进入`visible` |
| `1.4.46+112` | PAD副屏远控页预创建并复用主屏键盘代理；强制收起后进入不可交互停放状态，再次点击恢复同一实例并把输入焦点切回主屏 |
| `1.4.46+111` | PAD原始尺寸改为按实际远控画布进行物理像素点对点居中；适应窗口按真实画布边界重算；重复点击当前显示模式会清除手势缩放/偏移并强制应用 |
| `1.4.46+110` | PAD下载状态改为右侧圆形进度环内显示百分比；局域网HTTP页逐项还原既有DEMO；小型清单按raw GitHub→jsDelivr双源回退，大文件仍使用Release Assets API |
| `1.4.46+109` | Release Assets API请求只接受二进制响应，并自动清理旧版误存的JSON临时文件，防止把资产元数据当客户端 |
| `1.4.46+108` | 真机确认PAD网络访问`github.com/releases`首跳超时后，改用GitHub官方Release Assets API跳转下载，并补齐失败日志；107保留为未进入稳定分发的网络失败候选 |
| `1.4.46+107` | 四端制品统一进入 `BIN/` 与 `common-data` Release；PAD 不再内置三端大文件，改为开机后空闲同步、断点续传、SHA-256 校验和最后成功缓存；缺失客户端可点击并查看下载动画 |
| `1.4.46+106` | 按华为合规分发规范重发PAD：正式包名和固定release证书保持不变，递增versionCode并完整记录源码、APK与签名指纹；新增Mac同事从GitHub fresh clone到Release App的独立构建文档 |
| `1.4.46+105` | Windows x64与Linux x86_64云端候选生成并导入PAD；最终release内置Windows EXE、Mac ZIP、Linux AppImage，实机完成三端HTTP下载和哈希核验 |
| `1.4.46+104` | macOS Apple Silicon客户端与PAD同步发布：最新Mac App固定签名后内置到PAD，最终release完成实际HTTP下载、版本、签名和哈希端到端核验 |
| `1.4.45+103` | Android正式身份迁移：applicationId改为`com.newlinksz.kemi.remote`，release构建强制使用Newlink固定签名并禁止debug证书；业务功能和权限保持不变 |
| `1.4.44+102` | macOS 登录项统一使用系统 `SMAppService.mainApp`，Dock「选项 → 登录时打开」与 KEMI 开关一致；正式包自动修复登录项 URL 至 `/Applications`；主页默认显示“主页 / 设置”，关于页显示 KEMI 产品版本 |
| `1.4.43+101` | 首次让 Mac 主页默认创建“主页 / 设置”标签，并将关于页版本数据源改为 App 包版本；该临时构建仅用于验证，登录项最终路径修复由 1.4.44 交付 |
| `1.4.42+100` | 移除 macOS 授权卡片错误暴露的内部标记“授权流程 v1.0.11”；Mac 与 PAD 内置 Mac 下载包同步升级 |
| `1.4.41+99` | PAD 分发资源更新为固定签名的 macOS `1.4.40+98` 安装包，确保从“客户端”页下载的 Mac App 含“开机自启”中文选项 |
| `1.4.40+98` | Wi-Fi 名称读取不到时可直接打开系统定位设置，返回页面自动刷新；macOS 下载项使用 Apple 标志 |
| `1.4.39+97` | 客户端页可单独授权读取真实 Wi-Fi 名称；下载列表采用 Android、Apple、Windows、Linux 对应平台图标 |
| `1.4.38+96` | 无法真实读取 SSID 时不再显示伪 Wi-Fi 名称；明确浏览器输网址或扫码是二选一；PAD 停止启动无效悬浮入口；macOS 的登录项中文改为“开机自启” |
| `1.4.37+95` | 客户端页改为左侧半宽网址、右侧二维码；固定展示 PAD、macOS、Windows、Linux，Mac 离线 ZIP 已纳入，Windows/Linux 待导入时明确显示状态 |
| `1.4.36+94` | PAD 首页新增“客户端”入口；进入页面启动、离开关闭局域网 HTTP 下载服务；先提供当前 APK，已核验的 Windows/macOS/Linux 离线包存在时才显示 |
| `1.4.35+93` | 单屏设备点击键盘时由当前 Activity 在本屏启动键盘代理；双屏时仍严格弹到对面屏 |
| `1.4.34+92` | 共享屏幕启动直接进入 Android 原生录屏确认，移除应用内警告及悬浮窗/通知/文件权限插队；新增中文操作简介 |
| `1.4.33+91` | 双指滚轮只在两根手指共同、同向纵向移动时触发；一指按住、另一指滑动不会再误触发远端滚轮 |
| `1.4.32+90` | 主页“最近访问、收藏、已发现、地址簿、可访问设备”图标下显示当前选中功能的用途说明；Mac 与 PAD 共用同一实现，已构建、签名并部署双端 |
| `1.4.31+89` | macOS 远控必需授权收敛为屏幕录制和辅助功能；输入监控改为不阻塞远控的可选本地键盘功能，移除会打开空白条目的第三项授权 |
| `1.4.30+88` | macOS 输入监控改用官方 `CGRequestListenEventAccess` 申请、`CGPreflightListenEventAccess` 查询，修复隐私页空白项目 |
| `1.4.29+87` | macOS 单项授权真正成功后自动恢复仍在运行的 KEMI 主窗口到前台，避免窗口失焦被误认为退出 |
| `1.4.28+86` | macOS 输入监控仅允许由前台 KEMI 主 App 发起申请，避免隐私设置生成无名称条目 |
| `1.4.27+85` | macOS 首页左下“权限设置”入口永久显示；即使当前状态显示已授权，也可随时进入权限配置 |
| `1.4.26+84` | macOS 三项权限改为独立“申请授权”按钮，互不等待；输入监控无原生确认时打开对应系统页；新增“开机自启动”选项 |
| `1.4.25+83` | macOS 授权改为前台常驻；一个按钮启动三项系统授权，但严格串行置前，避免后一个弹窗遮住前一个；Mac 测试包改用固定证书签名，避免 ad-hoc 更新后 TCC 权限身份变化 |
| `1.4.22+80` | macOS 授权引导、状态列表及远端异常提示中文化；已构建并安装验证 macOS arm64 包，尚未重新部署 PAD APK |
| `1.4.21+79` | macOS 输入健康检查：首次远程输入统一请求屏幕录制、辅助功能和输入监控；未授予辅助功能时明确阻止输入并记录原因 |
| `1.4.20+78` | 直接指针保障：单点手势未回调时补发左键点击；双指中心纵向位移直接映射远端滚轮 |
| `1.4.19+77` | 移除重复“说明”入口，“输入”保留为手势说明入口 |
| `1.4.18+76` | 底栏整格水波纹与高亮点击反馈 |
| `1.4.17+75` | 恢复中文操作栏、说明入口和文件传输浮窗/连接令牌逻辑 |

### 技术栈

- **客户端 UI**: Flutter（`flutter/`）
- **Rust 核心**: 屏幕采集、编解码、输入控制、网络协议（`src/`、`libs/`）
- **原生桥接**: Android Kotlin MethodChannel / macOS Swift
- **信令服务**: Rust（`/Users/newlink/kemi/RustDesk/server/`）

---

## 文档目录

```
kemi-docs/
├── README.md                    ← 本文件（项目简介与文档导航）
├── WORKSPACE.md                 ← 工作区、客户端/服务端仓库边界
├── SESSION-HANDOFF.md           ← 跨会话接续手册、完整架构与构建部署流程
├── CHANGELOG-KEMI.md            ← 开发调试记录
├── GIT-OPS.md                   ← Git 操作与 GitHub 备份指南
├── LOCAL-CHANGE-REVIEW.md       ← 当前候选逐文件改动、风险与发布门禁
├── macos-configuration.md        ← macOS 权限、签名、交付与排障（唯一操作说明）
├── macos-local-build.md          ← Mac 同事从 GitHub 本地编译 Release App
├── android-release-signing.md    ← Android正式包名、固定签名、构建与迁移
├── ci-build.md                   ← 通用备份/云构建协调方法与 KEMI 特例
├── windows-vscode-build-prompt.md ← Windows 同事在 VSCode 本地构建 x64 客户端
├── client-distribution.md         ← 四端BIN/common-data发布、PAD自动同步与局域网分发规范
├── KEMI-SEND-CROSS-PLATFORM-CLIENT-DISTRIBUTION.md ← KEMI快传六文件、云后台、hbbc JSON、本地/云端下载闭环模板
├── file-transfer-history.md       ← PAD传输记录、去重、再次传输与错误提示设计
├── android-physical-mouse.md       ← PAD外接物理鼠标右键输入链路与排查
├── server-operations.md         ← 服务端构建与部署入口
├── cross-display-keyboard.md    ← 跨屏软键盘需求与设计
├── dual-screen-port.md          ← 安卓双屏移植总体架构
└── reference/                   ← 设备资料与历史架构文档

.github/prompts/
└── continue-kemi-rustdesk.prompt.md  ← VS Code 中可直接运行的接续提示词
```

---

## 文档说明

### README.md（本文件）

项目简介、文档目录导航、快速入门指引。**新成员入职第一份阅读材料。**

### WORKSPACE.md

唯一工作目录、客户端与服务端的 Git 边界、已退役目录和文档职责。涉及路径、提交或服务端时，先读本文件。

### SESSION-HANDOFF.md

**跨会话接续的第一事实入口**：

- 可直接复制给新会话的完整提示词
- 当前 Git 基线、不可回退行为和三层目录架构
- 双屏、键盘、并行文件传输 Session 的数据流
- Flutter/Java/Rust/ADB 环境与 Debug APK 构建、部署、验收命令
- 当前真实 GitHub 候选分支、WIP备份与恢复流程

> 新会话必须先读本文件，再根据任务进入具体模块文档。

### CHANGELOG-KEMI.md

**开发调试记录**，按时间倒序记录每次功能改动：

- 每节包含：问题、根因、修复文件、改动细节、验证结果
- 末尾维护"暂未完成任务"清单
- 当前最新：第六十九节（新服务器四端重编译与PAD自动更新验证）

> 每次提交代码前应同步更新本文档。

### GIT-OPS.md

**Git 操作与 GitHub 备份指南**，独立于外部对话记录：

- 真实远端地图（官方 `origin` vs KEMI `backup`）
- 候选提交推`backup/master`、未完成进度推`backup/wip/*`的分流规则
- 从备份仓恢复的完整步骤
- 分叉处理、冲突解决和远端完整哈希核验

> 不再复制文件到第二个本地仓库，也不再推送旧的 `origin main`。

### macos-configuration.md

macOS 客户端的唯一操作说明：两项远控必需权限、PAD 输入的真实准入条件、`LSUIElement` 授权窗口恢复、固定测试签名、交付核验、TCC 重置和开机自启动。涉及 Mac 构建、签名或权限时必须先读本文件。

### android-release-signing.md

Android正式身份、固定release密钥、环境变量、构建命令、证书指纹、旧包迁移和华为设备验收。
涉及Android release、包名或签名时必须先读本文件，禁止重新生成密钥或退回debug签名。

### ci-build.md

本地开发、远端备份与云端构建协调的唯一说明。第一部分是可供其他项目复用的分支职责、
WIP备份、并发取消、候选身份、失败处理和交付状态；第二部分单列KEMI的`master`/`wip/*`、
focused workflow、Windows/Linux矩阵、查询命令与当前run。涉及构建期间备份、Windows、
Linux或CI时必须先读本文件。

### client-distribution.md

四端交付的唯一说明：`BIN/`本地基准、`BIN/release/`六个固定上传文件、Newlink云端`Common`项目的人机分工、六个固定查询地址、上传顺序与回读验收，以及GitHub备用源、PAD同步和同Wi-Fi HTTP服务。涉及客户端分发或四端安装包时必须先读本文件。

### KEMI-SEND-CROSS-PLATFORM-CLIENT-DISTRIBUTION.md

以KEMI快传为实际例子的跨项目闭环模板：从四端构建、六个发布文件、Newlink后台固定name与查询地址、两份清单、hbbc站点JSON和服务器部署，到客户端发现API、PAD本地HTTP、APK自包复用、失败回退及初次接入/日常升级分工。其他多平台项目接入统一客户端下载时优先复制这套流程，不复制动态CDN地址。

### file-transfer-history.md

PAD文件传输记录的产品与代码规格：记录时机、目录归组和同路径去重键、持久化位置、再次传输前的源/目标校验、错误显示以及真机验收步骤。修改文件传输记录时以本文为准。

### android-physical-mouse.md

Android PAD外接物理鼠标的专项说明：右键失效根因、`BUTTON_SECONDARY`与鼠标来源`KEYCODE_BACK`兼容、单屏/双屏作用域、按下/移动/释放去重和ADB排查。修改物理鼠标输入时以本文为准。

### windows-vscode-build-prompt.md

给 Windows 同事直接复制到 VSCode AI 的本地构建提示词：先审计 Visual Studio/LLVM/Rust/Flutter/vcpkg，再生成 default bridge、构建 Windows x64 Flutter客户端、打便携 EXE并核验版本、哈希和签名。用于同事自己从 GitHub源码复现 Windows候选包。

### server-operations.md

服务端源码路径、构建命令及部署文档入口。服务端的详细部署说明只维护在 `../../server/bin/DEPLOY.md`，不在客户端文档重复复制。

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
2. **WORKSPACE.md** — 确认客户端、服务端与 Git 边界
3. **SESSION-HANDOFF.md** — 核对当前事实、基线、环境和不可回退行为
4. **CHANGELOG-KEMI.md** — 了解近期改动与当前待办
5. **dual-screen-port.md** — 理解双屏架构（历史命令以接续手册为准）
6. **cross-display-keyboard.md** — 理解键盘模块
7. **server-operations.md** — 服务端构建与部署入口
8. **macos-configuration.md** — 涉及 Mac 时阅读
9. **ci-build.md** — 涉及云端、Windows 或 Linux 构建时阅读
10. **GIT-OPS.md** — 掌握代码提交与 GitHub 备份流程

### 日常开发速查

```bash
# 构建 PAD APK
export PATH=/Users/newlink/flutter/bin:$PATH
cd /Users/newlink/kemi/RustDesk/client/flutter
flutter build apk --debug

# 安装到设备
adb -s 192.168.1.10:5555 install -r -d build/app/outputs/flutter-apk/app-debug.apk

# 查看改动
cd /Users/newlink/kemi/RustDesk/client
git status --short
git diff --stat

# 旧云构建仍在运行，只备份未完成进度
git add <files>
git commit -m "wip: back up current progress"
git push backup HEAD:refs/heads/wip/20260730-feature-name

# 功能完成并准备启动下一轮候选时才推 master
git push backup master

# 核对实际推送分支与本地哈希
git ls-remote backup refs/heads/wip/20260730-feature-name
git rev-parse HEAD
```

---

> 最后更新：2026-08-01
> 维护：KEMI 远程桌面团队
