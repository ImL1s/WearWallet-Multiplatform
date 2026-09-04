# Third-party and vendored-module inventory

**This is an engineering inventory, not legal advice.** It does not
clear redistribution, prove license compatibility, or replace a lawyer
review. The product-capability truth remains
[`FEATURE_STATUS.md`](./FEATURE_STATUS.md).

Machine-readable copy: [`sbom/modules-inventory.json`](../sbom/modules-inventory.json).

## Root

| Path | SPDX / id | File |
| --- | --- | --- |
| WearWallet own source | `GPL-3.0-or-later` | [`LICENSE`](../LICENSE) |
| Attribution pointer | n/a | [`NOTICE`](../NOTICE) |

See also [`LICENSING.md`](./LICENSING.md). `NOTICE` is a summary pointer; it
does not relicense anything.

This public tree does **not** ship
`coreKmp/native/archived/secp256k1_c_source/COPYING`. Do not treat that
historical path as present.

## Flattened `modules/*`

These are plain-tree copies (no gitlinks). `in_settings_gradle` is whether
root [`settings.gradle.kts`](../settings.gradle.kts) includes the module.

| Path | License id | License file | Upstream (if known) | In Gradle graph | Capability |
| --- | --- | --- | --- | --- | --- |
| `modules/bitcoin-kmp` | `Apache-2.0` | `modules/bitcoin-kmp/LICENSE` | https://github.com/ACINQ/bitcoin-kmp | no | n/a (not included) |
| `modules/secp256k1-kmp` | `Apache-2.0` | `modules/secp256k1-kmp/LICENSE` | https://github.com/ACINQ/secp256k1-kmp | no | n/a (not included) |
| `modules/secp256k1-kmp/native/secp256k1` | `MIT` | `modules/secp256k1-kmp/native/secp256k1/COPYING` | https://github.com/bitcoin-core/secp256k1 | no | n/a |
| `modules/kotlin-utxo` | `Apache-2.0` | `modules/kotlin-utxo/LICENSE` | https://github.com/ImL1s/kotlin-utxo | yes | n/a |
| `modules/kotlin-crypto-pure` | `Apache-2.0` | `modules/kotlin-crypto-pure/LICENSE` | https://github.com/ImL1s/kotlin-crypto-pure | yes | n/a |
| `modules/kotlin-caip-standards` | `Apache-2.0` | `modules/kotlin-caip-standards/LICENSE` | https://github.com/ImL1s/kotlin-caip-standards | yes | n/a |
| `modules/kotlin-address` | **none in tree** | missing | https://github.com/ImL1s/kotlin-address | yes | `vendored_kotlin_address` = `UNSUPPORTED` |
| `modules/kotlin-tx-builder` | **none in tree** | missing | https://github.com/ImL1s/kotlin-tx-builder | yes | `vendored_kotlin_tx_builder` = `UNSUPPORTED` |
| `modules/kotlin-blockchain-client` | **none in tree** | missing | https://github.com/ImL1s/kotlin-blockchain-client | yes | `vendored_kotlin_blockchain_client` = `UNSUPPORTED` |
| `modules/kotlin-secure-storage` | **none in tree** | missing | https://github.com/ImL1s/kotlin-secure-storage | yes | `vendored_kotlin_secure_storage` = `UNSUPPORTED` |

README badges or “MIT License” / “Apache 2.0” lines are **not** treated as
license files. This inventory does not invent a license for a tree that
has none.

`kotlin-utxo`, `kotlin-crypto-pure`, and `kotlin-caip-standards` have
Apache-2.0 `LICENSE` files even though their README license sections say MIT.
The `LICENSE` file is what this inventory records.

## Not inventoried here

- Maven/Gradle plugin and library graphs (TrustWallet Core, Web3j, Signum,
  AndroidX, etc.)
- CocoaPods / Swift Package Manager inputs for watchOS
- Fonts, screenshots, token marks, and store graphics
- A CycloneDX/SPDX SBOM of a built APK

Those remain unresolved items. Missing them is **not** a claim that they
are cleared.

## Policy

If a flattened module has no redistributable license file, its corresponding
`WearCapability` is `UNSUPPORTED`. Runtime Wear send/receive/backup rows stay
at their existing maturity with that dependency called out; they are not
`PRODUCTION`.
