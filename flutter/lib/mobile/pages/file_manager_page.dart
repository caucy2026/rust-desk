import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_breadcrumb/flutter_breadcrumb.dart';
import 'package:flutter_hbb/models/file_model.dart';
import 'package:flutter_hbb/models/transfer_history_model.dart';
import 'package:get/get.dart';
import 'package:toggle_switch/toggle_switch.dart';
import 'package:uuid/uuid.dart';

import '../../common.dart';
import '../../common/widgets/dialog.dart';
import '../../models/model.dart';
import '../../models/platform_model.dart';
import 'remote_page.dart';

class FileManagerPage extends StatefulWidget {
  FileManagerPage(
      {Key? key,
      required this.id,
      this.password,
      this.isSharedPassword,
      this.forceRelay,
      this.connToken,
      this.returnToRemoteOnClose = false,
      this.isOverlay = false,
      this.onOverlayClose})
      : super(key: key);
  final String id;
  final String? password;
  final bool? isSharedPassword;
  final bool? forceRelay;
  final String? connToken;
  final bool returnToRemoteOnClose;

  /// When true, renders as a floating card instead of a full-page Scaffold.
  /// Used when shown as an overlay on top of the remote desktop.
  final bool isOverlay;
  final Future<void> Function()? onOverlayClose;

  @override
  State<StatefulWidget> createState() => _FileManagerPageState();
}

enum SelectMode { local, remote, none }

extension SelectModeEq on SelectMode {
  bool eq(bool? currentIsLocal) {
    if (currentIsLocal == null) {
      return false;
    }
    if (currentIsLocal) {
      return this == SelectMode.local;
    } else {
      return this == SelectMode.remote;
    }
  }
}

extension SelectModeExt on Rx<SelectMode> {
  void toggle(bool currentIsLocal) {
    switch (value) {
      case SelectMode.local:
        value = SelectMode.none;
        break;
      case SelectMode.remote:
        value = SelectMode.none;
        break;
      case SelectMode.none:
        if (currentIsLocal) {
          value = SelectMode.local;
        } else {
          value = SelectMode.remote;
        }
        break;
    }
  }
}

class _FileManagerPageState extends State<FileManagerPage> {
  late final FFI _ffi;
  late final FileModel model;
  late final TransferHistoryStore _historyStore;
  final selectMode = SelectMode.none.obs;
  bool _navigatingBackToRemote = false;
  bool _showHistory = false;
  bool _connectionClosed = false;
  bool _crossDisplayClosing = false;
  final Set<String> _repeatingHistoryItems = {};

  var showLocal = true;

  FileController get currentFileController =>
      showLocal ? model.localController : model.remoteController;
  FileDirectory get currentDir => currentFileController.directory.value;
  DirectoryOptions get currentOptions => currentFileController.options.value;
  final _uniqueKey = UniqueKey();

  // PAD requires a read-only transfer experience in this build.
  bool get _deleteEnabled => !isAndroid;

  bool _useDualPane(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    return isAndroid && size.width >= 720 && size.width > size.height;
  }

  @override
  void initState() {
    super.initState();
    _ffi = widget.isOverlay ? FFI(Uuid().v4obj()) : gFFI;
    model = _ffi.fileModel;
    _historyStore = TransferHistoryStore(widget.id)..load();
    model.jobController.onTransferJobChanged = _historyStore.updateFromJob;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      try {
        debugPrint(
            '[FileManagerPage] init start id=${widget.id} session=${_ffi.sessionId}');
        _ffi.start(widget.id,
            isFileTransfer: true,
            password: widget.password,
            isSharedPassword: widget.isSharedPassword,
            forceRelay: widget.forceRelay,
            connToken: widget.connToken);
        debugPrint('[FileManagerPage] after start session=${_ffi.sessionId}');
        _ffi.dialogManager
            .showLoading(translate('Connecting...'), onCancel: closeConnection);
        _ffi.ffiModel.updateEventListener(_ffi.sessionId, widget.id);
        WakelockManager.enable(_uniqueKey);
      } catch (e) {
        debugPrint('FileManagerPage initState error: $e');
        if (mounted) {
          _ffi.dialogManager.dismissAll();
          showToast(translate('Failed'));
        }
      }
    });
  }

  @override
  void dispose() {
    model.jobController.onTransferJobChanged = null;
    if (_connectionClosed) {
      _ffi.dialogManager.dismissAll();
      WakelockManager.disable(_uniqueKey);
    } else {
      model.close().whenComplete(() {
        if (widget.isOverlay || !_navigatingBackToRemote) {
          _ffi.close();
        }
        _ffi.dialogManager.dismissAll();
        WakelockManager.disable(_uniqueKey);
      });
    }
    model.jobController.clear();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final dualPane = _useDualPane(context);
    final content = WillPopScope(
        onWillPop: () async {
          if (_showHistory) {
            setState(() => _showHistory = false);
          } else if (selectMode.value != SelectMode.none) {
            selectMode.value = SelectMode.none;
            setState(() {});
          } else {
            currentFileController.goBack();
          }
          return false;
        },
        child: Scaffold(
          backgroundColor: widget.isOverlay ? Colors.transparent : null,
          appBar: AppBar(
            leading: Row(children: [
              IconButton(
                  icon: Icon(widget.isOverlay
                      ? Icons.close
                      : widget.returnToRemoteOnClose
                          ? Icons.desktop_windows_outlined
                          : Icons.close),
                  tooltip: widget.isOverlay
                      ? translate('Close')
                      : widget.returnToRemoteOnClose
                          ? translate('Back')
                          : translate('Close'),
                  onPressed: () async {
                    if (widget.isOverlay) {
                      if (widget.onOverlayClose != null) {
                        await _closeCrossDisplayWindow();
                      } else {
                        // Same-display fallback: dismiss the dialog. The
                        // parent RemotePage continues owning the video session.
                        Navigator.of(context).pop();
                      }
                    } else if (widget.returnToRemoteOnClose) {
                      await _backToRemoteDesktop();
                    } else {
                      clientClose(_ffi.sessionId, _ffi);
                    }
                  }),
            ]),
            centerTitle: true,
            title: _showHistory
                ? const Text('传输记录')
                : dualPane
                    ? const Text('PAD文件 ⇄ 远端文件')
                    : ToggleSwitch(
                        initialLabelIndex: showLocal ? 0 : 1,
                        activeBgColor: [MyTheme.idColor],
                        inactiveBgColor:
                            Theme.of(context).brightness == Brightness.light
                                ? MyTheme.grayBg
                                : null,
                        inactiveFgColor:
                            Theme.of(context).brightness == Brightness.light
                                ? Colors.black54
                                : null,
                        totalSwitches: 2,
                        minWidth: 100,
                        fontSize: 15,
                        iconSize: 18,
                        labels: [translate("Local"), translate("Remote")],
                        icons: [Icons.phone_android_sharp, Icons.screen_share],
                        onToggle: (index) {
                          final current = showLocal ? 0 : 1;
                          if (index != current) {
                            setState(() => showLocal = !showLocal);
                          }
                        },
                      ),
            actions: [
              Obx(() {
                final count = _historyStore.groups
                    .fold<int>(0, (total, group) => total + group.items.length);
                return TextButton.icon(
                  onPressed: _toggleHistory,
                  style: TextButton.styleFrom(foregroundColor: Colors.white),
                  icon: Icon(_showHistory ? Icons.arrow_back : Icons.history,
                      size: 20),
                  label: Text(_showHistory
                      ? '返回文件'
                      : count > 0
                          ? '记录($count)'
                          : '记录'),
                );
              }),
              if (!widget.isOverlay && !_showHistory)
                PopupMenuButton<String>(
                    tooltip: "",
                    icon: Icon(Icons.more_vert),
                    itemBuilder: (context) {
                      return [
                        PopupMenuItem(
                          child: Row(
                            children: [
                              Icon(Icons.refresh,
                                  color: Theme.of(context).iconTheme.color),
                              SizedBox(width: 5),
                              Text(translate("Refresh File"))
                            ],
                          ),
                          value: "refresh",
                        ),
                        PopupMenuItem(
                          enabled: currentDir.path != "/",
                          child: Row(
                            children: [
                              Icon(Icons.check,
                                  color: Theme.of(context).iconTheme.color),
                              SizedBox(width: 5),
                              Text(translate("Multi Select"))
                            ],
                          ),
                          value: "select",
                        ),
                        PopupMenuItem(
                          enabled: currentDir.path != "/",
                          child: Row(
                            children: [
                              Icon(Icons.folder_outlined,
                                  color: Theme.of(context).iconTheme.color),
                              SizedBox(width: 5),
                              Text(translate("Create Folder"))
                            ],
                          ),
                          value: "folder",
                        ),
                        PopupMenuItem(
                          enabled: currentDir.path != "/",
                          child: Row(
                            children: [
                              Icon(
                                  currentOptions.showHidden
                                      ? Icons.check_box_outlined
                                      : Icons.check_box_outline_blank,
                                  color: Theme.of(context).iconTheme.color),
                              SizedBox(width: 5),
                              Text(translate("Show Hidden Files"))
                            ],
                          ),
                          value: "hidden",
                        )
                      ];
                    },
                    onSelected: (v) {
                      if (v == "refresh") {
                        currentFileController.refresh();
                      } else if (v == "select") {
                        model.localController.selectedItems.clear();
                        model.remoteController.selectedItems.clear();
                        selectMode.toggle(showLocal);
                        setState(() {});
                      } else if (v == "folder") {
                        final name = TextEditingController();
                        String? errorText;
                        _ffi.dialogManager.show((setState, close, context) {
                          name.addListener(() {
                            if (errorText != null) {
                              setState(() {
                                errorText = null;
                              });
                            }
                          });
                          return CustomAlertDialog(
                              title: Text(translate("Create Folder")),
                              content: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  TextFormField(
                                    decoration: InputDecoration(
                                      labelText: translate(
                                          "Please enter the folder name"),
                                      errorText: errorText,
                                    ),
                                    controller: name,
                                  ).workaroundFreezeLinuxMint(),
                                ],
                              ),
                              actions: [
                                dialogButton("Cancel",
                                    onPressed: () => close(false),
                                    isOutline: true),
                                dialogButton("OK", onPressed: () {
                                  if (name.value.text.isNotEmpty) {
                                    if (!PathUtil.validName(
                                        name.value.text,
                                        currentFileController
                                            .options.value.isWindows)) {
                                      setState(() {
                                        errorText =
                                            translate("Invalid folder name");
                                      });
                                      return;
                                    }
                                    currentFileController.createDir(
                                        PathUtil.join(
                                            currentDir.path,
                                            name.value.text,
                                            currentOptions.isWindows));
                                    close();
                                  }
                                })
                              ]);
                        });
                      } else if (v == "hidden") {
                        currentFileController.toggleShowHidden();
                      }
                    }),
            ],
          ),
          body: _showHistory
              ? _buildTransferHistory()
              : dualPane
                  ? _buildDualPane()
                  : showLocal
                      ? FileManagerView(
                          controller: model.localController,
                          selectMode: selectMode,
                          deleteEnabled: _deleteEnabled,
                        )
                      : FileManagerView(
                          controller: model.remoteController,
                          selectMode: selectMode,
                          deleteEnabled: _deleteEnabled,
                        ),
          bottomSheet: bottomSheet(),
        ));

    if (widget.isOverlay) {
      return SafeArea(
        child: Center(
          child: Container(
            width: MediaQuery.of(context).size.width * (dualPane ? 0.90 : 0.60),
            height:
                MediaQuery.of(context).size.height * (dualPane ? 0.78 : 0.60),
            margin: EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: Theme.of(context).scaffoldBackgroundColor,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                    color: Colors.black38, blurRadius: 24, offset: Offset(0, 8))
              ],
            ),
            clipBehavior: Clip.antiAlias,
            child: content,
          ),
        ),
      );
    }
    return content;
  }

  Widget _buildDualPane() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: _buildFilePane(
              title: 'PAD端',
              icon: Icons.tablet_android,
              controller: model.localController,
            ),
          ),
          SizedBox(
            width: 78,
            child: _buildDualPaneTransferControls(),
          ),
          Expanded(
            child: _buildFilePane(
              title: '远端设备',
              icon: Icons.desktop_windows_outlined,
              controller: model.remoteController,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilePane({
    required String title,
    required IconData icon,
    required FileController controller,
  }) {
    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: Theme.of(context).dividerColor),
        borderRadius: BorderRadius.circular(10),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          Container(
            height: 38,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: Row(
              children: [
                Icon(icon, size: 19, color: MyTheme.idColor),
                const SizedBox(width: 7),
                Text(title,
                    style: const TextStyle(fontWeight: FontWeight.w600)),
                const Spacer(),
                Obx(() => Text(
                      '${controller.selectedItems.items.length}项已选',
                      style: const TextStyle(fontSize: 11, color: Colors.grey),
                    )),
              ],
            ),
          ),
          Expanded(
            child: FileManagerView(
              controller: controller,
              selectMode: selectMode,
              deleteEnabled: _deleteEnabled,
              dualPane: true,
              onSelectionStarted: _activateDualPaneSelection,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDualPaneTransferControls() {
    return Obx(() {
      final localCount = model.localController.selectedItems.items.length;
      final remoteCount = model.remoteController.selectedItems.items.length;
      return Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _dualPaneTransferButton(
            icon: Icons.arrow_forward,
            label: localCount > 0 ? '发送 $localCount' : '发送',
            tooltip: '把PAD选中的文件发送到右侧远端目录',
            onPressed: localCount > 0
                ? () => _transferDualPaneSelection(
                      model.localController,
                      model.remoteController,
                    )
                : null,
          ),
          const SizedBox(height: 18),
          _dualPaneTransferButton(
            icon: Icons.arrow_back,
            label: remoteCount > 0 ? '取回 $remoteCount' : '取回',
            tooltip: '把远端选中的文件传到左侧PAD目录',
            onPressed: remoteCount > 0
                ? () => _transferDualPaneSelection(
                      model.remoteController,
                      model.localController,
                    )
                : null,
          ),
        ],
      );
    });
  }

  Widget _dualPaneTransferButton({
    required IconData icon,
    required String label,
    required String tooltip,
    required VoidCallback? onPressed,
  }) {
    return Tooltip(
      message: tooltip,
      child: SizedBox(
        width: 66,
        child: FilledButton(
          onPressed: onPressed,
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 22),
              const SizedBox(height: 3),
              Text(label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 11)),
            ],
          ),
        ),
      ),
    );
  }

  void _activateDualPaneSelection(bool isLocal) {
    selectMode.value = isLocal ? SelectMode.local : SelectMode.remote;
  }

  Future<void> _transferDualPaneSelection(
      FileController source, FileController target) async {
    if (source.selectedItems.items.isEmpty) return;
    await _startRecordedTransfer(
      source,
      source.selectedItems,
      target.directoryData(),
    );
    source.selectedItems.clear();
    if (target.selectedItems.items.isNotEmpty) {
      selectMode.value = target.isLocal ? SelectMode.local : SelectMode.remote;
    } else {
      selectMode.value = SelectMode.none;
    }
  }

  void _toggleHistory() {
    if (!_showHistory) {
      model.localController.selectedItems.clear();
      model.remoteController.selectedItems.clear();
      selectMode.value = SelectMode.none;
    }
    setState(() => _showHistory = !_showHistory);
  }

  Future<void> _closeCrossDisplayWindow() async {
    if (_crossDisplayClosing) return;
    _crossDisplayClosing = true;
    try {
      model.jobController.onTransferJobChanged = null;
      await model.close();
      await _ffi.close();
      _connectionClosed = true;
      await widget.onOverlayClose?.call();
    } catch (error) {
      _crossDisplayClosing = false;
      debugPrint('[FileManagerPage] close cross-display window failed: $error');
      if (mounted) showToast(translate('Failed'));
    }
  }

  Future<void> _startRecordedTransfer(FileController source,
      SelectedItems originalItems, DirectoryData target) async {
    final items = SelectedItems(isLocal: source.isLocal);
    items.items.addAll(originalItems.items);
    if (items.items.isEmpty) return;

    // Persist before dispatch. A small file can finish before the asynchronous
    // completion callback arrives, but it must already be visible in history.
    final bindings = <Entry, TransferHistoryBinding>{};
    for (final entry in items.items) {
      bindings[entry] = _historyStore.registerTransfer(
        entry: entry,
        isRemoteToLocal: !source.isLocal,
        targetDir: target.directory.path,
        sourceIsWindows: source.options.value.isWindows,
        targetIsWindows: target.options.isWindows,
        showHidden: target.options.showHidden,
      );
    }

    try {
      await source.sendFiles(
        items,
        target,
        onJobCreated: (entry, jobId) {
          final binding = bindings[entry];
          if (binding != null) _historyStore.bindJob(jobId, binding);
        },
      );
    } catch (error) {
      debugPrint('[TransferHistory] start transfer failed: $error');
      if (mounted) showToast('传输启动失败：$error');
    }
  }

  Future<void> _repeatTransfer(
      TransferHistoryGroup group, TransferHistoryItem historyItem) async {
    final repeatKey = '${group.key}|${historyItem.sourcePath}';
    if (_repeatingHistoryItems.contains(repeatKey)) return;
    setState(() => _repeatingHistoryItems.add(repeatKey));

    final source =
        group.isRemoteToLocal ? model.remoteController : model.localController;
    final target =
        group.isRemoteToLocal ? model.localController : model.remoteController;

    try {
      await target.fileFetcher
          .fetchDirectory(group.targetDir, target.isLocal, group.showHidden);
    } catch (error) {
      _historyStore.setValidationError(
          group, historyItem, '目标文件夹不存在或无法访问：${group.targetDir}');
      return _finishRepeating(repeatKey);
    }

    Entry? sourceEntry;
    try {
      final sourceDirectory = await source.fileFetcher
          .fetchDirectory(group.sourceDir, source.isLocal, group.showHidden);
      sourceDirectory.format(group.sourceIsWindows);
      sourceEntry = sourceDirectory.entries.firstWhereOrNull((entry) =>
          _samePath(entry.path, historyItem.sourcePath, group.sourceIsWindows));
    } catch (error) {
      debugPrint('[TransferHistory] validate source failed: $error');
    }
    if (sourceEntry == null) {
      _historyStore.setValidationError(
          group, historyItem, '源文件不存在：${historyItem.sourcePath}');
      return _finishRepeating(repeatKey);
    }

    final selectedItems = SelectedItems(isLocal: source.isLocal)
      ..add(sourceEntry);
    final targetDirectory = FileDirectory()..path = group.targetDir;
    final targetOptions = DirectoryOptions(
      isWindows: group.targetIsWindows,
      showHidden: group.showHidden,
    );
    await _startRecordedTransfer(
        source, selectedItems, DirectoryData(targetDirectory, targetOptions));
    _finishRepeating(repeatKey);
  }

  void _finishRepeating(String repeatKey) {
    if (!mounted) return;
    setState(() => _repeatingHistoryItems.remove(repeatKey));
  }

  bool _samePath(String first, String second, bool isWindowsPath) {
    String normalize(String value) {
      var result = value.trim();
      if (isWindowsPath) {
        result = result.replaceAll('/', '\\').toLowerCase();
        while (result.length > 3 && result.endsWith('\\')) {
          result = result.substring(0, result.length - 1);
        }
      } else {
        result = result.replaceAll('\\', '/');
        while (result.length > 1 && result.endsWith('/')) {
          result = result.substring(0, result.length - 1);
        }
      }
      return result;
    }

    return normalize(first) == normalize(second);
  }

  Widget _buildTransferHistory() {
    return Obx(() {
      final groups = _historyStore.groups;
      if (groups.isEmpty) {
        return Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: const [
                Icon(Icons.history, size: 56, color: Colors.grey),
                SizedBox(height: 12),
                Text('暂无传输记录',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                SizedBox(height: 6),
                Text('完成一次文件传输后，可在这里一键再次传输',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey)),
                SizedBox(height: 8),
                Text('点击右上角“返回文件”继续浏览',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey)),
              ],
            ),
          ),
        );
      }

      return ListView.builder(
        padding: const EdgeInsets.fromLTRB(10, 8, 10, 18),
        itemCount: groups.length + 1,
        itemBuilder: (context, groupIndex) {
          if (groupIndex == 0) return _historyReturnHint();
          final group = groups[groupIndex - 1];
          return Card(
            margin: const EdgeInsets.only(bottom: 10),
            clipBehavior: Clip.antiAlias,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  color: MyTheme.idColor.withOpacity(0.08),
                  padding: const EdgeInsets.fromLTRB(12, 9, 12, 9),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: MyTheme.idColor.withOpacity(0.14),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          group.isRemoteToLocal ? '对方 → PAD' : 'PAD → 对方',
                          style: TextStyle(
                              color: MyTheme.idColor,
                              fontSize: 12,
                              fontWeight: FontWeight.w600),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _historyPathLine('源目录', group.sourceDir),
                            const SizedBox(height: 3),
                            _historyPathLine('目标目录', group.targetDir),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                for (var itemIndex = 0;
                    itemIndex < group.items.length;
                    itemIndex++)
                  _buildTransferHistoryItem(
                      group, group.items[itemIndex], itemIndex),
              ],
            ),
          );
        },
      );
    });
  }

  Widget _historyReturnHint() {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(
        color: MyTheme.idColor.withOpacity(0.08),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(Icons.info_outline, size: 18, color: MyTheme.idColor),
          const SizedBox(width: 7),
          const Expanded(child: Text('点击右上角“返回文件”继续浏览和传输')),
        ],
      ),
    );
  }

  Widget _historyPathLine(String label, String path) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 54,
          child: Text('$label：',
              style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ),
        Expanded(
          child: Text(path,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12)),
        ),
      ],
    );
  }

  Widget _buildTransferHistoryItem(
      TransferHistoryGroup group, TransferHistoryItem item, int itemIndex) {
    final repeatKey = '${group.key}|${item.sourcePath}';
    final isChecking = _repeatingHistoryItems.contains(repeatKey);
    final isError = item.lastStatus == TransferHistoryStatus.error;
    final status = item.lastStatus == TransferHistoryStatus.inProgress
        ? '传输中'
        : isError
            ? '失败'
            : '已完成';
    final statusColor = item.lastStatus == TransferHistoryStatus.inProgress
        ? Colors.orange
        : isError
            ? Colors.red
            : Colors.green;
    final size = item.isDirectory || item.size <= 0
        ? ''
        : ' · ${readableFileSize(item.size.toDouble())}';

    return Container(
      decoration: itemIndex == 0
          ? null
          : const BoxDecoration(
              border: Border(top: BorderSide(color: Color(0x14000000)))),
      padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Icon(item.isDirectory ? Icons.folder_outlined : Icons.description,
              size: 28, color: item.isDirectory ? Colors.amber[700] : null),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w500)),
                const SizedBox(height: 2),
                Wrap(
                  spacing: 5,
                  children: [
                    Text(status,
                        style: TextStyle(fontSize: 11, color: statusColor)),
                    Text('已传输 ${item.transferCount} 次$size',
                        style:
                            const TextStyle(fontSize: 11, color: Colors.grey)),
                    if (item.lastTransferredAt > 0)
                      Text(_formatHistoryTime(item.lastTransferredAt),
                          style: const TextStyle(
                              fontSize: 11, color: Colors.grey)),
                  ],
                ),
                if (item.lastError.isNotEmpty) ...[
                  const SizedBox(height: 3),
                  Text(item.lastError,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 11, color: Colors.red)),
                ],
              ],
            ),
          ),
          const SizedBox(width: 6),
          SizedBox(
            width: 94,
            child: OutlinedButton(
              onPressed: isChecking ? null : () => _repeatTransfer(group, item),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                minimumSize: const Size(88, 36),
              ),
              child: isChecking
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('再次传输', style: TextStyle(fontSize: 12)),
            ),
          ),
          IconButton(
            tooltip: '删除记录',
            visualDensity: VisualDensity.compact,
            icon: const Icon(Icons.close, size: 18, color: Colors.grey),
            onPressed:
                isChecking ? null : () => _historyStore.removeItem(group, item),
          ),
        ],
      ),
    );
  }

  String _formatHistoryTime(int milliseconds) {
    final time = DateTime.fromMillisecondsSinceEpoch(milliseconds);
    String two(int value) => value.toString().padLeft(2, '0');
    final now = DateTime.now();
    if (now.year == time.year &&
        now.month == time.month &&
        now.day == time.day) {
      return '今天 ${two(time.hour)}:${two(time.minute)}';
    }
    return '${two(time.month)}-${two(time.day)} '
        '${two(time.hour)}:${two(time.minute)}';
  }

  Widget? bottomSheet() {
    return Obx(() {
      final dualPane = _useDualPane(context);
      final selectedItems = dualPane ? null : getActiveSelectedItems();
      final jobTable = model.jobController.jobTable;

      final localLabel = selectedItems?.isLocal == null
          ? ""
          : " [${selectedItems!.isLocal ? translate("Local") : translate("Remote")}]";
      if (!dualPane && !(selectMode.value == SelectMode.none)) {
        final selectedItemsLen =
            "${selectedItems?.items.length ?? 0} ${translate("items")}";
        if (selectedItems == null ||
            selectedItems.items.isEmpty ||
            selectMode.value.eq(showLocal)) {
          return BottomSheetBody(
              leading: Icon(Icons.check),
              title: translate("Selected"),
              text: selectedItemsLen + localLabel,
              onCanceled: () {
                selectedItems?.items.clear();
                selectMode.value = SelectMode.none;
                setState(() {});
              },
              actions: [
                IconButton(
                  icon: Icon(Icons.compare_arrows),
                  onPressed: () => setState(() => showLocal = !showLocal),
                ),
                IconButton(
                  icon: Icon(Icons.delete_forever),
                  onPressed: _deleteEnabled && selectedItems != null
                      ? () async {
                          if (selectedItems.items.isNotEmpty) {
                            await currentFileController
                                .removeAction(selectedItems);
                            selectedItems.items.clear();
                            selectMode.value = SelectMode.none;
                          }
                        }
                      : null,
                )
              ]);
        } else {
          return BottomSheetBody(
              leading: Icon(Icons.input),
              title: translate("Paste here?"),
              text: selectedItemsLen + localLabel,
              onCanceled: () {
                selectedItems.items.clear();
                selectMode.value = SelectMode.none;
                setState(() {});
              },
              actions: [
                IconButton(
                  icon: Icon(Icons.compare_arrows),
                  onPressed: () => setState(() => showLocal = !showLocal),
                ),
                IconButton(
                  icon: Icon(Icons.paste),
                  onPressed: () async {
                    selectMode.value = SelectMode.none;
                    final otherSide = showLocal
                        ? model.remoteController
                        : model.localController;
                    final thisSideData =
                        DirectoryData(currentDir, currentOptions);
                    await _startRecordedTransfer(
                        otherSide, selectedItems, thisSideData);
                    selectedItems.items.clear();
                    selectMode.value = SelectMode.none;
                  },
                )
              ]);
        }
      }

      if (jobTable.isEmpty) {
        return Offstage();
      }

      // Find the first job that is in progress (the one actually transferring data)
      // Rust backend processes jobs sequentially, so the first inProgress job is the active one
      final activeJob = jobTable
              .firstWhereOrNull((job) => job.state == JobState.inProgress) ??
          jobTable.last;

      switch (activeJob.state) {
        case JobState.inProgress:
          return BottomSheetBody(
            leading: CircularProgressIndicator(),
            title: translate("Waiting"),
            text:
                "${translate("Speed")}:  ${readableFileSize(activeJob.speed)}/s",
            onCanceled: () {
              model.jobController.cancelJob(activeJob.id);
              jobTable.clear();
            },
          );
        case JobState.done:
          return BottomSheetBody(
            leading: Icon(Icons.check),
            title: "${translate("Successful")}!",
            text: activeJob.display(),
            onCanceled: () => jobTable.clear(),
          );
        case JobState.error:
          return BottomSheetBody(
            leading: Icon(Icons.error),
            title: "${translate("Error")}!",
            text: activeJob.err,
            onCanceled: () => jobTable.clear(),
          );
        case JobState.none:
          break;
        case JobState.paused:
          // TODO: Handle this case.
          break;
      }
      return Offstage();
    });
  }

  SelectedItems? getActiveSelectedItems() {
    final localSelectedItems = model.localController.selectedItems;
    final remoteSelectedItems = model.remoteController.selectedItems;

    if (localSelectedItems.items.isNotEmpty &&
        remoteSelectedItems.items.isNotEmpty) {
      // assert unreachable
      debugPrint("Wrong SelectedItems state, reset");
      localSelectedItems.clear();
      remoteSelectedItems.clear();
    }

    if (localSelectedItems.items.isEmpty && remoteSelectedItems.items.isEmpty) {
      return null;
    }

    if (localSelectedItems.items.length > remoteSelectedItems.items.length) {
      return localSelectedItems;
    } else {
      return remoteSelectedItems;
    }
  }
}

class FileManagerView extends StatefulWidget {
  final FileController controller;
  final Rx<SelectMode> selectMode;
  final bool deleteEnabled;
  final bool dualPane;
  final ValueChanged<bool>? onSelectionStarted;

  FileManagerView(
      {required this.controller,
      required this.selectMode,
      required this.deleteEnabled,
      this.dualPane = false,
      this.onSelectionStarted});

  @override
  State<StatefulWidget> createState() => _FileManagerViewState();
}

class _FileManagerViewState extends State<FileManagerView> {
  final _listScrollController = ScrollController();
  final _breadCrumbScroller = ScrollController();
  late final ascending = Rx<bool>(controller.sortAscending);

  bool get isLocal => widget.controller.isLocal;
  FileController get controller => widget.controller;
  SelectedItems get _selectedItems => widget.controller.selectedItems;

  @override
  void initState() {
    super.initState();
    controller.directory.listen((e) => breadCrumbScrollToEnd());
  }

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      headTools(),
      Expanded(child: Obx(() {
        final entries = controller.directory.value.entries;
        return ListView.builder(
          controller: _listScrollController,
          itemCount: entries.length + 1,
          itemBuilder: (context, index) {
            if (index >= entries.length) {
              return listTail();
            }
            final selected = _selectedItems.items.contains(entries[index]);

            final sizeStr = entries[index].isFile
                ? readableFileSize(entries[index].size.toDouble())
                : "";

            final showCheckBox = () {
              return widget.dualPane ||
                  (widget.selectMode.value != SelectMode.none &&
                      widget.selectMode.value
                          .eq(controller.selectedItems.isLocal));
            }();
            return Card(
              child: ListTile(
                leading: entries[index].isDrive
                    ? Padding(
                        padding: EdgeInsets.symmetric(vertical: 8),
                        child: Image(
                            image: iconHardDrive,
                            fit: BoxFit.scaleDown,
                            color: Theme.of(context)
                                .iconTheme
                                .color
                                ?.withOpacity(0.7)))
                    : Icon(
                        entries[index].isFile
                            ? Icons.feed_outlined
                            : Icons.folder,
                        size: 40),
                title: Text(entries[index].name),
                selected: selected,
                subtitle: entries[index].isDrive
                    ? null
                    : Text(
                        "${entries[index].lastModified().toString().replaceAll(".000", "")}   $sizeStr",
                        style: TextStyle(fontSize: 12, color: MyTheme.darkGray),
                      ),
                trailing: entries[index].isDrive
                    ? null
                    : widget.dualPane
                        ? Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Checkbox(
                                value: selected,
                                onChanged: (v) {
                                  if (v == null) return;
                                  _setDualPaneSelection(entries[index], v);
                                },
                              ),
                              _buildEntryMenu(entries[index]),
                            ],
                          )
                        : showCheckBox
                            ? Checkbox(
                                value: selected,
                                onChanged: (v) {
                                  if (v == null) return;
                                  if (v && !selected) {
                                    _selectedItems.add(entries[index]);
                                  } else if (!v && selected) {
                                    _selectedItems.remove(entries[index]);
                                  }
                                  setState(() {});
                                })
                            : _buildEntryMenu(entries[index]),
                onTap: () {
                  if (widget.dualPane && entries[index].isFile) {
                    _setDualPaneSelection(entries[index], !selected);
                    return;
                  }
                  if (showCheckBox && !widget.dualPane) {
                    if (selected) {
                      _selectedItems.remove(entries[index]);
                    } else {
                      _selectedItems.add(entries[index]);
                    }
                    setState(() {});
                    return;
                  }
                  if (entries[index].isDirectory || entries[index].isDrive) {
                    controller.openDirectory(entries[index].path);
                  }
                },
                onLongPress: entries[index].isDrive
                    ? null
                    : () {
                        if (widget.dualPane) {
                          _setDualPaneSelection(entries[index], !selected);
                          return;
                        }
                        _selectedItems.clear();
                        widget.selectMode.toggle(isLocal);
                        if (widget.selectMode.value != SelectMode.none) {
                          _selectedItems.add(entries[index]);
                        }
                        setState(() {});
                      },
              ),
            );
          },
        );
      }))
    ]);
  }

  Widget _buildEntryMenu(Entry entry) {
    return PopupMenuButton<String>(
        tooltip: "",
        icon: Icon(Icons.more_vert),
        itemBuilder: (context) {
          return [
            PopupMenuItem(
              enabled: widget.deleteEnabled,
              child: Text(translate("Delete")),
              value: "delete",
            ),
            PopupMenuItem(
              child: Text(translate("Multi Select")),
              value: "multi_select",
            ),
            PopupMenuItem(
              child: Text(translate("Properties")),
              value: "properties",
              enabled: false,
            ),
            if (!entry.isDrive &&
                versionCmp(
                        controller.rootState.target?.ffiModel.pi.version ?? '',
                        "1.3.0") >=
                    0)
              PopupMenuItem(
                child: Text(translate("Rename")),
                value: "rename",
              )
          ];
        },
        onSelected: (v) {
          if (v == "delete") {
            if (!widget.deleteEnabled) {
              return;
            }
            final items = SelectedItems(isLocal: isLocal);
            items.add(entry);
            controller.removeAction(items);
          } else if (v == "multi_select") {
            _selectedItems.clear();
            if (widget.dualPane) {
              widget.onSelectionStarted?.call(isLocal);
              _selectedItems.add(entry);
            } else {
              widget.selectMode.toggle(isLocal);
            }
            setState(() {});
          } else if (v == "rename") {
            controller.renameAction(entry, isLocal);
          }
        });
  }

  void _setDualPaneSelection(Entry entry, bool selected) {
    if (selected) {
      if (_selectedItems.items.isEmpty) {
        widget.onSelectionStarted?.call(isLocal);
      }
      if (!_selectedItems.items.contains(entry)) {
        _selectedItems.add(entry);
      }
    } else {
      _selectedItems.remove(entry);
      if (_selectedItems.items.isEmpty) {
        widget.selectMode.value = SelectMode.none;
      }
    }
    setState(() {});
  }

  void breadCrumbScrollToEnd() {
    Future.delayed(Duration(milliseconds: 200), () {
      if (_breadCrumbScroller.hasClients) {
        _breadCrumbScroller.animateTo(
            _breadCrumbScroller.position.maxScrollExtent,
            duration: Duration(milliseconds: 200),
            curve: Curves.fastLinearToSlowEaseIn);
      }
    });
  }

  Widget headTools() => Container(
          child: Row(
        children: [
          Expanded(child: Obx(() {
            final home = controller.options.value.home;
            final isWindows = controller.options.value.isWindows;
            return BreadCrumb(
              items: getPathBreadCrumbItems(controller.shortPath, isWindows,
                  () => controller.goToHomeDirectory(), (list) {
                var path = "";
                if (home.startsWith(list[0])) {
                  // absolute path
                  for (var item in list) {
                    path = PathUtil.join(path, item, isWindows);
                  }
                } else {
                  path += home;
                  for (var item in list) {
                    path = PathUtil.join(path, item, isWindows);
                  }
                }
                controller.openDirectory(path);
              }),
              divider: Icon(Icons.chevron_right),
              overflow: ScrollableOverflow(controller: _breadCrumbScroller),
            );
          })),
          Row(
            children: [
              IconButton(
                icon: Icon(Icons.arrow_back),
                onPressed: controller.goBack,
              ),
              IconButton(
                icon: Icon(Icons.arrow_upward),
                onPressed: controller.goToParentDirectory,
              ),
              PopupMenuButton<SortBy>(
                  tooltip: "",
                  icon: Icon(Icons.sort),
                  itemBuilder: (context) {
                    return SortBy.values
                        .map((e) => PopupMenuItem(
                              child: Text(translate(e.toString())),
                              value: e,
                            ))
                        .toList();
                  },
                  onSelected: (sortBy) {
                    // If selecting the same sort option, flip the order
                    // If selecting a different sort option, use ascending order
                    if (controller.sortBy.value == sortBy) {
                      ascending.value = !controller.sortAscending;
                    } else {
                      ascending.value = true;
                    }
                    controller.changeSortStyle(sortBy,
                        ascending: ascending.value);
                  }),
            ],
          )
        ],
      ));

  Widget listTail() => Obx(() => Container(
        height: 100,
        child: Column(
          children: [
            Padding(
              padding: EdgeInsets.fromLTRB(30, 5, 30, 0),
              child: Text(
                controller.directory.value.path,
                style: TextStyle(color: MyTheme.darkGray),
              ),
            ),
            Padding(
              padding: EdgeInsets.all(2),
              child: Text(
                "${translate("Total")}: ${controller.directory.value.entries.length} ${translate("items")}",
                style: TextStyle(color: MyTheme.darkGray),
              ),
            )
          ],
        ),
      ));

  List<BreadCrumbItem> getPathBreadCrumbItems(String shortPath, bool isWindows,
      void Function() onHome, void Function(List<String>) onPressed) {
    final list = PathUtil.split(shortPath, isWindows);
    final breadCrumbList = [
      BreadCrumbItem(
          content: IconButton(
        icon: Icon(Icons.home_filled),
        onPressed: onHome,
      ))
    ];
    breadCrumbList.addAll(list.asMap().entries.map((e) => BreadCrumbItem(
        content: TextButton(
            child: Text(e.value),
            style:
                ButtonStyle(minimumSize: MaterialStateProperty.all(Size(0, 0))),
            onPressed: () => onPressed(list.sublist(0, e.key + 1))))));
    return breadCrumbList;
  }
}

class BottomSheetBody extends StatelessWidget {
  BottomSheetBody(
      {required this.leading,
      required this.title,
      required this.text,
      this.onCanceled,
      this.actions});

  final Widget leading;
  final String title;
  final String text;
  final VoidCallback? onCanceled;
  final List<IconButton>? actions;

  @override
  BottomSheet build(BuildContext context) {
    // ignore: no_leading_underscores_for_local_identifiers
    final _actions = actions ?? [];
    return BottomSheet(
      builder: (BuildContext context) {
        return Container(
            height: 65,
            alignment: Alignment.centerLeft,
            decoration: BoxDecoration(
                color: MyTheme.accent50,
                borderRadius: BorderRadius.vertical(top: Radius.circular(10))),
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: 15),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      leading,
                      SizedBox(width: 16),
                      Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(title, style: TextStyle(fontSize: 18)),
                          Text(text,
                              style: TextStyle(fontSize: 14)) // TODO color
                        ],
                      )
                    ],
                  ),
                  Row(children: () {
                    _actions.add(IconButton(
                      icon: Icon(Icons.cancel_outlined),
                      onPressed: onCanceled,
                    ));
                    return _actions;
                  }())
                ],
              ),
            ));
      },
      onClosing: () {},
      // backgroundColor: MyTheme.grayBg,
      enableDrag: false,
    );
  }
}

extension on _FileManagerPageState {
  Future<void> _backToRemoteDesktop() async {
    if (_navigatingBackToRemote) return;
    _navigatingBackToRemote = true;
    try {
      // Capture the connToken before closing the file-transfer session
      // so the new RemotePage can resume the same peer connection.
      final connToken = bind.sessionGetConnToken(sessionId: _ffi.sessionId);
      await _ffi.close();
      if (!mounted) return;
      await Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (BuildContext context) => RemotePage(
            id: widget.id,
            password: widget.password,
            isSharedPassword: widget.isSharedPassword,
            forceRelay: widget.forceRelay,
            connToken: connToken,
          ),
        ),
      );
    } catch (e) {
      _navigatingBackToRemote = false;
      debugPrint('Back to remote desktop failed: $e');
      showToast(translate('Failed'));
    }
  }
}
