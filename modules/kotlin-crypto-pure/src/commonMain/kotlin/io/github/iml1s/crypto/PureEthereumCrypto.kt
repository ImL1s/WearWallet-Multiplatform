package io.github.iml1s.crypto

import org.kotlincrypto.hash.sha2.SHA256

/**
 * 純 Kotlin 實現的 Ethereum 地址與 Key 衍生工具 (嚴格校驗 xpub 與路徑)
 */
object PureEthereumCrypto {

    fun ping(): String = "pong"
    fun pingWithArg(valStr: String): String = "pong:$valStr"

    /**
     * 從 Xpub 與路徑衍生 Ethereum 地址 (嚴格校驗網路、version header、depth 與點無窮大)
     */
    fun deriveAddressFromXpub(
        xpub: String,
        path: String,
        isTestnet: Boolean = false,
        expectedDepth: Int? = null
    ): String {
        val cleanXpub = xpub.trim()
        require(cleanXpub.isNotEmpty()) { "xpub cannot be empty" }

        // 1. Decode Base58
        val decoded = Base58.decode(cleanXpub)
        if (decoded.size != 82) {
            throw IllegalArgumentException("Invalid xpub length: ${decoded.size} bytes (expected 82 bytes)")
        }

        // 2. Verify Checksum (強制拒絕校驗碼不符)
        val data = decoded.copyOfRange(0, 78)
        val checksum = decoded.copyOfRange(78, 82)
        val calculatedChecksum = SHA256().digest(SHA256().digest(data)).copyOfRange(0, 4)

        if (!checksum.contentEquals(calculatedChecksum)) {
            throw IllegalArgumentException("xpub checksum mismatch")
        }

        // 3. Verify Network Context & Version Header (mainnet 0x0488b21e / testnet 0x043587cf)
        val versionHex = data.copyOfRange(0, 4).toHexString()
        if (isTestnet) {
            if (versionHex != "043587cf") {
                throw IllegalArgumentException("Testnet context requires tpub (0x043587cf), got 0x$versionHex")
            }
        } else {
            if (versionHex != "0488b21e") {
                throw IllegalArgumentException("Mainnet context requires xpub (0x0488b21e), got 0x$versionHex")
            }
        }

        // Verify Depth metadata (byte 4)
        val depth = data[4].toInt() and 0xFF
        if (expectedDepth != null && depth != expectedDepth) {
            throw IllegalArgumentException("xpub depth ($depth) does not match expected depth ($expectedDepth)")
        }

        // 4. Verify Compressed Public Key Prefix (0x02 or 0x03)
        val keyPrefix = data[45].toInt() and 0xFF
        if (keyPrefix != 2 && keyPrefix != 3) {
            throw IllegalArgumentException("Invalid compressed public key prefix in xpub: 0x${keyPrefix.toString(16)}")
        }

        var currentChainCode = data.copyOfRange(13, 45)
        var currentKeyData = data.copyOfRange(45, 78)

        // 5. Parse Derivation Path (嚴格校驗，任一無效 component 即刻拋出例外)
        val indices = parseDerivationPathStrict(path)

        // 6. Derive Child Keys (CKDpub)
        for (index in indices) {
            if ((index and 0x80000000u) != 0u) {
                throw IllegalArgumentException("Cannot derive hardened path from xpub")
            }

            val dataToHmac = currentKeyData + index.toByteArray()
            val i = HmacSha512.hmac(currentChainCode, dataToHmac)
            val il = i.copyOfRange(0, 32)
            val ir = i.copyOfRange(32, 64)

            val ilInt = Secp256k1Pure.BigInteger(il)
            val n = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".hexToBigInteger()

            if (ilInt >= n || ilInt == Secp256k1Pure.BigInteger.ZERO) {
                throw IllegalStateException("Invalid IL in CKDpub")
            }

            val pointIl = Secp256k1Pure.generatePublicKeyPoint(il)
            val pointKpar = Secp256k1Pure.decodePublicKey(currentKeyData)

            val pointKi = Secp256k1Pure.addPoints(pointIl, pointKpar)
            if (pointKi.first == Secp256k1Pure.BigInteger.ZERO && pointKi.second == Secp256k1Pure.BigInteger.ZERO) {
                throw IllegalStateException("Derived point is point at infinity in CKDpub")
            }

            currentKeyData = Secp256k1Pure.encodePublicKey(pointKi, compressed = true)
            currentChainCode = ir
        }

        // 7. Convert to Checksummed Address
        val finalPoint = Secp256k1Pure.decodePublicKey(currentKeyData)
        val uncompressed = Secp256k1Pure.encodePublicKey(finalPoint, compressed = false)

        val dataToHash = uncompressed.copyOfRange(1, uncompressed.size)
        val hash = Keccak256.hash(dataToHash)

        val addressBytes = hash.copyOfRange(12, 32)
        return toChecksumAddress("0x" + addressBytes.toHexString())
    }

    fun derivePrivateKey(mnemonic: String, path: String): String {
        val seed = Pbkdf2.bip39Seed(mnemonic, "")
        val hmac = HmacSha512.hmac("Bitcoin seed".encodeToByteArray(), seed)
        var currentKeyData = hmac.copyOfRange(0, 32)
        var currentChainCode = hmac.copyOfRange(32, 64)

        val indices = parseDerivationPathStrict(path)

        for (index in indices) {
            val isHardened = (index and 0x80000000u) != 0u

            val dataToHmac: ByteArray = if (isHardened) {
                byteArrayOf(0) + currentKeyData + index.toByteArray()
            } else {
                val point = Secp256k1Pure.generatePublicKeyPoint(currentKeyData)
                val pubBytes = Secp256k1Pure.encodePublicKey(point, compressed = true)
                pubBytes + index.toByteArray()
            }

            val i = HmacSha512.hmac(currentChainCode, dataToHmac)
            val il = i.copyOfRange(0, 32)
            val ir = i.copyOfRange(32, 64)

            val ilInt = Secp256k1Pure.BigInteger(il)
            val kparInt = Secp256k1Pure.BigInteger(currentKeyData)
            val n = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141".hexToBigInteger()

            if (ilInt >= n || ilInt == Secp256k1Pure.BigInteger.ZERO) {
                throw IllegalStateException("Invalid IL")
            }

            val kiInt = (ilInt + kparInt).mod(n)

            if (kiInt == Secp256k1Pure.BigInteger.ZERO) {
                throw IllegalStateException("Invalid ki")
            }

            val kiBytes = kiInt.toByteArray()
            currentKeyData = if (kiBytes.size < 32) {
                ByteArray(32 - kiBytes.size) + kiBytes
            } else if (kiBytes.size > 32) {
                kiBytes.copyOfRange(kiBytes.size - 32, kiBytes.size)
            } else {
                kiBytes
            }

            currentChainCode = ir
        }

        return "0x" + currentKeyData.toHexString()
    }

    fun getEthereumAddress(privateKeyHex: String): String {
        val keyClean = privateKeyHex.removePrefix("0x")
        val privateKey = keyClean.hexToByteArray()

        val publicKeyPoint = Secp256k1Pure.generatePublicKeyPoint(privateKey)
        val uncompressed = Secp256k1Pure.encodePublicKey(publicKeyPoint, compressed = false)

        val dataToHash = uncompressed.copyOfRange(1, uncompressed.size)
        val hash = Keccak256.hash(dataToHash)

        val addressBytes = hash.copyOfRange(12, 32)
        return toChecksumAddress("0x" + addressBytes.toHexString())
    }

    private fun parseDerivationPathStrict(path: String): List<UInt> {
        var cleanPath = path.trim()
        if (cleanPath.startsWith("m/") || cleanPath.startsWith("M/")) {
            cleanPath = cleanPath.substring(2)
        }
        if (cleanPath.isEmpty()) return emptyList()

        val components = cleanPath.split("/")
        return components.map { component ->
            val text = component.trim()
            if (text.isEmpty()) {
                throw IllegalArgumentException("Invalid empty component in derivation path: $path")
            }
            var isHardened = false
            var numberStr = text
            if (text.endsWith("'") || text.endsWith("h")) {
                isHardened = true
                numberStr = text.dropLast(1)
            }
            val valInt = numberStr.toUIntOrNull()
                ?: throw IllegalArgumentException("Invalid non-numeric component in derivation path: '$text'")
            if (isHardened) (valInt or 0x80000000u) else valInt
        }
    }

    private fun toChecksumAddress(address: String): String {
        val cleanAddress = address.removePrefix("0x").lowercase()
        val hash = Keccak256.hash(cleanAddress.encodeToByteArray()).toHexString()

        val result = StringBuilder("0x")
        for (i in cleanAddress.indices) {
            val char = cleanAddress[i]
            if (char in '0'..'9') {
                result.append(char)
            } else {
                if (hash[i] >= '8') {
                    result.append(char.uppercaseChar())
                } else {
                    result.append(char)
                }
            }
        }
        return result.toString()
    }

    private fun ByteArray.toHexString(): String {
        val hexChars = "0123456789abcdef"
        val builder = StringBuilder(this.size * 2)
        for (byte in this) {
            val v = byte.toInt() and 0xFF
            builder.append(hexChars[v shr 4])
            builder.append(hexChars[v and 0x0F])
        }
        return builder.toString()
    }

    private fun String.hexToBigInteger(): Secp256k1Pure.BigInteger {
        val len = length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            val digit1 = hexDigitToInt(this[i])
            val digit2 = hexDigitToInt(this[i + 1])
            data[i / 2] = ((digit1 shl 4) + digit2).toByte()
        }
        return Secp256k1Pure.BigInteger(data)
    }

    private fun hexDigitToInt(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $c")
        }
    }

    private fun UInt.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        )
    }

    private fun String.hexToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            val digit1 = hexDigitToInt(this[i])
            val digit2 = hexDigitToInt(this[i + 1])
            data[i / 2] = ((digit1 shl 4) + digit2).toByte()
        }
        return data
    }
}
