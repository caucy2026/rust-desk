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
`hbbc`负责JSON配置驱动的云端客户端下载页面。三个systemd service各自启停、各自设置
开机自启，不依赖`kemi-rustdesk.target`；hbbc不读取RustDesk密钥、不嵌入信令进程，
下载服务异常不会影响远控连接。

hbbc当前由程序自身直接提供公网HTTP服务，端口为`21120`，不依赖Nginx、TLS证书或服务器
其他代理配置。正式配置为`/etc/kemi-rustdesk/hbbc.json`，默认每600秒重新读取JSON并解析
Newlink `plugData`。修改页面、项目、平台卡片和固定name无需重新编译；需要立即生效时只重启
`kemi-rustdesk-hbbc.service`。当前入口为：

```text
http://kemi-chat.newlinksz.com:21120/kemi-desk
http://kemi-chat.newlinksz.com:21120/kemi-send
```

浏览器访问PAD的`http://PAD-IP:8686`后，网页顶部在一个统一外边框内提供两个主入口：方式一
继续使用当前PAD局域网地址；方式二固定打开`http://kemi-chat.newlinksz.com:21120/kemi-desk`
完整云端下载页。地址本身可点击，不显示多余打开/复制按钮。平台卡片中的Newlink HTTPS实际
文件恢复为“云备份下载”，每个按钮后分别注明只作为两个主入口都失效时的备案；该地址由PAD
实时解析固定`plugData`接口，不硬编码历史CDN URL。

完整字段、自动页面生成、两份清单交叉校验、独立HTTP服务、回滚和验收说明见服务端仓库
`docs/hbbc-cloud-download-server.md`以及发布包
`BIN/release/server/README-HBBC-配置与部署.md`。
