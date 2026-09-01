#!/bin/bash

# Script to copy KMP framework to watchOS project

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
KMP_BUILD_DIR="$PROJECT_ROOT/sharedKmp/build"
WATCHOS_FRAMEWORKS_DIR="$SCRIPT_DIR/Frameworks"

# Create Frameworks directory if it doesn't exist
mkdir -p "$WATCHOS_FRAMEWORKS_DIR"

# Copy the fat framework
echo "Copying WearWalletShared.framework..."
cp -R "$KMP_BUILD_DIR/fat-framework/debug/WearWalletShared.framework" "$WATCHOS_FRAMEWORKS_DIR/"

echo "Framework copied successfully!"
echo "Location: $WATCHOS_FRAMEWORKS_DIR/WearWalletShared.framework"