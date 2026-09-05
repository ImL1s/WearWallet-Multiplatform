# Android companion application

Gradle module `:mobile`. `applicationId` is `com.cbstudio.wearwallet` (same as
`:wear`). Feature status: `mobile_companion` is `EXPERIMENTAL` in
[`docs/FEATURE_STATUS.md`](../docs/FEATURE_STATUS.md). This is not a verified
phone wallet, Wear relay, or store product.

## Build

From the repository root. Public clones must skip Firebase:

```bash
./gradlew :mobile:assembleDebug -PpublicSnapshot=true
```

Do **not** deploy this APK to a Wear AVD. It can overwrite or fail the Wear
install because the package name matches. Wear install:
[`docs/WEAR_OS_INSTALL.md`](../docs/WEAR_OS_INSTALL.md).

Keys and `publicSnapshot`:
[`docs/API_CONFIGURATION.md`](../docs/API_CONFIGURATION.md).
