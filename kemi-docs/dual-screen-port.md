# RustDesk 安卓双屏移植文档

> 跨屏软键盘的最新需求、状态机和重构方案见
> [KEMI 跨屏软键盘需求与设计](cross-display-keyboard.md)。键盘行为若与本文旧说明冲突，以该设计文档为准。

## 2026-07-27 当日更新索引

- 跨屏键盘今天的完整调试过程、失败尝试、日志时间线、最终结论与遗留项，统一记录在 [cross-display-keyboard.md](cross-display-keyboard.md) 的第 15、16 节。
- 本文保留双屏移植总体架构说明；键盘实现细节不再在本文重复维护。

## 项目概述

将 RustDesk v1.4.9 (AGPL-3.0) 移植到安卓双屏设备，实现：
- **Display 0（主屏）**：键盘输入 + 快捷按键控制面板
- **Display 2（副屏）**：显示远程桌面画面，支持触摸操作
- **跨屏通信**：主屏键盘输入实时转发到副屏的远程连接

## 架构设计

```
┌──────────────────────────────┐     ┌──────────────────────────────┐
│       Display 0 (主屏)        │     │       Display 2 (副屏)        │
│                              │     │                              │
│  MainActivity                │     │  RemoteActivity              │
│  ├─ ControlPage (Flutter)    │     │  ├─ RemotePage (Flutter)     │
│  ├─ 连接输入 + 键盘          │     │  ├─ 远程桌面渲染              │
│  └─ 快捷键按钮               │     │  └─ 触摸事件处理              │
│         │                    │     │         │                    │
│         │ MethodChannel      │     │         │ MethodChannel      │
│         │ "mainChannel"      │     │         │ "remoteChannel"    │
│         │                    │     │         │                    │
│         └──────┬─────────────┘     └─────┬──────┘                │
│                │                         │                        │
│           ┌────▼─────────────────────────▼────┐                   │
│           │        SessionState (单例)         │                   │
│           │  - remoteMethodChannel            │                   │
│           │  - currentSessionId               │                   │
│           │  - forwardKeyString()             │                   │
│           │  - forwardKeyEvent()              │                   │
│           └───────────────────────────────────┘                   │
│                              │                                    │
│                       librustdesk.so                               │
│                       (Rust FFI 核心)                              │
└──────────────────────────────────────────────────────────────────┘
```

### 通信协议

| 方向 | 方法 | 数据 | 说明 |
|------|------|------|------|
| Flutter → MainActivity | `launch_remote_on_display2` | `{peer_id, password, force_relay}` | 启动副屏远程连接 |
| MainActivity → RemoteActivity | `send_key_string` → SessionState → `on_key_string` | `{text}` | 转发文本到远程 |
| MainActivity → RemoteActivity | `send_key_event` → SessionState → `on_key_event` | `{key, down}` | 转发按键到远程 |
| RemoteActivity → Flutter | `init_params` | `{peer_id, password, force_relay}` | 传递连接参数给 Flutter |
| Flutter → RemoteActivity | `get_connection_params` | — | Flutter 主动拉取连接参数 |
| Flutter → RemoteActivity | `notify_session_ready` | `{sessionId}` | 通知连接已建立 |
| Flutter → RemoteActivity | `notify_session_closed` | — | 通知连接已断开 |
| Flutter → RemoteActivity | `finish_activity` | — | 关闭副屏 Activity |
| MainActivity → Flutter | `on_remote_state` | `{connected, sessionId}` | 通知主屏连接状态变化 |
| Flutter → MainActivity | `get_remote_state` | — | 查询远程连接状态 |
| Flutter → MainActivity | `close_remote` | — | 请求关闭远程连接 |

---

## 文件清单

### 新增文件 (3 个)

| 文件 | 行数 | 说明 |
|------|------|------|
| `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/RemoteActivity.kt` | ~170 | 副屏专用 FlutterActivity |
| `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/SessionState.kt` | ~60 | 跨屏通信单例 |
| `flutter/lib/mobile/pages/control_page.dart` | ~550 | 主屏控制页面（重写） |

### 修改文件 (5 个)

| 文件 | 修改说明 |
|------|---------|
| `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainActivity.kt` | 新增 Display 0 防呆、5 个 MethodChannel handler、launchRemoteOnDisplay2() |
| `flutter/lib/main.dart` | 新增 `_initDualScreenRemoteListener()`、`_finishRemoteActivity()` |
| `flutter/lib/mobile/pages/home_page.dart` | 首页 Tab 从 ConnectionPage 切换为 ControlPage |
| `flutter/android/app/src/main/AndroidManifest.xml` | 新增 RemoteActivity 声明 |
| `flutter/android/app/build.gradle` | compileSdk 34、protobuf 配置、Kotlin 版本、依赖调整 |

### 构建配置文件修改

| 文件 | 修改说明 |
|------|---------|
| `flutter/android/settings.gradle` | AGP 7.4.2、Kotlin 1.8.22、阿里云镜像 |
| `flutter/android/build.gradle` | 阿里云镜像仓库 |
| `flutter/android/gradle/wrapper/gradle-wrapper.properties` | Gradle 7.6.4 |
| `flutter/pubspec.yaml` | extended_text ^15.0.2 |

### 原生库文件

| 文件 | 大小 | 来源 |
|------|------|------|
| `jniLibs/arm64-v8a/librustdesk.so` | ~32MB | 从官方 v1.4.9 aarch64 APK 提取 |
| `jniLibs/arm64-v8a/libc++_shared.so` | ~1.8MB | 从 NDK 27.0.12077973 复制 |

---

## 关键实现细节

### 1. RemoteActivity — 副屏 Activity

```kotlin
class RemoteActivity : FlutterActivity() {
    // 防呆：检测到在主屏则自动迁回副屏
    override fun onCreate(savedInstanceState: Bundle?) {
        if (currentDisplayId == Display.DEFAULT_DISPLAY) {
            relaunchOnDisplay2()  // 反射 setLaunchDisplayId(2)
            return
        }
        // 读取 Intent 参数：peer_id, password, force_relay
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        // 注册 "remoteChannel" MethodChannel
        SessionState.remoteMethodChannel = channel
        // 500ms 后通过 init_params 发送连接参数给 Flutter
    }
}
```

**防呆机制**：`RemoteActivity` 应该只在 Display 2 运行。如果系统错误地将其恢复到主屏（Display 0），会使用反射调用 `ActivityOptions.setLaunchDisplayId(2)` 重新启动到副屏，然后 `finish()` 当前实例。

### 2. SessionState — 跨屏通信桥梁

```kotlin
object SessionState {
    @Volatile var remoteMethodChannel: MethodChannel? = null
    @Volatile var currentSessionId: String? = null
    @Volatile var isRemoteConnected: Boolean = false

    fun forwardKeyString(text: String)   // 主屏 → 副屏：文本输入
    fun forwardKeyEvent(keyName: String, down: Boolean)  // 主屏 → 副屏：按键事件
    fun notifyConnectionState(connected, sessionId)  // 副屏 → 主屏：状态通知
    fun reset()  // 清理状态
}
```

使用 Kotlin `object` 单例模式，通过 `@Volatile` 保证多线程可见性。

### 3. MainActivity 修改

**新增 MethodChannel handlers**：
- `launch_remote_on_display2`：接收 Flutter 请求，启动 RemoteActivity
- `send_key_string` / `send_key_event`：转发键盘输入到 SessionState
- `get_remote_state` / `close_remote`：状态查询和远程关闭

**launchRemoteOnDisplay2() 实现**：
```kotlin
private fun launchRemoteOnDisplay2(peerId: String, password: String?, forceRelay: Boolean) {
    val intent = Intent(this, RemoteActivity::class.java).apply { ... }
    val options = ActivityOptions.makeBasic()
    // 反射调用隐藏 API: setLaunchDisplayId(int)
    val method = options.javaClass.getMethod(
        "setLaunchDisplayId",
        Int::class.javaPrimitiveType  // ⚠️ 注意：必须用 javaPrimitiveType
    )
    method.invoke(options, 2)  // Display 2
    startActivity(intent, options.toBundle())
}
```

### 4. ControlPage — 主屏控制界面

重写的控制页面，功能分区：

| 区域 | 状态 | 内容 |
|------|------|------|
| 连接区 | 未连接时显示 | peer ID 输入框 + autocomplete + Connect 按钮 |
| 键盘区 | 连接后显示 | TextField 键盘输入 + 文本 diff 算法（新增字符→sendKeyString，删除→sendKeyEvent VK_BACK） |
| 快捷键 | 连接后显示 | Enter, Tab, Esc, Del, Space, Home, End, ↑↓←→, Ctrl+C/V/Z |

### 5. main.dart 修改

`_initDualScreenRemoteListener()` 监听 `"remoteChannel"`：
- 收到 `init_params` → `Navigator.pushAndRemoveUntil(RemotePage(...))`
- 收到 `on_key_string` → `bind.sessionInputString(text)`
- 收到 `on_key_event` → `gFFI.inputModel.inputKey(keyName, down)`
- 主动拉取 fallback：`invokeMethod('get_connection_params')`

### 6. AndroidManifest.xml — RemoteActivity 声明

```xml
<activity
    android:name=".RemoteActivity"
    android:exported="false"
    android:configChanges="orientation|keyboardHidden|keyboard|touchscreen|...|screenSize|..."
    android:launchMode="singleInstance"
    android:screenOrientation="landscape"
    android:resizeableActivity="true"
    android:theme="@style/NormalTheme">
</activity>
```

---

## 构建环境

### 开发机
- **OS**: macOS (Apple Silicon ARM64)
- **JDK**: OpenJDK 17.0.19 (Temurin)
- **Rust**: 1.97.1 (清华镜像)

### Flutter 环境
- **Flutter SDK**: 3.29.3 (Dart 3.7.2)
- **存放路径**: `/tmp/flutter_sdk/`
- **flutter_rust_bridge**: v1.80.1

### Android SDK
- **compileSdk**: 34
- **targetSdk**: 33
- **minSdk**: 22
- **NDK**: 27.0.12077973（用于提取 libc++_shared.so）
- **AGP**: 7.4.2
- **Gradle**: 7.6.4
- **Kotlin**: 1.8.22

### 构建命令

```bash
export PATH="/tmp/flutter_sdk/flutter/bin:$PATH"
export FLUTTER_STORAGE_BASE_URL="https://storage.flutter-io.cn"
export PUB_HOSTED_URL="https://pub.flutter-io.cn"

cd /Users/newlink/kemi/RustDesk/client/flutter
flutter build apk --debug --target-platform android-arm64
```

输出：`build/app/outputs/flutter-apk/app-debug.apk` (~75MB)

---

## 编译问题与解决方案

### 问题 1：Flutter v1 Embedding 移除（PluginRegistry.Registrar 不存在）

**症状**：Flutter 3.29 移除了 `io.flutter.plugin.common.PluginRegistry.Registrar` 接口，多个插件编译失败。

**影响的插件**：battery, device_info, camera_android, connectivity, uni_links, external_path, sqflite, flutter_plugin_android_lifecycle, file_picker, url_launcher_android, video_player_android, webview_flutter_android, path_provider_android

**解决**：
1. 对大部分插件：注释掉 `registerWith` 静态方法（v1 embedding 已移除）
2. 对 `file_picker`：将 `setup()` 方法的 `Registrar` 参数改为 `Object`，注释 v1 分支代码
3. 修复 `external_path` 的 Kotlin 语法（companion object 清理）

### 问题 2：protoc 二进制架构错误

**症状**：Maven Central 的 `protoc-3.20.1-osx-aarch_64.exe` 实际是 x86_64 二进制，在 ARM64 Mac 上无法运行。

**解决**：从 GitHub 下载 protoc 25.1 ARM64 版本，修改 build.gradle 使用本地路径：
```groovy
protobuf {
    protoc {
        path = '/tmp/protoc3/bin/protoc'  // 替代 artifact = 'com.google.protobuf:protoc:3.20.1'
    }
}
```

### 问题 3：Gradle 8.x 与 Flutter SDK includeBuild 不兼容

**症状**：升级 Gradle 8.x 后，Flutter SDK 的 `flutter_tools/gradle/build.gradle.kts` 报 `kotlin-dsl:4.2.1` 找不到。

**解决**：保持 Gradle 7.6.4 + AGP 7.4.2，通过降级 androidx 依赖解决 compileSdk 兼容性。

### 问题 4：androidx 依赖要求 compileSdk 35

**症状**：`androidx.activity:activity:1.10.1`、`androidx.media3:*:1.5.1` 等要求 compileSdk 35。

**解决**：
1. 降级 `androidx.media:media` 从 1.6.0 到 1.4.0
2. 在 `configurations.all` 中强制 androidx 版本到兼容 compileSdk 34 的版本

### 问题 5：libc++_shared.so 缺失

**症状**：`librustdesk.so` 依赖 `libc++_shared.so`，且 NDK 23 版本符号不匹配（缺少 `__libcpp_verbose_abort`）。

**解决**：使用 NDK 27.0.12077973 的 `libc++_shared.so`（librustdesk.so 原本的编译 NDK 版本）。

### 问题 6：rustls-platform-verifier 无法下载

**症状**：`https://storage.googleapis.com/download.flutter.io/` 网络不可达。

**解决**：注释掉 `rustls:rustls-platform-verifier:0.1.1` 依赖（我们的 prebuilt .so 不需要 Java 层依赖）。

### 问题 7：Kotlin 2.1.21 + AGP 7.4.2 D8 不兼容

**症状**：Kotlin 2.x 编译的字节码需要更新版本的 D8/R8。

**解决**：降级 Kotlin 到 1.8.22，与 AGP 7.4.2 的 D8 版本兼容。

---

## 部署

### 设备连接
```bash
adb connect 192.168.3.54:5555
```

### 安装与启动
```bash
# 安装
adb -s 192.168.3.54:5555 install -r app-debug.apk

# 授权悬浮窗权限
adb shell appops set com.carriez.flutter_hbb SYSTEM_ALERT_WINDOW allow

# 在 Display 0 启动
adb shell am start -n com.carriez.flutter_hbb/.MainActivity --display 0
```

---

## 使用流程

1. 主屏（Display 0）显示 ControlPage
2. 输入对方 peer ID，点击 Connect
3. 系统自动在 Display 2 启动 RemoteActivity，显示远程桌面
4. 在主屏键盘区输入文字 → 实时转发到远程
5. 使用快捷键按钮（Ctrl+C/V/Z 等）控制远程
6. 在副屏上触摸操作远程桌面
7. 点击 Disconnect 或关闭副屏 → 断开连接

---

## 文件目录结构

```
RustDesk/rustdesk/
├── docs/
│   └── dual-screen-port.md          ← 本文档
├── flutter/
│   ├── lib/
│   │   ├── main.dart                ← 修改：_initDualScreenRemoteListener()
│   │   └── mobile/pages/
│   │       ├── control_page.dart    ← 新增：主屏控制页面
│   │       └── home_page.dart       ← 修改：Tab 切换
│   ├── android/
│   │   ├── app/
│   │   │   ├── build.gradle         ← 修改：编译配置
│   │   │   └── src/main/
│   │   │       ├── AndroidManifest.xml ← 修改：RemoteActivity 声明
│   │   │       ├── jniLibs/arm64-v8a/
│   │   │       │   ├── librustdesk.so      ← 新增：Rust 核心 (~32MB)
│   │   │       │   └── libc++_shared.so    ← 新增：C++ 运行时 (~1.8MB)
│   │   │       └── kotlin/com/carriez/flutter_hbb/
│   │   │           ├── MainActivity.kt     ← 修改：双屏逻辑
│   │   │           ├── RemoteActivity.kt   ← 新增：副屏 Activity
│   │   │           └── SessionState.kt     ← 新增：跨屏通信单例
│   │   ├── build.gradle             ← 修改：仓库配置
│   │   ├── settings.gradle          ← 修改：AGP/Kotlin 版本
│   │   └── gradle/wrapper/
│   │       └── gradle-wrapper.properties ← 修改：Gradle 版本
│   └── pubspec.yaml                 ← 修改：依赖版本
└── libs/
    └── hbb_common/protos/
        ├── message.proto
        └── rendezvous.proto
```

---

## 2026-07-25 闭环验证记录

### 本次目标

- 确保 Android 全量代码可编译。
- 确保编译产物可推送到设备并可正常启动运行。

### 今日关键修复

1. Flutter 依赖兼容
- 修复 `octo_image` 缓存包缺少 animation 导入导致的编译错误。
- 修复 `sqflite` 缓存包缺少 services 导入导致的编译错误。

2. Protobuf/Kotlin 兼容
- 适配 `proto3 oneof` 生成代码，不再使用 `hasSeq/hasChr/hasControlKey`。
- 修改为 `getUnionCase()` 判断分支，修复 Kotlin 编译失败。
- 影响文件：
    - `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/InputService.kt`
    - `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/KeyboardKeyEventMapper.kt`

3. protoc 工具链
- 按工程配置补齐 `/tmp/protoc3/bin/protoc`，满足 Gradle protobuf 任务依赖。

### 构建验证

- 命令：`flutter build apk --debug`
- 结果：通过，产物 `flutter/build/app/outputs/flutter-apk/app-debug.apk`
- 产物时间：`2026-07-25 21:54:45`
- 产物大小：约 `136MB`

### 设备闭环验证 (192.168.1.6:5555)

- 安装：`adb install -r .../app-debug.apk` -> `Success`
- 启动：`adb shell am start -n com.carriez.flutter_hbb/.MainActivity`
- 包名存在：`com.carriez.flutter_hbb`
- 前台 Activity：`com.carriez.flutter_hbb/.MainActivity`
- 进程 PID：`11995`
- 版本信息：`versionName=1.4.9`，`versionCode=67`
- 更新时间：`2026-07-25 21:56:09`

### 结论

- Android 代码编译通过。
- APK 推送成功并可在目标设备正常启动运行，闭环完成。
