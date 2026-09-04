# Public export (legacy)

This directory powered a **one-way, fail-closed sanitized export** from the
private repo (`ImL1s/WearWallet`) to the public repo
(`ImL1s/WearWallet-public`, pinned by numeric repo id in
`scripts/export-public.sh`).

**Public `ImL1s/WearWallet-public` is now the canonical development tree.**
The private repo is frozen as a historical / ops vault. Do **not** treat
`export-public.sh --push` as ongoing sync over public `main`.

```
last sanitized export: private main (clean HEAD) --(export-public.sh)--> public main (orphan tip)
```

- Public development continues on `ImL1s/WearWallet-public`.
- The public repo has no private git ancestry (orphan tip).
- Credential vaults and store-upload ops stay out of the public tree.
- Every guard below either passes or aborts the **entire** export — there is
  no "warn and continue" path, for `--dry-run` or `--push`.

## Usage

```bash
# Requires: clean `git status --porcelain`, gitleaks installed, python3.
# Commit ALL changes (not just under scripts/public-export/) before running.

# Dry-run: build export dir + run every guard/scanner (no push)
./scripts/export-public.sh --dry-run

# Publish (requires `gh auth login`; verifies destination repo id/visibility/
# default branch, and that no unexpected public branches/tags exist)
./scripts/export-public.sh --push --repo ImL1s/WearWallet-public

# If the public repo intentionally has extra tags (reviewed), allow them:
./scripts/export-public.sh --push --allow-tags
```

See `HARDENING_STATUS.md` for the current pass/fail status of every guard
against the live private tree.

## Guards (fail the export)

- Dirty private worktree (`git status --porcelain` non-empty) — hard fail.
- Exporter script / blacklist / overlay / scanner configs must all come from
  committed `HEAD` (read via `git show`/`git archive`, never the raw working
  tree).
- Every submodule must be initialized, `HEAD` must equal the pinned gitlink
  SHA, and the export packs exactly that pinned SHA — no fallback to the
  submodule's own `HEAD`.
- `gitleaks` and `python3` must be installed (`brew install gitleaks`) — a
  missing tool aborts dry-run **and** push.
- `gitleaks` runs with the full default rule set (`generic-api-key` stays
  enabled) against the export tree; false positives are suppressed only via
  the narrow, per-finding `scripts/public-export/.gitleaksignore` fingerprints
  or the static `modules/**` path allowlist in `gitleaks.toml` (vendored,
  independently-public upstream submodules — see that file's comments).
- A BIP39-aware mnemonic scanner (`tools/bip39_scan.py`) walks the whole
  export tree for valid, checksum-verified 12/15/18/21/24-word mnemonics;
  anything not in `bip39-allowlist.json` by content sha256 fails the export.
  Phrases are never printed — only file:line and sha256.
- Expanded heuristic token regex: GitHub (`ghp_/gho_/…`), AWS, Slack, Google
  API keys, BIP-32 `xprv`/`tprv`, and WIF-format private keys, filtered
  through the reviewed `heuristic-allowlist.txt` exact-substring list.
- Real `google-services.json` (only `*.example` allowed), `.mobileprovision`,
  real `.env*`, GCP `service_account` JSON keys, `*.jks`/`*.keystore`/`*.p12`/
  PEM private-key blocks, `.gitmodules`, renovate/dependabot configs, agent
  scratch dirs (`.claude/.serena/.kiro`), self-hosted CI runner markers,
  1Password vault references, production Firebase project id, and known
  live-mnemonic marker names — all hard-fail if found.
- Push-only: destination repo id/visibility/default-branch pinned and
  verified via `gh api`; no public branch other than `main`; no public tag
  unless `--allow-tags`.
- `export-manifest.json` (path/size/sha256 per file + scanner versions +
  full guard ledger) is generated before every publish decision, and a
  completeness check hard-fails if any required guard is missing from the
  ledger.

## Overlay

Files under `scripts/public-export/overlay/` replace or add public-safe
content after the blacklist is applied — including the public `README.md`
and `CLAUDE.md` (the private root versions of both are blacklisted).
