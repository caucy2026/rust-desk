package com.carriez.flutter_hbb

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Launches Android's trusted package installer for a verified cached update. */
object AndroidSelfUpdater {
    fun launch(
        activity: Activity,
        packageSync: ClientPackageSync,
        openPermissionSettings: Boolean,
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

        return launchApkFile(
            activity = activity,
            file = update.file,
            openPermissionSettings = openPermissionSettings,
            version = update.target.version,
        )
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
}
