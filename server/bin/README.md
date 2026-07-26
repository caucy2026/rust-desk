# RustDesk 服务器二进制

## 自编译版（demo 源码，单进程）
| 文件 | 大小 | 说明 |
|------|------|------|
| `rustdesk-server-selfbuilt-x86_64` | 995KB | Mac ARM → Linux x86_64 交叉编译，源码来自 rustdesk-server-demo，单进程同时处理 ID 注册+中继转发 |

## 官方预编译版（Pro，双进程）
| 文件 | 大小 | 说明 |
|------|------|------|
| `rustdesk-hbbs-official-v1.1.16-x86_64` | 9.2MB | 官方 v1.1.16 hbbs（会合/ID 服务器） |
| `rustdesk-hbbr-official-v1.1.16-x86_64` | 3.2MB | 官方 v1.1.16 hbbr（中继服务器） |

## 区别
- **自编译版**：开源 demo，单进程运行 (`IP=x.x.x.x ./rustdesk-server-selfbuilt-x86_64`)
- **官方版**：Pro 功能更全，双进程分别运行 (`./hbbs ...` + `./hbbr ...`)

所有文件均为 ELF 64-bit x86-64，可直接在 Ubuntu 上运行。
