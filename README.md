> [!IMPORTANT]
> This is a **sanitized public snapshot** of WearWallet. Canonical development
> and full git history live in a private repository. Do **not** use with real
> funds. See [`docs/PUBLIC_BUILD.md`](./docs/PUBLIC_BUILD.md) and
> [`docs/PUBLIC_SNAPSHOT.md`](./docs/PUBLIC_SNAPSHOT.md).
>
> Private source tip at export: `a1a7f39`

<div align="center">

**English** | **[繁體中文](./docs/zh-TW/README.md)**

# WearWallet

![WearWallet banner](./docs/images/banner.png)

A wearable-first cryptocurrency wallet project spanning Wear OS, Android,
watchOS, and shared Kotlin Multiplatform code.

</div>

> [!WARNING]
> WearWallet is under active development. The repository contains partial and
> experimental wallet implementations and is not a security audit, release
> certification, or guarantee of safe use with real funds.

## Repository map

| Path | Purpose |
| --- | --- |
| [`wear/`](./wear/) | Wear OS application |
| [`mobile/`](./mobile/) | Android companion application |
| [`watchos/`](./watchos/) | Native watchOS application |
| [`coreKmp/`](./coreKmp/) | Shared Kotlin Multiplatform business, data, and security code |
| [`modules/`](./modules/) | Focused Kotlin libraries used by the applications and core module |

The active Gradle modules are defined in
[`settings.gradle.kts`](./settings.gradle.kts). Historical design and migration
documents may mention modules that no longer exist; use the current Gradle
configuration and source tree as the implementation source of truth.

## What is in the project

- Wear OS and Android Compose application modules
- A native SwiftUI watchOS application
- Kotlin Multiplatform targets for Android, iOS, and watchOS
- Wallet, address book, token, transaction, and price-related domain code
- QR-based Keystone integration components
- Public Ubuntu unit-test CI for coreKmp/wear (no Apple matrix on this snapshot)

Capability varies by chain, platform, and signing backend. See the
[`coreKmp` API map](./docs/COREKMP_API_OVERVIEW.md), current source, capability
gates, and exact-head tests before treating a feature as implemented or
production-ready.

## Quick start

### Prerequisites

- JDK 17
- Android SDK 35
- macOS and Xcode for Apple targets
- GitHub Packages credentials when dependency resolution requires them

The repository uses the checked-in Gradle 8.13 wrapper and Kotlin 2.2.21.

### Configure and build

```bash
git clone --recurse-submodules https://github.com/ImL1s/WearWallet-public.git
cd WearWallet

# If you cloned without submodules:
./scripts/init-submodules.sh

export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN

./gradlew :wear:assembleDebug
./gradlew :mobile:assembleDebug

# watchOS (macOS + Xcode + CocoaPods). Pods are not committed.
cd watchos && ./build-kmp.sh && open WearWallet.xcworkspace
```

The root `gradle.properties` file is tracked and contains shared build settings;
never add credentials to it. Do not commit `.env`, signing files, or API keys.
See the [`GitHub token guide`](./docs/PUBLIC_BUILD.md) and
[`development guide`](./docs/DEVELOPMENT_GUIDE.md) for the supported setup and
validation commands.

## Verification

Run the smallest checks that cover your change:

```bash
# Documentation
./scripts/check_markdown_links.py

# Shared core and Wear OS unit tests
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest

# Wear OS debug build
./gradlew :wear:assembleDebug
```

Apple target checks require macOS. Emulator, simulator, and automated test
results do not prove physical Keystone, watch, phone, or mainnet behavior.

## Documentation

Start at the **[documentation index](./docs/README.md)**.

- [Development guide](./docs/DEVELOPMENT_GUIDE.md)
- [Testing guide](./docs/TESTING_GUIDE.md)
- [Architecture](./docs/ARCHITECTURE.md)
- [Security design](./docs/SECURITY.md)
- [`coreKmp` overview](./coreKmp/README.md)
- [Contributing](./docs/CONTRIBUTING.md)
- [Roadmap](./docs/ROADMAP.md)
- [Changelog](./docs/en/CHANGELOG.md)

## Contributing

Contributions are welcome. Keep changes focused, include the validation you
ran, and state any unverified platform or hardware lane. Read
[`docs/CONTRIBUTING.md`](./docs/CONTRIBUTING.md) before opening a pull request.

## License

WearWallet is available under the [MIT License](./LICENSE).
