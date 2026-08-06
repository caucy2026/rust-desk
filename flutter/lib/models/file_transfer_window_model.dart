import 'package:flutter/foundation.dart';

enum FileTransferWindowState { closed, opening, open, closing, hidden }

@immutable
class FileTransferWindowSnapshot {
  const FileTransferWindowSnapshot({
    this.state = FileTransferWindowState.closed,
    this.peerId = '',
    this.displayId = -1,
    this.reason = '',
  });

  final FileTransferWindowState state;
  final String peerId;
  final int displayId;
  final String reason;

  bool get isOpen => state == FileTransferWindowState.open;
  bool get isTransitioning =>
      state == FileTransferWindowState.opening ||
      state == FileTransferWindowState.closing;
}

class FileTransferWindowController
    extends ValueNotifier<FileTransferWindowSnapshot> {
  FileTransferWindowController() : super(const FileTransferWindowSnapshot());

  void handleNativeState(Map<dynamic, dynamic> arguments) {
    value = FileTransferWindowSnapshot(
      state: _parseState(arguments['state'] as String?),
      peerId: arguments['peerId'] as String? ?? '',
      displayId: (arguments['displayId'] as num?)?.toInt() ?? -1,
      reason: arguments['reason'] as String? ?? '',
    );
  }

  void markOpening(String peerId) {
    value = FileTransferWindowSnapshot(
      state: FileTransferWindowState.opening,
      peerId: peerId,
      displayId: value.displayId,
      reason: 'toolbar_toggle',
    );
  }

  void markClosing(String peerId) {
    value = FileTransferWindowSnapshot(
      state: FileTransferWindowState.closing,
      peerId: peerId,
      displayId: value.displayId,
      reason: 'toolbar_toggle',
    );
  }

  void reset() => value = const FileTransferWindowSnapshot();

  FileTransferWindowState _parseState(String? state) {
    switch (state) {
      case 'opening':
        return FileTransferWindowState.opening;
      case 'open':
        return FileTransferWindowState.open;
      case 'closing':
        return FileTransferWindowState.closing;
      case 'hidden':
        return FileTransferWindowState.hidden;
      default:
        return FileTransferWindowState.closed;
    }
  }
}

final fileTransferWindowController = FileTransferWindowController();
