#!/bin/bash

# ============================================
# Git 遷移工作流程腳本
# 用途：提供安全的 Git 分支管理和回滾策略
# 作者：Claude AI Assistant
# 日期：2025-10-22
# ============================================

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置
MIGRATION_BRANCH="refactor/remove-sharedkmp-phase1"
BACKUP_BRANCH="backup/pre-migration-$(date +%Y%m%d_%H%M%S)"
CURRENT_BRANCH=$(git branch --show-current)

# 函數：顯示當前狀態
show_status() {
    echo -e "${BLUE}=== Git 狀態 ===${NC}"
    echo "當前分支：${CURRENT_BRANCH}"
    echo "提交：$(git rev-parse --short HEAD)"
    echo ""
    echo -e "${YELLOW}未提交的變更：${NC}"
    git status --short
    echo ""
}

# 函數：檢查是否有未提交的變更
check_uncommitted_changes() {
    if [[ -n $(git status --porcelain) ]]; then
        echo -e "${YELLOW}警告：有未提交的變更${NC}"
        return 1
    else
        echo -e "${GREEN}✓ 工作目錄乾淨${NC}"
        return 0
    fi
}

# 函數：創建備份分支
create_backup_branch() {
    echo -e "${BLUE}[備份] 創建備份分支：${BACKUP_BRANCH}${NC}"
    git branch "${BACKUP_BRANCH}"
    echo -e "${GREEN}✓ 備份分支已創建${NC}"
    echo ""
}

# 函數：創建遷移分支
create_migration_branch() {
    echo -e "${BLUE}[分支] 創建遷移分支：${MIGRATION_BRANCH}${NC}"

    # 檢查分支是否已存在
    if git show-ref --verify --quiet "refs/heads/${MIGRATION_BRANCH}"; then
        echo -e "${YELLOW}分支 ${MIGRATION_BRANCH} 已存在${NC}"
        read -p "是否要刪除並重新創建？(y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git branch -D "${MIGRATION_BRANCH}"
            echo -e "${GREEN}✓ 舊分支已刪除${NC}"
        else
            echo -e "${RED}操作已取消${NC}"
            exit 1
        fi
    fi

    git checkout -b "${MIGRATION_BRANCH}"
    echo -e "${GREEN}✓ 遷移分支已創建並切換${NC}"
    echo ""
}

# 函數：Stash 當前變更
stash_changes() {
    local stash_message="Migration backup - $(date +%Y%m%d_%H%M%S)"

    echo -e "${BLUE}[Stash] 暫存當前變更${NC}"
    git stash push -u -m "${stash_message}"
    echo -e "${GREEN}✓ 變更已暫存：${stash_message}${NC}"
    echo ""

    # 顯示最新的 stash
    echo -e "${YELLOW}最新的 stash：${NC}"
    git stash list | head -3
    echo ""
}

# 函數：恢復 stash
restore_stash() {
    echo -e "${BLUE}[Stash] 恢復暫存的變更${NC}"

    # 顯示可用的 stash
    echo -e "${YELLOW}可用的 stash：${NC}"
    git stash list
    echo ""

    read -p "請輸入要恢復的 stash 編號（例如：0）：" stash_index

    if git stash apply "stash@{${stash_index}}"; then
        echo -e "${GREEN}✓ Stash 已恢復${NC}"

        read -p "是否要刪除這個 stash？(y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git stash drop "stash@{${stash_index}}"
            echo -e "${GREEN}✓ Stash 已刪除${NC}"
        fi
    else
        echo -e "${RED}✗ Stash 恢復失敗${NC}"
        exit 1
    fi
}

# 函數：提交變更
commit_changes() {
    local commit_message=$1

    echo -e "${BLUE}[提交] 提交變更${NC}"
    git add -A
    git commit -m "${commit_message}"
    echo -e "${GREEN}✓ 變更已提交${NC}"
    echo ""
}

# 函數：回滾到指定提交
rollback_to_commit() {
    local commit_hash=$1
    local rollback_mode=${2:-"soft"}  # soft, mixed, hard

    echo -e "${YELLOW}[回滾] 回滾到提交：${commit_hash}${NC}"
    echo "模式：${rollback_mode}"

    case $rollback_mode in
        soft)
            git reset --soft "${commit_hash}"
            echo -e "${GREEN}✓ Soft reset 完成（變更保留在暫存區）${NC}"
            ;;
        mixed)
            git reset --mixed "${commit_hash}"
            echo -e "${GREEN}✓ Mixed reset 完成（變更保留在工作目錄）${NC}"
            ;;
        hard)
            echo -e "${RED}警告：Hard reset 會永久刪除變更${NC}"
            read -p "確定要繼續嗎？(y/N) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                git reset --hard "${commit_hash}"
                echo -e "${GREEN}✓ Hard reset 完成${NC}"
            else
                echo -e "${YELLOW}操作已取消${NC}"
                return 1
            fi
            ;;
        *)
            echo -e "${RED}無效的回滾模式：${rollback_mode}${NC}"
            return 1
            ;;
    esac
}

# 函數：回滾到備份分支
rollback_to_backup() {
    echo -e "${BLUE}[回滾] 回滾到備份分支${NC}"

    # 顯示可用的備份分支
    echo -e "${YELLOW}可用的備份分支：${NC}"
    git branch | grep "backup/" || echo "無可用的備份分支"
    echo ""

    read -p "請輸入要回滾的備份分支名稱（例如：${BACKUP_BRANCH}）：" backup_branch

    if git show-ref --verify --quiet "refs/heads/${backup_branch}"; then
        git checkout "${backup_branch}"
        echo -e "${GREEN}✓ 已切換到備份分支：${backup_branch}${NC}"
    else
        echo -e "${RED}✗ 備份分支不存在：${backup_branch}${NC}"
        exit 1
    fi
}

# 函數：完整回滾流程
full_rollback() {
    echo -e "${RED}=== 完整回滾流程 ===${NC}"
    echo ""

    # 1. 回滾當前變更
    echo -e "${YELLOW}步驟 1：清除當前變更${NC}"
    git reset --hard HEAD
    git clean -fd
    echo -e "${GREEN}✓ 工作目錄已清除${NC}"
    echo ""

    # 2. 切換到備份分支
    echo -e "${YELLOW}步驟 2：切換到備份分支${NC}"
    rollback_to_backup
    echo ""

    # 3. 刪除遷移分支（可選）
    echo -e "${YELLOW}步驟 3：刪除遷移分支${NC}"
    read -p "是否要刪除遷移分支 ${MIGRATION_BRANCH}？(y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git branch -D "${MIGRATION_BRANCH}"
        echo -e "${GREEN}✓ 遷移分支已刪除${NC}"
    fi
    echo ""

    echo -e "${GREEN}=== 完整回滾完成 ===${NC}"
}

# 函數：顯示提交歷史
show_commit_history() {
    local count=${1:-10}

    echo -e "${BLUE}=== 最近 ${count} 次提交 ===${NC}"
    git log --oneline --graph --decorate -${count}
    echo ""
}

# 主選單
show_menu() {
    echo -e "${BLUE}=== Git 遷移工作流程工具 ===${NC}"
    echo ""
    echo "1) 顯示當前狀態"
    echo "2) 創建備份分支"
    echo "3) 創建遷移分支"
    echo "4) Stash 當前變更"
    echo "5) 恢復 Stash"
    echo "6) 提交變更"
    echo "7) 回滾到指定提交"
    echo "8) 完整回滾到備份"
    echo "9) 顯示提交歷史"
    echo "0) 退出"
    echo ""
}

# 互動式模式
interactive_mode() {
    while true; do
        show_menu
        read -p "請選擇操作：" choice

        case $choice in
            1)
                show_status
                ;;
            2)
                create_backup_branch
                ;;
            3)
                create_migration_branch
                ;;
            4)
                stash_changes
                ;;
            5)
                restore_stash
                ;;
            6)
                read -p "請輸入提交訊息：" msg
                commit_changes "$msg"
                ;;
            7)
                read -p "請輸入提交 hash：" hash
                read -p "請輸入回滾模式 (soft/mixed/hard)：" mode
                rollback_to_commit "$hash" "$mode"
                ;;
            8)
                full_rollback
                ;;
            9)
                read -p "要顯示多少次提交？(預設 10)：" count
                count=${count:-10}
                show_commit_history "$count"
                ;;
            0)
                echo -e "${GREEN}再見！${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}無效的選擇${NC}"
                ;;
        esac

        echo ""
        read -p "按 Enter 繼續..."
        clear
    done
}

# 快速啟動模式
quick_start() {
    echo -e "${BLUE}=== 快速啟動遷移工作流程 ===${NC}"
    echo ""

    show_status

    # 檢查未提交的變更
    if ! check_uncommitted_changes; then
        read -p "是否要 stash 這些變更？(Y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Nn]$ ]]; then
            stash_changes
        fi
    fi

    # 創建備份分支
    create_backup_branch

    # 創建遷移分支
    create_migration_branch

    echo -e "${GREEN}=== 遷移環境準備完成 ===${NC}"
    echo ""
    echo "當前分支：$(git branch --show-current)"
    echo "備份分支：${BACKUP_BRANCH}"
    echo ""
    echo -e "${YELLOW}提示：${NC}"
    echo "- 現在可以開始進行遷移工作"
    echo "- 如需回滾，執行：./scripts/git-migration-workflow.sh rollback"
    echo "- 如需查看更多選項，執行：./scripts/git-migration-workflow.sh menu"
}

# 主程式
main() {
    case "${1:-quick}" in
        quick)
            quick_start
            ;;
        menu)
            interactive_mode
            ;;
        rollback)
            full_rollback
            ;;
        status)
            show_status
            ;;
        history)
            show_commit_history "${2:-10}"
            ;;
        *)
            echo "用法："
            echo "  $0 quick      - 快速啟動遷移流程（預設）"
            echo "  $0 menu       - 互動式選單"
            echo "  $0 rollback   - 完整回滾"
            echo "  $0 status     - 顯示狀態"
            echo "  $0 history [n] - 顯示提交歷史"
            exit 1
            ;;
    esac
}

main "$@"
