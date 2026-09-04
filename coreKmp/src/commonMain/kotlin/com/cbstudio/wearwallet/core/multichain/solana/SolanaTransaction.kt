package com.cbstudio.wearwallet.core.multichain.solana

import io.github.iml1s.address.Base58

import kotlin.experimental.and

/**
 * Solana Transaction 結構
 */
data class SolanaTransaction(
    val signatures: List<ByteArray>,
    val message: SolanaMessage
) {
    /**
     * 序列化交易為 Base58 字串
     */
    fun serialize(): String {
        val buffer = mutableListOf<Byte>()

        // 1. 寫入簽名數量 (compact-u16 編碼)
        buffer.addAll(encodeCompactU16(signatures.size))

        // 2. 寫入所有簽名 (每個 64 bytes)
        signatures.forEach { signature ->
            buffer.addAll(signature.toList())
        }

        // 3. 寫入 message
        buffer.addAll(message.serialize().toList())

        return Base58.encode(buffer.toByteArray())
    }

    /**
     * 從 Base58 字串反序列化交易
     */
    companion object {
        fun deserialize(base58: String): SolanaTransaction {
            val bytes = Base58.decode(base58) ?: throw IllegalArgumentException("Invalid Base58 string: $base58")
            var offset = 0

            // 1. 讀取簽名數量
            val (sigCount, sigOffset) = decodeCompactU16(bytes, offset)
            offset = sigOffset

            // 2. 讀取所有簽名
            val signatures = mutableListOf<ByteArray>()
            repeat(sigCount) {
                signatures.add(bytes.copyOfRange(offset, offset + 64))
                offset += 64
            }

            // 3. 讀取 message
            val message = SolanaMessage.deserialize(bytes, offset)

            return SolanaTransaction(signatures, message)
        }
    }
}

/**
 * Solana Message 結構
 */
data class SolanaMessage(
    val header: MessageHeader,
    val accountKeys: List<String>,  // Base58 公鑰
    val recentBlockhash: String,     // Base58 blockhash
    val instructions: List<CompiledInstruction>
) {
    /**
     * 序列化 Message
     */
    fun serialize(): ByteArray {
        val buffer = mutableListOf<Byte>()

        // 1. Header (3 bytes)
        buffer.add(header.numRequiredSignatures.toByte())
        buffer.add(header.numReadonlySignedAccounts.toByte())
        buffer.add(header.numReadonlyUnsignedAccounts.toByte())

        // 2. Account keys 數量和內容
        buffer.addAll(encodeCompactU16(accountKeys.size))
        accountKeys.forEach { key ->
            val keyBytes = Base58.decode(key) ?: throw IllegalArgumentException("Invalid base58 public key: $key")
            if (keyBytes.size != 32) {
                throw IllegalArgumentException("Invalid public key length: ${keyBytes.size}")
            }
            buffer.addAll(keyBytes.toList())
        }

        // 3. Recent blockhash (32 bytes)
        val blockhashBytes = Base58.decode(recentBlockhash) ?: throw IllegalArgumentException("Invalid base58 blockhash: $recentBlockhash")
        if (blockhashBytes.size != 32) {
            throw IllegalArgumentException("Invalid blockhash length: ${blockhashBytes.size}")
        }
        buffer.addAll(blockhashBytes.toList())

        // 4. Instructions 數量和內容
        buffer.addAll(encodeCompactU16(instructions.size))
        instructions.forEach { instruction ->
            buffer.addAll(instruction.serialize().toList())
        }

        return buffer.toByteArray()
    }

    companion object {
        fun deserialize(bytes: ByteArray, startOffset: Int): SolanaMessage {
            var offset = startOffset

            // 1. Header
            val header = MessageHeader(
                numRequiredSignatures = bytes[offset++].toInt() and 0xFF,
                numReadonlySignedAccounts = bytes[offset++].toInt() and 0xFF,
                numReadonlyUnsignedAccounts = bytes[offset++].toInt() and 0xFF
            )

            // 2. Account keys
            val (keyCount, keyOffset) = decodeCompactU16(bytes, offset)
            offset = keyOffset
            val accountKeys = mutableListOf<String>()
            repeat(keyCount) {
                val keyBytes = bytes.copyOfRange(offset, offset + 32)
                accountKeys.add(Base58.encode(keyBytes))
                offset += 32
            }

            // 3. Recent blockhash
            val blockhashBytes = bytes.copyOfRange(offset, offset + 32)
            val recentBlockhash = Base58.encode(blockhashBytes)
            offset += 32

            // 4. Instructions
            val (instructionCount, instructionOffset) = decodeCompactU16(bytes, offset)
            offset = instructionOffset
            val instructions = mutableListOf<CompiledInstruction>()
            repeat(instructionCount) {
                val (instruction, newOffset) = CompiledInstruction.deserialize(bytes, offset)
                instructions.add(instruction)
                offset = newOffset
            }

            return SolanaMessage(header, accountKeys, recentBlockhash, instructions)
        }
    }
}

/**
 * Message Header
 */
data class MessageHeader(
    val numRequiredSignatures: Int,
    val numReadonlySignedAccounts: Int,
    val numReadonlyUnsignedAccounts: Int
)

/**
 * Compiled Instruction
 */
data class CompiledInstruction(
    val programIdIndex: Int,
    val accountIndexes: List<Int>,
    val data: ByteArray
) {
    fun serialize(): ByteArray {
        val buffer = mutableListOf<Byte>()

        // 1. Program ID index
        buffer.add(programIdIndex.toByte())

        // 2. Account indexes 數量和內容
        buffer.addAll(encodeCompactU16(accountIndexes.size))
        accountIndexes.forEach { index ->
            buffer.add(index.toByte())
        }

        // 3. Data 長度和內容
        buffer.addAll(encodeCompactU16(data.size))
        buffer.addAll(data.toList())

        return buffer.toByteArray()
    }

    companion object {
        fun deserialize(bytes: ByteArray, startOffset: Int): Pair<CompiledInstruction, Int> {
            var offset = startOffset

            // 1. Program ID index
            val programIdIndex = bytes[offset++].toInt() and 0xFF

            // 2. Account indexes
            val (accountCount, accountOffset) = decodeCompactU16(bytes, offset)
            offset = accountOffset
            val accountIndexes = mutableListOf<Int>()
            repeat(accountCount) {
                accountIndexes.add(bytes[offset++].toInt() and 0xFF)
            }

            // 3. Data
            val (dataLength, dataOffset) = decodeCompactU16(bytes, offset)
            offset = dataOffset
            val data = bytes.copyOfRange(offset, offset + dataLength)
            offset += dataLength

            return Pair(CompiledInstruction(programIdIndex, accountIndexes, data), offset)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CompiledInstruction

        if (programIdIndex != other.programIdIndex) return false
        if (accountIndexes != other.accountIndexes) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = programIdIndex
        result = 31 * result + accountIndexes.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Compact-u16 編碼 (Solana 使用的變長整數編碼)
 */
fun encodeCompactU16(value: Int): List<Byte> {
    val bytes = mutableListOf<Byte>()
    var remaining = value

    while (true) {
        var byte = (remaining and 0x7F).toByte()
        remaining = remaining ushr 7

        if (remaining == 0) {
            bytes.add(byte)
            break
        } else {
            byte = (byte.toInt() or 0x80).toByte()
            bytes.add(byte)
        }
    }

    return bytes
}

/**
 * Compact-u16 解碼
 * 返回 Pair<解碼值, 新的偏移量>
 */
fun decodeCompactU16(bytes: ByteArray, offset: Int): Pair<Int, Int> {
    var value = 0
    var shift = 0
    var currentOffset = offset

    while (true) {
        val byte = bytes[currentOffset++].toInt() and 0xFF
        value = value or ((byte and 0x7F) shl shift)

        if ((byte and 0x80) == 0) {
            break
        }

        shift += 7
    }

    return Pair(value, currentOffset)
}

/**
 * Transaction Builder 輔助類
 */
class SolanaTransactionBuilder {
    private val instructions = mutableListOf<CompiledInstruction>()
    private val accountKeys = mutableListOf<String>()
    private var recentBlockhash: String = ""
    private val signers = mutableListOf<String>()

    /**
     * 添加指令
     */
    fun addInstruction(
        programId: String,
        accounts: List<AccountMeta>,
        data: ByteArray
    ): SolanaTransactionBuilder {
        // 將所有相關的 account 添加到 accountKeys 中
        accounts.forEach { meta ->
            if (!accountKeys.contains(meta.pubkey)) {
                accountKeys.add(meta.pubkey)
            }
        }

        if (!accountKeys.contains(programId)) {
            accountKeys.add(programId)
        }

        // 創建 compiled instruction
        val programIdIndex = accountKeys.indexOf(programId)
        val accountIndexes = accounts.map { accountKeys.indexOf(it.pubkey) }

        instructions.add(CompiledInstruction(programIdIndex, accountIndexes, data))

        return this
    }

    /**
     * 設置 recent blockhash
     */
    fun setRecentBlockhash(blockhash: String): SolanaTransactionBuilder {
        this.recentBlockhash = blockhash
        return this
    }

    /**
     * 添加簽名者
     */
    fun addSigner(signer: String): SolanaTransactionBuilder {
        if (!signers.contains(signer)) {
            signers.add(signer)
            if (!accountKeys.contains(signer)) {
                accountKeys.add(0, signer)  // Signers 必須在最前面
            }
        }
        return this
    }

    /**
     * 建構交易
     */
    fun build(): SolanaTransaction {
        // 重新排序 accountKeys: signers first
        val orderedKeys = mutableListOf<String>()
        orderedKeys.addAll(signers)
        accountKeys.forEach { key ->
            if (!signers.contains(key)) {
                orderedKeys.add(key)
            }
        }

        val header = MessageHeader(
            numRequiredSignatures = signers.size,
            numReadonlySignedAccounts = 0,
            numReadonlyUnsignedAccounts = 0
        )

        val message = SolanaMessage(
            header = header,
            accountKeys = orderedKeys,
            recentBlockhash = recentBlockhash,
            instructions = instructions
        )

        // 創建空簽名 (實際簽名需要後續添加)
        val emptySignatures = List(signers.size) { ByteArray(64) }

        return SolanaTransaction(emptySignatures, message)
    }
}

/**
 * Account Meta
 */
data class AccountMeta(
    val pubkey: String,
    val isSigner: Boolean,
    val isWritable: Boolean
)
