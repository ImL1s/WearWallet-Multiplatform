package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertTrue

class SchnorrTest {

    @Test
    fun testBip340Vectors() {
        // Official BIP-340 test vectors from https://github.com/bitcoin/bips/blob/master/bip-0340/test-vectors.csv
        data class Vector(val sk: String, val pk: String, val auxRand: String, val msg: String, val sig: String)

        val vectors = listOf(
            // Vector 0
            Vector(
                "0000000000000000000000000000000000000000000000000000000000000003",
                "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA821525F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0"
            ),
            // Vector 1
            Vector(
                "B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF",
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89",
                "6896BD60EEAE296DB48A229FF71DFE071BDE413E6D43F917DC8DCF8C78DE33418906D11AC976ABCCB20B091292BFF4EA897EFCB639EA871CFA95F6DE339E4B0A"
            ),
            // Vector 2
            Vector(
                "C90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B14E5C9",
                "DD308AFEC5777E13121FA72B9CC1B7CC0139715309B086C960E18FD969774EB8",
                "C87AA53824B4D7AE2EB035A2B5BBBCCC080E76CDC6D1692C4B0B62D798E6D906",
                "7E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C",
                "5831AAEED7B44BB74E5EAB94BA9D4294C49BCF2A60728D8B4C200F50DD313C1BAB745879A5AD954A72C45A91C3A51D3C7ADEA98D82F8481E0E1E03674A6F3FB7"
            ),
            // Vector 3
            Vector(
                "0B432B2677937381AEF05BB02A66ECD012773062CF3FA2549E44F58ED2401710",
                "25D1DFF95105F5253C4022F628A996AD3A0D95FBF21D468A1B33F8C160D8F517",
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                "7EB0509757E246F19449885651611CB965ECC1A187DD51B64FDA1EDC9637D5EC97582B9CB13DB3933705B32BA982AF5AF25FD78881EBB32771FC5922EFC66EA3"
            )
        )

        fun hex(s: String): ByteArray {
            return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        for ((idx, v) in vectors.withIndex()) {
            println("Testing Vector $idx...")
            
            val sk = hex(v.sk)
            val pk = hex(v.pk)
            val msg = hex(v.msg)
            val expectedSig = hex(v.sig)

            // 1. Verify Public Key generation
            val pubPoint = Secp256k1Pure.generatePublicKeyPoint(sk)
            val generatedPk = Secp256k1Pure.encodePublicKey(pubPoint, compressed = true).sliceArray(1 until 33)

            if (!generatedPk.contentEquals(pk)) {
                println("  SK: ${v.sk}")
                println("  Expected PK: ${v.pk}")
                println("  Actual PK:   ${generatedPk.toHex()}")
            }
            assertTrue(generatedPk.contentEquals(pk), "Vector $idx: Public key mismatch")

            // 2. Verify known valid signature
            val verifyResult = Secp256k1Pure.verifySchnorr(msg, pk, expectedSig)
            assertTrue(verifyResult, "Vector $idx: Failed to verify known valid signature")
            
            // 3. Sign and verify our own signature
            val ourSig = Secp256k1Pure.signSchnorr(msg, sk)
            val selfVerify = Secp256k1Pure.verifySchnorr(msg, pk, ourSig)
            assertTrue(selfVerify, "Vector $idx: Failed to verify generated signature")
            
            println("  Vector $idx passed!")
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") {
            val v = it.toInt()
            val d1 = (v ushr 4) and 0xF
            val d2 = v and 0xF
            val hexChars = "0123456789abcdef"
            "${hexChars[d1]}${hexChars[d2]}"
        }
    }
}
