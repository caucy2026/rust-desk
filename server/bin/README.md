# RustDesk 信令服务器 — 完整部署包

> 将整个 `bin/` 目录复制到 Ubuntu 服务器即可部署。

## 文件清单

| 文件 | 说明 |
|------|------|
| `start-server.sh` | **主脚本**：启动/停止/状态/日志 |
| `start-hbbs.sh` | 单独启动 hbbs |
| `start-hbbr.sh` | 单独启动 hbbr |
| `start-demo.sh` | 单独启动自编译版 |
| `rustdesk-hbbs-official-v1.1.16-x86_64` | 官方 hbbs 二进制 (9.2MB) |
| `rustdesk-hbbr-official-v1.1.16-x86_64` | 官方 hbbr 二进制 (3.2MB) |
| `rustdesk-server-selfbuilt-x86_64` | 自编译 demo (995KB) |
| `DEPLOY.md` | **完整部署指南**（防火墙/systemd/排障） |
| `BUILD.md` | 编译指南（Mac 交叉编译 + Ubuntu 直接编译） |
| `CHANGELOG.md` | 开发调试全记录 |

## 快速开始

```bash
# 1. 复制到服务器
scp -r bin/ 用户名@你的服务器IP:~/rustdesk-server/

# 2. SSH 到服务器
ssh 用户名@你的服务器IP
cd ~/rustdesk-server/bin
chmod +x *.sh

# 3. 一键启动
./start-server.sh start 你的公网IP

# 4. 查看状态
./start-server.sh status
```

## 客户端配置

```
⚙️ → ID 服务器 → 你的公网IP
    → 中继服务器 → 你的公网IP
```

详细文档：`DEPLOY.md` | 编译文档：`BUILD.md`
