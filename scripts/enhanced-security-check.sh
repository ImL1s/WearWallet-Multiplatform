#!/bin/bash

# WearWallet 增強版安全檢查腳本
# 包含多層次的安全掃描和加密貨幣錢包特定檢查

set -e

# 顏色設定
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

# 檢查結果統計
TOTAL_CHECKS=0
CRITICAL_ISSUES=0
HIGH_ISSUES=0
MEDIUM_ISSUES=0
LOW_ISSUES=0

echo -e "${PURPLE}🔐 WearWallet 增強版安全檢查${NC}"
echo "================================"
echo ""

# 建立報告目錄
REPORT_DIR="security-reports/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$REPORT_DIR"

# 記錄問題
log_issue() {
    local severity=$1
    local category=$2
    local message=$3
    local details=$4
    
    echo -e "${RED}[${severity}]${NC} ${category}: ${message}"
    if [[ -n "$details" ]]; then
        echo -e "  詳細: ${details}"
    fi
    
    # 寫入報告檔案
    echo "[${severity}] ${category}: ${message}" >> "$REPORT_DIR/security-issues.txt"
    if [[ -n "$details" ]]; then
        echo "  詳細: ${details}" >> "$REPORT_DIR/security-issues.txt"
    fi
    echo "" >> "$REPORT_DIR/security-issues.txt"
    
    # 更新計數
    case $severity in
        CRITICAL) ((CRITICAL_ISSUES++)) ;;
        HIGH) ((HIGH_ISSUES++)) ;;
        MEDIUM) ((MEDIUM_ISSUES++)) ;;
        LOW) ((LOW_ISSUES++)) ;;
    esac
}

# 1. 加密貨幣錢包特定檢查
echo -e "${BLUE}1. 加密貨幣錢包安全檢查${NC}"
echo "-------------------------"
((TOTAL_CHECKS++))

# 檢查私鑰處理
echo "檢查私鑰處理..."
if grep -r -E "(private_key|privateKey|PRIVATE_KEY)" --include="*.kt" --include="*.java" --include="*.swift" . | grep -v -E "(test|Test|mock|Mock|example|Example)" | grep -v "// " | head -5; then
    log_issue "CRITICAL" "私鑰處理" "發現可能的私鑰硬編碼" "$(grep -r -E '(private_key|privateKey|PRIVATE_KEY)' --include='*.kt' --include='*.java' --include='*.swift' . | head -3)"
fi

# 檢查助記詞處理
echo "檢查助記詞處理..."
if grep -r -E "(mnemonic|seedPhrase|seed_phrase)" --include="*.kt" --include="*.java" --include="*.swift" . | grep -E "\"[a-z]+ [a-z]+ [a-z]+" | head -5; then
    log_issue "CRITICAL" "助記詞安全" "發現可能的助記詞硬編碼"
fi

# 檢查加密實現
echo "檢查加密實現..."
if grep -r -E "(AES|DES|3DES|RC4)" --include="*.kt" --include="*.java" . | grep -v "AES-256" | grep -v "// " | head -5; then
    log_issue "HIGH" "加密算法" "發現可能不安全的加密算法使用"
fi

# 2. Android 特定安全檢查
echo -e "\n${BLUE}2. Android 安全檢查${NC}"
echo "-------------------"
((TOTAL_CHECKS++))

# 檢查 AndroidManifest.xml
for manifest in $(find . -name "AndroidManifest.xml" -not -path "./build/*"); do
    echo "檢查 $manifest..."
    
    # 檢查 debuggable
    if grep -q "android:debuggable=\"true\"" "$manifest"; then
        log_issue "HIGH" "Android配置" "發現 debuggable=true" "$manifest"
    fi
    
    # 檢查 allowBackup
    if grep -q "android:allowBackup=\"true\"" "$manifest"; then
        log_issue "MEDIUM" "Android配置" "發現 allowBackup=true，可能導致敏感資料洩漏" "$manifest"
    fi
    
    # 檢查明文流量
    if ! grep -q "android:usesCleartextTraffic=\"false\"" "$manifest"; then
        log_issue "HIGH" "網路安全" "未禁用明文流量" "$manifest"
    fi
    
    # 檢查導出的組件
    if grep -E "android:exported=\"true\"" "$manifest" | grep -v -E "(MAIN|LAUNCHER)"; then
        log_issue "MEDIUM" "組件安全" "發現可能不必要的導出組件" "$manifest"
    fi
done

# 3. iOS/watchOS 安全檢查
echo -e "\n${BLUE}3. iOS/watchOS 安全檢查${NC}"
echo "------------------------"
((TOTAL_CHECKS++))

# 檢查 Info.plist
for plist in $(find . -name "Info.plist" -not -path "./build/*" -not -path "./DerivedData/*"); do
    echo "檢查 $plist..."
    
    # 檢查 ATS 設定
    if grep -A 5 "NSAppTransportSecurity" "$plist" | grep -q "NSAllowsArbitraryLoads.*true"; then
        log_issue "HIGH" "iOS網路安全" "發現 ATS 被禁用" "$plist"
    fi
    
    # 檢查 URL Schemes
    if grep -q "CFBundleURLSchemes" "$plist"; then
        log_issue "LOW" "iOS配置" "發現自定義 URL Scheme，請確認安全性" "$plist"
    fi
done

# 4. 依賴套件安全檢查
echo -e "\n${BLUE}4. 依賴套件安全檢查${NC}"
echo "--------------------"
((TOTAL_CHECKS++))

# 檢查已知有漏洞的套件版本
echo "檢查已知漏洞套件..."
vulnerable_packages=(
    "log4j:2.0-2.16"
    "spring-core:5.0.0-5.3.17"
    "jackson-databind:2.0.0-2.12.6"
)

for package in "${vulnerable_packages[@]}"; do
    pkg_name=$(echo $package | cut -d: -f1)
    pkg_versions=$(echo $package | cut -d: -f2)
    
    if grep -r "$pkg_name" build.gradle* gradle.properties; then
        log_issue "HIGH" "依賴漏洞" "發現可能有漏洞的套件: $pkg_name" "請檢查版本是否在 $pkg_versions 範圍內"
    fi
done

# 5. 程式碼品質與安全模式檢查
echo -e "\n${BLUE}5. 程式碼安全模式檢查${NC}"
echo "----------------------"
((TOTAL_CHECKS++))

# 檢查 SQL 注入風險
echo "檢查 SQL 注入風險..."
if grep -r -E "rawQuery|execSQL" --include="*.kt" --include="*.java" . | grep -E "\\\$|\\+.*getString" | head -5; then
    log_issue "HIGH" "SQL注入" "發現可能的 SQL 注入風險"
fi

# 檢查不安全的隨機數生成
echo "檢查隨機數生成..."
if grep -r -E "Random\\(\\)|Math\\.random" --include="*.kt" --include="*.java" . | grep -v -E "(test|Test)" | head -5; then
    log_issue "MEDIUM" "密碼學安全" "發現不安全的隨機數生成，應使用 SecureRandom"
fi

# 檢查硬編碼的 URL
echo "檢查硬編碼 URL..."
if grep -r -E "https?://[^\"]*\\.(infura|moralis|alchemy)\\.io" --include="*.kt" --include="*.java" --include="*.swift" . | grep -v -E "(test|Test|example|Example)" | head -5; then
    log_issue "MEDIUM" "配置安全" "發現硬編碼的 API 端點"
fi

# 6. Git 歷史安全檢查
echo -e "\n${BLUE}6. Git 歷史安全檢查${NC}"
echo "--------------------"
((TOTAL_CHECKS++))

# 檢查大檔案
echo "檢查大檔案..."
large_files=$(git rev-list --all --objects | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' | awk '$1=="blob" && $3>1000000 {print $4, $3/1024/1024 " MB"}' | head -10)
if [[ -n "$large_files" ]]; then
    log_issue "LOW" "Git歷史" "發現大檔案" "$large_files"
fi

# 檢查敏感檔案類型
echo "檢查敏感檔案類型..."
sensitive_extensions=("key" "pem" "p12" "jks" "keystore" "cer" "crt" "pfx")
for ext in "${sensitive_extensions[@]}"; do
    if git ls-files | grep -E "\\.${ext}$" | head -5; then
        log_issue "HIGH" "敏感檔案" "發現 .${ext} 檔案在版本控制中"
    fi
done

# 7. 網路安全配置檢查
echo -e "\n${BLUE}7. 網路安全配置檢查${NC}"
echo "---------------------"
((TOTAL_CHECKS++))

# 檢查網路安全配置檔案
network_config="wear/src/main/res/xml/network_security_config.xml"
if [[ -f "$network_config" ]]; then
    echo "檢查網路安全配置..."
    if grep -q "cleartextTrafficPermitted=\"true\"" "$network_config"; then
        log_issue "HIGH" "網路配置" "網路安全配置允許明文流量" "$network_config"
    fi
    if grep -q "<certificates src=\"user\"" "$network_config"; then
        log_issue "MEDIUM" "網路配置" "網路安全配置信任用戶證書" "$network_config"
    fi
else
    log_issue "MEDIUM" "網路配置" "缺少網路安全配置檔案" "建議創建 network_security_config.xml"
fi

# 8. 權限檢查
echo -e "\n${BLUE}8. 權限使用檢查${NC}"
echo "-----------------"
((TOTAL_CHECKS++))

# 檢查危險權限
dangerous_permissions=(
    "WRITE_EXTERNAL_STORAGE"
    "READ_PHONE_STATE"
    "ACCESS_FINE_LOCATION"
    "RECORD_AUDIO"
    "CAMERA"
)

for manifest in $(find . -name "AndroidManifest.xml" -not -path "./build/*"); do
    for perm in "${dangerous_permissions[@]}"; do
        if grep -q "android.permission.$perm" "$manifest"; then
            log_issue "LOW" "權限使用" "使用危險權限: $perm" "$manifest - 請確認是否必要"
        fi
    done
done

# 生成安全報告
echo -e "\n${BLUE}生成安全報告...${NC}"
cat > "$REPORT_DIR/security-report.html" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>WearWallet Security Report</title>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }
        h2 { color: #555; margin-top: 30px; }
        .summary { display: flex; justify-content: space-around; margin: 20px 0; }
        .summary-item { text-align: center; padding: 20px; border-radius: 8px; flex: 1; margin: 0 10px; }
        .critical { background-color: #dc3545; color: white; }
        .high { background-color: #fd7e14; color: white; }
        .medium { background-color: #ffc107; color: black; }
        .low { background-color: #28a745; color: white; }
        .issues { margin-top: 20px; }
        .issue { padding: 10px; margin: 10px 0; border-left: 4px solid; border-radius: 4px; background-color: #f9f9f9; }
        .issue-critical { border-color: #dc3545; }
        .issue-high { border-color: #fd7e14; }
        .issue-medium { border-color: #ffc107; }
        .issue-low { border-color: #28a745; }
        .timestamp { color: #666; font-size: 0.9em; }
        .recommendations { background-color: #e3f2fd; padding: 15px; border-radius: 4px; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔐 WearWallet Security Report</h1>
        <p class="timestamp">Generated: $(date '+%Y-%m-%d %H:%M:%S')</p>
        
        <h2>Summary</h2>
        <div class="summary">
            <div class="summary-item critical">
                <h3>${CRITICAL_ISSUES}</h3>
                <p>Critical</p>
            </div>
            <div class="summary-item high">
                <h3>${HIGH_ISSUES}</h3>
                <p>High</p>
            </div>
            <div class="summary-item medium">
                <h3>${MEDIUM_ISSUES}</h3>
                <p>Medium</p>
            </div>
            <div class="summary-item low">
                <h3>${LOW_ISSUES}</h3>
                <p>Low</p>
            </div>
        </div>
        
        <h2>Security Checks Performed</h2>
        <ul>
            <li>✓ Cryptocurrency wallet security (private keys, mnemonics, encryption)</li>
            <li>✓ Android security configuration</li>
            <li>✓ iOS/watchOS security settings</li>
            <li>✓ Dependency vulnerability scanning</li>
            <li>✓ Code security patterns</li>
            <li>✓ Git history analysis</li>
            <li>✓ Network security configuration</li>
            <li>✓ Permission usage audit</li>
        </ul>
        
        <h2>Recommendations</h2>
        <div class="recommendations">
            <h3>🛡️ Security Best Practices for Crypto Wallets</h3>
            <ul>
                <li>Always use hardware-backed key storage (Android Keystore, iOS Keychain)</li>
                <li>Implement secure key derivation with proper salt and iterations</li>
                <li>Never log or transmit private keys or mnemonics</li>
                <li>Use secure random number generation for all cryptographic operations</li>
                <li>Implement proper session management and timeout</li>
                <li>Enable certificate pinning for all API communications</li>
                <li>Regular security audits and penetration testing</li>
            </ul>
        </div>
    </div>
</body>
</html>
EOF

# 顯示總結
echo ""
echo -e "${BLUE}安全檢查完成！${NC}"
echo "=================="
echo -e "檢查項目數: ${TOTAL_CHECKS}"
echo -e "${RED}嚴重問題: ${CRITICAL_ISSUES}${NC}"
echo -e "${YELLOW}高風險問題: ${HIGH_ISSUES}${NC}"
echo -e "${YELLOW}中風險問題: ${MEDIUM_ISSUES}${NC}"
echo -e "${GREEN}低風險問題: ${LOW_ISSUES}${NC}"
echo ""
echo -e "詳細報告: ${BLUE}${REPORT_DIR}/security-report.html${NC}"
echo -e "問題清單: ${BLUE}${REPORT_DIR}/security-issues.txt${NC}"

# 根據嚴重程度返回適當的退出碼
if [[ $CRITICAL_ISSUES -gt 0 ]]; then
    echo -e "\n${RED}⚠️  發現嚴重安全問題，請立即修復！${NC}"
    exit 2
elif [[ $HIGH_ISSUES -gt 0 ]]; then
    echo -e "\n${YELLOW}⚠️  發現高風險問題，建議盡快修復${NC}"
    exit 1
else
    echo -e "\n${GREEN}✅ 未發現嚴重安全問題${NC}"
    exit 0
fi