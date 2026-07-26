# RustDesk 信令服务器 — 编译与部署指南

> 目录：`/Users/newlink/kemi/rusk-server`  
> 目标：Mac ARM 编译 → Ubuntu x86_64 运行

---

## 方法 A：Mac 交叉编译（推荐，只需做一次环境准备）

### A1. 一次性安装 zig 交叉编译器

```bash
# 下载 zig（约 50MB，一次性）
curl -L "https://ziglang.org/builds/zig-aarch64-macos-0.17.0-dev.1464+6aff551f1.tar.xz" -o /tmp/zig.tar.xz
mkdir -p ~/zig
tar -xf /tmp/zig.tar.xz -C ~/zig
ZIG_DIR=$(ls ~/zig | head -1)
echo "export PATH=\"\$HOME/zig/$ZIG_DIR:\$PATH\"" >> ~/.zshrc
export PATH="$HOME/zig/$ZIG_DIR:$PATH"

# 验证
zig version
```

### A2. 安装 cargo-zigbuild（已完成）

```bash
cargo install cargo-zigbuild
```

### A3. 编译 Linux x86_64 二进制

```bash
cd /Users/newlink/kemi/rusk-server

# 交叉编译（自动处理所有依赖，产出 Linux x86_64 ELF）
cargo zigbuild --release --target x86_64-unknown-linux-gnu

# 产物
ls -lh target/x86_64-unknown-linux-gnu/release/rustdesk-server
```

### A4. 拷到 Ubuntu 服务器

```bash
scp target/x86_64-unknown-linux-gnu/release/rustdesk-server 用户名@服务器IP:~/
```

---

## 方法 B：直接在 Ubuntu 上编译（无需交叉编译）

```bash
ssh 用户名@服务器IP

# 安装 Rust（已装跳过）
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env

# 克隆并编译
git clone https://github.com/rustdesk/rustdesk-server-demo
cd rustdesk-server-demo
cargo build --release

ls -lh target/release/rustdesk-server
```

---

## 运行服务器

```bash
export IP=你的服务器公网IP
nohup ./rustdesk-server > server.log 2>&1 &
```

---

## 防火墙

```bash
sudo ufw allow 21116/tcp
sudo ufw allow 21116/udp
sudo ufw allow 21117/tcp
sudo ufw reload
```

---

## 客户端配置

RustDesk（PAD / Mac）→ ⚙️ → ID 服务器 + 中继服务器 → `你的服务器公网IP`

---

## systemd 开机自启

```bash
sudo tee /etc/systemd/system/rustdesk-server.service << 'EOF'
[Unit]
Description=RustDesk Server
After=network.target

[Service]
Type=simple
Environment="IP=你的服务器公网IP"
ExecStart=/home/用户名/rustdesk-server
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now rustdesk-server
```

---

## 本地测试

```bash
cd /Users/newlink/kemi/rusk-server
export IP=127.0.0.1
cargo run
```

---

## 端口说明

| 端口 | 协议 | 用途 |
|------|------|------|
| 21116 | TCP+UDP | ID 注册 / 打洞协商 |
| 21117 | TCP | 流量中继转发 |

