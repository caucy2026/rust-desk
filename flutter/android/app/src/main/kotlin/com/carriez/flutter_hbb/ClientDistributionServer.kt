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
            )
        }
    }

    private fun availablePackages() = packageEntries().filter { it.file?.isFile == true }

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
        val packageLinks = packageEntries().joinToString(separator = "") { entry ->
            if (entry.file?.isFile == true) {
                val version = entry.version.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""
                """<a class="package" href="/download/${entry.fileName}"><b>${entry.platform}</b><span>${entry.detail}$version</span></a>"""
            } else {
                """<div class="package unavailable"><b>${entry.platform}</b><span>${entry.detail}（PAD 正在准备，请稍后刷新）</span></div>"""
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
