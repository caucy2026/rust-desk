# 服务器二进制 & 启动脚本

## 一键管理

```bash
./start-server.sh start 你的公网IP      # 启动官方版 (hbbs+hbbr)
./start-server.sh start 你的公网IP demo # 启动自编译版
./start-server.sh stop                  # 停止
./start-server.sh status                # 查看状态
./start-server.sh log                   # 查看日志
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `start-server.sh` | **主脚本**：启动/停止/状态/日志 统一管理 |
| `start-hbbs.sh` | 单独启动 hbbs |
| `start-hbbr.sh` | 单独启动 hbbr |
| `start-demo.sh` | 单独启动自编译版 |
| `rustdesk-hbbs-official-v1.1.16-x86_64` | 官方 hbbs |
| `rustdesk-hbbr-official-v1.1.16-x86_64` | 官方 hbbr |
| `rustdesk-server-selfbuilt-x86_64` | 自编译 demo |

详细部署文档：`../DEPLOY.md`
