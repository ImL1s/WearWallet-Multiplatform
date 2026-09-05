# `coreKmp` API map

<div align="center">

**English** | **[繁體中文](./COREKMP_API_OVERVIEW.zh-TW.md)**

</div>

This document is a navigation aid for the current `coreKmp` source tree. It is
not a generated API reference or a promise that every declared adapter is
complete on every platform.

## Primary entry points

| Area | Source |
| --- | --- |
| Chain adapter contract and shared models | [`SDKAdapter.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/multichain/sdk/SDKAdapter.kt) |
| Capability requests and platform/backend identity | [`CapabilityRequest.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityRequest.kt) |
| Fail-closed capability decisions | [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt) |
| Platform crypto abstraction | [`CryptoProvider.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CryptoProvider.kt) |
| Wallet creation flow | [`CreateWalletUseCase.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/wallet/CreateWalletUseCase.kt) |
| Wallet import flow | [`ImportWalletUseCase.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/wallet/ImportWalletUseCase.kt) |
| CAIP models and normalization | [`CAIPStandardization.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/caip/CAIPStandardization.kt) |

## Package map

All paths below are under
[`coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/).

| Package | Responsibility |
| --- | --- |
| `domain` | Models, repository contracts, services, and use cases |
| `data` | Repository implementations and mappers |
| `database` | SQLDelight-facing persistence, migration, and optimization code |
| `network` | Shared networking infrastructure |
| `security` | Crypto abstractions, key handling, capability gates, and policies |
| `blockchain` | RPC, signer, transaction, explorer, and UTXO components |
| `multichain` | Adapter, portfolio, DeFi, bridge, and chain-service code |
| `caip` | Chain-agnostic identifiers and adapter integration |
| `keystone` | QR-based Keystone integration components |

## Platform implementations

Common contracts are implemented or extended by platform source sets:

- [Android](../coreKmp/src/androidMain/)
- [iOS](../coreKmp/src/iosMain/)
- [watchOS](../coreKmp/src/watchosMain/)

Do not assume behavioral parity between those directories. Native libraries,
secure storage, key availability, and signing backends differ by target.

## Using an API safely

1. Start from the common interface or use case.
2. Inspect the concrete adapter and the target platform implementation.
3. Check for TODOs, fallback behavior, unsupported capabilities, and network
   assumptions.
4. Confirm the requested operation passes the intended capability gate.
5. Add a focused test for the exact target and failure mode.

Production signing entry points must use fail-closed decisions. The release gate
implements those decisions for requests that reach it, but exact-head release
evidence still has to prove the entry-point wiring. Do not bypass the gate to
make an example or test pass.

## Generated API documentation

The current Gradle project does not expose a `coreKmp` Dokka task. This source
map is therefore the maintained navigation entry point; do not document a
generated API command until the corresponding plugin and task exist and are
verified in CI.

## Status references

- [`coreKmp` README](../coreKmp/README.md)
- [Project architecture](./ARCHITECTURE.md)
- [Testing guide](./TESTING_GUIDE.md)
