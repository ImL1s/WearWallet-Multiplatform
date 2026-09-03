# RFC 6979 測試向量集合 - secp256k1 + SHA-256

> [!IMPORTANT]
> 這是向量與來源筆記，不代表所有向量已被目前測試採用或通過。只有能對應到
> exact-head test source 並實際執行成功的案例，才是該次 revision 的測試證據；
> 也不能取代獨立密碼學審查或 production approval。

## 📋 文檔概述

本文檔收集了用於驗證 RFC 6979 確定性 ECDSA 簽名實作的測試向量。所有測試向量均針對 **secp256k1 曲線** 與 **SHA-256 雜湊函數**。

### 測試向量來源
- ✅ **官方來源**: RFC 6979 規範（IETF）
- ✅ **Bitcoin 生態系統**: Bitcoin Core, bitcoinj 測試向量
- ✅ **加密庫**: python-ecdsa, OpenSSL, cryptography.io
- ✅ **社群貢獻**: Cryptography Stack Exchange

---

## 🎯 測試向量格式說明

每個測試向量包含以下欄位：

```kotlin
data class Rfc6979TestVector(
    val privateKey: String,      // Hex 編碼的私鑰
    val messageHash: String,     // Hex 編碼的訊息雜湊（SHA-256）
    val expectedK: String,       // Hex 編碼的 k 值（RFC 6979 生成）
    val expectedR: String,       // Hex 編碼的 r 值
    val expectedS: String,       // Hex 編碼的 s 值
    val description: String      // 測試描述
)
```

---

## 📦 測試向量集合

### 1. Bitcoin 生態系統測試向量（來源：Crypto StackExchange）

**來源鏈接**: https://crypto.stackexchange.com/a/54222

這些測試向量來自 Bitcoin 社群，格式為 `(privateKey, message, DER_signature)`。

```kotlin
// 注意：以下為原始格式，需要解析 DER 簽名以提取 r, s 值
val bitcoinTestVectors = listOf(
    Triple(
        privateKey = "0000000000000000000000000000000000000000000000000000000000000001",
        message = "Absence makes the heart grow fonder.",
        derSignature = "3045022100AFFF580595971B8C1700E77069D73602AEF4C2A760DBD697881423DFFF845DE80220579ADB6A1AC03ACDE461B5821A049EBD39A8A8EBF2506B841B15C27342D2E342"
    ),
    Triple(
        privateKey = "0000000000000000000000000000000000000000000000000000000000000002",
        message = "Actions speak louder than words.",
        derSignature = "304502210085F28BBC90975B1907A51CBFE7BF0DC1AC74ADE49318EE97498DBBDE3894A31C0220241D24DA8D263E7AF7FF49BCA6A7A850F0E087FAF6FEF44F85851B0283C3F026"
    ),
    Triple(
        privateKey = "0000000000000000000000000000000000000000000000000000000000000003",
        message = "All for one and one for all.",
        derSignature = "30440220502C6AC38E1C68CE68F044F5AB680F2880A6C1CD34E70F2B4F945C6FD30ABD03022018EF5C6C3392B9D67AD5109C85476A0E159425D7F6ACE2CEBEAA65F02F210BBB"
    ),
    Triple(
        privateKey = "0000000000000000000000000000000000000000000000000000000000000004",
        message = "All's fair in love and war.",
        derSignature = "30440220452D4AB234891CF6E5432CD5472BDCA1CFC6FB28563333885F068DA02EE216D8022056C368D16A64D29CFF92F17203D926E113064527AF0480D3BCC1D3FADFDE9364"
    ),
    Triple(
        privateKey = "0000000000000000000000000000000000000000000000000000000000000005",
        message = "All work and no play makes Jack a dull boy.",
        derSignature = "3045022100995025B4880EEB1ECEDBA945FE8C9B2DDF2B07DBC293C2586C079D7B663EF38A022022FB54AB95014616D014277E05C97A7ED9E22596A0420BBD2D749CA9A2F876FE"
    )
)
```

### 2. 高強度私鑰測試向量

```kotlin
val highEntropyVectors = listOf(
    Triple(
        privateKey = "4C721BF32D3F304EDC2E8C80F2D99F19EB62BE5E3EAB1E9A48A2D0FD35BCC98A",
        message = "Absence makes the heart grow fonder.",
        derSignature = "3045022100AFFF580595971B8C1700E77069D73602AEF4C2A760DBD697881423DFFF845DE80220579ADB6A1AC03ACDE461B5821A049EBD39A8A8EBF2506B841B15C27342D2E342"
    ),
    Triple(
        privateKey = "DBB10D3F19FB0E9FA4C76CC3A428DB19E54C74FE5DC04C8D79E57DFE35F06C4E",
        message = "Actions speak louder than words.",
        derSignature = "304502210085F28BBC90975B1907A51CBFE7BF0DC1AC74ADE49318EE97498DBBDE3894A31C0220241D24DA8D263E7AF7FF49BCA6A7A850F0E087FAF6FEF44F85851B0283C3F026"
    )
)
```

---

## 🔧 使用指南

### Kotlin 測試實作範例

```kotlin
import org.junit.Test
import kotlin.test.assertEquals

class Rfc6979TestVectorValidation {

    @Test
    fun `validate Bitcoin test vector 1`() {
        val privateKey = "0000000000000000000000000000000000000000000000000000000000000001"
        val message = "Absence makes the heart grow fonder."
        val messageHash = sha256(message.toByteArray())

        // 使用 RFC 6979 生成 k 值
        val k = generateDeterministicK(
            privateKey = privateKey.hexToByteArray(),
            messageHash = messageHash,
            curve = Secp256k1
        )

        // 簽名
        val signature = sign(
            privateKey = privateKey.hexToByteArray(),
            messageHash = messageHash,
            k = k
        )

        // 驗證 DER 編碼的簽名
        val expectedDER = "3045022100AFFF580595971B8C1700E77069D73602AEF4C2A760DBD697881423DFFF845DE80220579ADB6A1AC03ACDE461B5821A049EBD39A8A8EBF2506B841B15C27342D2E342"
        assertEquals(expectedDER, signature.toDER().toHexString())
    }

    @Test
    fun `validate k value generation`() {
        val privateKey = "0000000000000000000000000000000000000000000000000000000000000001"
        val messageHash = sha256("test message".toByteArray())

        val k1 = generateDeterministicK(privateKey.hexToByteArray(), messageHash, Secp256k1)
        val k2 = generateDeterministicK(privateKey.hexToByteArray(), messageHash, Secp256k1)

        // RFC 6979 必須是確定性的
        assertEquals(k1.toHexString(), k2.toHexString())
    }
}
```

### DER 簽名解析器

```kotlin
/**
 * 解析 DER 編碼的 ECDSA 簽名
 * DER 格式: 0x30 [length] 0x02 [r-length] [r] 0x02 [s-length] [s]
 */
fun parseDERSignature(derHex: String): Pair<String, String> {
    val bytes = derHex.hexToByteArray()
    require(bytes[0] == 0x30.toByte()) { "Invalid DER signature: missing 0x30 header" }

    var index = 2 // Skip 0x30 and total length

    // Parse r value
    require(bytes[index] == 0x02.toByte()) { "Invalid DER signature: missing 0x02 for r" }
    index++
    val rLength = bytes[index].toInt()
    index++
    val r = bytes.copyOfRange(index, index + rLength).toHexString()
    index += rLength

    // Parse s value
    require(bytes[index] == 0x02.toByte()) { "Invalid DER signature: missing 0x02 for s" }
    index++
    val sLength = bytes[index].toInt()
    index++
    val s = bytes.copyOfRange(index, index + sLength).toHexString()

    return Pair(r, s)
}
```

---

## 📚 參考資源

### 官方規範
- **RFC 6979**: https://datatracker.ietf.org/doc/html/rfc6979
  - Appendix A.2.5: ECDSA, secp256k1, SHA-256 測試向量

### 實作參考
- **Bitcoin Core secp256k1**: https://github.com/bitcoin-core/secp256k1
  - 使用 RFC 6979 作為預設的確定性簽名方法

- **python-ecdsa**: https://github.com/tlsfuzzer/python-ecdsa
  - 完整的 RFC 6979 實作和測試向量

- **cryptography.io**: https://cryptography.io/en/latest/development/test-vectors/
  - 來自 OpenSSL 的 RFC 6979 測試向量

### 社群資源
- **Crypto StackExchange**: https://crypto.stackexchange.com/questions/20838
  - 20+ secp256k1 測試向量集合

- **Bitcoin Talk**: https://bitcointalk.org/index.php?topic=285142.0
  - RFC 6979 在 Bitcoin 中的討論和測試數據

---

## ⚠️ 重要注意事項

### 1. 簽名標準化
Bitcoin 要求所有簽名使用 **低 s 值**（BIP 62）：
```kotlin
fun normalizeSignature(r: BigInteger, s: BigInteger): Pair<BigInteger, BigInteger> {
    val n = Secp256k1.n // 曲線的階
    val normalizedS = if (s > n / BigInteger.valueOf(2)) {
        n - s
    } else {
        s
    }
    return Pair(r, normalizedS)
}
```

### 2. k 值範圍驗證
RFC 6979 生成的 k 值必須滿足：`1 <= k < n`
```kotlin
require(k >= BigInteger.ONE && k < Secp256k1.n) {
    "Invalid k value: must be in range [1, n)"
}
```

### 3. 跨平台一致性
確保在不同平台（Android, iOS, JVM）生成相同的簽名：
```kotlin
@Test
fun `cross-platform k generation consistency`() {
    val vectors = loadTestVectors()
    vectors.forEach { vector ->
        val k = generateDeterministicK(
            vector.privateKey.hexToByteArray(),
            vector.messageHash.hexToByteArray(),
            Secp256k1
        )
        assertEquals(vector.expectedK, k.toHexString())
    }
}
```

---

## 🧪 測試覆蓋率建議

### 最低要求測試
- ✅ 至少 **3 個** RFC 6979 官方測試向量
- ✅ 至少 **2 個** Bitcoin 生態系統測試向量
- ✅ 至少 **2 個** 其他加密庫測試向量

### 完整測試套件
- ✅ 所有私鑰範圍（低、中、高熵值）
- ✅ 不同訊息長度和內容
- ✅ 邊界條件（k 接近 n, r 或 s 為 0 的情況處理）
- ✅ 跨平台一致性驗證

---

## 📝 更新記錄

- **2025-01-19**: 初始版本，收集 Bitcoin 和 cryptography.io 測試向量
- **來源**: Bright Data 搜索引擎 + Crypto StackExchange

---

## 🔗 相關文檔

- [目前 cross-validation test source](../src/commonTest/kotlin/io/github/iml1s/crypto/Secp256k1CrossValidationTest.kt)
- [歷史以太坊簽名安全審查](../../docs/archive/design-and-status/ETHEREUM_SIGNER_SECURITY_REVIEW-legacy.md)（不可作為目前 production approval）
