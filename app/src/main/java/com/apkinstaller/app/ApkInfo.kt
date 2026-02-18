package com.apkinstaller.app

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File

data class ApkInfo(
    val file: File,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    var packageName: String? = null,
    var appName: String? = null,
    var versionName: String? = null,
    var versionCode: Long? = null,
    var isSelected: Boolean = false
) {
    val fileSizeFormatted: String
        get() {
            return when {
                fileSize >= 1024 * 1024 * 1024 -> String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0))
                fileSize >= 1024 * 1024 -> String.format("%.2f MB", fileSize / (1024.0 * 1024.0))
                fileSize >= 1024 -> String.format("%.2f KB", fileSize / 1024.0)
                else -> "$fileSize B"
            }
        }
    
    val displayName: String
        get() = appName ?: fileName
}
