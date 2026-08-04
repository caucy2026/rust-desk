package com.carriez.flutter_hbb

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class ClientPackageDefinition(
    val id: String,
    val platform: String,
    val detail: String,
    val contentType: String,
)

data class RemoteClientPackage(
    val definition: ClientPackageDefinition,
    val version: String,
    val fileName: String,
    val size: Long,
    val sha256: String,
    val url: String,
    val md5: String = "",
)

data class ResolvedClientPackage(
    val target: RemoteClientPackage,
    val file: File,
    val isLatest: Boolean,
)

/**
 * Keeps a last-known-good local cache of the four KEMI clients.
 *
 * The manifest and downloads are allow-listed, size checked and SHA-256 checked.
 * A new package is exposed to the LAN server only after its .part file has been
 * atomically promoted and a verification sidecar has been written.
 */
class ClientPackageSync private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ClientPackageSync"
        private const val RAW_MANIFEST_URL =
            "https://raw.githubusercontent.com/caucy2026/common-data/main/kemi-rustdesk/stable/manifest.json"
        private const val CDN_MANIFEST_URL =
            "https://cdn.jsdelivr.net/gh/caucy2026/common-data@main/kemi-rustdesk/stable/manifest.json"
        private const val CLOUD_PLUG_DATA_URL =
            "https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name="
        private const val CLOUD_PORTAL_BASE_URL =
            "http://kemi-chat.newlinksz.com:21120/kemi-desk/download"
        private const val CLOUD_MANIFEST_NAME = "release-manifest"
        private const val CLOUD_CHECKSUMS_NAME = "SHA256SUMS"
        private const val CLOUD_METADATA_CONNECT_TIMEOUT_MS = 8_000
        private const val CLOUD_METADATA_READ_TIMEOUT_MS = 15_000
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val RAW_MANIFEST_CONNECT_TIMEOUT_MS = 4_000
        private const val CDN_MANIFEST_CONNECT_TIMEOUT_MS = 8_000
        private const val MANIFEST_READ_TIMEOUT_MS = 13_000
        private const val MAX_MANIFEST_BYTES = 1024 * 1024
        private val SAFE_FILE_NAME = Regex("[A-Za-z0-9._+\\-]+")
        private val SAFE_SHA256 = Regex("[a-f0-9]{64}")
        private val SAFE_MD5 = Regex("[a-f0-9]{32}")
        private val CLOUD_ASSET_NAMES = mapOf(
            "android" to "KEMI-PAD",
            "windows" to "KEMI-Windows",
            "macos" to "KEMI-macOS",
            "linux" to "KEMI-Linux",
        )
        private val ALLOWED_PACKAGE_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "cdn.newlink-sz.com",
        )
        private val ALLOWED_REMOTE_HOSTS = ALLOWED_PACKAGE_HOSTS + setOf(
            "www.newlinksz.cn",
            "raw.githubusercontent.com",
            "cdn.jsdelivr.net",
        )
        private val instanceLock = Any()
        @Volatile private var instance: ClientPackageSync? = null

        val definitions = listOf(
            ClientPackageDefinition(
                "android",
                "PAD / Android",
                "KEMI PAD 客户端",
                "application/vnd.android.package-archive",
            ),
            ClientPackageDefinition(
                "windows",
                "Windows（x64）",
                "下载后双击安装包",
                "application/vnd.microsoft.portable-executable",
            ),
            ClientPackageDefinition(
                "macos",
                "macOS（Apple 芯片）",
                "下载后拖入“应用程序”文件夹",
                "application/zip",
            ),
            ClientPackageDefinition(
                "linux",
                "Linux（x86_64）",
                "下载 AppImage 后按系统提示运行",
                "application/octet-stream",
            ),
        )

        fun get(context: Context): ClientPackageSync =
            instance ?: synchronized(instanceLock) {
                instance ?: ClientPackageSync(context.applicationContext).also { instance = it }
            }
    }

    private data class Progress(
        val state: String,
        val downloaded: Long = 0,
        val total: Long = 0,
        val message: String = "",
    )

    private data class CloudAsset(
        val name: String,
        val nickname: String,
        val md5: String,
        val url: String,
    )

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "kemi-client-package-sync").apply { isDaemon = true }
    }
    private val syncing = AtomicBoolean(false)
    private val pendingLock = Any()
    private val pendingIds = linkedSetOf<String>()
    private val progressLock = Any()
    private val progress = mutableMapOf<String, Progress>()
    @Volatile private var metadataSource = "cached"
    @Volatile private var metadataUpdatedAt = 0L
    @Volatile private var metadataMessage = ""
    private val cacheDir: File by lazy {
        (context.getExternalFilesDir("client-cache") ?: File(context.filesDir, "client-cache"))
            .apply { mkdirs() }
    }
    private val manifestFile: File by lazy { File(cacheDir, "manifest.json") }
    private val previousManifestFile: File by lazy { File(cacheDir, "manifest.previous.json") }

    fun syncAllAsync(onComplete: ((Boolean) -> Unit)? = null): Boolean {
        if (!syncing.compareAndSet(false, true)) return false
        executor.execute {
            val ok = try {
                val packages = refreshManifest()
                var allOk = true
                packages.forEach { target ->
                    if (!ensurePackage(target)) allOk = false
                }
                if (allOk) {
                    previousManifestFile.delete()
                    pruneObsoleteFiles(packages)
                }
                allOk
            } catch (error: Exception) {
                Log.w(TAG, "Client package sync failed", error)
                false
            } finally {
                syncing.set(false)
            }
            onComplete?.invoke(ok)
            startPendingDownloads()
        }
        return true
    }

    fun syncOneAsync(id: String): Boolean {
        if (definitions.none { it.id == id }) return false
        synchronized(pendingLock) { pendingIds += id }
        startPendingDownloads()
        return true
    }

    private fun startPendingDownloads() {
        if (synchronized(pendingLock) { pendingIds.isEmpty() }) return
        if (!syncing.compareAndSet(false, true)) return
        executor.execute {
            while (true) {
                val id = synchronized(pendingLock) {
                    pendingIds.firstOrNull()?.also { pendingIds.remove(it) }
                } ?: break
                try {
                    val target = refreshManifest().firstOrNull { it.definition.id == id }
                        ?: throw IllegalStateException("远端清单中没有该客户端")
                    ensurePackage(target)
                } catch (error: Exception) {
                    Log.w(TAG, "Cannot sync client package $id", error)
                    setProgress(id, Progress("error", message = error.message ?: "下载失败"))
                }
            }
            syncing.set(false)
            startPendingDownloads()
        }
    }

    fun packageStatus(): List<Map<String, Any?>> {
        val targets = readManifest().associateBy { it.definition.id }
        return definitions.map { definition ->
            val target = targets[definition.id]
            val currentProgress = synchronized(progressLock) { progress[definition.id] }
            val resolved = resolvePackage(definition.id)
            val latestReady = resolved?.isLatest == true
            mapOf(
                "id" to definition.id,
                "platform" to definition.platform,
                "detail" to definition.detail,
                "version" to (target?.version ?: if (definition.id == "android") installedPadVersion() else ""),
                "available" to latestReady,
                "fallbackAvailable" to (resolved != null && !latestReady),
                "servingVersion" to (resolved?.target?.version ?: ""),
                // The URL has already passed the HTTPS host allow-list while
                // parsing the manifest. Flutter only displays/copies it; the
                // local HTTP server remains the source of cached packages.
                "cloudUrl" to (target?.url ?: ""),
                // Stable hbbc route. hbbc resolves this to the current,
                // validated Newlink CDN object, so QR codes never expire.
                "cloudPortalUrl" to cloudPortalUrl(definition.id),
                "state" to (currentProgress?.state ?: if (latestReady) "ready" else "missing"),
                "downloaded" to (currentProgress?.downloaded ?: 0L),
                "total" to (currentProgress?.total ?: target?.size ?: 0L),
                "message" to (currentProgress?.message ?: ""),
            )
        }
    }

    fun cloudPortalUrl(id: String): String = "$CLOUD_PORTAL_BASE_URL/$id"

    fun metadataStatus(): Map<String, Any?> = mapOf(
        "source" to metadataSource,
        "updatedAt" to metadataUpdatedAt,
        "message" to metadataMessage,
    )

    fun packageForId(id: String): RemoteClientPackage? =
        readManifest().firstOrNull { it.definition.id == id }

    fun resolvePackage(id: String): ResolvedClientPackage? {
        val target = packageForId(id)
        resolveTarget(target)?.let { return ResolvedClientPackage(it.first, it.second, true) }
        val previous = readPreviousManifest().firstOrNull { it.definition.id == id }
        resolveTarget(previous)?.let { return ResolvedClientPackage(it.first, it.second, false) }
        if (id == "android" && target == null) {
            val installed = File(context.applicationInfo.sourceDir)
            if (installed.isFile) {
                val localTarget = RemoteClientPackage(
                    definitions.first { it.id == "android" },
                    installedPadVersion(),
                    currentAndroidFileName(),
                    installed.length(),
                    "",
                    "",
                )
                return ResolvedClientPackage(localTarget, installed, true)
            }
        }
        return null
    }

    fun resolveDownloadFile(id: String): File? = resolvePackage(id)?.file

    fun currentAndroidFileName(): String =
        packageForId("android")?.takeIf { it.version == installedPadVersion() }?.fileName
            ?: "KEMI-remote-desktop-PAD-${installedPadVersion()}.apk"

    private fun refreshManifest(): List<RemoteClientPackage> {
        try {
            val packages = downloadCloudManifest()
            metadataSource = "newlink_https"
            metadataUpdatedAt = System.currentTimeMillis()
            metadataMessage = "云端实时地址已刷新"
            return packages
        } catch (error: Exception) {
            metadataMessage = "云盘地址解析失败，正在使用备用版本清单：${error.message ?: "未知错误"}"
            Log.w(TAG, "Cannot load Newlink cloud manifest", error)
        }
        val cacheBust = System.currentTimeMillis() / 60_000
        val sources = listOf(
            RAW_MANIFEST_URL to RAW_MANIFEST_CONNECT_TIMEOUT_MS,
            "$CDN_MANIFEST_URL?_t=$cacheBust" to CDN_MANIFEST_CONNECT_TIMEOUT_MS,
        )
        var lastError: Exception? = null
        sources.forEach { (url, connectTimeout) ->
            try {
                val packages = downloadManifest(url, connectTimeout)
                metadataSource = "github_fallback"
                metadataUpdatedAt = System.currentTimeMillis()
                return packages
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "Cannot load manifest from ${URL(url).host}", error)
            }
        }
        metadataSource = "error"
        throw lastError ?: IllegalStateException("无法读取客户端版本清单")
    }

    private fun downloadCloudManifest(): List<RemoteClientPackage> {
        val manifestAsset = fetchCloudAsset(CLOUD_MANIFEST_NAME)
        if (manifestAsset.nickname != "release-manifest.json") {
            throw IllegalStateException("云端版本清单文件名不匹配")
        }
        val root = JSONObject(String(downloadCloudAsset(manifestAsset), Charsets.UTF_8))
        if (root.optInt("schema_version") != 1) {
            throw IllegalArgumentException("云端版本清单格式不支持")
        }

        val checksumAsset = fetchCloudAsset(CLOUD_CHECKSUMS_NAME)
        val checksums = parseChecksums(String(downloadCloudAsset(checksumAsset), Charsets.UTF_8))
        val targets = root.getJSONArray("targets")
        for (index in 0 until targets.length()) {
            val item = targets.getJSONObject(index)
            val id = item.getString("id")
            val plugName = CLOUD_ASSET_NAMES[id]
                ?: throw IllegalArgumentException("云端清单包含未知平台：$id")
            val expectedFile = item.getString("file")
            val expectedSha = item.getString("sha256").lowercase()
            if (checksums[expectedFile] != expectedSha) {
                throw IllegalStateException("云端 SHA256SUMS 与版本清单不一致：$id")
            }
            val asset = fetchCloudAsset(plugName)
            if (asset.nickname != expectedFile) {
                throw IllegalStateException("云端客户端文件名不匹配：$id")
            }
            item.put("url", asset.url)
            item.put("md5", asset.md5)
            Log.i(TAG, "Resolved Newlink HTTPS asset id=$id file=$expectedFile host=${URL(asset.url).host}")
        }
        root.put("cloud_urls_status", "active")
        root.put("cloud_metadata_resolved_at", System.currentTimeMillis())
        return saveManifest(root.toString(2)).also {
            Log.i(TAG, "Newlink HTTPS manifest resolved with ${it.size} targets")
        }
    }

    private fun fetchCloudAsset(name: String): CloudAsset {
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8.name())
        val bytes = readRemoteBytes(
            "$CLOUD_PLUG_DATA_URL$encodedName",
            CLOUD_METADATA_CONNECT_TIMEOUT_MS,
            CLOUD_METADATA_READ_TIMEOUT_MS,
        )
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.optInt("code", -1) != 0) throw IllegalStateException("云端接口返回失败：$name")
        val data = root.optJSONArray("data") ?: JSONArray()
        val item = (0 until data.length())
            .map { data.getJSONObject(it) }
            .firstOrNull { it.optString("name") == name }
            ?: throw IllegalStateException("云端接口缺少数据：$name")
        val nickname = item.optString("nickname").trim()
        val md5 = item.optString("md5").lowercase()
        val url = item.optString("url").trim()
        if (nickname.isBlank() || !SAFE_MD5.matches(md5)) {
            throw IllegalStateException("云端文件信息不完整：$name")
        }
        validateHttpsUrl(url, setOf("cdn.newlink-sz.com"), "云端文件 $name")
        return CloudAsset(name, nickname, md5, url)
    }

    private fun downloadCloudAsset(asset: CloudAsset): ByteArray {
        val bytes = readRemoteBytes(
            asset.url,
            CLOUD_METADATA_CONNECT_TIMEOUT_MS,
            CLOUD_METADATA_READ_TIMEOUT_MS,
        )
        if (md5(bytes) != asset.md5) throw IllegalStateException("云端文件 MD5 校验失败：${asset.name}")
        return bytes
    }

    private fun readRemoteBytes(url: String, connectTimeout: Int, readTimeout: Int): ByteArray {
        val connection = openConnection(url, connectTimeoutMs = connectTimeout, readTimeoutMs = readTimeout)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("云端请求失败：HTTP ${connection.responseCode}")
            }
            validateHttpsUrl(connection.url.toString(), ALLOWED_REMOTE_HOSTS, "云端响应")
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_MANIFEST_BYTES) throw IllegalStateException("云端元数据过大")
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseChecksums(text: String): Map<String, String> = text.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .associate { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size != 2 || !SAFE_SHA256.matches(parts[0]) || !SAFE_FILE_NAME.matches(parts[1])) {
                throw IllegalArgumentException("云端 SHA256SUMS 格式错误")
            }
            parts[1] to parts[0]
        }

    private fun downloadManifest(url: String, connectTimeout: Int): List<RemoteClientPackage> {
        val part = File(cacheDir, "manifest.json.part")
        val connection = openConnection(
            url,
            connectTimeoutMs = connectTimeout,
            readTimeoutMs = MANIFEST_READ_TIMEOUT_MS,
        )
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("版本清单请求失败：HTTP ${connection.responseCode}")
            }
            validateHttpsUrl(connection.url.toString(), ALLOWED_REMOTE_HOSTS, "版本清单响应")
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_MANIFEST_BYTES) throw IllegalStateException("版本清单过大")
                        output.write(buffer, 0, count)
                    }
                }
            }
            return promoteManifestPart(part)
        } finally {
            connection.disconnect()
        }
    }

    private fun saveManifest(text: String): List<RemoteClientPackage> {
        val part = File(cacheDir, "manifest.json.part")
        part.writeText(text)
        return promoteManifestPart(part)
    }

    private fun promoteManifestPart(part: File): List<RemoteClientPackage> {
        val parsed = parseManifest(part.readText())
        if (manifestFile.isFile && !manifestFile.readBytes().contentEquals(part.readBytes())) {
            manifestFile.copyTo(previousManifestFile, overwrite = true)
        }
        if (!replaceFile(part, manifestFile)) throw IllegalStateException("无法保存版本清单")
        return parsed
    }

    private fun readManifest(): List<RemoteClientPackage> = try {
        if (manifestFile.isFile) parseManifest(manifestFile.readText()) else emptyList()
    } catch (error: Exception) {
        Log.w(TAG, "Ignoring invalid cached manifest", error)
        emptyList()
    }

    private fun readPreviousManifest(): List<RemoteClientPackage> = try {
        if (previousManifestFile.isFile) parseManifest(previousManifestFile.readText()) else emptyList()
    } catch (error: Exception) {
        Log.w(TAG, "Ignoring invalid previous manifest", error)
        emptyList()
    }

    private fun resolveTarget(target: RemoteClientPackage?): Pair<RemoteClientPackage, File>? {
        target ?: return null
        if (target.definition.id == "android" && target.version == installedPadVersion()) {
            val installed = File(context.applicationInfo.sourceDir)
            if (installed.isFile && installed.length() == target.size) return target to installed
        }
        val cached = cachedFile(target)
        return if (isVerified(cached, target)) target to cached else null
    }

    private fun parseManifest(text: String): List<RemoteClientPackage> {
        val root = JSONObject(text)
        if (root.optInt("schema_version") != 1) throw IllegalArgumentException("不支持的清单格式")
        val targets = root.getJSONArray("targets")
        val parsed = mutableListOf<RemoteClientPackage>()
        val seen = mutableSetOf<String>()
        for (index in 0 until targets.length()) {
            val item = targets.getJSONObject(index)
            val id = item.getString("id")
            val definition = definitions.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("清单包含未知平台：$id")
            if (!seen.add(id)) throw IllegalArgumentException("清单平台重复：$id")
            val fileName = item.getString("file")
            val sha256 = item.getString("sha256").lowercase()
            val md5 = item.optString("md5").lowercase()
            val size = item.getLong("size")
            val url = item.getString("url")
            if (!SAFE_FILE_NAME.matches(fileName) || !SAFE_SHA256.matches(sha256) ||
                (md5.isNotBlank() && !SAFE_MD5.matches(md5)) || size <= 0
            ) {
                throw IllegalArgumentException("客户端信息不合法：$id")
            }
            validateHttpsUrl(url, ALLOWED_PACKAGE_HOSTS, "客户端地址 $id")
            parsed += RemoteClientPackage(
                definition,
                item.getString("version"),
                fileName,
                size,
                sha256,
                url,
                md5,
            )
        }
        if (seen != definitions.map { it.id }.toSet()) {
            throw IllegalArgumentException("清单必须同时包含四个平台")
        }
        return parsed
    }

    private fun ensurePackage(target: RemoteClientPackage): Boolean {
        if (target.definition.id == "android") {
            val installed = File(context.applicationInfo.sourceDir)
            if (target.version == installedPadVersion() &&
                installed.length() == target.size &&
                sha256(installed) == target.sha256
            ) {
                setProgress(target.definition.id, Progress("ready", target.size, target.size))
                return true
            }
        }
        val destination = cachedFile(target)
        if (isVerified(destination, target)) {
            setProgress(target.definition.id, Progress("ready", target.size, target.size))
            return true
        }
        val part = File(cacheDir, "${target.sha256.take(16)}-${target.fileName}.part")
        var offset = part.takeIf(File::isFile)?.length() ?: 0L
        if (offset > 0 && part.inputStream().buffered().use { it.read() } == '{'.code) {
            // GitHub Assets API metadata from an older client is not resumable binary data.
            part.delete()
            offset = 0
        }
        if (offset > target.size) {
            part.delete()
            offset = 0
        }
        setProgress(target.definition.id, Progress("downloading", offset, target.size, "正在下载"))
        val connection = openConnection(target.url, offset)
        try {
            val code = connection.responseCode
            val append = offset > 0 && code == HttpURLConnection.HTTP_PARTIAL
            if (code !in 200..299) throw IllegalStateException("下载请求失败：HTTP $code")
            validateHttpsUrl(connection.url.toString(), ALLOWED_PACKAGE_HOSTS, "客户端下载响应")
            if (connection.contentType?.contains("application/json", ignoreCase = true) == true) {
                part.delete()
                throw IllegalStateException("云端返回了JSON而不是客户端文件")
            }
            if (!append) offset = 0
            RandomAccessFile(part, "rw").use { output ->
                if (!append) output.setLength(0)
                output.seek(offset)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = offset
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        setProgress(
                            target.definition.id,
                            Progress("downloading", downloaded, target.size, "正在下载"),
                        )
                    }
                }
            }
            if (part.length() != target.size) throw IllegalStateException("文件大小校验失败")
            setProgress(
                target.definition.id,
                Progress("verifying", target.size, target.size, "正在校验文件"),
            )
            if (sha256(part) != target.sha256) {
                part.delete()
                throw IllegalStateException("SHA-256 校验失败，已丢弃异常文件")
            }
            if (target.md5.isNotBlank() && md5(part) != target.md5) {
                part.delete()
                throw IllegalStateException("MD5 校验失败，已丢弃异常文件")
            }
            if (!replaceFile(part, destination)) throw IllegalStateException("无法保存客户端")
            verificationFile(destination).writeText(target.sha256)
            setProgress(target.definition.id, Progress("ready", target.size, target.size, "下载完成"))
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Cannot download ${target.definition.id} ${target.version}", error)
            setProgress(
                target.definition.id,
                Progress("error", part.takeIf(File::isFile)?.length() ?: 0L, target.size, error.message ?: "下载失败"),
            )
            return false
        } finally {
            connection.disconnect()
        }
    }

    private fun cachedFile(target: RemoteClientPackage) =
        File(cacheDir, "${target.sha256.take(16)}-${target.fileName}")

    private fun verificationFile(file: File) = File(cacheDir, "${file.name}.sha256")

    private fun isVerified(file: File, target: RemoteClientPackage): Boolean =
        file.isFile && file.length() == target.size &&
            verificationFile(file).takeIf(File::isFile)?.readText()?.trim() == target.sha256

    private fun pruneObsoleteFiles(packages: List<RemoteClientPackage>) {
        val keep = packages.flatMap {
            val cacheName = cachedFile(it).name
            listOf(cacheName, "$cacheName.sha256", "${it.sha256.take(16)}-${it.fileName}.part")
        }.toSet() + setOf(manifestFile.name, previousManifestFile.name, "manifest.json.part")
        cacheDir.listFiles()?.filter { it.name !in keep }?.forEach { file ->
            if (!file.delete()) Log.w(TAG, "Cannot remove obsolete cache file ${file.name}")
        }
    }

    private fun openConnection(
        url: String,
        rangeStart: Long = 0,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): HttpURLConnection {
        validateHttpsUrl(url, ALLOWED_REMOTE_HOSTS, "远程请求")
        val targetUrl = URL(url)
        return (targetUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            useCaches = false
            requestMethod = "GET"
            setRequestProperty(
                "Accept",
                if (targetUrl.host == "api.github.com") "application/octet-stream"
                else "application/json,application/octet-stream",
            )
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "KEMI-PAD/${installedPadVersion()}")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            if (rangeStart > 0) setRequestProperty("Range", "bytes=$rangeStart-")
        }
    }

    private fun validateHttpsUrl(url: String, allowedHosts: Set<String>, label: String) {
        val parsed = try {
            URL(url)
        } catch (_: Exception) {
            throw IllegalArgumentException("$label 不是有效 URL")
        }
        if (parsed.protocol != "https" || parsed.host.lowercase() !in allowedHosts) {
            throw IllegalArgumentException("$label 不在 HTTPS 白名单")
        }
    }

    private fun replaceFile(source: File, destination: File): Boolean {
        return try {
            Os.rename(source.absolutePath, destination.absolutePath)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Cannot atomically replace ${destination.name}", error)
            false
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun installedPadVersion(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        "${info.versionName ?: "current"}+$code"
    } catch (_: Exception) {
        "current"
    }

    private fun setProgress(id: String, value: Progress) {
        synchronized(progressLock) { progress[id] = value }
    }
}
