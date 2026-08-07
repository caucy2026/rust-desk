// main window right pane

import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_hbb/common/widgets/connection_page_title.dart';
import 'package:flutter_hbb/consts.dart';
import 'package:flutter_hbb/desktop/widgets/popup_menu.dart';
import 'package:flutter_hbb/models/state_model.dart';
import 'package:get/get.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:window_manager/window_manager.dart';
import 'package:flutter_hbb/models/peer_model.dart';

import '../../common.dart';
import '../../common/formatter/id_formatter.dart';
import '../../common/widgets/peer_tab_page.dart';
import '../../common/widgets/autocomplete.dart';
import '../../models/platform_model.dart';
import '../../desktop/widgets/material_mod_popup_menu.dart' as mod_menu;

const _kemiCloudDownloadPage = 'http://kemi-chat.newlinksz.com:21120/kemi-desk';

class OnlineStatusWidget extends StatefulWidget {
  const OnlineStatusWidget({Key? key, this.onSvcStatusChanged})
      : super(key: key);

  final VoidCallback? onSvcStatusChanged;

  @override
  State<OnlineStatusWidget> createState() => _OnlineStatusWidgetState();
}

/// State for the connection page.
class _OnlineStatusWidgetState extends State<OnlineStatusWidget> {
  final _svcStopped = Get.find<RxBool>(tag: 'stop-service');
  final RxInt _remoteConnectionCount = 0.obs;
  final RxInt _screenCaptureCount = 0.obs;
  final RxInt _screenCaptureFrameCount = 0.obs;
  final RxDouble _resourceCpuPercent = 0.0.obs;
  final RxInt _resourceMemoryBytes = 0.obs;
  final RxInt _resourceProcessCount = 0.obs;
  final RxDouble _resourceMainCpuPercent = 0.0.obs;
  final RxInt _resourceMainMemoryBytes = 0.obs;
  final RxDouble _resourceServerCpuPercent = 0.0.obs;
  final RxInt _resourceServerMemoryBytes = 0.obs;
  final RxBool _resourceSessionActive = false.obs;
  final RxInt _resourceSessionSeconds = 0.obs;
  final RxDouble _resourceSessionCpuAveragePercent = 0.0.obs;
  final RxDouble _resourceSessionCpuPeakPercent = 0.0.obs;
  final RxInt _resourceSessionMemoryPeakBytes = 0.obs;
  Timer? _updateTimer;

  double get em => 14.0;
  double? get height => bind.isIncomingOnly() ? null : em * 3;

  @override
  void initState() {
    super.initState();
    _updateTimer = periodic_immediate(Duration(seconds: 1), () async {
      updateStatus();
    });
  }

  @override
  void dispose() {
    _updateTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isIncomingOnly = bind.isIncomingOnly();
    startServiceWidget() => Offstage(
          offstage: !_svcStopped.value,
          child: InkWell(
                  onTap: () async {
                    await start_service(true);
                  },
                  child: Text(translate("Start service"),
                      style: TextStyle(
                          decoration: TextDecoration.underline, fontSize: em)))
              .marginOnly(left: em),
        );

    Widget basicWidget() {
      widget.onSvcStatusChanged?.call();
      final serverReady =
          !_svcStopped.value && stateGlobal.svcStatus.value == SvcStatus.ready;
      final serverConnecting = !_svcStopped.value &&
          stateGlobal.svcStatus.value == SvcStatus.connecting;
      final serverColor = serverReady
          ? const Color.fromARGB(255, 50, 190, 166)
          : serverConnecting
              ? kColorWarn
              : Colors.grey.shade400;
      final serverDetails = _svcStopped.value
          ? '本机远程服务当前没有运行。\n\n“就绪”只表示客户端已连接ID/信令服务器，可以发起或接收连接；不代表已有设备连接，也不代表正在抓屏。'
          : serverReady
              ? 'ID/信令服务器已连接，可以发起或接收远程连接。\n\n“就绪”不代表已有设备连接，也不代表正在抓屏，请分别查看“连接”和“抓屏”指示。'
              : serverConnecting
                  ? '正在连接ID/信令服务器，请稍候。\n\n服务器就绪后本项会变为绿色。'
                  : 'ID/信令服务器当前不可用，请检查网络和服务器配置。';
      return Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          _buildStatusIndicator(
            label: '就绪',
            color: serverColor,
            tooltip: serverReady ? '服务器已就绪' : '服务器未就绪',
            details: serverDetails,
            onTap: serverReady && isMacOS ? _showReadyDetails : null,
            marginLeft: em,
          ),
          _buildStatusIndicator(
            label: '连接',
            color: _remoteConnectionCount.value > 0
                ? const Color.fromARGB(255, 50, 190, 166)
                : Colors.grey.shade400,
            tooltip: _remoteConnectionCount.value > 0 ? '远程已连接' : '没有远程连接',
            details: _remoteConnectionCount.value > 0
                ? '当前有 ${_remoteConnectionCount.value} 个已认证的远程控制会话。\n\n绿色：有设备正在远程连接本机。\n灰色：当前没有远程控制设备。'
                : '当前没有已认证的远程控制会话。\n\n绿色：有设备正在远程连接本机。\n灰色：当前没有远程控制设备。',
          ),
          _buildStatusIndicator(
            label: '抓屏',
            color: _screenCaptureCount.value > 0
                ? const Color.fromARGB(255, 50, 190, 166)
                : Colors.grey.shade400,
            tooltip: _screenCaptureCount.value > 0 ? '正在抓屏' : '没有抓屏',
            details: _screenCaptureCount.value > 0
                ? '正在抓屏，当前抓屏会话已成功抓取 ${_screenCaptureFrameCount.value} 次画面。\n\n这里统计的是内部采集器成功取得有效画面的次数，不是屏幕数量。\n\n如果“连接”已经变灰但“抓屏”仍为绿色，说明远程断开后的采集尚未释放，应视为异常。'
                : '当前没有屏幕采集循环。\n\n绿色：本机正在抓取屏幕画面。\n灰色：本机没有抓取屏幕。',
          ),
          _buildStatusIndicator(
            label:
                '资源 CPU ${_formatCpu(_resourceCpuPercent.value)} · ${_formatMemory(_resourceMemoryBytes.value)}',
            color: _resourceSessionActive.value
                ? const Color.fromARGB(255, 50, 190, 166)
                : Colors.grey.shade400,
            tooltip: 'KEMI实时CPU和内存占用',
            onTap: _showResourceDetails,
          ),
          // stop
          if (!isIncomingOnly) startServiceWidget(),
          // KEMI ships with the company rendezvous/relay configuration, so the
          // public-server self-hosting guide is intentionally not displayed.
        ],
      );
    }

    return Container(
      height: height,
      child: Obx(() => isIncomingOnly
          ? Column(
              children: [
                basicWidget(),
                Align(
                        child: startServiceWidget(),
                        alignment: Alignment.centerLeft)
                    .marginOnly(top: 2.0, left: 22.0),
              ],
            )
          : basicWidget()),
    ).paddingOnly(right: isIncomingOnly ? 8 : 0);
  }

  Widget _buildStatusIndicator({
    required String label,
    required Color color,
    required String tooltip,
    String? details,
    VoidCallback? onTap,
    double marginLeft = 7,
  }) {
    return Tooltip(
      message: tooltip,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap ??
            () => showDialog<void>(
                  context: context,
                  builder: (dialogContext) => AlertDialog(
                    title: Text('$label状态'),
                    content: Text(details ?? ''),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.of(dialogContext).pop(),
                        child: const Text('知道了'),
                      ),
                    ],
                  ),
                ),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: color.withAlpha(140)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 7,
                height: 7,
                decoration: BoxDecoration(color: color, shape: BoxShape.circle),
              ),
              const SizedBox(width: 4),
              Text(label, style: TextStyle(fontSize: 12, color: color)),
            ],
          ),
        ),
      ),
    ).marginOnly(left: marginLeft);
  }

  String _formatCpu(double value) {
    return '${value.toStringAsFixed(value < 10 ? 1 : 0)}%';
  }

  String _formatMemory(int bytes) {
    final mib = bytes / (1024 * 1024);
    if (mib >= 1024) {
      return '${(mib / 1024).toStringAsFixed(1)} GB';
    }
    return '${mib.toStringAsFixed(mib < 10 ? 1 : 0)} MB';
  }

  String _formatDuration(int seconds) {
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    final remainSeconds = seconds % 60;
    if (hours > 0) {
      return '$hours小时${minutes.toString().padLeft(2, '0')}分';
    }
    if (minutes > 0) {
      return '$minutes分${remainSeconds.toString().padLeft(2, '0')}秒';
    }
    return '$remainSeconds秒';
  }

  void _showResourceDetails() {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('资源状态'),
        content: Obx(
          () => SizedBox(
            width: 390,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'KEMI总占用',
                  style: Theme.of(dialogContext).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                Text(
                    'CPU：${_formatCpu(_resourceCpuPercent.value)}\n内存：${_formatMemory(_resourceMemoryBytes.value)}\n相关进程：${_resourceProcessCount.value} 个'),
                const SizedBox(height: 14),
                Text(
                  '进程构成',
                  style: Theme.of(dialogContext).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                Text(
                    '主界面与辅助进程：CPU ${_formatCpu(_resourceMainCpuPercent.value)}，内存 ${_formatMemory(_resourceMainMemoryBytes.value)}\n远程服务进程：CPU ${_formatCpu(_resourceServerCpuPercent.value)}，内存 ${_formatMemory(_resourceServerMemoryBytes.value)}'),
                const SizedBox(height: 14),
                Text(
                  _resourceSessionActive.value ? '当前远程会话' : '最近一次远程会话',
                  style: Theme.of(dialogContext).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                Text(
                    '状态：${_resourceSessionActive.value ? '正在监控' : '已结束'}\n持续时间：${_formatDuration(_resourceSessionSeconds.value)}\n平均CPU：${_formatCpu(_resourceSessionCpuAveragePercent.value)}\n峰值CPU：${_formatCpu(_resourceSessionCpuPeakPercent.value)}\n峰值内存：${_formatMemory(_resourceSessionMemoryPeakBytes.value)}'),
                const SizedBox(height: 12),
                Text(
                  'CPU 100%表示占满一个逻辑核心，多线程时可能超过100%。远程会话期间每5秒写入一次程序日志，便于排查长时间运行问题。',
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('知道了'),
          ),
        ],
      ),
    );
  }

  void _showReadyDetails() {
    var copied = false;
    showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          title: const Text('就绪状态'),
          content: SizedBox(
            width: 400,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'ID/信令服务器已连接，可以发起或接收远程连接。',
                ),
                const SizedBox(height: 14),
                const Divider(height: 1),
                const SizedBox(height: 14),
                Text(
                  '云端客户端下载',
                  style: Theme.of(dialogContext).textTheme.titleSmall,
                ),
                const SizedBox(height: 6),
                const Text('Windows、Linux、PAD/Android 客户端均在同一个云端页面下载。'),
                const SizedBox(height: 14),
                Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      QrImageView(
                        data: _kemiCloudDownloadPage,
                        size: 156,
                        backgroundColor: Colors.white,
                      ),
                      const SizedBox(height: 6),
                      const Text('扫码打开云端下载页', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                const Text('下载地址', style: TextStyle(fontSize: 11)),
                const SizedBox(height: 5),
                Container(
                  width: double.infinity,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  decoration: BoxDecoration(
                    color: Theme.of(dialogContext)
                        .colorScheme
                        .surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    _kemiCloudDownloadPage,
                    style: TextStyle(
                      fontSize: 11,
                      color: Theme.of(dialogContext).colorScheme.primary,
                    ),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () async {
                await Clipboard.setData(
                  const ClipboardData(text: _kemiCloudDownloadPage),
                );
                final data = await Clipboard.getData(Clipboard.kTextPlain);
                final succeeded = data?.text == _kemiCloudDownloadPage;
                if (!dialogContext.mounted) return;
                setDialogState(() => copied = succeeded);
                showToast(succeeded ? '已复制' : '复制失败，请重试');
              },
              child: Text(copied ? '已复制' : '复制'),
            ),
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('知道了'),
            ),
          ],
        ),
      ),
    );
  }

  updateStatus() async {
    final status =
        jsonDecode(await bind.mainGetConnectStatus()) as Map<String, dynamic>;
    final statusNum = status['status_num'] as int;
    if (statusNum == 0) {
      stateGlobal.svcStatus.value = SvcStatus.connecting;
    } else if (statusNum == -1) {
      stateGlobal.svcStatus.value = SvcStatus.notReady;
    } else if (statusNum == 1) {
      stateGlobal.svcStatus.value = SvcStatus.ready;
    } else {
      stateGlobal.svcStatus.value = SvcStatus.notReady;
    }
    try {
      final count = status['video_conn_count'] as int;
      stateGlobal.videoConnCount.value = count;
      _remoteConnectionCount.value = count;
    } catch (_) {}
    try {
      _screenCaptureCount.value = status['screen_capture_count'] as int;
    } catch (_) {}
    try {
      _screenCaptureFrameCount.value =
          status['screen_capture_frame_count'] as int;
    } catch (_) {}
    try {
      _resourceCpuPercent.value =
          (status['resource_cpu_percent'] as num).toDouble();
      _resourceMemoryBytes.value = status['resource_memory_bytes'] as int;
      _resourceProcessCount.value = status['resource_process_count'] as int;
      _resourceMainCpuPercent.value =
          (status['resource_main_cpu_percent'] as num).toDouble();
      _resourceMainMemoryBytes.value =
          status['resource_main_memory_bytes'] as int;
      _resourceServerCpuPercent.value =
          (status['resource_server_cpu_percent'] as num).toDouble();
      _resourceServerMemoryBytes.value =
          status['resource_server_memory_bytes'] as int;
      _resourceSessionActive.value = status['resource_session_active'] as bool;
      _resourceSessionSeconds.value = status['resource_session_seconds'] as int;
      _resourceSessionCpuAveragePercent.value =
          (status['resource_session_cpu_average_percent'] as num).toDouble();
      _resourceSessionCpuPeakPercent.value =
          (status['resource_session_cpu_peak_percent'] as num).toDouble();
      _resourceSessionMemoryPeakBytes.value =
          status['resource_session_memory_peak_bytes'] as int;
    } catch (_) {}
  }
}

/// Connection page for connecting to a remote peer.
class ConnectionPage extends StatefulWidget {
  const ConnectionPage({Key? key, this.showOnlineStatus = true})
      : super(key: key);

  final bool showOnlineStatus;

  @override
  State<ConnectionPage> createState() => _ConnectionPageState();
}

/// State for the connection page.
class _ConnectionPageState extends State<ConnectionPage>
    with SingleTickerProviderStateMixin, WindowListener {
  /// Controller for the id input bar.
  final _idController = IDTextEditingController();

  final RxBool _idInputFocused = false.obs;
  final FocusNode _idFocusNode = FocusNode();
  final TextEditingController _idEditingController = TextEditingController();

  String selectedConnectionType = 'Connect';

  bool isWindowMinimized = false;

  final AllPeersLoader _allPeersLoader = AllPeersLoader();

  // https://github.com/flutter/flutter/issues/157244
  Iterable<Peer> _autocompleteOpts = [];

  final _menuOpen = false.obs;

  @override
  void initState() {
    super.initState();
    _allPeersLoader.init(setState);
    _idFocusNode.addListener(onFocusChanged);
    if (_idController.text.isEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        final lastRemoteId = await bind.mainGetLastRemoteId();
        if (lastRemoteId != _idController.id) {
          setState(() {
            _idController.id = lastRemoteId;
          });
        }
      });
    }
    Get.put<TextEditingController>(_idEditingController);
    Get.put<IDTextEditingController>(_idController);
    windowManager.addListener(this);
  }

  @override
  void dispose() {
    _idController.dispose();
    windowManager.removeListener(this);
    _allPeersLoader.clear();
    _idFocusNode.removeListener(onFocusChanged);
    _idFocusNode.dispose();
    _idEditingController.dispose();
    if (Get.isRegistered<IDTextEditingController>()) {
      Get.delete<IDTextEditingController>();
    }
    if (Get.isRegistered<TextEditingController>()) {
      Get.delete<TextEditingController>();
    }
    super.dispose();
  }

  @override
  void onWindowEvent(String eventName) {
    super.onWindowEvent(eventName);
    if (eventName == 'minimize') {
      isWindowMinimized = true;
    } else if (eventName == 'maximize' || eventName == 'restore') {
      if (isWindowMinimized && isWindows) {
        // windows can't update when minimized.
        Get.forceAppUpdate();
      }
      isWindowMinimized = false;
    }
  }

  @override
  void onWindowEnterFullScreen() {
    // Remove edge border by setting the value to zero.
    stateGlobal.resizeEdgeSize.value = 0;
  }

  @override
  void onWindowLeaveFullScreen() {
    // Restore edge border to default edge size.
    stateGlobal.resizeEdgeSize.value = stateGlobal.isMaximized.isTrue
        ? kMaximizeEdgeSize
        : windowResizeEdgeSize;
  }

  @override
  void onWindowClose() {
    super.onWindowClose();
    bind.mainOnMainWindowClose();
  }

  void onFocusChanged() {
    _idInputFocused.value = _idFocusNode.hasFocus;
    if (_idFocusNode.hasFocus) {
      if (_allPeersLoader.needLoad) {
        _allPeersLoader.getAllPeers();
      }

      final textLength = _idEditingController.value.text.length;
      // Select all to facilitate removing text, just following the behavior of address input of chrome.
      _idEditingController.selection =
          TextSelection(baseOffset: 0, extentOffset: textLength);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isOutgoingOnly = bind.isOutgoingOnly();
    return Column(
      children: [
        Expanded(
            child: Column(
          children: [
            Row(
              children: [
                Flexible(child: _buildRemoteIDTextField(context)),
              ],
            ).marginOnly(top: 22),
            SizedBox(height: 12),
            Divider().paddingOnly(right: 12),
            Expanded(child: PeerTabPage()),
          ],
        ).paddingOnly(left: 12.0)),
        if (!isOutgoingOnly && widget.showOnlineStatus)
          const Divider(height: 1),
        if (!isOutgoingOnly && widget.showOnlineStatus) OnlineStatusWidget()
      ],
    );
  }

  /// Callback for the connect button.
  /// Connects to the selected peer.
  void onConnect(
      {bool isFileTransfer = false,
      bool isViewCamera = false,
      bool isTerminal = false}) {
    var id = _idController.id;
    connect(context, id,
        isFileTransfer: isFileTransfer,
        isViewCamera: isViewCamera,
        isTerminal: isTerminal);
  }

  /// UI for the remote ID TextField.
  /// Search for a peer.
  Widget _buildRemoteIDTextField(BuildContext context) {
    var w = Container(
      width: 320 + 20 * 2,
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 22),
      decoration: BoxDecoration(
          borderRadius: const BorderRadius.all(Radius.circular(13)),
          border: Border.all(color: Theme.of(context).colorScheme.background)),
      child: Ink(
        child: Column(
          children: [
            getConnectionPageTitle(context, false).marginOnly(bottom: 15),
            Row(
              children: [
                Expanded(
                    child: RawAutocomplete<Peer>(
                  optionsBuilder: (TextEditingValue textEditingValue) {
                    if (textEditingValue.text == '') {
                      _autocompleteOpts = const Iterable<Peer>.empty();
                    } else if (_allPeersLoader.peers.isEmpty &&
                        !_allPeersLoader.isPeersLoaded) {
                      Peer emptyPeer = Peer(
                        id: '',
                        username: '',
                        hostname: '',
                        alias: '',
                        platform: '',
                        tags: [],
                        hash: '',
                        password: '',
                        forceAlwaysRelay: false,
                        rdpPort: '',
                        rdpUsername: '',
                        loginName: '',
                        device_group_name: '',
                        note: '',
                      );
                      _autocompleteOpts = [emptyPeer];
                    } else {
                      String textWithoutSpaces =
                          textEditingValue.text.replaceAll(" ", "");
                      if (int.tryParse(textWithoutSpaces) != null) {
                        textEditingValue = TextEditingValue(
                          text: textWithoutSpaces,
                          selection: textEditingValue.selection,
                        );
                      }
                      String textToFind = textEditingValue.text.toLowerCase();
                      _autocompleteOpts = _allPeersLoader.peers
                          .where((peer) =>
                              peer.id.toLowerCase().contains(textToFind) ||
                              peer.username
                                  .toLowerCase()
                                  .contains(textToFind) ||
                              peer.hostname
                                  .toLowerCase()
                                  .contains(textToFind) ||
                              peer.alias.toLowerCase().contains(textToFind))
                          .toList();
                      _allPeersLoader.queryOnlines(_autocompleteOpts);
                    }
                    return _autocompleteOpts;
                  },
                  focusNode: _idFocusNode,
                  textEditingController: _idEditingController,
                  fieldViewBuilder: (
                    BuildContext context,
                    TextEditingController fieldTextEditingController,
                    FocusNode fieldFocusNode,
                    VoidCallback onFieldSubmitted,
                  ) {
                    updateTextAndPreserveSelection(
                        fieldTextEditingController, _idController.text);
                    return Obx(() => TextField(
                          autocorrect: false,
                          enableSuggestions: false,
                          keyboardType: TextInputType.visiblePassword,
                          focusNode: fieldFocusNode,
                          style: const TextStyle(
                            fontFamily: 'WorkSans',
                            fontSize: 22,
                            height: 1.4,
                          ),
                          maxLines: 1,
                          cursorColor:
                              Theme.of(context).textTheme.titleLarge?.color,
                          decoration: InputDecoration(
                              filled: false,
                              counterText: '',
                              hintText: _idInputFocused.value
                                  ? null
                                  : translate('Enter Remote ID'),
                              contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 15, vertical: 13)),
                          controller: fieldTextEditingController,
                          inputFormatters: [IDTextInputFormatter()],
                          onChanged: (v) {
                            _idController.id = v;
                          },
                          onSubmitted: (_) {
                            onConnect();
                          },
                        ).workaroundFreezeLinuxMint());
                  },
                  onSelected: (option) {
                    setState(() {
                      _idController.id = option.id;
                      FocusScope.of(context).unfocus();
                    });
                  },
                  optionsViewBuilder: (BuildContext context,
                      AutocompleteOnSelected<Peer> onSelected,
                      Iterable<Peer> options) {
                    options = _autocompleteOpts;
                    double maxHeight = options.length * 50;
                    if (options.length == 1) {
                      maxHeight = 52;
                    } else if (options.length == 3) {
                      maxHeight = 146;
                    } else if (options.length == 4) {
                      maxHeight = 193;
                    }
                    maxHeight = maxHeight.clamp(0, 200);

                    return Align(
                      alignment: Alignment.topLeft,
                      child: Container(
                          decoration: BoxDecoration(
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.3),
                                blurRadius: 5,
                                spreadRadius: 1,
                              ),
                            ],
                          ),
                          child: ClipRRect(
                              borderRadius: BorderRadius.circular(5),
                              child: Material(
                                elevation: 4,
                                child: ConstrainedBox(
                                  constraints: BoxConstraints(
                                    maxHeight: maxHeight,
                                    maxWidth: 319,
                                  ),
                                  child: _allPeersLoader.peers.isEmpty &&
                                          !_allPeersLoader.isPeersLoaded
                                      ? Container(
                                          height: 80,
                                          child: Center(
                                            child: CircularProgressIndicator(
                                              strokeWidth: 2,
                                            ),
                                          ))
                                      : Padding(
                                          padding:
                                              const EdgeInsets.only(top: 5),
                                          child: ListView(
                                            children: options
                                                .map((peer) =>
                                                    AutocompletePeerTile(
                                                        onSelect: () =>
                                                            onSelected(peer),
                                                        peer: peer))
                                                .toList(),
                                          ),
                                        ),
                                ),
                              ))),
                    );
                  },
                )),
              ],
            ),
            Padding(
              padding: const EdgeInsets.only(top: 13.0),
              child: Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                SizedBox(
                  height: 28.0,
                  child: ElevatedButton(
                    onPressed: () {
                      onConnect();
                    },
                    child: Text(translate("Connect")),
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  height: 28.0,
                  width: 28.0,
                  decoration: BoxDecoration(
                    border: Border.all(color: Theme.of(context).dividerColor),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Center(
                    child: StatefulBuilder(
                      builder: (context, setState) {
                        var offset = Offset(0, 0);
                        return Obx(() => InkWell(
                              child: _menuOpen.value
                                  ? Transform.rotate(
                                      angle: pi,
                                      child: Icon(IconFont.more, size: 14),
                                    )
                                  : Icon(IconFont.more, size: 14),
                              onTapDown: (e) {
                                offset = e.globalPosition;
                              },
                              onTap: () async {
                                _menuOpen.value = true;
                                final x = offset.dx;
                                final y = offset.dy;
                                await mod_menu
                                    .showMenu(
                                  context: context,
                                  position: RelativeRect.fromLTRB(x, y, x, y),
                                  items: [
                                    (
                                      'Transfer file',
                                      () => onConnect(isFileTransfer: true)
                                    ),
                                    (
                                      'View camera',
                                      () => onConnect(isViewCamera: true)
                                    ),
                                    (
                                      '${translate('Terminal')} (beta)',
                                      () => onConnect(isTerminal: true)
                                    ),
                                  ]
                                      .map((e) => MenuEntryButton<String>(
                                            childBuilder: (TextStyle? style) =>
                                                Text(
                                              translate(e.$1),
                                              style: style,
                                            ),
                                            proc: () => e.$2(),
                                            padding: EdgeInsets.symmetric(
                                                horizontal:
                                                    kDesktopMenuPadding.left),
                                            dismissOnClicked: true,
                                          ))
                                      .map((e) => e.build(
                                          context,
                                          const MenuConfig(
                                              commonColor: CustomPopupMenuTheme
                                                  .commonColor,
                                              height:
                                                  CustomPopupMenuTheme.height,
                                              dividerHeight:
                                                  CustomPopupMenuTheme
                                                      .dividerHeight)))
                                      .expand((i) => i)
                                      .toList(),
                                  elevation: 8,
                                )
                                    .then((_) {
                                  _menuOpen.value = false;
                                });
                              },
                            ));
                      },
                    ),
                  ),
                ),
              ]),
            ),
          ],
        ),
      ),
    );
    return Container(
        constraints: const BoxConstraints(maxWidth: 600), child: w);
  }
}
