#!/bin/bash

# 獲取主要 RPC 端點的證書指紋
# 用於配置 Certificate Pinning，防止中間人攻擊

echo "=========================================="
echo "獲取證書指紋 (SHA-256)"
echo "=========================================="
echo ""

# 函數：獲取證書指紋
get_cert_pin() {
    local host=$1
    local port=${2:-443}
    local name=$3

    echo "正在連接 $name ($host:$port)..."

    # 獲取證書並計算 SHA-256 指紋
    local pin=$(echo -n | openssl s_client -connect $host:$port -servername $host 2>/dev/null | \
        openssl x509 -pubkey -noout 2>/dev/null | \
        openssl pkey -pubin -outform der 2>/dev/null | \
        openssl dgst -sha256 -binary 2>/dev/null | \
        openssl base64 2>/dev/null)

    if [ -z "$pin" ]; then
        echo "  ❌ 失敗：無法獲取證書"
        echo "  sha256/PLACEHOLDER_FOR_$name"
    else
        echo "  ✅ 成功"
        echo "  sha256/$pin"
    fi
    echo ""
}

# 主要 RPC 端點
echo "=== Ethereum & EVM 鏈 ==="
echo ""

get_cert_pin "mainnet.infura.io" 443 "Infura_Ethereum"
get_cert_pin "sepolia.infura.io" 443 "Infura_Sepolia"
get_cert_pin "eth-mainnet.g.alchemy.com" 443 "Alchemy_Ethereum"
get_cert_pin "polygon-mainnet.g.alchemy.com" 443 "Alchemy_Polygon"
get_cert_pin "rpc.ankr.com" 443 "Ankr"

echo "=== BSC ==="
echo ""
get_cert_pin "bsc-dataseed.binance.org" 443 "BSC_Official"
get_cert_pin "bsc-dataseed1.defibit.io" 443 "BSC_DeFibit"

echo "=== Polygon ==="
echo ""
get_cert_pin "polygon-rpc.com" 443 "Polygon_Official"

echo "=== Arbitrum ==="
echo ""
get_cert_pin "arb1.arbitrum.io" 443 "Arbitrum"

echo "=== Optimism ==="
echo ""
get_cert_pin "mainnet.optimism.io" 443 "Optimism"

echo "=== Solana ==="
echo ""
get_cert_pin "api.mainnet-beta.solana.com" 443 "Solana_Mainnet"
get_cert_pin "api.devnet.solana.com" 443 "Solana_Devnet"

echo "=== Avalanche ==="
echo ""
get_cert_pin "api.avax.network" 443 "Avalanche"

echo "=== Base ==="
echo ""
get_cert_pin "mainnet.base.org" 443 "Base"

echo "=== 區塊瀏覽器 API ==="
echo ""
get_cert_pin "api.etherscan.io" 443 "Etherscan"
get_cert_pin "api.bscscan.com" 443 "BSCScan"
get_cert_pin "api.polygonscan.com" 443 "PolygonScan"

echo "=========================================="
echo "證書指紋獲取完成"
echo "=========================================="
echo ""
echo "注意事項："
echo "1. 請將上述 SHA-256 指紋複製到 CertificatePinningConfig.kt"
echo "2. 每個域名應配置主證書和備份證書"
echo "3. 建議每 90 天檢查一次證書是否即將過期"
echo "4. 如果證書更新，需要發布應用更新"
echo ""
