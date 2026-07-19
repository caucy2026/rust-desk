import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_hbb/common/formatter/id_formatter.dart';
import 'package:flutter_hbb/common/widgets/connection_page_title.dart';
import 'package:flutter_hbb/models/state_model.dart';
import 'package:get/get.dart';
import 'package:provider/provider.dart';
import 'package:flutter_hbb/models/peer_model.dart';

import '../../common.dart';
import '../../common/widgets/peer_tab_page.dart';
import '../../common/widgets/autocomplete.dart';
import '../../consts.dart';
import '../../models/model.dart';
import '../../models/platform_model.dart';
import 'home_page.dart';

/// 双屏模式: 主屏 (Display 0) 控制页面。
///
/// 职责:
///   1. 输入远程设备 ID + 发起连接 → 启动 RemoteActivity 到副屏 Display 2
///   2. 键盘输入区域 → 发送键盘事件到远程被控设备
///   3. 快捷操作按钮 (断开连接等)
///
/// 触摸: 主屏也支持触摸操作 (按钮点击、列表滚动等)。
class ControlPage extends StatefulWidget implements PageShape {
  ControlPage({Key? key, required this.appBarActions}) : super(key: key);

  @override
  final icon = const Icon(Icons.connected_tv);

  @override
  final title = "Remote Control";

  @override
  final List<Widget> appBarActions;

  @override
  State<ControlPage> createState() => _ControlPageState();
}

class _ControlPageState extends State<ControlPage> {
  final _idController = IDTextEditingController();
  final RxBool _idEmpty = true.obs;
  final FocusNode _idFocusNode = FocusNode();
  final TextEditingController _idEditingController = TextEditingController();
  final AllPeersLoader _allPeersLoader = AllPeersLoader();

  // 键盘输入
  final TextEditingController _keyboardController = TextEditingController();
  final FocusNode _keyboardFocusNode = FocusNode();
  bool _isConnected = false;
  String _remoteId = '';

  StreamSubscription? _uniLinksSubscription;

  Iterable<Peer> _autocompleteOpts = [];

  _ControlPageState() {
    if (!isWeb) _uniLinksSubscription = listenUniLinks();
    _idController.addListener(() {
      _idEmpty.value = _idController.text.isEmpty;
    });
    Get.put<IDTextEditingController>(_idController);

    // 监听键盘输入变化 → 发送到远程
    _keyboardController.addListener(_onKeyboardTextChanged);
  }

  @override
  void initState() {
    super.initState();
    _allPeersLoader.init(setState);
    _idFocusNode.addListener(_onIdFocusChanged);
    if (_idController.text.isEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        final lastRemoteId = await bind.mainGetLastRemoteId();
        if (lastRemoteId != _idController.id) {
          if (mounted) {
            setState(() {
              _idController.id = lastRemoteId;
            });
          }
        }
      });
    }
    Get.put<TextEditingController>(_idEditingController);
    // 监听远程连接状态
    _checkRemoteState();
  }

  @override
  void dispose() {
    _keyboardController.removeListener(_onKeyboardTextChanged);
    _keyboardController.dispose();
    _keyboardFocusNode.dispose();
    _idFocusNode.removeListener(_onIdFocusChanged);
    _idFocusNode.dispose();
    super.dispose();
  }

  void _checkRemoteState() async {
    try {
      final result = await platformFFI.invokeMethod("get_remote_state");
      if (result != null && mounted) {
        setState(() {
          _isConnected = result['connected'] == true;
          if (result['sessionId'] != null && result['sessionId'].isNotEmpty) {
            _remoteId = _idController.id;
          }
        });
      }
    } catch (_) {}
  }

  /// 键盘文本变化时，将新增字符发送到远程。
  /// 使用简单的 diff 算法: 只发送新增的字符。
  String _lastSentText = '';

  void _onKeyboardTextChanged() {
    if (!_isConnected) return;

    final currentText = _keyboardController.text;
    if (currentText.length > _lastSentText.length) {
      // 新增了字符
      final newChars = currentText.substring(_lastSentText.length);
      _sendKeyString(newChars);
    } else if (currentText.length < _lastSentText.length) {
      // 删除了字符 → 发送退格键
      final deleteCount = _lastSentText.length - currentText.length;
      for (int i = 0; i < deleteCount; i++) {
        _sendKeyEvent('VK_BACK', true);
        _sendKeyEvent('VK_BACK', false);
      }
    }
    _lastSentText = currentText;
  }

  /// 发送字符串到远程 (通过 MethodChannel → SessionState → RemoteActivity → FFI)
  void _sendKeyString(String text) {
    try {
      platformFFI.invokeMethod("send_key_string", {"text": text});
    } catch (e) {
      debugPrint("send_key_string error: $e");
    }
  }

  /// 发送单个虚拟键事件
  void _sendKeyEvent(String key, bool down) {
    try {
      platformFFI.invokeMethod("send_key_event", {"key": key, "down": down});
    } catch (e) {
      debugPrint("send_key_event error: $e");
    }
  }

  /// 连接按钮回调 — 启动 RemoteActivity 到副屏 Display 2
  void onConnect() {
    var id = _idController.id;
    if (id.isEmpty) return;

    id = id.replaceAll(' ', '');
    _remoteId = id;

    // 通过 MethodChannel 请求 Kotlin 层启动 RemoteActivity 到 Display 2
    platformFFI.invokeMethod("launch_remote_on_display2", {
      "peer_id": id,
      "password": null,
      "force_relay": false,
    });

    setState(() {
      _isConnected = true;
    });

    // 延迟检查实际连接状态
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) _checkRemoteState();
    });
  }

  /// 断开远程连接
  void onDisconnect() {
    platformFFI.invokeMethod("close_remote");
    setState(() {
      _isConnected = false;
      _lastSentText = '';
      _keyboardController.clear();
    });
  }

  void _onIdFocusChanged() {
    _idEmpty.value = _idEditingController.text.isEmpty;
    if (_idFocusNode.hasFocus) {
      if (_allPeersLoader.needLoad) {
        _allPeersLoader.getAllPeers();
      }
      final textLength = _idEditingController.value.text.length;
      _idEditingController.selection =
          TextSelection(baseOffset: 0, extentOffset: textLength);
    }
  }

  @override
  Widget build(BuildContext context) {
    Provider.of<FfiModel>(context);
    return CustomScrollView(
      slivers: [
        SliverList(
          delegate: SliverChildListDelegate([
            // 连接区域
            if (!_isConnected) _buildConnectionUI(),
            // 已连接: 键盘输入区域
            if (_isConnected) _buildKeyboardUI(),
            // 对等设备列表
            if (!_isConnected)
              SizedBox(
                height: 400,
                child: PeerTabPage(),
              ),
          ]),
        ),
      ],
    ).marginOnly(top: 2, left: 10, right: 10);
  }

  /// 连接 UI (与原始 ConnectionPage 相同风格)
  Widget _buildConnectionUI() {
    return SizedBox(
      height: 84,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 2),
        child: Ink(
          decoration: BoxDecoration(
            color: Theme.of(context).cardColor,
            borderRadius: const BorderRadius.all(Radius.circular(13)),
          ),
          child: Row(
            children: <Widget>[
              Expanded(
                child: Container(
                  padding: const EdgeInsets.only(left: 16, right: 16),
                  child: RawAutocomplete<Peer>(
                    optionsBuilder: (TextEditingValue textEditingValue) {
                      if (textEditingValue.text == '') {
                        _autocompleteOpts = const Iterable<Peer>.empty();
                      } else if (_allPeersLoader.peers.isEmpty &&
                          !_allPeersLoader.isPeersLoaded) {
                        _autocompleteOpts = [
                          Peer(
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
                          )
                        ];
                      } else {
                        String textToFind =
                            textEditingValue.text.replaceAll(" ", "").toLowerCase();
                        _autocompleteOpts = _allPeersLoader.peers
                            .where((peer) =>
                                peer.id.toLowerCase().contains(textToFind) ||
                                peer.username.toLowerCase().contains(textToFind) ||
                                peer.hostname.toLowerCase().contains(textToFind) ||
                                peer.alias.toLowerCase().contains(textToFind))
                            .toList();
                        _allPeersLoader.queryOnlines(_autocompleteOpts);
                      }
                      return _autocompleteOpts;
                    },
                    focusNode: _idFocusNode,
                    textEditingController: _idEditingController,
                    fieldViewBuilder: (BuildContext context,
                        TextEditingController fieldTextEditingController,
                        FocusNode fieldFocusNode,
                        VoidCallback onFieldSubmitted) {
                      // keep text in sync
                      if (fieldTextEditingController.text != _idController.text) {
                        fieldTextEditingController.text = _idController.text;
                        fieldTextEditingController.selection =
                            TextSelection.collapsed(
                                offset: fieldTextEditingController.text.length);
                      }
                      return TextField(
                        controller: fieldTextEditingController,
                        focusNode: fieldFocusNode,
                        autocorrect: false,
                        enableSuggestions: false,
                        keyboardType: TextInputType.visiblePassword,
                        onChanged: (String text) {
                          _idController.id = text;
                        },
                        style: const TextStyle(
                          fontFamily: 'WorkSans',
                          fontWeight: FontWeight.bold,
                          fontSize: 30,
                          color: MyTheme.idColor,
                        ),
                        decoration: InputDecoration(
                          labelText: translate('Remote ID'),
                          border: InputBorder.none,
                          labelStyle: const TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 16,
                          ),
                        ),
                      );
                    },
                    onSelected: (Peer peer) {
                      _idController.id = peer.id;
                      onConnect();
                    },
                  ),
                ),
              ),
              // Connect button
              Obx(() => IconButton(
                    icon: Icon(
                      Icons.arrow_forward_ios,
                      color: _idEmpty.value
                          ? MyTheme.darkGray
                          : MyTheme.accent,
                    ),
                    onPressed: _idEmpty.value ? null : onConnect,
                  )),
            ],
          ),
        ),
      ),
    );
  }

  /// 键盘输入 UI (已连接远程时显示)
  Widget _buildKeyboardUI() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // 状态栏
        Container(
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          decoration: BoxDecoration(
            color: Theme.of(context).cardColor,
            borderRadius: BorderRadius.circular(13),
          ),
          child: Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: Color(0xFF3FB950),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Connected to $_remoteId',
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                    color: Color(0xFF3FB950),
                  ),
                ),
              ),
              // Disconnect button
              TextButton.icon(
                onPressed: onDisconnect,
                icon: const Icon(Icons.close, color: Colors.red, size: 18),
                label: const Text(
                  'Disconnect',
                  style: TextStyle(color: Colors.red, fontSize: 13),
                ),
                style: TextButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 16),

        // 键盘输入提示
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Text(
            'Type here to send keyboard input to the remote device:',
            style: TextStyle(
              fontSize: 13,
              color: Colors.grey[400],
            ),
          ),
        ),

        // 键盘输入框
        Container(
          margin: const EdgeInsets.symmetric(horizontal: 2),
          decoration: BoxDecoration(
            color: Theme.of(context).cardColor,
            borderRadius: BorderRadius.circular(13),
          ),
          child: TextField(
            controller: _keyboardController,
            focusNode: _keyboardFocusNode,
            autocorrect: false,
            enableSuggestions: false,
            maxLines: 5,
            minLines: 3,
            style: const TextStyle(
              fontSize: 16,
              fontFamily: 'WorkSans',
            ),
            decoration: InputDecoration(
              hintText: 'Keyboard input → remote device...',
              hintStyle: TextStyle(color: Colors.grey[600], fontSize: 14),
              border: InputBorder.none,
              contentPadding: const EdgeInsets.all(16),
            ),
            onTap: () {
              // 点击输入框时确保键盘弹出
              _keyboardFocusNode.requestFocus();
            },
          ),
        ),

        const SizedBox(height: 12),

        // 快捷按键行
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _buildQuickKeyButton('Enter', 'VK_RETURN'),
            _buildQuickKeyButton('Tab', 'VK_TAB'),
            _buildQuickKeyButton('Esc', 'VK_ESCAPE'),
            _buildQuickKeyButton('Del', 'VK_DELETE'),
            _buildQuickKeyButton('Space', 'VK_SPACE'),
            _buildQuickKeyButton('Home', 'VK_HOME'),
            _buildQuickKeyButton('End', 'VK_END'),
            _buildQuickKeyButton('↑', 'VK_UP'),
            _buildQuickKeyButton('↓', 'VK_DOWN'),
            _buildQuickKeyButton('←', 'VK_LEFT'),
            _buildQuickKeyButton('→', 'VK_RIGHT'),
            _buildQuickKeyButton('Ctrl+C', 'VK_C'),
            _buildQuickKeyButton('Ctrl+V', 'VK_V'),
            _buildQuickKeyButton('Ctrl+Z', 'VK_Z'),
          ],
        ),

        const SizedBox(height: 16),

        // 提示: 副屏触摸
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Colors.blueGrey.withOpacity(0.15),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: Colors.blueGrey.withOpacity(0.3)),
          ),
          child: Row(
            children: [
              const Icon(Icons.touch_app, color: Colors.blueGrey, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Touch the secondary screen (Display 2) to control the remote device with gestures.',
                  style: TextStyle(
                    fontSize: 11,
                    color: Colors.grey[500],
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  /// 快捷按键按钮
  Widget _buildQuickKeyButton(String label, String vkName) {
    return SizedBox(
      height: 36,
      child: ElevatedButton(
        onPressed: () {
          _sendKeyEvent(vkName, true);
          Future.delayed(const Duration(milliseconds: 50), () {
            _sendKeyEvent(vkName, false);
          });
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: Theme.of(context).cardColor,
          foregroundColor: Colors.white70,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
          side: BorderSide(color: Colors.grey.withOpacity(0.3)),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(6),
          ),
          elevation: 0,
        ),
        child: Text(
          label,
          style: const TextStyle(fontSize: 12),
        ),
      ),
    );
  }
}
