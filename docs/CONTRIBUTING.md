# Contributing

Thanks for interest in WearWallet. This public repo
([ImL1s/WearWallet-public](https://github.com/ImL1s/WearWallet-public)) is the
**canonical development tree**. It is experimental — not a production wallet.

## How to contribute

1. Fork [ImL1s/WearWallet-public](https://github.com/ImL1s/WearWallet-public).
2. Create a branch from `main`.
3. Keep changes focused; include tests when touching wallet/security code.
4. Open a pull request against `ImL1s/WearWallet-public`.

The private repo (`ImL1s/WearWallet`) is frozen as a historical / ops vault
**forever**. Do **not** force-export from private over this `main`. Product
work lands here. We will **not** rewrite private git history and push it.

## Security

Do **not** open public issues for vulnerabilities. Use GitHub Security Advisories
or the contact in [SECURITY.md](../SECURITY.md).

Never commit mnemonics, private keys, keystores, or production API credentials.

## Local checks

```bash
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest -PpublicSnapshot=true
./scripts/check_markdown_links.py
```

Public CI today is Wear `assembleDebug`, Markdown links, the release-manifest
job, and the PAT-fallback guard. That is **not** a full unit suite. Run the
Gradle tests locally when you change wallet or security code.

```bash
python3 scripts/tests/test_check_ci_pat_fallback.py
python3 scripts/check_ci_pat_fallback.py
```

See [PUBLIC_BUILD.md](./PUBLIC_BUILD.md) for CI/CD and release package details.
