# 第三方與 vendor 模組清冊

<div align="center">

**[English](./THIRD_PARTY.md)** | **繁體中文**

</div>

**這是工程清冊，不是法律意見。** 它不清場散布權、不證明授權相容，也不能取代
律師審查。產品能力仍以 [`FEATURE_STATUS.md`](./FEATURE_STATUS.md) 為準。

機器可讀副本：[`sbom/modules-inventory.json`](../sbom/modules-inventory.json)。

## 根目錄

| 路徑 | SPDX / id | 檔案 |
| --- | --- | --- |
| WearWallet 自身原始碼 | `GPL-3.0-or-later` | [`LICENSE`](../LICENSE) |
| 歸屬指標 | n/a | [`NOTICE`](../NOTICE) |

另見 [`LICENSING.md`](./LICENSING.md)。`NOTICE` 是摘要指標，不重新授權任何東西。

這個公開樹 **沒有**
`coreKmp/native/archived/secp256k1_c_source/COPYING`。不要把那條歷史路徑當成
還在。

## 平面 `modules/*`

這些是平面目錄副本（無 gitlink）。`in_settings_gradle` 表示根目錄
[`settings.gradle.kts`](../settings.gradle.kts) 有沒有 include。

| 路徑 | 授權 id | 授權檔 | 上游（若已知） | 在 Gradle 圖 | 能力 |
| --- | --- | --- | --- | --- | --- |
| `modules/bitcoin-kmp` | `Apache-2.0` | `modules/bitcoin-kmp/LICENSE` | https://github.com/ACINQ/bitcoin-kmp | 否 | n/a（未 include） |
| `modules/secp256k1-kmp` | `Apache-2.0` | `modules/secp256k1-kmp/LICENSE` | https://github.com/ACINQ/secp256k1-kmp | 否 | n/a（未 include） |
| `modules/secp256k1-kmp/native/secp256k1` | `MIT` | `modules/secp256k1-kmp/native/secp256k1/COPYING` | https://github.com/bitcoin-core/secp256k1 | 否 | n/a |
| `modules/kotlin-utxo` | `Apache-2.0` | `modules/kotlin-utxo/LICENSE` | https://github.com/ImL1s/kotlin-utxo | 是 | n/a |
| `modules/kotlin-crypto-pure` | `Apache-2.0` | `modules/kotlin-crypto-pure/LICENSE` | https://github.com/ImL1s/kotlin-crypto-pure | 是 | n/a |
| `modules/kotlin-caip-standards` | `Apache-2.0` | `modules/kotlin-caip-standards/LICENSE` | https://github.com/ImL1s/kotlin-caip-standards | 是 | n/a |
| `modules/kotlin-address` | **樹內沒有** | 缺 | https://github.com/ImL1s/kotlin-address | 是 | `vendored_kotlin_address` = `UNSUPPORTED` |
| `modules/kotlin-tx-builder` | **樹內沒有** | 缺 | https://github.com/ImL1s/kotlin-tx-builder | 是 | `vendored_kotlin_tx_builder` = `UNSUPPORTED` |
| `modules/kotlin-blockchain-client` | **樹內沒有** | 缺 | https://github.com/ImL1s/kotlin-blockchain-client | 是 | `vendored_kotlin_blockchain_client` = `UNSUPPORTED` |
| `modules/kotlin-secure-storage` | **樹內沒有** | 缺 | https://github.com/ImL1s/kotlin-secure-storage | 是 | `vendored_kotlin_secure_storage` = `UNSUPPORTED` |

README badge 或「MIT License」／「Apache 2.0」字句 **不**當成授權檔。這個清冊
不為沒有授權檔的樹發明授權。

`kotlin-utxo`、`kotlin-crypto-pure` 與 `kotlin-caip-standards` 有 Apache-2.0
`LICENSE` 檔，即使 README 授權段寫 MIT。本清冊以 `LICENSE` 檔為準。

## 這裡沒盤點的

- Maven／Gradle plugin 與函式庫圖（TrustWallet Core、Web3j、Signum、
  AndroidX 等）
- watchOS 的 CocoaPods／Swift Package Manager 輸入
- 字型、截圖、代幣標記與商店圖
- 建好的 APK 的 CycloneDX／SPDX SBOM

那些仍是未決項目。缺它們 **不是**宣稱已清場。

## 政策

若平面模組沒有可散布的授權檔，對應 `WearCapability` 為 `UNSUPPORTED`。執行期
Wear send／receive／backup 列維持既有成熟度並標出該依賴；它們不是
`PRODUCTION`。
