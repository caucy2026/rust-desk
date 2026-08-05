import 'package:flutter/material.dart';
import 'package:flutter_hbb/models/connection_history_model.dart';

class ConnectionHistoryView extends StatelessWidget {
  const ConnectionHistoryView({super.key});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: connectionHistoryModel,
      builder: (context, _) {
        final entries = connectionHistoryModel.entries;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Text('连接记录（${entries.length}）',
                    style: Theme.of(context).textTheme.titleSmall),
                const Spacer(),
                TextButton.icon(
                  onPressed:
                      entries.isEmpty ? null : () => _confirmClear(context),
                  icon: const Icon(Icons.delete_sweep_outlined, size: 18),
                  label: const Text('清空记录'),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Expanded(
              child: entries.isEmpty
                  ? const Center(
                      child: Text(
                        '暂无连接记录\n发起远程桌面连接后，会在这里保存时间和连接结果。',
                        textAlign: TextAlign.center,
                      ),
                    )
                  : ListView.separated(
                      itemCount: entries.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, index) =>
                          _HistoryTile(entry: entries[index]),
                    ),
            ),
          ],
        );
      },
    );
  }

  Future<void> _confirmClear(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('清空连接记录'),
        content: const Text('确定删除全部连接记录吗？删除后无法恢复。'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('清空')),
        ],
      ),
    );
    if (confirmed == true) await connectionHistoryModel.clear();
  }
}

class _HistoryTile extends StatelessWidget {
  const _HistoryTile({required this.entry});

  final ConnectionHistoryEntry entry;

  @override
  Widget build(BuildContext context) {
    final color = _statusColor(context);
    final title = entry.peerName.isEmpty ? entry.peerId : entry.peerName;
    return ListTile(
      dense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      leading: CircleAvatar(
        radius: 18,
        backgroundColor: color.withOpacity(0.12),
        child: Icon(_statusIcon(), size: 19, color: color),
      ),
      title: Row(
        children: [
          Flexible(
            child: Text(title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600)),
          ),
          if (title != entry.peerId) ...[
            const SizedBox(width: 7),
            Text(entry.peerId, style: Theme.of(context).textTheme.bodySmall),
          ],
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
            decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(_resultText(),
                style: TextStyle(fontSize: 11, color: color)),
          ),
        ],
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('${_formatTime(entry.startedAtMs)}  ·  ${_durationText()}'),
          if (entry.error.isNotEmpty)
            Text(entry.error,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Colors.redAccent, fontSize: 12)),
        ],
      ),
      trailing: IconButton(
        tooltip: '删除此记录',
        icon: const Icon(Icons.delete_outline, size: 19),
        onPressed: () => connectionHistoryModel.delete(entry.recordId),
      ),
    );
  }

  String _resultText() {
    switch (entry.status) {
      case ConnectionHistoryStatus.connecting:
        return '连接中';
      case ConnectionHistoryStatus.failed:
        return '连接失败';
      case ConnectionHistoryStatus.interrupted:
        return '异常中断';
      case ConnectionHistoryStatus.connected:
      case ConnectionHistoryStatus.ended:
        final route = entry.direct == true
            ? 'P2P'
            : entry.direct == false
                ? '中继'
                : '已连接';
        return entry.streamType.isEmpty
            ? route
            : '$route · ${entry.streamType}';
    }
  }

  Color _statusColor(BuildContext context) {
    switch (entry.status) {
      case ConnectionHistoryStatus.connected:
        return Colors.green;
      case ConnectionHistoryStatus.ended:
        return Colors.blueGrey;
      case ConnectionHistoryStatus.connecting:
        return Colors.orange;
      case ConnectionHistoryStatus.failed:
      case ConnectionHistoryStatus.interrupted:
        return Colors.redAccent;
    }
  }

  IconData _statusIcon() {
    switch (entry.status) {
      case ConnectionHistoryStatus.connected:
        return Icons.link;
      case ConnectionHistoryStatus.ended:
        return Icons.check;
      case ConnectionHistoryStatus.connecting:
        return Icons.sync;
      case ConnectionHistoryStatus.failed:
        return Icons.link_off;
      case ConnectionHistoryStatus.interrupted:
        return Icons.warning_amber_rounded;
    }
  }

  String _durationText() {
    final end = entry.endedAtMs ?? DateTime.now().millisecondsSinceEpoch;
    final start = entry.connectedAtMs ?? entry.startedAtMs;
    final seconds = ((end - start) / 1000).floor().clamp(0, 1 << 31);
    if (entry.status == ConnectionHistoryStatus.connecting) return '正在建立连接';
    if (entry.status == ConnectionHistoryStatus.failed) return '未建立画面';
    if (seconds < 60) return '会话 $seconds秒';
    final minutes = seconds ~/ 60;
    if (minutes < 60) return '会话 $minutes分钟';
    return '会话 ${minutes ~/ 60}小时${minutes % 60}分钟';
  }

  String _formatTime(int milliseconds) {
    final time = DateTime.fromMillisecondsSinceEpoch(milliseconds);
    String two(int value) => value.toString().padLeft(2, '0');
    return '${time.year}-${two(time.month)}-${two(time.day)} '
        '${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
  }
}
