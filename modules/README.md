# Vendored Kotlin modules

This public tree vendors each library as a **plain directory** at the pinned
SHA from the last sanitized export. There is no `.gitmodules` and nothing to
`git submodule update`.

Active includes are in [`settings.gradle.kts`](../settings.gradle.kts):

- `:modules:kotlin-address`
- `:modules:kotlin-tx-builder`
- `:modules:kotlin-blockchain-client`
- `:modules:kotlin-secure-storage`
- `:modules:kotlin-utxo`
- `:modules:kotlin-crypto-pure`
- `:modules:kotlin-caip-standards`

Several of these trees have **no LICENSE file** in this snapshot.
Redistribution claims stay `UNSUPPORTED` in
[`docs/FEATURE_STATUS.md`](../docs/FEATURE_STATUS.md). Inventory:
[`docs/THIRD_PARTY.md`](../docs/THIRD_PARTY.md).

Also present on disk but **not** included by `settings.gradle.kts`:
`modules/bitcoin-kmp` and `modules/secp256k1-kmp` (and nested
`secp256k1`). They are inventory only. See
[`docs/THIRD_PARTY.md`](../docs/THIRD_PARTY.md).

Independently versioned upstreams live in each library's own GitHub
repository, not here.
