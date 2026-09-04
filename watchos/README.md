# WearWallet watchOS module

The `watchos/` directory contains the native SwiftUI watch application and its
Xcode/CocoaPods integration. Swift sources currently import the shared KMP
framework as `coreKmp`.

> [!WARNING]
> This directory is under active development. Source presence, framework
> compilation, or simulator output does not prove physical-watch, hardware
> wallet, mainnet, signing, or release readiness.

## Requirements

- macOS with Xcode
- JDK 17
- Android SDK configured for the Gradle project
- GitHub Packages credentials when dependency resolution requires them

`modules/` in this public tree are plain vendored copies. There is no
`.gitmodules`.

See the main [development guide](../docs/DEVELOPMENT_GUIDE.md) for credential
and Android SDK setup.

## Build the KMP framework

From the repository root, use the maintained helper:

```bash
./scripts/build-watchos.sh
```

Or run a verified target-specific task directly:

```bash
# Apple Silicon simulator
./gradlew :coreKmp:linkDebugFrameworkWatchosSimulatorArm64

# Physical arm64 watch target
./gradlew :coreKmp:linkDebugFrameworkWatchosArm64

# Intel simulator
./gradlew :coreKmp:linkDebugFrameworkWatchosX64
```

The simulator framework is generated at:

```text
coreKmp/build/bin/watchosSimulatorArm64/debugFramework/coreKmp.framework
```

`watchos/build-kmp.sh` builds that target and copies the framework into
`watchos/Frameworks/` for local Xcode integration.

## Xcode integration status

The checked-in app is `WatchWallet.xcodeproj` plus `WatchWallet Watch App`.
`watchos/WatchWallet/` is a small iOS companion target in that same project
(not a second watch app); its CocoaPods target is commented out.
`watchos/build-kmp.sh` prepares `watchos/Frameworks/coreKmp.framework`.
Framework Search Paths must point at `coreKmp.framework`; do not restore
`WearWalletShared.framework` or a `sharedKmp/` path.

There is no committed `WearWallet.xcworkspace`. `watchos/build-kmp.sh` runs
`pod install` and creates a local workspace. After that script, open
`WearWallet.xcworkspace` so `Pods_WatchWallet_Watch_App.framework` is linked.
Opening `WatchWallet.xcodeproj` alone omits the Pods project.

An Xcode / simulator / physical-watch result is a separate evidence lane. See
the [watchOS development guide](../docs/WATCHOS_DEVELOPMENT_GUIDE.md).

## Validate

Framework compilation proves only the selected KMP target compiled. Record
Xcode build, simulator, physical watch, network, and hardware-wallet evidence
as separate results.

Relevant repository checks include:

```bash
./scripts/check_markdown_links.py
./gradlew :coreKmp:compileKotlinWatchosSimulatorArm64
./gradlew :coreKmp:linkDebugFrameworkWatchosSimulatorArm64
```

Historical watchOS migration reports are not shipped in this public tree.
