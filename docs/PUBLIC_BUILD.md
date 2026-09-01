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

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push / PR to `main` | Ubuntu: unit tests + Wear **debug** APK artifact |
| `.github/workflows/release.yml` | tag `v*` or manual dispatch | Ubuntu: tests + debug APK + source tarball → GitHub Release (prerelease) |

There is **no** Apple compile/link matrix and no Play-signed release on this
public tip.

## Releases

```bash
# Maintainers (after a public-sync tip is published):
git tag v0.1.0-public.1
git push origin v0.1.0-public.1
# or: gh workflow run "Release Snapshot" -f tag=v0.1.0-public.1
```

Assets are labeled **debug / experimental**. They are not store uploads.

## Sync

Consumers should **not** run `scripts/export-public.sh`. That tooling is for
maintainers of the private canonical tree.
