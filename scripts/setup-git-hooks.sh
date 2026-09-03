#!/bin/bash

# Setup Git Hooks Script
# This script configures Git to use the custom hooks in .githooks directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}     Git Hooks Setup Script            ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if we're in a git repository
if [ ! -d "$PROJECT_ROOT/.git" ]; then
    echo -e "${RED}Error: Not in a git repository${NC}"
    exit 1
fi

# Create .githooks directory if it doesn't exist
if [ ! -d "$PROJECT_ROOT/.githooks" ]; then
    echo -e "${YELLOW}Creating .githooks directory...${NC}"
    mkdir -p "$PROJECT_ROOT/.githooks"
fi

# Make pre-commit hook executable
if [ -f "$PROJECT_ROOT/.githooks/pre-commit" ]; then
    chmod +x "$PROJECT_ROOT/.githooks/pre-commit"
    echo -e "${GREEN}✅ Pre-commit hook made executable${NC}"
else
    echo -e "${YELLOW}⚠️  Pre-commit hook not found${NC}"
fi

# Configure Git to use the .githooks directory
echo -e "${BLUE}Configuring Git to use .githooks directory...${NC}"
cd "$PROJECT_ROOT"
git config core.hooksPath .githooks

# Verify configuration
HOOKS_PATH=$(git config core.hooksPath)
if [ "$HOOKS_PATH" = ".githooks" ]; then
    echo -e "${GREEN}✅ Git hooks path configured successfully${NC}"
else
    echo -e "${RED}❌ Failed to configure Git hooks path${NC}"
    exit 1
fi

# Optional: Install additional hooks
echo ""
echo -e "${BLUE}Available hooks:${NC}"
echo "  - pre-commit: Runs tests before allowing commits"

# Create a sample commit-msg hook for conventional commits
cat > "$PROJECT_ROOT/.githooks/commit-msg" << 'EOF'
#!/bin/bash

# Conventional Commits Message Validator

COMMIT_MSG_FILE=$1
COMMIT_MSG=$(cat "$COMMIT_MSG_FILE")

# Regex for conventional commit format
PATTERN="^(feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert)(\(.+\))?: .{1,100}"

if ! echo "$COMMIT_MSG" | grep -qE "$PATTERN"; then
    echo "❌ Invalid commit message format!"
    echo ""
    echo "Commit message must follow Conventional Commits format:"
    echo "  <type>(<scope>): <subject>"
    echo ""
    echo "Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert"
    echo ""
    echo "Example:"
    echo "  feat(wallet): add hardware wallet support"
    echo "  fix(ui): resolve button alignment issue"
    echo ""
    exit 1
fi

# Check message length
MSG_LENGTH=${#COMMIT_MSG}
if [ $MSG_LENGTH -gt 100 ]; then
    echo "⚠️  Warning: Commit message is longer than 100 characters ($MSG_LENGTH)"
fi

exit 0
EOF

chmod +x "$PROJECT_ROOT/.githooks/commit-msg"
echo -e "${GREEN}✅ Commit message validator installed${NC}"

# Create prepare-commit-msg hook for adding issue numbers
cat > "$PROJECT_ROOT/.githooks/prepare-commit-msg" << 'EOF'
#!/bin/bash

# Automatically add issue number to commit message if branch contains it

COMMIT_MSG_FILE=$1
COMMIT_SOURCE=$2
SHA1=$3

# Only add issue number for new commits (not amends or merges)
if [ -z "$COMMIT_SOURCE" ]; then
    # Extract issue number from branch name (e.g., feature/WEAR-123-description)
    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    ISSUE=$(echo "$BRANCH" | grep -oE 'WEAR-[0-9]+' || true)
    
    if [ -n "$ISSUE" ]; then
        # Check if issue number is already in the message
        if ! grep -q "$ISSUE" "$COMMIT_MSG_FILE"; then
            # Append issue number to the commit message
            echo "" >> "$COMMIT_MSG_FILE"
            echo "Issue: $ISSUE" >> "$COMMIT_MSG_FILE"
        fi
    fi
fi
EOF

chmod +x "$PROJECT_ROOT/.githooks/prepare-commit-msg"
echo -e "${GREEN}✅ Issue number auto-appender installed${NC}"

# Summary
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✅ Git hooks setup complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${BLUE}Installed hooks:${NC}"
echo "  • pre-commit: Runs critical tests before commits"
echo "  • commit-msg: Validates conventional commit format"
echo "  • prepare-commit-msg: Auto-adds issue numbers"
echo ""
echo -e "${YELLOW}To skip hooks temporarily:${NC}"
echo "  • Skip tests: SKIP_TESTS=true git commit ..."
echo "  • Skip all hooks: git commit --no-verify ..."
echo ""
echo -e "${YELLOW}To disable hooks:${NC}"
echo "  git config --unset core.hooksPath"
echo ""
echo -e "${GREEN}Happy coding! 🚀${NC}"