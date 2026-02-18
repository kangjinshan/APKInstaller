#!/bin/bash
# APKInstaller推送到GitHub脚本

echo "======================================"
echo "APKInstaller - 推送到GitHub"
echo "======================================"
echo ""

cd "$(dirname "$0")"

# 显示当前状态
echo "📦 检查Git状态..."
git status --short
echo ""

# 显示远程仓库
echo "🔗 远程仓库:"
git remote -v
echo ""

# 开始推送
echo "🚀 开始推送到GitHub..."
echo ""

# 使用SSH方式推送（如果HTTPS不行的话）
# git remote set-url origin git@github.com:kangjinshan/APKInstaller.git

# 推送到main分支
git push -u origin main

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 推送成功！"
    echo "🔗 仓库地址: https://github.com/kangjinshan/APKInstaller"
else
    echo ""
    echo "❌ 推送失败"
    echo ""
    echo "可能的解决方案:"
    echo "1. 检查网络连接"
    echo "2. 验证GitHub Token是否有效"
    echo "3. 尝试使用SSH方式:"
    echo "   git remote set-url origin git@github.com:kangjinshan/APKInstaller.git"
    echo "   git push -u origin main"
    echo ""
    echo "4. 或者手动在浏览器上传:"
    echo "   https://github.com/kangjinshan/APKInstaller/upload/main"
fi
