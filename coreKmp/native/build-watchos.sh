#!/bin/bash
set -e

# watchOS libsecp256k1 交叉編譯腳本
# 編譯 watchOS arm64 (真機) 和 arm64 simulator (Apple Silicon 模擬器)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SECP256K1_DIR="${SCRIPT_DIR}/secp256k1"
BUILD_DIR="${SCRIPT_DIR}/build"
OUTPUT_DIR="${SCRIPT_DIR}/lib"

# 清理舊的構建
rm -rf "${BUILD_DIR}"
rm -rf "${OUTPUT_DIR}"
mkdir -p "${BUILD_DIR}"
mkdir -p "${OUTPUT_DIR}"

# 顏色輸出
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}===========================================${NC}"
echo -e "${BLUE}  Building libsecp256k1 for watchOS${NC}"
echo -e "${BLUE}===========================================${NC}"

# 生成 configure 腳本
cd "${SECP256K1_DIR}"
if [ ! -f "configure" ]; then
    echo -e "${GREEN}Running autogen.sh...${NC}"
    ./autogen.sh
fi

# 編譯選項（僅啟用 ECDSA 核心功能）
CONFIGURE_FLAGS="
    --disable-shared
    --enable-static
    --disable-tests
    --disable-benchmark
    --disable-exhaustive-tests
    --enable-module-ecdh=yes
    --enable-module-recovery=yes
    --enable-module-extrakeys=no
    --enable-module-schnorrsig=no
    --enable-module-musig=no
    --enable-module-ellswift=no
"

# 函數：編譯指定架構
build_arch() {
    local ARCH=$1
    local SDK=$2
    local PLATFORM=$3

    echo -e "${GREEN}Building for ${ARCH} (${PLATFORM})...${NC}"

    local ARCH_BUILD_DIR="${BUILD_DIR}/${ARCH}"
    mkdir -p "${ARCH_BUILD_DIR}"

    # 獲取 SDK 路徑
    local SDK_PATH=$(xcrun --sdk ${SDK} --show-sdk-path)
    local MIN_VERSION="7.0"  # watchOS 最低版本

    # 設置編譯器和標誌
    export CC="$(xcrun --sdk ${SDK} --find clang)"
    export CXX="$(xcrun --sdk ${SDK} --find clang++)"
    export CPP="$(xcrun --sdk ${SDK} --find clang) -E"
    export AR="$(xcrun --sdk ${SDK} --find ar)"
    export RANLIB="$(xcrun --sdk ${SDK} --find ranlib)"
    export STRIP="$(xcrun --sdk ${SDK} --find strip)"

    # 根據不同架構和平台設置 CFLAGS
    if [[ "$ARCH" == "arm64" && "$SDK" == "watchos" ]]; then
        # watchOS 真機 arm64
        export CFLAGS="-arch arm64 -isysroot ${SDK_PATH} -mwatchos-version-min=${MIN_VERSION} -fembed-bitcode -O3"
        local HOST_ARCH="aarch64-apple-watchos"
    elif [[ "$ARCH" == "arm64" && "$SDK" == "watchsimulator" ]]; then
        # watchOS 模擬器 arm64 (Apple Silicon Mac)
        export CFLAGS="-arch arm64 -isysroot ${SDK_PATH} -mwatchos-simulator-version-min=${MIN_VERSION} -O3"
        local HOST_ARCH="aarch64-apple-watchos-simulator"
    elif [[ "$ARCH" == "x86_64" && "$SDK" == "watchsimulator" ]]; then
        # watchOS 模擬器 x86_64 (Intel Mac) - 舊版支援
        export CFLAGS="-arch x86_64 -isysroot ${SDK_PATH} -mwatchos-simulator-version-min=${MIN_VERSION} -O3"
        local HOST_ARCH="x86_64-apple-watchos-simulator"
    fi

    export CXXFLAGS="${CFLAGS}"
    export LDFLAGS="-arch ${ARCH} -isysroot ${SDK_PATH}"

    cd "${SECP256K1_DIR}"

    # 清理之前的配置
    make clean 2>/dev/null || true
    make distclean 2>/dev/null || true

    # 配置
    ./configure \
        --host=${HOST_ARCH} \
        --prefix="${ARCH_BUILD_DIR}" \
        ${CONFIGURE_FLAGS}

    # 編譯
    make -j$(sysctl -n hw.ncpu)
    make install

    # 複製靜態庫
    cp "${ARCH_BUILD_DIR}/lib/libsecp256k1.a" "${OUTPUT_DIR}/libsecp256k1_${ARCH}_${PLATFORM}.a"

    echo -e "${GREEN}✅ ${ARCH} (${PLATFORM}) build complete${NC}"
}

# 編譯 watchOS 真機 arm64
build_arch "arm64" "watchos" "device"

# 編譯 watchOS 模擬器 arm64 (Apple Silicon)
build_arch "arm64" "watchsimulator" "simulator"

# 編譯 watchOS 模擬器 x86_64 (Intel - 可選)
# build_arch "x86_64" "watchsimulator" "simulator"

echo -e "${BLUE}===========================================${NC}"
echo -e "${GREEN}✅ All architectures built successfully!${NC}"
echo -e "${BLUE}===========================================${NC}"
echo ""
echo "Static libraries location:"
ls -lh "${OUTPUT_DIR}"/libsecp256k1_*.a

# 複製 header 文件
echo ""
echo -e "${GREEN}Copying header files...${NC}"
mkdir -p "${OUTPUT_DIR}/include"
cp -r "${BUILD_DIR}/arm64/include/"* "${OUTPUT_DIR}/include/"

echo -e "${GREEN}✅ Build complete!${NC}"
echo ""
echo "Output directory: ${OUTPUT_DIR}"
echo "  - libsecp256k1_arm64_device.a (watchOS 真機)"
echo "  - libsecp256k1_arm64_simulator.a (watchOS 模擬器 Apple Silicon)"
echo "  - include/ (header 文件)"
