# WearWallet API configuration

<div align="center">

**English** | **[繁體中文](./API_CONFIGURATION.zh-TW.md)**

</div>

WearWallet has separate configuration paths for dependency resolution, the
Wear OS app, and `coreKmp`. A key being configured does not prove that the
related chain, backend, or hardware path is supported. See
[FEATURE_STATUS.md](./FEATURE_STATUS.md).

## Security rules

- Never put credentials in the tracked root `gradle.properties` file.
- Keep local service values in the ignored `local.properties`, exported
  environment variables, or a password manager.
- Never log key values. Presence checks must not print the secret.
- Use the smallest scope and rotate any value that may have been exposed.
- Do not place real values in `.env.example` or `local.properties.template`.

## Configuration map

| Consumer | Supported input | Names |
| --- | --- | --- |
| GitHub Packages | Environment or user-level Gradle properties | `GITHUB_ACTOR`, `GITHUB_TOKEN`; `github.actor`, `github.token` |
| Wear OS build | Environment or ignored `local.properties` | `INFURA_PROJECT_ID` / `infura.project.id`; `ETHERSCAN_API_KEY` / `etherscan.api.key`; `MORALIS_API_KEY` / `moralis.api.key` |
| Wear OS Google AI build field | Environment or Gradle property | `GOOGLE_AI_API_KEY` |
| `coreKmp` BuildKonfig | Ignored `local.properties` | Uppercase keys in `local.properties.template` |

The implementation sources of truth are
[`settings.gradle.kts`](../settings.gradle.kts),
[`wear/build.gradle.kts`](../wear/build.gradle.kts), and
[`coreKmp/build.gradle.kts`](../coreKmp/build.gradle.kts).

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

## Wear OS service values

Either export values before running Gradle:

```bash
export INFURA_PROJECT_ID=YOUR_INFURA_PROJECT_ID
export ETHERSCAN_API_KEY=YOUR_ETHERSCAN_API_KEY
export MORALIS_API_KEY=YOUR_MORALIS_API_KEY
export GOOGLE_AI_API_KEY=YOUR_GOOGLE_AI_API_KEY
```

Or add the lowercase Wear OS properties to ignored `local.properties`:

```properties
infura.project.id=YOUR_INFURA_PROJECT_ID
etherscan.api.key=YOUR_ETHERSCAN_API_KEY
moralis.api.key=YOUR_MORALIS_API_KEY
```

Only configure services required by the feature being exercised. Placeholder
BuildConfig values and an assembled APK are not proof that a live service was
called successfully.

## `coreKmp` values

Start from the tracked placeholder template, then keep the populated file local:

```bash
cp local.properties.template local.properties
# Set sdk.dir and only the service values needed for the test or feature.
```

`coreKmp` currently reads these uppercase `local.properties` names:

- `INFURA_API_KEY`, `INFURA_HOLESKY_KEY`, `INFURA_POLYGON_KEY`
- `ETHERSCAN_API_KEY`, `POLYGONSCAN_API_KEY`, `ARBISCAN_API_KEY`
- `BASESCAN_API_KEY`, `OPTIMISM_API_KEY`, `BSCSCAN_API_KEY`
- `RANGO_API_KEY`, `ZEROX_API_KEY`, `MORALIS_API_KEY`
- `TRON_API_KEY`, `GETBLOCK_API_KEY`

Do not assume an environment variable with the same name reaches BuildKonfig;
follow the current `coreKmp/build.gradle.kts` implementation.

This public tree does **not** ship 1Password setup, `scripts/setup.sh`, or
Play Console automation. Keep local values in environment variables or ignored
`local.properties`.

## Verification boundaries

For a configuration change, record separately:

1. Gradle configuration or compilation result.
2. Automated test result for the exact module.
3. Emulator or simulator evidence, if any.
4. Physical device or hardware-wallet evidence, if any.
5. Testnet or mainnet network evidence, if any.

One lane does not substitute for another.
