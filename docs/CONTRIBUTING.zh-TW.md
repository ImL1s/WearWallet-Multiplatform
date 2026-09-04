<div align="center">

**[English](./CONTRIBUTING.md)** | **繁體中文**

</div>

# 貢獻 WearWallet

歡迎聚焦的錯誤修正、測試、文件整理與平台強化。

## 開始前

1. 先搜尋[現有 Issue](https://github.com/ImL1s/WearWallet/issues)。
2. 閱讀[開發指南](./DEVELOPMENT_GUIDE.zh-TW.md)。
3. 以 `settings.gradle.kts` 與模組 build 檔確認現行模組和 target。
4. 不要把無關的本機變更帶進分支。

安全相關問題要說明失敗模式與證據，但不可公開助記詞、金鑰、Token、簽章檔或
使用者資料。

## 進行修改

```bash
git switch main
git pull --ff-only
git switch -c type/short-description
```

- 維持小幅差異，新增抽象前先使用既有工具。
- 缺少覆蓋時，修改行為前先加入聚焦回歸測試。
- 錢包、金鑰、簽章、交易與後端能力路徑必須維持 fail-closed。
- 除非需求明確且有理由，否則不要增加依賴。
- 不可提交 `.env`、簽章資料、Pods、build 產物、測試 log 或生成文件；根目錄
  追蹤中的 `gradle.properties` 禁止加入秘密值。

## 驗證

依[測試指南](./TESTING_GUIDE.md)選擇指令。常用檢查如下：

```bash
./scripts/check_markdown_links.py
./gradlew :coreKmp:testDebugUnitTest
./gradlew :wear:testDebugUnitTest :wear:assembleDebug
git diff --check
```

本機只需執行與改動有關的任務，但 PR 必須列出所有尚未驗證的平台、硬體或
release 路徑。

## Commit 與 Pull Request

適合時使用 conventional 且有 scope 的標題：

```text
fix(wear): reject unsupported signing backend
docs: refresh setup and testing guidance
test(coreKmp): cover denied capability tuple
```

PR 應包含：

- 簡短的問題與解法
- 受影響的檔案或模組
- 實際執行的驗證指令與結果
- 已知風險及尚未驗證的路徑
- 可見 UI 變更的截圖
- 適用時連結 Issue

沒有對應證據時，不可宣稱變更已合併、已發佈、已完成硬體驗證或可安全用於正式
環境。

## Review 互動

保持尊重，回饋要具體且可執行，並假設對方出於善意。可能危及使用者的安全問題，
在 GitHub 提供私人安全回報管道時應優先使用，不要直接公開於 Issue。
