package com.apkinstaller.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * APK下载管理器（支持动态负载均衡多线程下载）
 * 负责从服务器获取最新版本信息并通过智能多线程下载APK
 */
class ApkDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "ApkDownloader"
        private const val VERSION_API_URL = "http://jinshan.southeastasia.cloudapp.azure.com:8011/api/version/latest?platform=android"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 60000
        private const val THREAD_COUNT = 4 // 4线程下载
        private const val CHUNK_SIZE = 2 * 1024 * 1024L // 每块2MB，实现动态负载均衡
    }
    
    /**
     * 版本信息数据类
     */
    data class VersionInfo(
        val version: String,
        val versionCode: Int,
        val downloadUrl: String,
        val fileName: String,
        val fileSize: Long,
        val releaseNotes: String?
    )
    
    /**
     * 下载任务块
     */
    private data class DownloadChunk(
        val id: Int,
        val startPos: Long,
        val endPos: Long
    )
    
    /**
     * 线程统计信息
     */
    private data class ThreadStats(
        val threadId: Int,
        var bytesDownloaded: Long = 0,
        var chunksCompleted: Int = 0,
        var startTime: Long = System.currentTimeMillis(),
        var lastUpdateTime: Long = System.currentTimeMillis()
    ) {
        fun getSpeed(): Double {
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            return if (elapsed > 0) bytesDownloaded / elapsed / 1024.0 else 0.0 // KB/s
        }
    }
    
    /**
     * 获取最新版本信息
     */
    suspend fun getLatestVersion(): Result<VersionInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取最新版本信息: $VERSION_API_URL")
            
            val url = URL(VERSION_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/json")
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP错误: $responseCode"))
            }
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "服务器响应: $response")
            
            // 解析JSON响应
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: json
            
            val versionInfo = VersionInfo(
                version = data.optString("version", "unknown"),
                versionCode = data.optInt("versionCode", 0),
                downloadUrl = data.optString("downloadUrl", ""),
                fileName = data.optString("fileName", "app-release.apk"),
                fileSize = data.optLong("fileSize", 0L),
                releaseNotes = if (data.has("releaseNotes")) data.optString("releaseNotes") else null
            )
            
            if (versionInfo.downloadUrl.isEmpty()) {
                return@withContext Result.failure(Exception("下载地址为空"))
            }
            
            Log.d(TAG, "最新版本: ${versionInfo.version}, 下载地址: ${versionInfo.downloadUrl}")
            Result.success(versionInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "获取版本信息失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 多线程下载APK文件
     */
    suspend fun downloadApk(
        versionInfo: VersionInfo,
        onProgress: (Int, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始智能多线程下载APK: ${versionInfo.downloadUrl}")
            
            // 创建下载目录
            val downloadDir = File(context.getExternalFilesDir(null), "downloads")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val tempFile = File(downloadDir, "${versionInfo.fileName}.tmp")
            val targetFile = File(downloadDir, versionInfo.fileName)
            
            // 如果目标文件已存在，删除
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
            
            // 检查服务器是否支持范围请求
            val url = URL(versionInfo.downloadUrl)
            val testConnection = url.openConnection() as HttpURLConnection
            testConnection.requestMethod = "HEAD"
            testConnection.connectTimeout = CONNECT_TIMEOUT
            testConnection.readTimeout = READ_TIMEOUT
            
            val acceptRanges = testConnection.getHeaderField("Accept-Ranges")
            val supportsRange = acceptRanges == "bytes"
            testConnection.disconnect()
            
            Log.d(TAG, "服务器支持Range请求: $supportsRange")
            
            val fileSize = versionInfo.fileSize
            if (fileSize <= 0) {
                return@withContext Result.failure(Exception("无法获取文件大小"))
            }
            
            // 创建临时文件
            RandomAccessFile(tempFile, "rw").use { it.setLength(fileSize) }
            
            if (supportsRange && fileSize > 1024 * 1024) {
                // 动态负载均衡多线程下载
                downloadWithLoadBalancing(versionInfo.downloadUrl, tempFile, fileSize, onProgress)
            } else {
                // 单线程下载
                downloadSingleThread(versionInfo.downloadUrl, tempFile, fileSize, onProgress)
            }
            
            // 重命名临时文件
            if (tempFile.renameTo(targetFile)) {
                Log.d(TAG, "APK下载完成: ${targetFile.absolutePath}")
                Result.success(targetFile)
            } else {
                Result.failure(Exception("无法重命名下载文件"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "下载APK失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 动态负载均衡多线程下载
     * 使用工作队列模式，快的线程自动处理更多任务
     */
    private suspend fun downloadWithLoadBalancing(
        downloadUrl: String,
        targetFile: File,
        fileSize: Long,
        onProgress: (Int, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        // 将文件分成小块
        val chunks = mutableListOf<DownloadChunk>()
        var chunkId = 0
        var currentPos = 0L
        
        while (currentPos < fileSize) {
            val endPos = minOf(currentPos + CHUNK_SIZE - 1, fileSize - 1)
            chunks.add(DownloadChunk(chunkId++, currentPos, endPos))
            currentPos = endPos + 1
        }
        
        Log.d(TAG, "文件分成 ${chunks.size} 个块，每块最大 ${CHUNK_SIZE / 1024 / 1024}MB，启动 $THREAD_COUNT 个线程")
        
        // 创建并发安全的任务队列
        val chunkQueue = ConcurrentLinkedQueue(chunks)
        val downloadedBytes = AtomicLong(0)
        val completedChunks = AtomicInteger(0)
        val totalChunks = chunks.size
        
        // 线程统计
        val threadStats = (0 until THREAD_COUNT).map { ThreadStats(it) }
        val statsLock = Any()
        
        // 创建工作线程
        val tasks = (0 until THREAD_COUNT).map { threadId ->
            async {
                var myChunk: DownloadChunk?
                while (chunkQueue.poll().also { myChunk = it } != null) {
                    val chunk = myChunk!!
                    
                    try {
                        // 下载这个块
                        val bytesDownloaded = downloadChunk(
                            downloadUrl,
                            targetFile,
                            chunk.startPos,
                            chunk.endPos,
                            threadId,
                            chunk.id,
                            downloadedBytes,
                            fileSize,
                            onProgress
                        )
                        
                        // 更新统计
                        synchronized(statsLock) {
                            threadStats[threadId].apply {
                                this.bytesDownloaded += bytesDownloaded
                                chunksCompleted++
                                lastUpdateTime = System.currentTimeMillis()
                            }
                        }
                        
                        val completed = completedChunks.incrementAndGet()
                        
                        // 每完成10个块或每个线程完成第一个块时输出统计
                        if (completed % 10 == 0 || threadStats[threadId].chunksCompleted == 1) {
                            logThreadStats(threadStats, completed, totalChunks)
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "线程 $threadId 下载块 ${chunk.id} 失败，重新加入队列", e)
                        // 失败的块重新加入队列
                        chunkQueue.offer(chunk)
                    }
                }
                
                Log.d(TAG, "线程 $threadId 完成，共处理 ${threadStats[threadId].chunksCompleted} 个块，" +
                        "下载 ${threadStats[threadId].bytesDownloaded / 1024 / 1024}MB，" +
                        "平均速度 ${String.format("%.2f", threadStats[threadId].getSpeed())} KB/s")
            }
        }
        
        // 等待所有线程完成
        tasks.awaitAll()
        
        // 最终统计
        Log.d(TAG, "==================== 下载完成统计 ====================")
        logThreadStats(threadStats, totalChunks, totalChunks)
        val totalSpeed = threadStats.sumOf { it.getSpeed() }
        Log.d(TAG, "总下载速度: ${String.format("%.2f", totalSpeed)} KB/s (${String.format("%.2f", totalSpeed / 1024)} MB/s)")
        Log.d(TAG, "================================================")
    }
    
    /**
     * 输出线程统计信息
     */
    private fun logThreadStats(stats: List<ThreadStats>, completed: Int, total: Int) {
        Log.d(TAG, "进度: $completed/$total 块")
        stats.forEach { stat ->
            if (stat.chunksCompleted > 0) {
                Log.d(TAG, "  线程${stat.threadId}: ${stat.chunksCompleted}块, " +
                        "${stat.bytesDownloaded / 1024 / 1024}MB, " +
                        "速度 ${String.format("%.2f", stat.getSpeed())} KB/s")
            }
        }
    }
    
    /**
     * 下载单个数据块
     */
    private fun downloadChunk(
        downloadUrl: String,
        targetFile: File,
        startPos: Long,
        endPos: Long,
        threadId: Int,
        chunkId: Int,
        downloadedBytes: AtomicLong,
        fileSize: Long,
        onProgress: (Int, Long, Long) -> Unit
    ): Long {
        var connection: HttpURLConnection? = null
        var raf: RandomAccessFile? = null
        val mainHandler = Handler(Looper.getMainLooper())
        var bytesDownloadedThisChunk = 0L
        
        try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Range", "bytes=$startPos-$endPos")
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_PARTIAL && responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP错误: $responseCode")
            }
            
            raf = RandomAccessFile(targetFile, "rw")
            raf.seek(startPos)
            
            val inputStream = connection.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var lastProgress = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                bytesDownloadedThisChunk += bytesRead
                val currentTotal = downloadedBytes.addAndGet(bytesRead.toLong())
                
                // 计算总进度
                val progress = ((currentTotal * 100) / fileSize).toInt()
                if (progress != lastProgress) {
                    mainHandler.post {
                        onProgress(progress, currentTotal, fileSize)
                    }
                    lastProgress = progress
                }
            }
            
            Log.d(TAG, "线程 $threadId 完成块 $chunkId: $startPos-$endPos (${bytesDownloadedThisChunk / 1024}KB)")
            return bytesDownloadedThisChunk
            
        } catch (e: Exception) {
            Log.e(TAG, "线程 $threadId 下载块 $chunkId 失败: $startPos-$endPos", e)
            throw e
        } finally {
            raf?.close()
            connection?.disconnect()
        }
    }
    
    /**
     * 单线程下载（服务器不支持Range时的降级方案）
     */
    private fun downloadSingleThread(
        downloadUrl: String,
        targetFile: File,
        fileSize: Long,
        onProgress: (Int, Long, Long) -> Unit
    ) {
        var connection: HttpURLConnection? = null
        var raf: RandomAccessFile? = null
        val mainHandler = Handler(Looper.getMainLooper())
        
        try {
            Log.d(TAG, "使用单线程下载")
            
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("下载失败: HTTP $responseCode")
            }
            
            raf = RandomAccessFile(targetFile, "rw")
            val inputStream = connection.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytes = 0L
            var lastProgress = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                
                val progress = if (fileSize > 0) {
                    ((totalBytes * 100) / fileSize).toInt()
                } else {
                    0
                }
                
                if (progress != lastProgress) {
                    mainHandler.post {
                        onProgress(progress, totalBytes, fileSize)
                    }
                    lastProgress = progress
                }
            }
            
        } finally {
            raf?.close()
            connection?.disconnect()
        }
    }
    
    /**
     * 获取并下载最新版本APK
     */
    suspend fun downloadLatestApk(
        onProgress: (Int, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取最新版本信息
            val versionResult = getLatestVersion()
            if (versionResult.isFailure) {
                return@withContext Result.failure(versionResult.exceptionOrNull()!!)
            }
            
            val versionInfo = versionResult.getOrNull()!!
            
            // 2. 下载APK
            downloadApk(versionInfo, onProgress)
            
        } catch (e: Exception) {
            Log.e(TAG, "下载最新APK失败", e)
            Result.failure(e)
        }
    }
}
