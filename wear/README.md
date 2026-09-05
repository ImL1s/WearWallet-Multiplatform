# Wear OS application

Gradle module `:wear`. Package `com.cbstudio.wearwallet`. Maturity claims are
only those in [`docs/FEATURE_STATUS.md`](../docs/FEATURE_STATUS.md). This is
not a Play-signed or mainnet-ready wallet.

## Build

From the repository root. Public clones must skip Firebase:

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

Output: `wear/build/outputs/apk/debug/wear-debug.apk`.

`sdk.dir`, service keys, and optional release signing:
[`docs/API_CONFIGURATION.md`](../docs/API_CONFIGURATION.md). Clone / CI:
[`docs/PUBLIC_BUILD.md`](../docs/PUBLIC_BUILD.md).

## Install

Create a Wear AVD (or sideload a developer watch), then `adb -s SERIAL
install -r` with the Wear serial from `adb devices -l`. Do not pick the first
row if a phone is attached. Full steps:
[`docs/WEAR_OS_INSTALL.md`](../docs/WEAR_OS_INSTALL.md).

Android Studio **Run** is optional: use the **`wear`** configuration (not
`mobile`) and `publicSnapshot=true` in user-level
`~/.gradle/gradle.properties`.

## Debug QA overlay

On a **debug Wear emulator** only. Not mainnet data:
[`docs/WEAR_QA_HARNESS.md`](../docs/WEAR_QA_HARNESS.md).
