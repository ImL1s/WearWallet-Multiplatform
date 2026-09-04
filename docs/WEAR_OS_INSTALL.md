# Wear OS install (debug)

This is how to put the **Wear debug APK** on a Wear OS emulator or a developer
watch. It is **not** a Play Store install, not a production watch build, and
**not** proof of mainnet, Keystone, or a funded wallet.

Do not use real seed phrases, private keys, or real funds.

Public clones must assemble with `-PpublicSnapshot=true` (no production
Firebase config). See [PUBLIC_BUILD.md](./PUBLIC_BUILD.md).

## 1. Create a Wear OS emulator

Official first-app path:
[Create and run a Wear OS app](https://developer.android.com/training/wearables/get-started/creating).

In Android Studio:

1. Install a current **Android SDK Platform-Tools**.
2. **Tools → Device Manager → Create** (`+`).
3. Category **Wear OS**. Pick a hardware profile (for example Wear OS Small
   Round or Wear OS Large Round).
4. Accept the default API / system image unless you have a reason not to, then
   **Finish**.
5. Start the AVD.

This repository does **not** pin an AVD name or API level. Screenshots in
[SCREENSHOTS.md](./SCREENSHOTS.md) were captured on a Wear OS Large Round AVD;
that is capture history, not a required device.

## 2. Assemble the debug APK

From the repository root:

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

Output: `wear/build/outputs/apk/debug/wear-debug.apk`.

## 3. Install with adb

List devices and use the serial you actually have. It is **not** always
`emulator-5554`.

```bash
adb devices -l
SERIAL="$(adb devices | awk 'NR==2 {print $1}')"
echo "using $SERIAL"
adb -s "$SERIAL" install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s "$SERIAL" shell monkey -p com.cbstudio.wearwallet -c android.intent.category.LAUNCHER 1
```

Generic adb install notes:
[Android Debug Bridge](https://developer.android.com/tools/adb).

If several devices are connected, do not use the `NR==2` one-liner; pick the
Wear serial from `adb devices -l` by hand.

### Android Studio Run

You can skip adb: select the Wear AVD in the Run device list and press **Run**.
Studio installs and launches the `wear` module. Official:
[Debug a Wear OS app](https://developer.android.com/training/wearables/get-started/debugging).

## 4. Debug QA overlay (emulator only)

On a **debug Wear AVD**, empty wallets can still tap QR / token / history /
contacts using local fixtures. See [WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md).
Look for **「QA 假資料 · 非主網」**. Release APKs, physical watches, and
non-emulator debug builds keep the overlay off. Broadcast stays denied.

## 5. Physical Wear OS watch (sideload)

Sideload is a separate evidence lane from emulator QA. Official:

- USB: enable **Developer options** (tap Build number seven times under
  System → About / Versions), then **ADB debugging**. Allow the computer when
  prompted.
  [Create and run a Wear OS app](https://developer.android.com/training/wearables/get-started/creating)
- Wi-Fi: same Wi-Fi as the computer, **Wireless debugging**, `adb pair` then
  `adb connect`. Requires a current Platform-Tools.
  [Debug Wear OS over Wi-Fi](https://developer.android.com/training/wearables/get-started/debug-wifi)

Then `adb devices -l` and the same `install -r` command with that serial.

This repository does **not** document phone–watch Bluetooth pairing, Play
internal testing, or store listing. Those are not this install path.

## What a successful install does not prove

- Physical-watch camera, companion phone, or hardware wallet
- Real balances, history, or contacts
- Broadcast or on-chain confirmation
- Store review or a signed Play artifact
