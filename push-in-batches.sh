#!/bin/bash
# 分批推送脚本 - 避免大文件一次性推送超时

echo "======================================"
echo "APKInstaller - 分批推送到GitHub"
echo "======================================"
echo ""

cd "$(dirname "$0")"

# 第一步：提交新文件到Git
echo "📝 Step 1: 提交新文件..."
git add push-to-github.sh diagnose-git.sh push-in-batches.sh
git commit -m "Add push scripts" 2>&1 || echo "无新文件需要提交"
echo ""

# 第二步：推送小文件（源代码）
echo "🚀 Step 2: 推送源代码文件..."
echo "推送 app/src/ 目录..."
if git push origin main:refs/heads/main-src 2>&1; then
    echo "✅ 源代码推送成功"
else
    echo "❌ 源代码推送失败"
fi
echo ""

# 第三步：尝试推送全部
echo "🚀 Step 3: 推送全部文件到main分支..."
git push -u origin main 2>&1

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 推送成功！"
    echo "🔗 仓库地址: https://github.com/kangjinshan/APKInstaller"
else
    echo ""
    echo "❌ 推送失败或超时"
    echo ""
    echo "📌 建议使用以下方法:"
    echo ""
    echo "方法1: 使用GitHub Desktop图形化工具"
    echo "  下载: https://desktop.github.com/"
    echo "  打开APKInstaller目录，点击Publish"
    echo ""
    echo "方法2: 使用git-backup-tools"
    echo "  cd ../git-backup-tools"
    echo "  ./git-backup.sh /Users/kanayama/Desktop/AI/APKInstaller"
    echo ""
    echo "方法3: 增加Git超时设置"
    echo "  git config --global http.postBuffer 524288000"
    echo "  git config --global http.lowSpeedLimit 0"
    echo "  git config --global http.lowSpeedTime 999999"
    echo "  然后重试: git push -u origin main"
    echo ""
    echo "方法4: 使用SSH代替HTTPS"
    echo "  git remote set-url origin git@github.com:kangjinshan/APKInstaller.git"
    echo "  git push -u origin main"
fi
