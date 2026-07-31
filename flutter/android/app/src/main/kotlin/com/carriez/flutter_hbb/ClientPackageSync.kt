package com.carriez.flutter_hbb

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
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
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/caucy2026/common-data/main/kemi-rustdesk/stable/manifest.json"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_MANIFEST_BYTES = 1024 * 1024
        private val SAFE_FILE_NAME = Regex("[A-Za-z0-9._+\\-]+")
        private val SAFE_SHA256 = Regex("[a-f0-9]{64}")
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

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "kemi-client-package-sync").apply { isDaemon = true }
    }
    private val syncing = AtomicBoolean(false)
    private val pendingLock = Any()
    private val pendingIds = linkedSetOf<String>()
    private val progressLock = Any()
    private val progress = mutableMapOf<String, Progress>()
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
                "state" to (currentProgress?.state ?: if (latestReady) "ready" else "missing"),
                "downloaded" to (currentProgress?.downloaded ?: 0L),
                "total" to (currentProgress?.total ?: target?.size ?: 0L),
                "message" to (currentProgress?.message ?: ""),
            )
        }
    }

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
        val part = File(cacheDir, "manifest.json.part")
        val connection = openConnection(MANIFEST_URL)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("版本清单请求失败：HTTP ${connection.responseCode}")
            }
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
            val parsed = parseManifest(part.readText())
            if (manifestFile.isFile && !manifestFile.readBytes().contentEquals(part.readBytes())) {
                manifestFile.copyTo(previousManifestFile, overwrite = true)
            }
            if (!replaceFile(part, manifestFile)) throw IllegalStateException("无法保存版本清单")
            return parsed
        } finally {
            connection.disconnect()
        }
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
            val size = item.getLong("size")
            val url = item.getString("url")
            if (!SAFE_FILE_NAME.matches(fileName) || !SAFE_SHA256.matches(sha256) || size <= 0) {
                throw IllegalArgumentException("客户端信息不合法：$id")
            }
            val uri = URL(url)
            if (uri.protocol != "https" ||
                uri.host !in setOf(
                    "api.github.com",
                    "github.com",
                    "objects.githubusercontent.com",
                    "release-assets.githubusercontent.com",
                )
            ) {
                throw IllegalArgumentException("客户端地址不在 GitHub 白名单：$id")
            }
            parsed += RemoteClientPackage(
                definition,
                item.getString("version"),
                fileName,
                size,
                sha256,
                url,
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
        val part = File(cacheDir, "${target.fileName}.part")
        var offset = part.takeIf(File::isFile)?.length() ?: 0L
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

    private fun cachedFile(target: RemoteClientPackage) = File(cacheDir, target.fileName)

    private fun verificationFile(file: File) = File(cacheDir, "${file.name}.sha256")

    private fun isVerified(file: File, target: RemoteClientPackage): Boolean =
        file.isFile && file.length() == target.size &&
            verificationFile(file).takeIf(File::isFile)?.readText()?.trim() == target.sha256

    private fun pruneObsoleteFiles(packages: List<RemoteClientPackage>) {
        val keep = packages.flatMap {
            listOf(it.fileName, "${it.fileName}.sha256", "${it.fileName}.part")
        }.toSet() + setOf(manifestFile.name, previousManifestFile.name, "manifest.json.part")
        cacheDir.listFiles()?.filter { it.name !in keep }?.forEach { file ->
            if (!file.delete()) Log.w(TAG, "Cannot remove obsolete cache file ${file.name}")
        }
    }

    private fun openConnection(url: String, rangeStart: Long = 0): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream,application/json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "KEMI-PAD/${installedPadVersion()}")
            if (rangeStart > 0) setRequestProperty("Range", "bytes=$rangeStart-")
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
