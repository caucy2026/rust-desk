import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_hbb/common.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:flutter_svg/flutter_svg.dart';

import 'home_page.dart';

class ClientDownloadPage extends StatefulWidget implements PageShape {
  ClientDownloadPage({Key? key}) : super(key: key);

  @override
  final String title = '客户端';

  @override
  final Widget icon = const Icon(Icons.devices_outlined);

  @override
  final List<Widget> appBarActions = const [];

  @override
  State<ClientDownloadPage> createState() => _ClientDownloadPageState();
}

class _ClientDownloadPageState extends State<ClientDownloadPage>
    with WidgetsBindingObserver {
  Map<dynamic, dynamic>? _status;
  String? _error;
  var _starting = true;
  var _refreshWifiNameOnResume = false;
  Timer? _statusTimer;
  Timer? _cloudRefreshTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startServer();
  }

  @override
  void dispose() {
    _statusTimer?.cancel();
    _cloudRefreshTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    unawaited(gFFI.invokeMethod('client_distribution_stop'));
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _refreshWifiNameOnResume) {
      _refreshWifiNameOnResume = false;
      unawaited(_startServer());
    }
  }

  Future<void> _startServer() async {
    setState(() {
      _starting = true;
      _error = null;
    });
    final result = await gFFI.invokeMethod('client_distribution_start');
    if (!mounted) return;
    if (result is Map && result['running'] == true) {
      setState(() {
        _status = result;
        _starting = false;
      });
      _statusTimer ??= Timer.periodic(
        const Duration(seconds: 1),
        (_) => _refreshStatus(),
      );
      _cloudRefreshTimer ??= Timer.periodic(
        const Duration(minutes: 5),
        (_) => unawaited(gFFI.invokeMethod('client_distribution_refresh')),
      );
    } else {
      setState(() {
        _status = null;
        _starting = false;
        _error = result is Map ? result['error']?.toString() : null;
      });
    }
  }

  Future<void> _refreshStatus() async {
    final result = await gFFI.invokeMethod('client_distribution_status');
    if (!mounted || result is! Map || result['running'] != true) return;
    setState(() => _status = result);
  }

  Future<void> _downloadPackage(Map item) async {
    final id = item['id']?.toString();
    if (id == null || id.isEmpty || item['available'] == true) return;
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder:
          (_) => _DownloadProgressDialog(
            id: id,
            platform: item['platform']?.toString() ?? '客户端',
          ),
    );
    await _refreshStatus();
  }

  Future<void> _copyUrl(String url) async {
    await Clipboard.setData(ClipboardData(text: url));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('网址已复制')));
  }

  Future<void> _requestWifiNamePermission() async {
    final granted = await gFFI.invokeMethod(
      'client_distribution_request_wifi_name_permission',
    );
    if (!mounted) return;
    if (granted == true) {
      await _startServer();
    } else {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('未授权位置权限，无法读取当前 Wi‑Fi 名称。')));
    }
  }

  Future<void> _openLocationSettings() async {
    _refreshWifiNameOnResume = true;
    final opened = await gFFI.invokeMethod(
      'client_distribution_open_location_settings',
    );
    if (!mounted || opened == true) return;
    _refreshWifiNameOnResume = false;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('无法打开系统定位设置。')));
  }

  @override
  Widget build(BuildContext context) {
    if (_starting) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_status == null) {
      return _ErrorView(error: _error, onRetry: _startServer);
    }
    final url = _status!['url']?.toString() ?? '';
    final wifiNameValue = _status!['wifiName'];
    final wifiName = wifiNameValue is String ? wifiNameValue.trim() : '';
    final wifiNamePermissionGranted =
        _status!['wifiNamePermissionGranted'] == true;
    final packages = (_status!['packages'] as List? ?? const []);
    final addresses = (_status!['addresses'] as List? ?? const []);
    final metadata =
        _status!['metadata'] is Map
            ? _status!['metadata'] as Map
            : const <dynamic, dynamic>{};
    final metadataSource = metadata['source']?.toString() ?? 'cached';
    final metadataMessage = metadata['message']?.toString() ?? '';
    return ListView(
      padding: const EdgeInsets.only(bottom: 20),
      children: [
        Card(
          margin: const EdgeInsets.fromLTRB(12, 10, 12, 0),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(13),
          ),
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.wifi, color: Colors.green),
                    const SizedBox(width: 10),
                    Text(
                      '客户端下载服务已开启',
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                LayoutBuilder(
                  builder: (context, constraints) {
                    final detail = Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('请让 PAD 与下载设备连接同一个 Wi‑Fi。'),
                        if (wifiName.isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Text(
                            '当前 Wi‑Fi：$wifiName',
                            style: Theme.of(
                              context,
                            ).textTheme.titleMedium?.copyWith(
                              color: Theme.of(context).colorScheme.primary,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ] else if (!wifiNamePermissionGranted) ...[
                          const SizedBox(height: 3),
                          TextButton.icon(
                            onPressed: _requestWifiNamePermission,
                            icon: const Icon(Icons.location_on_outlined),
                            label: const Text('授权显示当前 Wi‑Fi 名称'),
                          ),
                          const Text('仅用于读取当前 Wi‑Fi 名称，不会获取或上传位置。'),
                        ] else ...[
                          const SizedBox(height: 4),
                          const Text('无法读取当前 Wi‑Fi 名称，请确认系统定位服务已开启。'),
                          TextButton.icon(
                            onPressed: _openLocationSettings,
                            icon: const Icon(Icons.settings_outlined),
                            label: const Text('打开定位设置'),
                          ),
                        ],
                        const SizedBox(height: 18),
                        const Text('在下载设备浏览器中：输入下方网址，或扫描右侧二维码（二选一）。'),
                        const SizedBox(height: 7),
                        _AddressBox(url: url, onCopy: () => _copyUrl(url)),
                      ],
                    );
                    final qr =
                        url.isEmpty
                            ? const SizedBox.shrink()
                            : Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                QrImageView(
                                  data: url,
                                  size: 152,
                                  backgroundColor: Colors.white,
                                ),
                                const SizedBox(height: 6),
                                const Text('扫码打开（与输入网址二选一）'),
                              ],
                            );
                    if (constraints.maxWidth < 700 || url.isEmpty) {
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          detail,
                          if (url.isNotEmpty) ...[
                            const SizedBox(height: 18),
                            Center(child: qr),
                          ],
                        ],
                      );
                    }
                    return Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: constraints.maxWidth * .5,
                          child: detail,
                        ),
                        const Spacer(),
                        qr,
                      ],
                    );
                  },
                ),
                if (addresses.length > 1)
                  ExpansionTile(
                    title: const Text('其他可用地址'),
                    children:
                        addresses
                            .map(
                              (address) => ListTile(
                                dense: true,
                                title: Text(address.toString()),
                                trailing: IconButton(
                                  icon: const Icon(Icons.copy_outlined),
                                  onPressed: () => _copyUrl(address.toString()),
                                ),
                              ),
                            )
                            .toList(),
                  ),
              ],
            ),
          ),
        ),
        Card(
          margin: const EdgeInsets.fromLTRB(12, 10, 12, 0),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(13),
          ),
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '可下载客户端',
                  style: Theme.of(
                    context,
                  ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 5),
                const Text('打开网址后，点击对应设备的下载按钮。'),
                const SizedBox(height: 5),
                Text(
                  metadataSource == 'newlink_https'
                      ? '已解析 Newlink HTTPS 最新地址；PAD 校验成功后才对外提供。'
                      : metadataSource == 'github_fallback'
                      ? '云盘暂不可用，当前使用 GitHub 备用源。'
                      : metadataMessage.isNotEmpty
                      ? metadataMessage
                      : '正在解析云端实时地址……',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color:
                        metadataSource == 'newlink_https'
                            ? Colors.green.shade700
                            : Theme.of(context).colorScheme.secondary,
                  ),
                ),
                const SizedBox(height: 12),
                if (packages.isEmpty)
                  const Text('当前没有可分发的安装包。')
                else
                  ...packages.map(
                    (item) => _PackageRow(
                      item: item is Map ? item : const <String, dynamic>{},
                      onTap:
                          () => _downloadPackage(
                            item is Map ? item : const <String, dynamic>{},
                          ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        const Padding(
          padding: EdgeInsets.fromLTRB(24, 15, 24, 0),
          child: Text(
            '离开本页面后，客户端下载服务会自动关闭。',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.grey),
          ),
        ),
      ],
    );
  }
}

class _AddressBox extends StatelessWidget {
  const _AddressBox({required this.url, required this.onCopy});

  final String url;
  final VoidCallback onCopy;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(9),
    ),
    child: Row(
      children: [
        Expanded(
          child: SelectableText(
            url,
            style: const TextStyle(fontFamily: 'monospace', fontSize: 16),
          ),
        ),
        IconButton(
          tooltip: '复制网址',
          icon: const Icon(Icons.copy_outlined),
          onPressed: onCopy,
        ),
      ],
    ),
  );
}

class _PackageRow extends StatelessWidget {
  const _PackageRow({required this.item, required this.onTap});

  final Map item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final platform = item['platform']?.toString() ?? '客户端';
    final detail = item['detail']?.toString() ?? '';
    final version = item['version']?.toString() ?? '';
    final available = item['available'] == true;
    final state = item['state']?.toString() ?? '';
    final downloaded = (item['downloaded'] as num?)?.toDouble() ?? 0;
    final total = (item['total'] as num?)?.toDouble() ?? 0;
    final downloading = state == 'downloading' || state == 'verifying';
    final progress = total > 0 ? (downloaded / total).clamp(0.0, 1.0) : null;
    return ListTile(
      onTap: available ? null : onTap,
      contentPadding: EdgeInsets.zero,
      leading: _platformIcon(context, platform, available),
      title: Text(platform),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text([detail, if (version.isNotEmpty) '版本 $version'].join(' · ')),
        ],
      ),
      trailing:
          available
              ? const Icon(Icons.check_circle_outline, color: Colors.green)
              : downloading
              ? _CircularDownloadProgress(
                value: state == 'verifying' ? null : progress,
                label:
                    state == 'verifying'
                        ? '校验'
                        : '${((progress ?? 0) * 100).floor()}%',
              )
              : const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('点击下载'),
                  SizedBox(width: 3),
                  Icon(Icons.download_outlined),
                ],
              ),
    );
  }

  Widget _platformIcon(BuildContext context, String platform, bool available) {
    final color =
        available
            ? Theme.of(context).colorScheme.primary
            : Theme.of(context).disabledColor;
    final normalized = platform.toLowerCase();
    if (normalized.contains('macos')) {
      return Icon(Icons.apple, size: 28, color: color);
    }
    final String? asset =
        normalized.contains('android') || normalized.contains('pad')
            ? 'assets/android.svg'
            : normalized.contains('linux')
            ? 'assets/linux.svg'
            : null;
    if (asset != null) {
      return SvgPicture.asset(
        asset,
        width: 28,
        height: 28,
        colorFilter: ColorFilter.mode(color, BlendMode.srcIn),
      );
    }
    return Icon(Icons.window_rounded, size: 28, color: color);
  }
}

class _CircularDownloadProgress extends StatelessWidget {
  const _CircularDownloadProgress({required this.value, required this.label});

  final double? value;
  final String label;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 42,
    height: 42,
    child: Stack(
      alignment: Alignment.center,
      children: [
        SizedBox(
          width: 38,
          height: 38,
          child: CircularProgressIndicator(value: value, strokeWidth: 3),
        ),
        Text(
          label,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
            fontSize: label.length > 3 ? 9 : 10,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    ),
  );
}

class _DownloadProgressDialog extends StatefulWidget {
  const _DownloadProgressDialog({required this.id, required this.platform});

  final String id;
  final String platform;

  @override
  State<_DownloadProgressDialog> createState() =>
      _DownloadProgressDialogState();
}

class _DownloadProgressDialogState extends State<_DownloadProgressDialog> {
  Timer? _timer;
  Map<dynamic, dynamic> _item = const {};
  String? _error;
  var _done = false;

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _start() async {
    setState(() {
      _error = null;
      _done = false;
    });
    final accepted = await gFFI.invokeMethod(
      'client_distribution_download',
      widget.id,
    );
    if (!mounted) return;
    if (accepted != true) {
      setState(() => _error = '无法开始下载，请稍后重试。');
      return;
    }
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(milliseconds: 700), (_) => _poll());
    await _poll();
  }

  Future<void> _poll() async {
    final status = await gFFI.invokeMethod('client_distribution_status');
    if (!mounted || status is! Map) return;
    final packages = status['packages'] as List? ?? const [];
    final item = packages.cast<dynamic>().firstWhere(
      (item) => item is Map && item['id'] == widget.id,
      orElse: () => const <String, dynamic>{},
    );
    if (item is! Map) return;
    setState(() {
      _item = item;
      _error = item['state'] == 'error' ? item['message']?.toString() : null;
      _done = item['available'] == true;
    });
    if (_done) {
      _timer?.cancel();
      await Future<void>.delayed(const Duration(milliseconds: 800));
      if (mounted) Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final downloaded = (_item['downloaded'] as num?)?.toDouble() ?? 0;
    final total = (_item['total'] as num?)?.toDouble() ?? 0;
    final state = _item['state']?.toString() ?? '';
    final progress = total > 0 ? (downloaded / total).clamp(0.0, 1.0) : null;
    final percent =
        progress == null ? '' : ' ${(progress * 100).toStringAsFixed(0)}%';
    return AlertDialog(
      title: Text('准备 ${widget.platform} 客户端'),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 220),
              child:
                  _done
                      ? const Icon(
                        Icons.check_circle,
                        key: ValueKey('done'),
                        color: Colors.green,
                        size: 54,
                      )
                      : SizedBox(
                        key: const ValueKey('progress'),
                        width: 54,
                        height: 54,
                        child: CircularProgressIndicator(
                          value: state == 'verifying' ? null : progress,
                        ),
                      ),
            ),
            const SizedBox(height: 16),
            Text(
              _done
                  ? '下载并校验完成'
                  : state == 'verifying'
                  ? '正在校验文件…'
                  : '正在从 GitHub 下载$percent',
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(
                _error!,
                textAlign: TextAlign.center,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
          ],
        ),
      ),
      actions: [
        if (_error != null)
          TextButton(onPressed: _start, child: const Text('重试')),
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(_error == null ? '后台下载' : '关闭'),
        ),
      ],
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.error, required this.onRetry});

  final String? error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.wifi_off_outlined, size: 46),
          const SizedBox(height: 14),
          const Text('无法启动客户端下载服务'),
          if (error != null) ...[
            const SizedBox(height: 8),
            Text(error!, textAlign: TextAlign.center),
          ],
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('重试'),
          ),
        ],
      ),
    ),
  );
}
