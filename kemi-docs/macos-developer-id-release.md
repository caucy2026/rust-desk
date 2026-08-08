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

### 验签必须使用完整macOS安全环境

`codesign`、`stapler`和`spctl`不仅读取App文件，也依赖macOS钥匙串、证书链、Security/trustd和Gatekeeper服务。受限沙箱或隔离自动化环境可能输出：

```text
Authority=(unavailable)
invalid signature (code or signature have been modified)
```

这组输出只能说明当前执行环境无法完成可信验签，不能单独证明App字节被修改。正式结论必须在正常macOS用户安全上下文执行：

```bash
security find-identity -v -p codesigning
codesign --verify --deep --strict --verbose=2 '<解压后的App>'
xcrun stapler validate -v '<解压后的App>'
spctl --assess --type execute --verbose=4 '<解压后的App>'
```

同时比较压缩前后关键二进制SHA-256。若哈希一致且完整环境验证通过，隔离环境的失败属于误报；只有完整环境仍失败或文件哈希确实变化，才判定制品损坏。

## 1.4.75+182正式公证结果

2026-08-08完成首个可公开分发的KEMI macOS包：

- 公证提交ID：`b2746a22-151d-42c5-9bad-9876a936a083`；
- Apple结果：`Accepted`；
- App身份：`com.newlinksz.kemi.remote`、`1.4.75 (182)`、arm64；
- 签名：`Developer ID Application: zhen ji (26T5WV4GLP)`、Hardened Runtime、安全时间戳；
- `stapler staple`和`stapler validate`成功；
- 解压最终ZIP后再次执行深度签名、票据和Gatekeeper验证，结果为`accepted / Notarized Developer ID`；
- 最终归档：`BIN/KEMI-远程办公-macOS-arm64-1.4.75+182-notarized.zip`；
- 固定上传副本：`BIN/release/KEMI-macOS.zip`；
- 文件大小：22,650,917字节；
- SHA-256：`a0b51eeaca4284fc171cbe39d5cc227938d450c3363b8631b61c44233da2b206`。

提交给Apple的原始ZIP不能直接覆盖最终文件：票据装订会修改App，必须在装订后重新ZIP并重新计算哈希。本轮没有停止或替换`/Applications`中正在运行的旧App。

2026-08-08再次从固定上传ZIP解压复验，仍得到`valid on disk`、`satisfies its Designated Requirement`、`The validate action worked!`以及`accepted / Notarized Developer ID`。Apple公证等待不会修改本地文件；本包从生成后到复验时大小和SHA-256均保持不变。

### 会员续费、证书到期与现有客户包

当前Developer ID证书在本机钥匙串中的准确到期时间为北京时间`2027-02-02 06:12:15`，建议在`2027-01-02`前完成会员续费状态检查和新证书准备。

会员续费后，当前`1.4.75+182`正式ZIP不需要重新签名。已经安装的客户以及续费后首次下载的新客户，仍可安装和使用同一个原始文件。即使旧证书以后自然到期，只要签名时证书有效、包含安全时间戳、公证票据有效、文件没有被修改且证书没有被撤销，Gatekeeper仍按签名时刻判断证书有效性。

| 场景 | 当前正式ZIP | 新编译版本 |
|---|---|---|
| 会员续费 | 原样继续分发 | 使用当时有效的Developer ID签名 |
| 旧证书自然到期 | 已安装客户和新客户仍可使用原始ZIP | 换新证书并重新签名、公证、staple |
| 旧证书被撤销 | 可能无法安装或启动 | 必须换证并发布替代版本 |
| App内部内容或签名被修改 | 原公证结论不再适用 | 作为新制品完成全套流程 |

当前正式文件身份必须保持：

```text
BIN/release/KEMI-macOS.zip
版本 1.4.75+182
SHA-256 a0b51eeaca4284fc171cbe39d5cc227938d450c3363b8631b61c44233da2b206
```

不要因为会员续费或创建新证书而撤销旧证书，也不要重新签署现有ZIP。会员续费只维持开发者服务资格；它不会自动创建证书，也不会替未来新版本完成签名和公证。

## 后续候选验收

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

当前正式构建验证（`1.4.75+182`）已通过：`com.newlinksz.kemi.remote`、ARM64、
Developer ID签名链、安全时间戳、Apple notarization、stapler票据和最终ZIP SHA-256
全部正确。后续正式包仍必须使用默认`required`时间戳和`KEMI_NOTARY`配置；任何
`KEMI_MACOS_TIMESTAMP=off`候选都只能用于本机诊断，不能覆盖正式上传文件。
