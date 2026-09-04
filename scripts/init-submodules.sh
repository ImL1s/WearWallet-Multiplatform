#!/bin/bash
# Initialize WearWallet git submodules required for Gradle builds.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ ! -f .gitmodules ]]; then
  echo "ERROR: .gitmodules not found. Run this script from the WearWallet repository root."
  exit 1
fi

echo "Initializing WearWallet submodules..."
git submodule sync --recursive
git submodule update --init --recursive

missing=0
while IFS= read -r line; do
  [[ -n "$line" ]] || continue
  status_char="${line:0:1}"
  # git submodule status columns: [ -/+ /U / ]SHA path [description]
  path="$(awk '{print $2}' <<<"$line")"
  [[ -n "$path" ]] || continue

  if [[ "$status_char" == "-" ]]; then
    echo "ERROR: submodule not initialized: $path"
    missing=1
    continue
  fi

  if [[ ! -d "$path" ]]; then
    echo "ERROR: submodule path missing: $path"
    missing=1
    continue
  fi

  # Reject checkouts that only contain the .git pointer/dir (init without update).
  # Use -quit so find exits 0 itself (avoid SIGPIPE under pipefail from `head`).
  if ! find "$path" -mindepth 1 ! -name '.git' -print -quit 2>/dev/null | grep -q .; then
    echo "ERROR: submodule checkout appears empty: $path"
    missing=1
  fi
done < <(git submodule status --recursive)

if [[ "$missing" -ne 0 ]]; then
  echo "Submodule initialization failed. Try: git submodule update --init --recursive --force"
  exit 1
fi

echo "OK: all submodules initialized."
