package com.carriez.flutter_hbb

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Launches Android's trusted package installer for a verified cached update. */
object AndroidSelfUpdater {
    fun launch(
        activity: Activity,
        packageSync: ClientPackageSync,
        openPermissionSettings: Boolean,
        backupCurrentApk: Boolean,
    ): Map<String, Any> {
        val update = packageSync.resolveAndroidUpdate()
            ?: return mapOf(
                "status" to "not_ready",
                "message" to "新版本尚未下载并校验完成",
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            if (openPermissionSettings) {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    )
                )
            }
            return mapOf(
                "status" to "permission_required",
                "message" to "请允许 KEMI 安装此来源的应用，返回后将继续升级",
            )
        }

        val backup = if (backupCurrentApk) backupInstalledApk(activity) else null
        if (backup != null && !backup.ok) {
            return mapOf(
                "status" to "backup_failed",
                "message" to backup.message,
            )
        }

        val launchResult = launchApkFile(
            activity = activity,
            file = update.file,
            openPermissionSettings = openPermissionSettings,
            version = update.target.version,
        )
        return if (backup != null && launchResult["status"] == "launched") {
            launchResult + mapOf(
                "backupFileName" to backup.fileName,
                "message" to "当前版本已备份到下载目录，已打开Android系统安装确认界面",
            )
        } else {
            launchResult
        }
    }

    /** Installs an APK explicitly selected from the PAD-side file list. */
    fun launchLocalApk(
        activity: Activity,
        path: String,
        openPermissionSettings: Boolean,
    ): Map<String, Any> {
        val file = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return error("APK路径无效")
        }
        if (!file.isFile || !file.canRead() || !file.name.endsWith(".apk", ignoreCase = true)) {
            return error("APK文件不存在、不可读取或格式不正确")
        }
        if (activity.packageManager.getPackageArchiveInfo(file.absolutePath, 0) == null) {
            return error("系统无法识别这个APK安装包")
        }
        return launchApkFile(activity, file, openPermissionSettings, null)
    }

    /** Shares one explicitly selected PAD-side file through Android's chooser. */
    fun shareLocalFile(activity: Activity, path: String): Map<String, Any> {
        val file = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return error("文件路径无效")
        }
        if (!file.isFile || !file.canRead()) {
            return error("文件不存在或不可读取")
        }
        val uri = try {
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
        } catch (_: IllegalArgumentException) {
            return error("该文件不在PAD可分享的本地存储范围内")
        }
        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(activity.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (sendIntent.resolveActivity(activity.packageManager) == null) {
            return error("没有支持此文件类型的应用")
        }
        activity.startActivity(Intent.createChooser(sendIntent, "分享文件"))
        return mapOf(
            "status" to "launched",
            "message" to "已打开系统分享面板",
        )
    }

    private fun launchApkFile(
        activity: Activity,
        file: File,
        openPermissionSettings: Boolean,
        version: String?,
    ): Map<String, Any> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            if (openPermissionSettings) {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    )
                )
            }
            return mapOf(
                "status" to "permission_required",
                "message" to "请允许 KEMI 安装此来源的应用，返回后再次点击安装",
            )
        }

        val uri = try {
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
        } catch (_: IllegalArgumentException) {
            return error("该APK不在PAD可安装的本地存储范围内")
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (intent.resolveActivity(activity.packageManager) == null) {
            return mapOf(
                "status" to "error",
                "message" to "系统没有可用的 APK 安装程序",
            )
        }
        activity.startActivity(intent)
        return buildMap<String, Any> {
            put("status", "launched")
            put("message", "已打开Android系统安装确认界面")
            if (version != null) put("version", version)
        }
    }

    private fun error(message: String): Map<String, Any> =
        mapOf("status" to "error", "message" to message)

    private data class BackupResult(
        val ok: Boolean,
        val fileName: String = "",
        val message: String = "",
    )

    private fun backupInstalledApk(activity: Activity): BackupResult {
        val source = File(activity.applicationInfo.sourceDir)
        if (!source.isFile || !source.canRead()) {
            return BackupResult(false, message = "当前安装包不可读取，未开始升级")
        }
        if (!activity.applicationInfo.splitSourceDirs.isNullOrEmpty()) {
            return BackupResult(false, message = "当前版本是拆分安装包，无法备份为单个APK，未开始升级")
        }

        val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
        val versionName = packageInfo.versionName?.ifBlank { "unknown" } ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "KEMI远程办公-$versionName+$versionCode-备份-$timestamp.apk"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backupWithMediaStore(activity, source, fileName)
            } else {
                backupToLegacyDownloads(source, fileName)
            }
            BackupResult(true, fileName = fileName)
        } catch (error: Exception) {
            BackupResult(
                false,
                message = "备份当前版本失败：${error.message ?: "无法写入下载目录"}；未开始升级",
            )
        }
    }

    private fun backupWithMediaStore(activity: Activity, source: File, fileName: String) {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建备份文件")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法写入备份文件")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun backupToLegacyDownloads(source: File, fileName: String) {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists() && !downloads.mkdirs()) {
            throw IllegalStateException("无法创建下载目录")
        }
        source.copyTo(File(downloads, fileName), overwrite = false)
    }
}
