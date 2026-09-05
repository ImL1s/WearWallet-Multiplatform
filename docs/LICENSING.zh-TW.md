# 授權

<div align="center">

**[English](./LICENSING.md)** | **繁體中文**

</div>

WearWallet 以 **GNU General Public License v3.0 or later
（GPL-3.0-or-later）** 授權。

- SPDX 識別碼：`GPL-3.0-or-later`
- 完整授權本文：[`/LICENSE`](../LICENSE)
- 第三方歸屬摘要：[`/NOTICE`](../NOTICE)

## GPL-3.0-or-later 對本倉庫的意義

- 你可以為任何目的執行、研究、分享與修改 WearWallet。
- 若你散布 WearWallet 或其修改版（包含作為發給使用者的二進位的一部分），必須
  以相同授權向接收者提供對應原始碼。
- 修改過的檔案必須附上你已修改的聲明。
- 軟體沒有擔保；完整免責見 `LICENSE`。
- 「or later」表示接收者可選擇適用 GPLv3，或 Free Software Foundation 之後
  發布的任何更新版 GNU GPL。

這只是便利摘要。[`LICENSE`](../LICENSE) 才是具拘束力的法律本文；若有出入以
該檔為準。

## 第三方元件維持各自授權

WearWallet 依賴並 vendor 多個獨立於 GPL-3.0-or-later（涵蓋 WearWallet 自身
原始碼）的第三方元件。把 GPL 涵蓋的程式與寬鬆授權（例如 MIT、Apache-2.0）
第三方程式這樣組合是常見且授權相容的做法：寬鬆元件維持原授權，散布時的
組合作品由 GPL-3.0-or-later 管轄。

倉庫內已記載的例子：

- `modules/bitcoin-kmp/LICENSE` 與 `modules/secp256k1-kmp/LICENSE` —
  Apache License 2.0（ACINQ SAS）
- `modules/secp256k1-kmp/native/secp256k1/COPYING` — MIT License
  （Pieter Wuille）
- `modules/kotlin-utxo/LICENSE` 與 `modules/kotlin-crypto-pure/LICENSE`
  — Apache License 2.0（CB Studio）
- `modules/kotlin-caip-standards/LICENSE` — Apache License 2.0（iml1s）
- TrustWallet Core、Web3j、Signum 等第三方函式庫 — 各自上游授權（多為
  Apache License 2.0）；以各依賴自己的倉庫為準。

現行清冊見 [`/NOTICE`](../NOTICE) 與 [`THIRD_PARTY.md`](./THIRD_PARTY.md)。
**該清冊不是法律意見。** 本文件不改變任何第三方程式的授權；只說明那些元件
與涵蓋 WearWallet 自身原始碼的 GPL-3.0-or-later 的關係。

這個樹裡沒有 LICENSE 檔的平面模組（`kotlin-address`、`kotlin-tx-builder`、
`kotlin-blockchain-client`、`kotlin-secure-storage`）記為缺少可散布授權。
不要為它們發明授權。對應的 `WearCapability` 列是 `UNSUPPORTED`。

## 問題

特定使用情境如何適用授權，請在倉庫開 Issue。
