# RustDesk KEMI — Git 操作与 GitHub 备份指南

> 用途：代码备份到 GitHub、恢复操作、多仓库协作流程。
> 防止上下文丢失后无法恢复工作，本文独立保存，不依赖外部对话记录。

---

## 一、仓库地图

| 仓库 | 本地路径 | 远端 | 用途 |
|------|---------|------|------|
| **开发仓** | `/Users/newlink/kemi/RustDesk/rustdesk/` | `origin` → `git@github.com:rustdesk/rustdesk.git` | 日常开发、编译 APK |
| **备份仓** | `/Users/newlink/kemi/rust-desk/` | `origin` → `git@github.com:caucy2026/rust-desk.git` | 代码备份、团队共享 |

### 目录对应关系

```
rust-desk/client/  ← 同步自 RustDesk/rustdesk/
├── flutter/       ← flutter/
├── src/           ← src/
├── libs/          ← libs/
├── kemi-docs/          ← KEMI 开发文档
```

---

## 二、日常备份流程

### 2.1 在开发仓 (`RustDesk/rustdesk`) 提交

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk
git status                          # 查看改动
git add <files>                     # 按功能分步添加
git commit -m "feat(xxx): 描述"
```

**提交规范**：

- `docs:` — 纯文档变更
- `feat(android):` — Android 功能
- `feat(scrap):` — 编解码/采集
- `fix(xxx):` — Bug 修复，附根因

### 2.2 同步到备份仓 (`rust-desk`) 并推送

```bash
# 确认开发仓干净且已提交
cd /Users/newlink/kemi/RustDesk/rustdesk
git status --short

# 列出本次要同步的文件
# 只同步已提交的文件，不做增量复制
# 示例：跨屏键盘相关文件

src=/Users/newlink/kemi/RustDesk/rustdesk
dst=/Users/newlink/kemi/rust-desk/client

# 复制已改动的文件
cp "$src"/flutter/lib/mobile/pages/remote_page.dart "$dst"/flutter/lib/mobile/pages/
cp "$src"/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/KeyboardProxyManager.kt "$dst"/flutter/android/app/src/main/kotlin/com/carriez/flutter_hbb/
# ... 按需添加

# 在备份仓提交并推送
cd /Users/newlink/kemi/rust-desk
git add client/
git commit -m "backup(client): 描述同步内容"
git push origin main
```

### 2.3 验证推送成功

```bash
cd /Users/newlink/kemi/rust-desk
git rev-list --left-right --count HEAD...@{u}
# 期望输出: 0  0（本地与远端一致）
```

---

## 三、从备份仓恢复

### 3.1 同事下载编译

```bash
git clone https://github.com/caucy2026/rust-desk
cd rust-desk/client/flutter
flutter build apk --debug
```

### 3.2 本地从备份仓恢复文件

```bash
# 恢复单个文件
cp /Users/newlink/kemi/rust-desk/client/flutter/lib/mobile/pages/remote_page.dart \
   /Users/newlink/kemi/RustDesk/rustdesk/flutter/lib/mobile/pages/

# 恢复整个目录
cp -r /Users/newlink/kemi/rust-desk/client/flutter/lib/ \
   /Users/newlink/kemi/RustDesk/rustdesk/flutter/lib/
```

---

## 四、多仓库操作速查

### 开发仓常用命令

```bash
cd /Users/newlink/kemi/RustDesk/rustdesk

# 查看改动
git status --short
git diff --stat
git diff -- <file>

# 查看历史
git log --oneline -10
git log --oneline -- <file>

# 构建 APK
cd flutter
flutter clean && flutter pub get && flutter build apk --debug

# 安装到设备
adb -s 192.168.0.111:5555 install -r build/app/outputs/flutter-apk/app-debug.apk
```

### 备份仓常用命令

```bash
cd /Users/newlink/kemi/rust-desk

# 查看改动
git status --short

# 查看历史
git log --oneline -10

# 推送
git push origin main
```

---

## 五、重要注意事项

1. **开发仓的 origin 是 rustdesk/rustdesk（无推送权限）**
   - 开发仓提交只能留在本地，不能 `git push origin master`
   - 必须通过备份仓 `caucy2026/rust-desk` 推送

2. **备份仓是纯备份，不在其中编译**
   - 备份仓只在根目录做 `git push`
   - 不在备份仓内执行 Flutter/Gradle 构建

3. **文件同步要精确**
   - 不要用 `cp -r` 全量覆盖，会导致无关文件进入备份仓
   - 只同步已提交的改动文件

4. **按功能拆分提交**
   - 键盘、视频解码、文档各独立提交
   - 方便以后单独回退或 cherry-pick

---

## 六、紧急恢复清单

如果上下文丢失，按以下顺序恢复：

1. `cd /Users/newlink/kemi/RustDesk/rustdesk && git status --short && git log --oneline -10`
   - 确认开发仓当前状态与最近提交
2. `cd /Users/newlink/kemi/rust-desk && git log --oneline -10`
   - 确认备份仓最新同步版本
3. 阅读 `kemi-docs/cross-display-keyboard.md`
   - 跨屏键盘的完整需求、设计、调试记录
4. 阅读 `kemi-docs/dual-screen-port.md`
   - 双屏移植总体架构
5. 阅读本文件 `GIT-OPS.md`
   - 按第二节流程继续工作

---

> 最后更新：2026-07-27
> 维护：KEMI 远程桌面团队
