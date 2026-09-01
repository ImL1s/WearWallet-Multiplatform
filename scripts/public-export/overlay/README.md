<div align="center">

**English** | **[繁體中文](./docs/zh-TW/README.md)**

# WearWallet (public snapshot)

![WearWallet banner](./docs/images/banner.png)

A wearable-first cryptocurrency wallet project spanning Wear OS, Android,
watchOS, and shared Kotlin Multiplatform code.

</div>

> [!IMPORTANT]
> This is a **sanitized public snapshot** of WearWallet. Canonical development
> and full git history live in a private repository. This snapshot has **no
> private development history** — it is a single, regularly-replaced tip. Do
> **not** use with real funds. It is not a security audit, release
> certification, or guarantee of safe use. See
> [`docs/PUBLIC_BUILD.md`](./docs/PUBLIC_BUILD.md) and
> [`docs/PUBLIC_SNAPSHOT.md`](./docs/PUBLIC_SNAPSHOT.md) for how this snapshot
> is produced and what is intentionally left out.

## Repository map

| Path | Purpose |
| --- | --- |
| [`wear/`](./wear/) | Wear OS application |
| [`mobile/`](./mobile/) | Android companion application |
| [`watchos/`](./watchos/) | Native watchOS application |
| [`coreKmp/`](./coreKmp/) | Shared Kotlin Multiplatform business, data, and security code |
| [`modules/`](./modules/) | Focused Kotlin libraries used by the applications and core module (vendored as plain trees here — see below) |

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
- A public, Ubuntu-only unit-test CI workflow (`.github/workflows/ci.yml`) and
  a best-effort release-snapshot workflow (`.github/workflows/release.yml`)

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
git clone https://github.com/ImL1s/WearWallet-public.git
cd WearWallet-public
```

`modules/` in this snapshot is a **flattened, plain-tree vendoring** of each
library at the source commit used for this export — there is no
`.gitmodules` and nothing to `git submodule update`. If you need the
canonical, independently-versioned library repositories, see each library's
own upstream GitHub repository.

```bash
export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN

# Public snapshot builds never require google-services.json (or any Firebase
# config) — -PpublicSnapshot=true skips those plugins entirely.
./gradlew :wear:assembleDebug -PpublicSnapshot=true
./gradlew :mobile:assembleDebug -PpublicSnapshot=true

# watchOS (macOS + Xcode + CocoaPods). Pods are not committed.
cd watchos && ./build-kmp.sh && open WearWallet.xcworkspace
```

The root `gradle.properties` file is tracked and contains shared build settings;
never add credentials to it. Do not commit `.env`, signing files, or API keys.
See [`docs/PUBLIC_BUILD.md`](./docs/PUBLIC_BUILD.md) for the supported public
setup, credentials, and validation commands.

## Verification

Run the smallest checks that cover your change:

```bash
# Documentation
./scripts/check_markdown_links.py

# Shared core and Wear OS unit tests
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest -PpublicSnapshot=true

# Wear OS debug build
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

Apple target checks require macOS. Emulator, simulator, and automated test
results do not prove physical Keystone, watch, phone, or mainnet behavior.

## Documentation

Start at the **[documentation index](./docs/README.md)**.

- [Public build notes](./docs/PUBLIC_BUILD.md)
- [Public snapshot provenance](./docs/PUBLIC_SNAPSHOT.md)
- [Development guide](./docs/DEVELOPMENT_GUIDE.md)
- [`coreKmp` overview](./coreKmp/README.md)
- [Contributing](./docs/CONTRIBUTING.md)

## Contributing

Contributions are welcome. Keep changes focused, include the validation you
ran, and state any unverified platform or hardware lane. Read
[`docs/CONTRIBUTING.md`](./docs/CONTRIBUTING.md) before opening a pull request.
Note that this repository is a **generated snapshot**; large or structural
contributions are best discussed in an issue first.

## License

WearWallet is available under the [GNU GPL-3.0-or-later](./LICENSE). See [docs/LICENSING.md](./docs/LICENSING.md) for third-party license notes.
