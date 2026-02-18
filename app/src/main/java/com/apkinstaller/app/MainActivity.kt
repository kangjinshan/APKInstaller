package com.apkinstaller.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.apkinstaller.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var networkScanner: NetworkScanner
    private lateinit var socketTransferManager: SocketTransferManager
    private lateinit var apkDownloader: ApkDownloader
    
    private val devices = mutableListOf<Device>()
    private var selectedDevice: Device? = null
    private var downloadedApkFile: File? = null
    private var isReceiveServerRunning = false
    private var isDownloading = false
    
    // 安装APK权限请求
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeComponents()
        setupRecyclerView()
        setupClickListeners()
        requestInstallPermission()
    }
    
    private fun initializeComponents() {
        networkScanner = NetworkScanner()
        socketTransferManager = SocketTransferManager(this)
        apkDownloader = ApkDownloader(this)
        
        // 启动后自动执行相应操作
        if (isTvDevice()) {
            // 电视端自动启动接收服务
            startReceiveServer()
        } else {
            // 手机端自动扫描设备
            lifecycleScope.launch {
                // 延迟500ms后自动扫描
                kotlinx.coroutines.delay(500)
                scanDevices()
            }
        }
    }
    
    /**
     * 判断是否是TV设备
     */
    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(devices) { device ->
            selectedDevice = device
            updateInstallButtonState()
            showToast("已选择设备: ${device.ip}")
        }
        
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }
    
    private fun setupClickListeners() {
        // 启动/停止接收服务
        binding.btnToggleReceive.setOnClickListener {
            toggleReceiveServer()
        }
        
        // 扫描设备按钮
        binding.btnScanDevices.setOnClickListener {
            scanDevices()
        }
        
        // 下载或安装按钮
        binding.btnDownloadOrInstall.setOnClickListener {
            if (downloadedApkFile == null) {
                downloadApk()
            } else {
                sendAndInstallApk()
            }
        }
    }
    
    private fun toggleReceiveServer() {
        if (isReceiveServerRunning) {
            stopReceiveServer()
        } else {
            startReceiveServer()
        }
    }
    
    private fun startReceiveServer() {
        lifecycleScope.launch {
            try {
                isReceiveServerRunning = true
                binding.btnToggleReceive.text = "停止接收"
                binding.tvStatus.text = "接收服务已启动，等待APK..."
                showToast("接收服务已启动，端口: ${SocketTransferManager.TRANSFER_PORT}")
                
                socketTransferManager.startReceiveServer(
                    onApkReceived = { file ->
                        lifecycleScope.launch {
                            binding.tvStatus.text = "已接收APK: ${file.name}"
                            showToast("已接收APK: ${file.name}")
                            installReceivedApk(file)
                        }
                    },
                    onProgress = { progress ->
                        lifecycleScope.launch {
                            binding.tvStatus.text = "接收中: $progress%"
                        }
                    },
                    onError = { error ->
                        lifecycleScope.launch {
                            binding.tvStatus.text = "接收错误: $error"
                            showToast("接收错误: $error")
                        }
                    }
                )
            } catch (e: Exception) {
                isReceiveServerRunning = false
                binding.btnToggleReceive.text = "启动接收"
                binding.tvStatus.text = "启动接收服务失败: ${e.message}"
                showToast("启动失败: ${e.message}")
            }
        }
    }
    
    private fun stopReceiveServer() {
        socketTransferManager.stopReceiveServer()
        isReceiveServerRunning = false
        binding.btnToggleReceive.text = "启动接收"
        binding.tvStatus.text = "接收服务已停止"
        showToast("接收服务已停止")
    }
    
    private fun scanDevices() {
        binding.btnScanDevices.isEnabled = false
        binding.tvScanStatus.text = getString(R.string.scanning)
        devices.clear()
        deviceAdapter.updateDevices(devices)
        selectedDevice = null
        updateInstallButtonState()
        
        lifecycleScope.launch {
            try {
                val foundDevices = networkScanner.scanNetwork { device ->
                    lifecycleScope.launch {
                        devices.add(device)
                        deviceAdapter.notifyItemInserted(devices.size - 1)
                        updateScanStatus()
                        
                        // 自动选中第一个扫描到的设备
                        if (selectedDevice == null && devices.size == 1) {
                            selectedDevice = device
                            updateInstallButtonState()
                            showToast("已自动选择设备: ${device.ip}")
                        }
                    }
                }
                
                binding.btnScanDevices.isEnabled = true
                if (foundDevices.isEmpty()) {
                    binding.tvScanStatus.text = "未找到运行接收服务的设备"
                    showToast("未找到设备，请确保目标设备已启动接收服务")
                } else {
                    updateScanStatus()
                }
            } catch (e: Exception) {
                binding.btnScanDevices.isEnabled = true
                binding.tvScanStatus.text = "扫描失败: ${e.message}"
                showToast("扫描失败: ${e.message}")
            }
        }
    }
    
    private fun updateScanStatus() {
        binding.tvScanStatus.text = "找到 ${devices.size} 个设备"
    }
    
    private fun downloadApk() {
        if (isDownloading) {
            showToast("正在下载中...")
            return
        }
        
        isDownloading = true
        binding.btnDownloadOrInstall.isEnabled = false
        binding.btnDownloadOrInstall.text = "下载中..."
        binding.progressDownload.visibility = View.VISIBLE
        binding.tvDownloadProgress.visibility = View.VISIBLE
        binding.tvStatus.text = "正在下载最新版本APK..."
        
        lifecycleScope.launch {
            try {
                val result = apkDownloader.downloadLatestApk { progress, downloaded, total ->
                    lifecycleScope.launch {
                        binding.progressDownload.progress = progress
                        val downloadedMB = downloaded / (1024 * 1024)
                        val totalMB = total / (1024 * 1024)
                        binding.tvDownloadProgress.text = "下载进度: $progress% ($downloadedMB MB / $totalMB MB)"
                    }
                }
                
                if (result.isSuccess) {
                    val apkFile = result.getOrNull()!!
                    downloadedApkFile = apkFile
                    
                    binding.progressDownload.visibility = View.GONE
                    binding.tvDownloadProgress.visibility = View.GONE
                    binding.btnDownloadOrInstall.text = "安装APK"
                    binding.btnDownloadOrInstall.isEnabled = true
                    binding.tvApkInfo.text = "已下载: ${apkFile.name}\n大小: ${apkFile.length() / (1024 * 1024)} MB"
                    binding.tvStatus.text = "下载完成！"
                    showToast("下载完成")
                    
                    updateInstallButtonState()
                } else {
                    throw result.exceptionOrNull()!!
                }
            } catch (e: Exception) {
                binding.progressDownload.visibility = View.GONE
                binding.tvDownloadProgress.visibility = View.GONE
                binding.btnDownloadOrInstall.text = "下载APK"
                binding.btnDownloadOrInstall.isEnabled = true
                binding.tvStatus.text = "下载失败: ${e.message}"
                showToast("下载失败: ${e.message}")
            } finally {
                isDownloading = false
            }
        }
    }
    
    private fun sendAndInstallApk() {
        val device = selectedDevice
        val apkFile = downloadedApkFile
        
        if (device == null) {
            showToast("请先选择目标设备")
            return
        }
        
        if (apkFile == null) {
            showToast("请先下载APK文件")
            return
        }
        
        binding.btnDownloadOrInstall.isEnabled = false
        binding.tvStatus.text = "准备发送APK..."
        
        lifecycleScope.launch {
            try {
                socketTransferManager.sendApk(
                    targetIp = device.ip,
                    apkFile = apkFile,
                    onProgress = { progress ->
                        lifecycleScope.launch {
                            binding.tvStatus.text = "发送中: $progress%"
                        }
                    },
                    onSuccess = {
                        lifecycleScope.launch {
                            binding.tvStatus.text = "发送成功！目标设备将自动安装"
                            showToast("发送成功")
                        }
                    },
                    onError = { error ->
                        lifecycleScope.launch {
                            binding.tvStatus.text = "发送失败: $error"
                            showToast("发送失败: $error")
                        }
                    }
                )
            } catch (e: Exception) {
                binding.tvStatus.text = "发送失败: ${e.message}"
                showToast("发送失败: ${e.message}")
            } finally {
                binding.btnDownloadOrInstall.isEnabled = true
            }
        }
    }
    
    private fun installReceivedApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    apkFile
                )
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            
            showToast("正在安装: ${apkFile.name}")
        } catch (e: Exception) {
            showToast("安装失败: ${e.message}")
            binding.tvStatus.text = "安装失败: ${e.message}"
        }
    }
    
    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:$packageName")
                    installPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    // Android TV等设备可能没有这个设置界面，忽略错误
                    showToast("当前设备不支持此权限设置，将尝试直接安装")
                }
            }
        }
    }
    
    private fun updateInstallButtonState() {
        val hasApk = downloadedApkFile != null
        val hasDevice = selectedDevice != null
        
        if (hasApk) {
            binding.btnDownloadOrInstall.text = "安装APK"
            binding.btnDownloadOrInstall.isEnabled = hasDevice
        } else {
            binding.btnDownloadOrInstall.text = "下载APK"
            binding.btnDownloadOrInstall.isEnabled = true
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isReceiveServerRunning) {
            stopReceiveServer()
        }
    }
}
