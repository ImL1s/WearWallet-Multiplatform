# Development

> This public repo (`ImL1s/WearWallet-public`) is the **canonical development
> tree**. See [`PUBLIC_BUILD.md`](./PUBLIC_BUILD.md) for clone, CI, and release
> facts and [`FEATURE_STATUS.md`](./FEATURE_STATUS.md) for capability claims.
> The private repo stays private **forever**. Do **not** use with real funds.

## Clone

```bash
git clone https://github.com/ImL1s/WearWallet-public.git
cd WearWallet-public
```

## Prerequisites

- JDK 17
- Android SDK 35 (for Wear / mobile modules)
- Optional: a GitHub token only if TrustWallet Core GitHub Packages returns 401.
  CI uses the job `GITHUB_TOKEN`, not a maintainer PAT. Tracked
  `gradle.properties` has no `github.token`.

## Build / test (local)

```bash
chmod +x gradlew
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest :wear:assembleDebug -PpublicSnapshot=true
```

Wear debug emulator overlay (not mainnet): [WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md).

Apple / watchOS Xcode builds are **not** CI-proven on this public tip.
Public CI is Wear `assembleDebug`, Markdown links, the release-manifest job,
and the PAT-fallback guard — not a full unit suite and not issue #30.

## Contributing

Open issues and PRs on
[ImL1s/WearWallet-public](https://github.com/ImL1s/WearWallet-public).
Security reports: see [SECURITY.md](../SECURITY.md) / GitHub Security Advisories.

Do not commit secrets, keystores, or production Firebase configs.
