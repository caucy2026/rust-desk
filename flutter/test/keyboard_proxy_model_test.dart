import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_hbb/models/keyboard_proxy_model.dart';

void main() {
  group('KeyboardProxyController', () {
    late KeyboardProxyController controller;

    setUp(() {
      controller = KeyboardProxyController();
    });

    test('ignores state callbacks from an earlier session', () {
      expect(controller.tryBeginOpen('new-session'), isTrue);

      controller.handleState(
        {
          'requestId': 41,
          'state': 'visible',
          'sessionId': 'old-session',
        },
        currentSessionId: 'new-session',
      );

      expect(controller.value.state, KeyboardProxyState.opening);
      expect(controller.value.requestId, 0);
    });

    test('accepts current-session state and rejects older requests', () {
      expect(controller.tryBeginOpen('current-session'), isTrue);
      controller.handleState(
        {
          'requestId': 42,
          'state': 'visible',
          'sessionId': 'current-session',
        },
        currentSessionId: 'current-session',
      );

      expect(controller.value.state, KeyboardProxyState.visible);
      expect(controller.value.requestId, 42);

      controller.handleState(
        {
          'requestId': 41,
          'state': 'hidden',
          'sessionId': 'current-session',
        },
        currentSessionId: 'current-session',
      );

      expect(controller.value.state, KeyboardProxyState.visible);
      expect(controller.value.requestId, 42);
    });
  });
}
