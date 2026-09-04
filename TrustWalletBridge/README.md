# TrustWalletBridge source map

`TrustWalletBridge` is an experimental iOS-only CocoaPods bridge. It is **not**
a Gradle module, **not** watchOS-capable, and **not** a public CI input. The
watchOS companion Podfile target that referenced it is commented out.

It exposes a small Objective-C C-function surface backed by
`TrustWalletSwiftBridge.swift` and the `TrustWalletCore` pod.

> [!WARNING]
> This source tree is not an independent cryptographic audit, secure-memory
> guarantee, successful Pod build, application wiring proof, physical-device
> test, or release approval. Do not use it with real funds without validating
> the exact revision and every integration boundary.

## Current files

| File | Role |
| --- | --- |
| [`Classes/TrustWalletBridge.h`](./Classes/TrustWalletBridge.h) | Public C-function declarations visible to cinterop |
| [`Classes/TrustWalletBridge.m`](./Classes/TrustWalletBridge.m) | Objective-C forwarding implementation |
| [`Classes/TrustWalletSwiftBridge.swift`](./Classes/TrustWalletSwiftBridge.swift) | Swift wrapper around TrustWallet Core operations |
| [`TrustWalletBridge.podspec`](./TrustWalletBridge.podspec) | Local pod declaration |

The podspec currently declares iOS 13+, Swift 5, a static framework, and
`TrustWalletCore` 4.1.17. The `watchos/Podfile` reference is commented out, so
repository presence does not prove that an application target consumes this
pod.

## Public C functions

Use the names from `TrustWalletBridge.h`; do not infer Swift-style aliases.

```objective-c
NSString *signature = trustWalletSignEd25519(messageHex, privateKeyHex);
BOOL valid = trustWalletVerifyEd25519(messageHex, signatureHex, publicKeyHex);

NSString *mnemonic = TWBGenerateMnemonic(12);
BOOL mnemonicValid = TWBValidateMnemonic(mnemonic);

NSString *keccak = TWBHashKeccak256(dataHex);
NSString *sha256 = TWBHashSHA256(dataHex);
NSString *base58 = TWBBase58Encode(dataHex);
NSString *decodedHex = TWBBase58Decode(base58);
```

Nullable return values must be checked. Validate byte/hex lengths, supported
word counts, ownership, encoding, and error behavior at the caller boundary.

## Security and platform boundaries

- Objective-C `NSString` and Swift `String`/`Data` values can be copied. An
  autorelease pool is lifecycle management, not proof of secure zeroization.
- A local cleanup attempt for one Ed25519 buffer does not prove that mnemonic,
  entropy, seed, private-key, argument, return-value, or framework-internal
  copies are erased.
- The Objective-C type system does not make cryptographic inputs semantically
  safe or prevent secret exposure.
- The podspec targets iOS; it does not establish watchOS support.
- No mainnet, real-funds, device, application-entry-point, or release evidence
  was produced by this documentation cleanup.

## Validation before integration

1. Inspect the public header and Swift implementation at the exact commit.
2. Add focused known-answer and invalid-input tests for every function used.
3. Run `pod lib lint` and the consuming iOS build in a clean environment; do
   not treat the commands themselves as evidence that they pass.
4. Verify secret lifetime, logging, crash reporting, and error handling across
   Kotlin, Objective-C, Swift, and TrustWallet Core.
5. Record exact-head CI and physical-device evidence separately.

This module is covered by the repository [MIT License](../LICENSE).
