import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_hbb/consts.dart';
import 'package:flutter_hbb/models/platform_model.dart';

enum ConnectionHistoryStatus {
  connecting,
  connected,
  ended,
  failed,
  interrupted,
}

class ConnectionHistoryEntry {
  ConnectionHistoryEntry({
    required this.recordId,
    required this.sessionId,
    required this.peerId,
    required this.startedAtMs,
    this.peerName = '',
    this.connectedAtMs,
    this.endedAtMs,
    this.status = ConnectionHistoryStatus.connecting,
    this.direct,
    this.streamType = '',
    this.error = '',
  });

  final String recordId;
  final String sessionId;
  final String peerId;
  final int startedAtMs;
  String peerName;
  int? connectedAtMs;
  int? endedAtMs;
  ConnectionHistoryStatus status;
  bool? direct;
  String streamType;
  String error;

  Map<String, dynamic> toJson() => {
        'recordId': recordId,
        'sessionId': sessionId,
        'peerId': peerId,
        'peerName': peerName,
        'startedAtMs': startedAtMs,
        'connectedAtMs': connectedAtMs,
        'endedAtMs': endedAtMs,
        'status': status.name,
        'direct': direct,
        'streamType': streamType,
        'error': error,
      };

  static ConnectionHistoryEntry? fromJson(dynamic value) {
    if (value is! Map) return null;
    try {
      final statusName = value['status']?.toString() ?? '';
      final status = ConnectionHistoryStatus.values.firstWhere(
        (item) => item.name == statusName,
        orElse: () => ConnectionHistoryStatus.interrupted,
      );
      return ConnectionHistoryEntry(
        recordId: value['recordId'].toString(),
        sessionId: value['sessionId'].toString(),
        peerId: value['peerId'].toString(),
        peerName: value['peerName']?.toString() ?? '',
        startedAtMs: value['startedAtMs'] as int,
        connectedAtMs: value['connectedAtMs'] as int?,
        endedAtMs: value['endedAtMs'] as int?,
        status: status,
        direct: value['direct'] as bool?,
        streamType: value['streamType']?.toString() ?? '',
        error: value['error']?.toString() ?? '',
      );
    } catch (_) {
      return null;
    }
  }
}

/// Local, account-independent history of outgoing remote-desktop attempts.
///
/// This deliberately excludes file transfer, camera, terminal and tunnel
/// sessions. The P2P/relay result is only meaningful after connection_ready.
class ConnectionHistoryModel extends ChangeNotifier {
  static const int _maxRecords = 200;
  final List<ConnectionHistoryEntry> _entries = [];
  final Map<String, String> _activeRecordBySession = {};
  bool _loaded = false;
  Future<void> _persistChain = Future.value();

  List<ConnectionHistoryEntry> get entries {
    _ensureLoaded();
    return List.unmodifiable(_entries);
  }

  void _ensureLoaded() {
    if (_loaded) return;
    _loaded = true;
    try {
      final raw = bind.getLocalFlutterOption(k: kOptionConnectionHistory);
      if (raw.isNotEmpty) {
        final decoded = jsonDecode(raw);
        if (decoded is List) {
          for (final value in decoded) {
            final entry = ConnectionHistoryEntry.fromJson(value);
            if (entry != null) _entries.add(entry);
          }
        }
      }
    } catch (e) {
      debugPrint('Failed to load KEMI connection history: $e');
    }
    _entries.sort((a, b) => b.startedAtMs.compareTo(a.startedAtMs));
    final now = DateTime.now().millisecondsSinceEpoch;
    var repaired = false;
    for (final entry in _entries) {
      if (entry.status == ConnectionHistoryStatus.connecting ||
          entry.status == ConnectionHistoryStatus.connected) {
        entry.status = ConnectionHistoryStatus.interrupted;
        entry.endedAtMs ??= now;
        entry.error = entry.error.isEmpty ? '应用退出前会话未正常结束' : entry.error;
        repaired = true;
      }
    }
    if (_entries.length > _maxRecords) {
      _entries.removeRange(_maxRecords, _entries.length);
      repaired = true;
    }
    if (repaired) _persist();
  }

  String begin({required String sessionId, required String peerId}) {
    _ensureLoaded();
    final now = DateTime.now().millisecondsSinceEpoch;
    final previousId = _activeRecordBySession[sessionId];
    final previous = _find(previousId);
    if (previous != null &&
        (previous.status == ConnectionHistoryStatus.connecting ||
            previous.status == ConnectionHistoryStatus.connected)) {
      previous.status = ConnectionHistoryStatus.interrupted;
      previous.endedAtMs = now;
      previous.error = '新连接开始，上一会话未收到正常结束事件';
    }
    final recordId = '$sessionId-$now';
    _entries.insert(
      0,
      ConnectionHistoryEntry(
        recordId: recordId,
        sessionId: sessionId,
        peerId: peerId,
        startedAtMs: now,
      ),
    );
    _activeRecordBySession[sessionId] = recordId;
    if (_entries.length > _maxRecords) _entries.removeLast();
    _changed();
    return recordId;
  }

  void markConnected(String sessionId, bool direct, String streamType) {
    final entry = _active(sessionId);
    if (entry == null ||
        entry.status == ConnectionHistoryStatus.ended ||
        entry.status == ConnectionHistoryStatus.failed) return;
    entry.connectedAtMs ??= DateTime.now().millisecondsSinceEpoch;
    entry.status = ConnectionHistoryStatus.connected;
    entry.direct = direct;
    entry.streamType = streamType;
    entry.error = '';
    _changed();
  }

  void updatePeerName(String sessionId, String peerName) {
    final name = peerName.trim();
    if (name.isEmpty) return;
    final entry = _active(sessionId);
    if (entry == null || entry.peerName == name) return;
    entry.peerName = name.length > 80 ? name.substring(0, 80) : name;
    _changed();
  }

  void markFailed(String sessionId, String reason) {
    final entry = _active(sessionId);
    // A later permission/message error must not rewrite a successfully
    // connected session as a connection failure.
    if (entry == null || entry.connectedAtMs != null) return;
    entry.status = ConnectionHistoryStatus.failed;
    entry.endedAtMs = DateTime.now().millisecondsSinceEpoch;
    final clean = reason.replaceAll(RegExp(r'\s+'), ' ').trim();
    entry.error = clean.length > 240 ? clean.substring(0, 240) : clean;
    _activeRecordBySession.remove(sessionId);
    _changed();
  }

  void markEnded(String sessionId) {
    final entry = _active(sessionId);
    if (entry == null) return;
    if (entry.status == ConnectionHistoryStatus.connecting) {
      entry.status = ConnectionHistoryStatus.interrupted;
      entry.error = entry.error.isEmpty ? '连接在建立完成前结束' : entry.error;
    } else if (entry.status == ConnectionHistoryStatus.connected) {
      entry.status = ConnectionHistoryStatus.ended;
    }
    entry.endedAtMs ??= DateTime.now().millisecondsSinceEpoch;
    _activeRecordBySession.remove(sessionId);
    _changed();
  }

  Future<void> delete(String recordId) async {
    _ensureLoaded();
    _entries.removeWhere((entry) => entry.recordId == recordId);
    _activeRecordBySession.removeWhere((_, value) => value == recordId);
    _changed();
  }

  Future<void> clear() async {
    _ensureLoaded();
    _entries.clear();
    _activeRecordBySession.clear();
    _changed();
  }

  ConnectionHistoryEntry? _active(String sessionId) {
    _ensureLoaded();
    return _find(_activeRecordBySession[sessionId]);
  }

  ConnectionHistoryEntry? _find(String? recordId) {
    if (recordId == null) return null;
    for (final entry in _entries) {
      if (entry.recordId == recordId) return entry;
    }
    return null;
  }

  void _changed() {
    notifyListeners();
    _persist();
  }

  void _persist() {
    final value = jsonEncode(_entries.map((entry) => entry.toJson()).toList());
    // Native writes are asynchronous. Serialize them so an older snapshot can
    // never finish after a newer one and overwrite the latest connection state.
    _persistChain = _persistChain
        .then((_) =>
            bind.setLocalFlutterOption(k: kOptionConnectionHistory, v: value))
        .catchError((e) => debugPrint('Failed to save connection history: $e'));
  }
}

final connectionHistoryModel = ConnectionHistoryModel();
