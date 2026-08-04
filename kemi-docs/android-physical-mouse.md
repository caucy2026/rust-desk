# Android PAD物理鼠标输入原理与维护

## 1. 范围与已确认行为

本文记录PAD外接物理鼠标的右键输入链路。触摸手势仍由`remote_input.dart`处理，不与本文的原生鼠标兼容层混用。

`1.4.46+123`已由用户真机确认：在远控画面所在屏幕点击物理鼠标右键，远端能够收到并执行右键。兼容层只在当前显示`RemotePage`的Activity中启用；PAD主页、设置、另一块物理屏幕以及其他App不转发远程右键。`1.4.51+156`进一步修复跨屏键盘窗口焦点导致的系统ANR，并增加漏失release时的右键状态自校准。

## 2. 原来为什么失效

远控协议一直支持右键，失败不在网络、MAC辅助功能或被控端模拟层：

```text
Flutter kSecondaryMouseButton
  → mouseButtonsToPeer(2) = right
  → sessionSendMouse(mousedown/mouseup, right)
  → 被控端 MouseButton::Right
```

问题位于Android到Flutter之间。不同PAD固件可能用三种形式上报外接鼠标右键：

1. 标准Flutter`PointerDownEvent/PointerUpEvent`，`buttons=2`；
2. Android原生`ACTION_BUTTON_PRESS/RELEASE + BUTTON_SECONDARY`；
3. 把鼠标右键转换成来源仍为鼠标的`KEYCODE_BACK`。

旧代码只依赖第一种Flutter指针事件。如果设备使用第二或第三种形式，事件在进入`InputModel`前已经被Android系统解释或丢弃。因此切换“触摸模式/鼠标模式”没有效果：这两个模式改变的是手指手势，不改变Android物理鼠标事件的产生方式。

## 3. 当前实现

### 3.1 Android原生入口

`PhysicalMouseRightButtonForwarder.kt`统一处理原生输入：

- `dispatchTouchEvent`捕获可能以触摸分发路径到达的鼠标次键；
- `dispatchGenericMotionEvent`捕获`ACTION_BUTTON_PRESS/RELEASE`；
- `dispatchKeyEvent`只兜底处理鼠标来源的`KEYCODE_BACK`；
- 触摸来源、键盘Back键和普通系统返回键不匹配，不会被拦截。

`MainActivity`和`RemoteActivity`各自持有一个转发器，事件通过各自Flutter Engine的`mChannel`回到同一屏幕上的远控会话，不使用全局广播，也不转发给另一块显示屏。

### 3.2 只在远控屏幕启用

`RemotePage.initState()`调用：

```text
set_remote_mouse_input_active = true
```

`RemotePage.dispose()`立即设为`false`。原生层只有在该标志为真时才消费右键；关闭时如果存在未释放的右键，会先补发`up`再停用，避免远端保持按下状态。

这样同时满足：

- 双屏设备只由显示远控画面的`RemoteActivity`响应；
- 单屏设备在`MainActivity`打开远控页时也能使用右键；
- 离开远控页后恢复Android自身右键/返回行为。

### 3.3 按下、移动和释放

右键不能简化为一次`click`，必须传递完整状态：

```text
原生 down
  → Flutter记录 _lastButtons = kSecondaryMouseButton
  → 发送 mousedown/right

按住移动
  → Flutter继续发送带right状态的mousemove

原生 up / cancel / 离开远控页
  → 清空本地按钮状态
  → 发送 mouseup/right
```

原生层的`secondaryDown`防止同一次物理动作同时产生`ACTION_DOWN`和`ACTION_BUTTON_PRESS`时重复发送；Flutter层的`_androidSecondaryMouseDownSent`保证只释放真实发送过的按下，并让释放不受中途权限状态变化影响。

### 3.4 右键看似“卡死”的真实原因（1.4.51+156）

真机记录显示物理右键`down/up`成对到达，但`MainActivity`反复发生：

```text
Input dispatching timed out (Application does not have a focused window)
```

问题来自跨屏键盘保持逻辑，而不是远程鼠标协议。键盘位于另一显示屏时，旧实现给远控源Activity添加`FLAG_NOT_FOCUSABLE`，希望避免触摸抢走IME焦点；但Android InputDispatcher仍会把物理鼠标事件指向远控窗口，窗口不可聚焦便进入等待，约5秒后形成ANR。

当前规则：

- 永远不把显示远控画面的源Activity改成`NOT_FOCUSABLE`；
- 键盘保持依赖代理Activity现有的指针时间戳、IME隐藏原因分类和恢复逻辑；
- 若固件漏发右键release，在下一个`buttonState`已无次键的鼠标事件中补发`up`；
- 不把补发时遇到的普通hover/move事件吃掉，保证鼠标移动继续正常。

这条边界必须保留：不能再通过取消远控窗口焦点来换取键盘常驻，否则任意物理输入都可能产生系统ANR。

### 3.5 1.4.51+156真机收口证据

- 固定签名候选保留数据覆盖安装到`192.168.3.63:5555`，系统回读版本`1.4.51+156`。
- 清空旧日志后，在跨屏键盘开启并继续输入文字的现场连续采集19次物理右键，每次均严格形成一组`down/up on display 2`。
- 同一测试窗口内未再出现`Application does not have a focused window`、`Input dispatching timed out`或新的KEMI ANR；键盘代理仍持续产生`commit_text`。
- 用户确认本版本相对稳定。它是当前PAD回归基线，但特殊鼠标的右键按住拖动仍需观察固件是否在按住期间错误清空`buttonState`。

## 4. 涉及文件

- `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/PhysicalMouseRightButtonForwarder.kt`：Android次键/Back兼容状态机。
- `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/MainActivity.kt`：单屏Activity接入。
- `flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/RemoteActivity.kt`：副屏Activity接入。
- `flutter/lib/mobile/pages/remote_page.dart`：远控页生命周期开关。
- `flutter/lib/mobile/pages/server_page.dart`：Android平台消息入口。
- `flutter/lib/models/input_model.dart`：远程右键按下、移动和释放状态。
- `flutter/lib/common.dart`：Flutter按钮位到协议按钮名的既有映射。
- `src/server/input_service.rs`：被控端既有`MouseButton::Right`执行逻辑。

没有修改远控网络协议、视频连接方式或MAC权限模型。

## 5. 验收与排查

每次修改物理鼠标输入后至少验证：

1. 远控画面上单击右键，远端弹出上下文菜单。
2. 按住右键移动后释放，远端没有留下按键卡住。
3. 触摸模式和鼠标模式下物理右键均有效。
4. 双屏时主屏右键不发送到远端；副屏远控页右键正常。
5. 单屏设备打开远控页时右键正常；退出远控页后系统行为恢复。
6. 左键、滚轮、触摸单击、长按右键和双指滚动没有回归。

原生层每次实际转发都会记录：

```text
[PhysicalMouse] right down on display N
[PhysicalMouse] right up on display N
```

ADB排查顺序：

```bash
adb logcat -s RemoteActivity mMainActivity
```

- 没有`down`：PAD没有以上述鼠标来源形式上报，需要读取`getevent -lt`确认厂商键值。
- 有`down`无`up`：检查设备释放/取消事件，退出远控页应触发兜底释放。
- `down/up`成对但远端无响应：继续检查Flutter平台消息、当前会话输入权限和被控端辅助功能，不再修改Android按键识别。
