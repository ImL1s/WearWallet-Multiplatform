# Contributing

Thanks for interest in WearWallet. This public repo
([ImL1s/WearWallet-Multiplatform](https://github.com/ImL1s/WearWallet-Multiplatform)) is the
**canonical development tree**. It is experimental — not a production wallet.

## How to contribute

1. Fork [ImL1s/WearWallet-Multiplatform](https://github.com/ImL1s/WearWallet-Multiplatform).
2. Create a branch from `main`.
3. Keep changes focused; include tests when touching wallet/security code.
4. Open a pull request against `ImL1s/WearWallet-Multiplatform`.

The private repo (`ImL1s/WearWallet`) is frozen as a historical / ops vault
**forever**. Do **not** force-export from private over this `main`. Product
work lands here. We will **not** rewrite private git history and push it.

## Security

Do **not** open public issues for vulnerabilities. Use GitHub Security Advisories
or the contact in [SECURITY.md](./SECURITY.md).

Never commit mnemonics, private keys, keystores, or production API credentials.

Use the bug/feature forms under `.github/ISSUE_TEMPLATE/` and the pull-request
template. Do not add `CODEOWNERS` with invented emails.

## Local checks

```bash
./gradlew :wear:testDebugUnitTest \
  --tests '*ReleaseFeatureGateTest' \
  --tests '*WalletNavigationReleaseGateTest' \
  -PpublicSnapshot=true
./gradlew :coreKmp:testDebugUnitTest \
  --tests '*EvmRecipientAddressPolicyTest' \
  --tests '*EvmBroadcastOutcomeTest' \
  -PpublicSnapshot=true
./scripts/check_markdown_links.py
```

Public CI's required unit slice is those four test classes (job **Fail-closed
unit slice**, timeout 20 minutes), plus Wear `assembleDebug`, Markdown links,
the release-manifest job, and the PAT-fallback guard. That is **still not** a
full unit suite and **not** private-grade issue #30 completeness. Run the
broader Gradle tests locally when you change wallet or security code.

```bash
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest -PpublicSnapshot=true
```

```bash
python3 scripts/tests/test_check_ci_pat_fallback.py
python3 scripts/check_ci_pat_fallback.py
```

See [PUBLIC_BUILD.md](./docs/PUBLIC_BUILD.md) for CI/CD and release package details.
