# Public snapshot build notes

This repository is a **sanitized public snapshot**. Full history and private ops
live elsewhere. **Do not use with real funds.**

## Clone

```bash
git clone https://github.com/ImL1s/WearWallet-public.git
cd WearWallet-public
```

Modules are vendored as plain trees (no gitlinks). Do not expect
`git submodule update` to fetch private history.

## Credentials (local only)

- Copy `.env.example` → `.env` and/or use `gradle.properties.example` for local
  GitHub Packages credentials (`github.actor` / `github.token` with
  `read:packages`) if TrustWallet Core resolution requires it.
- Do **not** commit real Firebase `google-services.json`; use `*.example`.
- There is no 1Password / Play Console automation in this snapshot.

## CI / CD

| Workflow | Trigger | What it actually gates |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push / PR to `main` | Ubuntu: Wear **debug** APK assemble + curated Markdown link check. The APK is uploaded as a workflow artifact (14 days). |
| `.github/workflows/release.yml` | tag `v*` or manual dispatch | Ubuntu: Wear debug APK + source tarball + `SHA256SUMS.txt` → GitHub **prerelease** |

Public CI does **not** run the full Wear/coreKmp unit suite (those hang or belong on the private tree). There is **no** Apple compile/link matrix and no Play-signed release on this public tip.

## Releases

Downloadable packages live on
[GitHub Releases](https://github.com/ImL1s/WearWallet-public/releases)
(prerelease). They are **debug / experimental**, not store uploads, and **not
for real funds**.

Each public `main` rewrite is a new snapshot tip. Historical tags such as
`v0.1.0-public.1` stay as old tips; bump `N` after a successful
`./scripts/export-public.sh --push --allow-tags` from the private tree:

```bash
# From a clone of WearWallet-public, after main already has the new snapshot:
gh workflow run "Release Snapshot" --repo ImL1s/WearWallet-public \
  -f tag=v0.1.0-public.3
```

The workflow checks out current `main`, builds `:wear:assembleDebug -PpublicSnapshot=true`,
and publishes:

- `WearWallet-wear-<tag>-debug.apk`
- `WearWallet-public-<tag>-source.tar.gz`
- `SHA256SUMS.txt`

Wear debug emulator overlay (not mainnet): [WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md).

## Sync

Consumers should **not** run `scripts/export-public.sh`. That tooling is for
maintainers of the private canonical tree.
