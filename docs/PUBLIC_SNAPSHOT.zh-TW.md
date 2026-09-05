# 公開樹來源

<div align="center">

**[English](./PUBLIC_SNAPSHOT.md)** | **繁體中文**

</div>

這個公開 repo（`ImL1s/WearWallet-Multiplatform`）是 **正式開發樹**。私有 repo
（`ImL1s/WearWallet`）已凍結為歷史／維運保管庫，**永遠保持私有**。**不要**再
從私有樹 force-export 覆蓋這個 `main` 當持續同步。

本樹 **沒有**私有 git 祖先。它以淨化過的 orphan export 開始，不再是定期被替換
的私有倉快照。我們**不會**改寫私有 git 歷史再推上這個倉庫。原先「filter-repo
後把私有倉公開」的做法已被拒絕。

## 這裡有什麼

- 平面 `modules/*` 樹，釘在匯出時的 SHA（不是 git submodule）。
- 公開 CI 使用 `-PpublicSnapshot=true`（沒有 production Firebase 設定）。
- Wear `assembleDebug`、**Fail-closed unit slice** job（不是完整 unit
  suite）、精選 Markdown 連結檢查、release-manifest 攻擊面 job，以及
  GitHub-hosted Ubuntu 上的 PAT-fallback 守衛。

## 這裡沒有什麼

- 私有 commit 歷史、1Password 流程、Play 上傳金鑰或 self-hosted CI。
- Production `google-services.json`、keystore 或 agent／本機 IDE metadata。
- CI 裡的完整 unit suite。必要 unit slice **仍不是** issue #30、商店審核、
  mainnet 安全、3-OS CI 或完整測試覆蓋的證據。

## 建置

見 [PUBLIC_BUILD.md](./PUBLIC_BUILD.md) 與根目錄 [README.md](../README.md)。
**不要用真實資金。**

## 最後一次淨化匯出

- 私有原始碼 tip：`8be876ef60d7d27418232a799f1c1a93aa3b0ca7`
- 匯出 UTC：`2026-09-04T02:16:23Z`
- 方法：私有 vault 的 `scripts/export-public.sh`（blacklist + overlay +
  守衛）。該腳本 **不是**這個公開樹的一部分。

這個倉庫刻意 **沒有**私有開發歷史。這是最後一次打算用私有樹覆寫公開
`main`。使用者不應執行任何匯出腳本。
根目錄 `export-manifest.json` 是那次匯出產生的守衛／掃描 dump。它被
gitignore，不是原始碼；不要提交。
