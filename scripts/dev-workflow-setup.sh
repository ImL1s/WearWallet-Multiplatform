#!/bin/bash

# WearWallet Development Workflow Optimization Setup
# 配置開發環境以實現最佳建置效能和開發體驗 (2025 最佳實踐)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🛠️  WearWallet Development Workflow Setup${NC}"
echo "==========================================="

# 檢查先決條件
check_prerequisites() {
    echo -e "${BLUE}📋 Checking Prerequisites${NC}"
    echo "-------------------------"
    
    # 檢查 Java 版本
    if command -v java > /dev/null 2>&1; then
        java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [[ "$java_version" -ge 17 ]]; then
            echo -e "${GREEN}✅ Java $java_version installed${NC}"
        else
            echo -e "${RED}❌ Java 17+ required (found Java $java_version)${NC}"
            exit 1
        fi
    else
        echo -e "${RED}❌ Java not found${NC}"
        exit 1
    fi
    
    # 檢查 Gradle
    if [[ -f "$PROJECT_ROOT/gradlew" ]]; then
        echo -e "${GREEN}✅ Gradle Wrapper found${NC}"
    else
        echo -e "${RED}❌ Gradle Wrapper not found${NC}"
        exit 1
    fi
    
    # 檢查 Android SDK
    if [[ -n "$ANDROID_HOME" ]] || [[ -n "$ANDROID_SDK_ROOT" ]]; then
        echo -e "${GREEN}✅ Android SDK configured${NC}"
    else
        echo -e "${YELLOW}⚠️  Android SDK path not set${NC}"
        echo "  Please set ANDROID_HOME or ANDROID_SDK_ROOT"
    fi
    
    echo ""
}

# 優化 Gradle 設置
optimize_gradle_settings() {
    echo -e "${BLUE}⚙️  Optimizing Gradle Settings${NC}"
    echo "------------------------------"
    
    # 檢查系統資源
    if [[ "$OSTYPE" == "darwin"* ]]; then
        CPU_COUNT=$(sysctl -n hw.ncpu)
        MEMORY_GB=$(echo "$(sysctl -n hw.memsize) / 1024 / 1024 / 1024" | bc)
    else
        CPU_COUNT=$(nproc)
        MEMORY_GB=$(free -g | awk '/^Mem:/{print $2}')
    fi
    
    # 計算最佳設置
    HEAP_SIZE=$((MEMORY_GB * 1024 / 4))  # 25% of total memory, minimum 2GB
    [[ $HEAP_SIZE -lt 2048 ]] && HEAP_SIZE=2048
    [[ $HEAP_SIZE -gt 8192 ]] && HEAP_SIZE=8192
    
    WORKERS=$((CPU_COUNT > 8 ? 8 : CPU_COUNT))
    [[ $WORKERS -lt 2 ]] && WORKERS=2
    
    echo "Detected: ${CPU_COUNT} CPU cores, ${MEMORY_GB}GB RAM"
    echo "Recommended: ${HEAP_SIZE}MB heap, ${WORKERS} workers"
    
    # 檢查是否需要更新 gradle.properties
    gradle_props="$PROJECT_ROOT/gradle.properties"
    
    if [[ -f "$gradle_props" ]]; then
        # 創建備份
        cp "$gradle_props" "$gradle_props.backup.$(date +%Y%m%d_%H%M%S)"
        echo -e "${GREEN}📄 Created backup of gradle.properties${NC}"
    else
        echo -e "${YELLOW}⚠️  gradle.properties not found, creating new one${NC}"
    fi
    
    echo ""
}

# 設置 Git hooks
setup_git_hooks() {
    echo -e "${BLUE}🔗 Setting Up Git Hooks${NC}"
    echo "----------------------"
    
    hooks_dir="$PROJECT_ROOT/.git/hooks"
    
    # Pre-commit hook for code quality
    cat > "$hooks_dir/pre-commit" << 'EOF'
#!/bin/bash
# WearWallet Pre-commit Hook
# 在提交前執行程式碼品質檢查

set -e

echo "🔍 Running pre-commit checks..."

# Kotlin code formatting check
if ! ./gradlew ktlintCheck --no-daemon --quiet; then
    echo "❌ Kotlin formatting issues found. Run './gradlew ktlintFormat' to fix."
    exit 1
fi

# Run Detekt
if ! ./gradlew detekt --no-daemon --quiet; then
    echo "❌ Detekt found issues. Please fix before committing."
    exit 1
fi

# Check for TODO/FIXME in staged files
if git diff --cached --name-only | xargs grep -l "TODO\|FIXME" 2>/dev/null; then
    echo "⚠️  Found TODO/FIXME in staged files. Consider completing them."
fi

echo "✅ Pre-commit checks passed!"
EOF

    chmod +x "$hooks_dir/pre-commit"
    echo -e "${GREEN}✅ Pre-commit hook installed${NC}"
    
    # Pre-push hook for testing
    cat > "$hooks_dir/pre-push" << 'EOF'
#!/bin/bash
# WearWallet Pre-push Hook
# 在推送前執行測試

set -e

echo "🧪 Running pre-push tests..."

# Run unit tests
if ! ./gradlew testDebugUnitTest --no-daemon --quiet; then
    echo "❌ Unit tests failed. Please fix before pushing."
    exit 1
fi

echo "✅ Pre-push tests passed!"
EOF

    chmod +x "$hooks_dir/pre-push"
    echo -e "${GREEN}✅ Pre-push hook installed${NC}"
    
    echo ""
}

# 配置 IDE 整合
setup_ide_integration() {
    echo -e "${BLUE}💻 Setting Up IDE Integration${NC}"
    echo "-----------------------------"
    
    # Android Studio 設置
    idea_dir="$PROJECT_ROOT/.idea"
    
    if [[ -d "$idea_dir" ]]; then
        # Gradle 設置
        mkdir -p "$idea_dir"
        
        cat > "$idea_dir/gradle.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="testRunner" value="GRADLE" />
        <option name="distributionType" value="DEFAULT_WRAPPED" />
        <option name="externalProjectPath" value="$PROJECT_DIR$" />
        <option name="gradleJvm" value="17" />
        <option name="modules">
          <set>
            <option value="$PROJECT_DIR$" />
            <option value="$PROJECT_DIR$/wear" />
            <option value="$PROJECT_DIR$/mobile" />
            <option value="$PROJECT_DIR$/shared" />
            <option value="$PROJECT_DIR$/sharedKmp" />
          </set>
        </option>
      </GradleProjectSettings>
    </option>
  </component>
</project>
EOF
        
        echo -e "${GREEN}✅ Android Studio Gradle settings configured${NC}"
        
        # 編譯器設置
        cat > "$idea_dir/compiler.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <option name="BUILD_PROCESS_HEAP_SIZE" value="4096" />
    <option name="BUILD_PROCESS_ADDITIONAL_VM_OPTIONS" value="-XX:+UseG1GC -XX:MaxGCPauseMillis=200" />
    <option name="PARALLEL_COMPILATION" value="true" />
    <option name="AUTO_SHOW_ERRORS_IN_EDITOR" value="false" />
  </component>
</project>
EOF
        
        echo -e "${GREEN}✅ Android Studio compiler settings configured${NC}"
    fi
    
    echo ""
}

# 創建開發腳本
create_dev_scripts() {
    echo -e "${BLUE}📝 Creating Development Scripts${NC}"
    echo "------------------------------"
    
    scripts_dir="$PROJECT_ROOT/scripts"
    
    # 快速建置腳本
    cat > "$scripts_dir/quick-build.sh" << 'EOF'
#!/bin/bash
# WearWallet Quick Build Script
# 快速建置和測試腳本，適用於日常開發

set -e

echo "🚀 WearWallet Quick Build"
echo "========================"

# 選擇建置類型
case "${1:-debug}" in
    "debug")
        echo "Building debug variant..."
        ./gradlew assembleDebug --parallel --build-cache --configuration-cache
        ;;
    "release")
        echo "Building release variant..."
        ./gradlew assembleRelease --parallel --build-cache --configuration-cache
        ;;
    "test")
        echo "Running tests..."
        ./gradlew testDebugUnitTest --parallel --build-cache
        ;;
    "clean")
        echo "Clean building..."
        ./gradlew clean assembleDebug --parallel --build-cache
        ;;
    *)
        echo "Usage: $0 [debug|release|test|clean]"
        exit 1
        ;;
esac

echo "✅ Build completed successfully!"
EOF

    chmod +x "$scripts_dir/quick-build.sh"
    echo -e "${GREEN}✅ Quick build script created${NC}"
    
    # 依賴更新腳本
    cat > "$scripts_dir/update-dependencies.sh" << 'EOF'
#!/bin/bash
# WearWallet Dependency Update Script
# 檢查和更新項目依賴

set -e

echo "📦 WearWallet Dependency Update"
echo "==============================="

# 檢查過期依賴
echo "Checking for outdated dependencies..."
./gradlew dependencyUpdates --no-daemon

# 生成依賴報告
echo "Generating dependency report..."
./gradlew dependencies > dependencies_report.txt

echo "✅ Dependency check completed!"
echo "📄 Report saved to: dependencies_report.txt"
EOF

    chmod +x "$scripts_dir/update-dependencies.sh"
    echo -e "${GREEN}✅ Dependency update script created${NC}"
    
    echo ""
}

# 設置快取優化
setup_cache_optimization() {
    echo -e "${BLUE}💾 Setting Up Cache Optimization${NC}"
    echo "--------------------------------"
    
    # 創建快取清理腳本
    cat > "$PROJECT_ROOT/scripts/cache-cleanup.sh" << 'EOF'
#!/bin/bash
# WearWallet Cache Cleanup Script
# 清理和優化建置快取

set -e

echo "🧹 WearWallet Cache Cleanup"
echo "============================"

# Gradle 快取清理
echo "Cleaning Gradle caches..."
rm -rf ~/.gradle/caches/build-cache-*
rm -rf ~/.gradle/caches/transforms-*
rm -rf ~/.gradle/caches/journal-*

# Android 建置快取清理
echo "Cleaning Android build caches..."
rm -rf ~/.android/build-cache

# 項目本地快取清理
echo "Cleaning project caches..."
./gradlew clean

# Kotlin 編譯快取清理
echo "Cleaning Kotlin compilation caches..."
find . -name ".kotlin" -type d -exec rm -rf {} + 2>/dev/null || true

echo "✅ Cache cleanup completed!"
echo "💡 Next build will repopulate caches"
EOF

    chmod +x "$PROJECT_ROOT/scripts/cache-cleanup.sh"
    echo -e "${GREEN}✅ Cache cleanup script created${NC}"
    
    echo ""
}

# 配置測試優化
setup_test_optimization() {
    echo -e "${BLUE}🧪 Setting Up Test Optimization${NC}"
    echo "-------------------------------"
    
    # 測試配置
    cat > "$PROJECT_ROOT/scripts/run-tests.sh" << 'EOF'
#!/bin/bash
# WearWallet Test Runner Script
# 優化的測試執行腳本

set -e

echo "🧪 WearWallet Test Runner"
echo "========================="

# 測試類型選擇
case "${1:-unit}" in
    "unit")
        echo "Running unit tests..."
        ./gradlew testDebugUnitTest --parallel --build-cache --continue
        ;;
    "integration")
        echo "Running integration tests..."
        ./gradlew connectedDebugAndroidTest --parallel --build-cache
        ;;
    "all")
        echo "Running all tests..."
        ./gradlew test connectedDebugAndroidTest --parallel --build-cache --continue
        ;;
    "coverage")
        echo "Running tests with coverage..."
        ./gradlew testDebugUnitTest koverXmlReport --parallel --build-cache
        ;;
    *)
        echo "Usage: $0 [unit|integration|all|coverage]"
        exit 1
        ;;
esac

echo "✅ Tests completed!"
EOF

    chmod +x "$PROJECT_ROOT/scripts/run-tests.sh"
    echo -e "${GREEN}✅ Test runner script created${NC}"
    
    echo ""
}

# 生成開發文檔
generate_dev_documentation() {
    echo -e "${BLUE}📚 Generating Development Documentation${NC}"
    echo "---------------------------------------"
    
    cat > "$PROJECT_ROOT/DEVELOPMENT_OPTIMIZATION.md" << 'EOF'
# WearWallet Development Optimization Guide

This guide provides optimized workflows and best practices for developing WearWallet efficiently.

## Quick Start Commands

### Building
```bash
# Debug build (fastest)
./scripts/quick-build.sh debug

# Release build
./scripts/quick-build.sh release

# Clean build
./scripts/quick-build.sh clean
```

### Testing
```bash
# Unit tests only
./scripts/run-tests.sh unit

# All tests
./scripts/run-tests.sh all

# With coverage
./scripts/run-tests.sh coverage
```

### Maintenance
```bash
# Update dependencies
./scripts/update-dependencies.sh

# Clean caches
./scripts/cache-cleanup.sh

# Performance analysis
./scripts/build-performance-check.sh
```

## Development Best Practices

### 1. Incremental Development
- Use `./gradlew build` for incremental builds
- Avoid `clean` unless necessary
- Enable build cache and configuration cache

### 2. Module Development
- Work on single modules when possible
- Use `./gradlew :module:task` for module-specific tasks
- Leverage parallel builds

### 3. Testing Strategy
- Run unit tests frequently during development
- Use integration tests before major commits
- Generate coverage reports weekly

### 4. IDE Configuration
- Use Android Studio with optimized compiler settings
- Enable parallel compilation
- Configure appropriate heap sizes

## Performance Tips

### Build Performance
- Enable Gradle daemon
- Use build cache
- Configure appropriate worker count
- Monitor build performance regularly

### Development Performance
- Use incremental compilation
- Minimize clean builds
- Optimize dependency resolution
- Regular cache maintenance

## Troubleshooting

### Slow Builds
1. Check system resources (CPU, RAM)
2. Verify Gradle settings
3. Clean caches if necessary
4. Update dependencies

### Memory Issues
1. Increase JVM heap size
2. Reduce parallel workers
3. Close unnecessary applications
4. Check for memory leaks

## Monitoring

### Build Performance
- Run `./scripts/build-performance-check.sh` weekly
- Monitor build times and cache hit rates
- Review dependency resolution times

### Code Quality
- Pre-commit hooks ensure code quality
- Regular Detekt and lint checks
- Code coverage monitoring

## Support

For build optimization issues:
1. Check the performance analysis reports
2. Review Gradle build scans
3. Consult the troubleshooting guide
4. Update optimization configurations
EOF

    echo -e "${GREEN}✅ Development optimization documentation created${NC}"
    echo ""
}

# 主執行函數
main() {
    cd "$PROJECT_ROOT"
    
    echo "Starting development workflow optimization setup..."
    echo ""
    
    check_prerequisites
    optimize_gradle_settings
    setup_git_hooks
    setup_ide_integration
    create_dev_scripts
    setup_cache_optimization
    setup_test_optimization
    generate_dev_documentation
    
    echo -e "${GREEN}🎉 Development workflow setup completed!${NC}"
    echo ""
    echo -e "${BLUE}📋 Next Steps:${NC}"
    echo "1. Review and commit the new configuration files"
    echo "2. Restart your IDE to apply new settings"
    echo "3. Run './scripts/build-performance-check.sh' to baseline performance"
    echo "4. Read DEVELOPMENT_OPTIMIZATION.md for usage guidelines"
    echo ""
    echo -e "${BLUE}💡 Quick Commands:${NC}"
    echo "  ./scripts/quick-build.sh debug    # Fast debug build"
    echo "  ./scripts/run-tests.sh unit       # Run unit tests"
    echo "  ./scripts/cache-cleanup.sh        # Clean caches"
    echo ""
}

# 執行主程序
main "$@"