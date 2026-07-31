# 从 GitHub 本地编译 KEMI macOS 客户端

> 目标：同事在 Mac 上从 KEMI GitHub 仓库下载源码，并编译出
> `KEMI-远程桌面.app`。本文只说明 macOS 客户端编译，不包含 Android、
> Windows、Linux、服务端、PAD 内置分发或 GitHub Actions。

## 1. 源码和已验证基线

```text
GitHub：https://github.com/caucy2026/rust-desk
分支：master
产物：flutter/build/macos/Build/Products/Release/KEMI-远程桌面.app
当前源码版本：1.4.46+106
```

2026-07-31 已验证的 Apple Silicon 构建环境：

```text
macOS 26.5.2 / arm64
Xcode 26.6
Flutter 3.29.3 / Dart 3.7.2
Rust stable 1.97.1
CocoaPods 1.15.2
flutter_rust_bridge_codegen 1.80.1
vcpkg baseline 120deac3062162151622ca4860575a33844ba10b
```

同事的 Xcode 可以登录 Apple Developer 账号，但“编译成功”本身不要求把证书或私钥提交到
仓库。开发者证书只保存在构建 Mac 的登录钥匙串中。

## 2. 第一次准备 Mac

先从 App Store 安装完整 Xcode，打开一次并接受许可，然后执行：

```bash
xcode-select --install
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -license accept

brew install cmake ninja nasm pkg-config llvm cocoapods
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup default stable
```

安装项目固定使用的 Flutter，不要直接改用机器上任意版本：

```bash
mkdir -p ~/Developer
git clone --depth 1 --branch 3.29.3 \
  https://github.com/flutter/flutter.git ~/Developer/flutter-3.29.3
export PATH="$HOME/Developer/flutter-3.29.3/bin:$HOME/.cargo/bin:$PATH"
flutter doctor -v
flutter config --enable-macos-desktop
```

`flutter doctor -v` 中 Xcode、macOS toolchain 和 CocoaPods 必须可用。Android Studio
或 Android SDK 的警告不影响本文的 macOS 构建。

## 3. 下载源码与子模块

仓库为私有时，先让 GitHub 账号取得访问权限，再用浏览器登录凭据或 SSH。HTTPS 示例：

```bash
cd ~/Developer
git clone --recurse-submodules \
  https://github.com/caucy2026/rust-desk.git kemi-rust-desk
cd kemi-rust-desk
git checkout master
git pull --ff-only
git submodule sync --recursive
git submodule update --init --recursive
```

确认当前来源：

```bash
git status --short --branch
git rev-parse HEAD
git submodule status
```

开始构建前 `git status --short` 应为空；把 `git rev-parse HEAD` 的结果保留在交付记录中。

## 4. 安装本地 vcpkg 依赖

`vcpkg/` 是本机工具链并已被 `.gitignore` 排除，不会上传到 GitHub。第一次执行耗时较长，
之后会复用本地结果。

```bash
git clone https://github.com/microsoft/vcpkg.git vcpkg
git -C vcpkg checkout 120deac3062162151622ca4860575a33844ba10b
./vcpkg/bootstrap-vcpkg.sh -disableMetrics

export VCPKG_ROOT="$PWD/vcpkg"
export VCPKG_INSTALLED_ROOT="$VCPKG_ROOT/installed"
"$VCPKG_ROOT/vcpkg" install \
  --x-install-root="$VCPKG_INSTALLED_ROOT"
```

不要把其他工程的 `vcpkg/installed` 直接复制进来。架构或 baseline 不一致会在链接阶段产生
难以判断的错误。

## 5. 生成 Flutter/Rust 桥接代码

fresh clone 不包含生成文件，必须先执行本节：

```bash
export PATH="$HOME/Developer/flutter-3.29.3/bin:$HOME/.cargo/bin:$PATH"

cargo install flutter_rust_bridge_codegen \
  --version 1.80.1 \
  --features uuid \
  --locked

cd flutter
flutter pub get
cd ..

RUST_LOG=info flutter_rust_bridge_codegen \
  --rust-input ./src/flutter_ffi.rs \
  --dart-output ./flutter/lib/generated_bridge.dart \
  --c-output ./flutter/macos/Runner/bridge_generated.h
```

随后应存在：

```text
flutter/lib/generated_bridge.dart
flutter/macos/Runner/bridge_generated.h
```

它们是本地生成文件，不应提交。

`flutter_rust_bridge_codegen 1.80.1` 只接受 `RUST_LOG=debug` 或 `RUST_LOG=info`。
如果公司终端全局设置了 `RUST_LOG=warn`，不显式覆盖会在 codegen 启动时退出。

## 6. 编译 Release App

在仓库根目录执行：

```bash
export PATH="$HOME/Developer/flutter-3.29.3/bin:$HOME/.cargo/bin:$PATH"
export VCPKG_ROOT="$PWD/vcpkg"
export VCPKG_INSTALLED_ROOT="$VCPKG_ROOT/installed"
export MACOSX_DEPLOYMENT_TARGET=10.14

cargo build --locked --features flutter --release

cd flutter
FLUTTER_XCODE_ARCHS="$(uname -m)" \
FLUTTER_XCODE_ONLY_ACTIVE_ARCH=YES \
flutter build macos --release --no-pub

app_path="build/macos/Build/Products/Release/KEMI-远程桌面.app"
cp ../target/release/service "$app_path/Contents/MacOS/service"

# 复制 service 会改变 bundle seal；仅为本机编译验收做 ad-hoc 重签。
# 外发包必须由证书负责人改用 Developer ID Application 签名和公证。
codesign --force --deep --sign - "$app_path"
cd ..
```

Apple Silicon 生成 arm64；Intel Mac 生成 x86_64。本文不把单架构包伪装成 universal 包。

## 7. 编译结果验收

```bash
app_path="flutter/build/macos/Build/Products/Release/KEMI-远程桌面.app"

test -d "$app_path"
test -x "$app_path/Contents/MacOS/KEMI-远程桌面"
test -x "$app_path/Contents/MacOS/service"
test -f "$app_path/Contents/Frameworks/liblibrustdesk.dylib"

plutil -p "$app_path/Contents/Info.plist" | \
  grep -E 'CFBundleIdentifier|CFBundleShortVersionString|CFBundleVersion'
file "$app_path/Contents/MacOS/KEMI-远程桌面"
file "$app_path/Contents/MacOS/service"
file "$app_path/Contents/Frameworks/liblibrustdesk.dylib"
codesign --verify --deep --strict --verbose=2 "$app_path"
codesign -dv --verbose=4 "$app_path" 2>&1 | \
  grep -E 'Identifier|Format|Signature|TeamIdentifier'
```

本版本预期：

```text
CFBundleIdentifier = com.carriez.rustdesk
CFBundleShortVersionString = 1.4.46
CFBundleVersion = 106
主程序、service、liblibrustdesk.dylib 架构与构建 Mac 一致
codesign --verify 返回成功
本机编译验证包显示 Signature=adhoc、TeamIdentifier=not set
```

可在构建机直接启动：

```bash
open "flutter/build/macos/Build/Products/Release/KEMI-远程桌面.app"
```

首次启动后，远程被控功能需要用户在系统设置中授予“屏幕录制”和“辅助功能”。权限逻辑见
`kemi-docs/macos-configuration.md`，它不属于编译失败。

## 8. Apple Developer 账号说明

Flutter 本地 Release 构建默认可产生用于本机构建验证的 App。若要把 App 发给其他 Mac，
不能使用 KEMI 本机自签测试证书，也不能上传 `.p12` 或私钥到 GitHub。应由证书负责人在构建
Mac 的 Xcode 中创建或安装 `Developer ID Application` 身份，再执行 Developer ID 签名和
Apple notarization。那属于正式分发流程，不改变本文第 2～7 节的源码编译步骤。

查看当前机器可用身份：

```bash
security find-identity -v -p codesigning
```

只有看到属于同事团队的有效 `Developer ID Application` 身份，才可制作外发包。只有
`Apple Development` 时可做开发测试，不能把它描述为已公证的公开安装包。

## 9. 常见失败

| 现象 | 处理 |
|---|---|
| `libs/hbb_common` 缺文件 | 重新执行 `git submodule update --init --recursive` |
| 找不到 `VCPKG_ROOT` 或音视频库 | 重做第 4 节，并在同一终端导出两个 vcpkg 变量 |
| 找不到 `generated_bridge.dart` / `bridge_generated.h` | 按第 5 节用 codegen `1.80.1` 重新生成 |
| Dart/Flutter API 报错 | 确认 `flutter --version` 是 `3.29.3`，不要自动升级 SDK |
| CocoaPods 报错 | 执行 `pod --version`；必要时 `brew reinstall cocoapods` 后重跑 `flutter pub get` |
| Xcode 同时尝试 arm64 和 x86_64 | 保留第 6 节两个 `FLUTTER_XCODE_*` 参数 |
| App 有画面但不能点击远端 Mac | 这是“辅助功能”未授权，不是编译问题 |

完成交接时只需给出：源码 commit、`1.4.46+106`、目标架构、App 路径以及第 7 节核验结果。
