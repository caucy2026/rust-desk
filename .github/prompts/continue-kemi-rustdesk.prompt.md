---
name: "继续 KEMI RustDesk 开发"
description: "从已验证的 KEMI Android PAD 双屏、键盘和文件传输基线继续开发"
argument-hint: "描述本次要继续完成的功能、问题或验证任务"
agent: "agent"
---

请接手 KEMI 远程桌面 Android PAD 项目，并完成用户在本会话提出的当前任务。

真实项目仓库固定为 `/Users/newlink/kemi/RustDesk/rustdesk`。先阅读 [仓库规则](../../AGENTS.md) 和 [完整接续手册](../../kemi-docs/SESSION-HANDOFF.md)，再检查当前 `git status`、最近提交、远端和版本文件。涉及具体模块时，继续阅读接续手册索引的对应规格和变更记录。

必须以当前源代码、官方或原项目源码以及可执行验证为依据，不根据旧聊天记录猜测。保护接续手册列出的不可回退行为，尤其是 Display 0/2 双屏职责、三点菜单文件传输入口、60% x 60% 圆角浮窗、独立文件传输 FFI Session、后台视频持续播放、Android Download 默认目录和 APK PackageInfo 版本显示。

找到控制当前行为的最小代码路径后实施修改，并完成与风险相称的静态检查、Debug APK 构建和必要的实机验证。提交前更新 KEMI 变更记录。只有用户要求备份时才提交和推送；推送目标必须是 `backup master`，不能推送官方 `origin`，推送后核对本地与远端完整哈希。

最终报告应包含改动文件、根因或设计依据、验证结果、构建/设备结果（适用时）、未完成风险，以及提交与 GitHub 哈希（适用时）。