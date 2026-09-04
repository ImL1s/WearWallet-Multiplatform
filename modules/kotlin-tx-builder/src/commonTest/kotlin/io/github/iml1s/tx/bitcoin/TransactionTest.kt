package io.github.iml1s.tx.bitcoin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransactionTest {

    @Test
    fun testTransactionSerialization() {
        // 構造一個簡單的交易
        val tx = Transaction(
            version = 2,
            inputs = listOf(
                TxInput(
                    previousTxHash = ByteArray(32) { 0x00 },
                    previousOutputIndex = 0,
                    sequence = TxInput.SEQUENCE_FINAL
                )
            ),
            outputs = listOf(
                TxOutput(
                    value = 100000,
                    scriptPubKey = ByteArray(25) { 0x00 } // dummy script
                )
            ),
            lockTime = 0
        )

        val serialized = tx.serialize()
        assertNotNull(serialized)
        assertTrue(serialized.isNotEmpty())
        
        // 驗證基本的長度:
        // Version (4) + Input count (1) + Input (32+4+1+0+4) + Output count (1) + Output (8+1+25) + Locktime (4)
        // 4 + 1 + 41 + 1 + 34 + 4 = 85 bytes
        assertEquals(85, serialized.size)
    }

    @Test
    fun testVirtualSize() {
        val tx = Transaction(
            inputs = listOf(
                TxInput(ByteArray(32), 0)
            ),
            outputs = listOf(
                TxOutput(1000, ByteArray(25))
            )
        )
        
        // Non-SegWit, vSize = size
        assertEquals(tx.serialize().size, tx.getVirtualSize())
    }
}
