import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/gestures.dart';

import 'package:flutter_hbb/models/platform_model.dart';
import 'package:flutter_hbb/common.dart';
import 'package:flutter_hbb/consts.dart';
import 'package:flutter_hbb/models/model.dart';
import 'package:flutter_hbb/models/input_model.dart';

import './gestures.dart';

class RawKeyFocusScope extends StatelessWidget {
  final FocusNode? focusNode;
  final ValueChanged<bool>? onFocusChange;
  final InputModel inputModel;
  final Widget child;

  RawKeyFocusScope({
    this.focusNode,
    this.onFocusChange,
    required this.inputModel,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    // https://github.com/flutter/flutter/issues/154053
    final useRawKeyEvents = isLinux && !isWeb;
    // FIXME: On Windows, `AltGr` will generate `Alt` and `Control` key events,
    // while `Alt` and `Control` are separated key events for en-US input method.
    return FocusScope(
        autofocus: true,
        child: Focus(
            autofocus: true,
            canRequestFocus: true,
            focusNode: focusNode,
            onFocusChange: onFocusChange,
            onKey: useRawKeyEvents
                ? (FocusNode data, RawKeyEvent event) =>
                    inputModel.handleRawKeyEvent(event)
                : null,
            onKeyEvent: useRawKeyEvents
                ? null
                : (FocusNode node, KeyEvent event) =>
                    inputModel.handleKeyEvent(event),
            child: child));
  }
}

// For virtual mouse when using the mouse mode on mobile.
// Special hold-drag mode: one finger holds a button (left/right button), another finger pans.
// This flag is to override the scale gesture to a pan gesture.
bool isSpecialHoldDragActive = false;
// Cache the last focal point to calculate deltas in special hold-drag mode.
Offset _lastSpecialHoldDragFocalPoint = Offset.zero;

class RawTouchGestureDetectorRegion extends StatefulWidget {
  final Widget child;
  final FFI ffi;
  final bool isCamera;
  late final InputModel inputModel = ffi.inputModel;
  late final FfiModel ffiModel = ffi.ffiModel;

  RawTouchGestureDetectorRegion({
    required this.child,
    required this.ffi,
    this.isCamera = false,
  });

  @override
  State<RawTouchGestureDetectorRegion> createState() =>
      _RawTouchGestureDetectorRegionState();
}

/// touchMode only:
///   LongPress -> right click
///   OneFingerPan -> start/end -> left down start/end
///   onDoubleTapDown -> move to
///   onLongPressDown => move to
///
/// mouseMode only:
///   DoubleFiner -> right click
///   HoldDrag -> left drag
enum _TwoFingerGesture { none, zoom }

class _RawTouchGestureDetectorRegionState
    extends State<RawTouchGestureDetectorRegion> {
  Offset _cacheLongPressPosition = Offset(0, 0);
  // Timestamp of the last long press event.
  int _cacheLongPressPositionTs = 0;
  double _scale = 1;
  double _twoFingerInitialScale = 1;
  bool _twoFingerHasInitialUpdate = false;
  _TwoFingerGesture _twoFingerGesture = _TwoFingerGesture.none;

  // Workaround tap down event when two fingers are used to scale(mobile)
  TapDownDetails? _lastTapDownDetails;

  PointerDeviceKind? lastDeviceKind;

  // For touch mode, onDoubleTap
  // `onDoubleTap()` does not provide the position of the tap event.
  Offset _lastPosOfDoubleTapDown = Offset.zero;
  bool _touchModePanStarted = false;
  Offset _doubleFinerTapPosition = Offset.zero;

  // For mouse mode, we need to block the events when the cursor is in a blocked area.
  // So we need to cache the last tap down position.
  Offset? _lastTapDownPositionForMouseMode;
  // Cache global position for onTap (which lacks position info).
  Offset? _lastTapDownGlobalPosition;
  final Map<int, Offset> _rawTouchPositions = <int, Offset>{};
  int? _rawTapPointer;
  Offset? _rawTapStart;
  Duration? _rawTapStartTime;
  bool _rawTapCandidate = false;
  int _rawTapSequence = 0;
  int _standardTapSequence = -1;
  Timer? _rawTapTimer;
  final Map<int, Offset> _rawTwoFingerStartPositions = <int, Offset>{};
  final Map<int, Duration> _rawTwoFingerLastMoveTimes = <int, Duration>{};
  final Map<int, Offset> _rawTwoFingerLastMoveDeltas = <int, Offset>{};
  Offset? _rawTwoFingerFocalPoint;
  double _rawTwoFingerWheelDistance = 0;
  bool _rawTwoFingerScroll = false;

  FFI get ffi => widget.ffi;
  FfiModel get ffiModel => widget.ffiModel;
  InputModel get inputModel => widget.inputModel;
  bool get handleTouch => (isDesktop || isWebDesktop) || ffiModel.touchMode;
  SessionID get sessionId => ffi.sessionId;

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.opaque,
      onPointerDown: _onRawTouchDown,
      onPointerMove: _onRawTouchMove,
      onPointerUp: _onRawTouchUp,
      onPointerCancel: _onRawTouchCancel,
      child: RawGestureDetector(
        child: widget.child,
        gestures: makeGestures(context),
      ),
    );
  }

  bool _isTouchPointer(PointerEvent event) =>
      kTouchBasedDeviceKinds.contains(event.kind);

  void _onRawTouchDown(PointerDownEvent event) {
    if (!_isTouchPointer(event)) return;
    lastDeviceKind = event.kind;
    _rawTouchPositions[event.pointer] = event.localPosition;
    if (_rawTouchPositions.length == 1) {
      _rawTapSequence++;
      _rawTapPointer = event.pointer;
      _rawTapStart = event.localPosition;
      _rawTapStartTime = event.timeStamp;
      _rawTapCandidate = true;
      return;
    }
    _rawTapCandidate = false;
    _rawTapTimer?.cancel();
    _resetRawTwoFingerTracking();
    if (_rawTouchPositions.length == 2) {
      _rawTwoFingerStartPositions.addAll(_rawTouchPositions);
    }
  }

  void _onRawTouchMove(PointerMoveEvent event) {
    if (!_isTouchPointer(event) ||
        !_rawTouchPositions.containsKey(event.pointer)) {
      return;
    }
    final previousPosition = _rawTouchPositions[event.pointer]!;
    _rawTouchPositions[event.pointer] = event.localPosition;
    final tapStart = _rawTapStart;
    if (event.pointer == _rawTapPointer &&
        tapStart != null &&
        (event.localPosition - tapStart).distance > kTouchSlop) {
      _rawTapCandidate = false;
    }
    _updateRawTwoFingerScroll(
        event.pointer, event.timeStamp, event.localPosition - previousPosition);
  }

  void _onRawTouchUp(PointerUpEvent event) {
    if (!_isTouchPointer(event)) return;
    final isCandidate = event.pointer == _rawTapPointer &&
        _rawTapCandidate &&
        _rawTouchPositions.length == 1 &&
        _rawTapStartTime != null &&
        event.timeStamp - _rawTapStartTime! < const Duration(milliseconds: 450);
    final sequence = _rawTapSequence;
    _rawTouchPositions.remove(event.pointer);
    if (isCandidate) {
      _scheduleRawTapFallback(event, sequence);
    }
    if (_rawTouchPositions.length < 2) {
      _resetRawTwoFingerTracking();
    }
    if (_rawTouchPositions.isEmpty) {
      _rawTapPointer = null;
      _rawTapStart = null;
      _rawTapStartTime = null;
      _rawTapCandidate = false;
    }
  }

  void _onRawTouchCancel(PointerCancelEvent event) {
    if (!_isTouchPointer(event)) return;
    _rawTouchPositions.remove(event.pointer);
    _rawTapCandidate = false;
    _rawTapTimer?.cancel();
    if (_rawTouchPositions.length < 2) {
      _resetRawTwoFingerTracking();
    }
  }

  void _updateRawTwoFingerScroll(
      int movedPointer, Duration timeStamp, Offset moveDelta) {
    if (_rawTouchPositions.length != 2) return;
    final entries = _rawTouchPositions.entries.toList(growable: false);
    final firstStart = _rawTwoFingerStartPositions[entries[0].key];
    final secondStart = _rawTwoFingerStartPositions[entries[1].key];
    if (firstStart == null || secondStart == null) return;

    final firstDelta = entries[0].value - firstStart;
    final secondDelta = entries[1].value - secondStart;
    final focalPoint = (entries[0].value + entries[1].value) / 2;
    final previous = _rawTwoFingerFocalPoint;
    _rawTwoFingerFocalPoint = focalPoint;
    if (previous == null) return;
    final delta = focalPoint - previous;
    if (moveDelta != Offset.zero) {
      _rawTwoFingerLastMoveTimes[movedPointer] = timeStamp;
      _rawTwoFingerLastMoveDeltas[movedPointer] = moveDelta;
    }

    final firstLastMoveTime = _rawTwoFingerLastMoveTimes[entries[0].key];
    final secondLastMoveTime = _rawTwoFingerLastMoveTimes[entries[1].key];
    final firstLastMoveDelta = _rawTwoFingerLastMoveDeltas[entries[0].key];
    final secondLastMoveDelta = _rawTwoFingerLastMoveDeltas[entries[1].key];
    const simultaneousMoveWindow = Duration(milliseconds: 80);
    final movedTogether = firstLastMoveTime != null &&
        secondLastMoveTime != null &&
        (firstLastMoveTime - secondLastMoveTime).abs() <=
            simultaneousMoveWindow;
    final sameRecentVerticalDirection = firstLastMoveDelta != null &&
        secondLastMoveDelta != null &&
        firstLastMoveDelta.dy != 0 &&
        secondLastMoveDelta.dy != 0 &&
        firstLastMoveDelta.dy.isNegative == secondLastMoveDelta.dy.isNegative;

    if (!_rawTwoFingerScroll) {
      const twoFingerMoveSlop = 6.0;
      final bothMovedVertically = firstDelta.dy.abs() >= twoFingerMoveSlop &&
          secondDelta.dy.abs() >= twoFingerMoveSlop;
      final sameVerticalDirection =
          firstDelta.dy.isNegative == secondDelta.dy.isNegative;
      final averageVerticalTravel =
          (firstDelta.dy.abs() + secondDelta.dy.abs()) / 2;
      final averageHorizontalTravel =
          (firstDelta.dx.abs() + secondDelta.dx.abs()) / 2;
      if (!movedTogether ||
          !sameRecentVerticalDirection ||
          !bothMovedVertically ||
          !sameVerticalDirection ||
          averageVerticalTravel <= averageHorizontalTravel * 1.2) {
        return;
      }
      _rawTwoFingerScroll = true;
      _rawTwoFingerWheelDistance += delta.dy;
    } else {
      if (!movedTogether || !sameRecentVerticalDirection) return;
      _rawTwoFingerWheelDistance += delta.dy;
    }
    while (_rawTwoFingerWheelDistance.abs() >= 4) {
      final direction = _rawTwoFingerWheelDistance > 0 ? 1 : -1;
      inputModel.scroll(direction);
      _rawTwoFingerWheelDistance -= direction * 4;
    }
  }

  void _resetRawTwoFingerTracking() {
    _rawTwoFingerStartPositions.clear();
    _rawTwoFingerLastMoveTimes.clear();
    _rawTwoFingerLastMoveDeltas.clear();
    _rawTwoFingerFocalPoint = null;
    _rawTwoFingerWheelDistance = 0;
    _rawTwoFingerScroll = false;
  }

  void _scheduleRawTapFallback(PointerUpEvent event, int sequence) {
    _rawTapTimer?.cancel();
    final localPosition = event.localPosition;
    final globalPosition = event.position;
    _rawTapTimer = Timer(const Duration(milliseconds: 120), () async {
      if (_standardTapSequence == sequence) return;
      lastDeviceKind = event.kind;
      if (handleTouch) {
        final moved =
            await ffi.cursorModel.move(localPosition.dx, localPosition.dy);
        if (moved) await inputModel.tap(MouseButtons.left);
      } else {
        _lastTapDownPositionForMouseMode = localPosition;
        _lastTapDownGlobalPosition = globalPosition;
        if (!shouldBlockMouseModeEvent()) {
          await inputModel.tap(MouseButtons.left);
        }
      }
    });
  }

  bool isNotTouchBasedDevice() {
    return !kTouchBasedDeviceKinds.contains(lastDeviceKind);
  }

  // Mobile, mouse mode.
  // Check if should block the mouse tap event (`_lastTapDownPositionForMouseMode`).
  bool shouldBlockMouseModeEvent() {
    return _lastTapDownPositionForMouseMode != null &&
        ffi.cursorModel.shouldBlock(_lastTapDownPositionForMouseMode!.dx,
            _lastTapDownPositionForMouseMode!.dy);
  }

  onTapDown(TapDownDetails d) async {
    lastDeviceKind = d.kind;
    _lastTapDownGlobalPosition = d.globalPosition;
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (handleTouch) {
      _lastPosOfDoubleTapDown = d.localPosition;
      // Desktop or mobile "Touch mode"
      _lastTapDownDetails = d;
    } else {
      _lastTapDownPositionForMouseMode = d.localPosition;
    }
  }

  onTapUp(TapUpDetails d) async {
    _standardTapSequence = _rawTapSequence;
    final TapDownDetails? lastTapDownDetails = _lastTapDownDetails;
    _lastTapDownDetails = null;
    if (isNotTouchBasedDevice()) {
      return;
    }
    // Filter duplicate touch tap events on iOS (Magic Mouse issue).
    if (inputModel.shouldIgnoreTouchTap(d.globalPosition)) {
      return;
    }
    if (handleTouch) {
      final isMoved =
          await ffi.cursorModel.move(d.localPosition.dx, d.localPosition.dy);
      if (isMoved) {
        // If pan already handled 'down', don't send it again.
        if (lastTapDownDetails != null && !_touchModePanStarted) {
          await inputModel.tapDown(MouseButtons.left);
        }
        await inputModel.tapUp(MouseButtons.left);
      }
    }
  }

  onTap() async {
    _standardTapSequence = _rawTapSequence;
    if (isNotTouchBasedDevice()) {
      return;
    }
    // Filter duplicate touch tap events on iOS (Magic Mouse issue).
    final lastPos = _lastTapDownGlobalPosition;
    if (lastPos != null && inputModel.shouldIgnoreTouchTap(lastPos)) {
      return;
    }
    if (!handleTouch) {
      // Cannot use `_lastTapDownDetails` because Flutter calls `onTapUp` before `onTap`, clearing the cached details.
      // Using `_lastTapDownPositionForMouseMode` instead.
      if (shouldBlockMouseModeEvent()) {
        return;
      }
      // Mobile, "Mouse mode"
      await inputModel.tap(MouseButtons.left);
    }
  }

  onDoubleTapDown(TapDownDetails d) async {
    lastDeviceKind = d.kind;
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (handleTouch) {
      _lastPosOfDoubleTapDown = d.localPosition;
      await ffi.cursorModel.move(d.localPosition.dx, d.localPosition.dy);
    } else {
      _lastTapDownPositionForMouseMode = d.localPosition;
    }
  }

  onDoubleTap() async {
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (ffiModel.touchMode && ffi.cursorModel.lastIsBlocked) {
      return;
    }
    if (handleTouch &&
        !ffi.cursorModel.isInRemoteRect(_lastPosOfDoubleTapDown)) {
      return;
    }
    // Check if the position is in a blocked area when using the mouse mode.
    if (!handleTouch) {
      if (shouldBlockMouseModeEvent()) {
        return;
      }
    }
    await inputModel.tap(MouseButtons.left);
    await inputModel.tap(MouseButtons.left);
  }

  onLongPressDown(LongPressDownDetails d) async {
    lastDeviceKind = d.kind;
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (handleTouch) {
      _lastPosOfDoubleTapDown = d.localPosition;
      _cacheLongPressPosition = d.localPosition;
      if (!ffi.cursorModel.isInRemoteRect(d.localPosition)) {
        return;
      }
      _cacheLongPressPositionTs = DateTime.now().millisecondsSinceEpoch;
      if (ffiModel.isPeerMobile) {
        await ffi.cursorModel
            .move(_cacheLongPressPosition.dx, _cacheLongPressPosition.dy);
        await inputModel.tapDown(MouseButtons.left);
      }
    } else {
      _lastTapDownPositionForMouseMode = d.localPosition;
    }
  }

  onLongPressUp() async {
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (handleTouch) {
      await inputModel.tapUp(MouseButtons.left);
    }
  }

  // for mobiles
  onLongPress() async {
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (!ffi.ffiModel.isPeerMobile) {
      if (handleTouch) {
        final isMoved = await ffi.cursorModel
            .move(_cacheLongPressPosition.dx, _cacheLongPressPosition.dy);
        if (!isMoved) {
          return;
        }
      } else {
        if (shouldBlockMouseModeEvent()) {
          return;
        }
      }
      await inputModel.tap(MouseButtons.right);
    } else {
      // It's better to send a message to tell the controlled device that the long press event is triggered.
      // We're now using a `TimerTask` in `InputService.kt` to decide whether to trigger the long press event.
      // It's not accurate and it's better to use the same detection logic in the controlling side.
    }
  }

  onLongPressMoveUpdate(LongPressMoveUpdateDetails d) async {
    if (!ffiModel.isPeerMobile || isNotTouchBasedDevice()) {
      return;
    }
    if (handleTouch) {
      if (!ffi.cursorModel.isInRemoteRect(d.localPosition)) {
        return;
      }
      await ffi.cursorModel.move(d.localPosition.dx, d.localPosition.dy);
    }
  }

  onDoubleFinerTapDown(TapDownDetails d) async {
    lastDeviceKind = d.kind;
    if (isNotTouchBasedDevice()) {
      return;
    }
    _doubleFinerTapPosition = d.localPosition;
    // ignore for desktop and mobile
  }

  onDoubleFinerTap(TapDownDetails d) async {
    lastDeviceKind = d.kind;
    if (isNotTouchBasedDevice()) {
      return;
    }

    // mobile mouse mode or desktop touch screen
    final isMobileMouseMode = isMobile && !ffiModel.touchMode;
    // We can't use `d.localPosition` here because it's always (0, 0) on desktop.
    final isDesktopInRemoteRect = (isDesktop || isWebDesktop) &&
        ffi.cursorModel.isInRemoteRect(_doubleFinerTapPosition);
    if (isMobileMouseMode || isDesktopInRemoteRect) {
      await inputModel.tap(MouseButtons.right);
    }
  }

  onHoldDragStart(DragStartDetails d) async {
    lastDeviceKind = d.kind;
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (!handleTouch) {
      if (isSpecialHoldDragActive) return;
      await inputModel.sendMouse('down', MouseButtons.left);
    }
  }

  onHoldDragUpdate(DragUpdateDetails d) async {
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (!handleTouch) {
      if (isSpecialHoldDragActive) return;
      await ffi.cursorModel.updatePan(d.delta, d.localPosition, handleTouch);
    }
  }

  onHoldDragEnd(DragEndDetails d) async {
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (!handleTouch) {
      await inputModel.sendMouse('up', MouseButtons.left);
    }
  }

  onOneFingerPanStart(BuildContext context, DragStartDetails d) async {
    final TapDownDetails? lastTapDownDetails = _lastTapDownDetails;
    _lastTapDownDetails = null;
    lastDeviceKind = d.kind ?? lastDeviceKind;
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (handleTouch) {
      if (lastTapDownDetails != null) {
        await ffi.cursorModel.move(lastTapDownDetails.localPosition.dx,
            lastTapDownDetails.localPosition.dy);
      }
      if (ffi.cursorModel.shouldBlock(d.localPosition.dx, d.localPosition.dy)) {
        return;
      }
      if (!ffi.cursorModel.isInRemoteRect(d.localPosition)) {
        return;
      }

      _touchModePanStarted = true;
      if (isDesktop || isWebDesktop) {
        ffi.cursorModel.trySetRemoteWindowCoords();
      }

      // Workaround for the issue that the first pan event is sent a long time after the start event.
      // If the time interval between the start event and the first pan event is less than 500ms,
      // we consider to use the long press position as the start position.
      //
      // TODO: We should find a better way to send the first pan event as soon as possible.
      if (DateTime.now().millisecondsSinceEpoch - _cacheLongPressPositionTs <
          500) {
        await ffi.cursorModel
            .move(_cacheLongPressPosition.dx, _cacheLongPressPosition.dy);
      }
      // In relative mouse mode, skip mouse down - only send movement via sendMobileRelativeMouseMove
      if (!inputModel.relativeMouseMode.value) {
        await inputModel.sendMouse('down', MouseButtons.left);
      }
      await ffi.cursorModel.move(d.localPosition.dx, d.localPosition.dy);
    } else {
      final offset = ffi.cursorModel.offset;
      final cursorX = offset.dx;
      final cursorY = offset.dy;
      final visible =
          ffi.cursorModel.getVisibleRect().inflate(1); // extend edges
      final size = MediaQueryData.fromView(View.of(context)).size;
      if (!visible.contains(Offset(cursorX, cursorY))) {
        await ffi.cursorModel.move(size.width / 2, size.height / 2);
      }
    }
  }

  onOneFingerPanUpdate(DragUpdateDetails d) async {
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (ffi.cursorModel.shouldBlock(d.localPosition.dx, d.localPosition.dy)) {
      return;
    }
    if (handleTouch && !_touchModePanStarted) {
      return;
    }
    // In relative mouse mode, send delta directly without position tracking.
    if (inputModel.relativeMouseMode.value) {
      await inputModel.sendMobileRelativeMouseMove(d.delta.dx, d.delta.dy);
    } else {
      await ffi.cursorModel.updatePan(d.delta, d.localPosition, handleTouch);
    }
  }

  onOneFingerPanEnd(DragEndDetails d) async {
    _touchModePanStarted = false;
    if (isNotTouchBasedDevice()) {
      return;
    }
    if (isDesktop || isWebDesktop) {
      ffi.cursorModel.clearRemoteWindowCoords();
    }
    if (handleTouch) {
      // In relative mouse mode, skip mouse up - matches the skipped mouse down in onOneFingerPanStart
      if (!inputModel.relativeMouseMode.value) {
        await inputModel.sendMouse('up', MouseButtons.left);
      }
    }
  }

  // Reset `_touchModePanStarted` if the one-finger pan gesture is cancelled
  // or rejected by the gesture arena. Without this, the flag can remain
  // stuck in the "started" state and cause issues such as the Magic Mouse
  // double-click problem on iPad with magic mouse.
  onOneFingerPanCancel() {
    _touchModePanStarted = false;
  }

  // scale + pan event
  onTwoFingerScaleStart(ScaleStartDetails d) async {
    _lastTapDownDetails = null;
    _twoFingerGesture = _TwoFingerGesture.none;
    _scale = 1;
    _twoFingerInitialScale = 1;
    _twoFingerHasInitialUpdate = false;
    if (isNotTouchBasedDevice()) {
      return;
    }
    // A first finger can have started a touch-mode drag just before the
    // second finger lands. End that drag immediately so a two-finger gesture
    // cannot be sent to the remote side as a left-button drag.
    if (isMobile && handleTouch && _touchModePanStarted) {
      _touchModePanStarted = false;
      if (!inputModel.relativeMouseMode.value) {
        await inputModel.sendMouse('up', MouseButtons.left);
      }
    }
    if (isSpecialHoldDragActive) {
      // Initialize the last focal point to calculate deltas manually.
      _lastSpecialHoldDragFocalPoint = d.focalPoint;
    }
  }

  onTwoFingerScaleUpdate(ScaleUpdateDetails d) async {
    if (isNotTouchBasedDevice()) {
      return;
    }

    // If in special drag mode, perform a pan instead of a scale.
    if (isSpecialHoldDragActive) {
      // Calculate delta manually to avoid the jumpy behavior.
      final delta = d.focalPoint - _lastSpecialHoldDragFocalPoint;
      _lastSpecialHoldDragFocalPoint = d.focalPoint;
      await ffi.cursorModel.updatePan(delta * 2.0, d.focalPoint, handleTouch);
      return;
    }

    if ((isDesktop || isWebDesktop)) {
      final scale = ((d.scale - _scale) * 1000).toInt();
      _scale = d.scale;

      if (scale != 0) {
        if (widget.isCamera) return;
        await bind.sessionSendPointer(
            sessionId: sessionId,
            msg: json.encode(
                PointerEventToRust(kPointerEventKindTouch, 'scale', scale)
                    .toJson()));
      }
    } else {
      if (_rawTwoFingerScroll) return;
      // Mobile: the raw pointer path owns two-finger scrolling so it can
      // require both contacts to move. This scale path only handles pinch
      // zoom and must never turn a one-finger move plus one held finger into
      // a remote wheel event.
      final isFirstUpdate = !_twoFingerHasInitialUpdate;
      if (isFirstUpdate) {
        _twoFingerHasInitialUpdate = true;
        _twoFingerInitialScale = d.scale;
        _scale = d.scale;
      }

      if (_twoFingerGesture == _TwoFingerGesture.none) {
        if (!isFirstUpdate && (d.scale - _twoFingerInitialScale).abs() > 0.05) {
          _twoFingerGesture = _TwoFingerGesture.zoom;
        }
      }

      if (_twoFingerGesture == _TwoFingerGesture.zoom) {
        ffi.canvasModel.updateScale(d.scale / _scale, d.focalPoint);
        _scale = d.scale;
      }
    }
  }

  onTwoFingerScaleEnd(ScaleEndDetails d) async {
    if (isNotTouchBasedDevice()) {
      return;
    }
    if ((isDesktop || isWebDesktop)) {
      if (widget.isCamera) return;
      await bind.sessionSendPointer(
          sessionId: sessionId,
          msg: json.encode(
              PointerEventToRust(kPointerEventKindTouch, 'scale', 0).toJson()));
    } else {
      // mobile
      _scale = 1;
      _twoFingerInitialScale = 1;
      _twoFingerHasInitialUpdate = false;
      _twoFingerGesture = _TwoFingerGesture.none;
      // No idea why we need to set the view style to "" here.
      // bind.sessionSetViewStyle(sessionId: sessionId, value: "");
    }
    if (!isSpecialHoldDragActive) {
      await inputModel.sendMouse('up', MouseButtons.left);
    }
  }

  get onHoldDragCancel => null;
  get onThreeFingerVerticalDragUpdate => (d) {
        ffi.canvasModel.panX(d.delta.dx);
        ffi.canvasModel.panY(d.delta.dy);
      };

  makeGestures(BuildContext context) {
    return <Type, GestureRecognizerFactory>{
      // Official
      TapGestureRecognizer:
          GestureRecognizerFactoryWithHandlers<TapGestureRecognizer>(
              () => TapGestureRecognizer(), (instance) {
        instance
          ..onTapDown = onTapDown
          ..onTapUp = onTapUp
          ..onTap = onTap;
      }),
      DoubleTapGestureRecognizer:
          GestureRecognizerFactoryWithHandlers<DoubleTapGestureRecognizer>(
              () => DoubleTapGestureRecognizer(), (instance) {
        instance
          ..onDoubleTapDown = onDoubleTapDown
          ..onDoubleTap = onDoubleTap;
      }),
      LongPressGestureRecognizer:
          GestureRecognizerFactoryWithHandlers<LongPressGestureRecognizer>(
              () => LongPressGestureRecognizer(), (instance) {
        instance
          ..onLongPressDown = onLongPressDown
          ..onLongPressUp = onLongPressUp
          ..onLongPress = onLongPress
          ..onLongPressMoveUpdate = onLongPressMoveUpdate;
      }),
      // Customized
      HoldTapMoveGestureRecognizer:
          GestureRecognizerFactoryWithHandlers<HoldTapMoveGestureRecognizer>(
              () => HoldTapMoveGestureRecognizer(),
              (instance) => instance
                ..onHoldDragStart = onHoldDragStart
                ..onHoldDragUpdate = onHoldDragUpdate
                ..onHoldDragCancel = onHoldDragCancel
                ..onHoldDragEnd = onHoldDragEnd),
      DoubleFinerTapGestureRecognizer:
          GestureRecognizerFactoryWithHandlers<DoubleFinerTapGestureRecognizer>(
              () => DoubleFinerTapGestureRecognizer(), (instance) {
        instance
          ..onDoubleFinerTap = onDoubleFinerTap
          ..onDoubleFinerTapDown = onDoubleFinerTapDown;
      }),
      CustomTouchGestureRecognizer:
          GestureRecognizerFactoryWithHandlers<CustomTouchGestureRecognizer>(
              () => CustomTouchGestureRecognizer(), (instance) {
        instance.onOneFingerPanStart =
            (DragStartDetails d) => onOneFingerPanStart(context, d);
        instance
          ..onOneFingerPanUpdate = onOneFingerPanUpdate
          ..onOneFingerPanEnd = onOneFingerPanEnd
          ..onOneFingerPanCancel = onOneFingerPanCancel
          ..onTwoFingerScaleStart = onTwoFingerScaleStart
          ..onTwoFingerScaleUpdate = onTwoFingerScaleUpdate
          ..onTwoFingerScaleEnd = onTwoFingerScaleEnd
          ..onThreeFingerVerticalDragUpdate = onThreeFingerVerticalDragUpdate;
      }),
    };
  }
}

class RawPointerMouseRegion extends StatelessWidget {
  final InputModel inputModel;
  final Widget child;
  final MouseCursor? cursor;
  final PointerEnterEventListener? onEnter;
  final PointerExitEventListener? onExit;
  final PointerDownEventListener? onPointerDown;
  final PointerUpEventListener? onPointerUp;

  RawPointerMouseRegion({
    this.onEnter,
    this.onExit,
    this.cursor,
    this.onPointerDown,
    this.onPointerUp,
    required this.inputModel,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Listener(
      onPointerHover: inputModel.onPointHoverImage,
      onPointerDown: (evt) {
        onPointerDown?.call(evt);
        inputModel.onPointDownImage(evt);
      },
      onPointerUp: (evt) {
        onPointerUp?.call(evt);
        inputModel.onPointUpImage(evt);
      },
      onPointerMove: inputModel.onPointMoveImage,
      onPointerSignal: inputModel.onPointerSignalImage,
      onPointerPanZoomStart: inputModel.onPointerPanZoomStart,
      onPointerPanZoomUpdate: inputModel.onPointerPanZoomUpdate,
      onPointerPanZoomEnd: inputModel.onPointerPanZoomEnd,
      child: MouseRegion(
        cursor: inputModel.isViewOnly
            ? MouseCursor.defer
            : (cursor ?? MouseCursor.defer),
        onEnter: onEnter,
        onExit: onExit,
        child: child,
      ),
    );
  }
}

class CameraRawPointerMouseRegion extends StatelessWidget {
  final InputModel inputModel;
  final Widget child;
  final PointerEnterEventListener? onEnter;
  final PointerExitEventListener? onExit;
  final PointerDownEventListener? onPointerDown;
  final PointerUpEventListener? onPointerUp;

  CameraRawPointerMouseRegion({
    this.onEnter,
    this.onExit,
    this.onPointerDown,
    this.onPointerUp,
    required this.inputModel,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Listener(
      onPointerHover: (evt) {
        final offset = evt.position;
        double x = offset.dx;
        double y = max(0.0, offset.dy);
        inputModel.handlePointerDevicePos(
            kPointerEventKindMouse, x, y, true, kMouseEventTypeDefault);
      },
      onPointerDown: (evt) {
        onPointerDown?.call(evt);
      },
      onPointerUp: (evt) {
        onPointerUp?.call(evt);
      },
      child: MouseRegion(
        cursor: MouseCursor.defer,
        onEnter: onEnter,
        onExit: onExit,
        child: child,
      ),
    );
  }
}
