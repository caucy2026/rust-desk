# KEMI 本地与 GitHub 构建分工、监控和交付

> 适用版本：`1.4.44+102`。本文件是 KEMI 客户端“哪些在本地构建、哪些交给 GitHub、怎样并行、怎样持续监控、怎样取得并导入制品”的唯一执行说明。四端安装包进入 PAD 的规则见 `client-distribution.md`；macOS 签名和权限见 `macos-configuration.md`。

## 1. 固定原则

1. **本地能可靠完成的目标不等待 GitHub**；云端只承担当前 Mac 无法可靠交付的目标。
2. **本地与云端并行**。本地预检通过并形成一个候选 commit 后，立即推送；云端开始 Windows/Linux 构建时，本地继续构建 PAD、Mac 和准备验收，不互相等待。
3. **一次候选提交对应一次云端 run**。记录完整 commit SHA、run ID 和 URL；出现新 commit 后，旧 run 自动取消，不继续浪费 runner。
4. **失败立即处理首个失败步骤**。不能等整个大矩阵结束后才看；确定性编译错误不盲目重跑。
5. **绿色 run 不等于交付完成**。必须依次达到 `Cloud Ready`、`Imported`、`Delivery Done`。
6. KEMI 常规交付只需要 Windows x64 和 Linux x86_64。Windows ARM64/x86、Linux ARM、Flatpak、RPM、iOS 等只有出现明确需求时才启用完整上游流水线。

## 2. 当前构建能力与责任边界

| 目标 | 默认位置 | 当前能力和约束 | 完成责任 |
|---|---|---|---|
| PAD / Android debug | 本地 Mac | Flutter/Kotlin 包装和 ADB 安装可靠；若 Rust 核心变化，必须先重编 arm64 Android `librustdesk.so`，不能复用旧 JNI 冒充完整更新 | 本地构建与 PAD 实机验收 |
| PAD / Android release | 本地优先，有条件云端 | 当前 Apple Silicon Mac 缺 Rosetta 时 release AOT 不可靠；先补齐 Rosetta/NDK 后本地，否则才用云端 | 本地环境负责人 |
| macOS arm64 | 本地 Mac | GUI 编译可靠；固定测试证书/私钥必须在登录钥匙串可见后才能签名交付。当前若 `security find-identity -p codesigning` 为 0，只能称“编译完成”，不能称“交付完成” | 本地构建、签名和 Mac 实机验收 |
| Windows x64 GUI / EXE | GitHub Windows runner | 本机没有 Windows、MSVC、Windows SDK、Flutter Windows runner 和对应 vcpkg，不能可靠交叉编译 | 云端构建与 Windows 实机验收 |
| Linux x86_64 GUI / AppImage | GitHub Linux runner | 本机 Zig 交叉编译只适用于 `server/rustdesk-server`，不是 Flutter Linux GUI；GUI 还依赖 GTK、音频/X11、x64 vcpkg 和 Linux Flutter bundle | 云端构建与 Linux 实机验收 |

不要把 `server/build.sh linux` 生成的 Linux 服务端 ELF 当成 Linux GUI 客户端，也不要把旧 `flutter/android/app/src/main/jniLibs` 当成本次新编译的 Rust 核心。

## 3. 高效流水线结构

### 3.1 常规 KEMI 交付入口

唯一入口：

```text
.github/workflows/kemi-distribution.yml
显示名：KEMI Focused Client Artifacts
触发：master 有非纯文档 push，或手动 workflow_dispatch
```

它调用 `flutter-build.yml` 的 `kemi-distribution` profile，只运行：

```text
default bridge（Flutter 3.22.3）
        ├── Windows x64 TopMostWindow ─┐
        │                              ├── Windows x64 EXE ─┐
        └── Linux x86_64 → DEB → AppImage ─────────────────┤
                                                           ↓
                                     manifest + SHA256SUMS + 单一候选 Release
```

明确跳过：Flutter 3.44 ARM bridge、Windows ARM64/x86、macOS、iOS、Android、Linux ARM、Sciter、Flatpak 和其他非当前交付目标。`concurrency.cancel-in-progress=true` 会在同一分支出现新提交时取消旧 run。

最终同时保留 14 天 Actions artifacts，并由单一汇总 job 创建唯一预发布版本 `kemi-<完整commit>`，包含：

```text
KEMI-remote-desktop-windows-x64.exe
KEMI-remote-desktop-linux-x86_64.AppImage
manifest.json
SHA256SUMS.txt
```

Windows/Linux job 不再分别并发写同一个 nightly Release，避免覆盖、部分发布和来源混淆。

### 3.2 其他工作流的定位

| 工作流 | 用途 | 是否作为常规交付入口 |
|---|---|---|
| `kemi-distribution.yml` | KEMI Windows x64 + Linux x64 候选制品 | 是 |
| `flutter-ci.yml` | PR 或手动完整跨平台验证，不发布制品 | 否 |
| `ci.yml` | Rust 单元测试和基础质量检查 | 否 |
| `flutter-nightly.yml` | 上游式全平台 nightly，成本高 | 否 |
| `flutter-tag.yml` | 明确 tag 的全平台发布 | 只有正式规划全平台发布时使用 |
| `playground.yml` | 实验工作流，版本和组合可能过期 | 禁止用于交付 |

普通 push 不再自动启动完整 Flutter 大矩阵，避免一次提交同时浪费约二十多个 runner job；精简 KEMI 流水线负责实际所需的云端制品。

## 4. 具体分工与并行时间线

同一个开发任务固定分为四条责任线；一个人或一个 AI 可以兼任，但状态必须分别报告，不能因为正在本地构建而忘记云端。

| 责任线 | 立即开始的工作 | 完成输出 |
|---|---|---|
| 本地构建 | 版本检查、Dart/Kotlin/Rust 预检；构建 PAD 和 Mac；准备 ADB/Mac 验收 | 本地包、版本、签名/安装结果 |
| 云端构建 | 推送同一候选 commit；确认 focused run 已创建；定位 Windows/Linux job | commit、run ID、job 状态 |
| 持续监控 | 每 2–3 分钟查询一次；失败立即获取失败 step/annotation；有进展及时报告 | 状态时间线、失败根因 |
| 制品集成 | 下载候选 Release；核验 manifest/hash/格式/版本；导入 PAD assets；重建 PAD | 固定文件名、hash、最终 APK |

推荐时间线：

```text
T+0     本地 preflight
T+5     形成候选 commit 并 push；立即记录 run ID
T+5~10  云端 bridge；本地并行构建/验收 PAD 与 Mac
T+10+   Windows x64 与 Linux x64 并行
任一失败 立即分析首个失败 step，不等待另一个平台结束
两端成功 汇总 job 生成 manifest、校验和与唯一候选 Release
最后     导入 PAD、重构 APK、卸载安装、四端下载验收
```

## 5. 推送前 preflight

至少执行：

```bash
cd /Users/newlink/kemi/RustDesk/client

git diff --check
git status --short
git rev-parse HEAD

rg -n '^version:' flutter/pubspec.yaml
rg -n '^version = ' Cargo.toml
rg -n 'VERSION:' .github/workflows/flutter-build.yml

ruby -e "require 'yaml'; Dir['.github/workflows/*.yml'].each { |f| YAML.load(File.read(f)); puts f }"
```

根据改动范围追加：

- Dart/Kotlin 页面改动：`flutter analyze` 或最小目标构建。
- Rust 公共逻辑：本地 `cargo check/build --locked`；Android Rust 核心变化还要重编 JNI。
- Mac 交付：检查 `security find-identity -v -p codesigning`，随后固定签名和 `codesign --verify --deep --strict`。
- 构建脚本：确认 focused profile 只展开 default bridge、Windows x64、Linux x64 和 AppImage x64。

preflight 没通过时不要推送碰运气。提交前记录版本号和计划交付的平台。

## 6. 云端查询和持续监控

当前机器没有预装 `gh`，公开仓库状态可以直接通过 GitHub API + `jq` 查询。推送后第一件事是取得**与本次 commit 完全相同**的 run：

```bash
repo='caucy2026/rust-desk'
commit="$(git rev-parse HEAD)"

curl -fsSL "https://api.github.com/repos/$repo/actions/runs?branch=master&per_page=20" \
  | jq -r --arg commit "$commit" '
      .workflow_runs[]
      | select(.head_sha == $commit)
      | [.id,.name,.status,(.conclusion // "-"),.created_at,.html_url]
      | @tsv'
```

只认名称 `KEMI Focused Client Artifacts`、`head_sha` 完全一致的 run。记录 `<run-id>` 后查询 job：

```bash
curl -fsSL \
  "https://api.github.com/repos/$repo/actions/runs/<run-id>/jobs?per_page=100" \
  | jq -r '.jobs[] | [.id,.name,.status,(.conclusion // "-"),.started_at,.completed_at,.html_url] | @tsv'
```

查询失败步骤：

```bash
curl -fsSL \
  "https://api.github.com/repos/$repo/actions/runs/<run-id>/jobs?per_page=100" \
  | jq -r '
      .jobs[]
      | select(.conclusion == "failure")
      | "JOB\t\(.id)\t\(.name)",
        (.steps[] | select(.conclusion == "failure") | "STEP\t\(.number)\t\(.name)")'
```

查询失败 check 注释：

```bash
curl -fsSL \
  "https://api.github.com/repos/$repo/check-runs/<job-id>/annotations?per_page=100" \
  | jq -r '.[] | [.annotation_level,.path,.start_line,.message] | @tsv'
```

查询 Actions artifacts：

```bash
curl -fsSL \
  "https://api.github.com/repos/$repo/actions/runs/<run-id>/artifacts?per_page=100" \
  | jq -r '.artifacts[] | [.id,.name,.size_in_bytes,.expired,.archive_download_url] | @tsv'
```

持续监控纪律：

- run 创建后每 2–3 分钟查询一次，不超过 5 分钟无人查看。
- `queued` 超过 10 分钟标记为 runner 异常并报告。
- 任何 job 失败立即查看失败 step；另一个平台仍可继续，不互相阻塞分析。
- 若公开 API 不允许下载完整日志（常见 `403`），立即打开 job URL 查看，或在已授权环境使用 `gh run view <run-id> --log-failed`；不能因为 API 限制而把“exit code 1”当作根因。
- 新 commit 推送后确认旧 focused run 已被 concurrency 取消。

## 7. 失败分类和处理时限

| 失败类型 | 判断方法 | 处理 |
|---|---|---|
| 网络、GitHub cache、runner 临时故障 | 下载超时、5xx、cache 400/服务不可用，源码编译尚未报错 | 同一 commit 最多重跑失败 job 1 次 |
| 确定性编译错误 | Rust/Dart/C++ 编译器明确指出文件和错误 | 立即本地定位或按平台修复，形成新 commit；禁止盲目重跑 |
| bridge 失败 | `Install flutter rust bridge deps` 或 `Run flutter rust bridge` 失败 | 先修 bridge；Windows/Linux 都依赖它 |
| Windows 专属失败 | default bridge 和 TopMost 成功，Windows `Build rustdesk` 失败 | 只处理 Windows x64/MSVC、自定义 engine、vcpkg 或打包逻辑 |
| Linux 专属失败 | default bridge 成功，Linux `Build rustdesk` 或 AppImage 失败 | 区分 GUI/DEB 编译与 AppImage recipe，不能用 server 交叉编译替代 |
| 汇总失败 | Windows/Linux 都成功，manifest/Release 失败 | 保留两个最终 artifact，只修汇总；不要重编两端 |

同一 commit、同一步骤连续失败两次必须升级为代码/配置问题并留下根因记录。每次失败至少记录：run ID、job ID、失败 step、首条有效错误、处理决定和新 commit。

## 8. 三阶段完成判定

### Cloud Ready

必须同时满足：

- focused run 的 commit、版本和预期 commit 一致；
- Windows x64 EXE job 成功；
- Linux x86_64 AppImage job 成功；
- manifest 汇总 job 成功；
- 候选 Release 中四个文件都存在；
- `SHA256SUMS.txt` 与实际文件一致。

### Imported

从 `kemi-<commit>` 候选 Release 下载到临时 staging 目录，核验后再放入：

```text
flutter/android/app/src/main/assets/client-dist/
├── KEMI-remote-desktop-windows-x64.exe
└── KEMI-remote-desktop-linux-x86_64.AppImage
```

核验：

```bash
shasum -a 256 KEMI-remote-desktop-windows-x64.exe
shasum -a 256 KEMI-remote-desktop-linux-x86_64.AppImage
file KEMI-remote-desktop-windows-x64.exe
file KEMI-remote-desktop-linux-x86_64.AppImage
```

保留原始 `manifest.json` 和 `SHA256SUMS.txt` 的版本、commit、run ID、大小与哈希记录。文件名正确不代表品牌和功能正确，仍要在目标系统启动验证。

### Delivery Done

必须完成：

1. 将已核验 Windows/Linux 包与本地已核验 Mac ZIP一起编入最终 PAD APK。
2. 卸载测试 PAD 的旧包，安装最终包，检查 `versionName/versionCode/sourceDir`。
3. 从 PAD“客户端”页面分别下载 Android、Mac、Windows、Linux 文件。
4. 下载文件 hash 与 PAD 安装源/导入前静态包完全一致。
5. Windows x64、Linux x86_64、macOS arm64 分别完成启动；Android 完成安装。
6. 把版本、commit、run ID、hash、签名和验收结果写入 `CHANGELOG-KEMI.md`。

## 9. 当前云端基线审计

2026-07-30 查询到远端最新提交仍为：

```text
262bbedef0d6dc9df39b85c12b315458dcef4117
```

对应云端 run：

```text
Full Flutter CI  #30518880603  completed / failure
CI               #30518880357  completed / failure
```

Full Flutter CI 中两个 bridge 成功，但 Windows x64、Windows ARM64、Linux x64、Android ABI 和 macOS build 均失败；只留下 bridge 中间 artifacts，没有 Windows EXE 或 Linux AppImage。该 run 不是“仍在编译”，也不是可交付结果。当前 `1.4.44+102` 本地工作树尚未推送时，GitHub 不可能构建到这些新改动。

后续以 focused workflow 的新 run 为准，不继续等待或反复重跑上述旧全矩阵 run。

## 10. 每次汇报模板

```text
本地：
- PAD：构建/安装/版本/签名状态
- Mac：构建/固定签名/安装状态

云端：
- commit：
- focused run ID / URL：
- default bridge：
- Windows x64：
- Linux x64：
- manifest / candidate Release：
- 当前失败 step 与根因：
- 下次查询时间：

交付：
- Windows SHA-256 / 启动结果：
- Linux SHA-256 / 启动结果：
- Mac SHA-256 / 签名结果：
- 最终 PAD 版本 / APK SHA-256：
- 四端从 PAD 下载验收：
```

任何一栏未知就明确写“未完成/待验证”，不能用“正在构建”“应该可以”代替真实状态。
