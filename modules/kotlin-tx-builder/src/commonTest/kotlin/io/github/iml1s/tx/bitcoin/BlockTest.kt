package io.github.iml1s.tx.bitcoin

import io.github.iml1s.tx.crypto.Crypto
import io.github.iml1s.tx.utils.*
import io.github.iml1s.tx.utils.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockTest {

    @Test
    fun testSha256Truth() {
        val input = "hello".encodeToByteArray()
        val hash = Crypto.sha256(input).toHex()
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash)
        
        val h256 = Crypto.hash256(input).toHex()
        assertEquals("9595c9df90075148eb06860365df33584b75bff782a510c6cd4883a419833d50", h256)
    }

    @Test
    fun testLivenetGenesisBlock() {
        val genesis = Block.LivenetGenesisBlock
        
        // Check hash
        assertEquals(
            "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
            genesis.blockId.toString()
        )
        
        // Check PoW
        assertTrue(genesis.checkProofOfWork())
        
        // Check Merkle Root
        assertEquals(
            "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b", // Reversed of 3ba3...888a
            genesis.header.hashMerkleRoot.reversed().toHex()
        )
        
        // Compute Merkle Root
        val computedRoot = MerkleTree.computeRoot(genesis.tx.map { it.getTxId().byteVector32() })
        assertEquals(genesis.header.hashMerkleRoot, computedRoot)
    }

    @Test
    fun testUInt256() {
        val a = UInt256(100)
        val b = UInt256(200)
        assertTrue(a < b)
        
        val c = UInt256(Hex.decode("0000000000000000000000000000000000000000000000000000000000000001"))
        assertEquals("0000000000000000000000000000000000000000000000000000000000000001", c.toString())
    }
}
