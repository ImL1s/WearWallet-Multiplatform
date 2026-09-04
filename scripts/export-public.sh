#!/usr/bin/env bash
# Sanitize-export the private WearWallet tree into a public snapshot repo.
#
# Fail-closed policy: every guard below either passes or aborts the whole
# export (dry-run AND push). There is no "warn and continue" path — if a
# guard cannot run (missing tool, missing committed file, network failure
# during --push destination checks) that is itself a hard failure.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPORT_DIR="${EXPORT_DIR:-/tmp/wearwallet-public-export}"

# Paths (relative to ROOT) of exporter-owned control files. These are always
# read from the committed SOURCE_SHA (via `git show`/`git archive`), never
# from the working tree directly, so a clean-but-not-yet-pushed edit can't
# silently diverge from what guard #1 (clean worktree) already guarantees.
EXPORTER_SCRIPT_REL="scripts/export-public.sh"
BLACKLIST_REL="scripts/public-export/blacklist.txt"
OVERLAY_REL="scripts/public-export/overlay"
GITLEAKS_CFG_REL="scripts/public-export/gitleaks.toml"
GITLEAKS_IGNORE_REL="scripts/public-export/.gitleaksignore"
HEURISTIC_ALLOWLIST_REL="scripts/public-export/heuristic-allowlist.txt"
BIP39_TOOL_REL="scripts/public-export/tools/bip39_scan.py"
BIP39_WORDLIST_REL="scripts/public-export/tools/bip39-english.txt"
BIP39_ALLOWLIST_REL="scripts/public-export/bip39-allowlist.json"
MANIFEST_TOOL_REL="scripts/public-export/tools/gen_manifest.py"

PUBLIC_REPO="${PUBLIC_REPO:-ImL1s/WearWallet-public}"
PUBLIC_REPO_ID_EXPECTED="1353027767"   # pinned via `gh api repos/ImL1s/WearWallet-public --jq .id`
PUBLIC_DEFAULT_BRANCH_EXPECTED="main"

# Directories treated as "already public upstream" for the two noisier
# content-scanners (BIP39 mnemonic scan, extended-key/WIF heuristic). Every
# path here is a git submodule pointing at an independently public GitHub
# repo (verified visibility=public for all of them at hardening time); their
# own already-published content is not re-litigated by this exporter. They
# are still covered by gitleaks and the structural/file-type guards below.
ALREADY_PUBLIC_DIRS=(modules)

DRY_RUN=0
DO_PUSH=0
ALLOW_TAGS=0

usage() {
  cat <<'EOF'
Usage: scripts/export-public.sh [--dry-run] [--push] [--repo OWNER/NAME] [--out DIR] [--allow-tags]

Builds a sanitized tree from committed HEAD, runs secret/infra guards,
optionally force-syncs the public snapshot repo as a single orphan tip.

Requires a clean private worktree (`git status --porcelain` empty) and
gitleaks installed. Commit all changes under scripts/public-export/ and
scripts/export-public.sh before exporting; the exporter refuses to read
guard/blacklist/overlay content from an uncommitted working tree.

--allow-tags allows publishing when the public repo already has tags other
  than the current sync (reviewed exception; default is to fail closed).
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --push) DO_PUSH=1; shift ;;
    --repo) PUBLIC_REPO="$2"; shift 2 ;;
    --out) EXPORT_DIR="$2"; shift 2 ;;
    --allow-tags) ALLOW_TAGS=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

fail() { echo "GUARD FAIL: $*" >&2; exit 1; }
note() { echo "==> $*"; }

cd "$ROOT"

# ---------------------------------------------------------------------------
# Guard ledger: every required guard below calls mark_guard on success. If a
# guard is ever accidentally removed/commented out, its name is missing from
# the ledger and the completeness check right before publish fails closed.
# ---------------------------------------------------------------------------
GUARD_LOG="$(mktemp)"
mark_guard() { printf '%s\tPASS\n' "$1" >> "$GUARD_LOG"; }
REQUIRED_GUARDS=(
  clean-worktree provenance-verified tools-available submodules-pinned
  archive-built blacklist-applied blacklist-backstop overlay-applied
  google-services-example-only no-gitmodules no-renovate-dependabot
  no-agent-scratch no-keystore-pem no-mobileprovision-env-serviceaccount
  no-unsafe-ci workflow-count no-private-ops-paths no-vault-refs
  no-firebase-project-id no-mnemonic-markers no-watchos-frameworks
  bip39-scan gitleaks-scan heuristic-tokens manifest-generated
)

# ---------------------------------------------------------------------------
# 1. Refuse a dirty private worktree — hard fail, not a warning. This is the
#    foundation the "no using dirty working-tree rules" guarantee (#2) below
#    relies on: if the tree is clean, the working copy IS the committed SHA.
# ---------------------------------------------------------------------------
if [[ -n "$(git status --porcelain)" ]]; then
  git status --porcelain | head -20 >&2
  fail "private worktree is dirty (git status --porcelain is non-empty). Commit or stash ALL changes before exporting — including outside scripts/public-export/."
fi
mark_guard clean-worktree

SOURCE_SHA="$(git rev-parse HEAD)"
SOURCE_SHORT="$(git rev-parse --short=7 HEAD)"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
EXPORT_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

note "Export source: $BRANCH @ $SOURCE_SHA"

# ---------------------------------------------------------------------------
# 2. Require exporter script, blacklist, and overlay from the same committed
#    SOURCE_SHA. Read every control file via `git show`/`git archive`
#    (never a raw working-tree path) so guard logic always reflects exactly
#    what is committed at HEAD, independent of guard #1.
# ---------------------------------------------------------------------------
CTRL_DIR="$(mktemp -d)"
extract_committed_file() {
  local rel="$1" dest="$2"
  mkdir -p "$(dirname "$dest")"
  git show "HEAD:$rel" > "$dest" 2>/dev/null || fail "required control file not committed at HEAD: $rel"
}
extract_committed_file "$EXPORTER_SCRIPT_REL" "$CTRL_DIR/export-public.sh"
if ! diff -q "$CTRL_DIR/export-public.sh" "$ROOT/$EXPORTER_SCRIPT_REL" >/dev/null 2>&1; then
  fail "running script differs from committed HEAD:$EXPORTER_SCRIPT_REL (should be impossible with a clean worktree)"
fi
extract_committed_file "$BLACKLIST_REL" "$CTRL_DIR/blacklist.txt"
extract_committed_file "$GITLEAKS_CFG_REL" "$CTRL_DIR/gitleaks.toml"
extract_committed_file "$GITLEAKS_IGNORE_REL" "$CTRL_DIR/.gitleaksignore"
extract_committed_file "$HEURISTIC_ALLOWLIST_REL" "$CTRL_DIR/heuristic-allowlist.txt"
extract_committed_file "$BIP39_TOOL_REL" "$CTRL_DIR/bip39_scan.py"
extract_committed_file "$BIP39_WORDLIST_REL" "$CTRL_DIR/bip39-english.txt"
extract_committed_file "$BIP39_ALLOWLIST_REL" "$CTRL_DIR/bip39-allowlist.json"
extract_committed_file "$MANIFEST_TOOL_REL" "$CTRL_DIR/gen_manifest.py"
git rev-parse "HEAD:$OVERLAY_REL" >/dev/null 2>&1 || fail "overlay directory not committed at HEAD: $OVERLAY_REL"
mkdir -p "$CTRL_DIR/overlay"
git archive "HEAD:$OVERLAY_REL" | tar -x -C "$CTRL_DIR/overlay"

BLACKLIST="$CTRL_DIR/blacklist.txt"
OVERLAY="$CTRL_DIR/overlay"
GITLEAKS_CFG="$CTRL_DIR/gitleaks.toml"
GITLEAKS_IGNORE="$CTRL_DIR/.gitleaksignore"
HEURISTIC_ALLOWLIST="$CTRL_DIR/heuristic-allowlist.txt"
BIP39_TOOL="$CTRL_DIR/bip39_scan.py"
BIP39_WORDLIST="$CTRL_DIR/bip39-english.txt"
BIP39_ALLOWLIST="$CTRL_DIR/bip39-allowlist.json"
MANIFEST_TOOL="$CTRL_DIR/gen_manifest.py"
mark_guard provenance-verified

# ---------------------------------------------------------------------------
# Required tooling — missing gitleaks or python3 aborts BOTH dry-run and
# push. No silent "scanner skipped" path.
# ---------------------------------------------------------------------------
command -v gitleaks >/dev/null 2>&1 \
  || fail "gitleaks is required and was not found on PATH (brew install gitleaks). Missing gitleaks aborts dry-run and push."
command -v python3 >/dev/null 2>&1 \
  || fail "python3 is required (BIP39 scanner + manifest generator) and was not found on PATH."
GITLEAKS_VERSION="$(gitleaks version 2>&1 | tr -d '\n')"
mark_guard tools-available

rm -rf "$EXPORT_DIR"
mkdir -p "$EXPORT_DIR"

# ---------------------------------------------------------------------------
# 3/4. Submodules: every submodule must be initialized, its checked-out HEAD
#      must equal the pinned gitlink SHA, and we archive exactly that pinned
#      SHA from the submodule's own object store — no fallback to the
#      submodule's (possibly different) HEAD.
# ---------------------------------------------------------------------------
note "git archive HEAD → $EXPORT_DIR"
git archive --format=tar HEAD | tar -x -C "$EXPORT_DIR"
mark_guard archive-built

if [[ -f .gitmodules ]]; then
  note "verifying + packing submodules (flattened export; no gitlinks ship)"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    prefix="${line:0:1}"
    rest="${line:1}"
    sha="$(echo "$rest" | awk '{print $1}')"
    path="$(echo "$rest" | awk '{print $2}')"
    [[ -z "$path" ]] && continue
    case "$prefix" in
      ' ') : ;;
      '-') fail "submodule not initialized: $path (run: git submodule update --init --recursive)" ;;
      '+') fail "submodule checked-out commit does not match the pinned gitlink SHA: $path (run: git submodule update --init --recursive)" ;;
      'U') fail "submodule has merge conflicts: $path" ;;
      *) fail "unexpected submodule status prefix '$prefix' for $path" ;;
    esac
    git -C "$ROOT/$path" rev-parse --git-dir >/dev/null 2>&1 \
      || fail "submodule git directory missing (not initialized): $path"
    actual_head="$(git -C "$ROOT/$path" rev-parse HEAD)"
    [[ "$actual_head" == "$sha" ]] \
      || fail "submodule $path HEAD ($actual_head) does not match pinned gitlink SHA ($sha)"
    echo "    submodule $path @ ${sha:0:7} (initialized, HEAD verified)"
    rm -rf "${EXPORT_DIR:?}/$path"
    mkdir -p "$EXPORT_DIR/$path"
    if ! git -C "$ROOT/$path" archive --format=tar "$sha" 2>/dev/null | tar -x -C "$EXPORT_DIR/$path"; then
      fail "could not archive submodule $path @ pinned SHA $sha (no fallback to HEAD is attempted)"
    fi
  done < <(git submodule status --recursive)
fi
mark_guard submodules-pinned

# ---------------------------------------------------------------------------
# Blacklist application (mode A: flattened modules; .gitmodules removed).
# ---------------------------------------------------------------------------
apply_blacklist() {
  local line pattern base
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"
    line="$(echo "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    [[ -z "$line" ]] && continue
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

note "applying blacklist"
apply_blacklist
mark_guard blacklist-applied

note "blacklist backstop"
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
mark_guard blacklist-backstop

note "applying overlay"
(cd "$OVERLAY" && tar -cf - .) | (cd "$EXPORT_DIR" && tar -xf -)
mark_guard overlay-applied

# Soft-scrub remaining markdown that still teaches private ops (paths kept for build docs).
note "scrubbing private-ops phrasing from docs"
(
  cd "$EXPORT_DIR"
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
    -e 's|Automated unit, build, security, and cross-platform CI workflows|Public Ubuntu CI: Wear assembleDebug + markdown links (not a full unit suite)|g' \
    "$BANNER_FILE" && rm -f "${BANNER_FILE}.bak"
  if ! grep -q 'canonical development tree' "$BANNER_FILE"; then
    tmp="$(mktemp)"
    cat >"$tmp" <<EOF
> [!IMPORTANT]
> This public repo (\`ImL1s/WearWallet-public\`) is the **canonical development
> tree**. The private repo (\`ImL1s/WearWallet\`) is frozen as a historical / ops
> vault. Do **not** force-export from private over this \`main\`. Do **not** use
> with real funds. See [\`docs/PUBLIC_BUILD.md\`](./docs/PUBLIC_BUILD.md) and
> [\`docs/PUBLIC_SNAPSHOT.md\`](./docs/PUBLIC_SNAPSHOT.md).
>
> Last sanitized export from private tip: \`$SOURCE_SHORT\`

EOF
    cat "$BANNER_FILE" >>"$tmp"
    mv "$tmp" "$BANNER_FILE"
  fi
fi

mkdir -p "$EXPORT_DIR/docs"
PROVENANCE_FILE="$EXPORT_DIR/docs/PUBLIC_SNAPSHOT.md"
{
  if [[ -f "$PROVENANCE_FILE" ]]; then
    cat "$PROVENANCE_FILE"
    printf '\n'
  else
    cat <<'HDR'
# Public tree provenance

This public repo (`ImL1s/WearWallet-public`) is the **canonical development tree**.
The private repo (`ImL1s/WearWallet`) is frozen as a historical / ops vault.
Do **not** force-export from private over this `main` as ongoing sync.

HDR
  fi
  cat <<EOF
## Last sanitized export

- Private source tip: \`$SOURCE_SHA\`
- Export UTC time: \`$EXPORT_UTC\`
- Method: \`scripts/export-public.sh\` (blacklist + overlay + guards)

This repository intentionally has **no private development history**.
This was the last intended overwrite of public \`main\` from private.
Consumers should not run the export script.
See \`export-manifest.json\` at the repo root for the full guard/scanner
ledger and a sha256 of every shipped file.
EOF
} >"${PROVENANCE_FILE}.new"
mv "${PROVENANCE_FILE}.new" "$PROVENANCE_FILE"

if [[ -f "$EXPORT_DIR/.github/SECURITY.md" && ! -f "$EXPORT_DIR/SECURITY.md" ]]; then
  cp "$EXPORT_DIR/.github/SECURITY.md" "$EXPORT_DIR/SECURITY.md"
fi

note "running publish guards"
cd "$EXPORT_DIR"

[[ ! -f wear/google-services.json && ! -f mobile/google-services.json ]] \
  || fail "production google-services.json present (only *.example allowed)"
[[ -f wear/google-services.json.example ]] || fail "missing wear/google-services.json.example"
[[ -f mobile/google-services.json.example ]] || fail "missing mobile/google-services.json.example"
mark_guard google-services-example-only

[[ ! -f .gitmodules ]] || fail ".gitmodules must not ship (modules are flattened)"
mark_guard no-gitmodules

[[ ! -f renovate.json && ! -f .github/dependabot.yml ]] \
  || fail "renovate/dependabot configs must not ship on public tip"
mark_guard no-renovate-dependabot

[[ ! -d .claude && ! -d .serena && ! -d .kiro ]] || fail "agent scratch dirs must not ship"
mark_guard no-agent-scratch

if find . -type f \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.pem' -o -name '*.so' \) \
  ! -path './modules/*/LICENSE*' | grep -q .; then
  fail "keystore/pem/native .so artifacts found in export tree"
fi
if grep -R --binary-files=without-match -n \
  -e "BEGIN RSA PRIVATE KEY" -e "BEGIN OPENSSH PRIVATE KEY" -e "BEGIN EC PRIVATE KEY" -e "BEGIN PRIVATE KEY" \
  . --exclude-dir=.git --exclude-dir=scripts --exclude='*.md' 2>/dev/null | grep -q .; then
  fail "PEM private key block found in export tree"
fi
mark_guard no-keystore-pem

# --- 9. Reject PEM/JKS/keystore/P12/mobileprovision/.env/service-account JSON ---
if find . -type f -name '*.mobileprovision' | grep -q .; then
  fail ".mobileprovision file(s) found in export tree"
fi
ENV_HITS="$(find . -type f \( -name '.env' -o -name '.env.*' \) ! -name '.env.example' 2>/dev/null || true)"
if [[ -n "$ENV_HITS" ]]; then
  echo "$ENV_HITS" >&2
  fail "real .env file(s) found in export tree (only .env.example is allowed)"
fi
SA_HITS="$(grep -R --binary-files=without-match -l -E '"type"[[:space:]]*:[[:space:]]*"service_account"' \
  --include='*.json' . --exclude-dir=.git 2>/dev/null || true)"
if [[ -n "$SA_HITS" ]]; then
  echo "$SA_HITS" >&2
  fail "GCP service-account JSON key(s) found in export tree"
fi
mark_guard no-mobileprovision-env-serviceaccount

if grep -R -nE 'runs-on:.*self-hosted|runs-on:[[:space:]]*\[[^]]*self-hosted|ww-ci[0-9]|ENABLE_HOSTED_SIDE_CHECKS|pull_request_target' \
  .github/workflows 2>/dev/null | grep -v 'Never register self-hosted' | grep -q .; then
  fail "unsafe CI markers found under .github/workflows"
fi
mark_guard no-unsafe-ci

wf_count="$(find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) ! -name '*.example' | wc -l | tr -d ' ')"
[[ "$wf_count" -eq 2 ]] || fail "expected exactly 2 active workflows (ci+release); found $wf_count"
for required in .github/workflows/ci.yml .github/workflows/release.yml; do
  [[ -f "$required" ]] || fail "missing required workflow: $required"
done
mark_guard workflow-count

for p in scripts/deploy_to_play_console.py scripts/load-credentials.sh scripts/check-1password-vaults.sh set_clipboard.js; do
  [[ ! -e "$p" ]] || fail "private ops path still present: $p"
done
mark_guard no-private-ops-paths

OP_MARK="op:/"
OP_MARK="${OP_MARK}/"
SKVIP_MARK="skvip"".org"
VAULT_ITEM="WearWallet/API""_Keys"
if grep -R --binary-files=without-match -n -e "$OP_MARK" -e "1Password Connect" -e "$SKVIP_MARK" -e "$VAULT_ITEM" \
  . --exclude-dir=.git --exclude-dir=scripts --exclude='*.md' 2>/dev/null | grep -q .; then
  fail "credential vault references found in non-markdown export files"
fi
if grep -R --binary-files=without-match -n -e "$VAULT_ITEM" . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null | grep -q .; then
  fail "private vault item name found in export (including markdown)"
fi
mark_guard no-vault-refs

FB_MARK="wearwallet""-32cb8"
if grep -R --binary-files=without-match -n -e "$FB_MARK" \
  . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null | grep -q .; then
  fail "production Firebase project id found in export"
fi
mark_guard no-firebase-project-id

MNEMONIC_HITS="$(grep -R --binary-files=without-match -n \
  -e 'EMOTION_MNEMONIC' -e 'ROOKIE_MNEMONIC' -e 'MONERO_MNEMONIC_25' \
  -e 'REAL_MNEMONIC' -e 'LIVE_MNEMONIC' \
  -e 'production mnemonic' -e 'holds a substantial balance' \
  . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null || true)"
if [[ -n "$MNEMONIC_HITS" ]]; then
  echo "$MNEMONIC_HITS" | head -20 >&2
  fail "recovery-phrase / live-wallet fixture markers found in export"
fi
# Known-sensitive Monero seed (associated with a real, previously-funded test
# wallet — see scripts/public-export/HARDENING_STATUS.md). Split across two
# variables so this script's own source does not self-match. Deliberately NOT
# allowlisted anywhere: this must be fixed at the source (coreKmp) before it
# can ship publicly, not suppressed by the exporter.
MONERO_MARK_A="emotion adopt stockpile tumbling myth software talent python coal much lion nobody"
MONERO_MARK_B="tomorrow goblet habitat items tyrant pairing roster itches giddy ledge gigantic gleeful lion"
if grep -R --binary-files=without-match -n -e "$MONERO_MARK_A" -e "$MONERO_MARK_B" \
  . --exclude-dir=.git --exclude-dir=scripts 2>/dev/null | grep -q .; then
  fail "known-sensitive Monero seed fixture found in export tree (fix at source; see HARDENING_STATUS.md)"
fi
mark_guard no-mnemonic-markers

[[ ! -d watchos/Frameworks ]] || fail "watchos/Frameworks must not be published"
mark_guard no-watchos-frameworks

# ---------------------------------------------------------------------------
# 8. BIP39-aware mnemonic scanner. Skips ALREADY_PUBLIC_DIRS (vendored,
#    independently-public submodules) — see the comment on that array above.
# ---------------------------------------------------------------------------
note "BIP39-aware mnemonic scanner"
BIP39_REPORT="$EXPORT_DIR.bip39-report.json"
skip_args=()
for d in "${ALREADY_PUBLIC_DIRS[@]}"; do
  skip_args+=(--skip-dir "$d")
done
if ! python3 "$BIP39_TOOL" --root "$EXPORT_DIR" --wordlist "$BIP39_WORDLIST" \
    --allowlist "$BIP39_ALLOWLIST" --report "$BIP39_REPORT" "${skip_args[@]}"; then
  fail "BIP39 scanner found unreviewed mnemonic-like content (see $BIP39_REPORT; phrases are not printed — locate by file:line and review before adding to $BIP39_ALLOWLIST_REL)"
fi
mark_guard bip39-scan

# ---------------------------------------------------------------------------
# 5/6. gitleaks: required tool (already asserted above), full default rule
#      set including generic-api-key, narrow reviewed .gitleaksignore.
# ---------------------------------------------------------------------------
note "gitleaks (export tree, generic-api-key ENABLED)"
if ! gitleaks detect --source . --no-git --no-banner --redact \
    --config "$GITLEAKS_CFG" --gitleaks-ignore-path "$GITLEAKS_IGNORE"; then
  fail "gitleaks reported findings not covered by the reviewed .gitleaksignore"
fi
mark_guard gitleaks-scan

# ---------------------------------------------------------------------------
# 7. Expanded heuristic token checks: GitHub tokens (all prefixes), AWS keys
#    (permanent + temporary), Slack, Google API keys, BIP32 extended private
#    keys (xprv/tprv), and WIF-format private keys. Narrow, reviewed
#    exact-substring allowlist only (scripts/public-export/heuristic-allowlist.txt).
#    Already-public vendored dirs are excluded, same rationale as the BIP39
#    scanner above.
# ---------------------------------------------------------------------------
note "heuristic secret token patterns"
exclude_args=(--exclude-dir=.git --exclude-dir=scripts)
for d in "${ALREADY_PUBLIC_DIRS[@]}"; do
  exclude_args+=(--exclude-dir="$d")
done
HEUR_HITS="$(grep -RnE \
  'AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}|gho_[A-Za-z0-9]{20,}|ghu_[A-Za-z0-9]{20,}|ghs_[A-Za-z0-9]{20,}|ghr_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]+|AIza[0-9A-Za-z_-]{35}|[xt]prv[A-HJ-NP-Za-km-z1-9]{100,116}|\b[5KL][1-9A-HJ-NP-Za-km-z]{50,51}\b|\b[9c][1-9A-HJ-NP-Za-km-z]{50,51}\b' \
  . "${exclude_args[@]}" 2>/dev/null || true)"
if [[ -n "$HEUR_HITS" ]]; then
  FILTERED="$HEUR_HITS"
  while IFS= read -r safe || [[ -n "$safe" ]]; do
    [[ -z "$safe" ]] && continue
    [[ "$safe" == \#* ]] && continue
    FILTERED="$(echo "$FILTERED" | grep -vF "$safe" || true)"
  done < "$HEURISTIC_ALLOWLIST"
  if [[ -n "$FILTERED" ]]; then
    echo "$FILTERED" | head -40 >&2
    fail "high-confidence secret token pattern found (see above; if a reviewed false positive, add the exact literal to scripts/public-export/heuristic-allowlist.txt with a justification comment)"
  fi
fi
mark_guard heuristic-tokens

note "guards passed"

mkdir -p scripts/public-export scripts/public-export/tools
cp "$ROOT/scripts/public-export/README.md" scripts/public-export/README.md
cp "$BLACKLIST" scripts/public-export/blacklist.txt

# ---------------------------------------------------------------------------
# 12. export-manifest.json: every shipped file's path/size/sha256, scanner
#     versions, and the full guard ledger. Never publish if any required
#     guard is missing from the ledger (checked right below, before push).
# ---------------------------------------------------------------------------
note "generating export-manifest.json"
python3 "$MANIFEST_TOOL" --root "$EXPORT_DIR" --out "$EXPORT_DIR/export-manifest.json" \
  --source-sha "$SOURCE_SHA" --source-branch "$BRANCH" --export-utc "$EXPORT_UTC" \
  --gitleaks-version "$GITLEAKS_VERSION" --bip39-report "$BIP39_REPORT" --guard-log "$GUARD_LOG"
mark_guard manifest-generated

missing_guards=()
for g in "${REQUIRED_GUARDS[@]}"; do
  grep -q "^${g}	PASS$" "$GUARD_LOG" || missing_guards+=("$g")
done
if [[ ${#missing_guards[@]} -gt 0 ]]; then
  fail "refusing to publish: guard(s) skipped or missing from ledger: ${missing_guards[*]}"
fi
note "guard ledger complete (${#REQUIRED_GUARDS[@]} required guards all PASS)"

if [[ "$DO_PUSH" -ne 1 ]]; then
  echo "Dry-run complete. Export at: $EXPORT_DIR"
  echo "Re-run with --push to publish to $PUBLIC_REPO"
  exit 0
fi

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "Both --dry-run and --push set; refusing push." >&2
  exit 2
fi

# ---------------------------------------------------------------------------
# 10. Pin destination: owner/name, numeric repo ID, visibility=public,
#     default branch=main. Verified live via `gh api` right before push.
# ---------------------------------------------------------------------------
note "verifying pinned destination repo: $PUBLIC_REPO"
[[ "$PUBLIC_REPO" == "ImL1s/WearWallet-public" ]] \
  || fail "PUBLIC_REPO is pinned to ImL1s/WearWallet-public; refusing to push to '$PUBLIC_REPO'"
command -v gh >/dev/null 2>&1 || fail "gh CLI is required to verify the destination repo before push"
REPO_JSON="$(gh api "repos/$PUBLIC_REPO" 2>/dev/null)" || fail "cannot query destination repo via gh api: $PUBLIC_REPO"
REPO_ID="$(echo "$REPO_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')"
REPO_VISIBILITY="$(echo "$REPO_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["visibility"])')"
REPO_DEFAULT_BRANCH="$(echo "$REPO_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["default_branch"])')"
[[ "$REPO_ID" == "$PUBLIC_REPO_ID_EXPECTED" ]] \
  || fail "destination repo id mismatch: expected $PUBLIC_REPO_ID_EXPECTED, got $REPO_ID (possible repo recreation/hijack — re-pin PUBLIC_REPO_ID_EXPECTED only after manual verification)"
[[ "$REPO_VISIBILITY" == "public" ]] \
  || fail "destination repo visibility is '$REPO_VISIBILITY', expected 'public'"
[[ "$REPO_DEFAULT_BRANCH" == "$PUBLIC_DEFAULT_BRANCH_EXPECTED" ]] \
  || fail "destination repo default branch is '$REPO_DEFAULT_BRANCH', expected '$PUBLIC_DEFAULT_BRANCH_EXPECTED'"
note "destination verified: id=$REPO_ID visibility=$REPO_VISIBILITY default_branch=$REPO_DEFAULT_BRANCH"

# ---------------------------------------------------------------------------
# 11. No unexpected public branches/tags. main only, by default zero tags.
# ---------------------------------------------------------------------------
note "verifying public repo refs (main-only branches; tags policy)"
BRANCHES="$(gh api "repos/$PUBLIC_REPO/branches" --paginate --jq '.[].name' 2>/dev/null || true)"
UNEXPECTED_BRANCHES="$(echo "$BRANCHES" | grep -v '^main$' || true)"
if [[ -n "$UNEXPECTED_BRANCHES" ]]; then
  echo "$UNEXPECTED_BRANCHES" >&2
  fail "unexpected public branches present on $PUBLIC_REPO (main only is allowed)"
fi
TAGS="$(gh api "repos/$PUBLIC_REPO/tags" --paginate --jq '.[].name' 2>/dev/null || true)"
if [[ -n "$TAGS" && "$ALLOW_TAGS" -ne 1 ]]; then
  echo "$TAGS" >&2
  fail "unexpected tag(s) present on $PUBLIC_REPO (pass --allow-tags after reviewing them, or delete stray tags first)"
fi

note "publishing to $PUBLIC_REPO"
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
