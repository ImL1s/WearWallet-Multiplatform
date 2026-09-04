<div align="center">

**[English](./SECURITY.md)** | **[繁體中文](./SECURITY.zh-TW.md)**

</div>

# Security boundaries

This document maps security-relevant code and its current evidence boundaries.
It is not a security audit, certification, release approval, or assurance that
WearWallet is safe for real funds.

## Security-relevant source

| Boundary | Source |
| --- | --- |
| Typed operation context | [`CapabilityRequest.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityRequest.kt) |
| Release/development decisions | [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt) |
| Cryptographic contract | [`CryptoProvider.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CryptoProvider.kt) |
| Private-key handling flow | [`PrivateKeyManager.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/PrivateKeyManager.kt) |
| Platform secure-key contract | [`SecureKeyManager.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/SecureKeyManager.kt) |
| xpub validation policy | [`ExtendedPublicKeyPolicy.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/ExtendedPublicKeyPolicy.kt) |
| Side-effect observation | [`SideEffectTracker.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/SideEffectTracker.kt) |

These abstractions and partial implementations do not prove that every call site,
platform, build type, or release artifact enforces the same controls.

## Capability decisions

`ReleaseProductionCapabilityGate` evaluates a typed tuple containing operation,
chain, network, platform, build type, envelope, signer implementation, wallet
type, backend identity and availability, backend version, and smoke-vector
state. The implementation includes explicit rejection paths for unknown or
unsupported values, software identities requesting hardware signing, chains
outside its allowlist, broadcast when disabled, and restricted mainnet software
operations.

This is a code-level policy. Release evidence must also prove that production
entry points construct the correct request and actually use the release gate.
Signing permission must never be interpreted as broadcast permission.

## Key and storage boundaries

- `CryptoProvider`, `PrivateKeyManager`, and `SecureKeyManager` define key,
  encryption, storage, and signing responsibilities.
- Platform implementations differ. Interface presence is not proof of hardware
  backing, biometric enforcement, secure deletion, or equivalent behavior on
  Android, iOS, and watchOS.
- Some Apple-platform cryptographic paths intentionally fail closed or remain
  deferred; watchOS storage includes placeholder behavior. Do not claim complete
  cross-platform encryption until those paths have direct tests and platform
  evidence.
- Mnemonics, private keys, signing material, API credentials, and real payloads
  must never be committed or printed in logs.

## Keystone and transport boundaries

BC-UR and QR codes provide transport encoding and fragmentation. They do not
inherently provide confidentiality, authentication, or signature verification.
Validate the expected UR type, network, derivation metadata, request identity,
transaction contents, and returned signature independently. See the
[Keystone integration guide](./KEYSTONE_INTEGRATION_GUIDE.md).

Do not claim that certificate pinning is globally enforced. A configuration
helper or sample is not evidence that every active HTTP client uses current
pins. Verify the instantiated client and live failure behavior for each release.

## Credential handling

- GitHub Packages credentials are required when the build resolves private
  packages.
- Service API keys such as Infura, Moralis, and explorer keys are optional until
  their associated network feature is exercised.
- Keep local values in environment variables, ignored `.env`, or an ignored
  `local.properties`; never place them in tracked `gradle.properties`.
- Review [API configuration](./API_CONFIGURATION.md). This public tree does
  not ship 1Password setup or other private credential-management tooling.

## Verification lanes

Run the smallest relevant checks, then retain exact-head evidence:

```bash
./gradlew :coreKmp:testDebugUnitTest -PpublicSnapshot=true
./gradlew :wear:testDebugUnitTest -PpublicSnapshot=true
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

A release decision additionally needs targeted security tests, dependency and
secret scanning, platform builds, side-effect checks, real device/hardware
validation where applicable, signed-artifact inspection, CI for the exact commit,
and review of unresolved findings. Missing credentials or hardware must be
reported as an unverified lane, never converted into a pass.

Historical implementation claims from the private vault are **not** shipped
here. Treat current source, `settings.gradle.kts`, and exact-head CI as the
implementation source of truth.
