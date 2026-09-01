#!/usr/bin/env bash
# Load local environment values, validate them, then run the requested Gradle tasks.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
cd "$PROJECT_ROOT"

if [[ -f .env ]]; then
    echo 'Loading ignored .env values for this build process.'
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

echo 'Step 1: validate environment'
./scripts/validate-build.sh

echo
echo 'Step 2: run Gradle'
./gradlew "$@"

echo
echo 'Build completed.'
