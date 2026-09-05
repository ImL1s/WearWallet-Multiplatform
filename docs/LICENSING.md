# Licensing

<div align="center">

**English** | **[繁體中文](./LICENSING.zh-TW.md)**

</div>

WearWallet is licensed under the **GNU General Public License v3.0 or
later (GPL-3.0-or-later)**.

- SPDX identifier: `GPL-3.0-or-later`
- Full license text: [`/LICENSE`](../LICENSE)
- Third-party attribution summary: [`/NOTICE`](../NOTICE)

## What GPL-3.0-or-later means for this repository

- You may run, study, share, and modify WearWallet for any purpose.
- If you distribute WearWallet or a modified version of it (including as
  part of a binary you ship to users), you must make the corresponding
  source code available to recipients under the same license.
- Modified files must carry a notice stating that you changed them.
- There is no warranty for the software; see the `LICENSE` file for the
  full disclaimer.
- "or later" means recipients may choose to apply the terms of GPLv3 or
  any later version of the GNU General Public License published by the
  Free Software Foundation.

This is a summary for convenience only. The [`LICENSE`](../LICENSE) file
is the authoritative legal text and controls in the event of any
discrepancy.

## Third-party components keep their own licenses

WearWallet depends on and vendors several third-party components that
are licensed independently of the GPL-3.0-or-later license covering
WearWallet's own source code. Combining GPL-covered code with
permissively licensed (e.g. MIT, Apache-2.0) third-party code in this
way is standard and license-compatible: the permissive components
remain under their original licenses, while the combined work as
distributed is governed by GPL-3.0-or-later.

Notable examples already documented in this repository:

- `modules/bitcoin-kmp/LICENSE` and `modules/secp256k1-kmp/LICENSE` —
  Apache License 2.0 (ACINQ SAS)
- `modules/secp256k1-kmp/native/secp256k1/COPYING` — MIT License
  (Pieter Wuille)
- `modules/kotlin-utxo/LICENSE` and `modules/kotlin-crypto-pure/LICENSE`
  — Apache License 2.0 (CB Studio)
- `modules/kotlin-caip-standards/LICENSE` — Apache License 2.0 (iml1s)
- Third-party libraries such as TrustWallet Core, Web3j, and Signum —
  each distributed under their own upstream license (typically
  Apache License 2.0); see each dependency's own repository for the
  authoritative text.

See [`/NOTICE`](../NOTICE) and [`THIRD_PARTY.md`](./THIRD_PARTY.md) for the
current inventory. **That inventory is not legal advice.** Nothing in this
document changes the license of any third-party code; it only explains
how those components relate to the GPL-3.0-or-later license that covers
WearWallet's own source.

Flattened modules without a LICENSE file in this tree
(`kotlin-address`, `kotlin-tx-builder`, `kotlin-blockchain-client`,
`kotlin-secure-storage`) are recorded as missing a redistributable license.
Do not invent a license for them. Corresponding `WearCapability` rows are
`UNSUPPORTED`.

## Questions

If you have questions about how the license applies to a specific use
case, please open an issue in the repository.
