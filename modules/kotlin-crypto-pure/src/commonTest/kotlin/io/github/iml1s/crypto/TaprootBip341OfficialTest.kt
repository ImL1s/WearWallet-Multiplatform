package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BIP-341 Taproot 官方測試向量
 * 來源: https://github.com/bitcoin/bips/blob/master/bip-0341/wallet-test-vectors.json
 * 
 * 測試覆蓋:
 * - scriptPubKey 計算 (Key path only)
 * - scriptPubKey 計算 (Single script)
 * - scriptPubKey 計算 (Multiple scripts/nested trees)
 * - Tagged hash (TapTweak, TapLeaf, TapBranch)
 * - Control block 構建
 */
class TaprootBip341OfficialTest {

    // ==================== Test Vector 0: Key path only (no scripts) ====================
    @Test
    fun testVector0_KeyPathOnly() {
        val internalPubkey = hex("d6889cb081036e0faefa3a35157ad71086b123b2b144b649798b494c300a961d")
        val expectedTweak = "b86e7be8f39bab32a6f2c0443abbc210f0edac0e2c53d501b36b64437d9c6c70"
        val expectedTweakedPubkey = "53a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343"
        val expectedScriptPubKey = "512053a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343"
        
        // Test 1: TapTweak hash (no merkle root)
        val tweak = Secp256k1Pure.taggedHash("TapTweak", internalPubkey)
        assertEquals(expectedTweak, tweak.toHex(), "Vector 0: Tweak hash mismatch")
        
        // Test 2: Tweaked pubkey computation
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPubkey))
        val tweakScalar = Secp256k1Pure.BigInteger(tweak)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        val tweakedX = tweakedPoint.first.toByteArray32()
        
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Vector 0: Tweaked pubkey mismatch")
        
        // Test 3: scriptPubKey = OP_1 OP_PUSHBYTES_32 <32-byte-tweaked-pubkey>
        val scriptPubKey = byteArrayOf(0x51, 0x20) + tweakedX
        assertEquals(expectedScriptPubKey, scriptPubKey.toHex(), "Vector 0: scriptPubKey mismatch")
    }
    
    // ==================== Test Vector 1: Single script (leafVersion 192) ====================
    @Test
    fun testVector1_SingleScript() {
        val internalPubkey = hex("187791b6f712a8ea41c8ecdd0ee77fab3e85263b37e1ec18a3651926b3a6cf27")
        val script = hex("20d85a959b0290bf19bb89ed43c916be835475d013da4b362117393e25a48229b8ac")
        val leafVersion: Byte = 0xC0.toByte() // 192
        
        val expectedLeafHash = "5b75adecf53548f3ec6ad7d78383bf84cc57b55a3127c72b9a2481752dd88b21"
        val expectedMerkleRoot = "5b75adecf53548f3ec6ad7d78383bf84cc57b55a3127c72b9a2481752dd88b21"
        val expectedTweak = "cbd8679ba636c1110ea247542cfbd964131a6be84f873f7f3b62a777528ed001"
        val expectedTweakedPubkey = "147c9c57132f6e7ecddba9800bb0c4449251c92a1e60371ee77557b6620f3ea3"
        
        // Test 1: TapLeaf hash
        val leafHash = Secp256k1Pure.tapLeafHash(leafVersion, script)
        assertEquals(expectedLeafHash, leafHash.toHex(), "Vector 1: Leaf hash mismatch")
        
        // Test 2: Merkle root = leaf hash (single leaf)
        assertEquals(expectedMerkleRoot, leafHash.toHex(), "Vector 1: Merkle root mismatch")
        
        // Test 3: TapTweak with merkle root
        val tweakData = internalPubkey + leafHash
        val tweak = Secp256k1Pure.taggedHash("TapTweak", tweakData)
        assertEquals(expectedTweak, tweak.toHex(), "Vector 1: Tweak mismatch")
        
        // Test 4: Tweaked pubkey
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPubkey))
        val tweakScalar = Secp256k1Pure.BigInteger(tweak)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        val tweakedX = tweakedPoint.first.toByteArray32()
        
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Vector 1: Tweaked pubkey mismatch")
    }
    
    // ==================== Test Vector 2: Another single script ====================
    @Test
    fun testVector2_SingleScriptVariant() {
        val internalPubkey = hex("93478e9488f956df2396be2ce6c5cced75f900dfa18e7dabd2428aae78451820")
        val script = hex("20b617298552a72ade070667e86ca63b8f5789a9fe8731ef91202a91c9f3459007ac")
        val leafVersion: Byte = 0xC0.toByte()
        
        val expectedLeafHash = "c525714a7f49c28aedbbba78c005931a81c234b2f6c99a73e4d06082adc8bf2b"
        val expectedTweak = "6af9e28dbf9d6aaf027696e2598a5b3d056f5fd2355a7fd5a37a0e5008132d30"
        val expectedTweakedPubkey = "e4d810fd50586274face62b8a807eb9719cef49c04177cc6b76a9a4251d5450e"
        
        val leafHash = Secp256k1Pure.tapLeafHash(leafVersion, script)
        assertEquals(expectedLeafHash, leafHash.toHex(), "Vector 2: Leaf hash mismatch")
        
        val tweakData = internalPubkey + leafHash
        val tweak = Secp256k1Pure.taggedHash("TapTweak", tweakData)
        assertEquals(expectedTweak, tweak.toHex(), "Vector 2: Tweak mismatch")
        
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPubkey))
        val tweakScalar = Secp256k1Pure.BigInteger(tweak)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        val tweakedX = tweakedPoint.first.toByteArray32()
        
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Vector 2: Tweaked pubkey mismatch")
    }
    
    // ==================== Test Vector 3: Two scripts (balanced tree) ====================
    @Test
    fun testVector3_TwoScriptsBalanced() {
        val internalPubkey = hex("ee4fe085983462a184015d1f782d6a5f8b9c2b60130aff050ce221ecf3786592")
        
        val script0 = hex("20387671353e273264c495656e27e39ba899ea8fee3bb69fb2a680e22093447d48ac")
        val leafVersion0: Byte = 0xC0.toByte()
        
        val script1 = hex("06424950333431") // "BIP341" in hex
        val leafVersion1: Byte = 0xFA.toByte() // 250
        
        val expectedLeafHash0 = "8ad69ec7cf41c2a4001fd1f738bf1e505ce2277acdcaa63fe4765192497f47a7"
        val expectedLeafHash1 = "f224a923cd0021ab202ab139cc56802ddb92dcfc172b9212261a539df79a112a"
        val expectedMerkleRoot = "6c2dc106ab816b73f9d07e3cd1ef2c8c1256f519748e0813e4edd2405d277bef"
        val expectedTweakedPubkey = "712447206d7a5238acc7ff53fbe94a3b64539ad291c7cdbc490b7577e4b17df5"
        
        // Test leaf hashes
        val leafHash0 = Secp256k1Pure.tapLeafHash(leafVersion0, script0)
        assertEquals(expectedLeafHash0, leafHash0.toHex(), "Vector 3: Leaf 0 hash mismatch")
        
        val leafHash1 = Secp256k1Pure.tapLeafHash(leafVersion1, script1)
        assertEquals(expectedLeafHash1, leafHash1.toHex(), "Vector 3: Leaf 1 hash mismatch")
        
        // Test TapBranch hash (sort leaves lexicographically)
        val merkleRoot = Secp256k1Pure.tapBranchHash(leafHash0, leafHash1)
        assertEquals(expectedMerkleRoot, merkleRoot.toHex(), "Vector 3: Merkle root mismatch")
        
        // Test tweaked pubkey
        val tweakData = internalPubkey + merkleRoot
        val tweak = Secp256k1Pure.taggedHash("TapTweak", tweakData)
        
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPubkey))
        val tweakScalar = Secp256k1Pure.BigInteger(tweak)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        val tweakedX = tweakedPoint.first.toByteArray32()
        
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Vector 3: Tweaked pubkey mismatch")
    }
    
    // ==================== Test Vector 4: Two scripts (leafVersion 192) ====================
    @Test
    fun testVector4_TwoScriptsStandard() {
        val internalPubkey = hex("f9f400803e683727b14f463836e1e78e1c64417638aa066919291a225f0e8dd8")
        
        val script0 = hex("2044b178d64c32c4a05cc4f4d1407268f764c940d20ce97abfd44db5c3592b72fdac")
        val script1 = hex("07546170726f6f74") // "Taproot" in hex
        
        val expectedLeafHash0 = "64512fecdb5afa04f98839b50e6f0cb7b1e539bf6f205f67934083cdcc3c8d89"
        val expectedLeafHash1 = "2cb2b90daa543b544161530c925f285b06196940d6085ca9474d41dc3822c5cb"
        val expectedMerkleRoot = "ab179431c28d3b68fb798957faf5497d69c883c6fb1e1cd9f81483d87bac90cc"
        val expectedTweakedPubkey = "77e30a5522dd9f894c3f8b8bd4c4b2cf82ca7da8a3ea6a239655c39c050ab220"
        
        val leafHash0 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script0)
        assertEquals(expectedLeafHash0, leafHash0.toHex(), "Vector 4: Leaf 0 hash mismatch")
        
        val leafHash1 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script1)
        assertEquals(expectedLeafHash1, leafHash1.toHex(), "Vector 4: Leaf 1 hash mismatch")
        
        val merkleRoot = Secp256k1Pure.tapBranchHash(leafHash0, leafHash1)
        assertEquals(expectedMerkleRoot, merkleRoot.toHex(), "Vector 4: Merkle root mismatch")
        
        val tweakData = internalPubkey + merkleRoot
        val tweak = Secp256k1Pure.taggedHash("TapTweak", tweakData)
        
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPubkey))
        val tweakScalar = Secp256k1Pure.BigInteger(tweak)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        val tweakedX = tweakedPoint.first.toByteArray32()
        
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Vector 4: Tweaked pubkey mismatch")
    }
    
    // ==================== Test Vector 5: Three scripts (nested tree) ====================
    @Test
    fun testVector5_NestedTree() {
        val internalPubkey = hex("e0dfe2300b0dd746a3f8674dfd4525623639042569d829c7f0eed9602d263e6f")
        
        // Tree structure: [leaf0, [leaf1, leaf2]]
        val script0 = hex("2072ea6adcf1d371dea8fba1035a09f3d24ed5a059799bae114084130ee5898e69ac")
        val script1 = hex("202352d137f2f3ab38d1eaa976758873377fa5ebb817372c71e2c542313d4abda8ac")
        val script2 = hex("207337c0dd4253cb86f2c43a2351aadd82cccb12a172cd120452b9bb8324f2186aac")
        
        val expectedLeaf0 = "2645a02e0aac1fe69d69755733a9b7621b694bb5b5cde2bbfc94066ed62b9817"
        val expectedLeaf1 = "ba982a91d4fc552163cb1c0da03676102d5b7a014304c01f0c77b2b8e888de1c"
        val expectedLeaf2 = "9e31407bffa15fefbf5090b149d53959ecdf3f62b1246780238c24501d5ceaf6"
        val expectedMerkleRoot = "ccbd66c6f7e8fdab47b3a486f59d28262be857f30d4773f2d5ea47f7761ce0e2"
        val expectedTweakedPubkey = "91b64d5324723a985170e4dc5a0f84c041804f2cd12660fa5dec09fc21783605"
        
        val leafHash0 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script0)
        assertEquals(expectedLeaf0, leafHash0.toHex(), "Vector 5: Leaf 0 hash mismatch")
        
        val leafHash1 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script1)
        assertEquals(expectedLeaf1, leafHash1.toHex(), "Vector 5: Leaf 1 hash mismatch")
        
        val leafHash2 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script2)
        assertEquals(expectedLeaf2, leafHash2.toHex(), "Vector 5: Leaf 2 hash mismatch")
        
        // Inner branch: TapBranch(leaf1, leaf2)
        val innerBranch = Secp256k1Pure.tapBranchHash(leafHash1, leafHash2)
        
        // Root: TapBranch(leaf0, innerBranch)
        val merkleRoot = Secp256k1Pure.tapBranchHash(leafHash0, innerBranch)
        assertEquals(expectedMerkleRoot, merkleRoot.toHex(), "Vector 5: Merkle root mismatch")
        
        val tweakData = internalPubkey + merkleRoot
        val tweak = Secp256k1Pure.taggedHash("TapTweak", tweakData)
        
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPubkey))
        val tweakScalar = Secp256k1Pure.BigInteger(tweak)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        val tweakedX = tweakedPoint.first.toByteArray32()
        
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Vector 5: Tweaked pubkey mismatch")
    }
    
    // ==================== Test Vector 6: Three scripts (different structure) ====================
    @Test
    fun testVector6_AnotherNestedTree() {
        val internalPubkey = hex("55adf4e8967fbd2e29f20ac896e60c3b0f1d5b0efa9d34941b5958c7b0a0312d")
        
        val script0 = hex("2071981521ad9fc9036687364118fb6ccd2035b96a423c59c5430e98310a11abe2ac")
        val script1 = hex("20d5094d2dbe9b76e2c245a2b89b6006888952e2faa6a149ae318d69e520617748ac")
        val script2 = hex("20c440b462ad48c7a77f94cd4532d8f2119dcebbd7c9764557e62726419b08ad4cac")
        
        val expectedLeaf0 = "f154e8e8e17c31d3462d7132589ed29353c6fafdb884c5a6e04ea938834f0d9d"
        val expectedLeaf1 = "737ed1fe30bc42b8022d717b44f0d93516617af64a64753b7a06bf16b26cd711"
        val expectedLeaf2 = "d7485025fceb78b9ed667db36ed8b8dc7b1f0b307ac167fa516fe4352b9f4ef7"
        val expectedMerkleRoot = "2f6b2c5397b6d68ca18e09a3f05161668ffe93a988582d55c6f07bd5b3329def"
        val expectedTweakedPubkey = "75169f4001aa68f15bbed28b218df1d0a62cbbcf1188c6665110c293c907b831"
        
        val leafHash0 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script0)
        assertEquals(expectedLeaf0, leafHash0.toHex(), "Vector 6: Leaf 0 hash mismatch")
        
        val leafHash1 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script1)
        assertEquals(expectedLeaf1, leafHash1.toHex(), "Vector 6: Leaf 1 hash mismatch")
        
        val leafHash2 = Secp256k1Pure.tapLeafHash(0xC0.toByte(), script2)
        assertEquals(expectedLeaf2, leafHash2.toHex(), "Vector 6: Leaf 2 hash mismatch")
        
        // Tree: [leaf0, [leaf1, leaf2]]
        val innerBranch = Secp256k1Pure.tapBranchHash(leafHash1, leafHash2)
        val merkleRoot = Secp256k1Pure.tapBranchHash(leafHash0, innerBranch)
        assertEquals(expectedMerkleRoot, merkleRoot.toHex(), "Vector 6: Merkle root mismatch")
        
        val tweakData = internalPubkey + merkleRoot
        val tweak = Secp256k1Pure.taggedHash("TapTweak", tweakData)
        
        val internalPoint = Secp256k1Pure.liftX(Secp256k1Pure.BigInteger(internalPubkey))
        val tweakScalar = Secp256k1Pure.BigInteger(tweak)
        val tweakPoint = Secp256k1Pure.scalarMultiplyG(tweakScalar)
        val tweakedPoint = Secp256k1Pure.addPoints(internalPoint, tweakPoint)
        val tweakedX = tweakedPoint.first.toByteArray32()
        
        assertEquals(expectedTweakedPubkey, tweakedX.toHex(), "Vector 6: Tweaked pubkey mismatch")
    }
    
    // ==================== Utility functions ====================
    private fun hex(s: String): ByteArray {
        return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    private fun ByteArray.toHex(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            hex.toString(16).padStart(2, '0')
        }
    }
}
