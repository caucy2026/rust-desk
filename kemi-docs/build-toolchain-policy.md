# KEMI 编译工具链准则

## 唯一原则

每个项目使用自己的 Flutter SDK，绝不使用系统全局 Flutter、其他项目 SDK 或终端 `PATH` 中碰巧找到的 `flutter`。KEMI 的唯一构建 SDK 是：

```text
/Users/newlink/kemi/RustDesk/client/.toolchains/flutter/bin/flutter
Flutter 3.24.5 / Dart 3.5.4
```

当前 `pubspec.lock` 的 `extended_text 14.0.0` 等依赖与该版本匹配。使用较新的 Flutter 会导致 Material API、选择文本接口及插件依赖不兼容，产出失败或不可复现的包。

## 强制规则

1. 构建脚本必须从项目根目录计算 `.toolchains/flutter/bin/flutter` 的绝对路径，并先检查版本为 3.24.5。
2. 找不到或版本不正确时立即失败；不得回退到 `flutter`、`PATH`、`KEMI_FLUTTER_BIN`、`/Users/newlink/flutter` 或其他目录。
3. Android APK、macOS APP、Windows EXE、Linux AppImage 各自的构建脚本均遵循此规则；项目之间不共享 SDK。
4. 升级 Flutter 必须在独立分支、新建项目内工具链目录、更新此文档并完成目标平台构建后才可切换；不能覆盖当前 `.toolchains/flutter`。
5. `flutter/ios/Flutter/flutter_export_environment.sh`、`build/`、`.dart_tool/` 是生成物，不是工具链来源；工具链变更后必须先 `flutter clean` 再 `pub get`。

## 当前验证命令

```bash
KEMI_FLUTTER=/Users/newlink/kemi/RustDesk/client/.toolchains/flutter/bin/flutter
"$KEMI_FLUTTER" --version
cd /Users/newlink/kemi/RustDesk/client/flutter
"$KEMI_FLUTTER" analyze lib/desktop/pages/connection_page.dart
"$KEMI_FLUTTER" build apk --debug
```

macOS Developer ID 候选包只允许通过 `res/build-kemi-developer-id-macos.sh` 构建；该脚本已禁止全局 Flutter 回退。
