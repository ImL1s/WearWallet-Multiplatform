package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.Bip32
import io.github.iml1s.crypto.Pbkdf2
import io.github.iml1s.crypto.platformGetPublicKey
import io.github.iml1s.crypto.BIP39_ENGLISH_WORDLIST
import io.github.iml1s.crypto.Keccak256
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 平台特定的 SHA256 實現
 */
internal expect fun platformSha256(data: ByteArray): ByteArray

/**
 * 跨平台加密提供者實現
 * 這是一個簡化的實現，適用於所有平台包括 watchOS
 * 實際的加密操作應該在平台特定的實現中完成 (P1-6: 物理零化與契約更新)
 */
class CommonCryptoProvider(
    private val sideEffectTracker: SideEffectTracker = GlobalSideEffectTracker.instance
) : CryptoProvider {
    
    override suspend fun generateMnemonic(wordCount: Int): ScopedMnemonic = withContext(Dispatchers.Default) {
        val entropyBits = when (wordCount) {
            12 -> 128
            15 -> 160
            18 -> 192
            21 -> 224
            24 -> 256
            else -> throw IllegalArgumentException("Unsupported word count: $wordCount. Allowed word counts are 12, 15, 18, 21, and 24.")
        }
        
        val entropy = generateEntropy(entropyBits / 8)
        try {
            val chars = entropyToMnemonicChars(entropy)
            ScopedMnemonic.fromCharArray(chars, takeOwnership = true)
        } finally {
            entropy.fill(0)
        }
    }

    override suspend fun validateMnemonic(mnemonic: CharArray): Boolean = withContext(Dispatchers.Default) {
        try {
            val words = mutableListOf<String>()
            val currentWord = StringBuilder()
            for (c in mnemonic) {
                if (c.isWhitespace()) {
                    if (currentWord.isNotEmpty()) {
                        words.add(currentWord.toString().lowercase())
                        currentWord.clear()
                    }
                } else {
                    currentWord.append(c)
                }
            }
            if (currentWord.isNotEmpty()) {
                words.add(currentWord.toString().lowercase())
                currentWord.clear()
            }

            // 檢查詞數（12/15/18/21/24）
            if (words.size !in listOf(12, 15, 18, 21, 24)) {
                Logger.w("CryptoProvider", "無效的助記詞數量: ${words.size}")
                return@withContext false
            }

            // 檢查每個詞是否在 BIP39 詞表中
            val invalidWords = words.filter { !BIP39_ENGLISH_WORDLIST.contains(it) }
            if (invalidWords.isNotEmpty()) {
                Logger.w("CryptoProvider", "無效的詞: $invalidWords")
                return@withContext false
            }

            // 驗證 BIP39 校驗和
            val indices = words.map { BIP39_ENGLISH_WORDLIST.indexOf(it) }
            val bitString = indices.joinToString("") {
                it.toString(2).padStart(11, '0')
            }

            // 計算熵位數和校驗和位數
            val entropyBits = (words.size * 11) - (words.size / 3)
            val checksumBits = words.size / 3

            val entropy = bitString.take(entropyBits)
            val checksum = bitString.drop(entropyBits)

            // 將熵轉換為字節陣列
            val entropyBytes = entropy.chunked(8)
                .map { it.toInt(2).toByte() }
                .toByteArray()

            // 計算預期的校驗和
            val sha256Hash = sha256(entropyBytes)
            val sha256Bits = sha256Hash.joinToString("") {
                it.toInt().and(0xFF).toString(2).padStart(8, '0')
            }
            val expectedChecksum = sha256Bits.take(checksumBits)

            // 比對校驗和
            val isValid = checksum == expectedChecksum

            if (!isValid) {
                Logger.w("CryptoProvider", "校驗和驗證失敗")
                Logger.d("CryptoProvider", "預期: $expectedChecksum, 實際: $checksum")
            } else {
                Logger.d("CryptoProvider", "助記詞驗證成功")
            }

            isValid
        } catch (e: Exception) {
            Logger.e("CryptoProvider", "助記詞驗證錯誤", e)
            false
        }
    }
    
    override suspend fun generateKeyPairFromMnemonic(
        mnemonic: CharArray,
        derivationPath: String,
        chainType: ChainType
    ): KeyPair = withContext(Dispatchers.Default) {
        val mnemStr = String(mnemonic)
        val seed = try {
            mnemonicToSeed(mnemStr)
        } finally {
            // Ephemeral string scope
        }

        try {
            val extendedKey = Bip32.derivePath(seed, derivationPath)
            val privateKey = extendedKey.privateKey
            val publicKey = extendedKey.getPublicKey()

            KeyPair(
                publicKey = publicKey.toHexString(),
                privateKeyBytes = privateKey.copyOf()
            )
        } finally {
            seed.fill(0)
        }
    }

    override suspend fun generateKeyPairFromPrivateKey(privateKeyBytes: ByteArray): KeyPair = withContext(Dispatchers.Default) {
        val keyBytesCopy = privateKeyBytes.copyOf()
        try {
            val publicKey = platformGetPublicKey(keyBytesCopy)

            KeyPair(
                publicKey = publicKey.toHexString(),
                privateKeyBytes = keyBytesCopy.copyOf()
            )
        } finally {
            keyBytesCopy.fill(0)
        }
    }
    
    override suspend fun deriveAddress(publicKey: String): String = withContext(Dispatchers.Default) {
        val publicKeyBytes = publicKey.hexToByteArray()
        Keccak256.ethereumAddress(publicKeyBytes)
    }
    
    override suspend fun deriveAddressFromXpub(
        xpub: String,
        derivationPath: String,
        isTestnet: Boolean,
        policy: ExtendedPublicKeyPolicy?
    ): String = deriveAddressFromXpub(
        xpub = xpub,
        derivationPath = derivationPath,
        isTestnet = isTestnet,
        policy = policy,
        masterFingerprint = ""
    )

    suspend fun deriveAddressFromXpub(
        xpub: String,
        derivationPath: String,
        isTestnet: Boolean = false,
        policy: ExtendedPublicKeyPolicy? = null,
        masterFingerprint: String = ""
    ): String = withContext(Dispatchers.Default) {
        if (xpub.isBlank()) throw IllegalArgumentException("xpub must not be empty")
        policy?.validate(masterFingerprint = masterFingerprint, xpub = xpub, derivationPath = derivationPath, isTestnet = isTestnet)
        io.github.iml1s.crypto.PureEthereumCrypto.deriveAddressFromXpub(xpub, derivationPath, isTestnet = isTestnet)
    }

    override suspend fun encrypt(data: ByteArray, password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val passwordBytes = password.encodeToUtf8Bytes()
        val salt = "wearwallet_salt_v2".encodeToByteArray()
        val key = CryptoUtils.pbkdf2(passwordBytes, salt, 100_000, 32)
        try {
            val enc = CryptoUtils.aesGcmEncrypt(data, key)
            enc.nonce + enc.authTag + enc.ciphertext
        } finally {
            passwordBytes.fill(0)
            key.fill(0)
        }
    }

    override suspend fun decrypt(encryptedData: ByteArray, password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        require(encryptedData.size >= 28) { "Invalid encrypted data length: ${encryptedData.size} (need >= 28)" }
        val nonce = encryptedData.sliceArray(0 until 12)
        val authTag = encryptedData.sliceArray(12 until 28)
        val ciphertext = encryptedData.sliceArray(28 until encryptedData.size)
        val passwordBytes = password.encodeToUtf8Bytes()
        val salt = "wearwallet_salt_v2".encodeToByteArray()
        val key = CryptoUtils.pbkdf2(passwordBytes, salt, 100_000, 32)
        try {
            CryptoUtils.aesGcmDecrypt(EncryptedData(ciphertext, nonce, authTag), key)
        } finally {
            passwordBytes.fill(0)
            key.fill(0)
        }
    }
    
    // 輔助函數

    private suspend fun sha256(data: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        platformSha256(data)
    }

    private fun generateEntropy(bytes: Int): ByteArray {
        return CryptoUtils.randomBytes(bytes)
    }
    
    private fun entropyToMnemonicChars(entropy: ByteArray): CharArray {
        // 計算校驗和
        val hash = simpleHash(entropy)
        val checksumBits = entropy.size / 4
        
        // 將 entropy + checksum 轉換為 11-bit 索引
        val bits = entropy.toBitString() + hash.toBitString().take(checksumBits)
        
        val wordList = mutableListOf<String>()
        var totalChars = 0
        for (i in bits.indices step 11) {
            if (i + 11 <= bits.length) {
                val index = bits.substring(i, i + 11).toInt(2)
                val word = BIP39_ENGLISH_WORDLIST[index]
                wordList.add(word)
                totalChars += word.length
            }
        }
        totalChars += maxOf(0, wordList.size - 1) // spaces
        val result = CharArray(totalChars)
        var pos = 0
        for (idx in wordList.indices) {
            val word = wordList[idx]
            for (c in word) {
                result[pos++] = c
            }
            if (idx < wordList.size - 1) {
                result[pos++] = ' '
            }
        }
        return result
    }
    
    private fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        return Pbkdf2.bip39Seed(mnemonic, passphrase)
    }

    private fun simpleHash(data: ByteArray): ByteArray {
        return platformSha256(data)
    }
    
    // 擴展函數
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }
    
    private fun String.hexToByteArray(): ByteArray {
        val hex = removePrefix("0x")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
    
    private fun ByteArray.toBitString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }
    }
}