#!/usr/bin/env python3
"""
APK安装脚本 - 将本地APK发送到小米盒子
使用方法: python3 install_apk_to_tv.py <APK文件路径>
"""

import socket
import struct
import sys
import os
from pathlib import Path

# 小米盒子配置
TV_IP = "192.168.31.183"  # 小米盒子IP地址
TV_PORT = 8888  # APK安装工具监听端口
BUFFER_SIZE = 8192  # 8KB缓冲区


def send_apk(apk_path: str, target_ip: str = TV_IP, target_port: int = TV_PORT):
    """
    发送APK文件到目标设备
    
    Args:
        apk_path: APK文件路径
        target_ip: 目标设备IP
        target_port: 目标设备端口
    """
    # 检查文件是否存在
    if not os.path.exists(apk_path):
        print(f"❌ 错误: 文件不存在: {apk_path}")
        return False
    
    apk_file = Path(apk_path)
    file_size = apk_file.stat().st_size
    file_name = apk_file.name
    
    print(f"📦 APK文件: {file_name}")
    print(f"📏 文件大小: {file_size / (1024 * 1024):.2f} MB")
    print(f"🎯 目标设备: {target_ip}:{target_port}")
    print(f"⏳ 正在连接...")
    
    try:
        # 创建Socket连接
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(30)  # 30秒超时
        sock.connect((target_ip, target_port))
        print(f"✅ 连接成功!")
        
        # 1. 发送文件名（使用Java DataOutputStream.writeUTF()格式）
        # 格式：2字节长度（short）+ UTF-8编码的字符串
        file_name_bytes = file_name.encode('utf-8')
        file_name_length = len(file_name_bytes)
        
        if file_name_length > 65535:
            print(f"❌ 错误: 文件名太长（超过65535字节）")
            return False
        
        # 发送2字节长度（big-endian short）
        sock.sendall(struct.pack('>H', file_name_length))
        # 发送UTF-8编码的文件名
        sock.sendall(file_name_bytes)
        
        # 2. 发送文件大小（8字节长整数，大端序）
        sock.sendall(struct.pack('>q', file_size))
        
        print(f"📤 开始传输...")
        
        # 4. 发送文件内容
        sent_bytes = 0
        with open(apk_path, 'rb') as f:
            while True:
                chunk = f.read(BUFFER_SIZE)
                if not chunk:
                    break
                sock.sendall(chunk)
                sent_bytes += len(chunk)
                
                # 显示进度
                progress = (sent_bytes / file_size) * 100
                print(f"\r进度: {progress:.1f}% ({sent_bytes / (1024 * 1024):.2f} MB / {file_size / (1024 * 1024):.2f} MB)", end='')
        
        print(f"\n✅ 传输完成!")
        print(f"🎉 APK已发送到小米盒子，设备将自动安装")
        
        sock.close()
        return True
        
    except socket.timeout:
        print(f"\n❌ 错误: 连接超时")
        return False
    except ConnectionRefusedError:
        print(f"\n❌ 错误: 连接被拒绝，请确保:")
        print(f"   1. 小米盒子已打开APK安装工具")
        print(f"   2. 小米盒子已启动接收服务")
        print(f"   3. IP地址正确: {target_ip}")
        return False
    except Exception as e:
        print(f"\n❌ 错误: {e}")
        return False


def main():
    """主函数"""
    print("=" * 60)
    print("APK安装工具 - 本地脚本版本")
    print("=" * 60)
    
    # 检查参数
    if len(sys.argv) < 2:
        print("使用方法:")
        print(f"  python3 {sys.argv[0]} <APK文件路径>")
        print()
        print("示例:")
        print(f"  python3 {sys.argv[0]} /path/to/your/app.apk")
        print(f"  python3 {sys.argv[0]} ./app-debug.apk")
        sys.exit(1)
    
    apk_path = sys.argv[1]
    
    # 可选：自定义IP地址
    target_ip = TV_IP
    if len(sys.argv) >= 3:
        target_ip = sys.argv[2]
        print(f"使用自定义IP: {target_ip}")
    
    # 发送APK
    success = send_apk(apk_path, target_ip)
    
    print("=" * 60)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
