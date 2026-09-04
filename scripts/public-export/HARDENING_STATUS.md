# Public export hardening status

Scope of this report: `scripts/export-public.sh`, `scripts/public-export/**`,
`wear/build.gradle.kts`, `mobile/build.gradle.kts` (the `publicSnapshot`
Gradle property). No mnemonic phrases are printed anywhere in this file.

Destination pinned in `scripts/export-public.sh`:
`PUBLIC_REPO=ImL1s/WearWallet-public`, `PUBLIC_REPO_ID_EXPECTED=1353027767`
(verified live via `gh api repos/ImL1s/WearWallet-public` — `visibility:
public`, `default_branch: main`).

## TL;DR

- The exporter is now fail-closed end-to-end: dirty worktree, missing
  gitleaks, missing/uncommitted control files, uninitialized or
  drifted submodules, unexpected public branches, and a missing guard
  from the ledger all hard-abort both `--dry-run` and `--push`.
- Every scanner (gitleaks with `generic-api-key` restored, the new
  BIP39-aware mnemonic scanner, and the expanded heuristic token
  regexes) is **green** on a full simulated export of the current
  private `HEAD`.
- Re-checked Task 0.1: the previously reported hardcoded Monero seed is
  **not** in `MoneroBackend.kt` or `MemoryWalletManager.kt`. Those files
  take a caller-supplied mnemonic only. Remaining copies of the
  known-sensitive phrase live only on already-blacklisted fixture paths
  (see Remaining blocker). Do not print the phrase.
- I could not run the real `./scripts/export-public.sh --dry-run` command
  itself this session because the private worktree has other agents'
  concurrent uncommitted changes outside my ownership (by design, guard
  #1 now hard-fails on any dirty worktree). Everything below was verified
  by manually reproducing the exact same tree-build + guard steps the
  script performs, driven straight from committed `HEAD`.

## Files changed

### `scripts/export-public.sh` (rewritten)

- Guard #1: `git status --porcelain` non-empty is now a **hard fail**
  (was a warning).
- Guard #2: every control input (script itself, blacklist, overlay,
  gitleaks config/ignore file, heuristic allowlist, BIP39 tool/wordlist/
  allowlist, manifest generator) is read via `git show HEAD:<path>` /
  `git archive HEAD:<path>` into a scratch dir — never the live working
  tree — and the running script is byte-compared against
  `HEAD:scripts/export-public.sh`.
- Guard #3/#4: submodule status parsing now hard-fails on `-` (not
  initialized), `+` (checked-out commit differs from pinned gitlink SHA),
  or `U` (conflict); for `' '` (clean) it additionally re-verifies
  `git -C <path> rev-parse HEAD == <pinned sha>` before archiving. No
  fallback to the submodule's own `HEAD` if archiving the pinned SHA
  fails.
- Guard #5: `command -v gitleaks` and `command -v python3` are hard
  requirements checked before any tree is built; missing either aborts
  dry-run and push.
- Guard #6: gitleaks now runs with `useDefault = true` (full default
  rule set, including `generic-api-key`) and a narrow
  `--gitleaks-ignore-path` file — see `.gitleaksignore` below. No rule is
  silenced with a dummy pattern.
- Guard #7: heuristic token regex expanded to GitHub tokens
  (`ghp_/gho_/ghu_/ghs_/ghr_/github_pat_`), AWS (`AKIA…`/`ASIA…`), Slack
  (`xox[baprs]-…`), Google API keys (`AIza…`), BIP-32 extended private
  keys (`xprv…`/`tprv…`), and WIF-format private keys — filtered through
  a new exact-substring, reviewed allowlist
  (`scripts/public-export/heuristic-allowlist.txt`).
- Guard #8: new BIP39-aware mnemonic scanner
  (`scripts/public-export/tools/bip39_scan.py`) runs over the whole
  export tree (skipping `modules/`, see below) and hard-fails on any
  valid, checksum-verified 12/15/18/21/24-word mnemonic that is not in
  the reviewed `bip39-allowlist.json` by content sha256.
- Guard #9: explicit rejection of `.mobileprovision`, real `.env*`
  (`.env.example` excluded), and GCP `service_account` JSON keys, in
  addition to the pre-existing JKS/keystore/P12/PEM/`.so` checks.
- Guard #10 (push-only): verifies `PUBLIC_REPO == ImL1s/WearWallet-public`,
  then queries `gh api repos/$PUBLIC_REPO` and hard-fails unless
  `id == 1353027767`, `visibility == public`, `default_branch == main`.
- Guard #11 (push-only): hard-fails if any branch other than `main`
  exists on the public repo, or if any tag exists and `--allow-tags`
  was not passed (see "Known operational follow-up" below — one stray
  tag already exists on the public repo today).
- Guard #12: new `export-manifest.json` (via
  `scripts/public-export/tools/gen_manifest.py`) records every shipped
  file's path/size/sha256, gitleaks version, BIP39 scanner summary, and
  the full guard ledger. A `REQUIRED_GUARDS` completeness check runs
  right before the dry-run/push branch and hard-fails if any of the 24
  named guards did not record a `PASS` in the ledger — a guard that is
  silently deleted/commented out from the script can no longer result in
  a silent publish.
- Guard #13: unchanged "flattened modules" behavior — `.gitmodules` is
  blacklisted and each submodule is packed as a plain tree at its pinned
  SHA; `[[ ! -f .gitmodules ]]` remains a publish guard.
- `--allow-tags` CLI flag added (see usage banner) for the documented
  tag-policy exception path.

### `scripts/public-export/gitleaks.toml`

- Restored `useDefault = true` (nothing above silences a whole rule).
- `[allowlist].paths` now allows exactly two narrow, static path
  patterns: the Firebase example stub filename, and `^modules/.*`.
  `modules/**` is 9 git submodules (`ImL1s/kotlin-tx-builder`,
  `kotlin-address`, `kotlin-crypto-pure`, `kotlin-blockchain-client`,
  `kotlin-secure-storage`, `kotlin-caip-standards`, `kotlin-utxo`,
  `ACINQ/secp256k1-kmp`, `ACINQ/bitcoin-kmp`, plus the nested
  `bitcoin-core/secp256k1`) — **all verified `visibility: public`** via
  `gh api repos/<owner>/<repo> --jq .visibility` this session. Their
  content ships to the export tree byte-for-byte from each submodule's
  own pinned-SHA object store, so re-scanning it here is out of scope —
  the same rationale already applied to the BIP39/heuristic scanners via
  `ALREADY_PUBLIC_DIRS`/`--skip-dir modules`.
- No fingerprints live in this TOML file (gitleaks 8.28 does not honor
  a `fingerprints` field here) — see `.gitleaksignore`.

### `scripts/public-export/.gitleaksignore` (new)

131 exact `path:rule:line` fingerprints, each a manually-reviewed false
positive of `generic-api-key` in `coreKmp`/`wear`/`watchos` (RFC6979/
BIP32 test vectors, public on-chain addresses, code identifiers). None
are real secrets. Loaded via `--gitleaks-ignore-path`.

### `scripts/public-export/bip39-allowlist.json`

8 reviewed content-fingerprint entries (sha256 of the lowercase,
single-space-joined phrase): the official 12/24-word BIP-39 spec
reference vectors (all-zero and `0x7f`-repeated entropy — public domain,
reused by virtually every BIP-39 test suite), the same "all-zero +
checksum word" construction extended to 15/18/21 words in
`Bip39TestVectorsTest.kt`, and 2 project-owned testnet-only fixtures
(`TestnetConfig.MNEMONIC_1/2`). No mainnet mnemonic is or should ever be
listed here.

### `scripts/public-export/heuristic-allowlist.txt` (new)

12 exact BIP-32 `xprv` literals from
`coreKmp/src/commonTest/kotlin/io/github/iml1s/crypto/Bip32Test.kt` —
official BIP-32 spec test vectors (Test Vector 1 & 2), zero real-fund
risk.

### `scripts/public-export/tools/bip39_scan.py`, `bip39-english.txt`, `gen_manifest.py` (new)

See docstrings in each file. `bip39_scan.py` never prints phrases;
`--summary-only` mode is for local triage. `gen_manifest.py` writes
`export-manifest.json`.

### `scripts/public-export/overlay/README.md`, `overlay/CLAUDE.md` (new)

- `overlay/README.md`: full curated public README — public clone URL
  (`WearWallet-public`, no `--recurse-submodules`), explicit "modules are
  a flattened plain-tree vendoring, not git submodules" note, all
  `./gradlew` examples updated to `-PpublicSnapshot=true`, links only to
  docs that actually ship publicly, same experimental/no-real-funds
  banner the script also auto-injects if missing.
- `overlay/CLAUDE.md`: curated public agent-guidance file (root
  `CLAUDE.md` is blacklisted — before this change the public tip shipped
  with **no** `CLAUDE.md` at all). Build/test commands use
  `-PpublicSnapshot=true`; all 1Password/vault/Play-Console-upload
  content is removed; explicit "no CI credential automation lives here"
  note.

### `scripts/public-export/overlay/.github/workflows/ci.yml`, `release.yml`

- Removed the `cp -n *.example google-services.json` step from both
  workflows (was the explicitly-disallowed "primary approach" of
  materializing a fake `google-services.json`). All `./gradlew`
  invocations now pass `-PpublicSnapshot=true` instead, which skips the
  `google-services`/`crashlytics`/`firebase-perf` plugins entirely (see
  `wear/build.gradle.kts` / `mobile/build.gradle.kts`, already committed
  by an earlier pass this session and re-verified below).
- `ci.yml`: JUnit report upload changed `if-no-files-found: ignore` →
  `error` (tests are always expected to run and produce reports on this
  job).
- `release.yml`: added the same JUnit-report upload step (previously
  missing entirely), also `if-no-files-found: error`.
- `ci.yml`: added a second job, `markdown-link-check`, using
  `gaurav-nelson/github-action-markdown-link-check@v1` with a new
  `overlay/.github/markdown-link-check.json` config (ignores
  `localhost`/`127.0.0.1`/in-page anchors; retries 429s; treats 403/429
  as alive to avoid CI flakiness on rate-limited hosts).
- `release.yml` source-tarball staging already excluded `./dist` (via
  `tar --exclude='./dist' ...`) before this session — confirmed still
  correct, no change needed.

### `wear/build.gradle.kts`, `mobile/build.gradle.kts`

Already had `-PpublicSnapshot=true` wired in from an earlier pass this
session (`isPublicSnapshot` gate around
`com.google.gms.google-services` / `com.google.firebase.crashlytics` /
`com.google.firebase.firebase-perf`). Re-verified this session:
`./gradlew :wear:help -PpublicSnapshot=true` and
`./gradlew :mobile:help -PpublicSnapshot=true` both configure
successfully with no Firebase/Google-Services plugin applied and no
`google-services.json` required.

## Gates now passing (verified this session)

All of the following were run against a from-scratch tree built exactly
the way `scripts/export-public.sh` builds one — `git archive HEAD` +
pinned-SHA submodule packing + blacklist + overlay + the doc-scrub
step — from the current private `HEAD`:

| Gate | Result |
| --- | --- |
| gitleaks (`useDefault=true`, `.gitleaksignore`, `modules/` path-allowlisted) | **0 findings** (was 1353 findings pre-fix; 1222 were vendored `modules/**` test vectors, 131 were reviewed `.gitleaksignore` fingerprints in `coreKmp`/`wear`/`watchos`) |
| BIP39-aware scanner (`--skip-dir modules`) | **0 unreviewed** (8 distinct valid mnemonics found, all 8 reviewed/allowlisted) |
| Heuristic token regex (GitHub/AWS/Slack/Google/xprv/tprv/WIF, minus `heuristic-allowlist.txt`) | **0 remaining hits** |
| `google-services.json.example` present (wear + mobile), no real `google-services.json` | pass |
| No `.gitmodules`, no renovate/dependabot, no agent scratch dirs (`.claude/.serena/.kiro`) | pass |
| No keystore/JKS/P12/PEM/`.so`, no PEM private-key blocks | pass |
| No `.mobileprovision`, no real `.env*`, no `service_account` JSON | pass |
| No unsafe CI markers (self-hosted runners, `pull_request_target`, etc.) | pass |
| Exactly 2 active workflows (`ci.yml` + `release.yml`) | pass |
| No private-ops script paths (`deploy_to_play_console.py`, `load-credentials.sh`, etc.) | pass |
| No 1Password/vault references, no vault item name, anywhere including markdown | pass (only after the doc-scrub step runs — confirmed the scrub is a hard prerequisite for this guard) |
| No production Firebase project id | pass |
| No `EMOTION_MNEMONIC`/`REAL_MNEMONIC`/etc. marker names | pass |
| No `watchos/Frameworks` | pass |
| `export-manifest.json` generation (`gen_manifest.py`) | pass — 5,016 files, ~45.8 MB, sha256 per file, gitleaks + BIP39 scanner summaries, guard ledger |

## Remaining blocker

**Cleared for the two previously named `coreKmp` main files (Task 0.1
re-check).** Searched committed sources for the exporter marker names
(`MONERO_MNEMONIC_25`, `EMOTION_MNEMONIC`, `ROOKIE_MNEMONIC`,
`REAL_MNEMONIC`, `LIVE_MNEMONIC`) and the split phrase guard
(`MONERO_MARK_A` / `MONERO_MARK_B` in `scripts/export-public.sh`).
**The phrase is not printed in this file.**

- `MoneroBackend.kt` (`commonMain`) and `MemoryWalletManager.kt`
  (`androidMain`) do **not** contain the known-sensitive seed or those
  marker names. Both accept a mnemonic argument from the caller / env
  and store it in memory. No hardcoded live seed in those product files.
- `WalletMainScreen.kt` matches `HEAD` (`03cdfa63`); not a leftover
  local toast-uncomment.
- Copies of the known-sensitive phrase that remain are **already
  blacklisted** and must stay blacklisted (do not un-blacklist):
  `**/MoneroMnemonicUtils.kt`, `**/MoneroSimpleTest.kt`,
  `**/MoneroKMP*.kt`, `**/MoneroDeviceTestActivity.kt`,
  `wear/.../presentation/debug/`, `**/monerujo_dynamic_bridge.cpp`,
  plus `**/*.disabled` test fixtures. Those paths are not required on
  the public compile path the way `MoneroBackend` / `MemoryWalletManager`
  are.
- WatchOS create / import / mnemonic-display screens were un-blacklisted
  as product UX. Placeholder copy is the official BIP-39 all-zero
  spec-vector (already in `bip39-allowlist.json`), not a live seed.
  Keep `watchos/test*.swift`, `watchos/Test*.swift`, and
  `watchos/validate_kmp.swift` blacklisted.

The exporter's literal-substring guard stays in place (fail-closed). It
should pass `no-mnemonic-markers` on a clean worktree as long as the
fixture paths above remain blacklisted. Do not weaken the phrase guard.

## Known operational follow-up (not a code bug)

- **`ImL1s/WearWallet-public` already has one tag**, `v0.1.0-public.1`
  (from a prior release), in addition to `main`. Guard #11 will
  therefore fail-closed on the very next real `--push` unless the
  operator either deletes that tag (if it's stale/unwanted) or passes
  `--allow-tags` after confirming `v0.1.0-public.1` is a legitimate
  previous release marker worth keeping. This is a one-time policy
  decision for whoever runs the next `--push`, not something this
  exporter should decide unilaterally.
- Verified this session: `gh api repos/ImL1s/WearWallet-public` →
  `id: 1353027767`, `visibility: public`, `default_branch: main`,
  `branches: [main]` (no unexpected branches).

## Commands run this session (representative)

```bash
# Repo/branch/tag verification
gh api repos/ImL1s/WearWallet-public --jq '{id,visibility,default_branch,private}'
gh api repos/ImL1s/WearWallet-public/branches --jq '.[].name'
gh api repos/ImL1s/WearWallet-public/tags --jq '.[].name'
for r in ImL1s/kotlin-tx-builder ImL1s/kotlin-address ImL1s/kotlin-crypto-pure \
         ImL1s/kotlin-blockchain-client ImL1s/kotlin-secure-storage \
         ImL1s/kotlin-caip-standards ImL1s/kotlin-utxo \
         ACINQ/secp256k1-kmp ACINQ/bitcoin-kmp; do
  gh api "repos/$r" --jq '.visibility'
done

# Gradle publicSnapshot config validation
./gradlew :wear:help -PpublicSnapshot=true --console=plain -q
./gradlew :mobile:help -PpublicSnapshot=true --console=plain -q

# Simulated export tree (git archive HEAD + pinned-SHA submodule packing +
# blacklist + overlay + doc-scrub — the exact steps export-public.sh performs)
git archive --format=tar HEAD | tar -x -C /tmp/wwtest
# ... blacklist + overlay + doc-scrub applied by hand, then:

gitleaks detect --source /tmp/wwtest --no-git --no-banner --redact \
  --config scripts/public-export/gitleaks.toml \
  --gitleaks-ignore-path scripts/public-export/.gitleaksignore
# => no leaks found

python3 scripts/public-export/tools/bip39_scan.py --root /tmp/wwtest \
  --wordlist scripts/public-export/tools/bip39-english.txt \
  --allowlist scripts/public-export/bip39-allowlist.json --skip-dir modules
# => 8 distinct phrase(s) found, all reviewed/allowlisted.

python3 scripts/public-export/tools/gen_manifest.py --root /tmp/wwtest \
  --out /tmp/export-manifest.json --source-sha "$(git rev-parse HEAD)" \
  --source-branch "$(git rev-parse --abbrev-ref HEAD)" \
  --export-utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --gitleaks-version "$(gitleaks version)" --bip39-report /tmp/bip39-report.json \
  --guard-log /tmp/guard-log.tsv
# => wrote manifest: 5016 files, ~45.8 MB
```

## Why the original hardening pass did not run `./scripts/export-public.sh --dry-run`

Guard #1 (intentional) hard-fails on any dirty worktree. During that
session the private worktree had concurrent uncommitted changes outside
exporter file ownership, so the script would abort at guard #1. Task 0.1
clears that: kotlin-utxo build dirt restored (not committed), `.omg/`
ignored, watchOS create/import/mnemonic UI un-blacklisted, and the
stale `coreKmp` main-source seed claim corrected.

```bash
./scripts/export-public.sh --dry-run
```

`no-mnemonic-markers` is expected to **pass** while the known-sensitive
phrase remains only on already-blacklisted fixture paths. Do not weaken
the phrase guard. Every other guard, including both secret scanners, was
verified green against `HEAD` content in the original hardening pass.

## Do NOT

- This report does not commit or push anything. No `git commit` was run
  in the actual repository as part of this task; the "simulated export
  tree" work happened entirely under `/tmp/wwtest` against files read
  via `git show`/`git archive HEAD:<path>` and `gh api` calls.
- No mnemonic phrase, private key, or other secret value is printed
  anywhere in this file.
