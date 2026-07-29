# RustDesk Android 双屏异显适配 — 开发文档

> **日期**: 2026-07-19  
> **项目**: RustDesk 开源远程桌面 — Android 双屏定制  
> **目标设备**: RK356x / V900 双屏 Android 12 设备  
> **参考**: `chip.md` (芯片平台实战手册), `xtqx.md` (系统权限调试手册)

---

## 一、项目背景

### 1.1 客户需求

客户拥有一批 **RK356x (huanglong)** 和 **V900 (HL2.0)** 双屏 Android 12 设备，要求将 RustDesk 远程桌面适配为双屏工作模式：

| 屏幕 | 职责 | 交互方式 |
|:----:|------|---------|
| **主屏** (Display 0, 1920×1280) | 连接管理 + 键盘输入 | 触摸 + 键盘（物理键盘或虚拟键盘） |
| **副屏** (Display 2, 1920×1280) | 远程桌面画面 + 触摸控制 | 触摸 → 远程被控设备的鼠标/触摸事件 |

核心场景：主屏打字 → 远程设备响应；副屏触摸操控 → 远程设备响应。两块屏可独立操作，互不阻塞。

### 1.2 客户设备信息

| 特性 | RK356x | V900 |
|------|--------|------|
| SoC | 4核 Cortex-A55 | 8核 Cortex-A73 |
| GPU | Mali-G52 (OpenGL ES 3.2) | Mali-G52 6核 (OpenGL ES 3.2 / OpenCL 3.0) |
| Android | 12 (API 31) | 12 (API 31) |
| 显示 | 双屏 1920×1280 | 双屏 1920×1280 |
| ADB | WiFi 5555 | WiFi 5555 |

---

## 二、软件架构

### 2.1 RustDesk 原始架构（了解后方可改动）

```
┌─────────────────────────────────────────────┐
│              Flutter UI (Dart)               │
│  main.dart → HomePage → RemotePage           │
│  (连接管理)     (远程桌面画面 + 触摸)           │
├─────────────────────────────────────────────┤
│       flutter_rust_bridge v1.80.1            │
│       MethodChannel("mChannel")              │
├─────────────────────────────────────────────┤
│              Rust Core (librustdesk.so)       │
│  client.rs | server.rs | flutter.rs          │
│  视频编解码 | 网络协议 | 输入事件              │
├─────────────────────────────────────────────┤
│           Android Native (Kotlin)             │
│  MainActivity | MainService (屏幕录制)        │
│  InputService (无障碍输入) | FloatingWindow    │
└─────────────────────────────────────────────┘
```

### 2.2 双屏适配后架构

```
┌──────────────────────┐    ┌──────────────────────────┐
│   Display 0 (主屏)    │    │    Display 2 (副屏)       │
│                      │    │                          │
│  MainActivity        │    │  RemoteActivity           │
│  ┌────────────────┐  │    │  ┌────────────────────┐  │
│  │ ControlPage     │  │    │  │ RemotePage          │  │
│  │ • 远程ID输入    │  │    │  │ • 远程桌面视频渲染   │  │
│  │ • 连接按钮      │  │    │  │ • 触摸→远程鼠标     │  │
│  │ • 键盘输入区    │  │    │  │ • 物理键盘处理      │  │
│  │ • 快捷按键      │  │    │  └────────────────────┘  │
│  └────────────────┘  │    │                          │
│         │            │    │         │                │
│   MethodChannel      │    │   MethodChannel          │
│   ("mChannel")       │    │   ("remoteChannel")      │
│         │            │    │         │                │
└─────────┼────────────┘    └─────────┼────────────────┘
          │                           │
          └───────┬───────────────────┘
                  │
        ┌─────────▼──────────┐
        │   SessionState     │
        │   (跨屏通信单例)    │
        │   • 转发键盘事件   │
        │   • 持有sessionId  │
        │   • 连接状态同步   │
        └─────────┬──────────┘
                  │
        ┌─────────▼──────────┐
        │   Rust Core        │
        │   librustdesk.so   │
        │   (全局共享)       │
        └────────────────────┘
```

### 2.3 数据流

```
键盘输入流:
主屏 TextField → MethodChannel("send_key_string")
→ MainActivity → SessionState.forwardKeyString()
→ RemoteActivity MethodChannel("on_key_string")
→ Flutter → bind.sessionInputString()
→ Rust Core → 网络 → 远程设备

触摸流 (副屏):
RemotePage GestureDetector → inputModel.onPointer*
→ bind.sessionInputTouch*
→ Rust Core → 网络 → 远程设备

连接流:
主屏 输入ID → MethodChannel("launch_remote_on_display2")
→ MainActivity → 反射 setLaunchDisplayId(2)
→ RemoteActivity 启动 → remoteChannel.init_params
→ Flutter 直接导航到 RemotePage → 连接远程
```

---

## 三、软件选型与工作量分析

### 3.1 为什么选 RustDesk

| 因素 | 说明 |
|------|------|
| **开源** | AGPL-3.0 许可证，可自由修改和商用（需开源衍生代码） |
| **自建服务器** | 不依赖第三方云服务，客户可完全掌控数据 |
| **Flutter + Rust** | 跨平台 UI + 高性能核心，Android 端成熟 |
| **已有移动端** | 原生的 Android 触摸支持和视频解码已实现 |
| **社区活跃** | GitHub 15k+ stars，持续维护 |

### 3.2 费用说明

> ⚠️ **RustDesk 本身是完全开源免费的** (AGPL-3.0)，无需任何软件授权费。
> 以下费用仅指**双屏适配的定制开发工作量**（人工），属于一次性工程投入，并非软件的"价格"。

| 工作项 | 预估人天 | 说明 |
|------|:-------:|------|
| 架构分析 + 代码阅读 | 0.5 天 | 理解 Flutter+Rust+Android 三层交互 |
| 双屏 Activity 拆分 | 1 天 | RemoteActivity + 防呆 + Manifest |
| 跨屏通信层 | 0.5 天 | SessionState + MethodChannel 双向 |
| 主屏 ControlPage | 1 天 | 连接UI + 键盘输入 + 快捷按键 |
| 副屏适配 + 兼容 | 0.5 天 | 保持 RemotePage 零改动 |
| 联调测试 + 部署 | 1 天 | 真机验证 + 权限配置 |
| **合计** | **~4.5 天** | 一次性定制开发量 |

> 如果把本项目的改动贡献回 RustDesk 上游（发 PR），后续社区可免费享受双屏功能，长期维护成本为零。

### 3.3 竞品对比

| 方案 | 双屏原生支持 | 自建服务器 | 开源 | 移动端触摸 |
|------|:----------:|:--------:|:----:|:--------:|
| **RustDesk (本方案)** | ⚠️ 需定制 | ✅ | ✅ | ✅ |
| TeamViewer | ❌ | ❌ (强制云) | ❌ | ✅ |
| AnyDesk | ❌ | ❌ (强制云) | ❌ | ✅ |
| VNC (droidVNC) | ❌ | ✅ | ✅ | ⚠️ 仅服务端 |
| Sunshine+Moonlight | ❌ | ✅ | ✅ | ⚠️ 仅游戏串流 |

> 结论：RustDesk 是唯一可深度定制、支持自建服务器、且已有成熟移动端实现的方案。

---

## 四、开发过程（7月19日）

### 4.1 阶段一：源码分析（上午）

1. 克隆 `rustdesk/rustdesk` 主仓库到 `/Users/newlink/kemi/RustDesk/client`
2. 理解项目结构：
   - `flutter/lib/` — Flutter UI 层
   - `src/` — Rust 核心层
   - `flutter/android/` — Android 原生层 (Kotlin)
3. 分析关键文件：
   - `main.dart` — `runMobileApp()` 入口
   - `common.dart` — `connect()` 连接函数
   - `remote_page.dart` — 远程桌面页面
   - `MainActivity.kt` — MethodChannel 处理
   - `InputService.kt` — 无障碍输入服务
   - `ffi.kt` — JNI 桥接

**关键发现**：
- `connect()` 函数通过 `Navigator.push(RemotePage(...))` 进入远程桌面
- 触摸事件在 `RemotePage` 中通过 `GestureDetector` 处理
- `bind.sessionInputString(sessionId, value)` 发送键盘字符串到远程
- `gFFI.inputModel.inputKey('VK_*')` 发送虚拟键
- `platformFFI.invokeMethod(...)` → `MethodChannel("mChannel")` → Kotlin

### 4.2 阶段二：Kotlin 层实现（中午）

创建 2 个新文件，修改 2 个文件：

**新增 `SessionState.kt`**
```kotlin
object SessionState {
    @Volatile var remoteMethodChannel: MethodChannel? = null
    @Volatile var currentSessionId: String? = null
    @Volatile var isRemoteConnected: Boolean = false
    
    fun forwardKeyString(text: String) // 主屏→副屏 键盘文本
    fun forwardKeyEvent(keyName: String, down: Boolean) // 主屏→副屏 单键
    fun notifyConnectionState(connected: Boolean, sessionId: String?) // 状态同步
    fun reset() // 断开清理
}
```

**新增 `RemoteActivity.kt`**
- 继承 `FlutterActivity`
- `onCreate` 防呆检测：如果被启动到 Display 0，自动迁回 Display 2
- `configureFlutterEngine` 设置 `remoteChannel` MethodChannel
- 提供 `get_connection_params` / `notify_session_ready` / `notify_session_closed` / `finish_activity` 四个 handler
- 500ms 延迟推送 `init_params` 给 Flutter 端

**修改 `MainActivity.kt`**
- `onCreate` 添加防呆：检测到非 Display 0 → 迁回主屏
- `initFlutterChannel` 新增 5 个 handler：
  - `launch_remote_on_display2` — 反射启动 RemoteActivity 到 Display 2
  - `send_key_string` — 转发键盘文本
  - `send_key_event` — 转发虚拟键
  - `get_remote_state` — 查询连接状态
  - `close_remote` — 关闭副屏
- 新增 `launchRemoteOnDisplay2()` 方法，使用反射 `setLaunchDisplayId`

**修改 `AndroidManifest.xml`**
- 新增 `RemoteActivity` 声明：`singleInstance` + `landscape` + 完整 `configChanges`
- 添加 `android:resizeableActivity="true"`

### 4.3 阶段三：Flutter 层实现（下午）

**新增 `control_page.dart`** (521 行)
- 连接 UI：ID 输入 + 自动补全 + Connect 按钮（复用原有 `RawAutocomplete<Peer>` 和 `AllPeersLoader`）
- 键盘输入区：`TextField` 监听文本变化，diff 算法区分新增/删除字符
- 快捷按键栏：Enter / Tab / Esc / Del / Space / 方向键 / Ctrl+C/V/Z
- 连接流程：调用 `platformFFI.invokeMethod("launch_remote_on_display2", ...)`
- 断开流程：调用 `platformFFI.invokeMethod("close_remote")`

**修改 `main.dart`**
- 新增 `_initDualScreenRemoteListener()` 函数
- 监听 `remoteChannel` 的 4 个事件：
  - `init_params` → 导航到 RemotePage
  - `on_key_string` → `bind.sessionInputString()`
  - `on_key_event` → `gFFI.inputModel.inputKey()`
  - `finish_activity` → 关闭副屏
- 添加 `get_connection_params` 主动拉取（备选方案）

**修改 `home_page.dart`**
- 首页 Tab 从 `ConnectionPage` 切换为 `ControlPage`
- 保留原 `ConnectionPage` 不变（web 端仍可用）

### 4.4 阶段四：系统权限审查（参考 xtqx.md）

- 确认 `SYSTEM_ALERT_WINDOW` 已在 RustDesk manifest 中
- 添加 `android:resizeableActivity="true"` 确保双屏自由窗口兼容
- 整理部署验证清单

---

## 五、技术难点与解决方案

### 5.1 难点一：RustDesk 架构理解

**问题**: RustDesk 是 Flutter + Rust + Kotlin 三层混合架构，连接流程跨三层。

**解决**: 逐层追踪：
1. Flutter: `connect()` → `Navigator.push(RemotePage)` → `gFFI.start()`
2. Rust: `flutter.rs` 管理全局 `CUR_SESSION_ID`
3. Kotlin: `MainService` 使用 `MediaProjection` 录制屏幕

**关键洞察**: `librustdesk.so` 是进程级全局加载，两个 Activity 共享同一 Rust 状态。这意味着主屏 Flutter 也能调用 `bind.sessionInputString()` 操作副屏创建的 session。

### 5.2 难点二：跨屏通信

**问题**: 两个 Flutter 引擎各自独立，如何让主屏的键盘输入到达副屏的 RustDesk session？

**方案对比**:

| 方案 | 可行性 | 复杂度 | 选择 |
|------|:------:|:------:|:----:|
| A. 主屏直接调 `bind.sessionInputString()` | ✅ 同进程共享 .so | 低 | ❌ sessionId 跨引擎不可见 |
| B. MethodChannel → Kotlin → 另一个 MethodChannel | ✅ | 中 | ✅ **已采用** |
| C. EventBus / 广播 | ✅ | 高 | ❌ 过度设计 |
| D. 共享 FlutterEngine | ⚠️ API 限制 | 高 | ❌ |

**最终方案**: 方案 B
```
主屏 Flutter → MethodChannel("mChannel") → MainActivity
→ SessionState.forwardKeyString(text)
→ RemoteActivity MethodChannel("remoteChannel") → 副屏 Flutter
→ bind.sessionInputString(sessionId, text)
```

### 5.3 难点三：副屏 Activity 启动

**问题**: Android 没有公开 API 指定 Activity 启动到哪个 Display。

**解决**: 反射调用隐藏 API `ActivityOptions.setLaunchDisplayId(int)`。

```kotlin
val options = ActivityOptions.makeBasic()
val method = options.javaClass.getMethod(
    "setLaunchDisplayId",
    Int::class.javaPrimitiveType  // ⚠️ 注意: 是 javaPrimitiveType，不是 javaObjectType
)
method.invoke(options, 2)  // Display 2
```

这个方案来自 `chip.md` §2.3 的实战验证。如果反射失败，降级到默认 Display。

### 5.4 难点四：防呆机制

**问题**: Android 系统可能在恢复 Activity 时将其放到错误的 Display。

**解决**: 两个 Activity 都在 `onCreate` 极早期检测当前 Display ID：
- `MainActivity` 不在 Display 0 → 用公开 API `options.launchDisplayId = 0` 迁回
- `RemoteActivity` 不在 Display 2 → 用反射 `setLaunchDisplayId(2)` 迁回

### 5.5 难点五：副屏 RemotePage 零改动兼容

**问题**: 副屏需要复用原有的 `RemotePage`，不能改动其内部逻辑。

**解决**: RemoteActivity 的 Flutter 引擎通过 `remoteChannel.init_params` 接收连接参数，然后直接 `Navigator.pushAndRemoveUntil(RemotePage(...), (_) => false)` 清除所有页面并进入远程桌面。RemotePage 本身的触摸处理、视频渲染、键盘输入逻辑完全不改。

### 5.6 难点六：键盘输入到远程的转换

**问题**: 主屏 TextField 输入的是字符串，而 RustDesk 需要 `sessionInputString` 调用。

**解决**: 用简单 diff 算法：
- 新增字符 → `bind.sessionInputString(sessionId, newChars)`
- 删除字符 → `gFFI.inputModel.inputKey('VK_BACK')` × N 次
- 快捷按键 → `gFFI.inputModel.inputKey('VK_RETURN')` 等

---

## 六、文件清单

### 新增文件 (3 个)

| 文件 | 行数 | 说明 |
|------|:---:|------|
| `flutter/android/.../SessionState.kt` | 66 | 跨屏通信单例 |
| `flutter/android/.../RemoteActivity.kt` | 162 | 副屏专用 FlutterActivity |
| `flutter/lib/mobile/pages/control_page.dart` | 521 | 主屏控制页面 |

### 修改文件 (5 个)

| 文件 | 改动量 | 说明 |
|------|:---:|------|
| `AndroidManifest.xml` | +15 行 | RemoteActivity 声明 + resizeableActivity |
| `MainActivity.kt` | +80 行 | 防呆 + 5个MethodChannel handler + launchRemoteOnDisplay2 |
| `main.dart` | +80 行 | _initDualScreenRemoteListener + imports |
| `home_page.dart` | +2 行 | ControlPage 替换 ConnectionPage |
| (参考) `xtqx.md` | 读 | 权限配置参考 |

### 未改动（保持兼容）

| 文件 | 原因 |
|------|------|
| `remote_page.dart` | 副屏 100% 复用 |
| `connection_page.dart` | web 端仍使用 |
| `src/` (Rust Core) | 无需改动 |
| `ffi.kt` | 无需新增 JNI 方法 |
| `InputService.kt` | 副屏 RemotePage 直接使用 |
| `MainService.kt` | 副屏 RemotePage 直接使用 |

---

## 七、部署与验证

### 7.1 编译

```bash
cd /Users/newlink/kemi/RustDesk/client/flutter

# 1. 编译 Rust 核心 (如需要)
cd .. && cargo ndk -t arm64-v8a build --release

# 2. 编译 Flutter APK
cd flutter
flutter build apk --debug --target-platform android-arm64
```

### 7.2 一键部署脚本

```bash
#!/bin/bash
DEVICE="192.168.3.46:5555"
APK="build/app/outputs/flutter-apk/app-debug.apk"

adb connect $DEVICE
adb -s $DEVICE install -r $APK
adb -s $DEVICE shell appops set com.carriez.flutter_hbb SYSTEM_ALERT_WINDOW allow
adb -s $DEVICE shell am force-stop com.carriez.flutter_hbb
sleep 1
adb -s $DEVICE shell am start -n com.carriez.flutter_hbb/.MainActivity --display 0
echo "✅ 部署完成"
```

### 7.3 验证清单

```
□ ADB 连接: adb devices
□ 双屏检测: adb shell dumpsys display | grep "Display "
□ 权限确认: adb shell appops get com.carriez.flutter_hbb SYSTEM_ALERT_WINDOW
□ 主屏启动: adb shell am start -n ...MainActivity --display 0
□ 副屏直启: adb shell am start -n ...RemoteActivity --display 2 --es peer_id "123456789"
□ 防呆测试: adb shell am start -n ...MainActivity --display 2  (应自动迁回 Display 0)
□ 防呆测试: adb shell am start -n ...RemoteActivity --display 0 (应自动迁回 Display 2)
□ 键盘输入: 主屏输入文字 → 远程设备收到文字
□ 副屏触摸: 副屏滑动/点击 → 远程设备响应
□ 快捷按键: 主屏按 Enter/Tab → 远程设备响应
```

---

## 八、未来工作

### 8.1 短期（v1.1）

- [ ] **真机联调**: 在 RK356x/V900 上完整测试双屏流程
- [ ] **sessionId 传递优化**: 目前通过 `notify_session_ready` 回调，需验证时序
- [ ] **键盘输入优化**: 支持更多虚拟键（Ctrl+A/X、Shift+方向键 组合键等）
- [ ] **连接状态 UI**: 主屏 ControlPage 实时显示远程连接状态（延迟、帧率）
- [ ] **单屏降级**: 检测到单屏设备时自动回退到原始单屏模式

### 8.2 中期（v1.2）

- [ ] **音频转发**: 主屏麦克风 → 远程设备
- [ ] **剪贴板同步**: 主屏 Ctrl+C → 远程设备剪贴板
- [ ] **双屏同时操控**: 主屏键盘 + 副屏触摸同时工作，无竞态
- [ ] **断线重连**: 远程断开后主屏自动提示重连
- [ ] **多会话支持**: 主屏切换不同远程设备，副屏自动切换

### 8.3 长期（v2.0）

- [ ] **反向控制**: 远程设备控制 Android 主屏（即 Android 作为被控端时双屏显示）
- [ ] **副屏 PIP**: 副屏远程桌面支持画中画，叠加其他信息
- [ ] **手势增强**: 副屏双指缩放/旋转映射到远程
- [ ] **性能监控**: 主屏显示实时帧率、码率、延迟
- [ ] **AOSP 集成**: 作为系统级远程桌面方案，集成到 AOSP 构建

---

## 九、参考资源

| 资源 | 路径 | 内容 |
|------|------|------|
| RustDesk 源码 | `/Users/newlink/kemi/RustDesk/client/` | 主仓库 (Flutter+Rust+Kotlin) |
| 芯片实战手册 | `/Users/newlink/kemi/RustDesk/chip.md` | RK356x/V900 平台特性、双屏架构、踩坑记录 |
| 系统权限手册 | `/Users/newlink/kemi/RustDesk/xtqx.md` | SYSTEM_ALERT_WINDOW、ADB 调试、部署清单 |
| droidVNC-NG | https://github.com/bk138/droidVNC-NG | Android 屏幕录制 + 输入注入参考 |
| RustDesk 官网 | https://rustdesk.com | 产品文档、自建服务器指南 |

---

## 附录：架构决策记录 (ADR)

### ADR-001: 为什么用两个独立 Activity 而不是 Presentation API？

- **Presentation API** 只适合显示辅助信息（如播放器控制），不支持独立触摸交互
- **双 Activity** 各有独立生命周期、独立的触摸事件、独立的 Flutter 引擎
- 在 Go3DGlobe 项目中已成功验证此方案（`chip.md` §2.1）

### ADR-002: 为什么用 MethodChannel 中转而不是主屏直接调 FFI？

- 主屏 Flutter 引擎不知道副屏的 `sessionId`
- MethodChannel 中转虽然多一跳，但架构清晰、可追踪、可降级
- 延迟 ≤ 1ms（进程内通信），对键盘输入无感知

### ADR-003: 为什么副屏 RemotePage 零改动？

- RemotePage 已稳定运行多年，改动风险高
- 双屏只是改变了"谁启动它、谁给它发键盘"，不改变它的核心逻辑
- 保持零改动意味着未来 RustDesk 上游更新时，RemotePage 的改动自动生效

---

## 十、2026-07-25 编译与部署闭环记录

### 10.1 目标

- 确认 Android 全量代码可编译。
- 确认 APK 可推送到目标设备并可正常启动运行。

### 10.2 关键修复

- 修复 Flutter 依赖兼容：`octo_image` 和 `sqflite` 缓存包缺失导入导致的编译错误。
- 修复 Protobuf/Kotlin 兼容：将 `hasSeq/hasChr/hasControlKey` 改为 `getUnionCase()` 判断。
- 补齐 protobuf 编译工具路径：`/tmp/protoc3/bin/protoc`。

### 10.3 构建结果

- 命令：`flutter build apk --debug`
- 结果：通过
- 产物：`flutter/build/app/outputs/flutter-apk/app-debug.apk`
- 时间：`2026-07-25 21:54:45`
- 大小：约 `136MB`

### 10.4 设备验证 (192.168.1.6:5555)

- 安装：`adb install -r` -> `Success`
- 启动：`am start -n com.carriez.flutter_hbb/.MainActivity`
- 前台 Activity：`com.carriez.flutter_hbb/.MainActivity`
- 进程：`pidof com.carriez.flutter_hbb` 返回有效 PID
- 版本：`versionName=1.4.9`, `versionCode=67`
- 更新时间：`2026-07-25 21:56:09`

### 10.5 结论

- Android 编译、安装、启动全部通过，闭环完成。
