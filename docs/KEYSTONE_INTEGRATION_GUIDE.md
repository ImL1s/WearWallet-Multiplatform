# Keystone integration status and developer guide

<div align="center">

**English** | **[繁體中文](./KEYSTONE_INTEGRATION_GUIDE.zh-TW.md)**

</div>

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
./gradlew :coreKmp:tasks --all -PpublicSnapshot=true

# Android/JVM unit-test lane used by the module
./gradlew :coreKmp:testDebugUnitTest -PpublicSnapshot=true
```

The test suite includes UR and xpub-policy tests, but a green unit-test task is
not hardware evidence. Record unit tests, platform compilation, simulator,
physical device, hardware wallet, network, and release checks separately.

## Implementation pitfalls (not hardware evidence)

These are recurring encoding mismatches in the Kotlin/Android path. They do
**not** prove a physical Keystone, camera, or broadcast result. Verify against
current source rather than copied line numbers from older reports.

- **EIP-1559 `v` / yParity:** Keystone may return yParity `0`/`1`. Older web3j
  `Sign.getRecId()` rejected `v=0`. Convert using the current signing helper;
  do not assume a green parse is an on-chain success.
- **UR case and SDK type:** Ethereum signing requests should use the
  `eth-sign-request` / `KeystoneEthereumSDK` path, not an `evm-sign-request`
  mix-up. UR part case must match what the device firmware expects.
- **Request-id mismatch logs:** concurrent parsers can log an ID mismatch
  while another coroutine already consumed the signature. That log is a
  correlation guard, not proof the transaction failed or succeeded.
- **QR / scroll UI:** watch-sized Keystone screens must actually receive
  `qrCodeData` from the current ViewModel; a rendered scaffold is not a
  displayed UR.

Callers must still fail closed on malformed, uncorrelated, or unexpected-network
payloads. See the source map above.
