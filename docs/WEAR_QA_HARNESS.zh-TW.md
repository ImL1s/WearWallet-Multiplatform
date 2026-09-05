# Wear OS debug QA harness

<div align="center">

**[English](./WEAR_QA_HARNESS.md)** | **繁體中文**

</div>

這是 **debug／Wear 模擬器** overlay，讓空的 demo 錢包仍能操作 QR、代幣 Send、
歷史詳情與通訊錄 Send。**不是** mainnet 資料、不是已注資錢包，也不是簽章或
broadcast 成功的證據。

不要使用真實助記詞、私鑰或真實資金。

## 何時會開

只有**全部**成立時 overlay 才會開：

1. `BuildConfig.DEBUG` 為 true（Play／release APK 維持關閉）
2. 裝置看起來像 Wear AVD（`ranchu`／`goldfish`／`sdk_gwear`／模擬器標記）
3. JVM 測試沒有強制關掉它

Release 建置會忽略任何測試覆寫。Debug 模擬器的 capability gate 仍拒絕
broadcast。

開啟時，畫面會顯示 **「QA 假資料 · 非主網」**。把這條橫幅當成清單是本機
fixture 的標籤。

## 可以點什麼

| 流程 | Fixture | 作法 |
| --- | --- | --- |
| 沒有手機相機的 QR | 公開知名 EIP-681 payload | Wear 模擬器 QR 畫面 → **模擬掃描** |
| 有餘額的代幣列 | 本機 1 ETH 列 | 代幣清單 → ETH → Send |
| 有一筆歷史 | 本機 outgoing `CONFIRMED` fixture | 歷史 → QA 那一列 → 詳情 |
| 有聯絡人的通訊錄 | 本機「QA Vitalik」聯絡人 | 通訊錄 → Send（仍不能 broadcast） |

Fixture 收款地址是**公開知名地址**，不是專案錢包。產生的簽章或
`Result.success` 不是鏈上送金。

## 怎麼跑

先建立並啟動 Wear OS AVD。完整步驟（含實體錶 sideload）見
[WEAR_OS_INSTALL.zh-TW.md](./WEAR_OS_INSTALL.zh-TW.md)。

在這個倉庫（`-PpublicSnapshot=true` 略過 Firebase plugins）：

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

裝到**正在跑的 Wear OS 模擬器**，不是 production 手錶商店建置。用
`adb devices -l` 的 serial（常常是 `emulator-5554`，但不一定）：

```bash
adb devices -l
# 複製 Wear 模擬器 serial（emulator-XXXX）。不要假設第一列就是手錶。
adb -s SERIAL install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s SERIAL shell monkey -p com.cbstudio.wearwallet -c android.intent.category.LAUNCHER 1
```

應在代幣／歷史／聯絡人／QR／send-confirm 畫面看到 QA 橫幅。沒有橫幅代表
harness 沒開（release APK、實體錶、或非模擬器 debug）。

## 這不證明什麼

- 真實 Wear 相機或手機 QR 轉送
- 真實餘額、歷史或聯絡人
- Mainnet 軟體簽章作為產品通過
- Broadcast 或鏈上確認
