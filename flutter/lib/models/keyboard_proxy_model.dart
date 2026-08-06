import 'package:flutter/foundation.dart';

enum KeyboardProxyState { hidden, opening, visible, closing }

class KeyboardProxySnapshot {
  const KeyboardProxySnapshot({
    this.requestId = 0,
    this.state = KeyboardProxyState.hidden,
    this.sourceDisplayId = 0,
    this.targetDisplayId = 0,
    this.reason = '',
  });

  final int requestId;
  final KeyboardProxyState state;
  final int sourceDisplayId;
  final int targetDisplayId;
  final String reason;

  bool get isVisible => state == KeyboardProxyState.visible;
  bool get isTransitioning =>
      state == KeyboardProxyState.opening ||
      state == KeyboardProxyState.closing;
}

class KeyboardProxyController extends ValueNotifier<KeyboardProxySnapshot> {
  KeyboardProxyController() : super(const KeyboardProxySnapshot());

  String _sessionId = '';

  bool tryBeginOpen(String sessionId) {
    if (value.state != KeyboardProxyState.hidden) return false;
    _sessionId = sessionId;
    value = KeyboardProxySnapshot(
      requestId: value.requestId,
      state: KeyboardProxyState.opening,
      sourceDisplayId: value.sourceDisplayId,
      targetDisplayId: value.targetDisplayId,
      reason: 'local_open_requested',
    );
    return true;
  }

  bool tryBeginClose() {
    if (value.state != KeyboardProxyState.visible) return false;
    value = KeyboardProxySnapshot(
      requestId: value.requestId,
      state: KeyboardProxyState.closing,
      sourceDisplayId: value.sourceDisplayId,
      targetDisplayId: value.targetDisplayId,
      reason: 'local_close_requested',
    );
    return true;
  }

  void handleState(
    Map<dynamic, dynamic> arguments, {
    String currentSessionId = '',
  }) {
    final incomingSessionId = arguments['sessionId'] as String? ?? '';
    if (incomingSessionId.isNotEmpty &&
        currentSessionId.isNotEmpty &&
        incomingSessionId != currentSessionId) {
      return;
    }
    final incomingRequestId = (arguments['requestId'] as num?)?.toInt() ?? 0;
    if (incomingRequestId < value.requestId) return;

    final stateName = arguments['state'] as String? ?? 'hidden';
    final state = KeyboardProxyState.values.firstWhere(
      (candidate) => candidate.name == stateName,
      orElse: () => KeyboardProxyState.hidden,
    );
    value = KeyboardProxySnapshot(
      requestId: incomingRequestId,
      state: state,
      sourceDisplayId: (arguments['sourceDisplayId'] as num?)?.toInt() ?? 0,
      targetDisplayId: (arguments['targetDisplayId'] as num?)?.toInt() ?? 0,
      reason: arguments['reason'] as String? ?? '',
    );
    if (state == KeyboardProxyState.hidden) {
      _sessionId = '';
    }
  }

  bool acceptsInput(Map<dynamic, dynamic> arguments, String currentSessionId) {
    final eventRequestId = (arguments['requestId'] as num?)?.toInt() ?? 0;
    final eventSessionId = arguments['sessionId'] as String? ?? '';
    return value.state == KeyboardProxyState.visible &&
        eventRequestId == value.requestId &&
        eventSessionId.isNotEmpty &&
        eventSessionId == _sessionId &&
        eventSessionId == currentSessionId;
  }

  void reset() {
    _sessionId = '';
    value = const KeyboardProxySnapshot();
  }
}

final keyboardProxyController = KeyboardProxyController();
const kLocalIdKeyboardSession = '__kemi_local_id__';
final localIdKeyboardController = KeyboardProxyController();
const kLocalPasswordKeyboardSessionPrefix = '__kemi_local_password__:';
final localPasswordKeyboardController = KeyboardProxyController();

String _localPasswordKeyboardSessionId = '';
ValueChanged<String>? _localPasswordCommitHandler;
ValueChanged<String>? _localPasswordKeyHandler;

void attachLocalPasswordKeyboard({
  required String sessionId,
  required ValueChanged<String> onCommit,
  required ValueChanged<String> onKey,
}) {
  _localPasswordKeyboardSessionId = sessionId;
  _localPasswordCommitHandler = onCommit;
  _localPasswordKeyHandler = onKey;
}

bool handleLocalPasswordKeyboardCommit(Map<dynamic, dynamic> arguments) {
  if (!localPasswordKeyboardController.acceptsInput(
      arguments, _localPasswordKeyboardSessionId)) {
    return false;
  }
  final text = arguments['text'] as String? ?? '';
  if (text.isEmpty) return false;
  _localPasswordCommitHandler?.call(text);
  return true;
}

bool handleLocalPasswordKeyboardKey(Map<dynamic, dynamic> arguments) {
  if (!localPasswordKeyboardController.acceptsInput(
      arguments, _localPasswordKeyboardSessionId)) {
    return false;
  }
  final key = arguments['key'] as String? ?? '';
  if (key.isEmpty) return false;
  _localPasswordKeyHandler?.call(key);
  return true;
}

void detachLocalPasswordKeyboard() {
  _localPasswordKeyboardSessionId = '';
  _localPasswordCommitHandler = null;
  _localPasswordKeyHandler = null;
  localPasswordKeyboardController.reset();
}
