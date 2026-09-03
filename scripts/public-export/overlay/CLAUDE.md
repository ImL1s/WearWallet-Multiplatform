# CLAUDE.md (public snapshot)

This file provides guidance to Claude Code (or any AI coding assistant) when
working with this repository.

> This is a **sanitized public snapshot** of WearWallet. It has no private
> development history, no credential-management tooling, and no production
> release automation. See [`docs/PUBLIC_BUILD.md`](docs/PUBLIC_BUILD.md) and
> [`docs/PUBLIC_SNAPSHOT.md`](docs/PUBLIC_SNAPSHOT.md).

## Build & test commands

```bash
# Build all modules
./gradlew build -PpublicSnapshot=true

# Build a specific module
./gradlew :wear:assembleDebug -PpublicSnapshot=true   # Wear OS app
./gradlew :mobile:assembleDebug -PpublicSnapshot=true # Android phone app
./gradlew :coreKmp:build -PpublicSnapshot=true         # KMP core SDK

# Unit tests
./gradlew test -PpublicSnapshot=true                                   # all modules
./gradlew :coreKmp:testDebugUnitTest -PpublicSnapshot=true              # coreKmp Android tests
./gradlew :wear:test -PpublicSnapshot=true                              # wear module

# KMP cross-platform tests
./gradlew :coreKmp:iosSimulatorArm64Test -PpublicSnapshot=true
./gradlew :coreKmp:watchosSimulatorArm64Test -PpublicSnapshot=true

# Fast compile check (no tests)
./gradlew :wear:compileDebugKotlin -PpublicSnapshot=true
./gradlew :coreKmp:compileKotlinAndroid -PpublicSnapshot=true

# Lint
./gradlew detekt
```

`-PpublicSnapshot=true` skips the Firebase/Google-Services/Crashlytics/
Performance plugins (see `wear/build.gradle.kts`, `mobile/build.gradle.kts`)
so builds never require a real `google-services.json`. Use the committed
`*.example` files as a reference for the config shape only.

### watchOS

```bash
cd watchos
./build-kmp.sh
open WatchWallet.xcodeproj
```

## Project architecture

Pure-KMP architecture; `coreKmp` is the single active core development module.

```
WearWallet/
├── coreKmp/    # KMP blockchain core SDK (Koin DI) — multi-chain support [primary module]
├── wear/       # Wear OS app (Koin DI)
├── mobile/     # Android companion app (Hilt DI)
├── watchos/    # Swift/SwiftUI Apple Watch app
└── modules/    # Focused KMP library modules (vendored as plain trees in this snapshot)
```

**Architecture rules:**

- `coreKmp` is the only core-development module; new feature work should
  target it first, then be surfaced in `wear`/`mobile` via dependency
  injection.
- `shared`/`sharedKmp` are retired — do not add anything there.

### Dependency injection

- `wear` — **Koin** (`koinViewModel()`, `KoinComponent` + `inject()`)
- `mobile` — **Hilt** (`@HiltViewModel`, `@Inject`, KSP)
- `coreKmp` — **Koin** (pure KMP)

### KMP development priority

1. Implement directly in `coreKmp` (preferred — works on every platform).
2. Consume `coreKmp` services from `wear`/`mobile` via DI.
3. Use `expect`/`actual` only for genuinely platform-specific APIs.
4. Avoid platform-only code in `wear`/`mobile` unless unavoidable.

### Core stack

| Layer | Technology |
| --- | --- |
| UI | Jetpack Compose (Wear OS Material 3), SwiftUI (watchOS) |
| Async | Kotlin Coroutines + Flow |
| DB | SQLDelight (KMP) |
| Networking | Ktor (KMP), Retrofit (Android) |
| Blockchain | TrustWallet Core, Web3j, Signum (KMP crypto) |
| Hardware wallet | Keystone SDK + bc-ur |

### Version pins (do not casually bump)

- Kotlin **2.1.0** (2.2.0 changes ABI compatibility for this project)
- Signum **3.16.3** (3.17.0 requires Kotlin 2.2.0)
- Ktor **2.3.12** (avoids an `HttpTimeout` plugin conflict)

## Notes for this snapshot

- There is no CI/CD credential automation, signing pipeline, or store-upload
  tooling in this repository — those live only in the private canonical
  repository. Do not attempt to reconstruct or request such tooling here.
- Do not commit `.env`, keystores, service-account JSON, or any real
  `google-services.json` — only `*.example` files belong in this repo.
- Treat any file that looks like it contains a real recovery phrase, private
  key, or API token as a bug to report, not a fixture to reuse.
- See [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md) and the
  [`coreKmp` API overview](docs/COREKMP_API_OVERVIEW.md) for deeper module
  docs.
