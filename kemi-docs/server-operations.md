# 服务端操作入口

服务端唯一源码目录为 `/Users/newlink/kemi/RustDesk/server`。

```bash
cd /Users/newlink/kemi/RustDesk/server
./build.sh linux
```

Linux 部署包、端口和 systemd 指引见 `../../server/bin/DEPLOY.md`。客户端网络配置与客户端构建流程以本目录文档为准。

服务端与客户端是独立 Git 仓库：不要在一个模块的仓库中提交另一个模块的源码或构建产物。
