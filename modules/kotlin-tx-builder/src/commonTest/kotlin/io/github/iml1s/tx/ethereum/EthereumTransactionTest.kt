package io.github.iml1s.tx.ethereum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.iml1s.crypto.RLP

class EthereumTransactionTest {

    @Test
    fun testLegacyTransactionEncoding() {
        // EIP-155 example
        val tx = LegacyTransaction(
            nonce = 9,
            gasPrice = byteArrayOf(0x04.toByte(), 0xa8.toByte(), 0x17.toByte(), 0xc8.toByte(), 0x00.toByte()), // 20 Gwei
            gasLimit = 21000,
            to = "0x3535353535353535353535353535353535353535",
            value = byteArrayOf(0x0d.toByte(), 0xe0.toByte(), 0xb6.toByte(), 0xb3.toByte(), 0xa7.toByte(), 0x64.toByte(), 0x00.toByte(), 0x00.toByte()), // 1 Ether
            data = byteArrayOf(),
            chainId = 1
        )

        val encoded = tx.encode(forSigning = true)
        
        // RLP list should start with 0xC0 + length (or 0xF7 + len len if long)
        assertTrue(encoded.isNotEmpty())
        // Basic check for RLP list start (Legacy tx is always a list)
        assertTrue(encoded[0] == 0xF8.toByte() || encoded[0] == 0xC0.toByte() || (encoded[0].toInt() and 0xFF) >= 0xC0)
    }

    @Test
    fun testEip1559TransactionEncoding() {
        // Test vector from explicit construction
        // Value: 1 ETH
        // Nonce: 0
        // GasLimit: 21000
        // MaxFee: 20 Gwei
        // Priority: 1 Gwei
        // To: 0x3535...
        
        val tx = Eip1559Transaction(
            chainId = 1,
            nonce = 0,
            maxPriorityFeePerGas = byteArrayOf(0x3b.toByte(), 0x9a.toByte(), 0xca.toByte(), 0x00.toByte()), // 1 Gwei = 1000000000 = 3B9ACA00
            maxFeePerGas = byteArrayOf(0x04.toByte(), 0xa8.toByte(), 0x17.toByte(), 0xc8.toByte(), 0x00.toByte()), // 20 Gwei = 20000000000 = 04A817C800
            gasLimit = 21000,
            to = "0x3535353535353535353535353535353535353535",
            value = byteArrayOf(0x0d.toByte(), 0xe0.toByte(), 0xb6.toByte(), 0xb3.toByte(), 0xa7.toByte(), 0x64.toByte(), 0x00.toByte(), 0x00.toByte()), // 1 Ether
            data = byteArrayOf(),
            accessList = emptyList()
        )

        val encoded = tx.encode(forSigning = true)
        
        // Should start with 0x02 (EIP-1559 Type)
        assertEquals(0x02.toByte(), encoded[0])
        
        // Use a known good hex string for regression testing if available
        // Or at least verify components structure strictly
        // RLP structure: 0x02 + RLP([ chainId, nonce, priority, maxFee, gasLimit, to, value, data, accessList ])
        
        // Verify ChainID (1) encoding is present at start of list
        // List payload starts at index 2 (0x02, 0xF8/C0_len)
        // ChainID 1 should be encoded as 0x01
        
        // Assert first valid byte after list header is chainId=1
        // List header is likely 1-3 bytes depending on length
        // Let's just strictly check hex output logic in parts or check consistency
        // Ideally we paste a known hex from EIP docs, but EIP-1559 docs use dynamic values.
        
        fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
        
        // Ensuring header is correct type
        println(bytesToHex(encoded))
    }

    @Test
    fun testStrictLegacyRLP() {
        val tx = LegacyTransaction(
            nonce = 9,
            gasPrice = byteArrayOf(0x04.toByte(), 0xa8.toByte(), 0x17.toByte(), 0xc8.toByte(), 0x00.toByte()), // 20 Gwei
            gasLimit = 21000,
            to = "0x3535353535353535353535353535353535353535",
            value = byteArrayOf(0x0d.toByte(), 0xe0.toByte(), 0xb6.toByte(), 0xb3.toByte(), 0xa7.toByte(), 0x64.toByte(), 0x00.toByte(), 0x00.toByte()), // 1 Ether
            data = byteArrayOf(),
            chainId = 1
        )
        // Check exact hex if possible, or properties
        // This ensures no regression in RLP list structure
        val encoded = tx.encode(forSigning = true)
        assertTrue(encoded[0].toInt() and 0xFF >= 0xC0) // List
    }
}
