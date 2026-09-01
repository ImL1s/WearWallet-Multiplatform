# WearWallet API 設定

<div align="center">

**[English](./API_CONFIGURATION.md)** | **繁體中文**

</div>

WearWallet 的依賴下載、Wear OS App 與 `coreKmp` 使用不同設定路徑。設定了 API
Key 不代表對應鏈、後端或硬體流程已達 production-ready。

## 安全規則

- 根目錄 `gradle.properties` 是追蹤中的共用設定，禁止加入憑證。
- 本機服務值只能放在已忽略的 `local.properties`、環境變數或密碼管理器。
- 只檢查秘密值是否存在，不可把值輸出到 log。
- 使用最小權限；懷疑曝光時立即撤銷並輪替。
- `.env.example` 與 `local.properties.template` 只能保留 placeholder。

## 設定對照

| 使用端 | 支援來源 | 名稱 |
| --- | --- | --- |
| GitHub Packages | 環境變數或使用者層級 Gradle properties | `GITHUB_ACTOR`、`GITHUB_TOKEN`；`github.actor`、`github.token` |
| Wear OS 建置 | 環境變數或已忽略的 `local.properties` | `INFURA_PROJECT_ID` / `infura.project.id`；`ETHERSCAN_API_KEY` / `etherscan.api.key`；`MORALIS_API_KEY` / `moralis.api.key` |
| Wear OS Google AI build field | 環境變數或 Gradle property | `GOOGLE_AI_API_KEY` |
| `coreKmp` BuildKonfig | 已忽略的 `local.properties` | `local.properties.template` 內的全大寫名稱 |

實作以 [`settings.gradle.kts`](../settings.gradle.kts)、
[`wear/build.gradle.kts`](../wear/build.gradle.kts)與
[`coreKmp/build.gradle.kts`](../coreKmp/build.gradle.kts)為準。

## GitHub Packages

短期 shell 建議使用環境變數：

```bash
export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN
./gradlew help
```

詳見 [GitHub Packages 憑證設定](./GITHUB_TOKEN_SETUP.md)。若要使用 Gradle
property，放在 repository 外的 `~/.gradle/gradle.properties`；不可修改根目錄
追蹤中的共用檔來保存秘密。

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

只設定本次功能需要的服務。BuildConfig 有值或 APK 能組裝，不代表 live service
呼叫成功。

## `coreKmp` 服務值

從追蹤中的 placeholder 範本建立本機檔案：

```bash
cp local.properties.template local.properties
# 設定 sdk.dir，以及本次測試或功能需要的服務值。
```

`coreKmp` 目前讀取以下全大寫 `local.properties` 名稱：

- `INFURA_API_KEY`、`INFURA_HOLESKY_KEY`、`INFURA_POLYGON_KEY`
- `ETHERSCAN_API_KEY`、`POLYGONSCAN_API_KEY`、`ARBISCAN_API_KEY`
- `BASESCAN_API_KEY`、`OPTIMISM_API_KEY`、`BSCSCAN_API_KEY`
- `RANGO_API_KEY`、`ZEROX_API_KEY`、`MORALIS_API_KEY`
- `TRON_API_KEY`、`GETBLOCK_API_KEY`

不可假設同名環境變數一定會進入 BuildKonfig；以目前
`coreKmp/build.gradle.kts` 為準。

## 選用 1Password 流程

```bash
docs/PUBLIC_BUILD.md

set -a
source .env
set +a
./scripts/validate-build.sh
```

設定腳本需要現有且可讀取的 `a local secrets manager (never commit)` item，明確提示後才可能
修改缺少欄位，再把支援的值複製到已忽略的 `.env`。查詢失敗時不會自動建立
vault 或 item。只有明確要做欄位變更時才使用；驗證腳本只回報是否存在，不會輸出秘密值。

## 驗證界線

設定變更要分開記錄：

1. Gradle 設定或編譯結果。
2. 精確模組的自動化測試結果。
3. 模擬器證據。
4. 實體裝置或硬體錢包證據。
5. Testnet 或 mainnet 網路證據。

任何一條都不能代替其他證據路徑。
