package com.cbstudio.wearwallet.domain.service

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import io.github.iml1s.crypto.Base58
import com.cbstudio.wearwallet.core.security.KeystoreManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Solana 交易服務
 *
 * 處理 Solana 交易的構建、簽名和發送
 * 使用 coreKmp 的 CryptoSignature 進行 Ed25519 簽名
 *
 * 架構：
 * - 基於 coreKmp 的跨平台加密實現
 * - 使用 Koin DI 進行依賴注入
 * - 支援 SOL 轉帳和 SPL Token 轉帳
 */
class SolanaTransactionService : KoinComponent {

    private val keystoreManager: KeystoreManager by inject()

    // Raw private-key transaction sending is disabled in production. All signing must route via SecureKeyManager.

    /**
     * 估算交易費用
     *
     * Solana 交易費用通常固定為 5000 lamports
     *
     * @return 交易費用（lamports）
     */
    suspend fun estimateTransactionFee(): Result<Long> {
        return try {
            // Solana 的基本交易費用
            val baseFee = 5000L // lamports
            Timber.d("估算交易費用: $baseFee lamports (0.000005 SOL)")
            Result.success(baseFee)
        } catch (e: Exception) {
            Timber.e(e, "估算交易費用失敗")
            Result.failure(e)
        }
    }

    /**
     * 構建 System Program 的 Transfer 指令
     */
    private fun buildTransferInstruction(
        fromPublicKey: String,
        toPublicKey: String,
        lamports: Long
    ): SolanaInstruction {
        // System Program ID: 11111111111111111111111111111111
        val systemProgramId = "11111111111111111111111111111111"

        // Transfer 指令的數據格式：
        // [0..4] = 指令索引（u32，2 = Transfer）
        // [4..12] = lamports（u64）
        val data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(2) // Transfer 指令索引
            putLong(lamports)
        }.array()

        return SolanaInstruction(
            programId = systemProgramId,
            accounts = listOf(
                AccountMeta(
                    publicKey = fromPublicKey,
                    isSigner = true,
                    isWritable = true
                ),
                AccountMeta(
                    publicKey = toPublicKey,
                    isSigner = false,
                    isWritable = true
                )
            ),
            data = data
        )
    }

    /**
     * 構建交易消息
     */
    private fun buildTransactionMessage(
        instructions: List<SolanaInstruction>,
        recentBlockhash: String,
        feePayer: String
    ): SolanaMessage {
        // 收集所有賬戶及其元數據
        val accountMetaMap = mutableMapOf<String, AccountMeta>()

        // 添加 fee payer（必須是 signer + writable）
        accountMetaMap[feePayer] = AccountMeta(
            publicKey = feePayer,
            isSigner = true,
            isWritable = true
        )

        // 收集指令中的所有賬戶
        instructions.forEach { instruction ->
            instruction.accounts.forEach { account ->
                // 如果賬戶已存在，合併屬性（取最寬鬆的權限）
                val existing = accountMetaMap[account.publicKey]
                if (existing != null) {
                    accountMetaMap[account.publicKey] = AccountMeta(
                        publicKey = account.publicKey,
                        isSigner = existing.isSigner || account.isSigner,
                        isWritable = existing.isWritable || account.isWritable
                    )
                } else {
                    accountMetaMap[account.publicKey] = account
                }
            }

            // 添加 program ID（必須是 readonly + non-signer）
            if (!accountMetaMap.containsKey(instruction.programId)) {
                accountMetaMap[instruction.programId] = AccountMeta(
                    publicKey = instruction.programId,
                    isSigner = false,
                    isWritable = false
                )
            }
        }

        // ✅ 修復問題 3: 按 Solana 規範排序賬戶
        // 排序順序:
        // 1. Signer + Writable
        // 2. Signer + Readonly
        // 3. Non-Signer + Writable
        // 4. Non-Signer + Readonly
        val sortedAccounts = sortAccountsForSolana(accountMetaMap.values.toList())

        // ✅ 修復問題 2: 正確計算 MessageHeader
        val header = calculateMessageHeader(sortedAccounts)

        val accountKeys = sortedAccounts.map { it.publicKey }

        // 編譯指令
        val compiledInstructions = instructions.map { instruction ->
            val programIdIndex = accountKeys.indexOf(instruction.programId).toByte()
            val accountIndexes = instruction.accounts.map { account ->
                accountKeys.indexOf(account.publicKey).toByte()
            }.toByteArray()

            CompiledInstruction(
                programIdIndex = programIdIndex,
                accountIndexes = accountIndexes,
                data = instruction.data
            )
        }

        return SolanaMessage(
            header = header,
            accountKeys = accountKeys,
            recentBlockhash = recentBlockhash,
            instructions = compiledInstructions
        )
    }

    /**
     * ✅ 修復問題 3: 按 Solana 規範排序賬戶
     */
    private fun sortAccountsForSolana(accounts: List<AccountMeta>): List<AccountMeta> {
        return accounts.sortedWith(
            compareBy<AccountMeta> { !it.isSigner }      // Signers first
                .thenBy { !it.isWritable }               // Writable first (within same signer group)
        )
    }

    /**
     * ✅ 修復問題 2: 正確計算 MessageHeader
     */
    private fun calculateMessageHeader(sortedAccounts: List<AccountMeta>): MessageHeader {
        // 計算需要簽名的賬戶數量
        val signerAccounts = sortedAccounts.filter { it.isSigner }
        val numRequiredSignatures = signerAccounts.size.toByte()

        // 計算只讀且需要簽名的賬戶數量
        val numReadonlySignedAccounts = signerAccounts
            .count { !it.isWritable }
            .toByte()

        // 計算只讀且不需要簽名的賬戶數量
        val numReadonlyUnsignedAccounts = sortedAccounts
            .filter { !it.isSigner && !it.isWritable }
            .size
            .toByte()

        return MessageHeader(
            numRequiredSignatures = numRequiredSignatures,
            numReadonlySignedAccounts = numReadonlySignedAccounts,
            numReadonlyUnsignedAccounts = numReadonlyUnsignedAccounts
        )
    }

    /**
     * 構建已簽名的完整交易
     */
    private fun buildSignedTransaction(
        message: SolanaMessage,
        signature: ByteArray
    ): ByteArray {
        val messageBytes = message.toByteArray()

        // 交易格式：
        // [簽名數量（compact-u16）] [簽名...] [消息]
        // ✅ 修復問題 4: 使用正確的 compact-u16 編碼
        val signatureCountBytes = SolanaUtils.compactU16Encode(1) // 1 個簽名

        val buffer = ByteBuffer.allocate(
            signatureCountBytes.size + signature.size + messageBytes.size
        )
        buffer.put(signatureCountBytes)
        buffer.put(signature)
        buffer.put(messageBytes)

        return buffer.array()
    }

    /**
     * 輔助函數：字節數組轉十六進制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SolanaTransactionService"
        const val LAMPORTS_PER_SOL = 1_000_000_000L
    }
}

/**
 * Solana 工具類
 * 包含 Solana 特定的編碼和工具方法
 */
object SolanaUtils {
    /**
     * ✅ 修復問題 4: Compact-u16 編碼實現
     *
     * Solana 使用 compact-u16 編碼來壓縮小數字：
     * - 0-127: 1 byte (0xxxxxxx)
     * - 128-16383: 2 bytes (1xxxxxxx xxxxxxxx)
     * - 16384+: 3 bytes (1xxxxxxx 1xxxxxxx xxxxxxxx)
     *
     * @param value 要編碼的值
     * @return 編碼後的字節數組
     */
    fun compactU16Encode(value: Int): ByteArray {
        return when {
            value < 0x80 -> {
                // 0-127: 單字節編碼
                byteArrayOf(value.toByte())
            }
            value < 0x4000 -> {
                // 128-16383: 雙字節編碼
                byteArrayOf(
                    ((value and 0x7f) or 0x80).toByte(),  // 低 7 位 + 繼續位
                    ((value shr 7) and 0xff).toByte()     // 高位
                )
            }
            else -> {
                // 16384+: 三字節編碼
                byteArrayOf(
                    ((value and 0x7f) or 0x80).toByte(),           // 最低 7 位 + 繼續位
                    (((value shr 7) and 0x7f) or 0x80).toByte(),   // 中間 7 位 + 繼續位
                    ((value shr 14) and 0xff).toByte()             // 最高位
                )
            }
        }
    }
}

/**
 * Solana 指令
 */
data class SolanaInstruction(
    val programId: String,
    val accounts: List<AccountMeta>,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SolanaInstruction
        if (programId != other.programId) return false
        if (accounts != other.accounts) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + accounts.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * 賬戶元數據
 */
data class AccountMeta(
    val publicKey: String,
    val isSigner: Boolean,
    val isWritable: Boolean
)

/**
 * Solana 交易消息
 */
data class SolanaMessage(
    val header: MessageHeader,
    val accountKeys: List<String>,
    val recentBlockhash: String,
    val instructions: List<CompiledInstruction>
) {
    /**
     * 序列化為字節數組
     */
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)

        // 1. 消息頭部（3 bytes）
        buffer.put(header.numRequiredSignatures)
        buffer.put(header.numReadonlySignedAccounts)
        buffer.put(header.numReadonlyUnsignedAccounts)

        // 2. 賬戶數量（compact-u16）
        // ✅ 修復：使用正確的 compact-u16 編碼
        buffer.put(SolanaUtils.compactU16Encode(accountKeys.size))

        // 3. 賬戶公鑰（每個 32 bytes）
        accountKeys.forEach { key ->
            val keyBytes = Base58.decode(key)
            buffer.put(keyBytes)
        }

        // 4. 最近的區塊哈希（32 bytes）
        val blockhashBytes = Base58.decode(recentBlockhash)
        buffer.put(blockhashBytes)

        // 5. 指令數量（compact-u16）
        // ✅ 修復：使用正確的 compact-u16 編碼
        buffer.put(SolanaUtils.compactU16Encode(instructions.size))

        // 6. 指令數據
        instructions.forEach { instruction ->
            // Program ID 索引
            buffer.put(instruction.programIdIndex)

            // 賬戶索引數量（compact-u16）
            // ✅ 修復：使用正確的 compact-u16 編碼
            buffer.put(SolanaUtils.compactU16Encode(instruction.accountIndexes.size))

            // 賬戶索引
            buffer.put(instruction.accountIndexes)

            // 指令數據長度（compact-u16）
            // ✅ 修復：使用正確的 compact-u16 編碼
            buffer.put(SolanaUtils.compactU16Encode(instruction.data.size))

            // 指令數據
            buffer.put(instruction.data)
        }

        // 返回實際使用的字節
        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        return result
    }
}

/**
 * 消息頭部
 */
data class MessageHeader(
    val numRequiredSignatures: Byte,
    val numReadonlySignedAccounts: Byte,
    val numReadonlyUnsignedAccounts: Byte
)

/**
 * 編譯後的指令
 */
data class CompiledInstruction(
    val programIdIndex: Byte,
    val accountIndexes: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompiledInstruction
        if (programIdIndex != other.programIdIndex) return false
        if (!accountIndexes.contentEquals(other.accountIndexes)) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = programIdIndex.toInt()
        result = 31 * result + accountIndexes.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
