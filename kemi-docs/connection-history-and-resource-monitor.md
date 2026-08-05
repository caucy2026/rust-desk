# 连接记录与客户端资源监控

> 首次实现版本：KEMI 1.4.52+157（2026-08-05）

## 1. 首页入口恢复

首页设备区域统一保留六个入口：最近访问、收藏、已发现、通讯录、可访问设备、连接记录。此前KEMI关闭账号功能后，`PeerTabModel.isEnabled`把通讯录和可访问设备一并隐藏；1.4.52将“账号是否可用”和“入口是否展示”解耦。

当前开源`hbbs/hbbr`不提供账号、通讯录和设备分组API，因此：

- 通讯录、可访问设备图标继续展示；
- 点击后显示当前服务能力说明；
- 不恢复登录按钮，也不伪造可同步的数据；
- 未来接入带账号API的服务端后，原页面会自动恢复。

从旧版升级时，原五项的显示和排序设置原样保留，新连接记录自动追加在末尾并默认显示。

## 2. 连接记录的数据口径

连接记录只统计普通远程桌面，不把文件传输、摄像头、终端和端口转发混入P2P统计。每条记录包括：

- 发起连接时间；
- 远端ID和连接后取得的主机名；
- 连接中、已连接、已结束、连接失败、异常中断；
- Rust核心在`connection_ready`事件中确认的P2P或中继结果；
- 实际传输类型，如TCP、UDP、IPv6、Relay或WebSocket；
- 建立后的会话时长；
- 建立失败时的错误原因。

状态流如下：

```text
FFI.start
  -> connecting
  -> connection_ready: connected + P2P/中继 + streamType
  -> peer_info: 补充主机名
  -> close/onDone: ended

连接建立前收到Connection Error
  -> failed + 失败原因

应用退出时仍为connecting/connected
  -> 下次启动修复为interrupted
```

记录保存在本机Flutter配置`kemi-connection-history-v1`中，最多保留200条，跨应用重启继续存在。异步保存采用串行队列，避免较早快照晚完成而覆盖最新状态。用户可删除单条记录，也可在二次确认后清空全部记录。

## 3. CPU为什么可能超过100%

Android的`Process.getElapsedCpuTime()`是进程所有线程的CPU时间总和。本监控按一秒墙钟时间计算多核累计值，因此：

- 100%约等于持续占满一个逻辑核心；
- 162%约等于持续使用1.62个逻辑核心；
- 它不是“整机CPU超过100%”。

1.4.52同时显示：

- `CPU（多核累计）`：保留Android进程常用口径；
- `CPU（整机占比）`：多核累计除以逻辑核心数，限制在0%到100%。

即使视频使用硬件解码，网络收发、协议处理、像素拷贝、Flutter合成、缩放、光标和手势仍会使用CPU，所以硬解不等于CPU接近零。

## 4. 硬解状态不能靠开关猜测

资源窗口分开显示三层信息：

1. 视频编码：实际协商的VP8/VP9/AV1/H264/H265；
2. 硬件解码设置：用户配置是开启还是关闭；
3. 实际解码器：`scrap::codec::Decoder`成功创建的真实后端。

1.4.52的Android构建只使用`flutter,hwcodec`，因此H264/H265成功创建`HwRamDecoder`时上报`FFmpeg hardware`，VP9仍只能上报`Software VP9`。从1.4.53开始Android arm64原生库使用`flutter,hwcodec,mediacodec`：H264/H265保留原路径，VP9可按设备真实能力创建厂商MediaCodec。硬解创建或运行失败时仍明确回退并上报`Software VP9`。因此“设置已开启”不会再被误写成“当前正在硬解”。

## 5. 真机验收约束

指定PAD `192.168.3.63`具有Display 0主屏和Display 2 HDMI副屏。后续所有KEMI真机操作、输入注入、截图和验收都只允许在Display 2进行；主屏只可读取状态，不允许作为功能测试屏。

验收至少覆盖：

- 副屏发起一次成功连接，连接记录出现主机名、时间和真实P2P/中继结果；
- 结束连接后状态变为已结束且时长停止增长；
- 制造一次连接失败，记录包含错误原因；
- 删除单条、清空全部以及重启后持久化；
- 资源窗口连续刷新，确认整机占比、多核累计、编码、设置状态、实际解码器均显示；
- 通讯录、可访问设备、连接记录三个入口均在原标签行可见。

## 6. 1.4.52副屏实机结果

2026-08-05在`192.168.3.63`完成候选闭环，所有点击、截图、远控和重启均明确指定Display 2，主屏未参与测试：

- 首页同一行显示最近访问、收藏、已发现、通讯录、可访问设备、连接记录六个入口；选中连接记录时，图标下方显示用途说明。
- 从副屏连接本机`238638760 / laptop-luopp1ch`成功，核心实际报告`P2P直连·TCP`；资源窗口报告H265、硬解设置已开启、实际解码器为`FFmpeg hardware`。
- 同一采样时刻CPU整机占比为2.5%，多核累计为20.1%，证明两个口径可以同时观察，也证明不能用多核累计超过100%判断是否软解。
- 主动断开后生成一条已结束记录，包含开始时间、主机名、远端ID、`P2P·TCP`和会话时长；强制结束应用并用`am start --display 2`重新启动后记录仍存在。
- 每条记录右侧提供删除按钮，页面右上提供带二次确认的清空记录；本次保留真实P2P样例供人工验收。

候选APK为`BIN/KEMI-远程桌面-PAD-1.4.52+157-release.apk`，大小24,535,635字节，SHA-256为`b9c902c7541e3cd47bbb9ec81d7cc1e4909376962fede34820ff4a0223b9720e`。它是PAD单端候选，不得单独覆盖`BIN/release`正式四端批次。

## 7. 1.4.53 Android VP9硬解闭环

### 7.1 原来为什么始终是Software VP9

PAD原生库此前用`flutter,hwcodec`编译。RustDesk的该feature在Android上只给H264/H265创建FFmpeg硬件后端，VP9分支仍直接创建libvpx。系统支持VP9、设置打开和编码协商为VP9三个条件同时成立，也不会自动进入Android MediaCodec。

### 7.2 当前选择与回退规则

1. Java层从`MediaCodecList.REGULAR_CODECS`枚举编解码器，只发布系统确认`isHardwareAccelerated=true`的组件，并携带MIME、组件名、分辨率范围和输出色彩格式。
2. Rust层按`video/x-vnd.on2.vp9`选择真实硬件解码器；当前Flutter渲染链需要CPU可读像素，所以只接受明确的I420或NV12 ByteBuffer输出，不使用Surface-only，也不在缺少Image平面信息时猜测Flexible布局。
3. MediaCodec按当前PAD屏幕尺寸和组件最大分辨率配置；运行时读取真实`width/height/stride/slice-height/crop/color-format`，把I420或NV12转换为Flutter需要的ARGB/ABGR。
4. MediaCodec不可用、配置失败或输出格式不受支持时，当前会话释放硬解并创建libvpx软件VP9，优先保证有画面。资源窗口显示的是最终实例名称，不显示愿望状态。

### 7.3 真机证据

2026-08-05只在`192.168.3.63`的Display 2副屏安装和操作`1.4.53+158`。远端实际协商VP9后，Android日志给出：

```text
makeComponentInstance(OMX.uapi.video.decoder.vp9)
mime = "video/x-vnd.on2.vp9"
configured width=1920 height=1280 color-format=21
output width=1920 height=1080 stride=1920 slice-height=1080
```

`color-format=21`是NV12半平面输出。副屏实际画面颜色、尺寸和动态刷新正常；厂商组件持续处于Executing，证明VP9码流确实由`OMX.uapi.video.decoder.vp9`处理，而不是只枚举到了硬件名称后仍用libvpx。

测试期间系统另有外部PID通过ActivityManager强停KEMI远程桌面，同时也强停KEMI Email；`logcat -b crash`为空，系统明确记录`Force stopping ... from pid`及SIGKILL。这类外部管理动作和MediaCodec异常必须分开统计，不能把外部强停写成解码崩溃。

最终固定签名候选为`BIN/KEMI-远程桌面-PAD-1.4.53+158-release.apk`，大小24,545,396字节，SHA-256为`46da46668335d7baff995ad01fb7ccad022ab9ce72b86b764ef0c59d3a0c6945`。它只归档PAD候选，不单独覆盖`BIN/release`正式四端批次。
