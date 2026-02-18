package com.apkinstaller.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

class AdbManager {
    
    /**
     * 通过ADB连接到指定设备
     */
    suspend fun connectDevice(device: Device): Result<String> = withContext(Dispatchers.IO) {
        try {
            val command = "adb connect ${device.ip}:${device.port}"
            val result = executeCommand(command)
            
            if (result.contains("connected") || result.contains("already connected")) {
                Result.success(result)
            } else {
                Result.failure(Exception("连接失败: $result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 断开ADB连接
     */
    suspend fun disconnectDevice(device: Device): Result<String> = withContext(Dispatchers.IO) {
        try {
            val command = "adb disconnect ${device.ip}:${device.port}"
            val result = executeCommand(command)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 安装APK到设备
     */
    suspend fun installApk(device: Device, apkPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 首先确保已连接到设备
            val connectResult = connectDevice(device)
            if (connectResult.isFailure) {
                return@withContext Result.failure(connectResult.exceptionOrNull()!!)
            }
            
            // 检查APK文件是否存在
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                return@withContext Result.failure(Exception("APK文件不存在: $apkPath"))
            }
            
            // 执行安装命令
            val command = "adb -s ${device.ip}:${device.port} install -r \"$apkPath\""
            val result = executeCommand(command, timeoutMillis = 120000) // 2分钟超时
            
            if (result.contains("Success")) {
                Result.success("安装成功")
            } else {
                Result.failure(Exception("安装失败: $result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 检查ADB是否可用
     */
    suspend fun checkAdbAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = executeCommand("adb version")
            result.contains("Android Debug Bridge")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取已连接的设备列表
     */
    suspend fun getConnectedDevices(): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = executeCommand("adb devices")
            val lines = result.split("\n")
            val devices = mutableListOf<String>()
            
            for (line in lines) {
                if (line.contains("\t") && !line.contains("List of devices")) {
                    val parts = line.split("\t")
                    if (parts.size >= 2 && parts[1].trim() == "device") {
                        devices.add(parts[0].trim())
                    }
                }
            }
            devices
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 执行shell命令
     */
    private fun executeCommand(command: String, timeoutMillis: Long = 30000): String {
        val process = Runtime.getRuntime().exec(command)
        val output = StringBuilder()
        val error = StringBuilder()
        
        val outputReader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        
        // 读取标准输出
        outputReader.use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
        }
        
        // 读取错误输出
        errorReader.use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }
        }
        
        // 等待进程完成
        process.waitFor()
        
        val result = if (output.isNotEmpty()) output.toString() else error.toString()
        return result.trim()
    }
}
