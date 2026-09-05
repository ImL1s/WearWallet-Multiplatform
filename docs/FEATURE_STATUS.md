# Feature status

<div align="center">

**English** | **[繁體中文](./FEATURE_STATUS.zh-TW.md)**

</div>

This is the **only** public list of WearWallet product claims. Code, screenshots,
store copy, and other docs must not describe a capability as supported unless
this matrix says so. The Kotlin registry is
`wear/.../feature/WearCapability.kt` (`FeatureMaturity` enum). The two must
match; `ReleaseFeatureGateTest` checks that.

Do **not** use real funds. Nothing in this tree is `PRODUCTION`. A debug APK,
unit test, or screenshot is not mainnet, hardware, or store evidence.

## Status values

| Status | Meaning | Release Wear navigation |
| --- | --- | --- |
| `PRODUCTION` | Supported, tested, documented, release-gated | Reachable |
| `BETA` | Usable with explicit limitations | Reachable (fail-closed on sign/broadcast) |
| `EXPERIMENTAL` | Incomplete; not a release promise | Reachable only as labeled experimental UI |
| `MAINTENANCE` | Placeholder / disabled | **Omitted** |
| `DEMO` | Fake data; must not touch real funds | **Omitted** |
| `UNSUPPORTED` | No runtime success path | **Omitted** |

Unknown or missing status defaults to unavailable. A remote flag may disable a
capability; it must not enable `MAINTENANCE` / `DEMO` / `UNSUPPORTED` code in a
release binary.

## Matrix

| id | Surface | Maturity | Release Wear nav | Funds | Limitations |
| --- | --- | --- | --- | --- | --- |
| `wear_send` | Wear OS send | `BETA` | Yes | Signing possible only if a capability gate allows it | EIP-55 mixed-case checksum is enforced (all-lower/all-upper accepted), including a `proceedToConfirm` re-check. Missing/invalid gas fails closed (no 21000/20 Gwei fallback; no silent 500 Gwei cap). Valid sub-1 Gwei Wei is a decimal Gwei string, not fail-closed. Software send and Keystone submitted hashes are **PENDING/BROADCASTED**, not on-chain confirmation. Replaced/Dropped remain `UNSUPPORTED`. Depends on vendored trees that lack LICENSE files (`vendored_kotlin_address`, `vendored_kotlin_tx_builder`, `vendored_kotlin_blockchain_client`) — those redistribution claims stay `UNSUPPORTED`. No mainnet proof — not `PRODUCTION`. Public RPC/API hardening is not done; Wear round-screen UX is not a PRODUCTION journey. |
| `wear_receive` | Wear OS receive / address QR | `BETA` | Yes | Display only | Emulator QA overlay is not mainnet data. Depends on `vendored_kotlin_address` (no LICENSE in tree). No production certification. Wear UX and accessibility are not PRODUCTION gates. |
| `wallet_backup_create_import` | Create, import, backup / reveal mnemonic | `BETA` | Yes | Key material | Fail-closed under `ReleaseProductionCapabilityGate` for restricted tuples. Reveal/export is sensitive; not a store-ready backup product. Depends on `vendored_kotlin_secure_storage` (no LICENSE in tree). Create/import/backup/reveal UX is not PRODUCTION. |
| `keystone` | QR Keystone connect / sign | `EXPERIMENTAL` | Yes | Hardware sign request | Components exist. No physical Keystone interoperability evidence in this public tree. Physical Keystone E2E is not claimed. |
| `swap` | Wear swap UI | `EXPERIMENTAL` | Yes | Would move funds if allowed | Release capability gate fail-closes mainnet software paths. Not a DeFi product. |
| `wear_fi` | WearFi health mining | `MAINTENANCE` | **Omitted** | No | Maintenance placeholders only. |
| `nfc` | NFC tap-to-sign, wrist transfer, NFC pay | `MAINTENANCE` | **Omitted** (`nfc_payment`, `wrist_transfer`) | No in release | Debug may still register wrist-transfer. Not PRODUCTION. |
| `debit_card` | Crypto debit card UI | `MAINTENANCE` | **Omitted** | No | Placeholder service. |
| `ai_assistant` | AI assistant / investment advisor | `MAINTENANCE` | **Omitted** (`ai_assistant`, `ai_investment_advisor`) | No | Debug settings entry only. Gemini Live / mic FGS are debug-only (Task B). |
| `defi_one_click` | DeFi one-click | `MAINTENANCE` | **Omitted** | No | Route constant only; screen not implemented. |
| `direct_kmp` | Empty `DirectKmpModule` | `MAINTENANCE` | n/a | No | Must not load in `getAllWearModules()`. |
| `watchos` | Native watchOS app | `EXPERIMENTAL` | n/a | Unproven | Source exists. Public CI does not prove Xcode link, launch, or physical watch. Cross-OS parity is not closed; there is no 3-OS CI. |
| `mobile_companion` | Android companion | `EXPERIMENTAL` | n/a | Unproven | Module exists. Not a verified phone wallet or Wear relay product. Cross-OS parity is not closed. |
| `broadcast` | Broadcast as confirmed send | `UNSUPPORTED` | n/a | Must not claim success | Default `allowBroadcast=false`. Broadcast ≠ confirmed. Mainnet RPC confirmation is not implemented. |
| `mainnet_software_sign` | Mainnet software sign | `UNSUPPORTED` | n/a | Denied | `ReleaseProductionCapabilityGate(allowEvmMainnetSend=false)` denies this path. |
| `vendored_kotlin_address` | Flattened `modules/kotlin-address` | `UNSUPPORTED` | n/a | No redistributable claim | No LICENSE file in the vendored tree. README says “MIT License”; that is not a license file. Do not invent a license. See [THIRD_PARTY.md](./THIRD_PARTY.md). |
| `vendored_kotlin_tx_builder` | Flattened `modules/kotlin-tx-builder` | `UNSUPPORTED` | n/a | No redistributable claim | No LICENSE file. README badge links to Apache-2.0; that is not a license file. Do not invent a license. |
| `vendored_kotlin_blockchain_client` | Flattened `modules/kotlin-blockchain-client` | `UNSUPPORTED` | n/a | No redistributable claim | No LICENSE file. README says “MIT License”; that is not a license file. Do not invent a license. |
| `vendored_kotlin_secure_storage` | Flattened `modules/kotlin-secure-storage` | `UNSUPPORTED` | n/a | No redistributable claim | No LICENSE file. README badge links to Apache-2.0; that is not a license file. Do not invent a license. |

## Release navigation gate

Wear `walletNavigation(isRelease = !BuildConfig.DEBUG)` omits composables for
`WalletRoute.WEAR_FI`, `DEBIT_CARD`, `AI_ASSISTANT`, `DEFI_ONE_CLICK`,
`AI_INVESTMENT_ADVISOR`, `NFC_PAYMENT`, and `WRIST_TRANSFER`. Settings does not
show AI assistant in release. Direct navigation to an omitted route must not
present a fund-moving screen.

## P1 product follow-ups (not PRODUCTION)

Public issues #9–#14 (RPC/API, physical Keystone E2E, platform parity, Wear
UX, create/import/backup UX, accessibility) stay fail-closed in this matrix.
They are **not** closed as product-complete. Follow-up welcome.

Tests:

```bash
./gradlew :wear:testDebugUnitTest \
  --tests 'com.cbstudio.wearwallet.feature.WalletNavigationReleaseGateTest' \
  --tests 'com.cbstudio.wearwallet.feature.ReleaseFeatureGateTest' \
  -PpublicSnapshot=true
```
