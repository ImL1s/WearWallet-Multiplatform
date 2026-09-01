<div align="center">

**[English](./WATCHOS_DEVELOPMENT_GUIDE.md)** | **[繁體中文](./WATCHOS_DEVELOPMENT_GUIDE.zh-TW.md)**

</div>

# watchOS development

The native SwiftUI project lives in `watchos/`; maintained cross-platform code
lives in `coreKmp`. Kotlin/Native produces a framework named `coreKmp`.

> [!WARNING]
> This is an integration workspace, not release or hardware proof. A successful
> framework link does not prove the checked-in Xcode project, simulator,
> physical watch, Keystone device, signing path, or mainnet behavior.

## Requirements

- macOS and Xcode
- JDK 17
- an Android SDK visible to Gradle
- initialized Git submodules
- GitHub Packages credentials when private dependency resolution requires them

See the [development guide](./DEVELOPMENT_GUIDE.md) for environment setup.

## Build the framework

From the repository root:

```bash
# Apple Silicon simulator
./gradlew :coreKmp:linkDebugFrameworkWatchosSimulatorArm64

# Physical arm64 watch
./gradlew :coreKmp:linkDebugFrameworkWatchosArm64

# Intel simulator
./gradlew :coreKmp:linkDebugFrameworkWatchosX64
```

The maintained helper offers the same target selection:

```bash
./scripts/build-watchos.sh
```

Simulator output:

```text
coreKmp/build/bin/watchosSimulatorArm64/debugFramework/coreKmp.framework
```

`watchos/build-kmp.sh` builds that simulator target and copies the framework to
`watchos/Frameworks/coreKmp.framework`.

## Xcode integration status

Swift sources import the module as:

```swift
import coreKmp
```

The checked-in Xcode project still contains a retired `sharedKmp` framework
search path. Until that project setting is repaired and an Xcode build passes,
do not present the following as a verified one-command workflow:

```bash
cd watchos
./build-kmp.sh
open WearWallet.xcworkspace
```

When repairing the project, make Framework Search Paths and framework references
point to `coreKmp.framework`; do not restore `WearWalletShared.framework` or a
`sharedKmp/` path.

## Verification lanes

Record these separately:

1. Gradle task discovery and Kotlin compilation
2. target-specific framework link
3. Xcode project build
4. simulator test
5. physical-watch test
6. hardware-wallet and network evidence
7. signed archive and store/release state

Historical setup notes are under the [watchOS report archive](./archive/watchos-reports/)
and [migration archive](./archive/watchos-migration/).
