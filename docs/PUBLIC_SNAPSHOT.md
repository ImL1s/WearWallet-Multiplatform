# Public tree provenance

This public repo (`ImL1s/WearWallet-Multiplatform`) is the **canonical development
tree**. The private repo (`ImL1s/WearWallet`) is frozen as a historical / ops
vault. Do **not** force-export from private over this `main` as ongoing sync.

This tree has **no private git ancestry**. It began as a sanitized orphan
export and is no longer a regularly-replaced snapshot of the private repo.

## What ships here

- Flattened `modules/*` trees at pinned SHAs (not git submodules).
- Public CI using `-PpublicSnapshot=true` (no production Firebase config).
- Wear `assembleDebug` plus curated Markdown link checks on GitHub-hosted Ubuntu.

## What does not ship here

- Private commit history, 1Password flows, Play upload keys, or self-hosted CI.
- Production `google-services.json`, keystores, or agent/local IDE metadata.
- A full unit suite in CI. Assemble + markdown links are **not** proof of
  issue #30, store review, mainnet safety, or complete test coverage.

## Build

See [PUBLIC_BUILD.md](./PUBLIC_BUILD.md) and the root [README.md](../README.md).
Do **not** use with real funds.

## Last sanitized export

- Private source tip: `8be876ef60d7d27418232a799f1c1a93aa3b0ca7`
- Export UTC time: `2026-09-04T02:16:23Z`
- Method: `scripts/export-public.sh` (blacklist + overlay + guards)

This repository intentionally has **no private development history**.
This was the last intended overwrite of public `main` from private.
Consumers should not run the export script.
See `export-manifest.json` at the repo root for the full guard/scanner
ledger and a sha256 of every shipped file.
