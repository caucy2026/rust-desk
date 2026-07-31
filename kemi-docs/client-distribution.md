# PAD 局域网客户端下载与四端打包

> 适用源码版本：`1.4.46+105`。本文件是首页“客户端”入口、临时 HTTP 服务、APK 自分发和四端离线安装包的**唯一维护说明**。涉及此功能时，先读本文件，再修改代码或制作安装包。

## 1. 目标和用户可见流程

PAD 首页的“客户端”入口用于在私有局域网中，把 KEMI 客户端交给同一 Wi-Fi 下的另一台设备安装。进入页面即启动临时 HTTP 服务；离开页面或关闭 App 即关闭，不后台常驻。

用户只需完成下面的流程：

1. PAD 与下载设备连接到同一个 Wi-Fi。页面可在用户同意 Android 位置权限后显示真实 SSID；无法读取时不编造名称，只提示确认两台设备在同一网络。
2. 在下载设备浏览器中**输入页面地址或扫描二维码，二选一**打开下载页。
3. 选择对应平台的客户端，下载并按系统提示安装。

页面只展示当前实际存在、且已核验的包。某个平台包尚未导入时显示“待导入”，没有虚假的下载链接；`1.4.46+105`已导入Windows、macOS和Linux三端。

## 2. “APK 把自己打包进去”的真实实现

这不是把 `base.apk` 再复制一份到 Android assets，也不是构建时递归把 APK 塞进自己。实际做法是：**已安装的 KEMI App 在运行时把系统保存的当前 APK 文件作为一个只读下载文件提供。**

```text
PAD 已安装的 KEMI APK
  └─ Android PackageManager / applicationInfo.sourceDir
       └─ ClientDistributionServer
            └─ GET /download/KEMI-remote-desktop-<version>.apk
                 └─ 同 Wi-Fi 浏览器下载
```

对应代码在 `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/ClientDistributionServer.kt`：

- `packageEntries()` 为 Android 条目设置 `assetPath = null`；
- `isAvailable()` 因而检查 `File(context.applicationInfo.sourceDir)`；
- `route()` 收到该条目的固定下载地址时，调用 `writeFile()` 流式读取 `sourceDir`；
- 文件名由 `PackageManager` 读取当前 `versionName()` 生成，例如 `KEMI-remote-desktop-1.4.46.apk`。

因此有四个重要结果：

| 项目 | 结论 |
|---|---|
| 下载到的 APK | 就是此刻 PAD 已安装的那一份 APK，版本、签名、内容一致。 |
| APK 体积 | 不会因“再嵌入自己”翻倍；Android 包不在 `assets` 里保存第二份 APK。 |
| Debug / Release | PAD 装的是 debug 包，就会分发 debug 包；装的是正式签名 release 包，就会分发正式包。分发服务不会替换签名或版本。 |
| 升级顺序 | 必须先构建并安装最终 PAD APK，再让它开启下载页；下载页不能产生新 APK。 |

这是一种“运行中应用自分发”而非“APK 自嵌套”。它适合局域网测试和现场安装，不替代正式应用商店、MDM 或官网发布。

从构建关系看也不存在递归：

```text
最终 PAD APK = KEMI Android 程序 + macOS ZIP + Windows EXE + Linux AppImage
```

等号右边没有“最终 PAD APK”这一项。HTTP 服务启动后才由 Android 系统告诉程序当前安装文件的位置，服务只打开并发送该文件，不会修改它、重新执行 Gradle，也不会生成“包含下载副本的新 APK”。客户端把文件下载到另一台 Android 设备，只是得到相同字节的一份副本；新设备安装后又可以读取自己的安装文件提供下载，文件大小和层级仍保持不变。

当前实现有一个必须保留的前提：PAD 使用本项目直接构建、侧载的**单体 APK**。如果以后改为 Google Play/AAB 或其他 split APK 安装方式，`applicationInfo.sourceDir` 通常只指向 `base.apk`，单独下载它可能缺少 ABI、语言或资源 split 而无法安装。届时不能继续宣称“Android 可直接自分发”，必须改为提供独立的通用 release APK，或完整导出并安装所有 split APK，再同步修改代码、页面和本文件。

## 3. 四端制品如何进入 PAD

Android 与其余三端的来源不同：

| 下载项 | CPU / 格式 | PAD 中的来源 | 是否增加 PAD APK 体积 | 当前状态 |
|---|---|---|---|---|
| PAD / Android | APK | 已安装 App 的 `applicationInfo.sourceDir` | 否 | 已可下载 |
| macOS | Apple Silicon / ZIP | `assets/client-dist` 中的已签名 App ZIP | 是 | 已可下载 |
| Windows | x64 / EXE | `assets/client-dist` 中的已核验 EXE | 是 | 已可下载 |
| Linux | x86_64 / AppImage | `assets/client-dist` 中的已核验 AppImage | 是 | 已可下载 |

静态包的固定目录和文件名如下；文件名是 HTTP 白名单的一部分，**不可随意更名**：

```text
flutter/android/app/src/main/assets/client-dist/
├── KEMI-remote-desktop-windows-x64.exe
├── KEMI-remote-desktop-macos-arm64.zip
└── KEMI-remote-desktop-linux-x86_64.AppImage
```

`bundledPackages()` 只认识上述三项。文件存在时，Android Gradle 会将它们编入 PAD APK，`context.assets.open()` 在下载时流式输出；文件缺失时网页和 PAD 页面都只显示“待导入”。

最终 PAD APK 的体积会近似增加三个静态包各自压缩后的大小，并需要额外安装空间；Android 自身 APK 不贡献第二份体积。替换静态包时除了校验文件，还要确认 PAD 的存储空间、APK 安装耗时和同网下载耗时仍可接受。若四端包继续增大，应把非 Android 包迁移到 PAD 本地可更新的独立文件目录或受控发布服务器，而不是无限扩大 APK assets。

当前已内置 macOS Apple Silicon 包：`KEMI-remote-desktop-macos-arm64.zip`，对应 `1.4.46+104`，大小 `25,918,515` 字节，SHA-256 为：

```text
b0a826644814c488e2861d66ecd49b56983b270174e3de8de895b8f6ae06c2c4
```

当前Windows x64 EXE对应源码commit `4e30063b9a0b293eca18a355264fbbe6852be84e`、focused run `30590581209`，大小22,637,056字节，SHA-256为：

```text
9cc13f6780a39388d590b2f7dc575b1e42712da630f7ae801947d4465867d6ad
```

当前Linux x86_64 AppImage复用同一run的成功DEB，由补包run `30593128802`生成，大小82,983,416字节，SHA-256为：

```text
2276a6860c482b21f6ab4d9bb2502c5dadd69d2a140ef038506c638eebe5fa44
```

两份不会随Actions 14天保留期过期的候选文件、manifest和校验表位于GitHub prerelease：

```text
tag: kemi-client-4e30063b9a0b293eca18a355264fbbe6852be84e
https://github.com/caucy2026/rust-desk/releases/tag/kemi-client-4e30063b9a0b293eca18a355264fbbe6852be84e
```

该tag已核验直接指向源码commit `4e30063b9`。Windows EXE没有Authenticode签名，Linux AppImage也未签名，均为团队测试候选，不是公开发布签名包。

最终PAD `1.4.46+105`已在`192.168.1.10:5555`实机安装；从其HTTP服务实际下载Windows、macOS、Linux三端文件后的哈希均与导入前一致。Windows和Linux仍需在各自目标系统完成GUI启动、远控和文件传输验收，不能把“构建与PAD下载通过”写成“目标平台功能已全部通过”。

## 4. 实现结构和服务边界

```text
Flutter ClientDownloadPage
  ├─ initState() → MethodChannel: client_distribution_start
  ├─ 显示 IP、SSID、二维码和可用包状态
  └─ dispose()   → MethodChannel: client_distribution_stop
                 ↓
Android MainActivity
                 ↓
ClientDistributionServer（仅页面存活期间）
  ├─ /                         简化下载网页
  ├─ /health                   返回 ok
  └─ /download/<固定文件名>    仅提供白名单中的一个文件
       ├─ Android → sourceDir（当前已安装 APK）
       └─ macOS/Windows/Linux → Android assets
```

相关文件职责：

| 文件 | 职责 |
|---|---|
| `flutter/lib/mobile/pages/client_download_page.dart` | 页面生命周期、地址/二维码/SSID 与平台卡片 UI。 |
| `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainActivity.kt` | Flutter MethodChannel 转发、Wi-Fi 权限与 Activity 销毁兜底停止服务。 |
| `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/ClientDistributionServer.kt` | HTTP 服务、包清单、路由白名单、APK/asset 文件流。 |
| `flutter/android/app/src/main/assets/client-dist/` | 经过核验、要随 PAD APK 一起交付的非 Android 静态包。 |

服务规则：

- 优先监听 `8686`；端口占用时使用系统分配端口，页面显示实际地址。
- 仅接受 `GET`，仅允许 `/`、`/health` 和精确的 `/download/<固定文件名>`；没有目录浏览、任意路径读文件、上传、远程命令或网络转发。
- 下载使用 `Content-Disposition: attachment`、`Cache-Control: no-store`、`Connection: close`；asset 可能被 APK 压缩，故以连接关闭作为文件结束标记。
- `dispose()` 和 `MainActivity.onDestroy()` 都会停止服务，避免离开页面仍暴露下载地址。
- 当前服务不含账号登录、TLS 或访问令牌；仅可用于受信任的私有局域网，不能在公共 Wi-Fi、端口映射或公网环境开启。

## 5. 打包前的硬性需求

每个平台制品进入 `client-dist` 前，必须同时满足以下条件：

1. **版本一致**：产品版本须与本次准备分发的 KEMI 版本一致；Android 的 `versionName/versionCode`、Mac 的 `CFBundleShortVersionString/CFBundleVersion`、Windows/Linux 的安装包元数据都要记录。
2. **架构明确**：当前命名只支持 Windows x64、macOS arm64、Linux x86_64。需要 Intel Mac、Windows ARM64 或 Linux ARM64 时，先新增独立文件名、UI 文案和白名单，不能把不同架构替换进原文件。
3. **来源可追溯**：记录源 Git commit、构建机/CI run、构建命令和 SHA-256。GitHub artifact 仅在对应 job 成功、来源 commit 匹配、下载后核验通过时才可用。
4. **可安装**：至少在对应平台完成一次安装/启动验证；Mac 还必须验证签名，Windows/Linux 应验证其签名或包完整性。
5. **先放包，后构建 PAD**：静态包是在 Android 构建阶段编入 APK。替换任何 `.zip`、`.exe`、`.AppImage` 后，不重新构建 PAD APK 就不会生效。
6. **大文件治理**：二进制不作为普通源码历史反复提交。Windows/Linux 云端候选包统一保存到 `kemi-<完整commit>` GitHub prerelease，Mac 本地交付包保留在 `BIN/`；每次都保留版本、commit、run ID 和哈希记录。

## 6. 标准打包流程

### 6.1 统一顺序

```text
完成并验证四端候选制品
        ↓
记录版本 / 架构 / commit / SHA-256
        ↓
将 macOS、Windows、Linux 静态包导入 client-dist
        ↓
构建最终 PAD APK（静态包此时被编入）
        ↓
安装最终 PAD APK 到测试 PAD
        ↓
从 PAD 下载页下载四端包并逐端安装验证
```

不要先安装 PAD、后替换 `client-dist` 文件，然后以为已安装 PAD 会自动更新。Android 自分发项例外：它永远读取当前已安装 APK；但其余三端只能来自**构建该 APK 时**已存在的 assets。

### 6.2 macOS Apple Silicon 包

macOS 是当前本机可完成的 GUI 包。必须先构建、复制 `service`、使用固定证书签名并验证，然后压缩 `.app`。示例命令如下（构建环境路径以实际机器为准）：

```bash
cd /Users/newlink/kemi/RustDesk/client
env VCPKG_ROOT=/Users/newlink/kemi/RustDesk/client/vcpkg \
  cargo build --locked --features flutter --release

cd flutter
env PATH=/Users/newlink/.gem/ruby/2.6.0/bin:$PATH \
  RUBYOPT=-rlogger \
  FLUTTER_XCODE_ARCHS=arm64 \
  FLUTTER_XCODE_ONLY_ACTIVE_ARCH=YES \
  /Users/newlink/flutter/bin/flutter build macos --release --no-pub

app='build/macos/Build/Products/Release/KEMI-远程桌面.app'
cp ../target/release/service "$app/Contents/MacOS/service"
../res/sign-kemi-local-macos.sh "$app"
codesign --verify --deep --strict --verbose=2 "$app"

ditto -c -k --sequesterRsrc --keepParent "$app" \
  android/app/src/main/assets/client-dist/KEMI-remote-desktop-macos-arm64.zip
shasum -a 256 android/app/src/main/assets/client-dist/KEMI-remote-desktop-macos-arm64.zip
unzip -t android/app/src/main/assets/client-dist/KEMI-remote-desktop-macos-arm64.zip
```

固定测试证书仅适用于团队本地测试；正式外发 Mac 包必须按 `kemi-docs/macos-configuration.md` 改用 Developer ID 签名与公证。不可用 ad-hoc 签名替换当前固定签名，否则 macOS TCC 可能把升级包认作新应用并丢失授权。

### 6.3 Windows x64 与 Linux x86_64 包

原则是**本地优先、云端只做本地无法可靠交付的目标**。当前 Apple Silicon Mac 不能生成 Windows/MSVC Flutter GUI 安装包，也不能把 Linux GUI 所需的 GTK、音视频/X11、x64 vcpkg 和 Flutter Linux bundle 可靠交叉组成 AppImage；本机 Zig 脚本只能构建 Linux 服务端，不能替代客户端。因此 Windows x64 EXE 与 Linux x86_64 AppImage固定由 `.github/workflows/kemi-distribution.yml` 并行构建。详细分工、监控和失败处理见 `kemi-docs/ci-build.md`。

导入前最小核验清单：

```bash
# 在制品所在目录执行；每次都将输出写入本次构建记录
shasum -a 256 KEMI-remote-desktop-windows-x64.exe
shasum -a 256 KEMI-remote-desktop-linux-x86_64.AppImage
file KEMI-remote-desktop-windows-x64.exe
file KEMI-remote-desktop-linux-x86_64.AppImage
```

核验后分别放入第 3 节的固定路径和文件名。Windows必须在Windows x64上补做安装/启动；Linux至少在目标发行版x86_64上执行`chmod +x`后补做启动。`1.4.46`当前已完成云构建、格式/架构/哈希和PAD真实HTTP下载核验，目标平台GUI、远控和文件传输结果继续单独登记。

### 6.4 构建和安装最终 PAD APK

静态离线包确认后再构建 PAD：

```bash
cd /Users/newlink/kemi/RustDesk/client/flutter
env PATH=/private/tmp/kemi-flutter-3.29.3/bin:$PATH flutter build apk \
  --release --target-platform android-arm64 --no-pub

adb -s 192.168.1.10:5555 uninstall com.newlinksz.kemi.remote
adb -s 192.168.1.10:5555 install build/app/outputs/flutter-apk/app-release.apk
adb -s 192.168.1.10:5555 shell dumpsys package com.newlinksz.kemi.remote \
  | rg 'versionName|versionCode|sourceDir'
```

上例省略了受控release签名环境变量；完整签名命令以`android-release-signing.md`为准。当前Android依赖实际使用Flutter 3.29.3；不要用共享SDK的任意最新版，也不要用缺少`extended_text 14`所需selection API的3.24.5直接构建。安装后再次读取`versionName/versionCode`。因为Android下载项直接服务`sourceDir`，这一步能同时证明“PAD本机运行的版本”和“它会提供给别人下载的APK”完全一致。

## 7. 最终验收与记录模板

每次四端包更新，按下面顺序验收：

1. 在 PAD 进入“客户端”，日志应有 `Client download service started`，页面显示实际 IPv4 与端口。
2. 同 Wi-Fi 设备访问 `http://<PAD-IP>:<端口>/health`，应返回 `ok`；访问根路径应显示下载页。
3. 下载 Android APK，比较其 SHA-256 与 PAD 的 `sourceDir`，再检查下载包签名和 `versionName/versionCode`。
4. 下载每个已显示的 macOS / Windows / Linux 包，比较其 SHA-256 与导入 assets 前的记录，并在对应系统安装或启动。
5. 离开客户端页，原地址必须不可访问，日志应有 `Client download service stopped`。

每次在 `kemi-docs/CHANGELOG-KEMI.md` 写入至少以下数据：

```text
KEMI 版本：1.x.y+build
源 commit：<完整 Git commit>
PAD APK：文件名、versionName/versionCode、SHA-256、签名类型
macOS arm64 ZIP：版本、SHA-256、签名身份、安装验证结果
Windows x64 EXE：版本、SHA-256、来源 CI run、安装验证结果
Linux x86_64 AppImage：版本、SHA-256、来源 CI run/本地构建、启动验证结果
```

## 8. 常见误解和排查

| 现象 | 根因 | 处理 |
|---|---|---|
| 已替换 Mac/Windows/Linux 文件，但 PAD 网页还是旧包 | 已安装 PAD APK 的 assets 不会热更新。 | 重新构建并安装 PAD APK，再打开客户端下载页。 |
| Android 下载文件版本与源码不一致 | 服务读取的是已安装 `sourceDir`，不是当前工作区的构建输出。 | 卸载/安装最终 APK 后再测试，检查 `dumpsys package`。 |
| 包不显示 | 静态包缺失、文件名不匹配或 assets 无法打开。 | 核对第 3 节固定路径与文件名；不要改 `bundledPackages()` 白名单。 |
| 浏览器打不开地址 | 不在同 Wi-Fi、没有可用 IPv4、端口不是 8686 回退后的实际端口，或已离开页面。 | 以页面显示的完整地址为准，重新进入客户端页。 |
| 下载到错误架构 | 用错误包覆盖了固定文件名。 | 不覆盖；新增架构时同步新增文件名、包条目、UI 文案、文档和验收。 |
| Mac 更新后权限看似丢失 | 包被 ad-hoc/其他证书重新签名或解包修改。 | 使用固定测试签名重签；正式发布使用 Developer ID + 公证。 |

## 9. 变更规则

以下改动必须同步更新本文件、`CHANGELOG-KEMI.md` 和版本对应关系：

- 新增或删除平台、CPU 架构、文件格式或下载文件名；
- 改动 HTTP 路由、端口、生命周期、认证或网络访问范围；
- 替换任何内置静态包；
- 改动 APK 自分发的来源、签名或版本策略。

不要把安装包制作步骤散落在 README、CI 文档或聊天记录中：本文件描述“如何把四端制品交付到 PAD 下载页”，`ci-build.md` 仅描述“如何得到 Windows/Linux 等候选制品”，`macos-configuration.md` 仅描述“如何签名和交付 macOS App”。
