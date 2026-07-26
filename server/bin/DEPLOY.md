# RustDesk 信令服务器 — Ubuntu 完整部署指南

> 服务器 IP 示例：`119.96.24.110`，请替换为你的实际公网 IP。

---

## 一、上传文件到服务器

```bash
# 在本机 Mac 执行
cd /Users/newlink/kemi/rust-desk/server
scp -r bin/ 用户名@你的服务器IP:~/rustdesk-server/
```

---

## 二、服务器端操作

SSH 到服务器后：

```bash
cd ~/rustdesk-server/bin
chmod +x *.sh
ls -la
```

---

## 三、开放防火墙端口

```bash
# ufw 方式（推荐）
sudo ufw allow 21115/tcp
sudo ufw allow 21116/tcp
sudo ufw allow 21116/udp
sudo ufw allow 21117/tcp
sudo ufw reload
sudo ufw status

# 如果是云服务器（阿里云/腾讯云/华为云），还需要在控制台安全组里放行：
# 21115-21117 TCP, 21116 UDP
```

---

## 四、启动服务器

### 方式 A：官方 Pro 版（推荐）

```bash
cd ~/rustdesk-server/bin

# 启动 hbbs（ID 服务器）
nohup ./start-hbbs.sh 119.96.24.110 > ../hbbs.log 2>&1 &

# 启动 hbbr（中继服务器）
nohup ./start-hbbr.sh > ../hbbr.log 2>&1 &

# 验证
ps aux | grep hbb
tail ../hbbs.log
```

### 方式 B：自编译 demo 版（简单）

```bash
cd ~/rustdesk-server/bin
nohup ./start-demo.sh 119.96.24.110 > ../server.log 2>&1 &
```

---

## 五、客户端配置

在你的 RustDesk / KEMI-远程桌面 客户端：

```
⚙️ 设置 → 网络
  ID 服务器:     119.96.24.110
  中继服务器:     119.96.24.110
  API 服务器:     留空
  Key:           留空
```

---

## 六、设为开机自启（systemd）

```bash
# hbbs
sudo tee /etc/systemd/system/rustdesk-hbbs.service << 'EOF'
[Unit]
Description=RustDesk hbbs (ID Server)
After=network.target

[Service]
Type=simple
WorkingDirectory=/home/你的用户名/rustdesk-server/bin
ExecStart=/home/你的用户名/rustdesk-server/bin/start-hbbs.sh 119.96.24.110
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# hbbr
sudo tee /etc/systemd/system/rustdesk-hbbr.service << 'EOF'
[Unit]
Description=RustDesk hbbr (Relay Server)
After=network.target

[Service]
Type=simple
WorkingDirectory=/home/你的用户名/rustdesk-server/bin
ExecStart=/home/你的用户名/rustdesk-server/bin/start-hbbr.sh
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# 启用
sudo systemctl daemon-reload
sudo systemctl enable rustdesk-hbbs rustdesk-hbbr
sudo systemctl start rustdesk-hbbs rustdesk-hbbr
sudo systemctl status rustdesk-hbbs rustdesk-hbbr
```

---

## 七、验证服务

```bash
# 检查进程
ps aux | grep hbb

# 检查端口监听
ss -tlnp | grep -E "21115|21116|21117"

# 查看日志
tail -f ~/rustdesk-server/hbbs.log
tail -f ~/rustdesk-server/hbbr.log
```

---

## 八、端口速查表

| 端口 | 协议 | 服务 | 用途 |
|------|------|------|------|
| 21115 | TCP | hbbs | NAT 类型测试 |
| 21116 | TCP | hbbs | ID 注册、打洞协商 |
| 21116 | UDP | hbbs | ID 注册、心跳 |
| 21117 | TCP | hbbr | 中继流量转发 |

---

## 九、故障排查

```bash
# 1. 检查服务器能否访问
ping 119.96.24.110

# 2. 检查端口是否开放（在 Mac 上执行）
nc -zv 119.96.24.110 21116

# 3. 查看服务日志
tail -100 ~/rustdesk-server/hbbs.log

# 4. 重启服务
sudo systemctl restart rustdesk-hbbs rustdesk-hbbr
```
