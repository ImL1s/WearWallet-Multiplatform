# Wear OS 安裝（debug）

<div align="center">

**[English](./WEAR_OS_INSTALL.md)** | **繁體中文**

</div>

這是把 **Wear debug APK** 裝到 Wear OS 模擬器或開發用手錶的路徑。**不是**
Play 商店安裝、不是 production 手錶建置，也**不是** mainnet、Keystone 或已注資
錢包的證據。

不要使用真實助記詞、私鑰或真實資金。

公開 clone 必須用 `-PpublicSnapshot=true` 組裝（沒有 production Firebase
設定）。見 [PUBLIC_BUILD.md](./PUBLIC_BUILD.md) 與
[API 設定](./API_CONFIGURATION.zh-TW.md)。

## 1. 建立 Wear OS 模擬器

官方入門：
[Create and run a Wear OS app](https://developer.android.com/training/wearables/get-started/creating)。

在 Android Studio：

1. 安裝目前的 **Android SDK Platform-Tools**。
2. **Tools → Device Manager → Create**（`+`）。
3. 類別選 **Wear OS**。選硬體設定檔（例如 Wear OS Small Round 或 Wear OS
   Large Round）。
4. 沒有特殊理由就接受預設 API／系統映像，然後 **Finish**。
5. 啟動 AVD。

本倉庫**不鎖定** AVD 名稱或 API level。[SCREENSHOTS.md](./SCREENSHOTS.md)
的截圖來自 Wear OS Large Round AVD，那是拍攝紀錄，不是必備裝置。

## 2. 組裝 debug APK

在倉庫根目錄：

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

輸出：`wear/build/outputs/apk/debug/wear-debug.apk`。

## 3. 用 adb 安裝

列出裝置，用你**實際有的** serial。它**不一定**是 `emulator-5554`。

```bash
adb devices -l
# 複製 Wear 模擬器 serial（emulator-XXXX）。不要假設第一列就是手錶。
adb -s SERIAL install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s SERIAL shell monkey -p com.cbstudio.wearwallet -c android.intent.category.LAUNCHER 1
```

把 `SERIAL` 換成 Wear 那一列。手機與手錶同時接上時，`adb devices` 第一列常常
是手機。

通用 adb 說明：
[Android Debug Bridge](https://developer.android.com/tools/adb)。

### Android Studio Run

公開 clone 的支援路徑是上面的 CLI assemble + `adb install`。Studio **Run**
是選用，而且很容易設錯。

若要用：

1. 建立或選擇 **`wear` 模組**的 **Android App** run configuration，不要選
   `mobile`。兩個模組都可能用 `com.cbstudio.wearwallet`；對 Wear AVD 跑
   `mobile` 可能覆蓋或安裝失敗。
2. 部署目標：Wear AVD。
3. 這個公開樹仍需要 `publicSnapshot=true`，否則 Google Services 會找未附上的
   真實 `google-services.json`。放到**使用者層級**
   `~/.gradle/gradle.properties`（不是追蹤中的倉庫檔）：

   ```properties
   publicSnapshot=true
   ```

官方除錯 UI：
[Debug a Wear OS app](https://developer.android.com/training/wearables/get-started/debugging)。
若 IDE 建置仍卡在 Firebase 設定，改走 CLI。

## 4. Debug QA overlay（僅模擬器）

在 **debug Wear AVD** 上，空錢包仍可用本機 fixture 點 QR／代幣／歷史／通訊錄。
見 [WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md)。畫面上會有
**「QA 假資料 · 非主網」**。Release APK、實體錶、非模擬器 debug 建置不會開
overlay。Broadcast 維持拒絕。

## 5. 實體 Wear OS 手錶（sideload）

Sideload 與模擬器 QA 是不同證據路徑。官方：

- USB：開啟 **Developer options**（在 System → About / Versions 連點 Build
  number 七次），再開 **ADB debugging**。電腦連上時允許授權。
  [Create and run a Wear OS app](https://developer.android.com/training/wearables/get-started/creating)
- Wi-Fi：與電腦同一 Wi-Fi，**Wireless debugging**，先 `adb pair` 再
  `adb connect`。需要目前的 Platform-Tools。
  [Debug Wear OS over Wi-Fi](https://developer.android.com/training/wearables/get-started/debug-wifi)

然後 `adb devices -l`，用該 serial 跑同樣的 `install -r`。

本倉庫**不文件化**手機–手錶藍牙配對、Play 內部測試或商店上架。那些不是這條
安裝路徑。

## 安裝成功不代表什麼

- 實體錶相機、配套手機或硬體錢包
- 真實餘額、歷史或聯絡人
- Broadcast 或鏈上確認
- 商店審核或已簽章的 Play 產物
