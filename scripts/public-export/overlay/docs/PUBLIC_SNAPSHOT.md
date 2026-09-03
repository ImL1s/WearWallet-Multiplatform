# Public snapshot provenance

This repository is a **sanitized, force-updated orphan tip** exported from the
private canonical WearWallet development repository. It is not a mirror of private
git history.

## What ships here

- A single `main` branch commit produced by `git archive` + blacklist + overlay.
- Flattened `modules/*` trees at pinned submodule SHAs (not git submodules).
- Public CI using `-PpublicSnapshot=true` (no production Firebase config).

## What does not ship here

- Private commit history, 1Password flows, Play upload keys, or self-hosted CI.
- Production `google-services.json`, keystores, or agent/local IDE metadata.

## Sync model

Maintainers export from a clean private worktree when all export guards pass.
Each sync replaces public `main` with one new root commit.

## Build

See [PUBLIC_BUILD.md](./PUBLIC_BUILD.md) and the root [README.md](../README.md).
