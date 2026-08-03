# KEMI 四端客户端发布、云盘同步与局域网分发

> 适用版本：`1.4.46+119` 起。本文是四端制品、项目 `BIN/`、固定云盘文件名、GitHub `caucy2026/common-data`、PAD 后台缓存和局域网 HTTP/HTTPS 下载入口的唯一维护说明。

## 当前待上传批次：1.4.48+129

本批次四端统一绑定源码 commit `eef2e0c0222c0701c3fea6137907d933c8da8921`，并包含：KEMI服务器与固定公钥、无账户 UI、服务器状态语义及本批次已 review 的会话修复。PAD/macOS由本机全量构建，Windows x64/Linux x86_64由 GitHub focused run `30795669077` 构建；四项通过大小、架构和SHA-256校验后，`BIN/release` 六个固定文件整体切换到本批次，不再属于旧 `1.4.48+125` 批次。

更新顺序固定为：形成候选 commit → 本地构建并验收 PAD/Mac → 同 commit 获取 Windows/Linux focused artifacts → 核对四端版本/服务器/公钥/哈希 → 一次性覆盖六个 release 文件 → 最后生成并上传 manifest。

## 1. 需求结论

四个平台客户端统一按下面的链路管理：

```text
各平台构建并验收
        ↓
项目根目录 BIN/（带版本号、不可变的历史归档）
        ↓
BIN/release/（无版本号、固定文件名的云盘上传区）
        ↓ 上传后回读校验
Newlink国内云盘固定HTTPS接口（主源，动态解析CDN地址）
        ↓ 失败时回退
GitHub caucy2026/common-data Release（备用源）
        ↓ 最后发布 stable manifest
PAD 开机后联网且空闲时比较版本并增量下载
        ↓
PAD 私有缓存（最后一次校验成功的文件）
        ↓ 用户进入“客户端”页才开启
PAD 局域网 HTTP 服务
        ↓
同一 Wi-Fi 的 Android / macOS / Windows / Linux 设备下载
```

这里有三个强制原则：

1. `BIN/` 根目录保存带真实版本号的不可变历史归档；`BIN/release/` 只保存当前准备上传云盘的固定文件名副本。聊天记录、Actions 临时 Artifact 和 PAD 缓存都不能替代这两层。
2. `common-data` 的普通 Git 历史只保存小型清单和说明，大型客户端放 GitHub Release，避免仓库因每个版本重复提交二进制而失控。
3. 先上传四个文件，逐个回读校验，最后才更新稳定清单。PAD 只相信稳定清单，因此不会看到“清单已更新但客户端还没传完”的半成品发布。

## 2. 四端版本、归档名和云盘固定名

产品版本来自根 `Cargo.toml`，当前为 `1.4.48`。当前待上传批次绑定源码`eef2e0c0222c0701c3fea6137907d933c8da8921`：PAD为`1.4.48+129`，macOS为`1.4.48 (129)`，Windows/Linux包内产品版本为`1.4.48`。Windows/Linux由focused run `30795669077`生成，macOS/PAD在本机全量构建并使用既有固定测试签名。桌面云端包没有Flutter build number字段，因此清单如实保留产品版本，并通过批次、commit、run ID和SHA-256建立对应关系。

`BIN/`根目录中当前批次的版本化归档为：

```text
BIN/
├── KEMI-远程桌面-PAD-1.4.48+129-release.apk
├── KEMI-远程桌面-macOS-arm64-1.4.48+129.zip
├── KEMI-远程桌面-Windows-x64-1.4.48-eef2e0c.exe
└── KEMI-远程桌面-Linux-x86_64-1.4.48-eef2e0c.AppImage
```

上述`BIN/`根目录归档名必须带真实版本。旧版可以移到 `BIN/archive/`，但不能用新版文件名包装旧字节，也不能因为某个平台尚未构建完成就复制旧包冒充本次版本。macOS 当前是 Apple Silicon，Windows/Linux 当前是 x86_64；新增架构时新增独立目标，不能覆盖现有架构文件。

### 2.1 `BIN/release/` 云盘固定文件名

```text
BIN/release/
├── KEMI-PAD.apk
├── KEMI-macOS.zip
├── KEMI-Windows.exe
├── KEMI-Linux.AppImage
├── release-manifest.json
└── SHA256SUMS.txt
```

这六个名称是云盘稳定接口，后续不得增加版本号、改大小写或换名。二进制升级时覆盖同名云盘对象，同时更新`release-manifest.json`与`SHA256SUMS.txt`的版本、大小和SHA-256。固定名本身不代表版本；PAD和发布人员必须以清单的字节数、SHA-256及包内版本为准。

代码不保存任何动态CDN URL。PAD每次进入“客户端”页时请求Newlink固定HTTPS元数据接口，页面保持打开时每5分钟刷新；读取返回的`url`后仅允许`cdn.newlink-sz.com`。云盘路径任一步失败则回退GitHub源，不删除上一份校验成功缓存。

固定元数据名称为：`release-manifest`、`SHA256SUMS`、`KEMI-PAD`、`KEMI-Windows`、`KEMI-macOS`、`KEMI-Linux`，统一请求`https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=<名称>`。接口的`version=1`和`size=0`不参与判断：版本、架构、大小、SHA-256以`release-manifest`为准，并必须与`SHA256SUMS`一致；接口MD5作为完整下载的第二道校验。

### 2.2 Newlink云端人工上传与自动流程分工

人工上传管理入口：<https://www.newlinksz.cn/screensaver/main/configPlug/Common>。

- 这是新智联云端文件管理后台的`Common`独立项目，不是客户下载地址。
- 账号和密码由公司后台负责人分配；凭据只由上传人员保管，不写入源码、文档、聊天记录、脚本或GitHub。
- 每次发布只操作下表六个固定项目。管理后台中的项目名、上传文件名和大小写都必须完全一致；使用“覆盖/更新文件”，不要删除后新建、不要增加版本号。

| 上传顺序 | Common项目中的固定名称 | 从本地选择的文件 | PAD使用的固定查询地址 |
|---:|---|---|---|
| 1 | `KEMI-PAD` | `BIN/release/KEMI-PAD.apk` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-PAD` |
| 2 | `KEMI-macOS` | `BIN/release/KEMI-macOS.zip` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-macOS` |
| 3 | `KEMI-Windows` | `BIN/release/KEMI-Windows.exe` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Windows` |
| 4 | `KEMI-Linux` | `BIN/release/KEMI-Linux.AppImage` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Linux` |
| 5 | `SHA256SUMS` | `BIN/release/SHA256SUMS.txt` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=SHA256SUMS` |
| 6（最后） | `release-manifest` | `BIN/release/release-manifest.json` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=release-manifest` |

#### 用户负责

1. 向公司后台负责人申请并保管Newlink云端管理账号。
2. 收到“六个文件已准备并校验完成”的通知后，打开上述管理入口并确认当前项目是`Common`。
3. 严格按表格顺序覆盖上传六个文件；客户端二进制先传，`SHA256SUMS`倒数第二，`release-manifest`最后上传。
4. 六项都显示上传成功后，只回复“六个文件已上传完成”及实际完成时间；无需发送账号、密码或CDN动态URL。
5. 如果任何一项失败，停止上传`release-manifest`并告知失败的固定名称；不要拿旧文件、改名文件或空文件补位。

#### 开发/自动流程负责

1. 统一版本号，构建或接收四个平台候选，并确认它们确实来自KEMI源码；Windows下载项必须是本项目验收的`KEMI-Windows.exe`，不会跳转RustDesk官网安装器。
2. 校验包内版本、架构、启动能力和签名状态；Android必须使用固定Newlink release证书，macOS/Windows的签名与公证状态必须如实记录。
3. 在`BIN/`保存带版本号、不可变的历史归档；再生成`BIN/release/`六个固定名文件，不让用户手工重命名或修改清单。
4. 计算四端文件大小和SHA-256，生成`SHA256SUMS.txt`与`release-manifest.json`，执行本地六文件一致性检查后通知用户上传。
5. 用户确认上传后，逐个请求上述六个固定查询地址，解析每次上传产生的新CDN URL，验证HTTPS域名、接口MD5、文件名、大小、SHA-256及两份清单一致性。
6. 云端回读通过后，让PAD重新解析、下载并校验；再从Mac或Windows浏览器分别验证PAD本地下载与HTTPS云端下载，确认字节一致。
7. 最后更新版本记录和当前发布状态；需要GitHub备用源时另行更新`common-data`，但不要求用户参与GitHub命令。

#### 交接边界

- 用户只承担“登录后台并上传六个已准备文件”这一项人工权限操作；构建、命名、哈希、清单、回读、PAD安装和闭环测试均由开发流程承担。
- `BIN/release/`是一次发布的完整快照。即使本次只有PAD发生变化，也仍交付六个文件，避免服务器混用不同批次。
- `release-manifest`必须最后上传，它是本批次发布完成标志。上传未完成时PAD保留最后一次校验成功缓存并可回退GitHub，不把半成品提供给客户。
- 六个查询地址永久固定；接口返回的`url`是随覆盖上传变化的CDN实际地址，只能实时解析，禁止复制后硬编码到源码。

每个平台的“版本一致”含义如下：

| 平台 | 版本来源 | 当前`BIN/release/`内容 |
|---|---|---|
| PAD / Android | `flutter/pubspec.yaml` | `versionName=1.4.48`、`versionCode=129`，清单写 `1.4.48+129` |
| macOS arm64 | App `Info.plist` | `CFBundleShortVersionString=1.4.48`、`CFBundleVersion=129` |
| Windows x64 | focused构建环境与候选manifest | 产品版本`1.4.48`，绑定源码`eef2e0c02`与run `30795669077` |
| Linux x86_64 | focused构建环境、AppImage和候选manifest | 产品版本`1.4.48`，绑定源码`eef2e0c02`与run `30795669077` |

## 3. common-data 仓库结构

仓库地址：<https://github.com/caucy2026/common-data>，默认分支 `main`。

建议的普通 Git 目录只包含：

```text
kemi-rustdesk/
├── README.md
└── stable/
    └── manifest.json
```

每批二进制使用不可变 Release，例如：

```text
tag: kemi-rustdesk-v1.4.46-build110
assets:
  KEMI-remote-desktop-PAD-1.4.46+110.apk
  KEMI-remote-desktop-macos-arm64-1.4.46+110.zip
  KEMI-remote-desktop-windows-x64-1.4.46.exe
  KEMI-remote-desktop-linux-x86_64-1.4.46.AppImage
  manifest.json
  SHA256SUMS.txt
```

Release 资产一旦进入稳定清单就视为不可变；需要修复时增加构建号和新 tag，不覆盖同名文件。这样 PAD 的断点续传、哈希校验和问题追溯都有稳定依据。

## 4. 稳定清单格式

PAD读取的是同一份小型stable manifest，按`github-cloud-resource-guide.md`采用双源：

```text
1. raw GitHub（连接超时4秒，实时）
   https://raw.githubusercontent.com/caucy2026/common-data/main/kemi-rustdesk/stable/manifest.json
2. jsDelivr @main（连接超时8秒，附分钟级 _t 参数）
   https://cdn.jsdelivr.net/gh/caucy2026/common-data@main/kemi-rustdesk/stable/manifest.json
3. 两者失败：继续使用磁盘中的最后成功清单和已验证客户端
```

指南中的commit-hash CDN最适合由App版本绑定的静态小数据；本功能要求common-data更新后已安装PAD也能发现新版，因此清单使用`@main + 分钟cache-bust`并接受短暂CDN延迟。四端二进制不走jsDelivr：Linux AppImage约79MiB，超过指南建议的50MiB范围；大文件统一走GitHub Release Assets API，仍由大小和SHA-256门禁保护。

清单格式版本为 1，必须一次包含四个平台：

```json
{
  "schema_version": 1,
  "channel": "stable",
  "release_version": "1.4.46+110",
  "source_commit": "完整的 rust-desk 源码 commit",
  "generated_at": "2026-07-31T12:00:00+08:00",
  "targets": [
    {
      "id": "android",
      "version": "1.4.46+110",
      "architecture": "arm64-v8a",
      "file": "KEMI-remote-desktop-PAD-1.4.46+110.apk",
      "size": 12345678,
      "sha256": "64位小写SHA-256",
      "url": "https://api.github.com/repos/caucy2026/common-data/releases/assets/<PAD_ASSET_ID>"
    }
  ]
}
```

其余三个目标的 `id` 必须依次使用 `windows`、`macos`、`linux`。文件名只允许 ASCII 字母、数字、点、下划线、加号和连字符。当前PAD所在网络访问`github.com/releases`首跳会超时，因此稳定清单使用GitHub官方Release Assets API URL；客户端发送`Accept: application/octet-stream`后由`api.github.com`直接跳转到`release-assets.githubusercontent.com`。URL必须是HTTPS且属于代码白名单。

清单中的 SHA-256 用于确认下载字节与发布字节一致。它能防止传输错误和错误文件混入，但不能替代平台代码签名：Android 仍检查固定 release 签名，macOS 正式外发仍需 Developer ID 签名与公证，Windows 正式外发仍应做 Authenticode 签名。

## 5. 发布顺序与未完成构建的处理

每次发布严格执行：

1. 冻结本次源码 commit 和产品版本。
2. 本地能编译的 PAD/macOS 在本地构建；Windows/Linux 优先由对应平台同事本机构建，本地确实不具备目标环境时才使用 GitHub Actions。
3. 每个平台独立验收并进入候选区，不修改稳定清单。
4. 四个候选文件全部齐备后先保存带版本号的`BIN/`归档，生成大小、SHA-256、源码 commit、构建机或 Actions run ID 记录。
5. 只有候选验收通过后，才可以覆盖`BIN/release/`的四个固定名副本，然后重新生成两份校验文件。
6. 通知具备Newlink后台权限的用户按“2.2”顺序覆盖上传六个云盘固定文件；动态直链不写入代码。
7. 如仍需GitHub备用源，创建新的 `common-data` Release，上传四端带版本文件、批次 manifest 和 `SHA256SUMS.txt`。
8. 从 GitHub 回读资产元数据，至少复核文件名和大小；能下载回读时再次核对 SHA-256。
9. 最后一个单独提交更新 `kemi-rustdesk/stable/manifest.json`。
10. 用户确认上传完成后，由开发流程解析六个固定查询地址并回读校验；PAD完成下载后，再验证局域网HTTP和HTTPS直链下载与`BIN/release/`同名文件哈希一致。

如果上一次云端构建没有完成，而源码又需要备份：

- 源码照常提交和推送，备份不等待二进制构建。
- 不移动旧 tag，不覆盖旧 Release，不更新稳定清单。
- 新构建继续绑定原候选 commit；若新源码已改变功能，则另开新候选批次，不能把两个 commit 的产物混在一个发布中。
- 只有四端齐备且验收通过的批次才能成为 `stable`。

这使“源码及时备份”和“上次构建继续完成”互不阻塞，也避免 PAD 自动下载半成品。

## 6. PAD 自动同步设计

相关 Android 文件：

| 文件 | 职责 |
|---|---|
| `ClientPackageSync.kt` | 下载清单、比较版本、断点续传、大小/SHA-256 校验、最后成功缓存 |
| `ClientPackageSyncJobService.kt` | 使用 Android `JobScheduler` 安排开机后和周期性空闲同步 |
| `BootReceiver.kt` | 收到开机广播后独立安排客户端同步，不受“远控服务开机自启”开关影响 |
| `MainApplication.kt` | App 升级或首次启动后补登记周期任务 |
| `ClientDistributionServer.kt` | 只向局域网提供当前已验证文件 |
| `client_download_page.dart` | 显示状态、手动触发、进度动画与错误重试 |

调度条件：

- 开机后登记一次性任务，最早 1 分钟后运行，最迟在系统允许的窗口内尝试；
- 同时登记每 6 小时的周期任务；
- 仅在非计费网络（通常为 Wi-Fi）并且系统判断设备空闲时下载；
- Android 的省电策略和厂商调度可能推迟任务，因此“空闲同步”不是精确闹钟；
- 用户在客户端页点击缺失项属于明确操作，会立即请求下载，不等待下一个空闲窗口。

缓存目录使用 App 专属外部目录：

```text
Android/data/com.newlinksz.kemi.remote/files/client-cache/
```

不申请公共存储写权限。卸载 App 会清除缓存，重新安装后会在下次空闲或用户点击时恢复。

单个文件的状态机：

```text
missing → downloading(.part，可续传) → verifying → ready
                      └─失败→ error → 点击重试/下次任务续传
```

下载流程：

1. 先以4秒连接超时请求raw清单，失败后以8秒连接超时请求jsDelivr清单；限制最大1MiB，并校验格式、四个平台、文件名和URL白名单。双源均失败时不删除磁盘最后成功清单。
2. 远端版本、文件大小或 SHA-256 与本地验证标记不一致时才下载。
3. 写入 `<文件名>.part`；服务器支持 Range 时从已有长度继续，不支持时安全地从零重下。
4. 下载完成后先核对长度，再计算 SHA-256。
5. 只有校验成功才原子替换正式缓存并写 `.sha256` 验证标记。
6. 新文件失败时保留旧的最后成功文件；不把 `.part` 提供给用户。
7. 完整批次同步后清理不再被当前清单引用的旧缓存。

Android/PAD 项有一个优化：远端清单版本和当前已安装 App 的 `versionName+versionCode` 相同，且 APK 大小与 SHA-256 一致时，直接使用系统的 `applicationInfo.sourceDir`，不再额外保存一份 APK；远端版本更高时才缓存新版 APK供局域网下载。不存在“APK 无限嵌套”。

## 7. 客户端页交互

进入首页“客户端”页后：

1. 开启临时局域网 HTTP 服务，同时异步刷新远端清单。
2. PAD页面显示真实 Wi-Fi 名称、浏览器地址和二维码；输入地址与扫码二选一。HTTP网页顶部再次显示PAD当前SSID，帮助用户确认两台设备是否接入同一网络。
3. 四个平台每行显示平台图标、真实版本和状态。
4. 已校验的项目显示绿色完成标记。
5. 缺失项目显示“点击下载”。用户点击后弹出不可误解的进度窗口：圆形动画、百分比、校验阶段、错误原因和重试按钮。
6. 用户可选择“后台下载”关闭弹窗，原生下载继续；列表右侧圆形进度环内持续显示整数百分比，校验阶段显示“校验”。
7. HTTP网页每个平台提供两条明确路径：“从PAD下载”读取已校验本地缓存；“HTTPS云端下载”直接打开本次解析出的CDN URL，旁边可复制完整实际地址。
8. 离开页面只关闭局域网 HTTP 服务，不删除缓存，也不中断后台客户端同步。

浏览器网页的本地入口只能下载 `ready` 文件，不能触发任意 URL、浏览目录、上传文件或执行命令。云端入口只展示当前固定接口解析出的`https://cdn.newlink-sz.com/...`，不接收网页参数或用户输入，不能把PAD变成开放代理。包尚未准备好时显示“PAD 正在准备，请稍后刷新”，不会提供旧路径或未校验临时文件；云端地址未通过白名单时不显示直链和复制按钮。

## 8. 局域网 HTTP 边界

```text
GET /                              与设计DEMO一致的客户端下载网页
GET /health                        返回 ok
GET /download/<清单中的固定文件名>  发送已验证文件
```

- 进入客户端页时启动，离开页面或 Activity 销毁时停止。
- 优先使用端口 `8686`，占用时回退到系统端口，始终以页面显示地址为准。
- 只支持 GET 和固定白名单路径，不支持目录遍历。
- 服务没有 TLS、账号或访问令牌，只允许可信的同一局域网临时使用，不能做公网端口映射。
- 网页中的HTTPS按钮由浏览器直接连接Newlink CDN，文件不经过PAD HTTP服务器；这不改变本地服务的路由和访问边界。

## 9. 失败、回退与用户提示

| 情况 | PAD 行为 | 用户看到的内容 |
|---|---|---|
| GitHub 暂时无法访问 | 保留最后成功缓存，下次任务重试 | 已缓存项照常下载；缺失项提示错误和重试 |
| 下载中断 | 保留 `.part` | 再次点击或下次空闲任务继续 |
| 文件长度或 SHA 不符 | 删除异常临时文件，不替换正式缓存 | “校验失败”，可重试 |
| 新清单格式错误/缺平台 | 拒绝新清单 | 继续使用上次有效清单与缓存 |
| 用户刚进入页面但包未完成 | HTTP 不暴露临时包 | PAD 端点击项目查看动画；浏览器稍后刷新 |
| CDN实时地址缺失或域名不合规 | 不生成云端按钮和复制数据 | 继续使用已校验的PAD本地副本；无副本时提示稍后刷新 |
| 卸载 PAD App | Android 清除专属缓存 | 重装后重新同步 |

GitHub在部分网络环境下可能不可达或速度不稳定。小型清单已有raw/jsDelivr双源；Release大文件不能错误套用jsDelivr小资源方案。如果面向普通国内客户仍不稳定，应在相同清单中增加公司域名下的国内对象存储/CDN大文件地址并保留GitHub备用，下载校验规则不变。

## 10. 每次发布验收清单

- [ ] `Cargo.toml`、Flutter、macOS、Windows、Linux 记录的版本关系正确。
- [ ] `BIN/` 存在四端本批次文件、manifest 和 SHA256SUMS。
- [ ] `BIN/release/` 六个固定名文件齐全，与本次选定归档的大小和SHA-256一致。
- [ ] 已明确通知用户本次上传批次；用户只需登录Newlink云端管理后台的`Common`项目，按顺序覆盖六个固定项。
- [ ] 四端文件大小和 SHA-256 已记录，目标平台至少完成启动检查。
- [ ] PAD APK 使用固定 release 签名；macOS/Windows 公共发布签名状态如实记录。
- [ ] `common-data` 新 Release 的 tag、四个资产和源码 commit 对应。
- [ ] GitHub 资产回读校验完成后才更新 stable manifest。
- [ ] 云盘地址是无需登录的HTTPS文件直链，不是分享页；回读的大小和SHA-256与`release-manifest.json`一致。
- [ ] `release-manifest`最后上传；六个固定查询接口均返回本批次文件，动态CDN URL没有写入源码。
- [ ] PAD 开机任务已登记，Wi-Fi + idle 条件下能读取清单。
- [ ] 缺失客户端点击后能看到进度动画、百分比、校验、失败重试。
- [ ] 完成后 PAD 页面四项均为 ready。
- [ ] 同 Wi-Fi 浏览器下载四端文件，SHA-256 与 `BIN/` 完全一致。
- [ ] HTTP网页显示真实SSID；四个平台的HTTPS按钮和复制地址均来自本次解析，主机严格为`cdn.newlink-sz.com`。
- [ ] 至少选择一个平台，分别走PAD本地和HTTPS云端下载，确认大小、SHA-256和文件字节一致。
- [ ] 离开客户端页后 `/health` 不再可访问；重新进入时缓存立即可用。
- [ ] `CHANGELOG-KEMI.md` 记录版本、commit、Release tag、构建来源、哈希和实测结果。

## 11. 当前批次特别说明

`1.4.46+107`完成代码迁移，但真机发现`github.com/releases`首跳超时。`+108`改用Release Assets API后进一步发现Accept同时声明JSON会得到资产元数据。`+109`将API Accept严格限定为二进制，并自动清理旧JSON临时文件。当前稳定版`1.4.46+110`在此基础上补齐raw GitHub→jsDelivr双源清单、圆形百分比状态和与DEMO一致的HTTP网页。迁移后PAD APK不再内置约125 MiB的三端assets。

当前不可变Release为[`kemi-rustdesk-v1.4.46-build110`](https://github.com/caucy2026/common-data/releases/tag/kemi-rustdesk-v1.4.46-build110)，对应源码commit `1618ab449e5791b5280528623c6cddffcbec7fd4`、stable清单commit `7460ba5`。PAD真机已验证读取该清单并开始Assets API断点下载；列表百分比和HTTP DEMO页面已验收。完整四端缓存仍按网络速度后台顺序完成，未完成项不会被HTTP服务暴露。

现有 macOS ZIP 使用本地测试签名，未经 Apple Developer ID 公证；通过浏览器下载后仍可能被 Gatekeeper 阻止。它可用于内部测试，但在正式面向客户前必须由公司的 Apple Developer 账号完成 Developer ID 签名、公证和 stapling。Windows 当前候选也未做 Authenticode 签名。清单和 HTTP 分发只保证文件一致性，不会绕过操作系统安全策略。
