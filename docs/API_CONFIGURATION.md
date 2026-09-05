# WearWallet API configuration

<div align="center">

**English** | **[繁體中文](./API_CONFIGURATION.zh-TW.md)**

</div>

WearWallet has separate configuration paths for dependency resolution, the
Wear OS app, and `coreKmp`. A key being configured does not prove that the
related chain, backend, or hardware path is supported. See
[FEATURE_STATUS.md](./FEATURE_STATUS.md).

Debug assemble on a public clone does **not** require service keys:

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

## Security rules

- Never put credentials in the tracked root `gradle.properties` file.
- Keep local service values in the ignored `local.properties`, exported
  environment variables, or a password manager.
- Never log key values. Presence checks must not print the secret.
- Use the smallest scope and rotate any value that may have been exposed.
- Do not place real values in `.env.example` or `local.properties.template`.

This public tree does **not** ship 1Password setup, `scripts/setup.sh`, or
Play Console automation.

## Local files

| File | Tracked? | Role |
| --- | --- | --- |
| [`local.properties.template`](../local.properties.template) | Yes (placeholders) | Copy to ignored `local.properties`: `sdk.dir`, Wear lowercase keys, `coreKmp` uppercase keys |
| [`.env.example`](../.env.example) | Yes (placeholders) | Copy to ignored `.env` for GitHub Packages + Wear env names |
| [`gradle.properties.example`](../gradle.properties.example) | Yes (placeholders) | Copy selected keys to **user-level** `~/.gradle/gradle.properties` |
| `wear/google-services.json.example` and `mobile/google-services.json.example` | Yes (shape only) | Do not commit a real `google-services.json` |
| Tracked root `gradle.properties` | Yes | Shared Gradle JVM/Android flags only — no tokens |

```bash
cp local.properties.template local.properties
# Set sdk.dir to your Android SDK. Android Studio may write this line for you.
# Uncomment only service keys you fill. Blank Wear keys override env fallbacks.
```

## Configuration map

| Consumer | Supported input | Names |
| --- | --- | --- |
| Android SDK | Ignored `local.properties` | `sdk.dir` |
| Public snapshot / skip Firebase | Gradle `-PpublicSnapshot=true` or user-level `publicSnapshot=true` | `publicSnapshot` |
| GitHub Packages | Environment or user-level Gradle properties | `GITHUB_ACTOR`, `GITHUB_TOKEN`; `github.actor`, `github.token` |
| Wear OS BuildConfig | Environment or ignored `local.properties` (lowercase) | `INFURA_PROJECT_ID` / `infura.project.id`; `ETHERSCAN_API_KEY` / `etherscan.api.key`; `MORALIS_API_KEY` / `moralis.api.key` |
| Wear OS Google AI BuildConfig | Environment or Gradle property **only** | `GOOGLE_AI_API_KEY` (not `local.properties`) |
| `coreKmp` BuildKonfig | Ignored `local.properties` uppercase **only** | Keys listed under `coreKmp` below |
| Wear release signing | Gradle properties when present | `WEARWALLET_STORE_FILE`, `WEARWALLET_STORE_PASSWORD`, `WEARWALLET_KEY_ALIAS`, `WEARWALLET_KEY_PASSWORD` |

The implementation sources of truth are
[`settings.gradle.kts`](../settings.gradle.kts),
[`wear/build.gradle.kts`](../wear/build.gradle.kts), and
[`coreKmp/build.gradle.kts`](../coreKmp/build.gradle.kts).

Wear `INFURA_PROJECT_ID` / `infura.project.id` is **not** the same field as
`coreKmp` `INFURA_API_KEY`. Filling one does not populate the other.

## GitHub Packages

Use environment variables for a short-lived shell:

```bash
export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN
./gradlew help
```

Token scope is `read:packages` only. Clone and credential notes live in
[PUBLIC_BUILD.md](./PUBLIC_BUILD.md). An optional user-level
`~/.gradle/gradle.properties` can hold `github.actor` and `github.token`; the
repository's tracked `gradle.properties` must remain free of secrets.

Public CI uses optional repo secrets `GH_TOKEN_PACKAGES` and `GH_ACTOR_NAME`.
When those are empty, the workflow falls back to the job `GITHUB_TOKEN`.
Fork PRs do not get `github.token` written into `gradle.properties`.

## Wear OS service values

Either export values before running Gradle:

```bash
export INFURA_PROJECT_ID=YOUR_INFURA_PROJECT_ID
export ETHERSCAN_API_KEY=YOUR_ETHERSCAN_API_KEY
export MORALIS_API_KEY=YOUR_MORALIS_API_KEY
export GOOGLE_AI_API_KEY=YOUR_GOOGLE_AI_API_KEY
```

Or add the lowercase Wear OS properties to ignored `local.properties`
(omit a key entirely if you want the env fallback; do not leave it blank):

```properties
infura.project.id=YOUR_INFURA_PROJECT_ID
etherscan.api.key=YOUR_ETHERSCAN_API_KEY
moralis.api.key=YOUR_MORALIS_API_KEY
```

`GOOGLE_AI_API_KEY` is not read from `local.properties`. Use the environment
or a Gradle property.

Only configure services required by the feature being exercised. Placeholder
BuildConfig values and an assembled APK are not proof that a live service was
called successfully.

## `coreKmp` values

`coreKmp` currently reads these uppercase `local.properties` names:

- `INFURA_API_KEY`, `INFURA_HOLESKY_KEY`, `INFURA_POLYGON_KEY`
- `ETHERSCAN_API_KEY`, `POLYGONSCAN_API_KEY`, `ARBISCAN_API_KEY`
- `BASESCAN_API_KEY`, `OPTIMISM_API_KEY`, `BSCSCAN_API_KEY`
- `RANGO_API_KEY`, `ZEROX_API_KEY`, `MORALIS_API_KEY`
- `TRON_API_KEY`, `GETBLOCK_API_KEY`

Do not assume an environment variable with the same name reaches BuildKonfig;
follow the current `coreKmp/build.gradle.kts` implementation.

## Firebase / `publicSnapshot`

This tree ships `wear/google-services.json.example` and
`mobile/google-services.json.example` only. The supported public-clone path
skips Google Services / Crashlytics / Performance:

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
./gradlew :mobile:assembleDebug -PpublicSnapshot=true
```

For Android Studio **Run**, put `publicSnapshot=true` in user-level
`~/.gradle/gradle.properties` and select the **`wear`** module. See
[WEAR_OS_INSTALL.md](./WEAR_OS_INSTALL.md).

A filled real `google-services.json` is **not** a store, Crashlytics, or
production Firebase proof. Do not commit it.

## Wear release signing (optional)

`wear/build.gradle.kts` creates a release signing config only when
`WEARWALLET_STORE_FILE` is set, together with
`WEARWALLET_STORE_PASSWORD`, `WEARWALLET_KEY_ALIAS`, and
`WEARWALLET_KEY_PASSWORD`. Those are Gradle project properties
(`project.hasProperty` / `findProperty`). Put them in user-level
`~/.gradle/gradle.properties`, or pass `-PWEARWALLET_STORE_FILE=...` (and the
other three) on the Gradle command. Do not put them in tracked
`gradle.properties`. An ignored extra file is not read unless Gradle itself
loads it as project properties.

This is not a Play Console or store-upload path. `:wear:assembleRelease`
without those properties is still not a signed Play artifact. Do not commit
keystores.

## Verification boundaries

For a configuration change, record separately:

1. Gradle configuration or compilation result.
2. Automated test result for the exact module.
3. Emulator or simulator evidence, if any.
4. Physical device or hardware-wallet evidence, if any.
5. Testnet or mainnet network evidence, if any.

One lane does not substitute for another.
