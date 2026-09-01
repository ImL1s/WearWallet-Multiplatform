package com.cbstudio.wearwallet.core.blockchain.crypto

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.github.andreypfau.curve25519.ed25519.Ed25519PrivateKey
import io.github.andreypfau.curve25519.ed25519.Ed25519PublicKey
import kotlinx.cinterop.*
import platform.Foundation.*

/**
 * Solana 交易構建和簽名輔助類
 *
 * 實現 Solana 交易的序列化、簽名和組裝
 */
@OptIn(ExperimentalForeignApi::class)
object SolanaTransactionBuilder {

    /**
     * Base58 字符表
     */
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    /**
     * Base58 解碼為字節陣列
     * @param base58 Base58 編碼的字符串
     * @return 解碼後的字節陣列，失敗返回 null
     */
    fun base58DecodeToBytes(base58: String): ByteArray? {
        return try {
            if (base58.isEmpty()) return byteArrayOf()

            // 計算前導零的數量
            var leadingZeros = 0
            for (char in base58) {
                if (char == '1') leadingZeros++ else break
            }

            // 使用自定義大整數實現進行 Base58 解碼
            var result = mutableListOf<Byte>()
            result.add(0)

            for (i in leadingZeros until base58.length) {
                val digit = BASE58_ALPHABET.indexOf(base58[i])
                if (digit < 0) return null // 無效字符

                // 將現有結果乘以 58 並加上新數字
                var carry = digit
                for (j in result.indices.reversed()) {
                    val value = (result[j].toInt() and 0xFF) * 58 + carry
                    result[j] = (value and 0xFF).toByte()
                    carry = value shr 8
                }

                while (carry > 0) {
                    result.add(0, (carry and 0xFF).toByte())
                    carry = carry shr 8
                }
            }

            // 移除前導零（如果有）
            var startIndex = 0
            while (startIndex < result.size && result[startIndex] == 0.toByte()) {
                startIndex++
            }

            // 添加原始的前導零
            val decoded = ByteArray(leadingZeros + (result.size - startIndex))
            // 手動複製元素（避免使用 copyInto）
            for (i in startIndex until result.size) {
                decoded[leadingZeros + i - startIndex] = result[i]
            }

            decoded
        } catch (e: Exception) {
            println("❌ Base58 解碼失敗: ${e.message}")
            null
        }
    }

    /**
     * 將字節陣列編碼為 Base58
     * @param bytes 要編碼的字節陣列
     * @return Base58 編碼的字符串
     */
    fun base58Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // 計算前導零
        var leadingZeros = 0
        for (byte in bytes) {
            if (byte.toInt() == 0) leadingZeros++ else break
        }

        // 將字節陣列轉換為數字列表
        val digits = bytes.toMutableList()
        val result = StringBuilder()

        // 重複除以 58 直到數字為零
        while (digits.isNotEmpty() && !digits.all { it == 0.toByte() }) {
            var remainder = 0
            val newDigits = mutableListOf<Byte>()

            for (digit in digits) {
                val value = remainder * 256 + (digit.toInt() and 0xFF)
                val quotient = value / 58
                remainder = value % 58

                if (newDigits.isNotEmpty() || quotient > 0) {
                    newDigits.add(quotient.toByte())
                }
            }

            result.append(BASE58_ALPHABET[remainder])
            digits.clear()
            digits.addAll(newDigits)
        }

        // 添加前導 '1'
        repeat(leadingZeros) {
            result.append('1')
        }

        return result.reverse().toString()
    }

    /**
     * Compact-u16 編碼
     * Solana 使用這種編碼來表示動態長度
     *
     * 編碼規則：
     * - 0-0x7f: 單字節 [val]
     * - 0x80-0x3fff: 兩字節 [val & 0x7f | 0x80, val >> 7]
     * - 0x4000-0x3fffffff: 三字節 [val & 0x7f | 0x80, (val >> 7) & 0x7f | 0x80, val >> 14]
     */
    fun encodeCompactU16(value: Int): ByteArray {
        return when {
            value <= 0x7f -> byteArrayOf(value.toByte())
            value <= 0x3fff -> byteArrayOf(
                (value and 0x7f or 0x80).toByte(),
                (value shr 7).toByte()
            )
            value <= 0x3fffffff -> byteArrayOf(
                (value and 0x7f or 0x80).toByte(),
                ((value shr 7) and 0x7f or 0x80).toByte(),
                (value shr 14).toByte()
            )
            else -> throw IllegalArgumentException("Value too large for compact-u16: $value")
        }
    }

    /**
     * Solana 帳戶元數據
     *
     * @property publicKey 帳戶公鑰（32 字節）
     * @property isSigner 是否需要簽名
     * @property isWritable 是否可寫
     */
    data class SolanaAccount(
        val publicKey: ByteArray,
        val isSigner: Boolean,
        val isWritable: Boolean
    ) {
        init {
            require(publicKey.size == 32) { "Solana 公鑰必須是 32 字節" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as SolanaAccount
            return publicKey.contentEquals(other.publicKey) &&
                    isSigner == other.isSigner &&
                    isWritable == other.isWritable
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + isSigner.hashCode()
            result = 31 * result + isWritable.hashCode()
            return result
        }
    }

    /**
     * Solana 指令
     *
     * @property programId 程序 ID（32 字節）
     * @property accounts 指令所需的帳戶列表
     * @property data 指令數據（如函數參數）
     */
    data class SolanaInstruction(
        val programId: ByteArray,
        val accounts: List<SolanaAccount>,
        val data: ByteArray
    ) {
        init {
            require(programId.size == 32) { "程序 ID 必須是 32 字節" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as SolanaInstruction
            return programId.contentEquals(other.programId) &&
                    accounts == other.accounts &&
                    data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = programId.contentHashCode()
            result = 31 * result + accounts.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * 排序並去重所有帳戶
     *
     * Solana 帳戶排序規則（嚴格順序）：
     * 1. 可寫 + 簽名者
     * 2. 只讀 + 簽名者
     * 3. 可寫 + 非簽名者
     * 4. 只讀 + 非簽名者
     *
     * @param instructions 指令列表
     * @return 排序後的唯一帳戶列表
     */
    private fun sortAndDeduplicateAccounts(instructions: List<SolanaInstruction>): List<SolanaAccount> {
        // 收集所有帳戶（包括程序 ID）
        val accountMap = mutableMapOf<String, SolanaAccount>()

        for (instruction in instructions) {
            // 添加程序 ID（只讀 + 非簽名者）
            val programKeyHex = instruction.programId.toHex()
            if (!accountMap.containsKey(programKeyHex)) {
                accountMap[programKeyHex] = SolanaAccount(
                    publicKey = instruction.programId,
                    isSigner = false,
                    isWritable = false
                )
            }

            // 添加指令的帳戶
            for (account in instruction.accounts) {
                val keyHex = account.publicKey.toHex()
                val existing = accountMap[keyHex]

                // 如果帳戶已存在，合併權限（取最高權限）
                if (existing != null) {
                    accountMap[keyHex] = SolanaAccount(
                        publicKey = account.publicKey,
                        isSigner = existing.isSigner || account.isSigner,
                        isWritable = existing.isWritable || account.isWritable
                    )
                } else {
                    accountMap[keyHex] = account
                }
            }
        }

        // 按照 Solana 規則排序
        return accountMap.values.sortedWith(compareBy(
            { !it.isSigner },     // 簽名者優先
            { !it.isWritable },   // 可寫優先（在簽名者組內）
        ))
    }

    /**
     * 計算消息頭
     *
     * @param sortedAccounts 已排序的帳戶列表
     * @return 包含三個字節的 MessageHeader
     */
    private fun calculateMessageHeader(sortedAccounts: List<SolanaAccount>): ByteArray {
        var numRequiredSignatures = 0
        var numReadonlySignedAccounts = 0
        var numReadonlyUnsignedAccounts = 0

        for (account in sortedAccounts) {
            if (account.isSigner) {
                numRequiredSignatures++
                if (!account.isWritable) {
                    numReadonlySignedAccounts++
                }
            } else if (!account.isWritable) {
                numReadonlyUnsignedAccounts++
            }
        }

        return byteArrayOf(
            numRequiredSignatures.toByte(),
            numReadonlySignedAccounts.toByte(),
            numReadonlyUnsignedAccounts.toByte()
        )
    }

    /**
     * 編譯指令（將帳戶地址轉換為索引）
     *
     * @param instruction 原始指令
     * @param accountKeys 已排序的所有帳戶公鑰列表
     * @return 編譯後的指令字節陣列
     */
    private fun compileInstruction(
        instruction: SolanaInstruction,
        accountKeys: List<ByteArray>
    ): ByteArray {
        // 1. 找到程序 ID 的索引
        val programIdIndex = accountKeys.indexOfFirst { it.contentEquals(instruction.programId) }
        require(programIdIndex >= 0) { "找不到程序 ID" }
        require(programIdIndex <= 255) { "程序 ID 索引超出 u8 範圍" }

        // 2. 找到所有帳戶的索引
        val accountIndexes = mutableListOf<Byte>()
        for (account in instruction.accounts) {
            val index = accountKeys.indexOfFirst { it.contentEquals(account.publicKey) }
            require(index >= 0) { "找不到帳戶: ${account.publicKey.toHex()}" }
            require(index <= 255) { "帳戶索引超出 u8 範圍" }
            accountIndexes.add(index.toByte())
        }

        // 3. 組裝編譯後的指令
        // [program_id_index: u8][accounts_count: compact-u16][accounts: [u8]][data_len: compact-u16][data: [u8]]
        val result = mutableListOf<Byte>()

        // 程序 ID 索引
        result.add(programIdIndex.toByte())

        // 帳戶數量和索引
        result.addAll(encodeCompactU16(accountIndexes.size).toList())
        result.addAll(accountIndexes)

        // 指令數據長度和數據
        result.addAll(encodeCompactU16(instruction.data.size).toList())
        result.addAll(instruction.data.toList())

        return result.toByteArray()
    }

    /**
     * 構建 Solana 交易消息（完整實現）
     *
     * Solana 消息格式：
     * 1. Message Header (3 bytes)
     *    - numRequiredSignatures: 需要的簽名數量
     *    - numReadonlySignedAccounts: 只讀已簽名帳戶數量
     *    - numReadonlyUnsignedAccounts: 只讀未簽名帳戶數量
     * 2. Account Keys (compact-u16 + accounts)
     *    - compact-u16: 帳戶數量
     *    - accounts: 每個帳戶 32 字節公鑰
     * 3. Recent Blockhash (32 bytes)
     *    - 最近的區塊哈希
     * 4. Instructions (compact-u16 + instruction data)
     *    - compact-u16: 指令數量
     *    - instructions: 編譯後的指令列表
     *
     * @param instructions 指令列表
     * @param blockhashBytes 區塊哈希字節陣列（32 字節）
     * @return 構建好的消息字節陣列
     */
    fun buildSolanaTransactionMessage(
        instructions: List<SolanaInstruction>,
        blockhashBytes: ByteArray
    ): ByteArray {
        require(instructions.isNotEmpty()) { "至少需要一個指令" }
        require(blockhashBytes.size == 32) { "區塊哈希必須是 32 字節" }

        // 1. 排序並去重所有帳戶
        val sortedAccounts = sortAndDeduplicateAccounts(instructions)

        // 2. 計算消息頭
        val header = calculateMessageHeader(sortedAccounts)

        // 3. 準備帳戶公鑰列表
        val accountKeys = sortedAccounts.map { it.publicKey }

        // 4. 編譯所有指令
        val compiledInstructions = instructions.map { compileInstruction(it, accountKeys) }

        // 5. 組裝消息
        val message = mutableListOf<Byte>()

        // Message Header (3 bytes)
        message.addAll(header.toList())

        // Account Keys (compact-u16 + keys)
        message.addAll(encodeCompactU16(accountKeys.size).toList())
        for (key in accountKeys) {
            message.addAll(key.toList())
        }

        // Recent Blockhash (32 bytes)
        message.addAll(blockhashBytes.toList())

        // Instructions (compact-u16 + compiled instructions)
        message.addAll(encodeCompactU16(compiledInstructions.size).toList())
        for (instruction in compiledInstructions) {
            message.addAll(instruction.toList())
        }

        return message.toByteArray()
    }

    /**
     * 構建簡單的 SOL 轉帳交易消息
     *
     * @param fromPublicKey 發送方公鑰（32 字節）
     * @param toPublicKey 接收方公鑰（32 字節）
     * @param lamports 轉帳金額（lamports）
     * @param blockhashBytes 區塊哈希（32 字節）
     * @return 交易消息字節陣列
     */
    fun buildSolTransferMessage(
        fromPublicKey: ByteArray,
        toPublicKey: ByteArray,
        lamports: Long,
        blockhashBytes: ByteArray
    ): ByteArray {
        require(fromPublicKey.size == 32) { "發送方公鑰必須是 32 字節" }
        require(toPublicKey.size == 32) { "接收方公鑰必須是 32 字節" }
        require(blockhashBytes.size == 32) { "區塊哈希必須是 32 字節" }
        require(lamports > 0) { "轉帳金額必須大於 0" }

        // System Program ID: 11111111111111111111111111111111
        val systemProgramId = ByteArray(32) // 全零表示 System Program

        // 構建轉帳指令數據
        // Transfer instruction format:
        // [instruction_index: u32 = 2][lamports: u64]
        val instructionData = ByteArray(12)
        instructionData[0] = 2  // Transfer instruction index
        // 將 lamports 寫入為 little-endian u64
        for (i in 0 until 8) {
            instructionData[4 + i] = ((lamports shr (i * 8)) and 0xFF).toByte()
        }

        // 創建轉帳指令
        val transferInstruction = SolanaInstruction(
            programId = systemProgramId,
            accounts = listOf(
                SolanaAccount(fromPublicKey, isSigner = true, isWritable = true),
                SolanaAccount(toPublicKey, isSigner = false, isWritable = true)
            ),
            data = instructionData
        )

        return buildSolanaTransactionMessage(listOf(transferInstruction), blockhashBytes)
    }

    /**
     * 舊版本兼容：從預構建的交易數據構建消息
     * （向後兼容，但建議使用新的 API）
     */
    @Deprecated("請使用新的 buildSolanaTransactionMessage(instructions, blockhash) API")
    fun buildSolanaTransactionMessage(
        transaction: ByteArray,
        blockhashBytes: ByteArray
    ): ByteArray {
        // 簡化實現：假設 transaction 已經包含了部分消息數據
        // 這是為了向後兼容舊代碼

        // 消息 Header（簡化版）
        val header = byteArrayOf(
            1.toByte(),  // numRequiredSignatures: 1 個簽名
            0.toByte(),  // numReadonlySignedAccounts: 0
            1.toByte()   // numReadonlyUnsignedAccounts: 1
        )

        // 組裝消息
        return header + transaction + blockhashBytes
    }

    /**
     * 使用 Ed25519 對原始數據進行簽名
     *
     * 此實現使用 curve25519-kotlin 庫來執行 Ed25519 簽名。
     * 這是一個純 Kotlin Multiplatform 實現，支援所有平台。
     *
     * 技術方案選擇：
     * - ❌ 方案 A: 使用 iOS CryptoKit - cinterop 困難，Swift API 不易橋接
     * - ❌ 方案 B: 使用 Security Framework - 不支持 Ed25519，僅支持 ECDSA
     * - ✅ 方案 C: 使用 curve25519-kotlin - 跨平台、純 Kotlin、易於整合
     *
     * @param message 要簽名的消息字節陣列
     * @param privateKeyBytes Ed25519 種子/私鑰字節陣列 (32 字節)
     * @return 64 字節的 Ed25519 分離簽名
     * @throws IllegalArgumentException 如果私鑰長度不正確
     */
    fun signWithEd25519Raw(message: ByteArray, privateKeyBytes: ByteArray): ByteArray {
        require(privateKeyBytes.size == 32) {
            "Ed25519 私鑰種子必須是 32 字節，當前: ${privateKeyBytes.size} 字節"
        }

        return try {
            // 使用 curve25519-kotlin 進行真正的 Ed25519 簽名
            // Ed25519.keyFromSeed() 接受 32 字節的種子
            val privateKey = Ed25519.keyFromSeed(privateKeyBytes)
            val signature = privateKey.sign(message)

            println("✅ Ed25519 簽名完成: ${signature.take(16).toByteArray().toHex()}...")
            signature
        } catch (e: Exception) {
            println("❌ Ed25519 簽名失敗: ${e.message}")
            throw IllegalStateException("Ed25519 簽名失敗: ${e.message}", e)
        }
    }

    /**
     * 簡單的哈希函數（用於測試目的）
     * 在生產環境中應該使用 SHA-256 或其他標準哈希算法
     */
    private fun simpleHash(data: ByteArray): ByteArray {
        val hash = ByteArray(32)
        var h1 = 0x6a09e667L
        var h2 = 0xbb67ae85uL.toLong()  // 使用 unsigned literal 避免溢出

        for (i in data.indices) {
            val byte = (data[i].toInt() and 0xFF).toLong()
            h1 = (h1 xor byte) * 0x100000001b3L
            h2 = h2.xor(byte) + 0x1000193L  // 簡化乘法避免溢出
        }

        // 將兩個 64 位哈希值轉換為 32 字節
        for (i in 0 until 16) {
            hash[i] = ((h1 shr (i * 4)) and 0xFF).toByte()
            hash[i + 16] = ((h2 shr (i * 4)) and 0xFF).toByte()
        }

        return hash
    }

    /**
     * 驗證 Ed25519 簽名
     * 使用 curve25519-kotlin 進行真正的 Ed25519 驗證
     *
     * @param message 原始消息
     * @param signature 64 字節簽名
     * @param publicKeyBytes 32 字節公鑰
     * @return 簽名是否有效
     */
    fun verifyEd25519Signature(
        message: ByteArray,
        signature: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        require(signature.size == 64) { "Ed25519 簽名必須是 64 字節" }
        require(publicKeyBytes.size == 32) { "Ed25519 公鑰必須是 32 字節" }

        return try {
            // 使用 curve25519-kotlin 進行真正的 Ed25519 驗證
            val publicKey = Ed25519PublicKey(publicKeyBytes)
            val isValid = publicKey.verify(message, signature)

            if (isValid) {
                println("✅ Ed25519 簽名驗證成功")
            } else {
                println("❌ Ed25519 簽名驗證失敗 - 簽名無效")
            }

            isValid
        } catch (e: Exception) {
            println("❌ Ed25519 簽名驗證異常: ${e.message}")
            false
        }
    }

    /**
     * 組裝已簽名的 Solana 交易
     *
     * 格式：
     * 1. Compact-u16: 簽名數量
     * 2. 簽名陣列 (每個 64 字節)
     * 3. 消息
     *
     * @param signature 簽名字節陣列（64 字節）
     * @param message 消息字節陣列
     * @return 完整的已簽名交易
     */
    fun assembleSolanaSignedTransaction(
        signature: ByteArray,
        message: ByteArray
    ): ByteArray {
        // 簽名數量（compact-u16 編碼）
        val sigCount = encodeCompactU16(1)

        // 組裝：[簽名數量][簽名][消息]
        return sigCount + signature + message
    }

    /**
     * 擴展函數：ByteArray 轉十六進制
     */
    private fun ByteArray.toHex(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            if (hex < 16) "0${hex.toString(16)}" else hex.toString(16)
        }
    }
}