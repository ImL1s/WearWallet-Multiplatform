# WearWallet API 設定

<div align="center">

**[English](./API_CONFIGURATION.md)** | **繁體中文**

</div>

WearWallet 的依賴下載、Wear OS App 與 `coreKmp` 使用不同設定路徑。設定了 API
Key 不代表對應鏈、後端或硬體流程已支援。見
[FEATURE_STATUS.md](./FEATURE_STATUS.md)。

公開 clone 的 debug assemble **不需要**服務金鑰：

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

## 安全規則

- 根目錄 `gradle.properties` 是追蹤中的共用設定，禁止加入憑證。
- 本機服務值只能放在已忽略的 `local.properties`、環境變數或密碼管理器。
- 只檢查秘密值是否存在，不可把值輸出到 log。
- 使用最小權限；懷疑曝光時立即撤銷並輪替。
- `.env.example` 與 `local.properties.template` 只能保留 placeholder。

這個公開樹**沒有** 1Password 設定、`scripts/setup.sh` 或 Play Console
自動化。

## 本機檔案

| 檔案 | 是否追蹤 | 用途 |
| --- | --- | --- |
| [`local.properties.template`](../local.properties.template) | 是（placeholder） | 複製成已忽略的 `local.properties`：`sdk.dir`、Wear 小寫鍵、`coreKmp` 全大寫鍵 |
| [`.env.example`](../.env.example) | 是（placeholder） | 複製成已忽略的 `.env`：GitHub Packages 與 Wear 環境變數名稱 |
| [`gradle.properties.example`](../gradle.properties.example) | 是（placeholder） | 把需要的鍵放到**使用者層級** `~/.gradle/gradle.properties` |
| `wear/google-services.json.example` 與 `mobile/google-services.json.example` | 是（僅形狀） | 不可提交真實 `google-services.json` |
| 追蹤中的根目錄 `gradle.properties` | 是 | 共用 Gradle JVM／Android 旗標 — 不要放 token |

```bash
cp local.properties.template local.properties
# 設定 sdk.dir。Android Studio 開啟專案時也可能幫你寫這一行。
```

## 設定對照

| 使用端 | 支援來源 | 名稱 |
| --- | --- | --- |
| Android SDK | 已忽略的 `local.properties` | `sdk.dir` |
| 公開 snapshot／略過 Firebase | Gradle `-PpublicSnapshot=true` 或使用者層級 `publicSnapshot=true` | `publicSnapshot` |
| GitHub Packages | 環境變數或使用者層級 Gradle properties | `GITHUB_ACTOR`、`GITHUB_TOKEN`；`github.actor`、`github.token` |
| Wear OS BuildConfig | 環境變數或已忽略的 `local.properties`（小寫） | `INFURA_PROJECT_ID` / `infura.project.id`；`ETHERSCAN_API_KEY` / `etherscan.api.key`；`MORALIS_API_KEY` / `moralis.api.key` |
| Wear OS Google AI BuildConfig | **僅**環境變數或 Gradle property | `GOOGLE_AI_API_KEY`（不讀 `local.properties`） |
| `coreKmp` BuildKonfig | **僅**已忽略的 `local.properties` 全大寫 | 下方 `coreKmp` 清單 |
| Wear release 簽章 | 有設定時的 Gradle properties | `WEARWALLET_STORE_FILE`、`WEARWALLET_STORE_PASSWORD`、`WEARWALLET_KEY_ALIAS`、`WEARWALLET_KEY_PASSWORD` |

實作以 [`settings.gradle.kts`](../settings.gradle.kts)、
[`wear/build.gradle.kts`](../wear/build.gradle.kts) 與
[`coreKmp/build.gradle.kts`](../coreKmp/build.gradle.kts) 為準。

Wear 的 `INFURA_PROJECT_ID` / `infura.project.id` **不是** `coreKmp` 的
`INFURA_API_KEY`。填一邊不會自動填另一邊。

## GitHub Packages

短期 shell 建議使用環境變數：

```bash
export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN
./gradlew help
```

Token 權限只要 `read:packages`。Clone 與憑證說明見
[PUBLIC_BUILD.md](./PUBLIC_BUILD.md)。若要使用 Gradle property，放在
repository 外的 `~/.gradle/gradle.properties`；不可修改根目錄追蹤中的共用檔
來保存秘密。

公開 CI 可用選用 repo secret `GH_TOKEN_PACKAGES` 與 `GH_ACTOR_NAME`。兩者為空
時 workflow 改用 job `GITHUB_TOKEN`。Fork PR 不會把 `github.token` 寫進
`gradle.properties`。

## Wear OS 服務值

可在 Gradle 前匯出：

```bash
export INFURA_PROJECT_ID=YOUR_INFURA_PROJECT_ID
export ETHERSCAN_API_KEY=YOUR_ETHERSCAN_API_KEY
export MORALIS_API_KEY=YOUR_MORALIS_API_KEY
export GOOGLE_AI_API_KEY=YOUR_GOOGLE_AI_API_KEY
```

或把 Wear OS 使用的小寫名稱放入已忽略的 `local.properties`：

```properties
infura.project.id=YOUR_INFURA_PROJECT_ID
etherscan.api.key=YOUR_ETHERSCAN_API_KEY
moralis.api.key=YOUR_MORALIS_API_KEY
```

`GOOGLE_AI_API_KEY` 不從 `local.properties` 讀取。用環境變數或 Gradle
property。

只設定本次功能需要的服務。BuildConfig 有值或 APK 能組裝，不代表 live service
呼叫成功。

## `coreKmp` 服務值

`coreKmp` 目前讀取以下全大寫 `local.properties` 名稱：

- `INFURA_API_KEY`、`INFURA_HOLESKY_KEY`、`INFURA_POLYGON_KEY`
- `ETHERSCAN_API_KEY`、`POLYGONSCAN_API_KEY`、`ARBISCAN_API_KEY`
- `BASESCAN_API_KEY`、`OPTIMISM_API_KEY`、`BSCSCAN_API_KEY`
- `RANGO_API_KEY`、`ZEROX_API_KEY`、`MORALIS_API_KEY`
- `TRON_API_KEY`、`GETBLOCK_API_KEY`

不可假設同名環境變數一定會進入 BuildKonfig；以目前
`coreKmp/build.gradle.kts` 為準。

## Firebase / `publicSnapshot`

本樹只附 `wear/google-services.json.example` 與
`mobile/google-services.json.example`。公開 clone 的支援路徑會略過 Google
Services / Crashlytics / Performance：

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
./gradlew :mobile:assembleDebug -PpublicSnapshot=true
```

Android Studio **Run** 請把 `publicSnapshot=true` 放在使用者層級
`~/.gradle/gradle.properties`，並選 **`wear`** 模組。見
[WEAR_OS_INSTALL.md](./WEAR_OS_INSTALL.md)。

填好真實 `google-services.json` **不是**商店、Crashlytics 或 production
Firebase 證據。不可提交該檔。

## Wear release 簽章（選用）

`wear/build.gradle.kts` 只有在設定了 `WEARWALLET_STORE_FILE`（以及
`WEARWALLET_STORE_PASSWORD`、`WEARWALLET_KEY_ALIAS`、
`WEARWALLET_KEY_PASSWORD`）時才建立 release signing config。放到使用者層級
Gradle properties 或本機（已忽略）檔，不要寫進追蹤中的 `gradle.properties`。

這不是 Play Console 或商店上傳路徑。沒有這些屬性的 `:wear:assembleRelease`
仍然不是已簽章的 Play 產物。不可提交 keystore。

## 驗證界線

設定變更要分開記錄：

1. Gradle 設定或編譯結果。
2. 精確模組的自動化測試結果。
3. 模擬器證據。
4. 實體裝置或硬體錢包證據。
5. Testnet 或 mainnet 網路證據。

任何一條都不能代替其他證據路徑。
