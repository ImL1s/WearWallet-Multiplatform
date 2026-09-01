#!/bin/bash
set -e

# Path to project.yml
cd "$(dirname "$0")"
PROJECT_SPEC="project.yml"

# Check if xcodegen is installed
if ! command -v xcodegen &> /dev/null; then
    echo "🛠️ xcodegen not found. Attempting to install via Homebrew..."
    if command -v brew &> /dev/null; then
        brew install xcodegen
    else
        echo "❌ Error: Homebrew is not installed. Please install Homebrew or xcodegen manually."
        echo "To install Homebrew: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        exit 1
    fi
fi

# Generate the project
echo "🚀 Generating Xcode project from $PROJECT_SPEC..."
xcodegen generate --spec "$PROJECT_SPEC"

echo "✅ Project generation complete! Open 'WearWalletCompanion.xcodeproj' in Xcode."
