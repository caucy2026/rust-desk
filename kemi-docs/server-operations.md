# 服务端操作入口

服务端唯一源码目录为 `/Users/newlink/kemi/RustDesk/server`。

```bash
cd /Users/newlink/kemi/RustDesk/server
./build-hbbc.sh linux
```

正式Linux部署包、端口、三服务systemd和JSON维护指引见
`../../BIN/release/server/README-部署说明.md`与
`../../BIN/release/server/README-HBBC-配置与部署.md`。客户端网络配置与客户端构建流程
以本目录文档为准。

服务端与客户端是独立 Git 仓库：不要在一个模块的仓库中提交另一个模块的源码或构建产物。

## 云端客户端下载服务 hbbc

正式服务端按三个独立进程维护：`hbbs`负责ID、信令和UDP打洞，`hbbr`负责远控中继，
`hbbc`负责JSON配置驱动的云端客户端下载页面。三者通过`kemi-rustdesk.target`统一启停，
但hbbc不读取RustDesk密钥、不嵌入信令进程，下载服务异常不会影响远控连接。

hbbc内部监听`127.0.0.1:21120`，公网由Nginx 443提供
`https://kemi-chat.newlinksz.com/kemi`，不得直接把21120暴露公网。正式配置为
`/etc/kemi-rustdesk/hbbc.json`，默认每600秒重新读取JSON并解析Newlink `plugData`。
修改页面、项目、平台卡片和固定name无需重新编译；需要立即生效时只重启
`kemi-rustdesk-hbbc.service`。

完整字段、自动页面生成、两份清单交叉校验、Nginx、回滚和验收说明见服务端仓库
`docs/hbbc-cloud-download-server.md`以及发布包
`BIN/release/server/README-HBBC-配置与部署.md`。
