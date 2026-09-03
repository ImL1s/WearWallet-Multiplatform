package io.github.iml1s.tx.bitcoin

import io.github.iml1s.tx.crypto.Crypto
import io.github.iml1s.tx.utils.ByteVector32
import io.github.iml1s.tx.utils.byteVector32
import kotlin.jvm.JvmField

/**
 * This is the double hash of a transaction serialized without witness data.
 * Note that this is confusingly called `txid` in some context (e.g. in lightning messages).
 */
public data class TxHash(@JvmField val value: ByteVector32) {
    public constructor(hash: ByteArray) : this(hash.byteVector32())
    public constructor(hash: String) : this(ByteVector32(hash))
    public constructor(txid: TxId) : this(txid.value.reversed())

    override fun toString(): String = value.toString()
}

/**
 * This contains the same data as [TxHash], but encoded with the opposite endianness.
 * Some explorers and bitcoin RPCs use this encoding for their inputs.
 */
public data class TxId(@JvmField val value: ByteVector32) {
    public constructor(txid: ByteArray) : this(txid.byteVector32())
    public constructor(txid: String) : this(ByteVector32(txid))
    public constructor(hash: TxHash) : this(hash.value.reversed())

    override fun toString(): String = value.toString()
}

/**
 * Bitcoin 交易輸入 (TxIn)
 *
 * 符合 Bitcoin Protocol 規範
 * @see <a href="https://en.bitcoin.it/wiki/Transaction">Bitcoin Transaction</a>
 */
data class TxInput(
    /**
     * 前一筆交易的 txid (32 bytes, little-endian)
     */
    val previousTxHash: ByteArray,

    /**
     * 前一筆交易的輸出索引
     */
    val previousOutputIndex: Long,

    /**
     * scriptSig (解鎖腳本)
     * 對於 SegWit 交易，此欄位為空
     */
    val scriptSig: ByteArray = ByteArray(0),

    /**
     * 序列號（用於 RBF 和 nLockTime）
     * 0xFFFFFFFF = 最終交易（不可替換）
     * 0xFFFFFFFE = 可選 RBF
     * < 0xFFFFFFFE = 啟用 RBF
     */
    val sequence: Long = SEQUENCE_FINAL
) {
    init {
        require(previousTxHash.size == 32) { "Previous tx hash must be 32 bytes" }
    }

    /**
     * 是否是 coinbase 輸入
     */
    fun isCoinbase(): Boolean {
        return previousTxHash.all { it == 0.toByte() } && previousOutputIndex == 0xFFFFFFFFL
    }

    /**
     * 是否啟用 RBF (Replace-By-Fee)
     */
    fun isRbfEnabled(): Boolean {
        return sequence < SEQUENCE_RBF_DISABLED
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TxInput
        return previousTxHash.contentEquals(other.previousTxHash) &&
                previousOutputIndex == other.previousOutputIndex &&
                scriptSig.contentEquals(other.scriptSig) &&
                sequence == other.sequence
    }

    override fun hashCode(): Int {
        var result = previousTxHash.contentHashCode()
        result = 31 * result + previousOutputIndex.hashCode()
        result = 31 * result + scriptSig.contentHashCode()
        result = 31 * result + sequence.hashCode()
        return result
    }

    companion object {
        const val SEQUENCE_FINAL: Long = 0xFFFFFFFFL
        const val SEQUENCE_RBF_DISABLED: Long = 0xFFFFFFFEL
        const val SEQUENCE_LOCKTIME_ENABLED: Long = 0xFFFFFFFDL

        fun read(reader: ByteArrayReader): TxInput {
            val previousTxHash = reader.readBytes(32)
            val previousOutputIndex = reader.readInt32LE().toLong() and 0xFFFFFFFFL
            val scriptSig = reader.readScript()
            val sequence = reader.readInt32LE().toLong() and 0xFFFFFFFFL
            return TxInput(previousTxHash, previousOutputIndex, scriptSig, sequence)
        }
    }
}

/**
 * Bitcoin 交易輸出 (TxOut)
 */
data class TxOutput(
    /**
     * 輸出金額（satoshis）
     */
    val value: Long,

    /**
     * scriptPubKey (鎖定腳本)
     */
    val scriptPubKey: ByteArray
) {
    init {
        require(value >= 0) { "Value must be non-negative" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TxOutput
        return value == other.value && scriptPubKey.contentEquals(other.scriptPubKey)
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + scriptPubKey.contentHashCode()
        return result
    }

    companion object {
        fun read(reader: ByteArrayReader): TxOutput {
            val value = reader.readInt64LE()
            val scriptPubKey = reader.readScript()
            return TxOutput(value, scriptPubKey)
        }
    }
}

/**
 * Bitcoin 見證資料 (Witness)
 *
 * 用於 SegWit 交易
 */
data class TxWitness(
    /**
     * 見證堆疊項目
     */
    val stack: List<ByteArray> = emptyList()
) {
    fun isEmpty(): Boolean = stack.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TxWitness
        if (stack.size != other.stack.size) return false
        return stack.zip(other.stack).all { (a, b) -> a.contentEquals(b) }
    }

    override fun hashCode(): Int {
        return stack.fold(0) { acc, bytes -> 31 * acc + bytes.contentHashCode() }
    }

    companion object {
        val EMPTY = TxWitness(emptyList())

        fun read(reader: ByteArrayReader): TxWitness {
            val count = reader.readVarInt().toInt()
            val stack = (0 until count).map { reader.readScript() }
            return TxWitness(stack)
        }
    }
}

/**
 * Bitcoin 交易
 *
 * 支援 Legacy 和 SegWit (BIP141) 格式
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki">BIP141 SegWit</a>
 */
data class Transaction(
    /**
     * 交易版本
     * 1 = 標準交易
     * 2 = 支援 BIP68 相對鎖定時間
     */
    val version: Int = 2,

    /**
     * 交易輸入列表
     */
    val inputs: List<TxInput>,

    /**
     * 交易輸出列表
     */
    val outputs: List<TxOutput>,

    /**
     * 見證資料（每個輸入對應一個）
     */
    val witnesses: List<TxWitness> = emptyList(),

    /**
     * 鎖定時間
     * 0 = 無鎖定
     * < 500000000 = 區塊高度
     * >= 500000000 = Unix 時間戳
     */
    val lockTime: Long = 0
) {
    init {
        require(inputs.isNotEmpty()) { "Transaction must have at least one input" }
        require(outputs.isNotEmpty()) { "Transaction must have at least one output" }
        if (witnesses.isNotEmpty()) {
            require(witnesses.size == inputs.size) {
                "Witness count must match input count"
            }
        }
    }

    /**
     * 是否為 SegWit 交易
     */
    fun isSegWit(): Boolean = witnesses.isNotEmpty() && witnesses.any { !it.isEmpty() }

    /**
     * 計算 txid (不含 witness 資料)
     */
    val hash: TxHash get() = TxHash(doubleSha256(serializeWithoutWitness()))
    
    val txid: TxId get() = TxId(hash)

    /**
     * 計算 wtxid (含 witness 資料)
     */
    val wtxid: TxId get() = TxId(doubleSha256(serialize()))

    fun getTxId(): ByteArray = hash.value.toByteArray()

    fun getWTxId(): ByteArray = wtxid.value.reversed().toByteArray()

    /**
     * 序列化交易（完整格式）
     */
    fun serialize(): ByteArray {
        return if (isSegWit()) {
            serializeSegWit()
        } else {
            serializeWithoutWitness()
        }
    }

    /**
     * 序列化（不含 witness，用於計算 txid）
     */
    private fun serializeWithoutWitness(): ByteArray {
        val buffer = ByteArrayBuilder()

        // Version (4 bytes, little-endian)
        buffer.writeInt32LE(version)

        // Input count (varint)
        buffer.writeVarInt(inputs.size.toLong())

        // Inputs
        for (input in inputs) {
            buffer.writeBytes(input.previousTxHash)
            buffer.writeInt32LE(input.previousOutputIndex.toInt())
            buffer.writeVarInt(input.scriptSig.size.toLong())
            buffer.writeBytes(input.scriptSig)
            buffer.writeInt32LE(input.sequence.toInt())
        }

        // Output count (varint)
        buffer.writeVarInt(outputs.size.toLong())

        // Outputs
        for (output in outputs) {
            buffer.writeInt64LE(output.value)
            buffer.writeVarInt(output.scriptPubKey.size.toLong())
            buffer.writeBytes(output.scriptPubKey)
        }

        // Locktime (4 bytes, little-endian)
        buffer.writeInt32LE(lockTime.toInt())

        return buffer.toByteArray()
    }

    /**
     * 序列化（SegWit 格式）
     */
    private fun serializeSegWit(): ByteArray {
        val buffer = ByteArrayBuilder()

        // Version (4 bytes, little-endian)
        buffer.writeInt32LE(version)

        // SegWit marker and flag
        buffer.writeByte(0x00)
        buffer.writeByte(0x01)

        // Input count (varint)
        buffer.writeVarInt(inputs.size.toLong())

        // Inputs
        for (input in inputs) {
            buffer.writeBytes(input.previousTxHash)
            buffer.writeInt32LE(input.previousOutputIndex.toInt())
            buffer.writeVarInt(input.scriptSig.size.toLong())
            buffer.writeBytes(input.scriptSig)
            buffer.writeInt32LE(input.sequence.toInt())
        }

        // Output count (varint)
        buffer.writeVarInt(outputs.size.toLong())

        // Outputs
        for (output in outputs) {
            buffer.writeInt64LE(output.value)
            buffer.writeVarInt(output.scriptPubKey.size.toLong())
            buffer.writeBytes(output.scriptPubKey)
        }

        // Witnesses
        for (witness in witnesses) {
            buffer.writeVarInt(witness.stack.size.toLong())
            for (item in witness.stack) {
                buffer.writeVarInt(item.size.toLong())
                buffer.writeBytes(item)
            }
        }

        // Locktime (4 bytes, little-endian)
        buffer.writeInt32LE(lockTime.toInt())

        return buffer.toByteArray()
    }

    /**
     * 計算交易虛擬大小 (vBytes)
     */
    fun getVirtualSize(): Int {
        val baseSize = serializeWithoutWitness().size
        val totalSize = serialize().size
        val weight = baseSize * 3 + totalSize
        return (weight + 3) / 4
    }

    /**
     * 計算簽名哈希 (Sighash)
     *
     * @param inputIndex 輸入索引
     * @param scriptCode 腳本代碼 (對於 SegWit 是 P2PKH 腳本或完整 Redeem Script)
     * @param hashType 簽名類型 (SIGHASH_ALL, etc.)
     * @param amount 輸入金額 (SegWit 必須，Legacy 忽略)
     * @param isSegWit 是否使用 SegWit 哈希算法 (BIP143)
     */
    fun hashForSignature(
        inputIndex: Int,
        scriptCode: ByteArray,
        hashType: Int,
        amount: Long = 0,
        isSegWit: Boolean = false
    ): ByteArray {
        require(inputIndex in inputs.indices) { "Invalid input index" }

        return if (isSegWit) {
            hashForSignatureWitness(inputIndex, scriptCode, hashType, amount)
        } else {
            hashForSignatureLegacy(inputIndex, scriptCode, hashType)
        }
    }

    /**
     * Legacy Sighash algorithm
     */
    private fun hashForSignatureLegacy(inputIndex: Int, scriptCode: ByteArray, hashType: Int): ByteArray {
        val buffer = ByteArrayBuilder()
        buffer.writeInt32LE(version)
        buffer.writeVarInt(inputs.size.toLong())

        inputs.forEachIndexed { i, input ->
            buffer.writeBytes(input.previousTxHash)
            buffer.writeInt32LE(input.previousOutputIndex.toInt())
            
            if (i == inputIndex) {
                // For the signing input, use the scriptCode (subScript)
                buffer.writeVarInt(scriptCode.size.toLong())
                buffer.writeBytes(scriptCode)
            } else {
                // For other inputs, empty script
                buffer.writeVarInt(0)
            }
            buffer.writeInt32LE(input.sequence.toInt())
        }

        buffer.writeVarInt(outputs.size.toLong())
        outputs.forEach { output ->
            buffer.writeInt64LE(output.value)
            buffer.writeVarInt(output.scriptPubKey.size.toLong())
            buffer.writeBytes(output.scriptPubKey)
        }

        buffer.writeInt32LE(lockTime.toInt())
        buffer.writeInt32LE(hashType) // Append HashType (4 bytes)

        // SIGHASH_SINGLE bug compatibility
        if ((hashType and 0x1f) == SIGHASH_SINGLE && inputIndex >= outputs.size) {
            // Return 0x010000...00 (Little Endian "1") -> SHA256 returns this directly as the *signature hash*?
            // "The SignatureHash function returns the 256-bit integer 1 on error".
            // So we return 1 represented as 32 bytes.
            val one = ByteArray(32)
            one[0] = 1
            return one
        }

        return doubleSha256(buffer.toByteArray())
    }

    /**
     * BIP143 SegWit Sighash algorithm
     */
    private fun hashForSignatureWitness(inputIndex: Int, scriptCode: ByteArray, hashType: Int, amount: Long): ByteArray {
        val buffer = ByteArrayBuilder()

        // 1. Version
        buffer.writeInt32LE(version)

        // 2. HashPrevouts (Double SHA256 of all input outpoints)
        val hashPrevouts = if ((hashType and SIGHASH_ANYONECANPAY) == 0) {
            val prevouts = ByteArrayBuilder()
            inputs.forEach { input ->
                prevouts.writeBytes(input.previousTxHash)
                prevouts.writeInt32LE(input.previousOutputIndex.toInt())
            }
            doubleSha256(prevouts.toByteArray())
        } else {
            ByteArray(32)
        }
        buffer.writeBytes(hashPrevouts)

        // 3. HashSequence (Double SHA256 of all input sequences)
        val hashSequence = if ((hashType and SIGHASH_ANYONECANPAY) == 0 && 
                              (hashType and 0x1f) != SIGHASH_SINGLE && 
                              (hashType and 0x1f) != SIGHASH_NONE) {
            val sequences = ByteArrayBuilder()
            inputs.forEach { input ->
                sequences.writeInt32LE(input.sequence.toInt())
            }
            doubleSha256(sequences.toByteArray())
        } else {
            ByteArray(32)
        }
        buffer.writeBytes(hashSequence)

        // 4. Input Outpoint
        val input = inputs[inputIndex]
        buffer.writeBytes(input.previousTxHash)
        buffer.writeInt32LE(input.previousOutputIndex.toInt())

        // 5. ScriptCode
        buffer.writeVarInt(scriptCode.size.toLong())
        buffer.writeBytes(scriptCode)

        // 6. Amount (Value)
        buffer.writeInt64LE(amount)

        // 7. Sequence
        buffer.writeInt32LE(input.sequence.toInt())

        // 8. HashOutputs
        val hashOutputs = if ((hashType and 0x1f) != SIGHASH_SINGLE && (hashType and 0x1f) != SIGHASH_NONE) {
            val outs = ByteArrayBuilder()
            outputs.forEach { output ->
                outs.writeInt64LE(output.value)
                outs.writeVarInt(output.scriptPubKey.size.toLong())
                outs.writeBytes(output.scriptPubKey)
            }
            doubleSha256(outs.toByteArray())
        } else if ((hashType and 0x1f) == SIGHASH_SINGLE && inputIndex < outputs.size) {
            val out = outputs[inputIndex]
            val sOut = ByteArrayBuilder()
            sOut.writeInt64LE(out.value)
            sOut.writeVarInt(out.scriptPubKey.size.toLong())
            sOut.writeBytes(out.scriptPubKey)
            doubleSha256(sOut.toByteArray())
        } else {
             // For SIGHASH_SINGLE with inputIndex >= outputs.size in SegWit (BIP143), 
             // the hash is just 0x00...00 (32 bytes of zeros).
             // NOTE: This differs from Legacy SIGHASH which returns "1" (uint256).
             // BIP143: "If hash_type & 0x1f == SIGHASH_SINGLE, then ... if input_index >= outputs_count, then hashOutputs is the double SHA256 of the empty string (which is effectively 32 bytes of zeros here as placeholder? No, specification says just 32 bytes of zeros for the *hash*)"
             // Correction: BIP143 says "If hash_type & 0x1f == SIGHASH_SINGLE, then hashOutputs is the double SHA256 of the serialization of output[input_index]... if input_index >= outputs_count, this is not possible."
             // BIP143 actually avoids the bug by not including the hash in the preimage properly or defining it safely.
             // Wait, for BIP143, SIGHASH_SINGLE with invalid index...
             // Let's re-read BIP143. 
             // "If the input index of the current input is greater than or equal to the number of outputs, the SIGHASH_SINGLE bug applies... for legacy."
             // For SegWit, BIP143 defines the preimage explicitly.
             // If input_index >= outputs.size, we use 32 bytes of zeros for hashOutputs.
            ByteArray(32)
        }
        buffer.writeBytes(hashOutputs)

        // 9. LockTime
        buffer.writeInt32LE(lockTime.toInt())

        // 10. HashType
        buffer.writeInt32LE(hashType)

        return doubleSha256(buffer.toByteArray())
    }

    private fun doubleSha256(data: ByteArray): ByteArray {
        return sha256(sha256(data))
    }

    private fun sha256(data: ByteArray): ByteArray {
        return Crypto.sha256(data)
    }
    
    // ================================
    // Taproot Signing (BIP-341)
    // ================================
    
    /**
     * BIP-341 Taproot 簽名哈希 (Key Path)
     * 
     * @param inputIndex 輸入索引
     * @param prevOutputs 所有被花費的 UTXO (與 inputs 順序對應)
     * @param sighashType 簽名類型 (SIGHASH_DEFAULT, SIGHASH_ALL, etc.)
     * @return 32-byte sighash for Schnorr signing
     */
    fun hashForSigningTaprootKeyPath(
        inputIndex: Int,
        prevOutputs: List<TxOutput>,
        sighashType: Int = SIGHASH_DEFAULT
    ): ByteArray {
        require(inputIndex in inputs.indices) { "Invalid input index" }
        require(prevOutputs.size == inputs.size) { "prevOutputs size must match inputs size" }
        
        val buffer = ByteArrayBuilder()
        
        // Epoch 0 (BIP-341)
        buffer.writeByte(0x00)
        
        // SigHash type
        buffer.writeByte(sighashType)
        
        // Transaction data
        buffer.writeInt32LE(version)
        buffer.writeInt32LE(lockTime.toInt())
        
        val inputType = sighashType and SIGHASH_INPUT_MASK
        if (inputType != SIGHASH_ANYONECANPAY) {
            // hashPrevouts - SHA256 of all outpoints
            val prevouts = ByteArrayBuilder()
            inputs.forEach { input ->
                prevouts.writeBytes(input.previousTxHash)
                prevouts.writeInt32LE(input.previousOutputIndex.toInt())
            }
            buffer.writeBytes(sha256(prevouts.toByteArray()))
            
            // hashAmounts - SHA256 of all amounts
            val amounts = ByteArrayBuilder()
            prevOutputs.forEach { output ->
                amounts.writeInt64LE(output.value)
            }
            buffer.writeBytes(sha256(amounts.toByteArray()))
            
            // hashScriptPubkeys - SHA256 of all scriptPubKeys
            val scripts = ByteArrayBuilder()
            prevOutputs.forEach { output ->
                scripts.writeVarInt(output.scriptPubKey.size.toLong())
                scripts.writeBytes(output.scriptPubKey)
            }
            buffer.writeBytes(sha256(scripts.toByteArray()))
            
            // hashSequences - SHA256 of all sequences
            val sequences = ByteArrayBuilder()
            inputs.forEach { input ->
                sequences.writeInt32LE(input.sequence.toInt())
            }
            buffer.writeBytes(sha256(sequences.toByteArray()))
        }
        
        val outputType = if (sighashType == SIGHASH_DEFAULT) SIGHASH_ALL else sighashType and SIGHASH_OUTPUT_MASK
        if (outputType == SIGHASH_ALL) {
            // hashOutputs - SHA256 of all outputs
            val outs = ByteArrayBuilder()
            outputs.forEach { output ->
                outs.writeInt64LE(output.value)
                outs.writeVarInt(output.scriptPubKey.size.toLong())
                outs.writeBytes(output.scriptPubKey)
            }
            buffer.writeBytes(sha256(outs.toByteArray()))
        }
        
        // spend_type (0 for key path without annex)
        buffer.writeByte(0x00)
        
        // Input-specific data
        if (inputType == SIGHASH_ANYONECANPAY) {
            val input = inputs[inputIndex]
            val prevOutput = prevOutputs[inputIndex]
            buffer.writeBytes(input.previousTxHash)
            buffer.writeInt32LE(input.previousOutputIndex.toInt())
            buffer.writeInt64LE(prevOutput.value)
            buffer.writeVarInt(prevOutput.scriptPubKey.size.toLong())
            buffer.writeBytes(prevOutput.scriptPubKey)
            buffer.writeInt32LE(input.sequence.toInt())
        } else {
            buffer.writeInt32LE(inputIndex)
        }
        
        if (outputType == SIGHASH_SINGLE) {
            if (inputIndex < outputs.size) {
                val out = outputs[inputIndex]
                val singleOut = ByteArrayBuilder()
                singleOut.writeInt64LE(out.value)
                singleOut.writeVarInt(out.scriptPubKey.size.toLong())
                singleOut.writeBytes(out.scriptPubKey)
                buffer.writeBytes(sha256(singleOut.toByteArray()))
            }
        }
        
        // Tagged hash: TapSighash
        return Crypto.taggedHash(buffer.toByteArray(), "TapSighash").toByteArray()
    }
    
    companion object {
        const val SIGHASH_ALL = 0x01
        const val SIGHASH_NONE = 0x02
        const val SIGHASH_SINGLE = 0x03
        const val SIGHASH_ANYONECANPAY = 0x80
        
        // BIP-341 Taproot specific
        const val SIGHASH_DEFAULT = 0x00
        const val SIGHASH_INPUT_MASK = 0x80
        const val SIGHASH_OUTPUT_MASK = 0x03

        /**
         * 反序列化交易 (支援 Hex 字符串)
         */
        fun read(hex: String): Transaction {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return read(bytes)
        }

        /**
         * 反序列化交易 (支援 Byte Array)
         */
        fun read(data: ByteArray): Transaction {
            val reader = ByteArrayReader(data)
            return read(reader)
        }

        /**
         * 反序列化交易 (核心邏輯)
         */
        fun read(reader: ByteArrayReader): Transaction {
            val version = reader.readInt32LE()
            
            // Try to read input count
            // Note: 0x00 could be input count 0 OR SegWit marker
            val firstByte = reader.readByte()
            var isSegWit = false
            var flags = 0
            
            val inputCount: Long
            if (firstByte == 0x00) {
                // Potential SegWit marker
                // Look ahead 1 byte to check for flag
                // Note: Standard says inputs can be empty only if SegWit marker follows.
                // But valid transaction must have inputs? 
                // BIP-141: "It is recommended that wtxid is defined... If txin is empty..."
                // If it is SegWit, next byte must be != 0.
                if (reader.hasRemaining()) {
                     val nextByte = reader.peekByte()
                     if (nextByte != 0x00) {
                         // It is SegWit
                         isSegWit = true
                         flags = reader.readByte() // consume flag
                         inputCount = reader.readVarInt()
                     } else {
                         // Not SegWit, just 0 inputs?
                         inputCount = 0
                     }
                } else {
                    inputCount = 0
                }
            } else {
                inputCount = reader.readVarInt(firstByte)
            }
            
            // Read Inputs
            val inputs = (0 until inputCount).map { TxInput.read(reader) }
            
            // Read Output Count
            val outputCount = reader.readVarInt()
            
            // Read Outputs
            val outputs = (0 until outputCount).map { TxOutput.read(reader) }
            
            // Read Witnesses (if SegWit)
            val witnesses: List<TxWitness> = if (isSegWit) {
                (0 until inputCount).map { TxWitness.read(reader) }
            } else {
                List(inputs.size) { TxWitness() }
            }
            
            // Read LockTime
            val lockTime = reader.readInt32LE().toLong() and 0xFFFFFFFFL
            
            val tx = Transaction(
                version = version,
                inputs = inputs,
                outputs = outputs,
                witnesses = witnesses, // Transaction constructor must accept witnesses
                lockTime = lockTime
            )
            return tx
        }
    }
}
