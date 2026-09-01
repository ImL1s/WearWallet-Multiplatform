#!/usr/bin/env bash
# Sanitize-export the private WearWallet tree into a public snapshot repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPORT_DIR="${EXPORT_DIR:-/tmp/wearwallet-public-export}"
BLACKLIST="$ROOT/scripts/public-export/blacklist.txt"
OVERLAY="$ROOT/scripts/public-export/overlay"
PUBLIC_REPO="${PUBLIC_REPO:-ImL1s/WearWallet-public}"
DRY_RUN=0
DO_PUSH=0
SOURCE_SHA=""

usage() {
  cat <<'EOF'
Usage: scripts/export-public.sh [--dry-run] [--push] [--repo OWNER/NAME] [--out DIR]

Builds a sanitized tree from committed HEAD, runs secret/infra guards,
optionally force-syncs the public snapshot repo as a single orphan tip.

Commit any changes under scripts/public-export/ before exporting.
Uncommitted worktree edits outside that path are ignored (HEAD only).
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --push) DO_PUSH=1; shift ;;
    --repo) PUBLIC_REPO="$2"; shift 2 ;;
    --out) EXPORT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

fail() { echo "GUARD FAIL: $*" >&2; exit 1; }

cd "$ROOT"

if ! git diff --quiet -- scripts/public-export scripts/export-public.sh; then
  echo "ERROR: uncommitted changes under scripts/public-export or export-public.sh." >&2
  echo "Commit them before exporting so provenance matches the rules applied." >&2
  exit 2
fi

SOURCE_SHA="$(git rev-parse HEAD)"
SOURCE_SHORT="$(git rev-parse --short=7 HEAD)"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "WARN: worktree is dirty. Export uses committed HEAD ($SOURCE_SHORT) only." >&2
  echo "WARN: uncommitted secret removals are NOT applied — commit scrubbing first." >&2
fi

echo "==> Export source: $BRANCH @ $SOURCE_SHA"
rm -rf "$EXPORT_DIR"
mkdir -p "$EXPORT_DIR"

echo "==> git archive → $EXPORT_DIR"
git archive --format=tar HEAD | tar -x -C "$EXPORT_DIR"

if [[ -f .gitmodules ]]; then
  echo "==> packing submodules"
  while IFS= read -r line; do
    sha="$(echo "$line" | awk '{print $1}' | sed 's/^[-+U]//')"
    path="$(echo "$line" | awk '{print $2}')"
    [[ -z "$path" ]] && continue
    if ! git -C "$ROOT/$path" rev-parse --git-dir >/dev/null 2>&1; then
      echo "WARN: skipping uninitialized submodule $path" >&2
      continue
    fi
    echo "    submodule $path @ ${sha:0:7}"
    rm -rf "${EXPORT_DIR:?}/$path"
    mkdir -p "$EXPORT_DIR/$path"
    if ! git -C "$ROOT/$path" archive --format=tar "$sha" 2>/dev/null | tar -x -C "$EXPORT_DIR/$path"; then
      fail "could not archive submodule $path @ $sha"
    fi
  done < <(git submodule status --recursive)
fi

apply_blacklist() {
  local line pattern base
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"
    line="$(echo "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    [[ -z "$line" ]] && continue
    # Wildcard patterns first (including **/dir/ forms). bash 3.2 has no globstar.
    if [[ "$line" == *'*'* ]]; then
      pattern="${line%/}"
      (
        cd "$EXPORT_DIR"
        find . -path "./$pattern" -prune -exec rm -rf {} + 2>/dev/null || true
        base="${pattern##*/}"
        if [[ "$base" == *'*'* ]]; then
          find . -name "$base" -exec rm -rf {} + 2>/dev/null || true
        fi
      )
    elif [[ "$line" == */ ]]; then
      rm -rf "${EXPORT_DIR:?}/${line%/}"
    else
      rm -rf "${EXPORT_DIR:?}/$line"
    fi
  done < "$BLACKLIST"
}

echo "==> applying blacklist"
apply_blacklist

echo "==> blacklist backstop"
(
  cd "$EXPORT_DIR"
  RESIDUAL="$(find . \( \
    -path '*/xcuserdata/*' -o -name '*.xcuserstate' -o \
    -path '*/.claude/*' -o -path '*/.serena/*' -o -path '*/.kiro/*' -o \
    -name 'google-services.json' -o -name '*.so' -o \
    -name 'deploy_to_play_console.py' -o -name 'load-credentials.sh' \
  \) 2>/dev/null || true)"
  if [[ -n "$RESIDUAL" ]]; then
    echo "$RESIDUAL" | head -40 >&2
    fail "blacklist backstop hit residual sensitive/noisy paths"
  fi
)

echo "==> applying overlay"
if [[ -d "$OVERLAY" ]]; then
  (cd "$OVERLAY" && tar -cf - .) | (cd "$EXPORT_DIR" && tar -xf -)
fi

# Soft-scrub remaining markdown that still teaches private ops (paths kept for build docs).
echo "==> scrubbing private-ops phrasing from docs"
(
  cd "$EXPORT_DIR"
  # Split so this script does not self-match vault item strings.
  A="WearWallet/API"
  B="_Keys"
  find . -type f \( -name '*.md' -o -name '*.zh-TW.md' \) -print0 2>/dev/null \
    | xargs -0 sed -i.bak \
      -e "s|${A}${B}|a local secrets manager (never commit)|g" \
      -e 's|./scripts/setup.sh|docs/PUBLIC_BUILD.md|g' \
      -e 's|scripts/setup.sh|docs/PUBLIC_BUILD.md|g' \
      -e 's|scripts/load-credentials.sh|(removed from public snapshot)|g' \
      -e 's|docs/ONEPASSWORD_SETUP.md|docs/PUBLIC_BUILD.md|g' \
      -e 's|docs/GITHUB_TOKEN_SETUP.md|docs/PUBLIC_BUILD.md|g' \
      2>/dev/null || true
  find . -name '*.bak' -delete 2>/dev/null || true
)

BANNER_FILE="$EXPORT_DIR/README.md"
if [[ -f "$BANNER_FILE" ]]; then
  sed -i.bak \
    -e 's|https://github.com/ImL1s/WearWallet.git|https://github.com/ImL1s/WearWallet-public.git|g' \
    -e 's|github.com/ImL1s/WearWallet)|github.com/ImL1s/WearWallet-public)|g' \
    -e 's|Automated unit, build, security, and cross-platform CI workflows|Public Ubuntu unit-test CI for coreKmp/wear (no Apple matrix on this snapshot)|g' \
    "$BANNER_FILE" && rm -f "${BANNER_FILE}.bak"
  if ! grep -q 'sanitized public snapshot' "$BANNER_FILE"; then
    tmp="$(mktemp)"
    cat >"$tmp" <<EOF
> [!IMPORTANT]
> This is a **sanitized public snapshot** of WearWallet. Canonical development
> and full git history live in a private repository. Do **not** use with real
> funds. See [\`docs/PUBLIC_BUILD.md\`](./docs/PUBLIC_BUILD.md) and
> [\`docs/PUBLIC_SNAPSHOT.md\`](./docs/PUBLIC_SNAPSHOT.md).
>
> Private source tip at export: \`$SOURCE_SHORT\`

EOF
    cat "$BANNER_FILE" >>"$tmp"
    mv "$tmp" "$BANNER_FILE"
  fi
fi

mkdir -p "$EXPORT_DIR/docs"
cat >"$EXPORT_DIR/docs/PUBLIC_SNAPSHOT.md" <<EOF
# Public snapshot provenance

- Exported from private tip: \`$SOURCE_SHA\`
- Export UTC time: \`$(date -u +%Y-%m-%dT%H:%M:%SZ)\`
- Method: \`scripts/export-public.sh\` (blacklist + overlay + guards)

This repository intentionally has **no private development history**.
Consumers should not run the export script; it is maintainer tooling.
EOF

if [[ -f "$EXPORT_DIR/.github/SECURITY.md" && ! -f "$EXPORT_DIR/SECURITY.md" ]]; then
  cp "$EXPORT_DIR/.github/SECURITY.md" "$EXPORT_DIR/SECURITY.md"
fi

echo "==> running publish guards"
cd "$EXPORT_DIR"

[[ ! -f wear/google-services.json && ! -f mobile/google-services.json ]] \
  || fail "production google-services.json present (only *.example allowed)"
[[ -f wear/google-services.json.example ]] || fail "missing wear/google-services.json.example"
[[ -f mobile/google-services.json.example ]] || fail "missing mobile/google-services.json.example"
[[ ! -f .gitmodules ]] || fail ".gitmodules must not ship (modules are flattened)"
[[ ! -f renovate.json && ! -f .github/dependabot.yml ]] \
  || fail "renovate/dependabot configs must not ship on public tip"
[[ ! -d .claude && ! -d .serena && ! -d .kiro ]] || fail "agent scratch dirs must not ship"

if find . -type f \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.pem' -o -name '*.so' \) \
  ! -path './modules/*/LICENSE*' | grep -q .; then
  fail "keystore/pem/native .so artifacts found in export tree"
fi
if grep -R --binary-files=without-match -n \
  -e "BEGIN RSA PRIVATE KEY" -e "BEGIN OPENSSH PRIVATE KEY" -e "BEGIN EC PRIVATE KEY" -e "BEGIN PRIVATE KEY" \
  . --exclude-dir=.git --exclude-dir=scripts --exclude='*.md' 2>/dev/null | grep -q .; then
  fail "PEM private key block found in export tree"
fi

if grep -R -nE 'runs-on:.*self-hosted|runs-on:[[:space:]]*\[[^]]*self-hosted|ww-ci[0-9]|ENABLE_HOSTED_SIDE_CHECKS|pull_request_target' \
  .github/workflows 2>/dev/null | grep -v 'Never register self-hosted' | grep -q .; then
  fail "unsafe CI markers found under .github/workflows"
fi
# Allow overlay CI + release workflows only (no *.example)
wf_count="$(find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) ! -name '*.example' | wc -l | tr -d ' ')"
[[ "$wf_count" -eq 2 ]] || fail "expected exactly 2 active workflows (ci+release); found $wf_count"
for required in .github/workflows/ci.yml .github/workflows/release.yml; do
  [[ -f "$required" ]] || fail "missing required workflow: $required"
done

for p in scripts/deploy_to_play_console.py scripts/load-credentials.sh scripts/check-1password-vaults.sh set_clipboard.js; do
  [[ ! -e "$p" ]] || fail "private ops path still present: $p"
done

OP_MARK="op:/"
OP_MARK="${OP_MARK}/"
SKVIP_MARK="skvip"".org"
VAULT_ITEM="WearWallet/API""_Keys"
if grep -R --binary-files=without-match -n -e "$OP_MARK" -e "1Password Connect" -e "$SKVIP_MARK" -e "$VAULT_ITEM" \
  . --exclude-dir=.git --exclude-dir=scripts --exclude='*.md' 2>/dev/null | grep -q .; then
  fail "credential vault references found in non-markdown export files"
fi
# Also scrub vault item name from markdown that still teaches private ops
if grep -R --binary-files=without-match -n -e "$VAULT_ITEM" . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null | grep -q .; then
  fail "private vault item name found in export (including markdown)"
fi

FB_MARK="wearwallet""-32cb8"
if grep -R --binary-files=without-match -n -e "$FB_MARK" \
  . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null | grep -q .; then
  fail "production Firebase project id found in export"
fi

MNEMONIC_HITS="$(grep -R --binary-files=without-match -n \
  -e 'EMOTION_MNEMONIC' -e 'REAL_MNEMONIC' -e 'LIVE_MNEMONIC' \
  -e 'production mnemonic' -e 'holds a substantial balance' \
  . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null || true)"
if [[ -n "$MNEMONIC_HITS" ]]; then
  echo "$MNEMONIC_HITS" | head -20 >&2
  fail "recovery-phrase / live-wallet fixture markers found in export"
fi

BIP39_ABORT="abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
HITS="$(grep -R --binary-files=without-match -nE \
  '"[a-z]+( [a-z]+){11,23}"' \
  wear/src/main coreKmp/src/androidMain coreKmp/src/commonMain coreKmp/src/iosMain coreKmp/src/watchosMain mobile/src \
  "watchos/WatchWallet Watch App" 2>/dev/null \
  | grep -v 'google-services' || true)"
if [[ -n "$HITS" ]]; then
  BAD="$(echo "$HITS" | grep -v "$BIP39_ABORT" || true)"
  if [[ -n "$BAD" ]]; then
    echo "$BAD" | head -20 >&2
    fail "possible hard-coded mnemonic string in main source"
  fi
fi

[[ ! -d watchos/Frameworks ]] || fail "watchos/Frameworks must not be published"

if command -v gitleaks >/dev/null 2>&1; then
  echo "==> gitleaks (export tree)"
  mkdir -p scripts/public-export
  cp "$ROOT/scripts/public-export/gitleaks.toml" scripts/public-export/gitleaks.toml
  gitleaks detect --source . --no-git --no-banner --redact --config ./scripts/public-export/gitleaks.toml \
    || fail "gitleaks reported findings"
else
  echo "WARN: gitleaks not installed; skipped tree scan" >&2
fi

echo "==> heuristic secret patterns"
if grep -R --binary-files=without-match -nE \
  'AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-' \
  . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null | grep -q .; then
  fail "high-confidence token pattern found"
fi

echo "==> guards passed"

mkdir -p scripts/public-export
cp "$ROOT/scripts/public-export/README.md" scripts/public-export/README.md
cp "$ROOT/scripts/public-export/blacklist.txt" scripts/public-export/blacklist.txt

if [[ "$DO_PUSH" -ne 1 ]]; then
  echo "Dry-run complete. Export at: $EXPORT_DIR"
  echo "Re-run with --push to publish to $PUBLIC_REPO"
  exit 0
fi

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "Both --dry-run and --push set; refusing push." >&2
  exit 2
fi

echo "==> publishing to $PUBLIC_REPO"
if ! gh repo view "$PUBLIC_REPO" >/dev/null 2>&1; then
  echo "Creating public repo $PUBLIC_REPO"
  gh repo create "$PUBLIC_REPO" --public --description "Sanitized public snapshot of WearWallet (experimental; not for real funds)"
fi

# Record prior tip for operator awareness (force-push still required for scrubbed history).
PRIOR="$(git ls-remote "https://github.com/${PUBLIC_REPO}.git" refs/heads/main 2>/dev/null | awk '{print $1}' || true)"
if [[ -n "$PRIOR" ]]; then
  echo "NOTE: replacing public main tip $PRIOR (orphan rewrite)."
fi

WORKDIR="$(mktemp -d)/WearWallet-public"
mkdir -p "$WORKDIR"
git -C "$WORKDIR" init -b main
git -C "$WORKDIR" remote add origin "https://github.com/${PUBLIC_REPO}.git"
rsync -a --delete --exclude '.git' "$EXPORT_DIR"/ "$WORKDIR"/
git -C "$WORKDIR" add -A
git -C "$WORKDIR" \
  -c user.name='WearWallet Public Export' \
  -c user.email='public-export@users.noreply.github.com' \
  commit -m "public-sync: from private ${SOURCE_SHORT}"
git -C "$WORKDIR" push --force -u origin main

echo "Published: https://github.com/${PUBLIC_REPO}"
echo "Private tip: $SOURCE_SHA"
echo "NOTE: public history is intentionally rewritten to a single clean tip each sync."
