# KEMI macOS Developer ID 候选构建与公证

## 当前发布身份

2026-08-06 已在构建Mac登录的钥匙串创建：

```text
Developer ID Application: zhen ji (26T5WV4GLP)
```

正式macOS应用身份统一迁移为：

```text
Bundle ID: com.newlinksz.kemi.remote
显示名: KEMI远程办公
```

迁移后，旧`com.carriez.rustdesk`测试包的屏幕录制、辅助功能和登录项状态不会自动继承；用户在安装新正式身份的App后需要重新确认两项远控权限。这是macOS的安全边界，不能通过代码绕过。

## 两阶段交付

1. **Developer ID 候选**：使用公司Developer ID证书、Hardened Runtime和安全时间戳签名；仅用于团队安装测试。
2. **可公开发布包**：候选验收通过后，以同一签名包提交Apple notarization，Accepted后对ZIP/DMG执行`stapler`，再更新固定云端文件与发布清单。

Developer ID签名不等于已公证。未公证候选不能称为“正式公开发布”，也不应覆盖当前已运行的`/Applications/KEMI-远程桌面.app`。

## 构建与归档规则

执行：

```bash
cd /Users/newlink/kemi/RustDesk/client
res/build-kemi-developer-id-macos.sh
```

脚本默认保留项目`.dart_tool`和Xcode产物做增量构建，版本号变更不会触发无意义的依赖重新下载。只在Flutter SDK、Xcode或插件体系发生迁移后显式执行：

```bash
KEMI_MACOS_FORCE_CLEAN=1 res/build-kemi-developer-id-macos.sh
```

新鲜克隆或强制清理后，脚本仅在`.dart_tool/package_config.json`缺失时执行离线`pub get`；当前项目的Git依赖缓存必须事先完整。

脚本只构建并签名以下位置：

```text
flutter/build/macos/Build/Products/Release/KEMI-远程桌面.app
../BIN/release/candidates/KEMI-远程办公-macOS-<版本>-developer-id.app
../BIN/release/candidates/KEMI-远程办公-macOS-<版本>-developer-id.zip
../BIN/release/candidates/KEMI-远程办公-macOS-<版本>-developer-id.SHA256
```

它拒绝覆盖同名候选，且签名脚本明确拒绝`/Applications/*`；因此构建不会替换、停止或重启当前运行中的Mac App。

签名脚本先签`service`、Rust dylib和Framework，再以`Release.entitlements`签外层App。每次必须检查：

```bash
codesign --verify --deep --strict --verbose=2 '<候选App路径>'
codesign -dv --verbose=4 '<候选App路径>' 2>&1 | \
  grep -E 'Identifier|Authority|TeamIdentifier|Runtime Version'
spctl -a -vv -t exec '<候选App路径>'
```

结果必须包含新的Bundle ID、`Developer ID Application`、团队ID和Hardened Runtime。候选测试不得使用`codesign --sign -`、旧本地测试证书或`--deep`重新签整个App。

## Apple 公证（候选验收后）

公证不能把Apple账号密码写入脚本、Git或聊天。由账号持有人在本机使用App Store Connect API key或Apple app-specific password创建`notarytool`钥匙串profile；私钥/密码只留钥匙串。

对最终准备发布的ZIP执行：

```bash
xcrun notarytool submit '<发布ZIP>' \
  --keychain-profile '<本机notary profile>' --wait
```

`stapler`不支持直接装订ZIP。`notarytool`返回`Accepted`后，对提交ZIP内对应的App执行：

```bash
xcrun stapler staple '<发布App>'
xcrun stapler validate '<发布App>'
ditto -c -k --keepParent '<发布App>' '<最终发布ZIP>'
```

只有`notarytool`返回`Accepted`、App的`stapler validate`成功，且重新打包后的最终ZIP通过SHA-256校验，才可上传为`BIN/release/KEMI-macOS.zip`、更新manifest并发布到云端。DMG可以在公证通过后直接使用`stapler`装订和验证。

## 明日候选验收

1. 解压候选ZIP到非`/Applications`的临时测试目录，确认不影响正在运行的旧App。
2. 检查Info.plist、签名和`spctl`结果。
3. 启动候选，确认首页/设置、服务器、PAD连接、画面、辅助功能输入、屏幕录制、文件传输与开机自启状态。
4. 确认新Bundle ID下两项权限必须重新授予；不执行TCC全局重置。
5. 测试通过后，再单独获得公证凭据并制作可公开发布的最终ZIP/DMG。
## 工具链隔离

KEMI 不使用全局共享 Flutter。当前源码锁定 `extended_text 14.0.0`，唯一允许的
项目 SDK 是 `client/.toolchains/flutter`（Flutter 3.24.5）。MAC构建脚本只接受该
绝对项目内路径，并在版本不为3.24.5时立即失败；它**不会**读取`PATH`、
`KEMI_FLUTTER_BIN`、`/Users/newlink/flutter`或其他项目工具链。这样其他项目升级
Flutter 不会影响 KEMI，也不会再次产生“同一源码被错误SDK编译”的包。

Android、Windows、Linux构建也必须遵循相同规则：每个项目将固定SDK置于自己的
`.toolchains/flutter`，所有脚本都只引用该路径；需要升级Flutter时先新建独立版本分支
并完成全平台验证，不能用全局SDK临时替换。

当前本机构建验证（`1.4.64+169`）已通过：`com.newlinksz.kemi.remote`、ARM64、
Developer ID 签名链和 ZIP SHA-256 均正确。因 Apple 时间戳服务当时不可用，候选包以
`KEMI_MACOS_TIMESTAMP=off` 生成；它仅供本机测试，Gatekeeper 会将其标记为
“Unnotarized Developer ID”。网络恢复后须以默认 `required` 时间戳重新签名，并完成
notarization 后才能作为对外发布包。
