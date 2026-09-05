# WearWallet Wear OS 截圖

<div align="center">

**[English](./SCREENSHOTS.md)** | **繁體中文**

</div>

行銷用截圖來自 **Wear OS Large Round（AVD）**，app id
`com.cbstudio.wearwallet`。
在僅模擬器的 debug 流程後用 `adb shell screencap` 拍攝（沒有顯示真實助記詞）。

| 檔案 | 畫面 | 說明 |
|------|--------|---------|
| `01-welcome-onboarding.png` | 歡迎 | 首次啟動 — 選擇建立或匯入錢包 |
| `02-create-wallet-entry.png` | 建立錢包 | 在安全設定前為新錢包命名 |
| `03-import-wallet-entry.png` | 匯入錢包 | 以助記詞或私鑰匯入 |
| `04-wallet-home.png` | 錢包首頁 | Demo 錢包餘額、快捷操作（Send、Swap、QR、Receive） |
| `05-receive-qr.png` | 收款 | 收款 QR 與地址（測試錢包） |
| `06-send-address.png` | 送金（地址） | 收款地址輸入 — 流程在 broadcast 前停止 |
| `08-settings.png` | 設定 | 錢包管理、鏈與 App 偏好 |
| `09-wallet-management-keystone.png` | 錢包管理 | 多錢包清單與 **Connect Keystone** 硬體錢包入口 |

## 拍攝（本機模擬器）

```bash
# 先裝帶模擬器 QA 覆寫的 debug 建置，然後：
python3 scripts/capture-wear-screenshots.py
```

從 `adb devices -l` 取 Wear serial（不一定是 `emulator-5554`）。讓該 AVD 保持
喚醒（`adb -s SERIAL shell svc power stayon true`）。腳本會在 AVD 上自動建立
**Demo Wallet**（無生物辨識／無硬體 keystore）。

安裝路徑見 [WEAR_OS_INSTALL.zh-TW.md](./WEAR_OS_INSTALL.zh-TW.md)。這些截圖
**不是**商店、實體錶或 mainnet 證據。

## README 嵌入（建議）

```markdown
## Screenshots (Wear OS)

| Welcome | Create wallet | Import wallet |
|:--:|:--:|:--:|
| ![Welcome onboarding](docs/screenshots/01-welcome-onboarding.png) | ![Create wallet](docs/screenshots/02-create-wallet-entry.png) | ![Import wallet](docs/screenshots/03-import-wallet-entry.png) |

| Wallet home | Receive QR | Send address |
|:--:|:--:|:--:|
| ![Wallet home](docs/screenshots/04-wallet-home.png) | ![Receive](docs/screenshots/05-receive-qr.png) | ![Send](docs/screenshots/06-send-address.png) |

| Settings | Keystone connect |
|:--:|:--:|:--:|
| ![Settings](docs/screenshots/08-settings.png) | ![Keystone](docs/screenshots/09-wallet-management-keystone.png) |
```

## 備註

- 截圖使用餘額為零的 **Demo Wallet**；收款 QR 只顯示產生的測試地址。
- 僅 debug 的 wear 修正讓 AVD 拍攝可行：模擬器安全繞過、記憶體 key vault、
  capability gate 覆寫與自動建立錢包。**在實體裝置的 release 建置不會啟用。**
