# MAC + PAD IPv4 P2P 固定端口映射

> 本文是固定端口机制的简版设计与发布门禁。2026-08-06的现场证据、级联Wi-Fi/手机热点的可行性判断、失败处置与完整验收矩阵见[p2p-network-debug-and-optimization.md](p2p-network-debug-and-optimization.md)。尤其要注意：MAC拥有固定公网IPv4映射会增加PAD直连机会，但不保证任意PAD网络均可P2P；MAC映射必须位于最外层公网出口，PAD还必须允许向该端口出站TCP。

## 目标

PAD 没有可用 IPv6 时，仍优先让 PAD 与 MAC 走 IPv4 P2P；中继仅作为映射或公网条件不满足时的保底。首期范围仅为 **KEMI PAD 控制 KEMI macOS**。

本方案不把“尝试映射”误报为 P2P 成功。只有实际会话连接类型为 `TCP-Mapped`、`TCP`、`UDP` 或 `IPv6` 时才是直连；`Relay` 仍代表中继。

## 已实现的连接路径

1. MAC 启动后，RustDesk 现有的直连服务固定监听 `TCP 21118`（默认 `hbbs 21116 + 2`）。
2. 在用户已同意的前提下，MAC 后台依次请求本机默认网关的 **PCP → NAT-PMP → UPnP IGD** 映射：`公网 TCP 21118 → MAC TCP 21118`。
3. 必须同时满足以下条件才认定映射有效：
   - 由网关返回的外部端口仍是 `21118`；
   - 返回的是公网 IPv4，而不是私网、回环或 `100.64.0.0/10` 运营商级 NAT 地址；
   - 映射租期仍有效。临时租期在半程自动续租；最长每小时复核一次。
4. PAD 请求连接且目标 MAC 被判定为 `ASYMMETRIC` NAT 时，除原有 TCP/UDP 打洞外，并行尝试：`MAC 信令公网 IP:21118`。
5. 该尝试仍进入 RustDesk 原有加密和身份认证路径；端口映射不提供文件、Shell 或未认证的额外服务。
6. 任何映射/直连尝试失败均保持既有中继回退，不影响已能连接的场景。

## 为什么要求固定 21118

当前 RustDesk 信令返回的是对端公网 IP 与普通打洞端口。若路由器把自动映射放到随机外部端口，PAD 无从可靠获知这个端口，不能称为可用 P2P。

因此首期**拒绝随机端口映射**，只接受外部端口等于 `21118`。后续若需要支持随机端口，必须先扩展 hbbs 信令协议，使 MAC 可信地上报映射端点、PAD 再按该端点连接；不能在客户端猜测。

## 现实边界

不能通过客户端代码保证所有网络都 P2P：多级路由的上级仍可能是 NAT、手机热点/运营商通常是 CGNAT、企业网络也可能禁止 PCP/NAT-PMP/UPnP。此时即使 MAC 的本地路由器接受映射，外网依然不可达，正确结果是中继。

要让“必定直连”成立，至少需要其中之一：可路由 IPv6、MAC 所在出口具有公网 IPv4 且允许固定映射、或由网络管理员在最外层网关手工转发 TCP 21118。应用会记录原因，不能假报成功。

## 日志与验收

MAC 日志出现以下之一：

- 成功：`KEMI P2P mapped direct TCP port ready: <公网IP>:21118 (PCP|NAT-PMP|UPnP)`
- 不可用：`automatic fixed-port mapping unavailable (...)`

PAD/MAC 会话日志出现：

- 尝试：`KEMI trying mapped direct TCP candidate: <公网IP>:21118`
- 成功：连接类型 `TCP-Mapped`；
- 未成功：最终为 `Relay`，并保留现有可用会话。

验收顺序：

1. 在 MAC 安装并重启含本功能的新 App，确认前一条 MAC 成功日志。
2. 在正确的 PAD 安装同一测试构建，保持 PAD 无 IPv6、与 MAC 不同 Wi-Fi。
3. 发起 PAD → MAC 控制，使用 MAC 的“调试日志”导出连接日志。
4. 验收连接类型为 `TCP-Mapped`；若为 Relay，依据映射日志判断是网关未开放还是上级/运营商 CGNAT。
5. 分别复测：同网、PAD 手机热点、级联路由、MAC 网络切换与 MAC 重启，确认失败时稳定回退中继且不会阻塞连接。

## 涉及代码

- `src/kemi_p2p_portmap.rs`：MAC 固定端口申请、校验与续租；
- `src/rendezvous_mediator.rs`：MAC 默认启动直连监听并启动映射生命周期；
- `src/client.rs`：PAD/客户端并行加入固定端口直连候选；
- `Cargo.toml`：UPnP IGD 依赖。

首次发布前应将映射状态接入 MAC 首页状态说明；在此之前以上述日志和“调试日志”按钮为验收依据。
