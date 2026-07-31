# KEMI 四端客户端发布、PAD 自动同步与局域网分发

> 适用版本：`1.4.46+107` 起。本文是四端制品、项目 `BIN/`、GitHub `caucy2026/common-data`、PAD 后台缓存和局域网 HTTP 下载的唯一维护说明。

## 1. 需求结论

四个平台客户端统一按下面的链路管理：

```text
各平台构建并验收
        ↓
项目根目录 BIN/（本地发布基准，四端齐全）
        ↓
GitHub caucy2026/common-data Release（不可变二进制）
        ↓ 最后发布 stable/manifest.json
PAD 开机后联网且空闲时比较版本并增量下载
        ↓
PAD 私有缓存（最后一次校验成功的文件）
        ↓ 用户进入“客户端”页才开启
PAD 局域网 HTTP 服务
        ↓
同一 Wi-Fi 的 Android / macOS / Windows / Linux 设备下载
```

这里有三个强制原则：

1. `BIN/` 每次必须同时保存本次准备发布的四端客户端、清单和校验表；聊天记录、Actions 临时 Artifact 和 PAD 缓存都不能替代它。
2. `common-data` 的普通 Git 历史只保存小型清单和说明，大型客户端放 GitHub Release，避免仓库因每个版本重复提交二进制而失控。
3. 先上传四个文件，逐个回读校验，最后才更新稳定清单。PAD 只相信稳定清单，因此不会看到“清单已更新但客户端还没传完”的半成品发布。

## 2. 四端版本和文件名

产品版本来自根 `Cargo.toml`，当前为 `1.4.46`。Flutter/PAD 和 macOS 还需要构建号，当前下一版为 `+107`。Windows/Linux 如果产物元数据只支持三段版本，记录为 `1.4.46`，但仍在本次发布清单中绑定到发布批次 `1.4.46+107`。

`BIN/` 当前发布批次必须包含：

```text
BIN/
├── KEMI-远程桌面-PAD-1.4.46+107-release.apk
├── KEMI-远程桌面-macOS-arm64-1.4.46+107.zip
├── KEMI-远程桌面-Windows-x64-1.4.46.exe
├── KEMI-远程桌面-Linux-x86_64-1.4.46.AppImage
├── KEMI-client-manifest-1.4.46+107.json
└── KEMI-client-SHA256SUMS-1.4.46+107.txt
```

文件名必须带真实版本。旧版可以移到 `BIN/archive/`，但不能用新版文件名包装旧字节，也不能因为某个平台尚未构建完成就复制旧包冒充本次版本。macOS 当前是 Apple Silicon，Windows/Linux 当前是 x86_64；新增架构时新增独立目标，不能覆盖现有架构文件。

每个平台的“版本一致”含义如下：

| 平台 | 版本来源 | 本批次要求 |
|---|---|---|
| PAD / Android | `flutter/pubspec.yaml` | `versionName=1.4.46`、`versionCode=107`，清单写 `1.4.46+107` |
| macOS arm64 | App `Info.plist` | `CFBundleShortVersionString=1.4.46`、`CFBundleVersion=107` |
| Windows x64 | EXE 元数据 | 产品版本 `1.4.46`，清单同时记录发布批次 |
| Linux x86_64 | 构建源码和文件名 | 产品版本 `1.4.46`，清单同时记录发布批次 |

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
tag: kemi-rustdesk-v1.4.46-build107
assets:
  KEMI-remote-desktop-PAD-1.4.46+107.apk
  KEMI-remote-desktop-macos-arm64-1.4.46+107.zip
  KEMI-remote-desktop-windows-x64-1.4.46.exe
  KEMI-remote-desktop-linux-x86_64-1.4.46.AppImage
  manifest.json
  SHA256SUMS.txt
```

Release 资产一旦进入稳定清单就视为不可变；需要修复时增加构建号和新 tag，不覆盖同名文件。这样 PAD 的断点续传、哈希校验和问题追溯都有稳定依据。

## 4. 稳定清单格式

PAD 固定读取：

```text
https://raw.githubusercontent.com/caucy2026/common-data/main/kemi-rustdesk/stable/manifest.json
```

清单格式版本为 1，必须一次包含四个平台：

```json
{
  "schema_version": 1,
  "channel": "stable",
  "release_version": "1.4.46+107",
  "source_commit": "完整的 rust-desk 源码 commit",
  "generated_at": "2026-07-31T12:00:00+08:00",
  "targets": [
    {
      "id": "android",
      "version": "1.4.46+107",
      "architecture": "arm64-v8a",
      "file": "KEMI-remote-desktop-PAD-1.4.46+107.apk",
      "size": 12345678,
      "sha256": "64位小写SHA-256",
      "url": "https://github.com/caucy2026/common-data/releases/download/kemi-rustdesk-v1.4.46-build107/KEMI-remote-desktop-PAD-1.4.46%2B107.apk"
    }
  ]
}
```

其余三个目标的 `id` 必须依次使用 `windows`、`macos`、`linux`。文件名只允许 ASCII 字母、数字、点、下划线、加号和连字符；URL 必须是 HTTPS 且属于 GitHub 下载域名白名单。

清单中的 SHA-256 用于确认下载字节与发布字节一致。它能防止传输错误和错误文件混入，但不能替代平台代码签名：Android 仍检查固定 release 签名，macOS 正式外发仍需 Developer ID 签名与公证，Windows 正式外发仍应做 Authenticode 签名。

## 5. 发布顺序与未完成构建的处理

每次发布严格执行：

1. 冻结本次源码 commit 和产品版本。
2. 本地能编译的 PAD/macOS 在本地构建；Windows/Linux 优先由对应平台同事本机构建，本地确实不具备目标环境时才使用 GitHub Actions。
3. 每个平台独立验收并进入候选区，不修改稳定清单。
4. 四个候选文件全部齐备后复制到 `BIN/`，生成大小、SHA-256、源码 commit、构建机或 Actions run ID 记录。
5. 创建新的 `common-data` Release，上传四端文件、批次 manifest 和 `SHA256SUMS.txt`。
6. 从 GitHub 回读资产元数据，至少复核文件名和大小；能下载回读时再次核对 SHA-256。
7. 最后一个单独提交更新 `kemi-rustdesk/stable/manifest.json`。
8. PAD 读取稳定清单并完成下载后，验证局域网 HTTP 下载的四个文件与 `BIN/` 哈希一致。

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

1. 请求远端清单，限制最大 1 MiB，并校验格式、四个平台、文件名和 URL 白名单。
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
2. 页面显示真实 Wi-Fi 名称、浏览器地址和二维码；输入地址与扫码二选一。
3. 四个平台每行显示平台图标、真实版本和状态。
4. 已校验的项目显示绿色完成标记。
5. 缺失项目显示“点击下载”。用户点击后弹出不可误解的进度窗口：圆形动画、百分比、校验阶段、错误原因和重试按钮。
6. 用户可选择“后台下载”关闭弹窗，原生下载继续；页面行内仍显示进度条。
7. 离开页面只关闭局域网 HTTP 服务，不删除缓存，也不中断后台客户端同步。

浏览器网页只能下载 `ready` 文件，不能触发任意 URL、浏览目录、上传文件或执行命令。包尚未准备好时显示“PAD 正在准备，请稍后刷新”，不会提供旧路径或未校验临时文件。

## 8. 局域网 HTTP 边界

```text
GET /                              简化下载网页
GET /health                        返回 ok
GET /download/<清单中的固定文件名>  发送已验证文件
```

- 进入客户端页时启动，离开页面或 Activity 销毁时停止。
- 优先使用端口 `8686`，占用时回退到系统端口，始终以页面显示地址为准。
- 只支持 GET 和固定白名单路径，不支持目录遍历。
- 服务没有 TLS、账号或访问令牌，只允许可信的同一局域网临时使用，不能做公网端口映射。

## 9. 失败、回退与用户提示

| 情况 | PAD 行为 | 用户看到的内容 |
|---|---|---|
| GitHub 暂时无法访问 | 保留最后成功缓存，下次任务重试 | 已缓存项照常下载；缺失项提示错误和重试 |
| 下载中断 | 保留 `.part` | 再次点击或下次空闲任务继续 |
| 文件长度或 SHA 不符 | 删除异常临时文件，不替换正式缓存 | “校验失败”，可重试 |
| 新清单格式错误/缺平台 | 拒绝新清单 | 继续使用上次有效清单与缓存 |
| 用户刚进入页面但包未完成 | HTTP 不暴露临时包 | PAD 端点击项目查看动画；浏览器稍后刷新 |
| 卸载 PAD App | Android 清除专属缓存 | 重装后重新同步 |

GitHub 在部分网络环境下可能不可达或速度不稳定。本版按用户指定以 `common-data` 为源；如果要面对普通国内客户，后续应在相同清单中增加公司域名下的国内对象存储/CDN 主地址并保留 GitHub 作为备用，下载校验规则不变。

## 10. 每次发布验收清单

- [ ] `Cargo.toml`、Flutter、macOS、Windows、Linux 记录的版本关系正确。
- [ ] `BIN/` 存在四端本批次文件、manifest 和 SHA256SUMS。
- [ ] 四端文件大小和 SHA-256 已记录，目标平台至少完成启动检查。
- [ ] PAD APK 使用固定 release 签名；macOS/Windows 公共发布签名状态如实记录。
- [ ] `common-data` 新 Release 的 tag、四个资产和源码 commit 对应。
- [ ] GitHub 资产回读校验完成后才更新 stable manifest。
- [ ] PAD 开机任务已登记，Wi-Fi + idle 条件下能读取清单。
- [ ] 缺失客户端点击后能看到进度动画、百分比、校验、失败重试。
- [ ] 完成后 PAD 页面四项均为 ready。
- [ ] 同 Wi-Fi 浏览器下载四端文件，SHA-256 与 `BIN/` 完全一致。
- [ ] 离开客户端页后 `/health` 不再可访问；重新进入时缓存立即可用。
- [ ] `CHANGELOG-KEMI.md` 记录版本、commit、Release tag、构建来源、哈希和实测结果。

## 11. 当前批次特别说明

`1.4.46+107` 是从“把 macOS/Windows/Linux 全部塞进 PAD APK”迁移到“GitHub 清单 + PAD 持久缓存”的首版。迁移完成后 PAD APK 不再内置约 125 MiB 的三端 assets，体积会显著下降。

现有 macOS ZIP 使用本地测试签名，未经 Apple Developer ID 公证；通过浏览器下载后仍可能被 Gatekeeper 阻止。它可用于内部测试，但在正式面向客户前必须由公司的 Apple Developer 账号完成 Developer ID 签名、公证和 stapling。Windows 当前候选也未做 Authenticode 签名。清单和 HTTP 分发只保证文件一致性，不会绕过操作系统安全策略。
