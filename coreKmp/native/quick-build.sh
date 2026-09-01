#!/bin/bash
set -e

cd /Users/iml1s/Documents/WearWallet/coreKmp/native/secp256k1

SDK_PATH=$(xcrun --sdk watchos --show-sdk-path)

export CC="$(xcrun --sdk watchos --find clang)"
export CFLAGS="-arch arm64 -isysroot ${SDK_PATH} -mwatchos-version-min=7.0 -fembed-bitcode -O3"

./configure \
    --disable-shared \
    --enable-static \
    --disable-tests \
    --disable-benchmark \
    --host=aarch64-apple-watchos \
    --prefix=/Users/iml1s/Documents/WearWallet/coreKmp/native/build/arm64

make -j8
make install

echo "✅ Build complete!"
ls -lh /Users/iml1s/Documents/WearWallet/coreKmp/native/build/arm64/lib/
