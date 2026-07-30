# GitHub 跨平台构建与桥接生成

> 适用版本：`1.4.34+92`。本文件描述 KEMI 客户端的 GitHub Actions 构建依赖、失败定位和验证规则；版本号统一规则仍以 `SESSION-HANDOFF.md` 为准。

## 1. 流水线关系

`flutter-ci.yml` 调用 `flutter-build.yml`。所有 Windows/Linux/Android 桌面构建先依赖 `bridge.yml` 的两个 bridge artifact：

```text
generate-bridge (Flutter 3.22.3) ──> bridge-artifact ──> 默认平台构建
generate-bridge (Flutter 3.44.0) ──> bridge-artifact-flutter-3.44 ──> Windows ARM64
```

任何 bridge 生成失败都会让依赖它的下游作业被跳过；先看 bridge 的“Install flutter rust bridge deps”和“Run flutter rust bridge”，不要把下游 skipped 当作独立故障。

## 2. Flutter 版本与依赖基线

| 用途 | Flutter | `extended_text` 状态 |
|---|---:|---|
| 默认 Windows/Linux/Android 构建 | 3.24.5 | 仓库提交 `14.0.0` |
| 默认 bridge 生成 | 3.22.3 | CI 临时降为 `13.0.0`，只用于生成兼容 bridge |
| Windows ARM64 与专用 bridge | 3.44.0 | `.github/patches/apply_flutter_3.44_source_patches.sh` 临时升为 `15.0.2`，并同步主题 API 改动 |

2026-07-23 的本地 Flutter 3.29 解析曾把 `extended_text` 写成 `^15.0.2` 并整体升级锁文件，但默认 CI 仍在旧基线。于是 3.22 无法解析依赖，而 3.44 补丁对 `14.0.0` 的断言也不再匹配并退出。2026-07-30 已把提交基线恢复为精确 `14.0.0`，使两条临时补丁再次互斥、可预测；锁文件也已重新解析为 `extended_text 14.0.0`。

不要直接把默认 CI 升到 Flutter 3.29/3.44：Windows x64 仍依赖与 3.24.5 匹配的 RustDesk 自定义 engine。升级前必须先获得并验证匹配 engine，而不是只改一个版本号。

## 3. bridge 工具缓存与网络稳定性

`bridge.yml` 固定使用：Rust `1.75`、`cargo-expand 1.0.95`、`flutter_rust_bridge_codegen 1.80.1`。工具安装到 `/tmp/flutter_rust_bridge/bin`，缓存 key 包含操作系统、Rust 版本和两个工具版本；命中时直接复用二进制，避免每次重新编译。

首次缓存未命中时，Cargo 同时启用 sparse registry、`CARGO_NET_RETRY=10`、`CARGO_HTTP_TIMEOUT=600`，外层再最多重试 3 次（10/20 秒间隔）。这只重试可重试的网络/registry 波动；版本或依赖不匹配必须修正配置，不能无限重试掩盖错误。

## 4. 查询与验收

查询最近 push 的状态：

```bash
curl --http1.1 -sS \
  'https://api.github.com/repos/caucy2026/rust-desk/actions/runs?branch=master&event=push&per_page=8'
```

查看一个 run 的作业级状态：

```bash
curl --http1.1 -sS \
  'https://api.github.com/repos/caucy2026/rust-desk/actions/runs/<run-id>/jobs?per_page=100' \
  | jq -r '.jobs[] | [.name, .status, (.conclusion // "-")] | @tsv'
```

GitHub 对未认证 API 可能不开放详细日志下载；此时从 Actions 网页打开失败作业，定位具体 step。确认 bridge 成功后，再核对 Windows x64、Windows ARM64 以及目标 Linux 作业是否真正执行并生成 artifact。

注意：`flutter-ci.yml` 对仅 `.github/**`、`docs/**`、`README.md`、`res/**` 的 push 有 paths-ignore。修改工作流本身时，必须同时以一次真实源码/依赖修复触发，或手动 `workflow_dispatch`；不要以为只推工作流文件就已经验证。
