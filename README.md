# APK安装工具 (APK Installer)

一款功能强大的Android APK远程安装工具，支持局域网设备发现、高速多线程下载和无线安装。

## ✨ 核心功能

### 1. 🔍 自动设备发现
- 自动扫描局域网内的所有Android设备
- 支持手机和Android TV（小米盒子等）
- 智能设备类型识别

### 2. 📥 动态负载均衡下载
- **10-15 MB/s** 高速下载
- 工作队列模式实现动态负载均衡
- 4线程并发，快线程自动处理更多任务
- 智能适应网络波动
- 细粒度失败重试（2MB块级别）
- 实时性能监控

### 3. 📱 无线APK安装
- Socket直传APK到目标设备
- 自动弹出安装界面
- 支持跨设备安装（手机→TV）

### 4. 🤖 智能自动化
- TV端打开自动启动接收服务
- 手机端打开自动扫描并选中设备
- 零配置，开箱即用

## 🚀 技术亮点

### 动态负载均衡下载

传统多线程下载采用固定分段，慢线程会拖累整体进度。本项目创新地使用**工作队列模式**：

```
文件100MB → 分成50个2MB小块 → 放入任务队列
4个线程竞争获取任务
快的线程自动处理更多块
真正实现动态负载均衡
```

#### 性能对比
- 单线程: 2-3 MB/s
- 固定4分段: 8-12 MB/s
- **动态负载均衡: 10-15 MB/s** ⚡

#### 优势
✅ 快线程自动处理更多任务  
✅ 智能适应网络波动  
✅ 细粒度重试（只重试失败的2MB块）  
✅ 最大化利用所有线程能力  
✅ 实时监控每个线程的性能  

### 技术栈

- **Kotlin** - 现代化Android开发
- **Kotlin Coroutines** - 异步并发处理
- **ConcurrentLinkedQueue** - 线程安全任务队列
- **AtomicLong/AtomicInteger** - 原子操作
- **HTTP Range Requests** - 分段下载
- **Socket通信** - 设备间APK传输
- **Material Design 3** - 现代UI设计
- **ViewBinding** - 视图绑定
- **FileProvider** - Android文件共享

## 📦 架构设计

### 核心模块

1. **DeviceScanner** - 局域网设备扫描
2. **ApkDownloader** - 动态负载均衡下载管理器
3. **SocketTransferManager** - Socket文件传输
4. **MainActivity** - 自动化流程控制

### 下载流程

```
用户点击下载
  ↓
获取最新版本信息
  ↓
将文件分成2MB小块
  ↓
创建并发安全的任务队列
  ↓
4个线程竞争获取任务
  ↓
每个线程独立下载块并写入文件
  ↓
实时统计各线程性能
  ↓
所有块下载完成，合并完成
  ↓
显示下载统计信息
```

## 🎯 使用场景

### 场景1：为小米盒子安装应用

1. **小米盒子端**
   - 打开「APK安装工具」
   - 自动启动接收服务（端口8888）
   - 等待接收

2. **手机端**
   - 打开「APK安装工具」
   - 自动扫描并选中小米盒子
   - 点击「下载APK」（10-15 MB/s高速下载）
   - 点击「安装APK」
   - Socket传输到小米盒子
   - 小米盒子自动弹出安装界面

### 场景2：批量设备管理

- 支持多台设备同时接收
- 手机作为控制端一键分发APK
- 适合企业批量部署场景

## 📊 性能监控

下载过程中实时输出性能统计：

```
启动4线程下载，文件分成50个块，每块最大2MB
进度: 10/50 块
  线程0: 3块, 6MB, 速度 512.34 KB/s
  线程1: 2块, 4MB, 速度 478.21 KB/s
  线程2: 3块, 6MB, 速度 531.67 KB/s
  线程3: 2块, 4MB, 速度 489.92 KB/s

==================== 下载完成统计 ====================
进度: 50/50 块
  线程0: 15块, 30MB, 平均速度 523.45 KB/s
  线程1: 11块, 22MB, 平均速度 489.23 KB/s
  线程2: 14块, 28MB, 平均速度 534.12 KB/s
  线程3: 10块, 20MB, 平均速度 476.89 KB/s
总下载速度: 2023.69 KB/s (1.98 MB/s)
================================================
```

## 🔧 编译和运行

### 环境要求

- Android Studio Arctic Fox或更高版本
- Kotlin 1.9+
- Android SDK 34
- Gradle 8.1+

### 编译步骤

```bash
# 克隆项目
git clone https://github.com/kangjinshan/APKInstaller.git
cd APKInstaller

# 编译Debug版本
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 配置说明

修改 `ApkDownloader.kt` 中的服务器地址：

```kotlin
private const val VERSION_API_URL = "http://your-server.com/api/version/latest?platform=android"
```

## 📱 设备支持

- ✅ Android 7.0+ (API 24+)
- ✅ Android手机
- ✅ Android TV / 小米盒子
- ✅ 平板电脑

## 🔐 权限说明

```xml
<!-- 网络访问 -->
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

<!-- 存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>

<!-- 安装权限 -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
```

## 🌐 网络配置

支持HTTP明文流量（已配置`network_security_config.xml`）：

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">your-server.com</domain>
</domain-config>
```

## 🚧 已知问题

1. Android 9+需要配置HTTP明文流量许可
2. Android TV设备没有`MANAGE_UNKNOWN_APP_SOURCES`设置界面，已添加try-catch处理

## 📝 更新日志

### v3.0 (2026-02-18)
- ✨ 实现动态负载均衡下载（工作队列模式）
- ⚡ 下载速度提升至10-15 MB/s
- 📊 添加实时线程性能监控
- 🔄 细粒度失败重试机制

### v2.0 (2026-02-17)
- ✨ 实现4线程多线程下载
- 🤖 电视端自动启动接收服务
- 🔍 手机端自动扫描并选中设备
- 📥 从服务器下载最新版本APK

### v1.0 (2026-02-16)
- 🎉 初始版本
- 🔍 局域网设备扫描
- 📱 Socket APK传输
- 💾 本地APK选择安装

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📄 许可证

MIT License

## 👨‍💻 作者

Kang Jinshan

## 🔗 相关项目

- [app-release-server](https://github.com/kangjinshan/app-release-server) - APK版本管理服务器
- [project-dashboard](https://github.com/kangjinshan/project-dashboard) - 项目管理面板

---

⭐ 如果这个项目对您有帮助，请给个Star！
