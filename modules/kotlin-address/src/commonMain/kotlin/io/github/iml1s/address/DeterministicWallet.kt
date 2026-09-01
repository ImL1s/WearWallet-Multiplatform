package io.github.iml1s.address

import io.github.iml1s.crypto.HmacSha512
import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.Ripemd160

/**
 * Ported from ACINQ bitcoin-kmp
 * Simplified and adapted for watchOS support with Secp256k1Pure
 */
object DeterministicWallet {
    const val hardenedKeyIndex: Long = 0x80000000L

    fun hardened(index: Long): Long = hardenedKeyIndex + index
    fun isHardened(index: Long): Boolean = index >= hardenedKeyIndex

    // Version bytes
    const val xprv: Int = 0x0488ade4
    const val xpub: Int = 0x0488b21e
    const val yprv: Int = 0x049d7878
    const val ypub: Int = 0x049d7cb2
    const val zprv: Int = 0x04b2430c
    const val zpub: Int = 0x04b24746

    data class ExtendedPrivateKey(
        val secretkeybytes: ByteArray,
        val chaincode: ByteArray,
        val depth: Int,
        val path: KeyPath,
        val parent: Long
    ) {
        init {
            require(secretkeybytes.size == 32)
            require(chaincode.size == 32)
            require(Secp256k1Pure.secKeyVerify(secretkeybytes)) { "private key is invalid" }
        }

        fun publicKey(): ByteArray = Secp256k1Pure.pubkeyCreate(secretkeybytes)

        fun extendedPublicKey(): ExtendedPublicKey = ExtendedPublicKey(
            publickeybytes = publicKey(),
            chaincode = chaincode,
            depth = depth,
            path = path,
            parent = parent
        )

        fun derivePrivateKey(index: Long): ExtendedPrivateKey {
            val hmacInput = if (isHardened(index)) {
                byteArrayOf(0) + secretkeybytes + writeInt32BE(index.toInt())
            } else {
                publicKey() + writeInt32BE(index.toInt())
            }
            // Debug prints
            val indexHex = index.toString(16)
            val inputHex = hmacInput.joinToString("") { "%02x".format(it) }
            val chaincodeHex = chaincode.joinToString("") { "%02x".format(it) }
            println("DEBUG: derivePrivateKey index=$indexHex chaincode=$chaincodeHex input=$inputHex")

            val i = HmacSha512.hmac(chaincode, hmacInput)
            val il = i.take(32).toByteArray()
            val ir = i.takeLast(32).toByteArray()

            // Debug prints
            val ilHex = il.joinToString("") { "%02x".format(it) }
            val irHex = ir.joinToString("") { "%02x".format(it) }
            println("DEBUG: derivePrivateKey il=$ilHex ir=$irHex")
            
            val childKey = Secp256k1Pure.privKeyTweakAdd(secretkeybytes, il)
            val childKeyHex = childKey.joinToString("") { "%02x".format(it) }
            val parentFP = fingerprint()
            val parentFPHex = "%08x".format(parentFP)
            println("DEBUG: derivePrivateKey childKey=$childKeyHex parentFP=$parentFPHex (val=$parentFP) depth=${depth}")
            
            return ExtendedPrivateKey(
                secretkeybytes = childKey,
                chaincode = ir,
                depth = depth + 1,
                path = path.derive(index),
                parent = fingerprint()
            )
        }

        fun derivePrivateKey(keyPath: KeyPath): ExtendedPrivateKey = keyPath.path.fold(this) { k, i -> k.derivePrivateKey(i) }

        fun fingerprint(): Long = extendedPublicKey().fingerprint()

        fun encode(prefix: Int): String {
            val data = ByteArray(78)
            writeInt32BE(prefix).copyInto(data, 0)
            data[4] = depth.toByte()
            writeInt32BE(parent.toInt()).copyInto(data, 5)
            writeInt32BE(path.lastChildNumber.toInt()).copyInto(data, 9)
            chaincode.copyInto(data, 13)
            data[45] = 0
            secretkeybytes.copyInto(data, 46)
            
            val rawHex = data.joinToString("") { "%02x".format(it) }
            println("DEBUG: xprv encodeRaw=$rawHex")

            // Base58.encodeCheck expects (version, payload)
            // Fix: Swapped arguments to correct order
            return Base58.encodeCheck(byteArrayOf(data[0], data[1], data[2], data[3]), data.sliceArray(4 until data.size))
        }
        
        override fun toString(): String = "<extended_private_key>"
    }

    data class ExtendedPublicKey(
        val publickeybytes: ByteArray,
        val chaincode: ByteArray,
        val depth: Int,
        val path: KeyPath,
        val parent: Long
    ) {
        init {
            require(publickeybytes.size == 33)
            require(chaincode.size == 32)
        }

        fun derivePublicKey(index: Long): ExtendedPublicKey {
            require(!isHardened(index)) { "Cannot derive public keys from public hardened keys" }

            val hmacInput = publickeybytes + writeInt32BE(index.toInt())
            val i = HmacSha512.hmac(chaincode, hmacInput)
            val il = i.take(32).toByteArray()
            val ir = i.takeLast(32).toByteArray()

            val ki = Secp256k1Pure.pubKeyTweakAdd(publickeybytes, il)
            
            return ExtendedPublicKey(
                publickeybytes = ki,
                chaincode = ir,
                depth = depth + 1,
                path = path.derive(index),
                parent = fingerprint()
            )
        }

        fun fingerprint(): Long {
            val pubHex = publickeybytes.joinToString("") { "%02x".format(it) }
            val sha256 = Secp256k1Pure.sha256(publickeybytes)
            val sha256Hex = sha256.joinToString("") { "%02x".format(it) }
            val hash160 = Ripemd160.hash(sha256)
            val hash160Hex = hash160.joinToString("") { "%02x".format(it) }
            
            println("DEBUG: fingerprint pub=$pubHex")
            println("DEBUG: fingerprint sha256=$sha256Hex")
            println("DEBUG: fingerprint hash160=$hash160Hex")
            
            return ((hash160[0].toLong() and 0xFF) shl 24) or
                   ((hash160[1].toLong() and 0xFF) shl 16) or
                   ((hash160[2].toLong() and 0xFF) shl 8) or
                   (hash160[3].toLong() and 0xFF)
        }

        fun encode(prefix: Int): String {
            val data = ByteArray(78)
            writeInt32BE(prefix).copyInto(data, 0)
            data[4] = depth.toByte()
            writeInt32BE(parent.toInt()).copyInto(data, 5)
            writeInt32BE(path.lastChildNumber.toInt()).copyInto(data, 9)
            chaincode.copyInto(data, 13)
            publickeybytes.copyInto(data, 45)
            // Base58.encodeCheck expects (version, payload)
            // Fix: Swapped arguments to correct order
            return Base58.encodeCheck(byteArrayOf(data[0], data[1], data[2], data[3]), data.sliceArray(4 until data.size))
        }
    }

    fun generate(seed: ByteArray): ExtendedPrivateKey {
        val i = HmacSha512.hmac("Bitcoin seed".encodeToByteArray(), seed)
        val il = i.take(32).toByteArray()
        val ir = i.takeLast(32).toByteArray()
        return ExtendedPrivateKey(il, ir, depth = 0, path = KeyPath.empty, parent = 0L)
    }

    private fun writeInt32BE(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }
}

data class KeyPath(val path: List<Long>) {
    constructor(path: String) : this(computePath(path))

    val lastChildNumber: Long get() = if (path.isEmpty()) 0L else path.last()

    fun derive(number: Long): KeyPath = KeyPath(path + listOf(number))

    companion object {
        val empty: KeyPath = KeyPath(listOf())

        fun computePath(path: String): List<Long> {
            fun toNumber(value: String): Long = if (value.endsWith("'") || value.endsWith("h")) {
                val num = value.dropLast(1).toLong()
                DeterministicWallet.hardened(num)
            } else {
                value.toLong()
            }

            val p = path.removePrefix("m").removePrefix("/")
            return if (p.isEmpty()) {
                listOf()
            } else {
                p.split('/').filter { it.isNotEmpty() }.map { toNumber(it) }
            }
        }
    }
}
