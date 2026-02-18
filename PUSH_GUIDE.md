# APKInstaller 推送到GitHub完整指南

## 当前状态
- ✅ 项目开发完成
- ✅ 本地Git仓库已创建和提交
- ✅ GitHub远程仓库已创建
- ✅ Token已修正: ghp_vIs1hrOTAX6K7lwITmvFUllZrV27HnaG
- ⚠️ 推送遇到macOS keychain问题

## 遇到的问题
```
fatal: could not read Password for 'https://...'@github.com': Device not configured
```

这是macOS keychain读取凭证失败的错误。

## 推荐解决方案（按优先级）

### 🥇 方案1: 使用GitHub CLI (gh)
最简单可靠的方法：

```bash
# 1. 安装GitHub CLI (如果未安装)
brew install gh

# 2. 登录GitHub
gh auth login

# 3. 推送仓库
cd /Users/kanayama/Desktop/AI/APKInstaller
gh repo view kangjinshan/APKInstaller --web  # 验证仓库存在
git push -u origin main
```

### 🥈 方案2: 使用GitHub Desktop
图形界面最简单：

```
1. 下载: https://desktop.github.com/
2. 安装并登录GitHub账号
3. File → Add Local Repository
4. 选择: /Users/kanayama/Desktop/AI/APKInstaller  
5. 点击 "Publish repository"
6. 完成！
```

### 🥉 方案3: 清除keychain并重新配置

```bash
cd /Users/kanayama/Desktop/AI/APKInstaller

# 1. 完全移除credential helper
git config --global --unset credential.helper
git config --local --unset credential.helper

# 2. 配置使用store（明文存储）
git config --global credential.helper store

# 3. 手动创建.git-credentials文件
echo "https://ghp_vIs1hrOTAX6K7lwUllZrV27HnaG@github.com" > ~/.git-credentials

# 4. 推送
git push -u origin main
```

### 方案4: 使用SSH代替HTTPS

```bash
cd /Users/kanayama/Desktop/AI/APKInstaller

# 1. 生成SSH密钥（如果没有）
ssh-keygen -t ed25519 -C "your_email@example.com"

# 2. 复制公钥
cat ~/.ssh/id_ed25519.pub

# 3. 添加到GitHub
# 访问: https://github.com/settings/keys
# 点击 "New SSH key"，粘贴公钥

# 4. 切换到SSH URL
git remote set-url origin git@github.com:kangjinshan/APKInstaller.git

# 5. 测试连接
ssh -T git@github.com

# 6. 推送
git push -u origin main
```

### 方案5: 使用git-backup-tools

```bash
cd /Users/kanayama/Desktop/AI/git-backup-tools

# 查看工具说明
cat README.md

# 执行备份
./git-backup.sh /Users/kanayama/Desktop/AI/APKInstaller
```

### 方案6: GitHub网页上传

```
1. 访问: https://github.com/kangjinshan/APKInstaller
2. 点击 "Add file" → "Upload files"
3. 拖拽整个项目文件夹
4. 提交
```

## 推送成功后验证

访问: https://github.com/kangjinshan/APKInstaller

应该能看到：
- ✅ README.md
- ✅ app/目录
- ✅ gradle/目录
- ✅ 所有Kotlin源代码

## 项目信息

```
名称: APKInstaller
本地路径: /Users/kanayama/Desktop/AI/APKInstaller
GitHub: https://github.com/kangjinshan/APKInstaller
用户: kangjinshan
分支: main
文件数: 32个
代码行: 2805行
```

## 问题诊断

如果推送仍然失败，请运行诊断：

```bash
cd /Users/kanayama/Desktop/AI/APKInstaller
chmod +x diagnose-git.sh
./diagnose-git.sh
```

## 注意事项

1. **Token已包含在URL中**，不需要输入密码
2. **macOS keychain问题**是常见的Git认证问题
3. **推荐使用GitHub CLI或GitHub Desktop**，最可靠
4. **SSH方式**是长期最佳方案（一次配置永久使用）

## 联系方式

如果以上方案都不work，可以：
1. 使用GitHub网页上传
2. 或将项目打包发送给其他人帮助上传

---

创建时间: 2026-02-18
作者: WeCoder AI Assistant
