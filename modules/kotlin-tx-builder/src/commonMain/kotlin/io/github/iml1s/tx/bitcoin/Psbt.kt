package io.github.iml1s.tx.bitcoin

/**
 * PSBT 鍵值對
 */
data class PsbtKeyValuePair(
    val key: ByteArray,
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as PsbtKeyValuePair
        return key.contentEquals(other.key) && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

/**
 * PSBT 映射 (Global, Input, Output)
 *
 * 由一系列鍵值對組成，以 0x00 鍵結尾
 */
open class PsbtMap(
    val map: MutableMap<String, ByteArray> = mutableMapOf(),
    // 保留原始順序和未知鍵
    val unknownKeys: MutableList<PsbtKeyValuePair> = mutableListOf()
) {
    fun addUnknownKey(key: ByteArray, value: ByteArray) {
        unknownKeys.add(PsbtKeyValuePair(key, value))
    }

    /**
     * 序列化映射
     */
    fun serialize(buffer: ByteArrayBuilder) {
        // 按照 BIP174 建議，通常不需要排序，但某些實現可能會排序
        // 這裡我們優先寫入已知鍵，然後是未知鍵

        // 未知鍵（或其他非標準鍵）直接寫入
        for (pair in unknownKeys) {
            writeKeyValuePair(buffer, pair.key, pair.value)
        }

        // 寫入結束符 0x00
        buffer.writeByte(0x00)
    }

    private fun writeKeyValuePair(buffer: ByteArrayBuilder, key: ByteArray, value: ByteArray) {
        buffer.writeVarInt(key.size.toLong())
        buffer.writeBytes(key)
        buffer.writeVarInt(value.size.toLong())
        buffer.writeBytes(value)
    }
}

/**
 * PSBT 輸入 (Input Map)
 */
class PsbtInput : PsbtMap() {
    // 定義常用 Key Type
    companion object {
        const val PSBT_IN_NON_WITNESS_UTXO: Byte = 0x00
        const val PSBT_IN_WITNESS_UTXO: Byte = 0x01
        const val PSBT_IN_PARTIAL_SIG: Byte = 0x02
        const val PSBT_IN_SIGHASH_TYPE: Byte = 0x03
        const val PSBT_IN_REDEEM_SCRIPT: Byte = 0x04
        const val PSBT_IN_WITNESS_SCRIPT: Byte = 0x05
        const val PSBT_IN_BIP32_DERIVATION: Byte = 0x06
        const val PSBT_IN_FINAL_SCRIPTSIG: Byte = 0x07
        const val PSBT_IN_FINAL_SCRIPTWITNESS: Byte = 0x08
    }

    var nonWitnessUtxo: Transaction? = null
    var witnessUtxo: TxOutput? = null
    val partialSigs = mutableMapOf<String, ByteArray>() // PubKey (hex) -> Signature
    var sighashType: Int? = null
    var redeemScript: ByteArray? = null
    var witnessScript: ByteArray? = null
    val bip32Derivation = mutableMapOf<String, ByteArray>() // PubKey (hex) -> MasterFingerprint + Path
    var finalScriptSig: ByteArray? = null
    var finalScriptWitness: List<ByteArray>? = null

    // 序列化邏輯需覆寫 serialize，將強類型欄位轉換為鍵值對寫入
    // 為簡化，暫時只存儲在 unknownKeys 或專用欄位，完整實現需轉換
}

/**
 * PSBT 輸出 (Output Map)
 */
class PsbtOutput : PsbtMap() {
    companion object {
        const val PSBT_OUT_REDEEM_SCRIPT: Byte = 0x00
        const val PSBT_OUT_WITNESS_SCRIPT: Byte = 0x01
        const val PSBT_OUT_BIP32_DERIVATION: Byte = 0x02
    }

    var redeemScript: ByteArray? = null
    var witnessScript: ByteArray? = null
    val bip32Derivation = mutableMapOf<String, ByteArray>() // PubKey (hex) -> MasterFingerprint + Path
}

/**
 * Partially Signed Bitcoin Transaction (BIP174)
 */
class Psbt(
    val global: PsbtGlobal,
    val inputs: List<PsbtInput>,
    val outputs: List<PsbtOutput>
) {
    /**
     * 序列化 PSBT
     */
    fun serialize(): ByteArray {
        val buffer = ByteArrayBuilder()

        // 1. Magic Bytes (0x70736274ff) "psbt\xff"
        buffer.writeBytes(MAGIC_BYTES)

        // 2. Global Map
        global.serialize(buffer)

        // 3. Input Maps
        for (input in inputs) {
            input.serialize(buffer)
        }

        // 4. Output Maps
        for (output in outputs) {
            output.serialize(buffer)
        }

        return buffer.toByteArray()
    }

    companion object {
        val MAGIC_BYTES = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte())

        /**
         * 解析 PSBT 字節流
         */
        fun deserialize(data: ByteArray): Psbt {
            val reader = ByteArrayReader(data)

            // 1. Check Magic Bytes
            val magic = reader.readBytes(5)
            require(magic.contentEquals(MAGIC_BYTES)) { "Invalid PSBT magic bytes" }

            // 2. Read Global Map
            val globalMap = readMap(reader)
            val global = PsbtGlobal.fromMap(globalMap)

            // 3. Read Input Maps
            val inputs = mutableListOf<PsbtInput>()
            // 輸入數量由 global.unsignedTx.inputs.size 決定
            // 這裡假設 global map 必須包含 UNSIG_TX
            val inputCount = global.unsignedTx?.inputs?.size ?: 0
            repeat(inputCount) {
                val inputMap = readMap(reader)
                inputs.add(PsbtInput()) // TODO: Populate from inputMap
            }

            // 4. Read Output Maps
            val outputs = mutableListOf<PsbtOutput>()
            val outputCount = global.unsignedTx?.outputs?.size ?: 0
            repeat(outputCount) {
                val outputMap = readMap(reader)
                outputs.add(PsbtOutput()) // TODO: Populate from outputMap
            }

            return Psbt(global, inputs, outputs)
        }

        private fun readMap(reader: ByteArrayReader): List<PsbtKeyValuePair> {
            val pairs = mutableListOf<PsbtKeyValuePair>()
            while (reader.hasRemaining()) {
                val keyLen = reader.readVarInt().toInt()
                if (keyLen == 0) break // Separator 0x00 indicates end of map

                val key = reader.readBytes(keyLen)
                val valueLen = reader.readVarInt().toInt()
                val value = reader.readBytes(valueLen)

                pairs.add(PsbtKeyValuePair(key, value))
            }
            return pairs
        }
    }
}

/**
 * PSBT Global Map
 */
class PsbtGlobal : PsbtMap() {
    companion object {
        const val PSBT_GLOBAL_UNSIGNED_TX: Byte = 0x00
        const val PSBT_GLOBAL_XPUB: Byte = 0x01
        const val PSBT_GLOBAL_VERSION: Byte = 0xFB.toByte()
        const val PSBT_GLOBAL_PROPRIETARY: Byte = 0xC.toByte()

        fun fromMap(pairs: List<PsbtKeyValuePair>): PsbtGlobal {
            val global = PsbtGlobal()
            for (pair in pairs) {
                if (pair.key.size == 1 && pair.key[0] == PSBT_GLOBAL_UNSIGNED_TX) {
                    // TODO: Deserialize Unsigned Tx
                    // val tx = Transaction.deserialize(pair.value)
                    // global.unsignedTx = tx
                }
                global.addUnknownKey(pair.key, pair.value)
            }
            return global
        }
    }

    var unsignedTx: Transaction? = null
    var version: Int = 0
    val xpubs = mutableMapOf<String, String>() // XPub (hex) -> Fingerprint + Path (hex)

}
