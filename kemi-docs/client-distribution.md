# KEMI 四端客户端发布、云盘同步与局域网分发

> 适用版本：`1.4.46+119` 起。本文是四端制品、项目 `BIN/`、固定云盘文件名、GitHub `caucy2026/common-data`、PAD 后台缓存和局域网 HTTP/HTTPS 下载入口的唯一维护说明。

## 当前待上传正式批次：1.4.77+184 PAD单端更新

`BIN/release`已在2026-08-08更新为`1.4.77+184-single-display-local-tools`批次：PAD为`1.4.77+184`，普通单屏设备的远程ID、密码、远控键盘和文件传输全部留在当前屏幕；macOS继续使用已完成Developer ID签名、Apple公证和票据装订的`1.4.75+182`原文件，Windows/Linux继续使用`1.4.75`原文件。本地六文件一致性门禁已通过，但Newlink云端实际版本仍必须以在线`release-manifest`回读为准；管理员完成PAD、SHA256SUMS和最后的release-manifest三项覆盖上传前，本批次状态为`pending-manual-upload`，不能把本地准备完成写成云端已发布。

客户端页Android条目同时显示云端版本和小字“（本机版本 xxxxx）”。只有数值比较确认云端`versionName + versionCode`严格高于本机时才显示“更新”；相同或更旧版本不显示。点击后下载清单指定的固定签名APK，核对大小和SHA-256；安装前询问是否把当前已安装APK备份到系统“下载”目录。选“是”先备份再打开系统安装器，选“否”直接打开系统安装器，两条路径都不跳转RustDesk官网。未知来源授权往返必须保留选择，不能重复备份。

本批次只需覆盖上传`KEMI-PAD`、`SHA256SUMS`和最后的`release-manifest`，桌面三端不得重复替换。上传完成后必须回读六个固定HTTPS接口，并在PAD上验证“发现1.4.77+184 → 下载及双哈希校验 → 可选备份旧APK → 系统安装 → 新版本启动”；局域网HTTP和hbbc云端入口也必须回读到同一批次，不能只根据管理后台显示“上传成功”结束验收。

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

若终端与PAD无法局域网互访：
PAD页面或本地网页中的固定hbbc HTTP备用地址
        ↓ 302
Newlink国内云盘HTTPS文件
```

这里有三个强制原则：

1. `BIN/` 根目录保存带真实版本号的不可变历史归档；`BIN/release/` 只保存当前准备上传云盘的固定文件名副本。聊天记录、Actions 临时 Artifact 和 PAD 缓存都不能替代这两层。
2. `common-data` 的普通 Git 历史只保存小型清单和说明，大型客户端放 GitHub Release，避免仓库因每个版本重复提交二进制而失控。
3. 先上传四个文件，逐个回读校验，最后才更新稳定清单。PAD 只相信稳定清单，因此不会看到“清单已更新但客户端还没传完”的半成品发布。

### 1.1 1.4.50新增的同步一致性门禁

- Newlink manifest、SHA256SUMS和四端固定文件上传存在短暂时间窗时，PAD最多重试三次，不立即把临时不一致解释成主源永久失效。
- GitHub备用manifest的Android版本早于当前已安装PAD时拒绝落盘，不能让新版PAD页面退回并下载1.4.46等旧批次。
- 页面重复刷新会标记高优先级全量同步；正在运行的旧大文件下载在数据块边界保存断点并让位，新manifest不会再被单线程`syncing`状态阻塞数十分钟。
- PAD原生页面的局域网地址、复制和二维码统一指向`http://PAD-IP:8686/clients`。该路由直接显示四平台下载区；`/`保留完整双通道导读，兼容旧书签。
- 页面展示的“目标版本”和PAD当前“缓存版本”必须区分。云端仍发布1.4.49时，新安装的1.4.50测试PAD继续提供1.4.49下载是正确行为，不得用本机测试APK单独篡改正式manifest。

## 2. 四端版本、归档名和云盘固定名

源码产品版本来自根`Cargo.toml`，当前发布版本为`1.4.75`，PAD/macOS build为`182`。`BIN/release`六文件已取消历史混合批次，四端版本、大小和哈希由同目录manifest如实记录。功能发布提交为`62473ba7f`；Windows/Linux focused构建使用`66c71a888`，后者只包含Rust 1.75依赖兼容锁定，清单必须同时保留两个完整提交号和run ID。

`BIN/`根目录中当前批次的版本化归档为：

```text
BIN/
├── KEMI-远程办公-PAD-1.4.75+182-release.apk
├── KEMI-远程办公-macOS-arm64-1.4.75+182-notarized.zip
├── KEMI-远程办公-Windows-x64-1.4.75-66c71a888.exe
└── KEMI-远程办公-Linux-x86_64-1.4.75-66c71a888.AppImage
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
| PAD / Android | `flutter/pubspec.yaml` | `versionName=1.4.75`、`versionCode=182`，清单写`1.4.75+182` |
| macOS arm64 | App `Info.plist` | `CFBundleShortVersionString=1.4.75`、`CFBundleVersion=182`，Developer ID公证已Accepted并装订 |
| Windows x64 | focused构建环境与候选manifest | 产品版本`1.4.75`，绑定源码`66c71a888`与run `31184071063` |
| Linux x86_64 | focused构建环境、AppImage和候选manifest | 产品版本`1.4.75`，绑定源码`66c71a888`与run `31184071063` |

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
7. PAD原生“客户端”页第一框同时显示两个主入口：左侧为当前动态`http://PAD-IP:8686`，右侧为固定`http://kemi-chat.newlinksz.com:21120/kemi-desk`；两项各自显示完整地址、复制操作和对应二维码。第二框只展示按平台切换的Newlink HTTPS“云备份下载”，并逐字说明“仅作为上面两种下载均失效情况下的备案”。
8. HTTP网页顶部在一个统一外边框中提供相同的两个主入口。地址本身可点击，不增加重复打开/复制按钮。四个平台卡片继续提供已校验的“从PAD下载”，并恢复当前Newlink HTTPS实际文件的“云备份下载”；每个云备份按钮后分别使用相同备案说明。
9. 离开页面只关闭局域网 HTTP 服务，不删除缓存，也不中断后台客户端同步。

浏览器网页的本地入口只能下载 `ready` 文件，不能触发任意 URL、浏览目录、上传文件或执行命令。云端主入口是代码固定的`http://kemi-chat.newlinksz.com:21120/kemi-desk`，不接受用户输入；“云备份下载”只展示PAD通过固定Newlink接口实时解析、且通过`https + cdn.newlink-sz.com`白名单的文件地址。包尚未准备好时本地入口显示“PAD 正在准备，请稍后刷新”，云端主入口仍可独立使用。

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
- 网页中的“云端下载”打开hbbc完整页面；“云备份下载”由浏览器直接访问当前Newlink HTTPS CDN文件。二者文件都不经过PAD HTTP服务器，不改变本地服务的路由和访问边界。

## 9. 失败、回退与用户提示

| 情况 | PAD 行为 | 用户看到的内容 |
|---|---|---|
| GitHub 暂时无法访问 | 保留最后成功缓存，下次任务重试 | 已缓存项照常下载；缺失项提示错误和重试 |
| 下载中断 | 保留 `.part` | 再次点击或下次空闲任务继续 |
| 文件长度或 SHA 不符 | 删除异常临时文件，不替换正式缓存 | “校验失败”，可重试 |
| 新清单格式错误/缺平台 | 拒绝新清单 | 继续使用上次有效清单与缓存 |
| 用户刚进入页面但包未完成 | HTTP 不暴露临时包 | PAD 端点击项目查看动画；浏览器稍后刷新 |
| PAD与下载终端被AP隔离 | PAD本地8686仍运行但终端无法访问 | 使用hbbc完整云端页`/kemi-desk` |
| hbbc不可达或配置无效 | 不影响PAD缓存与8686本地服务 | 若本地入口也不可用，最后使用平台卡片中的Newlink HTTPS云备份 |
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
- [ ] HTTP网页显示真实SSID；首屏同时显示当前`PAD-IP:8686`和`kemi-chat.newlinksz.com:21120/kemi-desk`两个主入口。
- [ ] hbbc完整云端页返回200；四个平台“云备份下载”均是当前`https://cdn.newlink-sz.com/...`白名单文件。
- [ ] 至少选择一个平台，分别走PAD本地、hbbc云端页和Newlink云备份，确认下载文件正确。
- [ ] 离开客户端页后 `/health` 不再可访问；重新进入时缓存立即可用。
- [ ] `CHANGELOG-KEMI.md` 记录版本、commit、Release tag、构建来源、哈希和实测结果。

## 11. 当前批次特别说明

`1.4.46+107`完成代码迁移，但真机发现`github.com/releases`首跳超时。`+108`改用Release Assets API后进一步发现Accept同时声明JSON会得到资产元数据。`+109`将API Accept严格限定为二进制，并自动清理旧JSON临时文件。当前稳定版`1.4.46+110`在此基础上补齐raw GitHub→jsDelivr双源清单、圆形百分比状态和与DEMO一致的HTTP网页。迁移后PAD APK不再内置约125 MiB的三端assets。

当前不可变Release为[`kemi-rustdesk-v1.4.46-build110`](https://github.com/caucy2026/common-data/releases/tag/kemi-rustdesk-v1.4.46-build110)，对应源码commit `1618ab449e5791b5280528623c6cddffcbec7fd4`、stable清单commit `7460ba5`。PAD真机已验证读取该清单并开始Assets API断点下载；列表百分比和HTTP DEMO页面已验收。完整四端缓存仍按网络速度后台顺序完成，未完成项不会被HTTP服务暴露。

> 历史说明：本段描述的是`1.4.46+110`时期的旧制品，不能用于判断当前release。当前`1.4.75+182` macOS ZIP已经使用Apple Developer ID签名、通过公证并装订票据，解压后的Gatekeeper结果为`accepted / Notarized Developer ID`。Windows当前候选仍未做Authenticode签名。清单和HTTP分发只保证文件一致性，不能替代各平台签名。
