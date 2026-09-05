# Development

> This public repo (`ImL1s/WearWallet-Multiplatform`) is the **canonical development
> tree**. See [`PUBLIC_BUILD.md`](./PUBLIC_BUILD.md) for clone, CI, and release
> facts and [`FEATURE_STATUS.md`](./FEATURE_STATUS.md) for capability claims.
> The private repo stays private **forever**. Do **not** use with real funds.

## Clone

```bash
git clone https://github.com/ImL1s/WearWallet-Multiplatform.git
cd WearWallet-Multiplatform
```

## Prerequisites

- JDK 17
- Android SDK 35 (for Wear / mobile modules). Local Gradle needs `sdk.dir` in
  ignored `local.properties` (copy [`local.properties.template`](../local.properties.template)
  or let Android Studio write it).
- Optional: a GitHub token only if TrustWallet Core GitHub Packages returns 401.
  CI uses the job `GITHUB_TOKEN`, not a maintainer PAT. Tracked
  `gradle.properties` has no `github.token`.

Service keys, `publicSnapshot`, Firebase examples, and optional Wear signing:
[API configuration](./API_CONFIGURATION.md). Clone / CI / tags:
[PUBLIC_BUILD.md](./PUBLIC_BUILD.md).

## Build / test (local)

```bash
chmod +x gradlew
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest :wear:assembleDebug -PpublicSnapshot=true
```

Install the Wear debug APK on an emulator or developer watch:
[WEAR_OS_INSTALL.md](./WEAR_OS_INSTALL.md)
([繁體中文](./WEAR_OS_INSTALL.zh-TW.md)). Debug emulator overlay (not
mainnet): [WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md).

Apple / watchOS Xcode builds are **not** CI-proven on this public tip.
Public CI includes the **Fail-closed unit slice** job (timeout 20 minutes),
Wear `assembleDebug`, Markdown links, the release-manifest job, and the
PAT-fallback guard — not a full unit suite, not 3-OS CI, and not issue #30.

## Contributing

Open issues and PRs on
[ImL1s/WearWallet-Multiplatform](https://github.com/ImL1s/WearWallet-Multiplatform).
Security reports: see [SECURITY.md](../SECURITY.md) / GitHub Security Advisories.

Do not commit secrets, keystores, or production Firebase configs.
