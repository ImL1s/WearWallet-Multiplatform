# 公開建置說明

<div align="center">

**[English](./PUBLIC_BUILD.md)** | **繁體中文**

</div>

這個公開 repo（`ImL1s/WearWallet-Multiplatform`）是 **正式開發樹**。私有 repo
（`ImL1s/WearWallet`）已凍結為歷史／維運保管庫，**永遠保持私有**。**不要**再
從私有樹 force-export 覆蓋這個 `main` 當持續同步。**不要**改寫私有 git 歷史再
推上來。

本樹 **沒有**私有 git 祖先。**不要用真實資金。**

## Clone

```bash
git clone https://github.com/ImL1s/WearWallet-Multiplatform.git
cd WearWallet-Multiplatform
```

模組是平面 vendoring（無 gitlink）。不要指望 `git submodule update` 拉私有
歷史。

## 無 token 的 debug assemble

追蹤中的 `gradle.properties` **沒有** `github.token`。文件化的本機檢查是：

```bash
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

公開 CI 同樣用 `-PpublicSnapshot=true`。當 `secrets.GH_TOKEN_PACKAGES` 為空，
workflow 用 job `GITHUB_TOKEN`，且 **不會**把 maintainer `github.token` 寫進
fork PR 的 `gradle.properties`。守衛：

```bash
python3 scripts/tests/test_check_ci_pat_fallback.py
python3 scripts/check_ci_pat_fallback.py
```

### 仍在的限制（公開 #6 維持關閉並具名）

TrustWallet Core 仍從 GitHub Packages 解析
（`https://maven.pkg.github.com/trustwallet/wallet-core`）。該 registry **沒有
token 仍可能 401**。這個 repo 的 CI 用 job `GITHUB_TOKEN` 就夠；不需要
maintainer PAT。完全匿名的乾淨 clone（空的 `GITHUB_TOKEN`／`github.token`、
空的 Gradle 依賴快取）仍可能解析失敗。

溫熱的本機 Gradle cache 可能讓 `:wear:assembleDebug` 在空的
`-Pgithub.token=`／`-Pgithub.actor=` 下成功。那 **不是**匿名乾淨 clone 證據。
在沒有快取的 `com.trustwallet:wallet-core` 產物時驗證過：Gradle
`Could not GET ... Received status code 401`；對同一 POM 的未驗證 HTTP GET
也回 **401**。本樹 **沒有** vendor Wallet Core。

選用的本機 Packages 憑證放在已忽略的 `.env`（須 `source`）、使用者層級
`~/.gradle/gradle.properties`，或 `-Pgithub.actor=`／`-Pgithub.token=` —
不要放進追蹤中的 `gradle.properties`。從
[`gradle.properties.example`](../gradle.properties.example) 複製需要的鍵到
那個使用者層級檔。範例檔是追蹤的，必須維持沒有真實 token。

- **不要**提交真實 Firebase `google-services.json`；用 `*.example`。
- 這個樹沒有 1Password／Play Console 自動化。

服務金鑰、`sdk.dir`、`publicSnapshot` 與選用 Wear 簽章：
[API 設定](./API_CONFIGURATION.zh-TW.md)。Wear debug 安裝：
[WEAR_OS_INSTALL.zh-TW.md](./WEAR_OS_INSTALL.zh-TW.md)。

### 選用 GitHub Actions secrets

| Secret | 必要？ | 角色 |
| --- | --- | --- |
| `GH_TOKEN_PACKAGES` | 否 | 若要 CI 優先用 maintainer token 的 `read:packages` PAT |
| `GH_ACTOR_NAME` | 否 | 與該 PAT 配對的 actor |

`GH_TOKEN_PACKAGES` 為空時，CI 用 job `GITHUB_TOKEN`。不要把 token 放進追蹤中
的 `gradle.properties`。Maintainer 設定路徑：倉庫 **Settings → Secrets and
variables → Actions**。Fork 讀不到這些 secret；fork PR job 不會寫
`github.token`。

## CI / CD

正式 PR 與 Actions 流量是 **`ImL1s/WearWallet-Multiplatform`**。私有
`ImL1s/WearWallet` vault 不會在 push 上跑這條 pipeline。

| Workflow | 觸發 | 實際閘什麼 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push 與對 `main`、`develop` 的 **pull_request**，以及 `workflow_dispatch` | Ubuntu：**Fail-closed unit slice**（timeout 20 分鐘）— Wear `ReleaseFeatureGateTest` + `WalletNavigationReleaseGateTest` 與 coreKmp `EvmRecipientAddressPolicyTest` + `EvmBroadcastOutcomeTest`；Wear **debug** APK assemble／上傳；精選 Markdown 連結檢查；release-manifest 攻擊面 job；PAT-fallback 守衛。**`main`** 上的必要檢查列於下方。 |
| `.github/workflows/release.yml` | tag `v*` 或手動 dispatch | Ubuntu：Wear debug APK + 原始碼 tarball + `SHA256SUMS.txt` → GitHub **prerelease** |

`main` 受保護，產品變更經 **pull request** 進入。必要 GitHub Actions 檢查
（精確 job 名稱）：

- `Test & Debug Build (Ubuntu)`
- `Fail-closed unit slice`
- `CI PAT fallback guard`
- `Markdown link check`
- `Release manifest attack surface`

Reviews **不是**必要（solo maintainer 可在那些檢查後 merge）。對 `main` 的
force-push 被擋。這仍 **不是**私有級 issue #30 完整度：沒有 3-OS 矩陣、沒有
完整 `:wear:testDebugUnitTest`、沒有把 coverage／SAST 當完整、沒有 Play 簽章
發行。公開 CI **不**跑完整 Wear／coreKmp unit suite（那些 job 在
GitHub-hosted Ubuntu 上反覆掛 30–60+ 分鐘）。Assemble + markdown 連結 +
release-manifest job **不是** Apple compile／link 矩陣。本機用
`-PpublicSnapshot=true` 跑對應 Gradle 測試。

PR 上的即時 AI review 是 GitHub Codex connector（`chatgpt-codex-connector`）。
在 PR 留言 `@codex review`。那不是必要 check，也不能取代上面的 Actions job。
見 [CONTRIBUTING.zh-TW.md](./CONTRIBUTING.zh-TW.md)。

## 發行

可下載套件在
[GitHub Releases](https://github.com/ImL1s/WearWallet-Multiplatform/releases)
（prerelease）。它們是 **debug／實驗性**，不是商店上傳，**不可當真實資金**。

在公開 repo 的 `main` 已有你要的 commit 之後打 tag：

```bash
gh workflow run "Release Snapshot" --repo ImL1s/WearWallet-Multiplatform \
  -f tag=v0.1.0-public.3
```

Workflow 會 checkout 目前 `main`，建 `:wear:assembleDebug -PpublicSnapshot=true`，
並發布：

- `WearWallet-wear-<tag>-debug.apk`
- `WearWallet-Multiplatform-<tag>-source.tar.gz`
- `SHA256SUMS.txt`

Wear debug 模擬器 overlay（不是 mainnet）：
[WEAR_QA_HARNESS.zh-TW.md](./WEAR_QA_HARNESS.zh-TW.md)。

## 匯出工具

產生這棵 orphan 樹的淨化器 **不在**這裡。不要在公開 clone 找
`scripts/export-public.sh`，也不要對這個 `main` 跑任何私有 vault 匯出。原先
「filter-repo 私有倉再公開」的做法已被拒絕。最後一次淨化匯出的來源見
[PUBLIC_SNAPSHOT.md](./PUBLIC_SNAPSHOT.md)。
