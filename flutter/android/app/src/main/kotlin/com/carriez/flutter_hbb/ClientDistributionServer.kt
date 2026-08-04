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
        private const val CLOUD_DOWNLOAD_PAGE_URL =
            "http://kemi-chat.newlinksz.com:21120/kemi-desk"
    }

    private data class PackageEntry(
        val id: String,
        val platform: String,
        val detail: String,
        val version: String,
        val fileName: String,
        val contentType: String,
        val file: File?,
        val backupUrl: String?,
        val isLatest: Boolean,
        val servingVersion: String,
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
        val urls = localIpv4Addresses().map { "http://$it:$port/clients" }
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
                version = target?.version ?: servedTarget?.version ?: "",
                fileName = fileName,
                contentType = definition.contentType,
                file = resolved?.file,
                backupUrl = target?.url?.takeIf(::isSafeBackupUrl),
                isLatest = resolved?.isLatest == true,
                servingVersion = resolved?.target?.version ?: "",
            )
        }
    }

    private fun availablePackages() = packageEntries().filter { it.file?.isFile == true }

    private fun isSafeBackupUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" &&
            uri.host.equals("cdn.newlink-sz.com", ignoreCase = true)
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
            "/clients" -> writeText(output, 200, buildHtml(directClients = true), "text/html; charset=utf-8")
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

    private fun buildHtml(directClients: Boolean = false): String {
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
                val status = if (entry.isLatest) "已校验" else "缓存 ${escapeHtml(entry.servingVersion)}"
                """<a class="action primary" href="/download/${entry.fileName}">从 PAD 下载 <small>$status</small></a>"""
            } else {
                """<span class="action disabled">PAD 正在校验</span>"""
            }
            val backupUrl = entry.backupUrl
            val backupActions = if (backupUrl != null) {
                val safeUrl = escapeHtml(backupUrl)
                """
                  <div class="backup-option">
                    <a class="action backup" href="$safeUrl" target="_blank" rel="noopener noreferrer">云备份下载</a>
                    <small>仅作为上面两种下载均失效情况下的备案</small>
                  </div>
                """.trimIndent()
            } else {
                """<span class="unavailable-address">云备份暂不可用</span>"""
            }
            """
              <article class="download$recommendedClass">
                <div class="icon">$icon</div>
                <div class="package-body">
                  <div class="package-title"><b>$title</b>$recommendedTag</div>
                  <p>$detail$version</p>
                  <div class="actions">$localAction $backupActions</div>
                </div>
              </article>
            """.trimIndent()
        }
        val address = localIpv4Addresses().firstOrNull()?.let { "http://$it:$port/clients" } ?: ""
        val cloudDownloadPageUrl = escapeHtml(CLOUD_DOWNLOAD_PAGE_URL)
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
            "github_fallback" -> "备用版本清单"
            "error" -> "云端暂不可用，保留已验证缓存"
            else -> "已验证缓存"
        }
        val pageIntro = if (directClients) {
            "请选择对应系统，直接从当前PAD下载已校验的客户端。"
        } else {
            "请选择同局域网下载或云端下载，两种方式均可进入客户端选择页面。"
        }
        val channelsHtml = if (directClients) "" else """
          <div class="channels">
            <article class="channel">
              <span class="channel-number">方式一</span>
              <h3>同局域网下载</h3>
              <p>下载设备与PAD处于同一局域网，并且网络允许设备互相访问时使用。</p>
              <a class="channel-url" id="url" href="$address">${escapeHtml(address)}</a>
            </article>
            <article class="channel">
              <span class="channel-number">方式二</span>
              <h3>云端下载</h3>
              <p>无需访问PAD的局域网IP，直接进入KEMI云端客户端下载页面。</p>
              <a class="channel-url" href="$cloudDownloadPageUrl" target="_blank" rel="noopener noreferrer">$cloudDownloadPageUrl</a>
            </article>
          </div>
        """.trimIndent()
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
    .channels { display: grid; grid-template-columns: repeat(2, 1fr); margin-top: 22px; overflow: hidden; border: 1px solid #bfd1ef; border-radius: 15px; background: #fff; }
    .channel { display: flex; min-width: 0; flex-direction: column; padding: 18px; }
    .channel + .channel { border-left: 1px solid #d9e4f3; }
    .channel-number { color: var(--blue-dark); font-size: 12px; font-weight: 800; }
    .channel h3 { margin: 5px 0; font-size: 19px; }
    .channel p { min-height: 42px; margin: 0 0 12px; color: var(--muted); font-size: 13px; line-height: 1.55; }
    .channel-url { display: block; min-height: 58px; padding: 11px; border-radius: 9px; background: #f4f7fb; color: var(--blue-dark); font: 13px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; text-decoration: none; }
    .channel-url:hover, .channel-url:focus-visible { background: #e9f2ff; text-decoration: underline; }
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
    .backup { border: 1px solid #d7b867; background: #fff9e9; color: #765b13; }
    .backup:hover, .backup:focus-visible { border-color: #b99535; background: #fff3d1; }
    .backup-option { display: flex; width: 100%; align-items: center; gap: 8px; }
    .backup-option > small { color: var(--muted); font-size: 10px; line-height: 1.35; }
    .disabled { background: #edf0f5; color: #8b94a6; cursor: wait; }
    .unavailable-address { display: block; color: var(--muted); font-size: 12px; }
    .recommended { grid-column: 1 / -1; border-color: #95b9ff; background: #f6f9ff; }
    .tag { display: inline-block; margin: 0 0 5px 7px; padding: 3px 7px; border-radius: 5px; background: #dce9ff; color: var(--blue-dark); font-size: 11px; vertical-align: middle; }
    .meta { display: flex; flex-wrap: wrap; gap: 8px 18px; margin-top: 20px; color: var(--muted); font-size: 12px; }
    @media (max-width: 650px) { main { width: min(100% - 20px, 960px); margin-top: 10px; } .hero, section { padding: 23px 18px; } .channels, .downloads { grid-template-columns: 1fr; } .channel + .channel { border-top: 1px solid #d9e4f3; border-left: 0; } .recommended { grid-column: auto; } .address { padding: 15px; } .download { min-height: 0; } .backup-option { align-items: flex-start; flex-direction: column; } }
  </style>
</head>
<body>
  <main>
    <header class="panel hero">
      <div class="badge"><i class="dot"></i> 客户端下载服务已开启</div>
      <h1>下载 KEMI 客户端</h1>
      <p class="intro">$pageIntro</p>
      <div class="wifi-card">
        <div class="wifi-symbol">Wi-Fi</div>
        <div><small>当前 PAD 网络</small><strong>$wifiTitle</strong><p>$wifiDetail</p></div>
      </div>
      $channelsHtml
    </header>
    <section class="panel" id="clients">
      <h2>选择客户端</h2>
      <p class="hint">选择对应平台下载客户端。</p>
      <div class="downloads">$packageLinks</div>
      <div class="meta"><span>版本：${escapeHtml(androidVersion)}</span><span>上游：${escapeHtml(sourceText)}</span><span>主入口：同局域网 / KEMI云端</span></div>
    </section>
  </main>
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
