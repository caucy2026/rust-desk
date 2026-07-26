# Rust Desk — KEMI 远程桌面统一仓库

```
rust-desk/
├── client/    ← RustDesk 客户端（KEMI-远程桌面）Mac + Android
├── server/    ← 信令/中继服务器（自编译 + 官方版）
└── README.md
```

## client/ — 客户端

基于 RustDesk 定制：
- Mac 端：KEMI-远程桌面，权限流程简化，品牌化
- Android 端：KEMI 品牌启动画面，触摸精准点击，启动加速
- 版本：v1.0.4

### 构建
```bash
cd client/flutter
flutter build apk --debug        # Android
flutter build macos --debug      # macOS (需要 Xcode)
```

## server/ — 服务器

- 自编译版：Mac ARM 交叉编译 Linux x86_64
- 官方 Pro 预编译版：v1.1.16
- 详见 `server/BUILD.md` `server/CHANGELOG.md`

### 二进制
```bash
ls server/bin/
# rustdesk-server-selfbuilt-x86_64    (自编译)
# rustdesk-hbbs-official-v1.1.16-x86_64
# rustdesk-hbbr-official-v1.1.16-x86_64
```
