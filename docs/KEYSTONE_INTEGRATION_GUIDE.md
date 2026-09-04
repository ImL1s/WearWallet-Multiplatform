# Keystone integration status and developer guide

WearWallet contains a Kotlin Multiplatform integration skeleton for exchanging
Keystone-compatible BC-UR payloads. Treat it as code under development, not as
proof of a complete or hardware-verified signing product.

> [!WARNING]
> Do not use this repository with real funds based on this guide. No physical
> Keystone, camera, watch, release, or mainnet evidence is established here.

## Current source map

| Area | Maintained source |
| --- | --- |
| Shared service contract | [`KeystoneService.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/service/KeystoneService.kt) |
| Shared UR contract | [`URProtocol.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/protocol/URProtocol.kt) |
| Shared flow coordinator | [`KeystoneManager.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/keystone/KeystoneManager.kt) |
| Models | [`KeystoneModels.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/model/keystone/KeystoneModels.kt) |
| Android service implementation | [`KeystoneService.kt`](../coreKmp/src/androidMain/kotlin/com/cbstudio/wearwallet/core/domain/service/KeystoneService.kt) |
| Android UR implementation | [`URProtocol.kt`](../coreKmp/src/androidMain/kotlin/com/cbstudio/wearwallet/core/domain/protocol/URProtocol.kt) |
| Extended-public-key policy | [`ExtendedPublicKeyPolicy.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/ExtendedPublicKeyPolicy.kt) |
| Release capability gate | [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt) |

The shared service contract exposes initialization, Ethereum signing-request
creation, signature parsing, HD-key parsing, wallet import, signing-request UR
creation, signing-response parsing, UR identification, and request-ID creation.
Callers must code against those actual methods rather than copied examples from
older reports.

## What the code establishes

- BC-UR encoding, decoding, format checks, and multipart handling have an
  expect/actual boundary.
- The Android implementation uses a BC-UR library for transport processing.
- `KeystoneManager` coordinates imported xpub data, repository state, and signing
  request state.
- `ExtendedPublicKeyPolicy` validates metadata such as fingerprint, derivation
  path, network, Base58Check data, depth, parent fingerprint, and compressed-key
  form.
- `ReleaseProductionCapabilityGate` evaluates a typed capability tuple and
  rejects unknown/unsupported contexts, unsupported chains, software identities
  requesting hardware-sign operations, and operations outside its allowlist.

## What the code does not establish

- UR or QR encoding is **not encryption, authentication, or signature
  verification** by itself.
- PSBT is a Bitcoin transaction format; do not describe it as an Ethereum
  signing format.
- `KeystoneManager.startSync()` currently returns a not-implemented failure.
- Interface or platform-source presence does not prove platform parity.
- No repository test proves a physical Keystone display, camera scan, watch
  relay, user confirmation, transaction broadcast, or real-network result.
- Hardware isolation remains a property to verify on the actual device and
  payload flow; it is not guaranteed by these Kotlin interfaces.

## Safe integration sequence

1. Parse the device's exported HD-key UR.
2. Validate its xpub metadata and the expected chain/network/path.
3. Store only the public account material required for a watch-only wallet.
4. Build an unsigned request and check the capability gate for the exact
   operation, network, platform, signer, wallet, backend, and build context.
5. Encode the request as UR and present it to the hardware device.
6. Parse the returned UR, verify request correlation and signature semantics,
   and display the final transaction fields again.
7. Treat broadcast as a separate capability and side effect.

Integration code should fail closed on malformed, incomplete,
unexpected-network, or uncorrelated data. Do not log payloads that can expose
account or transaction information.

## Verification

```bash
# Discover current tasks before copying a command into automation
./gradlew :coreKmp:tasks --all

# Android/JVM unit-test lane used by the module
./gradlew :coreKmp:testDebugUnitTest
```

The test suite includes UR and xpub-policy tests, but a green unit-test task is
not hardware evidence. Record unit tests, platform compilation, simulator,
physical device, hardware wallet, network, and release checks separately.

For historical context only, see the [Keystone archive](./archive/keystone/) and
[current troubleshooting notes](./keystone/KEYSTONE_TROUBLESHOOTING.md).
