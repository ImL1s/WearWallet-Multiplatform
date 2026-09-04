package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * BIP39 助記詞官方測試向量驗證
 * 數據來源：https://github.com/trezor/python-mnemonic/blob/master/vectors.json
 */
class Bip39VectorTest {

    /**
     * 官方 Trezor BIP39 測試向量
     * 來源: https://github.com/trezor/python-mnemonic/blob/master/vectors.json
     *
     * 注意：所有官方向量都使用 "TREZOR" 作為 passphrase
     */
    @Test
    fun testBIP39_OfficialVectors() {
        val vectors = listOf(
            // Vector 0: 12 words, passphrase "TREZOR"
            BIP39Vector(
                entropy = "00000000000000000000000000000000",
                mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                passphrase = "TREZOR",
                seed = "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"
            ),
            // Vector 1: 12 words, different entropy
            BIP39Vector(
                entropy = "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
                mnemonic = "legal winner thank year wave sausage worth useful legal winner thank yellow",
                passphrase = "TREZOR",
                seed = "2e8905819b8723fe2c1d161860e5ee1830318dbf49a83bd451cfb8440c28bd6fa457fe1296106559a3c80937a1c1069be3a3a5bd381ee6260e8d9739fce1f607"
            ),
            // Vector 2: 12 words, another pattern
            BIP39Vector(
                entropy = "80808080808080808080808080808080",
                mnemonic = "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
                passphrase = "TREZOR",
                seed = "d71de856f81a8acc65e6fc851a38d4d7ec216fd0796d0a6827a3ad6ed5511a30fa280f12eb2e47ed2ac03b5c462a0358d18d69fe4f985ec81778c1b370b652a8"
            ),
            // Vector 3: all ones (12 words)
            BIP39Vector(
                entropy = "ffffffffffffffffffffffffffffffff",
                mnemonic = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
                passphrase = "TREZOR",
                seed = "ac27495480225222079d7be181583751e86f571027b0497b5b5d11218e0a8a13332572917f0f8e5a589620c6f15b11c61dee327651a14c34e18231052e48c069"
            )
        )

        for ((index, v) in vectors.withIndex()) {
            val derivedSeedBytes = platformDeriveSeed(v.mnemonic, v.passphrase)
            val derivedSeed = derivedSeedBytes.toHexString()
            assertEquals(v.seed, derivedSeed, "Seed derivation failed for vector $index")
        }
    }

    private data class BIP39Vector(
        val entropy: String,
        val mnemonic: String,
        val passphrase: String,
        val seed: String
    )

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val unsigned = byte.toInt() and 0xFF
            if (unsigned < 16) "0${unsigned.toString(16)}" else unsigned.toString(16)
        }
    }
}

// 需要平台特定的 pbkdf2 實現
internal expect fun platformDeriveSeed(mnemonic: String, passphrase: String): ByteArray
