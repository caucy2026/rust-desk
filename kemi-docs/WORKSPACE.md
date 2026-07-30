# KEMI 工作区与仓库边界

## 唯一目录结构

```text
/Users/newlink/kemi/RustDesk/
├── BIN/        # 已验证的交付产物归档；不是源码仓库，不提交 Git
├── client/     # KEMI RustDesk 客户端，独立 Git 仓库
├── server/     # RustDesk 信令/中继服务端，独立 Git 仓库
└── rustdesk/   # 历史空目录（仅空的 flutter/build 路径），不参与开发或构建
```

客户端只在 `/Users/newlink/kemi/RustDesk/client` 开发、构建和提交；服务端只在 `/Users/newlink/kemi/RustDesk/server` 开发和构建。不要在一个仓库中复制另一个模块的源码、`target/`、Flutter `build/` 或 APK。

`BIN/`用于保存本地交付包，当前版本为`1.4.44+102`：PAD debug APK、macOS App和macOS
arm64 ZIP。BIN不是Git源码备份；每份制品仍需记录来源commit、版本、哈希与签名状态。当前Mac
包保留原固定签名身份，但本机构建环境的证书链显示不受信任，不能把“文件已在BIN”写成
“新的固定签名已完成”。不要从`BIN/`反向修改或构建源码。

`rustdesk/` 不包含 Git、客户端源码或服务端源码，当前大小为 0B。它不是第三个模块，也不是 `client/` 的上级工程；如需清理，可在确认后删除这个空目录。

## Git 规则

- 客户端远端：`origin`是RustDesk上游，禁止推送KEMI代码；`backup`是KEMI备份。准备候选
  构建的提交推`backup/master`，旧run未完成时的新开发进度推`backup/wip/*`。完整协调规则
  见`ci-build.md`。
- GitHub当前default branch仍是旧`main`，真实客户端开发分支是`master`；修正仓库设置前，
  新环境必须显式clone`master`。
- 服务端是独立仓库。当前 `origin` 为 rustdesk-server-demo 上游；服务端定制在推送前必须先确认 KEMI 专用备份远端，不能误推到上游。
- 已退役目录：`/Users/newlink/kemi/rust-desk` 与 `/Users/newlink/kemi/rusk-server`。它们不再用于开发、构建或备份。

## 文档边界

- 当前客户端实现与验收：本目录的 `SESSION-HANDOFF.md`、`CHANGELOG-KEMI.md`。
- 源码备份、候选构建和云端制品协调：`ci-build.md`；具体Git操作：`GIT-OPS.md`。
- 服务端编译：`../../server/BUILD.md`；部署：`../../server/bin/DEPLOY.md`。
- 设备资料：`reference/hardware/`；历史架构：`reference/architecture/`。历史资料不能覆盖接续手册中的当前事实。
