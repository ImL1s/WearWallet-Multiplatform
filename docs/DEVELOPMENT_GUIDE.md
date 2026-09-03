# Development (public snapshot)

> This guide is trimmed for the **public snapshot**. See
> [`PUBLIC_BUILD.md`](./PUBLIC_BUILD.md) for clone, CI, and release facts.

## Clone

```bash
git clone https://github.com/ImL1s/WearWallet-public.git
cd WearWallet-public
```

## Prerequisites

- JDK 17
- Android SDK 35 (for Wear / mobile modules)
- Optional: personal `GITHUB_TOKEN` with `read:packages` for TrustWallet Core

## Build / test (local)

```bash
chmod +x gradlew
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest :wear:assembleDebug -PpublicSnapshot=true
```

Wear debug emulator overlay (not mainnet): [WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md).

Apple / watchOS Xcode builds are **not** CI-proven on this public tip.

## Contributing

Open issues and PRs on
[ImL1s/WearWallet-public](https://github.com/ImL1s/WearWallet-public).
Security reports: see [SECURITY.md](../SECURITY.md) / GitHub Security Advisories.

Do not commit secrets, keystores, or production Firebase configs.
