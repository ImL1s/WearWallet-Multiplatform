#!/bin/bash

# WearWallet Build Performance Analysis Script
# 分析並優化 Gradle 建置效能 (2025 最佳實踐)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORTS_DIR="$PROJECT_ROOT/build-performance-reports"

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 WearWallet Build Performance Analysis${NC}"
echo "========================================"

# 創建報告目錄
mkdir -p "$REPORTS_DIR"

# 清理函數
cleanup() {
    echo -e "${YELLOW}🧹 Cleaning up...${NC}"
    cd "$PROJECT_ROOT"
    ./gradlew clean > /dev/null 2>&1 || true
}

# 設置清理陷阱
trap cleanup EXIT

# 檢查系統資源
check_system_resources() {
    echo -e "${BLUE}📊 System Resources Analysis${NC}"
    echo "----------------------------"
    
    # CPU 信息
    if [[ "$OSTYPE" == "darwin"* ]]; then
        CPU_COUNT=$(sysctl -n hw.ncpu)
        MEMORY_GB=$(echo "$(sysctl -n hw.memsize) / 1024 / 1024 / 1024" | bc)
    else
        CPU_COUNT=$(nproc)
        MEMORY_GB=$(free -g | awk '/^Mem:/{print $2}')
    fi
    
    echo "CPU Cores: $CPU_COUNT"
    echo "Memory: ${MEMORY_GB}GB"
    
    # 建議的 Gradle 設置
    RECOMMENDED_HEAP=$((MEMORY_GB * 1024 / 4))  # 25% of total memory
    RECOMMENDED_WORKERS=$((CPU_COUNT > 8 ? 8 : CPU_COUNT))
    
    echo -e "${GREEN}💡 Recommended Settings:${NC}"
    echo "  org.gradle.jvmargs=-Xmx${RECOMMENDED_HEAP}m"
    echo "  org.gradle.workers.max=$RECOMMENDED_WORKERS"
    echo ""
}

# 測量建置時間
measure_build_time() {
    local build_type="$1"
    local gradle_args="$2"
    local description="$3"
    
    echo -e "${BLUE}⏱️  Measuring: $description${NC}"
    
    # 清理快取
    ./gradlew clean > /dev/null 2>&1
    
    # 測量建置時間
    start_time=$(date +%s.%N)
    
    if ./gradlew $gradle_args --profile --no-daemon 2>&1 | tee "$REPORTS_DIR/${build_type}.log"; then
        end_time=$(date +%s.%N)
        duration=$(echo "$end_time - $start_time" | bc)
        
        echo -e "${GREEN}✅ $description: ${duration}s${NC}"
        echo "$build_type,$duration,$description" >> "$REPORTS_DIR/timing_results.csv"
        
        # 移動 profile 報告
        find . -name "profile-*.html" -exec mv {} "$REPORTS_DIR/${build_type}-profile.html" \; 2>/dev/null || true
        
        return 0
    else
        echo -e "${RED}❌ $description: FAILED${NC}"
        return 1
    fi
}

# 分析 profile 報告
analyze_profile_reports() {
    echo -e "${BLUE}📈 Analyzing Profile Reports${NC}"
    echo "-----------------------------"
    
    for profile_file in "$REPORTS_DIR"/*-profile.html; do
        if [[ -f "$profile_file" ]]; then
            build_name=$(basename "$profile_file" -profile.html)
            echo "Profile report available: $profile_file"
            
            # 提取關鍵指標 (需要 HTML 解析)
            if command -v pup > /dev/null 2>&1; then
                total_time=$(pup 'div.time text{}' < "$profile_file" | head -1 | tr -d ' ')
                echo "  Total time: $total_time"
            fi
        fi
    done
    echo ""
}

# 依賴解析分析
analyze_dependency_resolution() {
    echo -e "${BLUE}📦 Dependency Resolution Analysis${NC}"
    echo "---------------------------------"
    
    # 生成依賴報告
    echo "Generating dependency insight report..."
    ./gradlew dependencyInsight --dependency org.jetbrains.kotlin:kotlin-stdlib \
        --no-daemon > "$REPORTS_DIR/dependency_insight.txt" 2>&1
    
    # 分析重複依賴
    echo "Checking for duplicate dependencies..."
    ./gradlew dependencies --no-daemon 2>/dev/null | \
        grep -E "^[+\\-]" | \
        sed 's/^[+\\-]*[[:space:]]*//' | \
        sort | uniq -d > "$REPORTS_DIR/duplicate_dependencies.txt"
    
    dup_count=$(wc -l < "$REPORTS_DIR/duplicate_dependencies.txt")
    if [[ $dup_count -gt 0 ]]; then
        echo -e "${YELLOW}⚠️  Found $dup_count duplicate dependencies${NC}"
    else
        echo -e "${GREEN}✅ No duplicate dependencies found${NC}"
    fi
    echo ""
}

# Gradle 快取分析
analyze_gradle_cache() {
    echo -e "${BLUE}💾 Gradle Cache Analysis${NC}"
    echo "------------------------"
    
    # 快取大小
    if [[ -d ~/.gradle/caches ]]; then
        cache_size=$(du -sh ~/.gradle/caches | cut -f1)
        echo "Gradle cache size: $cache_size"
    fi
    
    # 建置快取統計
    echo "Testing build cache effectiveness..."
    
    # 第一次建置
    ./gradlew clean build --build-cache --no-daemon > /dev/null 2>&1
    
    # 第二次建置 (應該更快)
    start_time=$(date +%s.%N)
    ./gradlew build --build-cache --no-daemon > /dev/null 2>&1
    end_time=$(date +%s.%N)
    cached_duration=$(echo "$end_time - $start_time" | bc)
    
    echo -e "${GREEN}Cached build time: ${cached_duration}s${NC}"
    echo ""
}

# 模組並行建置測試
test_parallel_builds() {
    echo -e "${BLUE}⚡ Parallel Build Testing${NC}"
    echo "-------------------------"
    
    # 測試不同並行設置
    for workers in 1 2 4 8; do
        if [[ $workers -le $CPU_COUNT ]]; then
            echo "Testing with $workers workers..."
            
            # 臨時修改 gradle.properties
            backup_props="$PROJECT_ROOT/gradle.properties.backup"
            cp "$PROJECT_ROOT/gradle.properties" "$backup_props"
            
            sed -i.tmp "s/org.gradle.workers.max=.*/org.gradle.workers.max=$workers/" "$PROJECT_ROOT/gradle.properties"
            
            start_time=$(date +%s.%N)
            ./gradlew clean build --parallel --no-daemon > /dev/null 2>&1
            end_time=$(date +%s.%N)
            duration=$(echo "$end_time - $start_time" | bc)
            
            echo "  $workers workers: ${duration}s"
            
            # 恢復設置
            mv "$backup_props" "$PROJECT_ROOT/gradle.properties"
        fi
    done
    echo ""
}

# 生成最佳化建議
generate_optimization_recommendations() {
    echo -e "${BLUE}💡 Optimization Recommendations${NC}"
    echo "--------------------------------"
    
    local recommendations_file="$REPORTS_DIR/optimization_recommendations.txt"
    
    {
        echo "WearWallet Build Optimization Recommendations"
        echo "Generated on: $(date)"
        echo "=============================================="
        echo ""
        
        echo "1. GRADLE CONFIGURATION"
        echo "   - Use JVM heap size: -Xmx${RECOMMENDED_HEAP}m"
        echo "   - Set workers: org.gradle.workers.max=$RECOMMENDED_WORKERS"
        echo "   - Enable file system watching: org.gradle.vfs.watch=true"
        echo ""
        
        echo "2. BUILD CACHE"
        echo "   - Enable build cache: org.gradle.caching=true"
        echo "   - Use remote cache for team builds"
        echo "   - Regular cache cleanup (weekly)"
        echo ""
        
        echo "3. DEPENDENCY OPTIMIZATION"
        echo "   - Review duplicate dependencies"
        echo "   - Use version catalogs (already implemented)"
        echo "   - Enable dependency verification"
        echo ""
        
        echo "4. MODULE OPTIMIZATION"
        echo "   - Split large modules when possible"
        echo "   - Use api() vs implementation() correctly"
        echo "   - Minimize cross-module dependencies"
        echo ""
        
        echo "5. CI/CD OPTIMIZATION"
        echo "   - Use GitHub Actions matrix builds"
        echo "   - Cache Gradle wrapper and dependencies"
        echo "   - Parallel test execution"
        echo ""
        
    } > "$recommendations_file"
    
    echo -e "${GREEN}📝 Recommendations saved to: $recommendations_file${NC}"
    echo ""
}

# 主執行流程
main() {
    cd "$PROJECT_ROOT"
    
    # 初始化結果文件
    echo "Build Type,Duration (seconds),Description" > "$REPORTS_DIR/timing_results.csv"
    
    # 系統資源檢查
    check_system_resources
    
    # 建置效能測試
    echo -e "${BLUE}🏗️  Build Performance Testing${NC}"
    echo "=============================="
    
    # 測試各種建置配置
    measure_build_time "clean_build" "clean build" "Clean Build (No Cache)"
    measure_build_time "incremental_build" "build" "Incremental Build"
    measure_build_time "parallel_build" "clean build --parallel" "Parallel Build"
    measure_build_time "cached_build" "clean build --build-cache" "Cached Build"
    measure_build_time "optimized_build" "clean build --parallel --build-cache --configuration-cache" "Fully Optimized Build"
    
    # 分析結果
    analyze_profile_reports
    analyze_dependency_resolution
    analyze_gradle_cache
    test_parallel_builds
    
    # 生成建議
    generate_optimization_recommendations
    
    echo -e "${GREEN}🎉 Build performance analysis complete!${NC}"
    echo -e "${BLUE}📊 Reports available in: $REPORTS_DIR${NC}"
}

# 執行主程序
main "$@"