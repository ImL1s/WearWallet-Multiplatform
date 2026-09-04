# Public build notes

This public repo (`ImL1s/WearWallet-public`) is the **canonical development
tree**. The private repo (`ImL1s/WearWallet`) is frozen as a historical / ops
vault. Do **not** force-export from private over this `main` as ongoing sync.

This tree has **no private git ancestry**. **Do not use with real funds.**

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
- There is no 1Password / Play Console automation in this tree.

## CI / CD

| Workflow | Trigger | What it actually gates |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push / PR to `main` | Ubuntu: Wear **debug** APK assemble + curated Markdown link check. The APK is uploaded as a workflow artifact (14 days). |
| `.github/workflows/release.yml` | tag `v*` or manual dispatch | Ubuntu: Wear debug APK + source tarball + `SHA256SUMS.txt` → GitHub **prerelease** |

Public CI does **not** run the full Wear/coreKmp unit suite (those jobs have
hung for 30–60+ minutes on GitHub-hosted Ubuntu). Assemble + markdown links
are **not** proof of issue #30, an Apple compile/link matrix, or a Play-signed
release. Run targeted Gradle tests locally with `-PpublicSnapshot=true`.

## Releases

Downloadable packages live on
[GitHub Releases](https://github.com/ImL1s/WearWallet-public/releases)
(prerelease). They are **debug / experimental**, not store uploads, and **not
for real funds**.

Cut a tag from this public repo after `main` already has the commit you want:

```bash
gh workflow run "Release Snapshot" --repo ImL1s/WearWallet-public \
  -f tag=v0.1.0-public.3
```

The workflow checks out current `main`, builds `:wear:assembleDebug -PpublicSnapshot=true`,
and publishes:

- `WearWallet-wear-<tag>-debug.apk`
- `WearWallet-public-<tag>-source.tar.gz`
- `SHA256SUMS.txt`

Wear debug emulator overlay (not mainnet): [WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md).

## Export tooling

`scripts/export-public.sh` is leftover sanitizer tooling from the last private
→ public orphan export. Do **not** run it as ongoing sync. Consumers should
not run it.
