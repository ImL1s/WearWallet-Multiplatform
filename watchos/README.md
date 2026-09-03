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
- Git submodules initialized
- GitHub Packages credentials when dependency resolution requires them

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

The checked-in Xcode project still contains a retired `sharedKmp` framework
search path. `watchos/build-kmp.sh` can prepare
`watchos/Frameworks/coreKmp.framework`, but the Xcode project is not a verified
one-command build until that setting is repaired and an Xcode build passes.

After repairing the project, confirm Framework Search Paths and the embedded
framework reference point to `coreKmp.framework`; do not restore
`WearWalletShared.framework` or a `sharedKmp/` path. See the
[watchOS development guide](../docs/WATCHOS_DEVELOPMENT_GUIDE.md) for the
separate evidence lanes.

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

## Historical documents

Older Xcode setup notes, migration plans, and point-in-time fix reports are
preserved under [`docs/archive/watchos-reports/`](../docs/archive/watchos-reports/)
and [`docs/archive/watchos-migration/`](../docs/archive/watchos-migration/).
They intentionally retain historical context and are not current instructions.
