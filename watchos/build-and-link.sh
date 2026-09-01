#!/bin/bash

echo "🔨 Building KMP shared framework for watchOS..."

# Build the KMP framework
cd ..
./gradlew :sharedKmp:linkDebugFrameworkWatchosSimulatorArm64

# Check if build was successful
if [ $? -ne 0 ]; then
    echo "❌ Failed to build KMP framework"
    exit 1
fi

# Create symlink to the framework
cd watchos
FRAMEWORK_PATH="../sharedKmp/build/bin/watchosSimulatorArm64/debugFramework/WearWalletShared.framework"

if [ -L "WearWalletShared.framework" ]; then
    rm WearWalletShared.framework
fi

ln -s "$FRAMEWORK_PATH" WearWalletShared.framework

echo "✅ Framework linked successfully!"
echo "📱 You can now open the watchOS project in Xcode"
echo ""
echo "To create an Xcode project:"
echo "1. Open Xcode"
echo "2. Create a new watchOS app"
echo "3. Add the WearWalletShared.framework to your project"
echo "4. Import WearWalletShared in your Swift files"