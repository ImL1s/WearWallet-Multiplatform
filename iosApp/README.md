# Experimental iOS companion leftovers

`iosApp/` is **not** a supported product entry. Feature status
`mobile_companion` / `watchos` in
[`docs/FEATURE_STATUS.md`](../docs/FEATURE_STATUS.md) do not claim this
directory. Public CI does not build it. The supported Apple path is
[`watchos/`](../watchos/README.md).

Do not treat XcodeGen output, WatchConnectivity sources, or StoreKit stubs as
a phone wallet, Wear relay, or App Store product.

## What is here

| Path | Role |
| --- | --- |
| [`project.yml`](./project.yml) | XcodeGen spec (`WearWalletCompanion`, bundle `com.cbstudio.wearwallet.ios`) |
| [`generate_ios_project.sh`](./generate_ios_project.sh) | Runs `xcodegen generate` locally |
| [`iosApp/`](./iosApp/) | SwiftUI leftovers |
| [`iosAppTests/`](./iosAppTests/), [`iosAppUITests/`](./iosAppUITests/) | Companion tests; not public CI |

`DEVELOPMENT_TEAM` in `project.yml` is empty on purpose. Signing is local.

The spec points at
`../coreKmp/build/cocoapods/publish/debug/coreKmp.xcframework`. That path is
not produced by public Ubuntu CI. A generated `WearWalletCompanion.xcodeproj`
is local and should not be committed as proof.

## If you still generate it

```bash
cd iosApp
./generate_ios_project.sh
# Then open the local WearWalletCompanion.xcodeproj — not a store archive.
```

Requires macOS, Xcode, and `xcodegen`. Success is not watchOS, Keystone,
mainnet, or TestFlight evidence.
