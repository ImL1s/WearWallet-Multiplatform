# WearWallet Wear OS Screenshots

<div align="center">

**English** | **[繁體中文](./SCREENSHOTS.zh-TW.md)**

</div>

Marketing captures from **Wear OS Large Round (AVD)**, app id `com.cbstudio.wearwallet`.  
Captured with `adb shell screencap` after emulator-only debug flows (no real mnemonics shown).

| File | Screen | Caption |
|------|--------|---------|
| `01-welcome-onboarding.png` | Welcome | First launch — choose to create or import a wallet |
| `02-create-wallet-entry.png` | Create wallet | Name your new wallet before secure setup |
| `03-import-wallet-entry.png` | Import wallet | Import via recovery phrase or private key |
| `04-wallet-home.png` | Wallet home | Demo wallet balance, quick actions (Send, Swap, QR, Receive) |
| `05-receive-qr.png` | Receive | QR code and address for incoming funds (test wallet) |
| `06-send-address.png` | Send (address) | Recipient address entry — flow stops before broadcast |
| `08-settings.png` | Settings | Wallet management, chains, and app preferences |
| `09-wallet-management-keystone.png` | Wallet management | Multi-wallet list and **Connect Keystone** hardware wallet entry |

## Capture (local emulator)

```bash
# Install debug build with emulator QA overrides, then:
adb devices -l
# Copy the Wear serial. Do not assume emulator-5554.
python3 scripts/capture-wear-screenshots.py --serial SERIAL
```

Keep that AVD awake (`adb -s SERIAL shell svc power stayon true`). The script
writes into `docs/screenshots/` and auto-creates a **Demo Wallet** on AVD (no
biometric / no hardware keystore).

## README embed (suggested)

```markdown
## Screenshots (Wear OS)

| Welcome | Create wallet | Import wallet |
|:--:|:--:|:--:|
| ![Welcome onboarding](docs/screenshots/01-welcome-onboarding.png) | ![Create wallet](docs/screenshots/02-create-wallet-entry.png) | ![Import wallet](docs/screenshots/03-import-wallet-entry.png) |

| Wallet home | Receive QR | Send address |
|:--:|:--:|:--:|
| ![Wallet home](docs/screenshots/04-wallet-home.png) | ![Receive](docs/screenshots/05-receive-qr.png) | ![Send](docs/screenshots/06-send-address.png) |

| Settings | Keystone connect |
|:--:|:--:|
| ![Settings](docs/screenshots/08-settings.png) | ![Keystone](docs/screenshots/09-wallet-management-keystone.png) |
```

## Notes

- Screenshots use a **Demo Wallet** with zero balance; receive QR shows a generated test address only.
- Debug-only wear fixes enable AVD capture: emulator security bypass, in-memory key vault, capability gate override, and automated wallet creation. **Not active in release builds on physical devices.**
