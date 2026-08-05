package com.carriez.flutter_hbb

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider

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

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            update.file,
        )
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
        return mapOf(
            "status" to "launched",
            "version" to update.target.version,
            "message" to "已打开系统升级确认界面",
        )
    }
}
