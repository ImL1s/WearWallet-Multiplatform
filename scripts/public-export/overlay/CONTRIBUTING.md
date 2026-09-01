# Contributing (public snapshot)

Thanks for interest in WearWallet. This tree is an **experimental sanitized
public snapshot** — not a production wallet.

## How to contribute

1. Fork [ImL1s/WearWallet-public](https://github.com/ImL1s/WearWallet-public).
2. Create a branch from `main`.
3. Keep changes focused; include tests when touching wallet/security code.
4. Open a pull request against `ImL1s/WearWallet-public`.

Maintainers may port accepted changes into the private canonical repository.

## Security

Do **not** open public issues for vulnerabilities. Use GitHub Security Advisories
or the contact in [SECURITY.md](../SECURITY.md).

Never commit mnemonics, private keys, keystores, or production API credentials.

## Local checks

```bash
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest
```

See [PUBLIC_BUILD.md](./PUBLIC_BUILD.md) for CI/CD and release package details.
