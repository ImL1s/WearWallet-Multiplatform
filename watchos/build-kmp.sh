#!/bin/bash

# Build KMP and copy framework for watchOS

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

echo "Building coreKmp framework for watchOS..."
cd "$PROJECT_ROOT"

# Build coreKmp framework for simulator (arm64)
echo "Building coreKmp framework..."
./gradlew :coreKmp:linkDebugFrameworkWatchosSimulatorArm64

# Copy the framework
WATCHOS_FRAMEWORKS_DIR="$SCRIPT_DIR/Frameworks"
mkdir -p "$WATCHOS_FRAMEWORKS_DIR"

echo "Copying simulator framework to watchOS project..."
cp -R "$PROJECT_ROOT/coreKmp/build/bin/watchosSimulatorArm64/debugFramework/coreKmp.framework" "$WATCHOS_FRAMEWORKS_DIR/"

# Remove old WearWalletShared.framework if it exists
if [ -d "$WATCHOS_FRAMEWORKS_DIR/WearWalletShared.framework" ]; then
    echo "Removing old WearWalletShared.framework..."
    rm -rf "$WATCHOS_FRAMEWORKS_DIR/WearWalletShared.framework"
fi

echo "Done! Framework is at: $WATCHOS_FRAMEWORKS_DIR/coreKmp.framework"

# CocoaPods output is gitignored; regenerate before Xcode/archive jobs.
if [[ -f "$SCRIPT_DIR/Podfile" ]]; then
  if ! command -v pod >/dev/null 2>&1; then
    echo "ERROR: CocoaPods (pod) is required after untracking watchos/Pods."
    echo "Install with: sudo gem install cocoapods  (or brew install cocoapods)"
    exit 1
  fi
  # coreKmp.podspec expects build/cocoapods/framework/coreKmp.framework
  echo "Generating CocoaPods dummy framework for coreKmp.podspec..."
  ./gradlew :coreKmp:generateDummyFramework
  echo "Running pod install in watchos/..."
  (
    cd "$SCRIPT_DIR"
    pod install --repo-update
  )
  echo "OK: watchos/Pods regenerated."
fi