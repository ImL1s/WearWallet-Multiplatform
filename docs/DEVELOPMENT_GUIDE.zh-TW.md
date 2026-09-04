# 開發

<div align="center">

**[English](./DEVELOPMENT_GUIDE.md)** | **繁體中文**

</div>

> 這個公開 repo（`ImL1s/WearWallet-Multiplatform`）是 **正式開發樹**。
> 禁止把名稱或文件引用還原成 `WearWallet-public`。Clone、CI 與發佈事實見
> [`PUBLIC_BUILD.md`](./PUBLIC_BUILD.md)；能力聲明見
> [`FEATURE_STATUS.md`](./FEATURE_STATUS.md)。私有倉永遠保持私有。
> **不要用真實資金。**

## Clone

```bash
git clone https://github.com/ImL1s/WearWallet-Multiplatform.git
cd WearWallet-Multiplatform
```

`modules/` 是平面 vendoring（無 `.gitmodules`）。不要跑 `git submodule update`。

## 環境需求

- JDK 17
- Android SDK 35（Wear / mobile）
- 選用：TrustWallet Core 的 GitHub Packages 若回 401，再準備具 `read:packages`
  的 token。CI 用 job `GITHUB_TOKEN`，不要求 maintainer PAT。追蹤中的
  `gradle.properties` 沒有 `github.token`。

## 本機建置／測試

```bash
chmod +x gradlew
./gradlew :coreKmp:testDebugUnitTest :wear:testDebugUnitTest :wear:assembleDebug -PpublicSnapshot=true
```

Wear debug 模擬器 overlay（不是 mainnet）：[WEAR_QA_HARNESS.md](./WEAR_QA_HARNESS.md)。

Apple / watchOS 的 Xcode 建置**不是**這個公開 tip 的 CI 證據。公開 CI 包含
**Fail-closed unit slice**（20 分鐘 timeout）、Wear `assembleDebug`、Markdown
連結、release-manifest job 與 PAT-fallback 守衛 — 不是完整 unit suite、不是
3-OS CI、也不是 issue #30。

公開樹沒有 1Password 或 Play Console 自動化。憑證說明見
[PUBLIC_BUILD.md](./PUBLIC_BUILD.md)。

## 貢獻

Issue 與 PR 開在
[ImL1s/WearWallet-Multiplatform](https://github.com/ImL1s/WearWallet-Multiplatform)。
安全回報見 [SECURITY.md](../SECURITY.md) / GitHub Security Advisories。

不要提交秘密、keystore 或 production Firebase 設定。
