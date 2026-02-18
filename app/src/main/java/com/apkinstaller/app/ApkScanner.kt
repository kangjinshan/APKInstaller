package com.apkinstaller.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ApkScanner(private val context: Context) {
    
    /**
     * 扫描手机中的所有APK文件
     */
    suspend fun scanApkFiles(onApkFound: (ApkInfo) -> Unit): List<ApkInfo> = withContext(Dispatchers.IO) {
        val apkList = mutableListOf<ApkInfo>()
        val scannedPaths = mutableSetOf<String>() // 避免重复扫描
        
        // 获取常见的APK存储目录
        val scanDirs = getCommonApkDirectories()
        
        for (dir in scanDirs) {
            try {
                if (dir.exists() && dir.isDirectory && !scannedPaths.contains(dir.absolutePath)) {
                    scannedPaths.add(dir.absolutePath)
                    scanDirectory(dir, apkList, onApkFound)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 按修改时间倒序排序（最新的在前面）
        apkList.sortByDescending { it.file.lastModified() }
        
        apkList
    }
    
    /**
     * 获取常见的APK下载目录
     */
    private fun getCommonApkDirectories(): List<File> {
        val directories = mutableListOf<File>()
        
        // 主要存储路径
        val externalStorage = Environment.getExternalStorageDirectory()
        
        // 1. Download 目录（包含大小写变体）
        directories.add(File(externalStorage, "Download"))
        directories.add(File(externalStorage, "Downloads"))
        directories.add(File(externalStorage, "download"))  // 小写版本
        directories.add(File(externalStorage, "downloads")) // 小写复数版本
        directories.add(File(externalStorage, "Download/WeiXin")) // 微信专用目录
        directories.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        
        // 2. 微信下载目录
        directories.add(File(externalStorage, "tencent/MicroMsg/Download"))
        directories.add(File(externalStorage, "Android/data/com.tencent.mm/MicroMsg/Download"))
        directories.add(File(externalStorage, "Tencent/MicroMsg/Download"))
        
        // 3. QQ下载目录
        directories.add(File(externalStorage, "tencent/QQfile_recv"))
        directories.add(File(externalStorage, "Tencent/QQfile_recv"))
        
        // 4. 浏览器下载目录
        directories.add(File(externalStorage, "browser/download"))
        directories.add(File(externalStorage, "UCDownloads"))
        
        // 5. 其他常见目录
        directories.add(File(externalStorage, "0/Download"))
        directories.add(externalStorage)
        
        // 6. 应用私有目录
        try {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                directories.add(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return directories.distinctBy { it.absolutePath }
    }
    
    /**
     * 递归扫描目录中的APK文件
     */
    private suspend fun scanDirectory(
        directory: File,
        apkList: MutableList<ApkInfo>,
        onApkFound: (ApkInfo) -> Unit,
        maxDepth: Int = 3,
        currentDepth: Int = 0
    ) {
        if (currentDepth >= maxDepth) return
        
        try {
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                try {
                    when {
                        file.isFile && file.extension.equals("apk", ignoreCase = true) -> {
                            val apkInfo = createApkInfo(file)
                            apkList.add(apkInfo)
                            withContext(Dispatchers.Main) {
                                onApkFound(apkInfo)
                            }
                        }
                        file.isDirectory && !file.name.startsWith(".") -> {
                            // 递归扫描子目录（限制深度避免性能问题）
                            scanDirectory(file, apkList, onApkFound, maxDepth, currentDepth + 1)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 创建APK信息对象
     */
    private fun createApkInfo(file: File): ApkInfo {
        val apkInfo = ApkInfo(
            file = file,
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length()
        )
        
        // 尝试解析APK信息
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_META_DATA
            )
            
            packageInfo?.let { info ->
                apkInfo.packageName = info.packageName
                apkInfo.versionName = info.versionName
                apkInfo.versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
                
                // 获取应用名称
                info.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = file.absolutePath
                    appInfo.publicSourceDir = file.absolutePath
                    apkInfo.appName = packageManager.getApplicationLabel(appInfo).toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 解析失败时，使用文件名作为显示名称
        }
        
        return apkInfo
    }
}
