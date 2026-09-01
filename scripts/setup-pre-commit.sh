#!/bin/bash

echo "🔧 設置 Pre-commit Hooks"
echo "======================"

# 檢查 Python 是否安裝
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 未安裝，請先安裝 Python 3"
    echo "   macOS: brew install python3"
    echo "   Ubuntu: sudo apt-get install python3 python3-pip"
    exit 1
fi

# 檢查 pip 是否安裝
if ! command -v pip3 &> /dev/null; then
    echo "❌ pip3 未安裝，請先安裝 pip3"
    exit 1
fi

# 安裝 pre-commit
echo "📦 安裝 pre-commit..."
pip3 install --user pre-commit detect-secrets

# 確保 pre-commit 在 PATH 中
export PATH="$HOME/.local/bin:$PATH"

# 檢查 pre-commit 是否成功安裝
if ! command -v pre-commit &> /dev/null; then
    echo "⚠️  pre-commit 安裝成功但不在 PATH 中"
    echo "請將以下內容加入您的 shell 配置檔案 (~/.zshrc 或 ~/.bashrc):"
    echo 'export PATH="$HOME/.local/bin:$PATH"'
    echo ""
    echo "然後執行: source ~/.zshrc (或 source ~/.bashrc)"
    exit 1
fi

# 安裝 git hooks
echo "🎣 安裝 git hooks..."
pre-commit install
pre-commit install --hook-type commit-msg

# 初始化 detect-secrets baseline
echo "🔐 初始化 detect-secrets baseline..."
if [ ! -f .secrets.baseline ]; then
    detect-secrets scan --baseline .secrets.baseline
fi

# 安裝額外的工具 (可選)
echo ""
echo "📋 檢查額外工具..."

# 檢查 ktlint
if ! ./gradlew ktlintCheck --dry-run &> /dev/null; then
    echo "⚠️  ktlint 未設置，將在第一次執行時自動下載"
fi

# 檢查 swiftlint (僅在 macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    if ! command -v swiftlint &> /dev/null; then
        echo "⚠️  SwiftLint 未安裝 (可選)"
        echo "   安裝: brew install swiftlint"
    else
        echo "✅ SwiftLint 已安裝"
    fi
fi

# 檢查 markdownlint
if ! command -v markdownlint &> /dev/null; then
    echo "⚠️  markdownlint 未安裝 (可選)"
    echo "   安裝: npm install -g markdownlint-cli"
fi

echo ""
echo "✅ Pre-commit hooks 設置完成！"
echo ""
echo "📝 使用說明:"
echo "1. 現在每次 git commit 時會自動執行檢查"
echo "2. 手動執行所有檢查: pre-commit run --all-files"
echo "3. 更新 hooks: pre-commit autoupdate"
echo "4. 跳過檢查 (緊急情況): git commit --no-verify"
echo ""
echo "⚠️  第一次執行可能需要下載依賴，請耐心等待"