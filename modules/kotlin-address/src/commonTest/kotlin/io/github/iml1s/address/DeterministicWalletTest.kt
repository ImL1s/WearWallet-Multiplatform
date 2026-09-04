package io.github.iml1s.address

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.iml1s.address.DeterministicWallet
import io.github.iml1s.address.KeyPath

class DeterministicWalletTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testBIP32_Vector1() {
        // BIP32 Test Vector 1
        val seed = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val m = DeterministicWallet.generate(seed)

        // Master (m)
        assertEquals("xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi", m.encode(DeterministicWallet.xprv))
        assertEquals("xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8", m.extendedPublicKey().encode(DeterministicWallet.xpub))

        // m/0H
        val m0h = m.derivePrivateKey(DeterministicWallet.hardened(0))
        assertEquals("xprv9uHRZZhk6KAJC1avXpDAp4MDc3sQKNxDiPvvkX8Br5ngLNv1TxvUxt4cV1rGL5hj6KCesnDYUhd7oWgT11eZG7XnxHrnYeSvkzY7d2bhkJ7", m0h.encode(DeterministicWallet.xprv))
        assertEquals("xpub68Gmy5EdvgibQVfPdqkBBCHxA5htiqg55crXYuXoQRKfDBFA1WEjWgP6LHhwBZeNK1VTsfTFUHCdrfp1bgwQ9xv5ski8PX9rL2dZXvgGDnw", m0h.extendedPublicKey().encode(DeterministicWallet.xpub))
        
        // m/0H/1
        val m0h1 = m0h.derivePrivateKey(1)
        assertEquals("xprv9wTYmMFdV23N2TdNG573QoEsfRrWKQgWeibmLntzniatZvR9BmLnvSxqu53Kw1UmYPxLgboyZQaXwTCg8MSY3H2EU4pWcQDnRnrVA1xe8fs", m0h1.encode(DeterministicWallet.xprv))
        assertEquals("xpub6ASuArnXKPbfEwhqN6e3mwBcDTgzisQN1wXN9BJcM47sSikHjJf3UFHKkNAWbWMiGj7Wf5uMash7SyYq527Hqck2AxYysAA7xmALppuCkwQ", m0h1.extendedPublicKey().encode(DeterministicWallet.xpub))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testBIP32_Vector2() {
        // BIP32 Test Vector 2
        val seed = "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542".hexToByteArray()
        val m = DeterministicWallet.generate(seed)

        // Master (m)
        assertEquals("xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U", m.encode(DeterministicWallet.xprv))
        assertEquals("xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47LJhkJ8UB7WEGuduB", m.extendedPublicKey().encode(DeterministicWallet.xpub))

        // m/0
        val m0 = m.derivePrivateKey(0)
        assertEquals("xprv9vHkqa6EV4sPZHYqZznhT2NPtPCjKuDKGY38FBWLvgaDx45zo9WQRUT3dKYnjwih2yJD9mkrocEZXo1ex8G81dwSM1fwqWpWkeS3v86pgKt", m0.encode(DeterministicWallet.xprv))
        assertEquals("xpub69H7F5d8KSRgmmdJg2KhpAK8SR3DjMwAdkxj3ZuxV27CprR9LgpeyGmXUbC6wb7ERfvrnKZjXoUmmDznezpbZb7ap6r1D3tgFxHmwMkQTPH", m0.extendedPublicKey().encode(DeterministicWallet.xpub))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testBIP32_Vector3() {
        // BIP32 Test Vector 3
        val seed = "4b381541583be4423346c643850da4b320e46a87ae3d2a4e6da11eba819cd4acba45d239319ac14f863b8d5ab5a0d0c64d2e8a1e7d1457df2e5a3c51c73235be".hexToByteArray()
        val m = DeterministicWallet.generate(seed)

        // Master (m)
        assertEquals("xprv9s21ZrQH143K25QhxbucbDDuQ4naNntJRi4KUfWT7xo4EKsHt2QJDu7KXp1A3u7Bi1j8ph3EGsZ9Xvz9dGuVrtHHs7pXeTzjuxBrCmmhgC6", m.encode(DeterministicWallet.xprv))
        assertEquals("xpub661MyMwAqRbcEZVB4dScxMAdx6d4nFc9nvyvH3v4gJL378CSRZiYmhRoP7mBy6gSPSCYk6SzXPTf3ND1cZAceL7SfJ1Z3GC8vBgp2epUt13", m.extendedPublicKey().encode(DeterministicWallet.xpub))

        // m/0H
        val m0h = m.derivePrivateKey(DeterministicWallet.hardened(0))
        assertEquals("xprv9uPDJpEQgRQfDcW7BkF7eTya6RPxXeJCqCJGHuCJ4GiRVLzkTXBAJMu2qaMWPrS7AANYqdq6vcBcBUdJCVVFceUvJFjaPdGZ2y9WACViL4L", m0h.encode(DeterministicWallet.xprv))
        assertEquals("xpub68NZiKmJWnxxS6aaHmn81bvJeTESw724CRDs6HbuccFQN9Ku14VQrADWgqbhhTHBaohPX4CjNLf9fq9MYo6oDaPPLPxSb7gwQN3ih19Zm4Y", m0h.extendedPublicKey().encode(DeterministicWallet.xpub))
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
