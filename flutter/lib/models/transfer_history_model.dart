import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:get/get.dart';

import 'file_model.dart';
import 'platform_model.dart';

const _kTransferHistoryOption = 'kemi-transfer-history-v1';
const _kReceivedLocalFilesOption = 'kemi-received-local-files-v1';

/// Persistent allow-list for entries that were successfully downloaded to this
/// device. It is intentionally independent from the visible per-peer history:
/// deleting a history row must never change the real-entry deletion boundary.
class ReceivedLocalFileRegistry {
  final void Function(String value)? persistOverride;
  final records = <String, ReceivedLocalFileRecord>{}.obs;

  ReceivedLocalFileRegistry({this.persistOverride});

  void load() {
    records.clear();
    final raw = bind.mainGetLocalOption(key: _kReceivedLocalFilesOption);
    if (raw.trim().isEmpty) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return;
      for (final value in decoded) {
        if (value is! Map) continue;
        final record = ReceivedLocalFileRecord.fromJson(
          Map<String, dynamic>.from(value),
        );
        if (record.path.isNotEmpty) {
          records[_pathKey(record.path, false)] = record;
        }
      }
    } catch (_) {
      // A damaged option must fail closed: no local file becomes deletable.
      records.clear();
    }
  }

  bool canDelete(Entry entry) {
    if (entry.isDrive || entry.path.isEmpty) return false;
    final record = records[_pathKey(entry.path, false)];
    if (record == null || record.isDirectory != entry.isDirectory) return false;
    // A received directory is owned as one transfer root. Its mtime naturally
    // changes while children arrive, so provenance (path + directory type),
    // not a volatile timestamp, is the recursive-delete boundary.
    if (entry.isDirectory) return true;
    if (entry.isFile && record.size != entry.size) return false;
    return record.modifiedTime <= 0 ||
        entry.modifiedTime <= 0 ||
        record.modifiedTime == entry.modifiedTime;
  }

  void rememberCompletedEntry({
    required String path,
    required int size,
    required int modifiedTime,
    required bool isDirectory,
  }) {
    if (path.isEmpty) return;
    records[_pathKey(path, false)] = ReceivedLocalFileRecord(
      path: path,
      size: size,
      modifiedTime: modifiedTime,
      isDirectory: isDirectory,
      completedAt: DateTime.now().millisecondsSinceEpoch,
    );
    records.refresh();
    _save();
  }

  void forget(String path) {
    if (records.remove(_pathKey(path, false)) == null) return;
    records.refresh();
    _save();
  }

  void forgetTree(String path) {
    final key = _pathKey(path, false);
    final childPrefix = key.endsWith('/') ? key : '$key/';
    final removed = records.keys
        .where((candidate) =>
            candidate == key || candidate.startsWith(childPrefix))
        .toSet();
    if (removed.isEmpty) return;
    records.removeWhere((candidate, _) => removed.contains(candidate));
    records.refresh();
    _save();
  }

  void _save() {
    final value = jsonEncode(
      records.values.map((record) => record.toJson()).toList(),
    );
    if (persistOverride != null) {
      persistOverride!(value);
      return;
    }
    bind.mainSetLocalOption(
      key: _kReceivedLocalFilesOption,
      value: value,
    );
  }
}

class ReceivedLocalFileRecord {
  final String path;
  final int size;
  final int modifiedTime;
  final bool isDirectory;
  final int completedAt;

  const ReceivedLocalFileRecord({
    required this.path,
    required this.size,
    required this.modifiedTime,
    this.isDirectory = false,
    required this.completedAt,
  });

  factory ReceivedLocalFileRecord.fromJson(Map<String, dynamic> json) =>
      ReceivedLocalFileRecord(
        path: json['path']?.toString() ?? '',
        size: (json['size'] as num?)?.toInt() ?? -1,
        modifiedTime: (json['modified_time'] as num?)?.toInt() ?? 0,
        isDirectory: json['is_directory'] == true,
        completedAt: (json['completed_at'] as num?)?.toInt() ?? 0,
      );

  Map<String, dynamic> toJson() => {
        'path': path,
        'size': size,
        'modified_time': modifiedTime,
        'is_directory': isDirectory,
        'completed_at': completedAt,
      };
}

class TransferHistoryBinding {
  final String groupKey;
  final String itemKey;

  const TransferHistoryBinding(this.groupKey, this.itemKey);
}

class TransferHistoryStore {
  final String peerId;
  final ReceivedLocalFileRegistry? receivedLocalFiles;
  final groups = <TransferHistoryGroup>[].obs;
  final Map<int, TransferHistoryBinding> _jobBindings = {};

  TransferHistoryStore(this.peerId, {this.receivedLocalFiles});

  void load() {
    groups.clear();
    final raw = bind.mainGetPeerFlutterOptionSync(
      id: peerId,
      k: _kTransferHistoryOption,
    );
    if (raw.trim().isEmpty) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return;
      for (final value in decoded) {
        if (value is! Map) continue;
        final group = TransferHistoryGroup.fromJson(
          Map<String, dynamic>.from(value),
        );
        for (final item in group.items) {
          if (item.lastStatus == TransferHistoryStatus.inProgress) {
            item
              ..lastStatus = TransferHistoryStatus.error
              ..lastError = '上次传输未完成，可点击再次传输';
          }
        }
        if (group.items.isNotEmpty) groups.add(group);
      }
      _sortHistory();
    } catch (_) {
      // Corrupt local history must not block the live file-transfer page.
      groups.clear();
    }
  }

  TransferHistoryBinding registerTransfer({
    required Entry entry,
    required bool isRemoteToLocal,
    required String targetDir,
    required bool sourceIsWindows,
    required bool targetIsWindows,
    required bool showHidden,
  }) {
    final sourceDir = PathUtil.dirname(entry.path, sourceIsWindows);
    final groupKey = _groupKey(
      isRemoteToLocal,
      sourceDir,
      targetDir,
      sourceIsWindows,
      targetIsWindows,
    );
    var group = groups.firstWhereOrNull((value) => value.key == groupKey);
    if (group == null) {
      group = TransferHistoryGroup(
        isRemoteToLocal: isRemoteToLocal,
        sourceDir: sourceDir,
        targetDir: targetDir,
        sourceIsWindows: sourceIsWindows,
        targetIsWindows: targetIsWindows,
        showHidden: showHidden,
      );
      groups.insert(0, group);
    }

    final itemKey = _pathKey(entry.path, sourceIsWindows);
    var item = group.items.firstWhereOrNull(
      (value) => _pathKey(value.sourcePath, sourceIsWindows) == itemKey,
    );
    if (item == null) {
      item = TransferHistoryItem(
        name: entry.name,
        sourcePath: entry.path,
        entryType: entry.entryType,
        size: entry.size,
        modifiedTime: entry.modifiedTime,
      );
      group.items.insert(0, item);
    } else {
      item
        ..name = entry.name
        ..entryType = entry.entryType
        ..size = entry.size
        ..modifiedTime = entry.modifiedTime;
    }

    final now = DateTime.now().millisecondsSinceEpoch;
    item
      ..transferCount += 1
      ..lastTransferredAt = now
      ..lastStatus = TransferHistoryStatus.inProgress
      ..lastError = '';
    group
      ..lastUsedAt = now
      ..showHidden = showHidden;
    _sortHistory();
    _save();
    debugPrint('[TransferHistory] registered peer=$peerId '
        'direction=${isRemoteToLocal ? 'download' : 'upload'} '
        'source=${entry.path} target=$targetDir');
    return TransferHistoryBinding(groupKey, itemKey);
  }

  void bindJob(int jobId, TransferHistoryBinding binding) {
    _jobBindings[jobId] = binding;
  }

  void updateFromJob(JobProgress job) {
    final binding = _jobBindings[job.id];
    if (binding == null || job.type != JobType.transfer) return;
    final item = findItem(binding);
    if (item == null) {
      _jobBindings.remove(job.id);
      return;
    }
    switch (job.state) {
      case JobState.done:
        item
          ..lastStatus = TransferHistoryStatus.done
          ..lastError = '';
        final group = groups.firstWhereOrNull(
          (value) => value.key == binding.groupKey,
        );
        if (job.err.isEmpty && group?.isRemoteToLocal == true) {
          receivedLocalFiles?.rememberCompletedEntry(
            path: PathUtil.join(
              group!.targetDir,
              item.name,
              group.targetIsWindows,
            ),
            size: item.size,
            modifiedTime: item.modifiedTime,
            isDirectory: item.isDirectory,
          );
        }
        _jobBindings.remove(job.id);
        break;
      case JobState.error:
        item
          ..lastStatus = TransferHistoryStatus.error
          ..lastError = job.err;
        _jobBindings.remove(job.id);
        break;
      case JobState.inProgress:
        item.lastStatus = TransferHistoryStatus.inProgress;
        break;
      default:
        return;
    }
    groups.refresh();
    _save();
  }

  void setValidationError(
    TransferHistoryGroup group,
    TransferHistoryItem item,
    String error,
  ) {
    item
      ..lastStatus = TransferHistoryStatus.error
      ..lastError = error;
    group.lastUsedAt = DateTime.now().millisecondsSinceEpoch;
    groups.refresh();
    _save();
  }

  TransferHistoryItem? findItem(TransferHistoryBinding binding) {
    final group = groups.firstWhereOrNull(
      (value) => value.key == binding.groupKey,
    );
    return group?.items.firstWhereOrNull(
      (value) =>
          _pathKey(value.sourcePath, group.sourceIsWindows) == binding.itemKey,
    );
  }

  void removeItem(TransferHistoryGroup group, TransferHistoryItem item) {
    final itemKey = _pathKey(item.sourcePath, group.sourceIsWindows);
    group.items.removeWhere(
      (value) => _pathKey(value.sourcePath, group.sourceIsWindows) == itemKey,
    );
    if (group.items.isEmpty) {
      groups.removeWhere((value) => value.key == group.key);
    }
    groups.refresh();
    _save();
  }

  void clear() {
    groups.clear();
    _jobBindings.clear();
    _save();
  }

  void _sortHistory() {
    groups.sort((a, b) => b.lastUsedAt.compareTo(a.lastUsedAt));
    for (final group in groups) {
      group.items.sort(
        (a, b) => b.lastTransferredAt.compareTo(a.lastTransferredAt),
      );
    }
    groups.refresh();
  }

  void _save() {
    bind.mainSetPeerFlutterOptionSync(
      id: peerId,
      k: _kTransferHistoryOption,
      v: jsonEncode(groups.map((value) => value.toJson()).toList()),
    );
  }
}

class TransferHistoryGroup {
  bool isRemoteToLocal;
  String sourceDir;
  String targetDir;
  bool sourceIsWindows;
  bool targetIsWindows;
  bool showHidden;
  int lastUsedAt;
  final List<TransferHistoryItem> items;

  TransferHistoryGroup({
    required this.isRemoteToLocal,
    required this.sourceDir,
    required this.targetDir,
    required this.sourceIsWindows,
    required this.targetIsWindows,
    required this.showHidden,
    this.lastUsedAt = 0,
    List<TransferHistoryItem>? items,
  }) : items = items ?? <TransferHistoryItem>[];

  String get key => _groupKey(
        isRemoteToLocal,
        sourceDir,
        targetDir,
        sourceIsWindows,
        targetIsWindows,
      );

  factory TransferHistoryGroup.fromJson(Map<String, dynamic> json) {
    return TransferHistoryGroup(
      isRemoteToLocal: json['is_remote_to_local'] == true,
      sourceDir: json['source_dir']?.toString() ?? '',
      targetDir: json['target_dir']?.toString() ?? '',
      sourceIsWindows: json['source_is_windows'] == true,
      targetIsWindows: json['target_is_windows'] == true,
      showHidden: json['show_hidden'] == true,
      lastUsedAt: (json['last_used_at'] as num?)?.toInt() ?? 0,
      items: ((json['items'] as List?) ?? const [])
          .whereType<Map>()
          .map(
            (value) => TransferHistoryItem.fromJson(
              Map<String, dynamic>.from(value),
            ),
          )
          .where((value) => value.sourcePath.isNotEmpty)
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
        'is_remote_to_local': isRemoteToLocal,
        'source_dir': sourceDir,
        'target_dir': targetDir,
        'source_is_windows': sourceIsWindows,
        'target_is_windows': targetIsWindows,
        'show_hidden': showHidden,
        'last_used_at': lastUsedAt,
        'items': items.map((value) => value.toJson()).toList(),
      };
}

class TransferHistoryItem {
  String name;
  String sourcePath;
  int entryType;
  int size;
  int modifiedTime;
  int transferCount;
  int lastTransferredAt;
  String lastStatus;
  String lastError;

  TransferHistoryItem({
    required this.name,
    required this.sourcePath,
    required this.entryType,
    required this.size,
    required this.modifiedTime,
    this.transferCount = 0,
    this.lastTransferredAt = 0,
    this.lastStatus = TransferHistoryStatus.inProgress,
    this.lastError = '',
  });

  bool get isDirectory => entryType < 3;

  Entry toEntry() => Entry()
    ..name = name
    ..path = sourcePath
    ..entryType = entryType
    ..size = size
    ..modifiedTime = modifiedTime;

  factory TransferHistoryItem.fromJson(Map<String, dynamic> json) {
    return TransferHistoryItem(
      name: json['name']?.toString() ?? '',
      sourcePath: json['source_path']?.toString() ?? '',
      entryType: (json['entry_type'] as num?)?.toInt() ?? 4,
      size: (json['size'] as num?)?.toInt() ?? 0,
      modifiedTime: (json['modified_time'] as num?)?.toInt() ?? 0,
      transferCount: (json['transfer_count'] as num?)?.toInt() ?? 0,
      lastTransferredAt: (json['last_transferred_at'] as num?)?.toInt() ?? 0,
      lastStatus: json['last_status']?.toString() ?? TransferHistoryStatus.done,
      lastError: json['last_error']?.toString() ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
        'name': name,
        'source_path': sourcePath,
        'entry_type': entryType,
        'size': size,
        'modified_time': modifiedTime,
        'transfer_count': transferCount,
        'last_transferred_at': lastTransferredAt,
        'last_status': lastStatus,
        'last_error': lastError,
      };
}

class TransferHistoryStatus {
  static const inProgress = 'in_progress';
  static const done = 'done';
  static const error = 'error';
}

String _groupKey(
  bool isRemoteToLocal,
  String sourceDir,
  String targetDir,
  bool sourceIsWindows,
  bool targetIsWindows,
) {
  return '${isRemoteToLocal ? 'download' : 'upload'}|'
      '${_pathKey(sourceDir, sourceIsWindows)}|'
      '${_pathKey(targetDir, targetIsWindows)}';
}

String _pathKey(String value, bool isWindows) {
  var path = value.trim();
  if (isWindows) {
    path = path.replaceAll('/', '\\').toLowerCase();
    while (path.length > 3 && path.endsWith('\\')) {
      path = path.substring(0, path.length - 1);
    }
  } else {
    path = path.replaceAll('\\', '/');
    while (path.length > 1 && path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
  }
  return path;
}
