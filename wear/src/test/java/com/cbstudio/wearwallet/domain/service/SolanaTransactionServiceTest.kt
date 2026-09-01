package com.cbstudio.wearwallet.domain.service

import org.junit.Test
import org.junit.Assert.*

/**
 * SolanaTransactionService 單元測試
 * 驗證 P0 問題修復的正確性
 */
class SolanaTransactionServiceTest {

    /**
     * 測試問題 2: MessageHeader 計算
     */
    @Test
    fun `test MessageHeader calculation with mixed accounts`() {
        val accounts = listOf(
            // Signer + Writable
            AccountMeta("fromPubkey", isSigner = true, isWritable = true),
            // Non-Signer + Writable
            AccountMeta("toPubkey", isSigner = false, isWritable = true),
            // Non-Signer + Readonly (System Program)
            AccountMeta("11111111111111111111111111111111", isSigner = false, isWritable = false)
        )

        val service = SolanaTransactionService()

        // 使用反射訪問私有方法進行測試
        val calculateMethod = SolanaTransactionService::class.java.getDeclaredMethod(
            "calculateMessageHeader",
            List::class.java
        )
        calculateMethod.isAccessible = true

        val header = calculateMethod.invoke(service, accounts) as MessageHeader

        // 驗證結果
        assertEquals("應該有 1 個簽名者", 1, header.numRequiredSignatures.toInt())
        assertEquals("簽名者中沒有只讀賬戶", 0, header.numReadonlySignedAccounts.toInt())
        assertEquals("應該有 1 個只讀未簽名賬戶", 1, header.numReadonlyUnsignedAccounts.toInt())
    }

    /**
     * 測試問題 2: MessageHeader 計算 - 包含只讀簽名者
     */
    @Test
    fun `test MessageHeader calculation with readonly signer`() {
        val accounts = listOf(
            // Signer + Writable
            AccountMeta("signer1", isSigner = true, isWritable = true),
            // Signer + Readonly (例如：某些 multisig 場景)
            AccountMeta("signer2", isSigner = true, isWritable = false),
            // Non-Signer + Readonly
            AccountMeta("program", isSigner = false, isWritable = false)
        )

        val service = SolanaTransactionService()
        val calculateMethod = SolanaTransactionService::class.java.getDeclaredMethod(
            "calculateMessageHeader",
            List::class.java
        )
        calculateMethod.isAccessible = true

        val header = calculateMethod.invoke(service, accounts) as MessageHeader

        assertEquals("應該有 2 個簽名者", 2, header.numRequiredSignatures.toInt())
        assertEquals("簽名者中有 1 個只讀賬戶", 1, header.numReadonlySignedAccounts.toInt())
        assertEquals("應該有 1 個只讀未簽名賬戶", 1, header.numReadonlyUnsignedAccounts.toInt())
    }

    /**
     * 測試問題 3: 賬戶排序
     */
    @Test
    fun `test account sorting follows Solana specification`() {
        val unsorted = listOf(
            // Non-Signer + Readonly (應該最後)
            AccountMeta("readonly", isSigner = false, isWritable = false),
            // Signer + Writable (應該第一)
            AccountMeta("signer", isSigner = true, isWritable = true),
            // Non-Signer + Writable (應該中間)
            AccountMeta("writable", isSigner = false, isWritable = true)
        )

        val service = SolanaTransactionService()
        val sortMethod = SolanaTransactionService::class.java.getDeclaredMethod(
            "sortAccountsForSolana",
            List::class.java
        )
        sortMethod.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val sorted = sortMethod.invoke(service, unsorted) as List<AccountMeta>

        // 驗證排序順序
        assertTrue("第一個應該是 Signer + Writable",
            sorted[0].isSigner && sorted[0].isWritable)
        assertTrue("第二個應該是 Non-Signer + Writable",
            !sorted[1].isSigner && sorted[1].isWritable)
        assertTrue("最後一個應該是 Non-Signer + Readonly",
            !sorted[2].isSigner && !sorted[2].isWritable)
    }

    /**
     * 測試問題 3: 賬戶排序 - 複雜場景
     */
    @Test
    fun `test account sorting with all four categories`() {
        val unsorted = listOf(
            // 類別 4: Non-Signer + Readonly
            AccountMeta("readonly1", isSigner = false, isWritable = false),
            // 類別 2: Signer + Readonly
            AccountMeta("signerReadonly", isSigner = true, isWritable = false),
            // 類別 3: Non-Signer + Writable
            AccountMeta("writable1", isSigner = false, isWritable = true),
            // 類別 1: Signer + Writable
            AccountMeta("signerWritable", isSigner = true, isWritable = true),
            // 類別 4: 另一個 Non-Signer + Readonly
            AccountMeta("readonly2", isSigner = false, isWritable = false)
        )

        val service = SolanaTransactionService()
        val sortMethod = SolanaTransactionService::class.java.getDeclaredMethod(
            "sortAccountsForSolana",
            List::class.java
        )
        sortMethod.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val sorted = sortMethod.invoke(service, unsorted) as List<AccountMeta>

        // 驗證 Solana 規範的排序
        // 1. Signer + Writable
        assertTrue("索引 0 應該是 Signer + Writable",
            sorted[0].isSigner && sorted[0].isWritable)

        // 2. Signer + Readonly
        assertTrue("索引 1 應該是 Signer + Readonly",
            sorted[1].isSigner && !sorted[1].isWritable)

        // 3. Non-Signer + Writable
        assertTrue("索引 2 應該是 Non-Signer + Writable",
            !sorted[2].isSigner && sorted[2].isWritable)

        // 4. Non-Signer + Readonly (2 個)
        assertTrue("索引 3 應該是 Non-Signer + Readonly",
            !sorted[3].isSigner && !sorted[3].isWritable)
        assertTrue("索引 4 應該是 Non-Signer + Readonly",
            !sorted[4].isSigner && !sorted[4].isWritable)
    }

    /**
     * 測試問題 4: Compact-u16 編碼
     */
    @Test
    fun `test compactU16Encode for single byte values`() {
        // 0-127 範圍應該編碼為 1 字節
        val result1 = SolanaUtils.compactU16Encode(0)
        assertArrayEquals("0 應該編碼為 [0]", byteArrayOf(0), result1)

        val result127 = SolanaUtils.compactU16Encode(127)
        assertArrayEquals("127 應該編碼為 [127]", byteArrayOf(127), result127)

        val result50 = SolanaUtils.compactU16Encode(50)
        assertArrayEquals("50 應該編碼為 [50]", byteArrayOf(50), result50)
    }

    @Test
    fun `test compactU16Encode for two byte values`() {
        // 128-16383 範圍應該編碼為 2 字節
        val result128 = SolanaUtils.compactU16Encode(128)
        assertEquals("128 應該編碼為 2 字節", 2, result128.size)
        // 128 = 0x80 = 10000000 (二進制)
        // 編碼: [0x80 | 0x80, 0x01] = [0x80, 0x01]
        assertArrayEquals("128 應該編碼為 [0x80, 0x01]",
            byteArrayOf(0x80.toByte(), 0x01), result128)

        val result256 = SolanaUtils.compactU16Encode(256)
        assertEquals("256 應該編碼為 2 字節", 2, result256.size)
        // 256 = 0x100 = 100000000 (二進制)
        // 低 7 位: 0, 高位: 2
        // 編碼: [0x80, 0x02]
        assertArrayEquals("256 應該編碼為 [0x80, 0x02]",
            byteArrayOf(0x80.toByte(), 0x02), result256)
    }

    @Test
    fun `test compactU16Encode for three byte values`() {
        // 16384+ 應該編碼為 3 字節
        val result16384 = SolanaUtils.compactU16Encode(16384)
        assertEquals("16384 應該編碼為 3 字節", 3, result16384.size)
        // 16384 = 0x4000
        // 編碼: [0x80, 0x80, 0x01]
        assertArrayEquals("16384 應該編碼為 [0x80, 0x80, 0x01]",
            byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x01), result16384)

        val result65535 = SolanaUtils.compactU16Encode(65535)
        assertEquals("65535 應該編碼為 3 字節", 3, result65535.size)
    }

    /**
     * 測試問題 4: Compact-u16 編碼邊界值
     */
    @Test
    fun `test compactU16Encode boundary values`() {
        // 邊界值 127 (最大單字節)
        val result127 = SolanaUtils.compactU16Encode(127)
        assertEquals("127 應該是單字節", 1, result127.size)

        // 邊界值 128 (最小雙字節)
        val result128 = SolanaUtils.compactU16Encode(128)
        assertEquals("128 應該是雙字節", 2, result128.size)

        // 邊界值 16383 (最大雙字節)
        val result16383 = SolanaUtils.compactU16Encode(16383)
        assertEquals("16383 應該是雙字節", 2, result16383.size)

        // 邊界值 16384 (最小三字節)
        val result16384 = SolanaUtils.compactU16Encode(16384)
        assertEquals("16384 應該是三字節", 3, result16384.size)
    }

    /**
     * 測試完整的交易構建流程（整合測試）
     */
    @Test
    fun `test complete transaction message structure`() {
        // 創建一個簡單的 SOL 轉帳交易
        val fromPubkey = "11111111111111111111111111111111"
        val toPubkey = "22222222222222222222222222222222"
        val systemProgram = "11111111111111111111111111111111"

        val instruction = SolanaInstruction(
            programId = systemProgram,
            accounts = listOf(
                AccountMeta(fromPubkey, isSigner = true, isWritable = true),
                AccountMeta(toPubkey, isSigner = false, isWritable = true)
            ),
            data = ByteArray(12) // Transfer 指令數據
        )

        // 使用反射構建消息
        val service = SolanaTransactionService()
        val buildMethod = SolanaTransactionService::class.java.getDeclaredMethod(
            "buildTransactionMessage",
            List::class.java,
            String::class.java,
            String::class.java
        )
        buildMethod.isAccessible = true

        val message = buildMethod.invoke(
            service,
            listOf(instruction),
            "DummyBlockhash111111111111111111",
            fromPubkey
        ) as SolanaMessage

        // 驗證消息結構
        assertEquals("應該有 1 個簽名者", 1, message.header.numRequiredSignatures.toInt())
        assertTrue("賬戶應該按正確順序排列", message.accountKeys[0] == fromPubkey)
        assertEquals("應該有 1 個指令", 1, message.instructions.size)
    }
}
