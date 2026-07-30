# macOS 客户端配置、权限与签名

> 适用版本：`1.4.34+92`。本文件是 macOS 远程查看/控制、固定测试签名和交付核验的唯一操作说明；历史试验过程见 `CHANGELOG-KEMI.md`。

## 1. 当前已核验配置

已安装交付包：`/Applications/KEMI-远程桌面.app`；归档副本：`/Users/newlink/kemi/RustDesk/BIN/KEMI-远程桌面.app`。

| 项目 | 已核验值 | 作用 |
|---|---|---|
| Bundle ID | `com.carriez.rustdesk` | TCC 隐私授权与应用身份 |
| 显示名 / 可执行名 | `KEMI-远程桌面` | 系统设置与 Finder 中的名称 |
| 版本 | `1.4.34+92` | `CFBundleShortVersionString=1.4.34`、`CFBundleVersion=92` |
| 架构 | `arm64` | 当前 Apple Silicon 测试包 |
| 运行形态 | `LSUIElement=1` | 代理型 App，不常驻 Dock；系统设置抢焦点后须主动恢复窗口 |
| 签名叶证书 | `KEMI Local App Signing 2026`，SHA-1 `41330AE46A6AF5B400B44E771FF1CD7BAA6D1163` | 固定测试身份，包含 Digital Signature / Code Signing 用途 |
| 签名根证书 | `KEMI Local Development Code Signing 2026` | 仅本机构建链使用；不用于对外发布 |

2026-07-30 审计已执行 `codesign --verify --deep --strict`，整个 bundle（主程序、`service`、Flutter Framework 和全部嵌入 Framework）通过。不得以 `codesign --sign -`、二次签名或解包再封装替代固定签名，否则 TCC 会把它视为不同主体。

## 2. 远程控制实际需要的权限

| 权限 | PAD 控制本机时的用途 | 代码入口 | 用户操作 |
|---|---|---|---|
| 屏幕录制 | 向 PAD 输出本机画面 | `mainIsCanScreenRecording()` | 在 KEMI 的“权限设置”中单独点“申请授权” |
| 辅助功能 | 注入鼠标点击、拖动、滚轮与键盘 | `src/server/input_service.rs` → `ensure_remote_input_permissions()` | 在 KEMI 的“权限设置”中单独点“申请授权” |
| 输入监控 | **不属于 PAD 控制本机的准入条件**；仅用于 Mac 本机作为控制端时的可选键盘输入源 | `src/keyboard.rs` 的输入源选择 | 不在远控授权窗口展示、不自动打开系统设置页 |

关键约束：`ensure_remote_input_permissions()` 只查询辅助功能。若未授权，`handle_mouse_simulation_()` 和 `handle_key_()` 会明确拒绝注入；这就是“有画面但 PAD 点按、滚轮、键盘都无效”的唯一输入权限门槛。屏幕录制缺失则没有画面，但不替代辅助功能的输入判断。

输入监控页面中的无名称项目不是 KEMI 的远控权限：TCC 日志表明系统设置扩展也会请求 `ListenEvent`，且该扩展点不能弹系统授权。继续引导用户对该空白项目操作不能解决 PAD 控制问题，故 `1.4.31+89` 已移除第三项入口和 `Privacy_ListenEvent` 自动跳转。

## 3. 授权窗口流程

1. 首页左下“权限设置”卡片始终存在，即使两项显示已授权也可再次进入检查。
2. 弹窗只显示“屏幕录制”和“辅助功能”；每项都可独立申请，彼此不等待、不串行遮挡。
3. 点击后，KEMI 保持说明页，轮询实际 TCC 状态；用户在系统设置中完成操作后回到 KEMI 刷新即可。
4. KEMI 是 `LSUIElement` App。只有某项实际变为已授权后，Flutter 才调用 Runner 的 `activateMainAppWindow`，通过 `NSApp.activate`、`makeKeyAndOrderFront` 与 `orderFrontRegardless` 重新置前。授权尚未完成时绝不抢走系统设置前台。
5. 每次 PAD 新连接若仍缺少必需权限，主窗口和授权引导可再次显示（5 秒限频），不会被旧的“已经提示”标记永久压制。

测试顺序：先在本机授予屏幕录制和辅助功能，再从 PAD 重连，依次验证画面、单点左键、长按右键、单指拖动、双指纵向滚轮和键盘。任何输入无效时先看辅助功能状态，而不是输入监控。

## 4. 构建、固定签名与交付

构建完成后只用下列脚本签名：

```bash
cd /Users/newlink/kemi/RustDesk/client
res/sign-kemi-local-macos.sh \
  flutter/build/macos/Build/Products/Release/KEMI-远程桌面.app
```

脚本会拒绝缺失 `KEMI Local App Signing 2026` 身份的构建机，随后签署、深度校验并打印 designated requirement。交付前还必须核验：

```bash
app_bundle='/Applications/KEMI-远程桌面.app'
plutil -p "$app_bundle/Contents/Info.plist"
codesign --verify --deep --strict --verbose=2 "$app_bundle"
codesign -dv --verbose=4 "$app_bundle" 2>&1
```

确认版本、`com.carriez.rustdesk` 和签名链均正确后，才可将同一 App 复制到 `../BIN/`。`BIN/` 是已核验交付归档，不是构建目录，也不提交到 Git。

测试机无需导入私钥或本地根证书；但不得二次签名或改动 bundle。正式公开发布必须替换为 Apple Developer ID Application 签名并完成 notarization；本地测试证书不能用于发布。

## 5. TCC 重置与排障

以下命令会撤销该 Bundle ID 的所有 macOS 隐私授权，只能在获得明确同意后执行：

```bash
tccutil reset All com.carriez.rustdesk
```

重置后应使用当前固定签名 App 重新授予本文件第 2 节的两项权限。不要通过删除整个 TCC 数据库、修改系统数据库或更换签名证书来“修复”状态；这些做法会影响其他应用或再次破坏权限身份。

## 6. 开机自启动

桌面端“通用设置”保留“开机自启动”选项。启用时仅为当前用户创建 `~/Library/LaunchAgents/com.carriez.kemi-remote-desktop.plist`，关闭时删除该项；不使用管理员权限、不安装系统级 daemon，也不影响 RustDesk 信令服务端。
