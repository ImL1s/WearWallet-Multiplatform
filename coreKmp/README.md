# `coreKmp`

`coreKmp` is WearWallet's shared Kotlin Multiplatform module. It contains
cross-platform domain models, repositories, database code, network clients,
security policies, and blockchain adapters used by the application modules.

> [!IMPORTANT]
> The module is an active, partial implementation. Capability differs by
> platform, chain, network, wallet type, and signer backend. Do not infer
> production readiness from the presence of an adapter or API surface.

## Targets

The current Gradle configuration declares:

- Android
- iOS device and simulator targets
- watchOS device and simulator targets

Android and Apple targets do not have identical dependency or native-crypto
availability. Consult [`build.gradle.kts`](./build.gradle.kts) before changing
platform-specific behavior.

## Source layout

| Path | Responsibility |
| --- | --- |
| [`src/commonMain/`](./src/commonMain/) | Shared models, use cases, repositories, services, and policies |
| [`src/androidMain/`](./src/androidMain/) | Android implementations and integrations |
| [`src/iosMain/`](./src/iosMain/) | iOS implementations |
| [`src/watchosMain/`](./src/watchosMain/) | watchOS implementations |
| [`src/commonTest/`](./src/commonTest/) | Shared tests |
| [`src/androidUnitTest/`](./src/androidUnitTest/) | Android/JVM unit tests |

Key common packages live under
[`com.cbstudio.wearwallet.core`](./src/commonMain/kotlin/com/cbstudio/wearwallet/core/):

- `domain` and `data` for models, repositories, and use cases
- `database` and `network` for persistence and remote access
- `security` for crypto abstractions, key policies, and capability gates
- `blockchain`, `multichain`, and `caip` for chain-facing APIs and adapters
- `keystone` for QR-based hardware-wallet integration components

## Build and test

Run commands from the repository root:

```bash
# Android/JVM unit tests
./gradlew :coreKmp:testDebugUnitTest -PpublicSnapshot=true

# All configured KMP tests on the current host
./gradlew :coreKmp:allTests -PpublicSnapshot=true

# Android compilation
./gradlew :coreKmp:compileDebugKotlinAndroid -PpublicSnapshot=true
```

Apple compilation and framework tasks require macOS and Xcode. CI currently
uses target-specific tasks such as:

```bash
./gradlew \
  :coreKmp:compileKotlinIosSimulatorArm64 \
  :coreKmp:linkDebugFrameworkIosSimulatorArm64 \
  :coreKmp:compileKotlinWatchosSimulatorArm64 \
  :coreKmp:linkDebugFrameworkWatchosSimulatorArm64 \
  -PpublicSnapshot=true
```

Passing unit or simulator checks is not physical-device, hardware-wallet, or
mainnet proof.

## Current capability guidance

Before extending or calling a chain feature:

1. Read the [`BlockchainSDKAdapter`](./src/commonMain/kotlin/com/cbstudio/wearwallet/core/multichain/sdk/SDKAdapter.kt)
   contract and the concrete adapter.
2. Search the concrete platform source set for TODOs, fallbacks, and native
   dependency differences.
3. Confirm the relevant capability gate and signing path fail closed.
4. Add tests for the exact platform, network, wallet, and backend tuple.
5. Record any unverified physical or release lane in the pull request.

Historical assessments and status reports are archived. Inspect the current
source, adapters, platform source sets, capability gate, and exact-head tests
instead of relying on percentage scores or completion checklists.

## Related documentation

- [`coreKmp` API map](../docs/COREKMP_API_OVERVIEW.md)
- [Project testing guide](../docs/TESTING_GUIDE.md)

## License

This module is part of WearWallet and is covered by the repository's
[GNU GPL-3.0-or-later](../LICENSE).
