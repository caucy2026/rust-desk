# KEMI Android 正式包名、签名与 Release 构建

> 当前基线：`1.4.46+104`。本文是Android正式身份和签名的唯一操作说明。

## 1. 固定身份

```text
公司域名：www.newlink-sz.com
Android applicationId：com.newlinksz.kemi.remote
Kotlin namespace：com.carriez.flutter_hbb
版本：1.4.46+104
```

域名中的连字符不能直接用于applicationId，因此固定使用`newlinksz`。applicationId是设备和
应用市场识别应用的正式身份；Kotlin namespace暂时保留上游目录，避免一次性重命名全部原生
代码造成业务回归。两者不同不影响最终APK身份。

旧测试包`com.carriez.flutter_hbb`与新包是两个独立应用，不能覆盖升级。正式迁移时必须先
卸载旧包，再安装新包，并重新确认录屏、辅助功能、悬浮窗、文件和通知等系统权限。

## 2. 固定签名

```text
Keystore：/Users/newlink/kemi/RustDesk/signing/android/kemi-release-2026.p12
类型：PKCS12
Alias：kemi-android-release-2026
算法：RSA 3072 / SHA256withRSA
有效期：10000天
钥匙串服务：KEMI Android Release Keystore 2026
钥匙串账户：newlink
```

证书指纹：

```text
SHA-1：50:69:14:22:2C:E0:62:F2:C8:44:3C:4E:2F:5A:6A:76:0A:B3:46:05
SHA-256：85:46:D0:3E:51:D0:9D:FA:17:DB:CF:43:2F:84:BC:CF:74:BD:2D:9F:DE:1C:FF:98:1F:F2:02:F8:87:18:71:A2
```

规则：

- 不得重新生成同名密钥；
- 不得使用`~/.android/debug.keystore`生成release；
- keystore和密码不得提交Git、写入Gradle文件、聊天记录或构建日志；
- keystore必须制作至少一份受控加密离线备份，密码通过独立安全渠道保管；
- 其他构建机必须安全导入同一keystore，不能各自生成证书；
- 密钥丢失将导致已安装用户无法正常覆盖升级。

## 3. Gradle签名入口

`flutter/android/app/build.gradle`只从以下环境变量读取release签名：

```text
KEMI_ANDROID_KEYSTORE
KEMI_ANDROID_STORE_PASSWORD
KEMI_ANDROID_KEY_ALIAS
KEMI_ANDROID_KEY_PASSWORD
```

任何一项缺失时，release任务必须直接失败，不能回退到debug签名。

当前Mac本地构建：

```bash
export KEMI_ANDROID_KEYSTORE=/Users/newlink/kemi/RustDesk/signing/android/kemi-release-2026.p12
export KEMI_ANDROID_KEY_ALIAS=kemi-android-release-2026
export KEMI_ANDROID_STORE_PASSWORD="$(security find-generic-password \
  -a newlink -s 'KEMI Android Release Keystore 2026' -w)"
export KEMI_ANDROID_KEY_PASSWORD="$KEMI_ANDROID_STORE_PASSWORD"

cd /Users/newlink/kemi/RustDesk/client/flutter
/Users/newlink/flutter/bin/flutter build apk \
  --release \
  --target-platform android-arm64 \
  --no-pub

unset KEMI_ANDROID_KEYSTORE
unset KEMI_ANDROID_KEY_ALIAS
unset KEMI_ANDROID_STORE_PASSWORD
unset KEMI_ANDROID_KEY_PASSWORD
```

输出：

```text
flutter/build/app/outputs/flutter-apk/app-release.apk
```

## 4. 构建后强制核验

至少检查：

```text
package=com.newlinksz.kemi.remote
versionName=1.4.46
versionCode=104
debuggable不存在或为false
APK v1/v2签名状态
Signer SHA-256=85:46:D0:3E:51:D0:9D:FA:17:DB:CF:43:2F:84:BC:CF:
               74:BD:2D:9F:DE:1C:FF:98:1F:F2:02:F8:87:18:71:A2
```

还要记录APK文件大小、SHA-256、源码commit、构建时间、目标ABI和内置客户端分发资源。

## 5. 功能与安全边界

`1.4.46+104`沿用Android正式身份和固定签名，不删除现有功能。录屏、辅助功能、文件传输、
悬浮窗、开机广播、音频、Wi-Fi名称和局域网客户端分发保持原行为。

因此，固定签名可以解决debug身份不稳定问题，但不能保证华为不再提示风险。远控敏感权限和
APK内置其他平台安装包仍可能触发安全模型。后续若需要公开上架，应另做“普通控制端”和
“专用PAD/被控分发端”两个flavor；本次不得为了降低告警悄悄删除用户要求保留的功能。

## 6. 设备迁移与验收

旧包和新包可以暂时并存，但正式测试应避免数据混淆：

```bash
adb -s <device> uninstall com.carriez.flutter_hbb
adb -s <device> install flutter/build/app/outputs/flutter-apk/app-release.apk
adb -s <device> shell dumpsys package com.newlinksz.kemi.remote
```

验收：

1. 首页版本为`1.4.46`；
2. 连接、触摸、双指滚轮、键盘和文件传输正常；
3. 共享屏幕仍由Android原生录屏确认；
4. 客户端页仍能启动/停止局域网HTTP服务；
5. 华为告警必须记录完整文字、截图、系统版本、安装来源和APK SHA-256；
6. 不允许通过关闭系统安全保护或反复改变包名/签名掩盖告警。
