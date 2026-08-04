import 'package:flutter/material.dart';
import 'package:flutter_hbb/mobile/pages/client_download_page.dart';
import 'package:flutter_hbb/mobile/pages/server_page.dart';
import 'package:flutter_hbb/mobile/pages/settings_page.dart';
import 'package:flutter_hbb/web/settings_page.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../../common.dart';
import '../../common/widgets/chat_page.dart';
import '../../consts.dart';
import '../../models/platform_model.dart';
import '../../models/state_model.dart';
import 'connection_page.dart';

const String kKemiPadAppTitle = 'KEMI远程桌面PAD版';

abstract class PageShape extends Widget {
  final String title = "";
  final Widget icon = Icon(null);
  final List<Widget> appBarActions = [];
}

class HomePage extends StatefulWidget {
  static final homeKey = GlobalKey<HomePageState>();

  HomePage() : super(key: homeKey);

  @override
  HomePageState createState() => HomePageState();
}

class HomePageState extends State<HomePage> {
  var _selectedIndex = 0;
  int get selectedIndex => _selectedIndex;
  final List<PageShape> _pages = [];
  int _chatPageTabIndex = -1;
  String _appVersion = '';
  bool get isChatPageCurrentTab => isAndroid
      ? _selectedIndex == _chatPageTabIndex
      : false; // change this when ios have chat page

  void refreshPages() {
    setState(() {
      initPages();
    });
  }

  @override
  void initState() {
    super.initState();
    initPages();
    _loadAppVersion();
  }

  Future<void> _loadAppVersion() async {
    try {
      final version = (await PackageInfo.fromPlatform()).version;
      if (!mounted) return;
      setState(() {
        _appVersion = version.trim();
      });
    } catch (_) {}
  }

  void initPages() {
    _pages.clear();
    if (!bind.isIncomingOnly()) {
      _pages.add(ConnectionPage(
        appBarActions: [],
      ));
    }
    if (isAndroid && !bind.isOutgoingOnly()) {
      _chatPageTabIndex = _pages.length;
      _pages.addAll([
        ChatPage(type: ChatPageType.mobileMain),
        ServerPage(),
        ClientDownloadPage(),
      ]);
    }
    _pages.add(SettingsPage());
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
        onWillPop: () async {
          if (_selectedIndex != 0) {
            setState(() {
              _selectedIndex = 0;
            });
          } else {
            return true;
          }
          return false;
        },
        child: Scaffold(
          // backgroundColor: MyTheme.grayBg,
          appBar: AppBar(
            centerTitle: true,
            title: appTitle(),
            actions: _pages.elementAt(_selectedIndex).appBarActions,
          ),
          bottomNavigationBar: AnimatedBuilder(
            animation: gFFI.serverModel,
            builder: (context, child) => Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildServerStatus(gFFI.serverModel.connectStatus),
                child!,
              ],
            ),
            child: BottomNavigationBar(
              key: navigationBarKey,
              items: _pages
                  .map((page) => BottomNavigationBarItem(
                      icon: page.icon, label: page.title))
                  .toList(),
              currentIndex: _selectedIndex,
              type: BottomNavigationBarType.fixed,
              selectedItemColor: MyTheme.accent,
              unselectedItemColor: MyTheme.darkGray,
              onTap: (index) => setState(() {
                // close chat overlay when go chat page
                if (_selectedIndex != index) {
                  _selectedIndex = index;
                  if (isChatPageCurrentTab) {
                    gFFI.chatModel.hideChatIconOverlay();
                    gFFI.chatModel.hideChatWindowOverlay();
                    gFFI.chatModel.mobileClearClientUnread(
                        gFFI.chatModel.currentKey.connId);
                  }
                }
              }),
            ),
          ),
          body: _pages.elementAt(_selectedIndex),
        ));
  }

  Widget _buildServerStatus(int status) {
    final Color color;
    final String message;
    if (status > 0) {
      color = const Color.fromARGB(255, 50, 190, 166);
      message = translate('server_ready_status');
    } else if (status == 0) {
      color = kColorWarn;
      message = translate('server_connecting_status');
    } else {
      color = const Color.fromARGB(255, 224, 79, 95);
      message = translate('server_offline_status');
    }
    final foreground =
        Theme.of(context).colorScheme.onSurface.withOpacity(0.72);
    return Container(
      height: 24,
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        border: Border(
          top: BorderSide(color: foreground.withOpacity(0.12), width: 0.5),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 7),
          Expanded(
            child: Text(
              message,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 12, color: foreground),
            ),
          ),
          if (status <= 0) ...[
            const SizedBox(width: 6),
            InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: gFFI.serverModel.isReconnectingRendezvous
                  ? null
                  : gFFI.serverModel.reconnectRendezvous,
              child: Container(
                height: 20,
                constraints: const BoxConstraints(minWidth: 48),
                padding: const EdgeInsets.symmetric(horizontal: 7),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: color.withOpacity(0.65), width: 1),
                ),
                child: gFFI.serverModel.isReconnectingRendezvous
                    ? SizedBox(
                        width: 12,
                        height: 12,
                        child: CircularProgressIndicator(
                          strokeWidth: 1.5,
                          color: color,
                        ),
                      )
                    : Text(
                        '重连',
                        style: TextStyle(
                          fontSize: 11,
                          height: 1,
                          color: color,
                        ),
                      ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget appTitle() {
    final currentUser = gFFI.chatModel.currentUser;
    final currentKey = gFFI.chatModel.currentKey;
    if (isChatPageCurrentTab &&
        currentUser != null &&
        currentKey.peerId.isNotEmpty) {
      final connected =
          gFFI.serverModel.clients.any((e) => e.id == currentKey.connId);
      return Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Tooltip(
            message: currentKey.isOut
                ? translate('Outgoing connection')
                : translate('Incoming connection'),
            child: Icon(
              currentKey.isOut
                  ? Icons.call_made_rounded
                  : Icons.call_received_rounded,
            ),
          ),
          Expanded(
            child: Center(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    "${currentUser.firstName}   ${currentUser.id}",
                  ),
                  if (connected)
                    Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Color.fromARGB(255, 133, 246, 199)),
                    ).marginSymmetric(horizontal: 2),
                ],
              ),
            ),
          ),
        ],
      );
    }
    final titleColor = Theme.of(context).appBarTheme.titleTextStyle?.color ??
        Theme.of(context).colorScheme.onSurface;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Text(kKemiPadAppTitle),
        if (_appVersion.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(left: 6, top: 3),
            child: Text(
              'v$_appVersion',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w400,
                color: titleColor.withOpacity(0.6),
              ),
            ),
          ),
      ],
    );
  }
}

class WebHomePage extends StatelessWidget {
  final connectionPage =
      ConnectionPage(appBarActions: <Widget>[const WebSettingsPage()]);

  @override
  Widget build(BuildContext context) {
    stateGlobal.isInMainPage = true;
    handleUnilink(context);
    return Scaffold(
      // backgroundColor: MyTheme.grayBg,
      appBar: AppBar(
        centerTitle: true,
        title: Text('$kKemiPadAppTitle (Preview)'),
        actions: connectionPage.appBarActions,
      ),
      body: connectionPage,
    );
  }

  handleUnilink(BuildContext context) {
    if (webInitialLink.isEmpty) {
      return;
    }
    final link = webInitialLink;
    webInitialLink = '';
    final splitter = ["/#/", "/#", "#/", "#"];
    var fakelink = '';
    for (var s in splitter) {
      if (link.contains(s)) {
        var list = link.split(s);
        if (list.length < 2 || list[1].isEmpty) {
          return;
        }
        list.removeAt(0);
        fakelink = "rustdesk://${list.join(s)}";
        break;
      }
    }
    if (fakelink.isEmpty) {
      return;
    }
    final uri = Uri.tryParse(fakelink);
    if (uri == null) {
      return;
    }
    final args = urlLinkToCmdArgs(uri);
    if (args == null || args.isEmpty) {
      return;
    }
    bool isFileTransfer = false;
    bool isViewCamera = false;
    bool isTerminal = false;
    String? id;
    String? password;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case '--connect':
        case '--play':
          id = args[i + 1];
          i++;
          break;
        case '--file-transfer':
          isFileTransfer = true;
          id = args[i + 1];
          i++;
          break;
        case '--view-camera':
          isViewCamera = true;
          id = args[i + 1];
          i++;
          break;
        case '--terminal':
          isTerminal = true;
          id = args[i + 1];
          i++;
          break;
        case '--terminal-admin':
          setEnvTerminalAdmin();
          isTerminal = true;
          id = args[i + 1];
          i++;
          break;
        case '--password':
          password = args[i + 1];
          i++;
          break;
        default:
          break;
      }
    }
    if (id != null) {
      connect(context, id,
          isFileTransfer: isFileTransfer,
          isViewCamera: isViewCamera,
          isTerminal: isTerminal,
          password: password);
    }
  }
}
