## Summary

<!-- What changed and why. Link the issue. -->

Closes #

## Honesty

- [ ] Does **not** claim PRODUCTION, mainnet safety, Keystone hardware proof, or 3-OS CI unless `docs/FEATURE_STATUS.md` already says so
- [ ] Fail-closed on invalid input; no capability-gate bypass to make a test pass
- [ ] No seeds, private keys, keystores, production credentials, or real funds

## Tests

<!-- Commands you ran, or why this is docs-only. -->

```bash
# Required public unit slice (if you touched gates, send, or address policy):
./gradlew :wear:testDebugUnitTest \
  --tests '*ReleaseFeatureGateTest' \
  --tests '*WalletNavigationReleaseGateTest' \
  -PpublicSnapshot=true
./gradlew :coreKmp:testDebugUnitTest \
  --tests '*EvmRecipientAddressPolicyTest' \
  --tests '*EvmBroadcastOutcomeTest' \
  -PpublicSnapshot=true
```

## Security

Vulnerabilities are **not** discussed in public PRs. See [SECURITY.md](../SECURITY.md).
