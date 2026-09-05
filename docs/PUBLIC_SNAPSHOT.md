# Public tree provenance

<div align="center">

**English** | **[繁體中文](./PUBLIC_SNAPSHOT.zh-TW.md)**

</div>

This public repo (`ImL1s/WearWallet-Multiplatform`) is the **canonical development
tree**. The private repo (`ImL1s/WearWallet`) is frozen as a historical / ops
vault **forever**. Do **not** force-export from private over this `main` as
ongoing sync.

This tree has **no private git ancestry**. It began as a sanitized orphan
export and is no longer a regularly-replaced snapshot of the private repo.
We will **not** rewrite private git history and push it onto this repository.
The original “make the private repo public after filter-repo” approach is
rejected.

## What ships here

- Flattened `modules/*` trees at pinned SHAs (not git submodules).
- Public CI using `-PpublicSnapshot=true` (no production Firebase config).
- Wear `assembleDebug`, the **Fail-closed unit slice** job (not a full unit
  suite), curated Markdown link checks, a release-manifest attack-surface
  job, and a PAT-fallback guard on GitHub-hosted Ubuntu.

## What does not ship here

- Private commit history, 1Password flows, Play upload keys, or self-hosted CI.
- Production `google-services.json`, keystores, or agent/local IDE metadata.
- A full unit suite in CI. The required unit slice is **still not** proof of
  issue #30, store review, mainnet safety, 3-OS CI, or complete test coverage.

## Build

See [PUBLIC_BUILD.md](./PUBLIC_BUILD.md) and the root [README.md](../README.md).
Do **not** use with real funds.

## Last sanitized export

- Private source tip: `8be876ef60d7d27418232a799f1c1a93aa3b0ca7`
- Export UTC time: `2026-09-04T02:16:23Z`
- Method: private-vault `scripts/export-public.sh` (blacklist + overlay +
  guards). That script is **not** part of this public tree.

This repository intentionally has **no private development history**.
This was the last intended overwrite of public `main` from private.
Consumers should not run any export script.
Root `export-manifest.json` was a generated guard/scanner dump from that
export. It is gitignored and is not source; do not commit it.
