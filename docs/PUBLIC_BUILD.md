# Public build notes

This public repo (`ImL1s/WearWallet-public`) is the **canonical development
tree**. The private repo (`ImL1s/WearWallet`) is frozen as a historical / ops
vault **forever**. Do **not** force-export from private over this `main` as
ongoing sync. Do **not** rewrite private git history and push it here.

This tree has **no private git ancestry**. **Do not use with real funds.**

## Clone

```bash
git clone https://github.com/ImL1s/WearWallet-public.git
cd WearWallet-public
```

Modules are vendored as plain trees (no gitlinks). Do not expect
`git submodule update` to fetch private history.

## Tokenless debug assemble

The tracked `gradle.properties` has **no** `github.token`. The documented
local check is:

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

Public CI does the same with `-PpublicSnapshot=true`. When
`secrets.GH_TOKEN_PACKAGES` is empty, the workflow uses the job
`GITHUB_TOKEN` and **does not** write maintainer `github.token` into
`gradle.properties` for **fork PRs**. Guard:

```bash
python3 scripts/tests/test_check_ci_pat_fallback.py
python3 scripts/check_ci_pat_fallback.py
```

### Remaining limitation (public #6 stays closed with this named)

TrustWallet Core is still resolved from GitHub Packages
(`https://maven.pkg.github.com/trustwallet/wallet-core`). That registry
**still can 401 without a token**. The job `GITHUB_TOKEN` is enough in
this repo's CI; a maintainer PAT is not required. A fully anonymous
clean clone (empty `GITHUB_TOKEN` / `github.token`, empty Gradle
dependency cache) can still fail resolution.

A warm local Gradle cache can make `:wear:assembleDebug` succeed with
empty `-Pgithub.token=` / `-Pgithub.actor=`. That is **not** anonymous
clean-clone proof. Verified without a cached `com.trustwallet:wallet-core`
artifact: Gradle `Could not GET ... Received status code 401`; an
unauthenticated HTTP GET of the same POM also returns **401**. This tree
does **not** vendor Wallet Core.

Optional local packages credentials live only in untracked
`gradle.properties.example` / `.env` / user Gradle properties — never in
the tracked `gradle.properties`.

- Do **not** commit real Firebase `google-services.json`; use `*.example`.
- There is no 1Password / Play Console automation in this tree.

## CI / CD

| Workflow | Trigger | What it actually gates |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push / PR to `main` | Ubuntu: **Fail-closed unit slice** (timeout 20 minutes) — Wear `ReleaseFeatureGateTest` + `WalletNavigationReleaseGateTest` and coreKmp `EvmRecipientAddressPolicyTest` + `EvmBroadcastOutcomeTest`; Wear **debug** APK assemble/upload; curated Markdown link check; release-manifest attack-surface job; PAT-fallback guard. |
| `.github/workflows/release.yml` | tag `v*` or manual dispatch | Ubuntu: Wear debug APK + source tarball + `SHA256SUMS.txt` → GitHub **prerelease** |

The **Fail-closed unit slice** job is the required unit slice. It is **still
not** private-grade issue #30 completeness: no 3-OS matrix, no full
`:wear:testDebugUnitTest`, no coverage/SAST-as-complete, no Play-signed
release. Public CI does **not** run the full Wear/coreKmp unit suite (those
jobs have hung for 30–60+ minutes on GitHub-hosted Ubuntu). Assemble +
markdown links + the release-manifest job are **not** an Apple compile/link
matrix. Run targeted Gradle tests locally with `-PpublicSnapshot=true`.

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
not run it. The original “filter-repo the private repo then make it public”
approach is rejected. The generated root `export-manifest.json` dump is
gitignored and is not source.
