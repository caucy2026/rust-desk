# 服务器二进制 & 启动脚本

## 文件说明

| 文件 | 说明 |
|------|------|
| `start-hbbs.sh` | 启动官方 hbbs（ID 服务器），自动获取公网 IP |
| `start-hbbr.sh` | 启动官方 hbbr（中继服务器） |
| `start-demo.sh` | 启动自编译 demo 服务器 |
| `rustdesk-hbbs-official-v1.1.16-x86_64` | 官方 hbbs 二进制 |
| `rustdesk-hbbr-official-v1.1.16-x86_64` | 官方 hbbr 二进制 |
| `rustdesk-server-selfbuilt-x86_64` | 自编译 demo 二进制 |

## 快速开始

```bash
# 官方版
./start-hbbs.sh 你的公网IP &
./start-hbbr.sh &

# 自编译版
./start-demo.sh 你的公网IP &
```

详细部署文档：`../DEPLOY.md`
