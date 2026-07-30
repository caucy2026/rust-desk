# Windows + VSCode 本地构建 KEMI 客户端提示词

> 适用版本：`1.4.44+102`，目标：Windows 10/11 x64。本文给从 GitHub 下载源码的同事使用。最省事的方式是把第 2 节整段复制给 VSCode 中的 Codex/Copilot/其他编程助手，让它先检查环境，再逐步执行并报告。

## 1. 使用前说明

源码仓库：

```text
https://github.com/caucy2026/rust-desk
分支：master
```

Windows 本地生成的是 Flutter GUI 客户端，必须使用 Windows x64 主机。不要在 WSL 中执行 Windows GUI 打包，也不要使用服务器目录中的 Linux 交叉编译脚本。

完整构建依赖较多，第一次主要耗时在 Visual Studio、Flutter engine 和 vcpkg；依赖准备完成后，后续构建会复用缓存。若只想取得已构建候选包，可直接到 GitHub 的 `KEMI Focused Client Artifacts` 对应 commit run 下载；Windows/Linux 都成功后也会生成 `kemi-<完整commit>` 候选 prerelease，无需本地重编。

## 2. 可直接复制给 VSCode AI 的提示词

```text
你现在位于 Windows 10/11 x64 的 VSCode 中，请帮助我从源码完整构建 KEMI-远程桌面 Windows x64 Flutter 客户端。

仓库地址：https://github.com/caucy2026/rust-desk
分支：master
目标产物：Windows x64 便携 EXE
当前产品版本以 flutter/pubspec.yaml 和 Cargo.toml 为准，不要自行猜版本。

必须遵守以下规则：

1. 如果仓库还没下载，使用 git clone --recursive；如果已经下载，先运行：
   git status --short
   git submodule status
   git rev-parse HEAD
   git remote -v
   有未提交改动时先告诉我，不要 reset、clean、checkout 或覆盖我的文件。

2. 完整阅读仓库根目录 AGENTS.md、kemi-docs/README.md、kemi-docs/ci-build.md 和 .github/workflows/flutter-build.yml 中 Windows x64 job。只构建 x86_64-pc-windows-msvc，不构建 ARM64、x86、Sciter、Android、Mac 或 Linux。

3. 先做环境审计，只检查、不立刻大规模安装。逐项输出“已满足/缺失/版本错误”：
   - Windows 10/11 x64，不在 WSL
   - Git
   - Python 3，py、python、python3、pip3 命令可用（仓库脚本会直接调用 python3/pip3）
   - Visual Studio 2022 或 Build Tools 2022
   - Desktop development with C++ 工作负载
   - MSVC v143 x64 工具链
   - Windows 10/11 SDK
   - CMake、Ninja、NuGet
   - LLVM/Clang 15.0.6，并能找到 clang、libclang
   - Rustup，Rust 1.75，目标 x86_64-pc-windows-msvc
   - Flutter 3.24.5 x64
   - vcpkg commit 120deac3062162151622ca4860575a33844ba10b
   - 磁盘至少预留 35 GB

   缺少 Visual Studio workload、SDK或其他需要管理员权限的系统组件时，先给我准确安装命令或 Visual Studio Installer 勾选项，等我确认后再继续。不要静默升级到最新 Flutter、Rust、LLVM、vcpkg或依赖版本。

4. 使用 PowerShell，并固定以下环境：
   $env:VCPKG_ROOT = 'C:\vcpkg'
   $env:VCPKG_DEFAULT_HOST_TRIPLET = 'x64-windows-static'
   $env:LIBCLANG_PATH = '<LLVM 15 的 bin 目录>'
   rustup toolchain install 1.75
   rustup override set 1.75
   rustup target add x86_64-pc-windows-msvc

   如果 C:\vcpkg 不存在，克隆 microsoft/vcpkg；随后切换到固定 commit：
   git -C C:\vcpkg fetch --all
   git -C C:\vcpkg checkout 120deac3062162151622ca4860575a33844ba10b
   C:\vcpkg\bootstrap-vcpkg.bat -disableMetrics

   在仓库根目录执行 manifest 安装：
   C:\vcpkg\vcpkg.exe install --triplet x64-windows-static --x-install-root=C:\vcpkg\installed
   如果失败，打印 C:\vcpkg\buildtrees 下相关 *-out.log 的最后 150 行；不要只回复 exit code 1。

5. Flutter x64 必须使用 3.24.5。运行 flutter --version 和 flutter doctor -v，并执行：
   flutter config --enable-windows-desktop
   flutter precache --windows

   RustDesk Windows x64依赖与 Flutter 3.24时代匹配的自定义 engine。按照 .github/workflows/flutter-build.yml 的 Windows x64步骤：
   - 下载 https://github.com/rustdesk/engine/releases/download/main/windows-x64-release.zip
   - 解压后，把其中内容放进当前 Flutter SDK的 bin\cache\artifacts\engine\windows-x64-release
   - 目录中不能多套一层 windows-x64-release
   - 在 Flutter SDK根目录应用仓库的 .github\patches\flutter_3.24.4_dropdown_menu_enableFilter.diff
   已经应用过补丁时先用 git apply --check 判断，不要重复应用。

6. 检查以下 bridge 文件：
   src\bridge_generated.rs
   src\bridge_generated.io.rs
   flutter\lib\generated_bridge.dart
   flutter\lib\generated_bridge.freezed.dart

   Git仓库默认只跟踪 Rust侧生成文件；若 Dart侧文件不存在或与当前 commit不匹配，按 .github/workflows/bridge.yml 的 default bridge流程生成：
   - 使用单独的 Flutter 3.22.3 SDK生成 default bridge，不要替换主构建用的 Flutter 3.24.5
   - cargo-expand 固定 1.0.95
   - flutter_rust_bridge_codegen 固定 1.80.1，并启用 uuid feature
   - 在生成用临时状态中把 flutter/pubspec.yaml 的 extended_text: 14.0.0 临时改为 13.0.0
   - Flutter 3.22.3执行 flutter pub get
   - 执行：
     flutter_rust_bridge_codegen --rust-input .\src\flutter_ffi.rs --dart-output .\flutter\lib\generated_bridge.dart --c-output .\flutter\macos\Runner\bridge_generated.h
   - 生成后恢复 pubspec.yaml/pubspec.lock原始内容，再用 Flutter 3.24.5执行 flutter pub get
   不得把临时依赖降级提交进 Git。

7. 构建前核对版本一致：
   - flutter/pubspec.yaml：例如 1.4.44+102
   - Cargo.toml/Cargo.lock：例如 1.4.44
   - .github/workflows/flutter-build.yml 的 VERSION
   发现不一致先停止并报告，不要自行选一个版本。

8. 在仓库根目录先构建 Windows release目录：
   py .\build.py --portable --flutter --skip-portable-pack --hwcodec --vram

   失败时必须报告：
   - 完整失败命令
   - 首条有效 compiler error
   - 文件和行号
   - 是 Rust、vcpkg、Flutter、CMake还是链接错误
   不要盲目重复整个构建，也不要为了通过编译修改产品功能。

9. 构建成功后确认：
   flutter\build\windows\x64\runner\Release\rustdesk.exe
   flutter\build\windows\x64\runner\Release\flutter_windows.dll
   flutter\build\windows\x64\runner\Release\data\
   target\release\librustdesk.dll

   将整个 Release目录复制到仓库根目录的 rustdesk\ 临时交付目录。不要只复制一个 rustdesk.exe，否则会缺少 Flutter DLL、data和插件。

10. 生成单文件便携 EXE：
    py -m pip install -r .\libs\portable\requirements.txt
    Push-Location .\libs\portable
    py .\generate.py -f ..\..\rustdesk -o . -e ..\..\rustdesk\rustdesk.exe
    Pop-Location

    将 target\release\rustdesk-portable-packer.exe 复制并命名为：
    BIN\KEMI-remote-desktop-windows-x64.exe

    BIN不存在时创建。不要把编译产物、大型依赖目录或 vcpkg提交到普通Git历史。

11. 最终验收：
    - Get-Item查看文件大小和时间
    - Get-FileHash -Algorithm SHA256
    - 用 sigcheck、Get-AuthenticodeSignature 或文件属性检查架构/签名/版本
    - 在当前 Windows x64机器启动
    - 检查 Windows 文件属性中的 ProductName、FileDescription、FileVersion、ProductVersion
    - 检查主窗口标题、主页品牌和产品版本
    - 验证远程控制与文件传输入口
    - 未配置正式代码签名证书时明确写“unsigned测试候选包”，不能声称已签名
    - 当前仓库部分 Windows资源和默认 APP_NAME仍可能显示RustDesk；仅把最终文件改名为
      KEMI-remote-desktop-windows-x64.exe不代表品牌化完成。若检查仍显示RustDesk，要在报告中
      如实记录并单独登记“KEMI Windows品牌化”开发任务，不要为本次构建临时改业务源码
    - 输出源码commit、版本、构建命令、最终路径、大小、SHA-256、签名状态和启动结果

12. 如果本地工具链问题短时间无法解决，不要擅自更换Flutter/Rust大版本。指出准确阻塞后，给出使用仓库 KEMI Focused Client Artifacts 工作流生成同一commit Windows x64候选包的备用方案。

现在先执行第1～3步环境审计并把结果列成表格；在需要管理员权限安装组件前暂停等待我确认。
```

## 3. 人工快速核对表

VSCode AI报告完成后，同事至少确认：

- 源 commit 与计划交付 commit 完全一致；
- 只构建 Windows x64；
- Flutter 是 `3.24.5`，Rust 是 `1.75`，vcpkg 是固定 commit；
- 没有把临时 `extended_text 13.0.0` 或生成环境改动留在 Git diff；
- 交付的是便携 packer EXE，不是孤立的 `rustdesk.exe`；
- 文件内部品牌、版本、远控和文件传输实际可用；若仍显示 RustDesk，已经明确记录而不是只改文件名；
- 未签名包明确标为测试候选；
- 最终记录 SHA-256。

若本地结果要放入 PAD 下载页，还必须按 `client-distribution.md` 重命名、导入 assets、重构 PAD APK并从 PAD下载验证，不能只把文件复制进目录就宣布交付。
