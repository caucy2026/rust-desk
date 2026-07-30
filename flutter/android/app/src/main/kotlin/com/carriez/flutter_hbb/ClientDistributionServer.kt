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
        private const val ASSET_ROOT = "client-dist"
        private const val SOCKET_TIMEOUT_MS = 7_000
    }

    private data class PackageEntry(
        val platform: String,
        val detail: String,
        val fileName: String,
        val assetPath: String?,
        val contentType: String,
    )

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
            "packages" to packageEntries().map { entry ->
                mapOf(
                    "platform" to entry.platform,
                    "detail" to entry.detail,
                    "available" to isAvailable(entry),
                )
            },
        )
    }

    private fun packageEntries() = listOf(
        PackageEntry(
            platform = "PAD / Android",
            detail = "当前 PAD 的 KEMI 客户端",
            fileName = "KEMI-remote-desktop-${versionName()}.apk",
            assetPath = null,
            contentType = "application/vnd.android.package-archive",
        ),
    ) + bundledPackages()

    private fun availablePackages() = packageEntries().filter(::isAvailable)

    private fun isAvailable(entry: PackageEntry): Boolean =
        entry.assetPath?.let(::assetExists) ?: File(context.applicationInfo.sourceDir).isFile

    private fun bundledPackages() = listOf(
        PackageEntry(
            platform = "Windows（x64）",
            detail = "下载后双击安装包",
            fileName = "KEMI-remote-desktop-windows-x64.exe",
            assetPath = "$ASSET_ROOT/KEMI-remote-desktop-windows-x64.exe",
            contentType = "application/vnd.microsoft.portable-executable",
        ),
        PackageEntry(
            platform = "macOS（Apple 芯片）",
            detail = "下载后拖入“应用程序”文件夹",
            fileName = "KEMI-remote-desktop-macos-arm64.zip",
            assetPath = "$ASSET_ROOT/KEMI-remote-desktop-macos-arm64.zip",
            contentType = "application/zip",
        ),
        PackageEntry(
            platform = "Linux（x86_64）",
            detail = "下载 AppImage 后按系统提示运行",
            fileName = "KEMI-remote-desktop-linux-x86_64.AppImage",
            assetPath = "$ASSET_ROOT/KEMI-remote-desktop-linux-x86_64.AppImage",
            contentType = "application/octet-stream",
        ),
    )

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).use { }
        true
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
                } else if (entry.assetPath == null) {
                    writeFile(output, File(context.applicationInfo.sourceDir), entry)
                } else {
                    writeAsset(output, entry)
                }
            }
        }
    }

    private fun buildHtml(): String {
        val packageLinks = packageEntries().joinToString(separator = "") { entry ->
            if (isAvailable(entry)) {
                """<a class="package" href="/download/${entry.fileName}"><b>${entry.platform}</b><span>${entry.detail}</span></a>"""
            } else {
                """<div class="package unavailable"><b>${entry.platform}</b><span>${entry.detail}（待导入）</span></div>"""
            }
        }
        return """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>KEMI 客户端下载</title><style>body{margin:0;background:#f1f5fb;color:#152039;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,PingFang SC,Microsoft YaHei,sans-serif}main{max-width:680px;margin:32px auto;padding:0 16px}.card{background:#fff;border:1px solid #dce3ef;border-radius:16px;padding:24px;box-shadow:0 8px 28px #15203912}h1{margin:0 0 8px;font-size:28px}p{color:#61708a;line-height:1.65}.notice{margin:20px 0;padding:13px 15px;border:1px solid #b9d2ff;border-radius:10px;background:#f7faff}.package{display:block;margin-top:12px;padding:16px;border:1px solid #dce3ef;border-radius:12px;color:#152039;text-decoration:none}.package:hover{border-color:#276ef1}.package b,.package span{display:block}.package span{margin-top:5px;color:#61708a;font-size:14px}.unavailable{opacity:.55;background:#f5f6f8}</style></head><body><main><div class="card"><h1>下载 KEMI 客户端</h1><p>请在需要安装客户端的设备浏览器中选择对应版本下载。</p><div class="notice"><b>请先连接同一个 Wi-Fi</b><br>PAD 与下载设备必须在同一局域网。</div>$packageLinks</div></main></body></html>"""
    }

    private fun writeText(output: OutputStream, status: Int, body: String, contentType: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(output, status, contentType, bytes.size.toLong(), null)
        output.write(bytes)
        output.flush()
    }

    private fun writeFile(output: OutputStream, file: File, entry: PackageEntry) {
        if (!file.isFile) {
            writeText(output, 404, "Package is unavailable")
            return
        }
        writeHeaders(output, 200, entry.contentType, file.length(), entry.fileName)
        FileInputStream(file).use { input -> input.copyTo(output) }
        output.flush()
    }

    private fun writeAsset(output: OutputStream, entry: PackageEntry) {
        val assetPath = entry.assetPath ?: return
        try {
            context.assets.open(assetPath).use { input ->
                // Assets can be compressed, so connection-close framing is used.
                writeHeaders(output, 200, entry.contentType, null, entry.fileName)
                input.copyTo(output)
                output.flush()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Cannot serve asset $assetPath", error)
            writeText(output, 404, "Package is unavailable")
        }
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

    private fun versionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "current"
    } catch (_: Exception) {
        "current"
    }
}
