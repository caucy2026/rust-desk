# RustDesk KEMI：Git 操作与 GitHub 备份指南

> 用途：在真实开发仓中安全提交 KEMI 定制、备份到 GitHub、处理分叉和恢复项目。
>
> 当前基线日期：2026-07-29。完整项目上下文见 `kemi-docs/SESSION-HANDOFF.md`。

---

## 一、真实仓库模型

本项目只使用一个本地开发仓：

```text
/Users/newlink/kemi/RustDesk/rustdesk
```

该仓配置两个远端：

| 远端 | 地址 | 用途 | 是否推送 KEMI 代码 |
|---|---|---|---|
| `origin` | `git@github.com:rustdesk/rustdesk.git` | RustDesk 官方上游 | 否 |
| `backup` | `git@github.com:caucy2026/rust-desk.git` | KEMI GitHub 备份 | 是，推送 `master` |

检查命令：

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk
git remote -v
git branch --show-current
git status --short
```

预期当前分支为 `master`。

以下历史流程已经废弃：

- 不再复制文件到 `/Users/newlink/kemi/rust-desk/client`。
- 不再在第二个本地仓重复提交。
- 不再推送 `origin main`。

---

## 二、提交前检查

### 2.1 查看当前状态

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk

git status --short
git diff --stat
git diff --check
```

逐项确认：

1. 没有覆盖其他人的未提交改动。
2. 没有混入 `build/`、`target/`、APK、日志或临时文件。
3. 只修改当前任务需要的文件。
4. `kemi-docs/CHANGELOG-KEMI.md` 已记录功能、根因和验证结果。
5. 已完成与改动风险匹配的静态检查和构建。

不要为了让工作区变干净而使用 `git reset --hard` 或 `git checkout --`。遇到不属于当前任务的修改时保留并绕开。

### 2.2 常用验证

Flutter 修改至少执行：

```bash
export PATH=/Users/newlink/flutter/bin:$PATH
cd /Users/newlink/kemi/RustDesk/rustdesk/flutter

flutter analyze <本次修改的 Dart 文件>
flutter build apk --debug
```

Debug APK：

```text
/Users/newlink/kemi/RustDesk/rustdesk/flutter/build/app/outputs/flutter-apk/app-debug.apk
```

文档修改至少执行：

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk
git diff --check -- kemi-docs .github/prompts
```

---

## 三、本地提交

### 3.1 精确暂存

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk

git add flutter/lib/mobile/pages/remote_page.dart
git add flutter/lib/mobile/pages/file_manager_page.dart
git add kemi-docs/CHANGELOG-KEMI.md
git diff --cached --stat
git diff --cached --check
```

上面只是文件列表示例。使用本次真实改动文件，不要默认 `git add -A`。

### 3.2 提交消息

```bash
git commit -m "feat(mobile): 描述用户可见行为"
```

常用前缀：

| 类型 | 用途 |
|---|---|
| `feat(mobile):` | Flutter 移动端功能 |
| `feat(android):` | Kotlin/Android 原生功能 |
| `fix(mobile):` | Flutter 行为修复 |
| `fix(android):` | Android 原生修复 |
| `docs:` | 纯文档和接续说明 |

提交后检查：

```bash
git show --stat --oneline HEAD
git status --short
```

---

## 四、同步 GitHub 状态

推送前获取备份仓最新状态：

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk
git fetch backup master
git log --graph --oneline --decorate --max-count=12 master backup/master
git rev-list --left-right --count backup/master...master
```

最后一个命令的输出含义：

```text
0 N  本地 master 领先 N 个提交，可以正常推送
N 0  backup/master 领先，先 rebase
N M  双方分叉，先 rebase 并解决冲突
0 0  本地与远端一致，无需推送
```

---

## 五、分叉与冲突处理

如果 `backup/master` 有本地没有的提交：

```bash
git rebase backup/master
```

发生冲突时：

```bash
git status
```

逐个读取冲突文件，结合当前产品要求和远端历史处理。处理完成后：

```bash
git add <已解决的冲突文件>
GIT_EDITOR=true git rebase --continue
```

需要放弃本次 rebase 时：

```bash
git rebase --abort
```

冲突处理原则：

1. 保留远端已有且不冲突的历史。
2. 保留当前最终产品行为，不恢复旧实现。
3. 文件传输冲突必须保留 60% × 60% 浮窗、独立 FFI Session、后台视频持续播放和 Android Download 默认目录。
4. rebase 改写本地提交哈希后，要以新哈希为准更新交付记录。
5. 不使用 `git push --force`。只有用户明确授权并理解影响后才考虑强推。

rebase 后重新验证：

```bash
git diff --check backup/master..HEAD
git log --oneline --max-count=8
```

代码冲突还必须重新运行静态检查和构建。

---

## 六、推送到 KEMI GitHub

确认本地历史已包含 `backup/master` 后：

```bash
git push backup master
```

禁止将 KEMI 定制推送到官方上游：

```bash
# 不要执行
git push origin master
```

如果网络或 SSH 身份失败，先保留本地提交，报告真实错误；不要改写远端地址或改用强推规避问题。

---

## 七、推送后强校验

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk

git rev-parse HEAD
git ls-remote backup refs/heads/master
git status --short
```

验收条件：

- `git rev-parse HEAD` 与 `git ls-remote` 第一列完全相同。
- `git status --short` 无输出。
- GitHub 分支是 `master`。

当前已验证基线：

```text
8f4c18c577a2352ba7d270ec4a350ef22c3d9abc
```

---

## 八、从 GitHub 恢复

### 8.1 克隆完整 KEMI 仓库

SSH：

```bash
git clone git@github.com:caucy2026/rust-desk.git
cd rust-desk
git checkout master
```

HTTPS：

```bash
git clone https://github.com/caucy2026/rust-desk.git
cd rust-desk
git checkout master
```

这已经是完整可构建仓库，不存在旧流程中的 `client/` 子目录。

### 8.2 核对恢复结果

```bash
git remote -v
git log -1 --oneline
grep -n '^version' Cargo.toml flutter/pubspec.yaml
test -f kemi-docs/SESSION-HANDOFF.md
```

### 8.3 构建恢复后的 APK

```bash
export PATH=/Users/newlink/flutter/bin:$PATH
cd flutter
flutter pub get
flutter build apk --debug
```

Apple Silicon 无 Rosetta 时，release AOT 可能因 x64 `gen_snapshot` 失败；当前已验证的恢复检查使用 Debug APK。

---

## 九、紧急接续清单

聊天上下文丢失或由新开发者接手时：

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk

git status --short
git log --oneline -8
git remote -v
git rev-parse HEAD
git ls-remote backup refs/heads/master
```

然后按顺序阅读：

1. `AGENTS.md`
2. `kemi-docs/SESSION-HANDOFF.md`
3. `kemi-docs/CHANGELOG-KEMI.md` 最新章节
4. 当前任务对应的专项文档
5. 本文件

不要只凭旧聊天摘要或旧文档中的 IP、SDK 路径和版本开始修改。

---

> 最后更新：2026-07-29
> 维护：KEMI 远程桌面团队