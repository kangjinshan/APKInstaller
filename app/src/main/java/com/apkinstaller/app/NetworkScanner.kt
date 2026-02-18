package com.apkinstaller.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class NetworkScanner {
    
    /**
     * 扫描局域网内的所有设备
     * 扫描当前网段的所有IP地址，检测8888端口（Socket传输端口）是否开放
     */
    suspend fun scanNetwork(onDeviceFound: (Device) -> Unit): List<Device> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<Device>()
        
        try {
            // 获取本机IP地址
            val localIp = getLocalIpAddress()
            if (localIp.isEmpty()) {
                return@withContext devices
            }
            
            // 解析网段
            val ipParts = localIp.split(".")
            if (ipParts.size != 4) {
                return@withContext devices
            }
            
            val subnet = "${ipParts[0]}.${ipParts[1]}.${ipParts[2]}"
            
            // 扫描网段内的所有IP地址 (1-254)
            val jobs = (1..254).map { i ->
                async {
                    val ip = "$subnet.$i"
                    // 扫描Socket传输端口（8888）
                    if (isDeviceReachable(ip, SocketTransferManager.TRANSFER_PORT)) {
                        val device = Device(ip, SocketTransferManager.TRANSFER_PORT)
                        devices.add(device)
                        onDeviceFound(device)
                    }
                }
            }
            
            // 等待所有扫描任务完成
            jobs.awaitAll()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        devices
    }
    
    /**
     * 检查设备是否可达（检测端口是否开放）
     */
    private fun isDeviceReachable(ip: String, port: Int, timeout: Int = 200): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取本机IP地址
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // 过滤掉回环地址和IPv6地址
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress ?: continue
                        // 确保是私有网段
                        if (ip.startsWith("192.168.") || 
                            ip.startsWith("10.") || 
                            ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
