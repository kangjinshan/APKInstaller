#!/bin/bash
# Git推送问题诊断脚本

echo "======================================"
echo "Git推送问题诊断工具"
echo "======================================"
echo ""

cd "$(dirname "$0")"

echo "1️⃣ 检查Git版本"
git --version
echo ""

echo "2️⃣ 检查Git配置"
echo "用户信息:"
git config user.name
git config user.email
echo ""
echo "远程仓库:"
git remote -v
echo ""

echo "3️⃣ 检查网络连接"
echo "测试GitHub.com连接..."
if ping -c 1 github.com > /dev/null 2>&1; then
    echo "✅ 可以ping通github.com"
else
    echo "❌ 无法ping通github.com"
fi
echo ""

echo "4️⃣ 检查HTTP/HTTPS连接"
echo "测试HTTPS连接..."
if command -v wget > /dev/null; then
    wget --spider -q https://github.com 2>&1 && echo "✅ wget可以访问GitHub" || echo "❌ wget无法访问GitHub"
fi
echo ""

echo "5️⃣ 检查代理设置"
echo "全局代理:"
git config --global http.proxy || echo "无"
git config --global https.proxy || echo "无"
echo ""
echo "当前仓库代理:"
git config --local http.proxy || echo "无"
git config --local https.proxy || echo "无"
echo ""

echo "6️⃣ 建议的解决方案"
echo ""
echo "方案A: 使用git-backup-tools"
echo "  cd ../git-backup-tools"
echo "  ./quick-backup.sh"
echo ""
echo "方案B: 配置Git代理（如果使用VPN/代理）"
echo "  git config --global http.proxy http://127.0.0.1:7890"
echo "  git config --global https.proxy https://127.0.0.1:7890"
echo ""
echo "方案C: 使用SSH代替HTTPS"
echo "  git remote set-url origin git@github.com:kangjinshan/APKInstaller.git"
echo "  git push -u origin main"
echo ""
echo "方案D: 在GitHub网页界面手动上传"
echo "  访问: https://github.com/kangjinshan/APKInstaller/upload/main"
echo "  将整个项目文件夹压缩后上传"
echo ""
