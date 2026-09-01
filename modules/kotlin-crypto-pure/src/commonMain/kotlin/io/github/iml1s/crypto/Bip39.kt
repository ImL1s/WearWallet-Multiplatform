package io.github.iml1s.crypto

import kotlin.random.Random

/**
 * BIP39 助記詞生成與驗證
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39</a>
 */
object Bip39 {

    /**
     * 生成助記詞
     *
     * @param bits 熵的位數 (128, 160, 192, 224, 256)
     * @param language 語言（目前僅支援 English）
     * @return 助記詞（單詞列表）
     */
    fun generateMnemonic(bits: Int = 128): String {
        require(bits in listOf(128, 160, 192, 224, 256)) { "Invalid entropy bits: $bits" }

        val entropy = secureRandomBytes(bits / 8)

        return encode(entropy).joinToString(" ")
    }

    /**
     * 將熵編碼為助記詞
     */
    fun encode(entropy: ByteArray): List<String> {
        val bits = entropy.size * 8
        require(bits in listOf(128, 160, 192, 224, 256)) { "Invalid entropy length: $bits bits" }

        // Checksum length = bits / 32
        val checksumLen = bits / 32
        
        // Calculate checksum
        val sha256 = Secp256k1Pure.sha256(entropy)
        val checksumByte = sha256[0].toInt()

        // Convert entropy + checksum to binary string (conceptual) or boolean array
        val totalBits = bits + checksumLen
        val booleanBits = BooleanArray(totalBits)

        // Fill entropy bits
        for (i in 0 until bits) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            booleanBits[i] = (entropy[byteIndex].toInt() shr bitIndex) and 1 == 1
        }

        // Fill checksum bits
        for (i in 0 until checksumLen) {
            val bitIndex = 7 - i
            booleanBits[bits + i] = (checksumByte shr bitIndex) and 1 == 1
        }

        // Group into 11-bit indices
        val indices = mutableListOf<Int>()
        for (i in 0 until totalBits step 11) {
            var index = 0
            for (j in 0 until 11) {
                if (booleanBits[i + j]) {
                    index = index or (1 shl (10 - j))
                }
            }
            indices.add(index)
        }

        return indices.map { BIP39_ENGLISH_WORDLIST[it] }
    }

    /**
     * 驗證助記詞
     *
     * @param mnemonic 助記詞字串
     * @return 是否有效
     */
    fun validate(mnemonic: String): Boolean {
        val words = mnemonic.trim().split("\\s+".toRegex())
        if (words.size !in listOf(12, 15, 18, 21, 24)) return false

        // Convert words to indices
        val indices = words.map { word ->
            val index = BIP39_ENGLISH_WORDLIST.indexOf(word)
            if (index == -1) return false
            index
        }

        // Convert indices to bits
        val totalBits = words.size * 11
        val checksumLen = totalBits % 32 // Should be totalBits / 33 * 1 ?? No..
        // 12 words * 11 = 132 bits. Entropy = 128, Checksum = 4. 132 = 128 + 4.
        val entropyBits = totalBits - (totalBits / 33) // Formula derivation: CS = ENT / 32. Total = ENT + CS = ENT + ENT/32 = 33/32 ENT. => ENT = Total * 32 / 33.
        // Or simpler:
        // 12 words -> 128 entropy + 4 checksum
        // 15 words -> 160 entropy + 5 checksum
        // 18 words -> 192 entropy + 6 checksum
        // 21 words -> 224 entropy + 7 checksum
        // 24 words -> 256 entropy + 8 checksum
        
        val entropyLen = (totalBits * 32) / 33
        val checksumLenCalc = totalBits - entropyLen
        
        val booleanBits = BooleanArray(totalBits)
        for (i in indices.indices) {
            val index = indices[i]
            for (j in 0 until 11) {
                booleanBits[i * 11 + j] = (index shr (10 - j)) and 1 == 1
            }
        }

        // Extract entropy bytes
        val entropy = ByteArray(entropyLen / 8)
        for (i in 0 until entropyLen) {
            if (booleanBits[i]) {
                val byteIndex = i / 8
                val bitIndex = 7 - (i % 8)
                entropy[byteIndex] = (entropy[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }

        // Calculate expected checksum
        val sha256 = Secp256k1Pure.sha256(entropy)
        val checksumByte = sha256[0].toInt()

        // Verify checksum bits
        for (i in 0 until checksumLenCalc) {
            val bitIndex = 7 - i
            val expectedBit = (checksumByte shr bitIndex) and 1 == 1
            if (booleanBits[entropyLen + i] != expectedBit) return false
        }

        return true
    }
}
