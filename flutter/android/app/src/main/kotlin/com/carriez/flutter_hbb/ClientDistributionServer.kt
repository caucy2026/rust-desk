package com.carriez.flutter_hbb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.wifi.WifiManager
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets

/** Short-lived LAN-only client download service owned by MainActivity. */
class ClientDistributionServer(private val context: Context) {
    companion object {
        private const val TAG = "ClientDistribution"
        private const val PREFERRED_PORT = 8686
        private const val SOCKET_TIMEOUT_MS = 7_000
    }

    private data class PackageEntry(
        val id: String,
        val platform: String,
        val detail: String,
        val version: String,
        val fileName: String,
        val contentType: String,
        val file: File?,
        val cloudUrl: String?,
    )

    private val packageSync = ClientPackageSync.get(context)
    private val lock = Any()
    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var port = 0

    fun start(): Map<String, Any?> = synchronized(lock) {
        if (!running) {
            val socket = openSocket() ?: return@synchronized mapOf(
                "running" to false,
                "error" to "端口无法使用，请稍后重试。",
            )
            serverSocket = socket
            port = socket.localPort
            running = true
            Thread({ acceptLoop(socket) }, "kemi-client-download-server").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Client download service started on port $port")
        }
        status()
    }

    fun stop() {
        synchronized(lock) {
            if (!running) return
            running = false
            serverSocket?.close()
            serverSocket = null
            port = 0
            Log.i(TAG, "Client download service stopped")
        }
    }

    private fun openSocket(): ServerSocket? {
        for (candidatePort in intArrayOf(PREFERRED_PORT, 0)) {
            try {
                return ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(candidatePort))
                }
            } catch (error: Exception) {
                Log.w(TAG, "Cannot bind port $candidatePort", error)
            }
        }
        return null
    }

    private fun status(): Map<String, Any?> {
        val urls = localIpv4Addresses().map { "http://$it:$port" }
        return mapOf(
            "running" to running,
            "port" to port,
            "url" to (urls.firstOrNull() ?: ""),
            "addresses" to urls,
            // Android 10+ 未授予定位权限时系统会返回 <unknown ssid>。
            // 不伪造 Wi-Fi 名称，界面会只提示两台设备需在同一网络。
            "wifiName" to currentWifiName(),
            "wifiNamePermissionGranted" to hasWifiNamePermission(),
            "metadata" to packageSync.metadataStatus(),
            "packages" to packageSync.packageStatus(),
        )
    }

    private fun packageEntries(): List<PackageEntry> {
        val targets = ClientPackageSync.definitions.associateWith {
            packageSync.packageForId(it.id)
        }
        return targets.map { (definition, target) ->
            val resolved = packageSync.resolvePackage(definition.id)
            val servedTarget = resolved?.target ?: target
            val fileName = servedTarget?.fileName
                ?: if (definition.id == "android") packageSync.currentAndroidFileName()
                else "unavailable-${definition.id}"
            PackageEntry(
                id = definition.id,
                platform = definition.platform,
                detail = definition.detail,
                version = servedTarget?.version ?: "",
                fileName = fileName,
                contentType = definition.contentType,
                file = resolved?.file,
                cloudUrl = target?.url?.takeIf(::isSafeCloudUrl),
            )
        }
    }

    private fun availablePackages() = packageEntries().filter { it.file?.isFile == true }

    private fun isSafeCloudUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.host.equals("cdn.newlink-sz.com", ignoreCase = true)
    } catch (_: Exception) {
        false
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            try {
                val client = socket.accept()
                Thread({ handleClient(client) }, "kemi-client-download-request").apply {
                    isDaemon = true
                    start()
                }
            } catch (_: SocketException) {
                if (running) Log.w(TAG, "Download server socket closed unexpectedly")
                break
            } catch (error: Exception) {
                if (running) Log.w(TAG, "Cannot accept client request", error)
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val reader = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                val requestLine = reader.readLine()?.take(4096) ?: return
                var headerCount = 0
                while (reader.readLine()?.also { headerCount++ }?.isNotEmpty() == true && headerCount < 32) {
                    // No request-body routes; discard only a small, bounded header set.
                }
                val parts = requestLine.split(' ')
                if (parts.size < 2 || parts[0] != "GET") {
                    writeText(socket.getOutputStream(), 405, "Only GET is supported")
                    return
                }
                val path = try {
                    URI(parts[1]).path ?: "/"
                } catch (_: Exception) {
                    writeText(socket.getOutputStream(), 400, "Bad request")
                    return
                }
                route(socket.getOutputStream(), path)
            } catch (error: Exception) {
                Log.d(TAG, "Client request ended", error)
            }
        }
    }

    private fun route(output: OutputStream, path: String) {
        when (path) {
            "/" -> writeText(output, 200, buildHtml(), "text/html; charset=utf-8")
            "/health" -> writeText(output, 200, "ok")
            else -> {
                val entry = availablePackages().firstOrNull { "/download/${it.fileName}" == path }
                if (entry == null) {
                    writeText(output, 404, "Not found")
                } else {
                    writeFile(output, entry.file, entry)
                }
            }
        }
    }

    private fun buildHtml(): String {
        val entries = packageEntries().associateBy { it.id }
        val packageLinks = listOf("windows", "macos", "linux", "android").joinToString("") { id ->
            val entry = entries[id] ?: return@joinToString ""
            val title = when (id) {
                "windows" -> "Windows"
                "macos" -> "macOS（Apple 芯片）"
                "linux" -> "Linux（x86_64）"
                else -> "Android APK"
            }
            val detail = when (id) {
                "windows" -> "适用于大多数 Windows 10 / 11 电脑 · 下载并双击安装包即可"
                "macos" -> "下载后拖入“应用程序”文件夹"
                "linux" -> "适用于常见 Intel / AMD 电脑 · 下载 AppImage 后按系统提示运行"
                else -> "下载后按系统提示允许本次安装"
            }
            val icon = when (id) {
                "windows" -> "⊞"
                "macos" -> "⌘"
                "linux" -> "◈"
                else -> "▣"
            }
            val recommendedClass = if (id == "windows") " recommended" else ""
            val recommendedTag = if (id == "windows") "<em class=\"tag\">推荐</em>" else ""
            val version = entry.version.takeIf(String::isNotBlank)?.let { " · 版本 ${escapeHtml(it)}" } ?: ""
            val localAction = if (entry.file?.isFile == true) {
                """<a class="action primary" href="/download/${entry.fileName}">从 PAD 下载 <small>已校验</small></a>"""
            } else {
                """<span class="action disabled">PAD 正在校验</span>"""
            }
            val cloudUrl = entry.cloudUrl
            val cloudActions = if (cloudUrl != null) {
                val safeUrl = escapeHtml(cloudUrl)
                """
                  <a class="action secondary" href="$safeUrl" target="_blank" rel="noopener noreferrer">HTTPS 云端下载</a>
                  <div class="cloud-address">
                    <span>云端实时地址</span>
                    <code>$safeUrl</code>
                    <button class="copy-cloud" type="button" data-url="$safeUrl" onclick="copyCloudUrl(this)">复制地址</button>
                  </div>
                """.trimIndent()
            } else {
                """<div class="cloud-address unavailable-address">云端地址暂未解析，请稍后刷新。</div>"""
            }
            """
              <article class="download$recommendedClass">
                <div class="icon">$icon</div>
                <div class="package-body">
                  <div class="package-title"><b>$title</b>$recommendedTag</div>
                  <p>$detail$version</p>
                  <div class="actions">$localAction $cloudActions</div>
                </div>
              </article>
            """.trimIndent()
        }
        val address = localIpv4Addresses().firstOrNull()?.let { "http://$it:$port" } ?: ""
        val wifiName = currentWifiName()
        val wifiTitle = if (wifiName.isNullOrBlank()) {
            "未读取到 Wi-Fi 名称"
        } else {
            escapeHtml(wifiName)
        }
        val wifiDetail = if (wifiName.isNullOrBlank()) {
            "请在 PAD 的“客户端”页授权读取 Wi-Fi 名称，并确认下载设备与 PAD 在同一网络。"
        } else {
            "下载设备必须与 PAD 连接同一个 Wi-Fi。"
        }
        val androidVersion = entries["android"]?.version?.takeIf(String::isNotBlank) ?: "当前版本"
        val metadata = packageSync.metadataStatus()
        val sourceText = when (metadata["source"]) {
            "newlink_https" -> "Newlink HTTPS 实时地址"
            "github_fallback" -> "GitHub 备用源"
            "error" -> "云端暂不可用，保留已验证缓存"
            else -> "已验证缓存"
        }
        return """<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>KEMI 客户端下载</title>
  <style>
    :root { color-scheme: light; --ink: #152039; --muted: #647089; --line: #dce3ef; --blue: #276ef1; --blue-dark: #1457d4; --pale: #f4f8ff; --ok: #14804a; }
    * { box-sizing: border-box; }
    body { margin: 0; background: #eef3fb; color: var(--ink); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }
    main { width: min(960px, calc(100% - 32px)); margin: 36px auto 60px; }
    .panel { background: #fff; border: 1px solid var(--line); border-radius: 20px; box-shadow: 0 12px 35px rgba(37, 62, 111, .08); }
    .hero { padding: 36px; background: linear-gradient(135deg, #fbfdff 0%, #edf4ff 100%); }
    .badge { display: inline-flex; gap: 8px; align-items: center; padding: 7px 11px; border-radius: 999px; background: #e7f8ef; color: var(--ok); font-size: 14px; font-weight: 700; }
    .dot { width: 8px; height: 8px; background: var(--ok); border-radius: 50%; box-shadow: 0 0 0 4px rgba(20,128,74,.13); }
    h1 { margin: 18px 0 8px; font-size: clamp(27px, 5vw, 38px); letter-spacing: -.03em; }
    .intro { max-width: 680px; margin: 0; color: var(--muted); font-size: 17px; line-height: 1.7; }
    .wifi-card { display: flex; align-items: center; gap: 15px; margin: 23px 0 14px; padding: 16px; border: 1px solid #b9d2ff; border-radius: 14px; background: #fff; }
    .wifi-symbol { display: grid; flex: 0 0 50px; width: 50px; height: 50px; place-items: center; border-radius: 14px; background: #e9f2ff; color: var(--blue-dark); font-size: 13px; font-weight: 800; }
    .wifi-card small, .wifi-card strong, .wifi-card p { display: block; }
    .wifi-card small { color: var(--muted); font-size: 12px; }
    .wifi-card strong { margin-top: 2px; font-size: 18px; }
    .wifi-card p { margin: 4px 0 0; color: var(--muted); font-size: 13px; line-height: 1.45; }
    .address { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 19px; border-radius: 15px; background: #17233c; color: #fff; }
    .address small { width: 100%; color: #b9c6dc; font-size: 13px; }
    .address code { flex: 1; min-width: 230px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: clamp(17px, 3vw, 21px); overflow-wrap: anywhere; }
    button { appearance: none; border: 0; cursor: pointer; font: inherit; font-weight: 700; }
    .copy { padding: 10px 15px; border-radius: 9px; background: #fff; color: #17233c; font-size: 14px; }
    section { margin-top: 24px; padding: 28px; }
    h2 { margin: 0; font-size: 22px; letter-spacing: -.02em; }
    .hint { margin: 8px 0 22px; color: var(--muted); line-height: 1.6; }
    .downloads { display: grid; grid-template-columns: repeat(2, 1fr); gap: 13px; }
    .download { display: flex; gap: 14px; align-items: flex-start; min-height: 238px; padding: 17px; text-align: left; border: 1px solid var(--line); border-radius: 14px; background: #fff; transition: .16s ease; }
    .download:hover { border-color: #a9c5fa; box-shadow: 0 8px 20px rgba(39,110,241,.09); transform: translateY(-1px); }
    .icon { display: grid; flex: 0 0 43px; width: 43px; height: 43px; place-items: center; border-radius: 12px; background: var(--pale); font-size: 21px; }
    .package-body { flex: 1; min-width: 0; }
    .package-title { display: flex; flex-wrap: wrap; align-items: center; }
    .package-title b { color: var(--ink); font-size: 16px; }
    .package-body p { min-height: 38px; margin: 5px 0 12px; color: var(--muted); font-size: 13px; line-height: 1.45; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .action { display: inline-flex; min-height: 38px; align-items: center; justify-content: center; padding: 9px 12px; border-radius: 9px; text-decoration: none; font-size: 13px; font-weight: 750; }
    .action small { margin-left: 5px; font-size: 10px; opacity: .8; }
    .primary { background: var(--blue); color: #fff; }
    .primary:hover, .primary:focus-visible { background: var(--blue-dark); }
    .secondary { border: 1px solid #a9c5fa; background: #f3f7ff; color: var(--blue-dark); }
    .secondary:hover, .secondary:focus-visible { border-color: var(--blue); background: #eaf2ff; }
    .disabled { background: #edf0f5; color: #8b94a6; cursor: wait; }
    .cloud-address { display: grid; grid-template-columns: 1fr auto; width: 100%; margin-top: 2px; padding: 10px; border: 1px solid #e2e7f0; border-radius: 9px; background: #f8fafc; }
    .cloud-address > span { grid-column: 1 / -1; margin-bottom: 5px; color: var(--muted); font-size: 11px; }
    .cloud-address code { min-width: 0; padding-right: 8px; color: #45516a; font: 11px/1.45 ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; word-break: break-all; }
    .copy-cloud { align-self: center; padding: 7px 9px; border: 1px solid #ccd6e7; border-radius: 7px; background: #fff; color: #34425c; font-size: 12px; }
    .unavailable-address { display: block; color: var(--muted); font-size: 12px; }
    .recommended { grid-column: 1 / -1; border-color: #95b9ff; background: #f6f9ff; }
    .tag { display: inline-block; margin: 0 0 5px 7px; padding: 3px 7px; border-radius: 5px; background: #dce9ff; color: var(--blue-dark); font-size: 11px; vertical-align: middle; }
    .footnote { margin-top: 18px; padding: 13px 15px; border-radius: 10px; background: #fff8e7; color: #6c571e; font-size: 13px; line-height: 1.6; }
    .meta { display: flex; flex-wrap: wrap; gap: 8px 18px; margin-top: 20px; color: var(--muted); font-size: 12px; }
    @media (max-width: 650px) { main { width: min(100% - 20px, 960px); margin-top: 10px; } .hero, section { padding: 23px 18px; } .downloads { grid-template-columns: 1fr; } .recommended { grid-column: auto; } .address { padding: 15px; } .download { min-height: 0; } .cloud-address { grid-template-columns: 1fr; } .copy-cloud { width: 100%; margin-top: 8px; } }
  </style>
</head>
<body>
  <main>
    <header class="panel hero">
      <div class="badge"><i class="dot"></i> 客户端下载服务已开启</div>
      <h1>下载 KEMI 客户端</h1>
      <p class="intro">先确认 Wi-Fi，再选择从 PAD 下载已校验文件，或直接使用 HTTPS 云端地址。</p>
      <div class="wifi-card">
        <div class="wifi-symbol">Wi-Fi</div>
        <div><small>当前 PAD 网络</small><strong>$wifiTitle</strong><p>$wifiDetail</p></div>
      </div>
      <div class="address">
        <small>在电脑或手机浏览器中输入</small>
        <code id="url">${escapeHtml(address)}</code>
        <button class="copy" type="button" onclick="copyUrl()">复制网址</button>
      </div>
    </header>
    <section class="panel">
      <h2>选择客户端</h2>
      <p class="hint">“从PAD下载”适合同一 Wi-Fi；“HTTPS云端下载”可直接下载，也可复制真实地址到其他浏览器。</p>
      <div class="downloads">$packageLinks</div>
      <div class="footnote">PAD 先通过 HTTPS 下载并校验完整文件，只向局域网提供校验成功的版本。打不开时，请确认两台设备连接同一个 Wi-Fi，并关闭电脑 VPN。</div>
      <div class="meta"><span>版本：${escapeHtml(androidVersion)}</span><span>上游：${escapeHtml(sourceText)}</span><span>可选：PAD本地 / HTTPS云端</span></div>
    </section>
  </main>
  <script>
    async function copyText(value) {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(value);
        return;
      }
      const area = document.createElement('textarea');
      area.value = value;
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      document.execCommand('copy');
      area.remove();
    }
    async function flashCopied(button, original) {
      button.textContent = '已复制';
      setTimeout(() => button.textContent = original, 1600);
    }
    async function copyUrl() {
      const button = document.querySelector('.copy');
      await copyText(document.getElementById('url').textContent);
      flashCopied(button, '复制网址');
    }
    async function copyCloudUrl(button) {
      await copyText(button.dataset.url);
      flashCopied(button, '复制地址');
    }
  </script>
</body>
</html>"""
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun writeText(output: OutputStream, status: Int, body: String, contentType: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(output, status, contentType, bytes.size.toLong(), null)
        output.write(bytes)
        output.flush()
    }

    private fun writeFile(output: OutputStream, file: File?, entry: PackageEntry) {
        if (file?.isFile != true) {
            writeText(output, 404, "Package is unavailable")
            return
        }
        writeHeaders(output, 200, entry.contentType, file.length(), entry.fileName)
        FileInputStream(file).use { input -> input.copyTo(output) }
        output.flush()
    }

    private fun writeHeaders(output: OutputStream, status: Int, contentType: String, contentLength: Long?, attachmentName: String?) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            contentLength?.let { append("Content-Length: $it\r\n") }
            attachmentName?.let { append("Content-Disposition: attachment; filename=\"$it\"\r\n") }
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun localIpv4Addresses(): List<String> {
        val addresses = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val interfaceAddresses = networkInterface.inetAddresses
            while (interfaceAddresses.hasMoreElements()) {
                val address = interfaceAddresses.nextElement()
                if (address is Inet4Address && isUsableLanAddress(address)) {
                    addresses += address.hostAddress
                }
            }
        }
        return addresses.distinct().sortedWith(compareBy<String> { !it.startsWith("192.168.") }.thenBy { it })
    }

    private fun isUsableLanAddress(address: InetAddress): Boolean =
        !address.isLoopbackAddress && !address.isAnyLocalAddress && !address.isLinkLocalAddress

    @Suppress("DEPRECATION")
    private fun currentWifiName(): String? {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ssid = manager?.connectionInfo?.ssid
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.trim()
        return ssid?.takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
    }

    private fun hasWifiNamePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

}
