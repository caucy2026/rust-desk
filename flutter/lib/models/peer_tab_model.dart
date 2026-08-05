import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_hbb/consts.dart';
import 'package:flutter_hbb/models/peer_model.dart';
import 'package:flutter_hbb/models/platform_model.dart';
import 'package:get/get.dart';

import '../common.dart';
import 'model.dart';

enum PeerTabIndex {
  recent,
  fav,
  lan,
  ab,
  group,
  connectionHistory,
}

class PeerTabModel with ChangeNotifier {
  WeakReference<FFI> parent;
  int get currentTab => _currentTab;
  int _currentTab = 0; // index in tabNames
  static const int maxTabCount = 6;
  static const List<String> tabNames = [
    'Recent sessions',
    'Favorites',
    'Discovered',
    'Address book',
    'Accessible devices',
    '连接记录',
  ];
  // KEMI's home screen is localized for the target Chinese PAD/Mac workflow.
  // Keep these product explanations in the shared UI model rather than adding
  // new upstream RustDesk translation keys outside template.rs.
  static const List<String> tabRemarks = [
    '这里显示最近成功连接的设备，点击可快速再次连接。',
    '将常用设备加入收藏，方便快速访问。',
    '查看当前局域网内可被发现的设备。',
    '管理并同步地址簿中的联系人和设备。',
    '查看已共享给当前账号或当前账号可访问的设备。',
    '记录每次连接时间、P2P/中继结果和失败原因，可按条删除。',
  ];
  static const List<IconData> icons = [
    Icons.access_time_filled,
    Icons.star,
    Icons.explore,
    IconFont.addressBook,
    IconFont.deviceGroupFill,
    Icons.history,
  ];
  List<bool> isEnabled = List.from([
    true,
    true,
    !isWeb && bind.mainGetLocalOption(key: "disable-discovery-panel") != "Y",
    !bind.isDisableAb(),
    !bind.isDisableGroupPanel(),
    true,
  ]);
  final List<bool> _isVisible = List.filled(maxTabCount, true, growable: false);
  List<bool> get isVisibleEnabled => () {
        final list = _isVisible.toList();
        for (int i = 0; i < maxTabCount; i++) {
          list[i] = list[i] && isEnabled[i];
        }
        return list;
      }();
  final List<int> orders =
      List.generate(maxTabCount, (index) => index, growable: false);
  List<int> get visibleEnabledOrderedIndexs =>
      orders.where((e) => isVisibleEnabled[e]).toList();
  List<Peer> _selectedPeers = List.empty(growable: true);
  List<Peer> get selectedPeers => _selectedPeers;
  bool _multiSelectionMode = false;
  bool get multiSelectionMode => _multiSelectionMode;
  List<Peer> _currentTabCachedPeers = List.empty(growable: true);
  List<Peer> get currentTabCachedPeers => _currentTabCachedPeers;
  bool _isShiftDown = false;
  bool get isShiftDown => _isShiftDown;
  String _lastId = '';
  String get lastId => _lastId;

  PeerTabModel(this.parent) {
    // visible
    try {
      final option = bind.getLocalFlutterOption(k: kOptionPeerTabVisible);
      if (option.isNotEmpty) {
        List<dynamic> decodeList = jsonDecode(option);
        // Migrate the previous five-tab layout by preserving all old choices
        // and appending the new connection-history tab as visible.
        if (decodeList.length == _isVisible.length ||
            decodeList.length == _isVisible.length - 1) {
          for (int i = 0; i < decodeList.length; i++) {
            if (decodeList[i] is bool) {
              _isVisible[i] = decodeList[i];
            }
          }
        }
      }
    } catch (e) {
      debugPrint("failed to get peer tab visible list:$e");
    }
    // order
    try {
      final option = bind.getLocalFlutterOption(k: kOptionPeerTabOrder);
      if (option.isNotEmpty) {
        List<dynamic> decodeList = jsonDecode(option);
        if (decodeList.length == maxTabCount ||
            decodeList.length == maxTabCount - 1) {
          var migrated = decodeList.toList();
          if (migrated.length == maxTabCount - 1) {
            migrated.add(maxTabCount - 1);
          }
          var sortedList = migrated.toList();
          sortedList.sort();
          bool valid = true;
          for (int i = 0; i < maxTabCount; i++) {
            if (sortedList[i] is! int || sortedList[i] != i) {
              valid = false;
            }
          }
          if (valid) {
            for (int i = 0; i < orders.length; i++) {
              orders[i] = migrated[i];
            }
          }
        }
      }
    } catch (e) {
      debugPrint("failed to get peer tab order list: $e");
    }
    // init currentTab
    _currentTab =
        int.tryParse(bind.getLocalFlutterOption(k: kOptionPeerTabIndex)) ?? 0;
    if (_currentTab < 0 || _currentTab >= maxTabCount) {
      _currentTab = 0;
    }
    _trySetCurrentTabToFirstVisibleEnabled();
  }

  setCurrentTab(int index) {
    if (_currentTab != index) {
      _currentTab = index;
      notifyListeners();
    }
  }

  String tabTooltip(int index) {
    if (index >= 0 && index < tabNames.length) {
      return translate(tabNames[index]);
    }
    return index.toString();
  }

  String tabRemark(int index) {
    if (index >= 0 && index < tabRemarks.length) {
      return tabRemarks[index];
    }
    return '';
  }

  IconData tabIcon(int index) {
    if (index >= 0 && index < icons.length) {
      return icons[index];
    }
    return Icons.help;
  }

  setMultiSelectionMode(bool mode) {
    _multiSelectionMode = mode;
    if (!mode) {
      _selectedPeers.clear();
      _lastId = '';
    }
    notifyListeners();
  }

  select(Peer peer) {
    if (!_multiSelectionMode) {
      // https://github.com/flutter/flutter/issues/101275#issuecomment-1604541700
      // After onTap, the shift key should be pressed for a while when not in multiselection mode,
      // because onTap is delayed when onDoubleTap is not null
      if (isDesktop || isWebDesktop) return;
      _multiSelectionMode = true;
    }
    final cached = _currentTabCachedPeers.map((e) => e.id).toList();
    int thisIndex = cached.indexOf(peer.id);
    int lastIndex = cached.indexOf(_lastId);
    if (_isShiftDown && thisIndex >= 0 && lastIndex >= 0) {
      int start = min(thisIndex, lastIndex);
      int end = max(thisIndex, lastIndex);
      bool remove = isPeerSelected(peer.id);
      for (var i = start; i <= end; i++) {
        if (remove) {
          if (isPeerSelected(cached[i])) {
            _selectedPeers.removeWhere((p) => p.id == cached[i]);
          }
        } else {
          if (!isPeerSelected(cached[i])) {
            _selectedPeers.add(_currentTabCachedPeers[i]);
          }
        }
      }
    } else {
      if (isPeerSelected(peer.id)) {
        _selectedPeers.removeWhere((p) => p.id == peer.id);
      } else {
        _selectedPeers.add(peer);
      }
    }
    _lastId = peer.id;
    notifyListeners();
  }

  // `notifyListeners()` will cause many rebuilds.
  // So, we need to reduce the calls to "notifyListeners()" only when necessary.
  // A better way is to use a new model.
  setCurrentTabCachedPeers(List<Peer> peers) {
    Future.delayed(Duration.zero, () {
      final isPreEmpty = _currentTabCachedPeers.isEmpty;
      _currentTabCachedPeers = peers;
      final isNowEmpty = _currentTabCachedPeers.isEmpty;
      if (isPreEmpty != isNowEmpty) {
        notifyListeners();
      }
    });
  }

  selectAll() {
    _selectedPeers = _currentTabCachedPeers.toList();
    notifyListeners();
  }

  bool isPeerSelected(String id) {
    return selectedPeers.firstWhereOrNull((p) => p.id == id) != null;
  }

  setShiftDown(bool v) {
    if (_isShiftDown != v) {
      _isShiftDown = v;
      if (_multiSelectionMode) {
        notifyListeners();
      }
    }
  }

  setTabVisible(int index, bool visible) {
    if (index >= 0 && index < maxTabCount) {
      if (_isVisible[index] != visible) {
        _isVisible[index] = visible;
        if (index == _currentTab && !visible) {
          _trySetCurrentTabToFirstVisibleEnabled();
        } else if (visible && visibleEnabledOrderedIndexs.length == 1) {
          _currentTab = index;
        }
        try {
          bind.setLocalFlutterOption(
              k: kOptionPeerTabVisible, v: jsonEncode(_isVisible));
        } catch (_) {}
        notifyListeners();
      }
    }
  }

  _trySetCurrentTabToFirstVisibleEnabled() {
    if (!visibleEnabledOrderedIndexs.contains(_currentTab)) {
      if (visibleEnabledOrderedIndexs.isNotEmpty) {
        _currentTab = visibleEnabledOrderedIndexs.first;
      }
    }
  }

  reorder(int oldIndex, int newIndex) {
    if (oldIndex < newIndex) {
      newIndex -= 1;
    }
    if (oldIndex < 0 || oldIndex >= visibleEnabledOrderedIndexs.length) {
      return;
    }
    if (newIndex < 0 || newIndex >= visibleEnabledOrderedIndexs.length) {
      return;
    }
    final oldTabValue = visibleEnabledOrderedIndexs[oldIndex];
    final newTabValue = visibleEnabledOrderedIndexs[newIndex];
    int oldValueIndex = orders.indexOf(oldTabValue);
    int newValueIndex = orders.indexOf(newTabValue);
    final list = orders.toList();
    if (oldIndex != -1 && newIndex != -1) {
      list.removeAt(oldValueIndex);
      list.insert(newValueIndex, oldTabValue);
      for (int i = 0; i < list.length; i++) {
        orders[i] = list[i];
      }
      bind.setLocalFlutterOption(k: kOptionPeerTabOrder, v: jsonEncode(orders));
      notifyListeners();
    }
  }
}
