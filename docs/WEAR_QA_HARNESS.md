# Wear OS debug QA harness

This is a **debug / Wear emulator** overlay so empty demo wallets can still
exercise QR, token Send, history detail, and address-book Send. It is **not**
mainnet data, not a funded wallet, and not evidence that signing or broadcast
succeeded.

Do not use real seed phrases, private keys, or real funds.

## When it is on

The overlay is active only when **all** of the following hold:

1. `BuildConfig.DEBUG` is true (Play/release APK stays off)
2. The device looks like a Wear AVD (`ranchu` / `goldfish` / `sdk_gwear` / emulator markers)
3. You did not force it off in JVM tests

Release builds ignore any test override. Broadcast stays denied on the debug
emulator capability gate.

When it is on, screens show **「QA 假資料 · 非主網」**. Treat that banner as
the label that the list is local fixture data.

## What you can tap

| Flow | Fixture | How |
| --- | --- | --- |
| QR without a phone camera | Well-known public EIP-681 payload | Wear emulator QR screen → **模擬掃描** |
| Token row with a balance | Local 1 ETH row | Token list → ETH → Send |
| History with a row | Local outgoing `CONFIRMED` fixture | History → the QA row → detail |
| Address book with a contact | Local “QA Vitalik” contact | Contacts → Send (still cannot broadcast) |

The fixture recipient is a **well-known public address**, not a project wallet.
A generated signature or a `Result.success` is not an on-chain send.

## Run it

From this repository (`-PpublicSnapshot=true` skips Firebase plugins):

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
# private tree can omit -PpublicSnapshot=true if google-services.json is present
```

Install on a Wear OS emulator, not a production watch store build:

```bash
adb devices -l
adb -s emulator-5554 install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s emulator-5554 shell monkey -p com.cbstudio.wearwallet -c android.intent.category.LAUNCHER 1
```

You should see the QA banner on token / history / contact / QR / send-confirm
screens. If the banner is missing, the harness is off (release APK, physical
watch, or non-emulator debug).

## What this does not prove

- Real Wear camera or phone QR relay
- Real balances, history, or contacts
- Mainnet software sign as a product pass
- Broadcast or on-chain confirmation
