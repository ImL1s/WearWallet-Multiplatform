#!/bin/bash

echo "🔒 WearWallet 安全檢查腳本"
echo "=========================="

# 設定顏色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 檢查是否有 API 金鑰洩漏的常見模式
API_KEY_PATTERNS=(
    "[A-Z0-9]{32}"                    # 32 字符大寫字母數字（常見 API 金鑰格式）
    "sk-[a-zA-Z0-9]{48}"             # OpenAI API 金鑰格式
    "ghp_[a-zA-Z0-9]{36}"            # GitHub Personal Access Token
    "AKIA[0-9A-Z]{16}"               # AWS Access Key ID
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" # UUID 格式
)

SUSPICIOUS_FOUND=0
TOTAL_CHECKS=0

echo -e "\n${BLUE}1. 檢查源代碼中的可疑 API 金鑰模式...${NC}"

# 排除的檔案和目錄
EXCLUDE_PATTERNS="--exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=node_modules --exclude=*.log --exclude=.env --exclude=.env.*"

for pattern in "${API_KEY_PATTERNS[@]}"; do
    echo -e "檢查模式: ${pattern}"
    
    # 搜尋但排除 .env 檔案和其他不需要檢查的檔案
    if results=$(grep -r -E "$pattern" . $EXCLUDE_PATTERNS 2>/dev/null | grep -v "Binary file"); then
        if [[ -n "$results" ]]; then
            echo -e "${RED}⚠️  發現可疑的 API 金鑰模式:${NC}"
            echo "$results"
            ((SUSPICIOUS_FOUND++))
        fi
    fi
    ((TOTAL_CHECKS++))
done

echo -e "\n${BLUE}2. 檢查常見的 API 金鑰字段名稱...${NC}"

# 檢查常見的 API 金鑰欄位名稱（但忽略範例和註釋）
KEY_FIELD_PATTERNS=(
    "api_key.*=.*[^your_|example|placeholder]"
    "apikey.*=.*[^your_|example|placeholder]"
    "secret.*=.*[^your_|example|placeholder]"
    "token.*=.*[^your_|example|placeholder]"
    "password.*=.*[^your_|example|placeholder]"
)

for pattern in "${KEY_FIELD_PATTERNS[@]}"; do
    echo -e "檢查欄位: ${pattern}"
    
    if results=$(grep -r -i -E "$pattern" . $EXCLUDE_PATTERNS --exclude="*.md" 2>/dev/null | grep -v "Binary file" | grep -v "your_" | grep -v "example" | grep -v "placeholder" | grep -v "// " | grep -v "# "); then
        if [[ -n "$results" ]]; then
            echo -e "${RED}⚠️  發現可疑的 API 金鑰賦值:${NC}"
            echo "$results"
            ((SUSPICIOUS_FOUND++))
        fi
    fi
    ((TOTAL_CHECKS++))
done

echo -e "\n${BLUE}3. 檢查 .env 檔案是否在 .gitignore 中...${NC}"

if grep -q "^\.env$" .gitignore; then
    echo -e "${GREEN}✅ .env 檔案已正確加入 .gitignore${NC}"
else
    echo -e "${RED}❌ .env 檔案未加入 .gitignore！${NC}"
    ((SUSPICIOUS_FOUND++))
fi

echo -e "\n${BLUE}4. 檢查是否有意外提交的敏感檔案...${NC}"

SENSITIVE_FILES=(
    ".env"
    ".env.local"
    ".env.production"
    "secrets.properties"
    "gradle.properties"
    "*.key"
    "*.pem"
    "*.p12"
    "*.jks"
    "*.keystore"
)

for file_pattern in "${SENSITIVE_FILES[@]}"; do
    if [[ -f "$file_pattern" ]] || ls $file_pattern 1> /dev/null 2>&1; then
        if git ls-files --error-unmatch "$file_pattern" 2>/dev/null; then
            echo -e "${RED}❌ 敏感檔案 $file_pattern 已被追蹤！${NC}"
            ((SUSPICIOUS_FOUND++))
        else
            echo -e "${GREEN}✅ 敏感檔案 $file_pattern 未被追蹤${NC}"
        fi
    fi
done

echo -e "\n${BLUE}5. 檢查 Git 歷史中的敏感資訊...${NC}"

# 檢查最近 10 次提交是否包含可疑內容
if git log --oneline -10 | grep -i -E "(key|secret|token|password)" > /dev/null; then
    echo -e "${YELLOW}⚠️  最近的提交訊息中包含敏感詞彙，請檢查是否適當${NC}"
    git log --oneline -10 | grep -i -E "(key|secret|token|password)"
else
    echo -e "${GREEN}✅ 最近的提交訊息中未發現敏感詞彙${NC}"
fi

echo -e "\n${BLUE}6. 檢查文檔中是否有真實 API 金鑰...${NC}"

# 檢查 docs 目錄中是否有真實的 API 金鑰
if find docs/ -name "*.md" -exec grep -l -E "[A-Z0-9]{20,}" {} \; 2>/dev/null | head -1; then
    echo -e "${RED}⚠️  文檔中可能包含真實 API 金鑰:${NC}"
    find docs/ -name "*.md" -exec grep -H -E "[A-Z0-9]{20,}" {} \; 2>/dev/null | head -5
    ((SUSPICIOUS_FOUND++))
else
    echo -e "${GREEN}✅ 文檔中未發現可疑 API 金鑰${NC}"
fi

# 總結
echo -e "\n${BLUE}安全檢查總結...${NC}"
echo "總檢查項目: $TOTAL_CHECKS"
echo "發現可疑項目: $SUSPICIOUS_FOUND"

if [[ $SUSPICIOUS_FOUND -eq 0 ]]; then
    echo -e "${GREEN}🎉 安全檢查通過！沒有發現敏感資訊洩漏${NC}"
    exit 0
else
    echo -e "${RED}⚠️  發現 $SUSPICIOUS_FOUND 個潛在安全問題，請檢查並修正${NC}"
    echo ""
    echo -e "${YELLOW}修正建議:${NC}"
    echo "1. 移除源代碼中的真實 API 金鑰"
    echo "2. 將敏感資訊移至 .env 檔案"
    echo "3. 確保 .env 檔案在 .gitignore 中"
    echo "4. 使用 1Password 等工具管理 API 金鑰"
    echo "5. 如有必要，使用 git filter-branch 清理歷史"
    exit 1
fi