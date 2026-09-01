<div align="center">

**English** | **[繁體中文](./ARCHITECTURE.zh-TW.md)**

</div>

# WearWallet architecture

WearWallet combines native application shells with a shared Kotlin
Multiplatform core. This document describes the current repository shape, not a
future migration plan.

## Module overview

```text
Wear OS UI (:wear) ───────┐
Android UI (:mobile) ─────┼──> shared contracts and implementations (:coreKmp)
watchOS SwiftUI (:watchos)┘                │
                                          ├──> focused Kotlin modules (:modules:*)
                                          ├──> local persistence
                                          └──> external RPC/API/hardware boundaries
```

The active modules are declared in
[`settings.gradle.kts`](../settings.gradle.kts):

- `wear` — Wear OS application and Compose UI
- `mobile` — Android companion application and Compose UI
- `watchos` — native Apple application integration
- `coreKmp` — shared domain, data, security, network, and platform code
- `modules:*` — focused libraries for address, transaction, blockchain client,
  secure storage, UTXO, crypto, and CAIP concerns

There is no active `shared` or `sharedKmp` module.

## Layers

### Application and presentation

`wear`, `mobile`, and `watchos` own platform UI, navigation, lifecycle, and
device integration. They should consume shared contracts without moving
platform UI types into common code.

### Domain

`coreKmp/src/commonMain/.../domain` contains domain models, repository
contracts, services, and use cases. Domain APIs should describe wallet behavior
without depending on Android or SwiftUI types.

### Data and platform implementations

Repositories, SQLDelight integration, network clients, secure storage, and
native crypto are split between common code and platform source sets:

- `coreKmp/src/androidMain`
- `coreKmp/src/iosMain`
- `coreKmp/src/watchosMain`

The same common interface can have different dependency availability and
security properties on each platform. Parity must be tested, not assumed.

### External boundaries

Blockchain RPC providers, explorers, price services, GitHub Packages, and
Keystone QR exchange are outside the trust boundary. Code must treat remote
data, scanned payloads, and backend capability as untrusted inputs.

## Security architecture

Security-sensitive flows should follow this shape:

```text
user intent
  -> typed and validated request
  -> capability decision for platform/network/wallet/backend
  -> platform key or hardware-wallet boundary
  -> signed result
  -> explicit broadcast or persistence step
```

Important constraints:

- Production signing entry points must fail closed when a backend or capability
  is absent. The release capability gate implements deny decisions for requests
  that reach it; release evidence must prove the entry-point wiring.
- Private material must not cross logs, analytics, documentation, or test
  fixtures.
- Platform storage and crypto implementations are explicit source-set
  responsibilities.
- Tests must cover denied and unavailable paths, not only successful helpers.
- A declared adapter does not prove complete chain support.

See [`CapabilityRequest.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityRequest.kt)
and [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt)
for the current capability model.

## Data flow example

```text
UI event
  -> view model / presentation state
  -> domain use case
  -> repository or chain adapter
  -> platform storage / RPC / hardware boundary
  -> typed result or failure
  -> presentation state
```

Errors should remain typed across boundaries. UI code must not silently replace
a denied signing or unavailable backend result with a permissive fallback.

## Verification boundaries

- Common tests prove common behavior only.
- Android/JVM tests do not prove Kotlin/Native behavior.
- Simulator builds do not prove physical watch or phone behavior.
- QR fixtures do not prove physical Keystone interoperability.
- A successful build does not prove store signing, distribution, or mainnet
  behavior.

Record these lanes separately in CI, pull requests, and release notes.

## Source of truth

Use, in order:

1. Current source and module build files
2. Executed tests and exact-head CI
3. This maintained architecture overview
4. Point-in-time assessments, migration plans, and reports

Related documents:

- [Documentation index](./README.md)
- [`coreKmp` README](../coreKmp/README.md)
- [`coreKmp` API map](./COREKMP_API_OVERVIEW.md)
- [Security design](./SECURITY.md)
- [Testing guide](./TESTING_GUIDE.md)
