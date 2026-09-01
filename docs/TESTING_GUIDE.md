# WearWallet testing guide

Choose checks by the behavior and platform changed. A green command proves only
the lane it exercised.

## Fast checks

```bash
# Maintained local Markdown targets
./scripts/check_markdown_links.py

# Shared Android/JVM unit tests
./gradlew :coreKmp:testDebugUnitTest

# Wear OS unit tests
./gradlew :wear:testDebugUnitTest

# Android companion unit tests
./gradlew :mobile:testDebugUnitTest
```

## Build checks

```bash
# Shared Android compilation
./gradlew :coreKmp:compileDebugKotlinAndroid

# Application debug builds
./gradlew :wear:assembleDebug
./gradlew :mobile:assembleDebug

# Wear OS release assembly without publishing
./gradlew :wear:assembleRelease
```

Release tasks may require local signing or service configuration. Do not commit
credentials or weaken a release check to make it pass.

## Apple and KMP checks

Run Apple tasks on macOS with Xcode installed:

```bash
./gradlew \
  :coreKmp:allTests \
  :coreKmp:compileKotlinIosSimulatorArm64 \
  :coreKmp:compileKotlinIosArm64 \
  :coreKmp:linkDebugFrameworkIosSimulatorArm64 \
  :coreKmp:linkDebugFrameworkIosArm64 \
  :coreKmp:compileKotlinWatchosSimulatorArm64 \
  :coreKmp:compileKotlinWatchosArm64 \
  :coreKmp:linkDebugFrameworkWatchosSimulatorArm64 \
  :coreKmp:linkDebugFrameworkWatchosArm64
```

These tasks mirror the intent of the macOS CI lane. Check the current workflow
before copying the list into automation.

## Change-to-check matrix

| Change | Minimum local evidence |
| --- | --- |
| Documentation only | Markdown link check and `git diff --check` |
| `coreKmp` common code | Focused test plus `:coreKmp:testDebugUnitTest` |
| Wear OS code | Focused test, `:wear:testDebugUnitTest`, and `:wear:assembleDebug` |
| Android companion code | Focused test, `:mobile:testDebugUnitTest`, and `:mobile:assembleDebug` |
| Apple source set | Target-specific compile/link task on macOS |
| Signing or crypto path | Fixed vectors, negative cases, capability-gate tests, and target build |
| Persistence or migration | Migration tests, rollback/failure cases, and platform database test |

## Evidence levels

Keep these results separate in pull requests and release notes:

1. Static analysis or documentation validation
2. Unit tests
3. Integration tests
4. Emulator or simulator checks
5. Physical device checks
6. Physical Keystone or other external hardware checks
7. Store-distributed release checks
8. Mainnet behavior

One level does not imply the next. In particular, a mocked QR flow or simulator
build is not physical hardware proof, and a sideloaded build is not store-release
proof.

## Focused test practice

- Reproduce the failure before changing behavior.
- Prefer a test that exercises the public guard path, not only a helper.
- Test success, malformed input, unavailable backend, and denied capability.
- Use fixed, independently verifiable vectors for signing and hashing.
- Fail when no expected test reports are produced; zero discovered tests is not
  a pass.
- Avoid tests that require real secrets, personal accounts, or mainnet writes.

## CI source of truth

Current workflows live in [`.github/workflows/`](../.github/workflows/). The main
platform matrix is in [`ci.yml`](../.github/workflows/ci.yml), with additional
security checks in
[`sec13-security-verification.yml`](../.github/workflows/sec13-security-verification.yml).

Before reporting a pull request as green, verify checks ran against the exact
head commit and inspect any skipped, cancelled, or neutral jobs.
