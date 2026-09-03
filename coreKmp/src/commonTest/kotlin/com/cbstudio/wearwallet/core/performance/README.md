# coreKmp 性能基準測試

## 🚀 快速開始

這是 coreKmp 模塊的綜合性能基準測試套件，用於測量和優化關鍵路徑的性能。

### 運行所有性能測試

```bash
./gradlew :coreKmp:testDebugUnitTest --tests "ComprehensivePerformanceBenchmark"
```

### 運行特定測試

```bash
# 測試地址生成性能
./gradlew :coreKmp:testDebugUnitTest \
  --tests "ComprehensivePerformanceBenchmark.benchmarkAddressGeneration_Ethereum"

# 測試簽名性能
./gradlew :coreKmp:testDebugUnitTest \
  --tests "ComprehensivePerformanceBenchmark.benchmarkSigning_ECDSA_secp256k1"

# 生成完整性能報告
./gradlew :coreKmp:testDebugUnitTest \
  --tests "ComprehensivePerformanceBenchmark.generateComprehensivePerformanceReport"
```

---

## 📊 性能目標

| 操作類型 | 性能目標 | 重要性 |
|---------|---------|--------|
| 地址生成 | < 100ms | 🔴 高 |
| 交易建構 | < 500ms | 🔴 高 |
| 簽名操作 | < 200ms | 🔴 高 |
| RPC 調用 | < 2s | 🟡 中 |
| 內存使用 | < 50MB | 🟡 中 |
| 並發加速 | > 2x | 🟢 低 |

---

## 🧪 測試覆蓋

### 1. 地址生成測試
- `benchmarkAddressGeneration_Bitcoin()` - Bitcoin 地址生成
- `benchmarkAddressGeneration_Ethereum()` - Ethereum 地址生成
- `benchmarkAddressGeneration_Solana()` - Solana 地址生成
- `benchmarkBatchAddressGeneration()` - 批次生成 100 個地址

### 2. 交易建構測試
- `benchmarkTransactionCreation_Ethereum()` - Ethereum EIP-1559 交易
- `benchmarkTransactionCreation_Bitcoin()` - Bitcoin UTXO 交易
- `benchmarkTransactionCreation_Solana()` - Solana 交易

### 3. 簽名性能測試
- `benchmarkSigning_ECDSA_secp256k1()` - ECDSA 簽名
- `benchmarkSigning_Ed25519()` - Ed25519 簽名
- `benchmarkSigning_Verification()` - 簽名驗證

### 4. RPC 調用測試
- `benchmarkRPCCalls_Ethereum()` - Ethereum RPC（餘額、Gas Price）
- `benchmarkRPCCalls_Solana()` - Solana RPC
- `benchmarkConcurrentRPC()` - 並發 RPC 調用

### 5. 系統測試
- `benchmarkMemoryUsage()` - 內存使用分析
- `benchmarkConcurrentProcessing()` - 並發處理能力

---

## 🔧 使用測試框架

### 基本用法

```kotlin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Test
fun myPerformanceTest() = runTest {
    val measurer = PerformanceMeasurer()

    val result = measurer.measurePerformance(
        name = "我的操作",
        iterations = 50,                     // 測試 50 次
        warmup = 10,                         // 預熱 10 次
        target = 100.milliseconds            // 目標 < 100ms
    ) {
        // 要測試的操作
        myOperation()
    }

    measurer.printResult(result)

    // 驗證是否達標
    assertTrue(result.passed, "操作應該 < 100ms")
}
```

### 批次測試

```kotlin
@Test
fun batchPerformanceTests() = runTest {
    val measurer = PerformanceMeasurer()
    val results = mutableListOf<BenchmarkResult>()

    // 測試 1
    results.add(measurer.measurePerformance(
        name = "測試 1",
        iterations = 100,
        target = 50.milliseconds
    ) {
        operation1()
    })

    // 測試 2
    results.add(measurer.measurePerformance(
        name = "測試 2",
        iterations = 100,
        target = 100.milliseconds
    ) {
        operation2()
    })

    // 打印所有結果
    measurer.printResults(results)
}
```

### 並發測試

```kotlin
@Test
fun concurrentOperationTest() = runTest {
    // 順序執行
    val sequentialTime = measureTime {
        repeat(10) {
            performOperation()
        }
    }

    // 並發執行
    val concurrentTime = measureTime {
        coroutineScope {
            (0 until 10).map {
                async(Dispatchers.Default) {
                    performOperation()
                }
            }.awaitAll()
        }
    }

    val speedup = sequentialTime.inWholeMilliseconds.toDouble() /
                  concurrentTime.inWholeMilliseconds

    println("順序: $sequentialTime")
    println("並發: $concurrentTime")
    println("加速比: ${"%.2f".format(speedup)}x")

    assertTrue(concurrentTime < sequentialTime, "並發應該更快")
}
```

---

## 📈 測試結果解讀

### 輸出範例

```
=== Ethereum 地址生成 ✅ PASS ===
迭代次數: 50
總時間: 4.2s
平均時間: 84ms (目標: 100ms)
最快: 65ms
最慢: 112ms
吞吐量: 11.90 ops/sec
```

### 結果欄位說明

- **迭代次數**: 測試執行的次數
- **總時間**: 所有迭代的總時間
- **平均時間**: 單次操作的平均時間
- **最快/最慢**: 最快和最慢的單次操作時間
- **吞吐量**: 每秒可以執行的操作數 (ops/sec)
- **狀態**: ✅ PASS (通過) 或 ❌ FAIL (失敗)

---

## 🎯 性能優化建議

當測試顯示性能不達標時，參考以下優化建議：

### 地址生成慢 (> 100ms)
- [ ] 實現 Extended Key 緩存
- [ ] 使用原生加密庫 (libsecp256k1)
- [ ] 批次操作使用並發處理

### 交易建構慢 (> 500ms)
- [ ] 緩存網路參數 (nonce, gas price)
- [ ] 優化 UTXO 選擇算法
- [ ] 使用連接池管理 RPC 連接

### 簽名慢 (> 200ms)
- [ ] 確認使用了最優化的加密庫
- [ ] 考慮硬體加速 (Secure Enclave, KeyStore)
- [ ] 檢查是否有不必要的重複計算

### RPC 調用慢 (> 2s)
- [ ] 實現請求批處理
- [ ] 添加本地緩存層
- [ ] 使用多個 RPC 端點負載均衡
- [ ] 實現超時和重試機制

### 內存使用高 (> 50MB)
- [ ] 使用對象池減少 GC 壓力
- [ ] 及時釋放大型數據結構
- [ ] 使用流式處理替代一次性加載

---

## 📚 相關文檔

- [綜合性能基準測試](./ComprehensivePerformanceBenchmark.kt)
- [加密性能測試](./CryptoPerformanceTest.kt)

---

## 🔍 故障排除

### 測試無法執行

**問題**: `SDK 尚未初始化` 錯誤
**解決**: 確保在測試中正確初始化了 SDK

```kotlin
@Before
fun setup() {
    sdk.initialize(SDKConfig(
        network = "mainnet",
        apiKey = "test-key"
    ))
}
```

**問題**: 編譯錯誤
**解決**: 確保所有依賴已正確配置

```bash
./gradlew :coreKmp:clean
./gradlew :coreKmp:build
```

### 測試結果不穩定

**問題**: 每次運行結果差異很大
**解決**:
- 增加預熱次數 (warmup)
- 增加迭代次數 (iterations)
- 關閉其他應用程序
- 使用飛行模式（非 RPC 測試）

### 內存測試不准確

**問題**: 無法獲取準確的內存數據
**解決**: 在實際設備上使用專業工具
- Android: Android Profiler
- iOS/watchOS: Xcode Instruments

---

## 💡 最佳實踐

### 1. 預熱是必須的
```kotlin
// ❌ 錯誤 - 沒有預熱
measurePerformance(name = "測試", iterations = 100, warmup = 0) {
    operation()
}

// ✅ 正確 - 有預熱
measurePerformance(name = "測試", iterations = 100, warmup = 10) {
    operation()
}
```

### 2. 選擇合適的迭代次數
```kotlin
// 快速操作 (< 10ms) - 多次迭代
measurePerformance(iterations = 1000) { fastOperation() }

// 中速操作 (10-100ms) - 標準迭代
measurePerformance(iterations = 100) { mediumOperation() }

// 慢速操作 (> 100ms) - 少次迭代
measurePerformance(iterations = 10) { slowOperation() }
```

### 3. 避免外部因素干擾
```kotlin
// ❌ 錯誤 - 包含網路延遲
measurePerformance("簽名性能") {
    val data = fetchDataFromAPI()  // 網路調用！
    signData(data)
}

// ✅ 正確 - 只測試簽名
measurePerformance("簽名性能") {
    val data = mockData  // 使用模擬數據
    signData(data)
}
```

### 4. 使用有意義的測試名稱
```kotlin
// ❌ 不好
measurePerformance("test1") { ... }

// ✅ 好
measurePerformance("Ethereum 地址生成 (secp256k1)") { ... }
```

---

## 🚦 CI/CD 整合

### GitHub Actions 範例

```yaml
name: Performance Tests

on:
  pull_request:
    branches: [main, develop]

jobs:
  performance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Run Performance Tests
        run: |
          ./gradlew :coreKmp:testDebugUnitTest \
            --tests "ComprehensivePerformanceBenchmark"

      - name: Check Performance Regression
        run: |
          python scripts/check_performance.py \
            --threshold 10%
```

---

## 📞 支援

如有問題或建議，請：
1. 查看相關文檔
2. 檢查故障排除部分
3. 聯絡 WearWallet 團隊

---

**最後更新**: 2025-08-22
**版本**: 1.0.0
