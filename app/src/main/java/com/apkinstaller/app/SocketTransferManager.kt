package com.apkinstaller.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress

/**
 * Socket传输管理器
 * 负责通过Socket在局域网设备之间传输APK文件
 */
class SocketTransferManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SocketTransferManager"
        const val TRANSFER_PORT = 8888
        private const val BUFFER_SIZE = 8192
        private const val SOCKET_TIMEOUT = 30000 // 30秒超时
    }
    
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    
    /**
     * 启动接收服务器
     */
    suspend fun startReceiveServer(
        onApkReceived: (File) -> Unit,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // 如果服务器已在运行，先停止
            stopReceiveServer()
            
            serverSocket = ServerSocket(TRANSFER_PORT)
            isServerRunning = true
            
            Log.d(TAG, "接收服务器已启动，端口: $TRANSFER_PORT")
            
            while (isServerRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.d(TAG, "接受来自 ${clientSocket.inetAddress.hostAddress} 的连接")
                    
                    // 处理接收
                    handleReceive(clientSocket, onApkReceived, onProgress, onError)
                } catch (e: Exception) {
                    if (isServerRunning) {
                        Log.e(TAG, "接受连接时出错", e)
                        withContext(Dispatchers.Main) {
                            onError("接受连接失败: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动接收服务器失败", e)
            withContext(Dispatchers.Main) {
                onError("启动接收服务失败: ${e.message}")
            }
        }
    }
    
    /**
     * 停止接收服务器
     */
    fun stopReceiveServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "接收服务器已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止接收服务器时出错", e)
        }
    }
    
    /**
     * 发送APK到目标设备
     */
    suspend fun sendApk(
        targetIp: String,
        apkFile: File,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        
        try {
            // 检查文件是否存在
            if (!apkFile.exists()) {
                withContext(Dispatchers.Main) {
                    onError("APK文件不存在")
                }
                return@withContext
            }
            
            Log.d(TAG, "开始发送APK到 $targetIp:$TRANSFER_PORT")
            
            // 连接到目标设备
            socket = Socket()
            socket.connect(InetSocketAddress(targetIp, TRANSFER_PORT), SOCKET_TIMEOUT)
            socket.soTimeout = SOCKET_TIMEOUT
            
            outputStream = socket.getOutputStream()
            val dataOutput = DataOutputStream(outputStream)
            
            // 发送文件信息
            dataOutput.writeUTF(apkFile.name) // 文件名
            dataOutput.writeLong(apkFile.length()) // 文件大小
            dataOutput.flush()
            
            Log.d(TAG, "发送文件信息: ${apkFile.name}, 大小: ${apkFile.length()} 字节")
            
            // 发送文件内容
            inputStream = FileInputStream(apkFile)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalSent = 0L
            val fileSize = apkFile.length()
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalSent += bytesRead
                
                // 更新进度
                val progress = ((totalSent * 100) / fileSize).toInt()
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }
            }
            
            outputStream.flush()
            Log.d(TAG, "APK发送完成，共 $totalSent 字节")
            
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送APK失败", e)
            withContext(Dispatchers.Main) {
                onError("发送失败: ${e.message}")
            }
        } finally {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        }
    }
    
    /**
     * 处理接收APK
     */
    private suspend fun handleReceive(
        socket: Socket,
        onApkReceived: (File) -> Unit,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            socket.soTimeout = SOCKET_TIMEOUT
            inputStream = socket.getInputStream()
            val dataInput = DataInputStream(inputStream)
            
            // 接收文件信息
            val fileName = dataInput.readUTF()
            val fileSize = dataInput.readLong()
            
            Log.d(TAG, "接收文件信息: $fileName, 大小: $fileSize 字节")
            
            // 创建临时文件
            val receivedDir = File(context.getExternalFilesDir(null), "received")
            if (!receivedDir.exists()) {
                receivedDir.mkdirs()
            }
            
            val receivedFile = File(receivedDir, fileName)
            outputStream = FileOutputStream(receivedFile)
            
            // 接收文件内容
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalReceived = 0L
            
            while (totalReceived < fileSize) {
                val remaining = fileSize - totalReceived
                val toRead = if (remaining < BUFFER_SIZE) remaining.toInt() else BUFFER_SIZE
                
                bytesRead = inputStream.read(buffer, 0, toRead)
                if (bytesRead == -1) break
                
                outputStream.write(buffer, 0, bytesRead)
                totalReceived += bytesRead
                
                // 更新进度
                val progress = ((totalReceived * 100) / fileSize).toInt()
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }
            }
            
            outputStream.flush()
            Log.d(TAG, "APK接收完成: ${receivedFile.absolutePath}")
            
            withContext(Dispatchers.Main) {
                onApkReceived(receivedFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "接收APK失败", e)
            withContext(Dispatchers.Main) {
                onError("接收失败: ${e.message}")
            }
        } finally {
            outputStream?.close()
            inputStream?.close()
            socket.close()
        }
    }
    
    /**
     * 检查端口是否可用
     */
    suspend fun isPortAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val testSocket = ServerSocket(TRANSFER_PORT)
            testSocket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
