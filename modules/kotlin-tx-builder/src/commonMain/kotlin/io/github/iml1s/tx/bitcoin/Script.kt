package io.github.iml1s.tx.bitcoin

/**
 * Bitcoin Script 生成與解析工具
 * 
 * 精簡版實作，只包含腳本生成功能 (不含執行器)
 * 參考: BIP-141 (SegWit), BIP-341 (Taproot)
 */
object Script {
    
    // ========================
    // Script Type Detection
    // ========================
    
    /**
     * 判斷是否為 P2PKH 腳本
     * OP_DUP OP_HASH160 <20 bytes> OP_EQUALVERIFY OP_CHECKSIG
     */
    fun isP2PKH(script: ByteArray): Boolean {
        return script.size == 25 &&
               script[0].toInt() and 0xFF == OpCodes.OP_DUP &&
               script[1].toInt() and 0xFF == OpCodes.OP_HASH160 &&
               script[2].toInt() and 0xFF == 0x14 && // 20 bytes
               script[23].toInt() and 0xFF == OpCodes.OP_EQUALVERIFY &&
               script[24].toInt() and 0xFF == OpCodes.OP_CHECKSIG
    }
    
    /**
     * 判斷是否為 P2SH 腳本
     * OP_HASH160 <20 bytes> OP_EQUAL
     */
    fun isP2SH(script: ByteArray): Boolean {
        return script.size == 23 &&
               script[0].toInt() and 0xFF == OpCodes.OP_HASH160 &&
               script[1].toInt() and 0xFF == 0x14 && // 20 bytes
               script[22].toInt() and 0xFF == OpCodes.OP_EQUAL
    }
    
    /**
     * 判斷是否為 P2WPKH 腳本 (Native SegWit v0)
     * OP_0 <20 bytes>
     */
    fun isP2WPKH(script: ByteArray): Boolean {
        return script.size == 22 &&
               script[0].toInt() and 0xFF == OpCodes.OP_0 &&
               script[1].toInt() and 0xFF == 0x14 // 20 bytes
    }
    
    /**
     * 判斷是否為 P2WSH 腳本 (Native SegWit v0)
     * OP_0 <32 bytes>
     */
    fun isP2WSH(script: ByteArray): Boolean {
        return script.size == 34 &&
               script[0].toInt() and 0xFF == OpCodes.OP_0 &&
               script[1].toInt() and 0xFF == 0x20 // 32 bytes
    }
    
    /**
     * 判斷是否為 P2TR 腳本 (Taproot)
     * OP_1 <32 bytes>
     */
    fun isP2TR(script: ByteArray): Boolean {
        return script.size == 34 &&
               script[0].toInt() and 0xFF == OpCodes.OP_1 &&
               script[1].toInt() and 0xFF == 0x20 // 32 bytes
    }
    
    // ========================
    // Script Generation
    // ========================
    
    /**
     * 生成 P2PKH 腳本 (Pay-to-Public-Key-Hash)
     * @param pubKeyHash 20-byte public key hash (HASH160)
     */
    fun pay2pkh(pubKeyHash: ByteArray): ByteArray {
        require(pubKeyHash.size == 20) { "pubKeyHash must be 20 bytes" }
        return byteArrayOf(
            OpCodes.OP_DUP.toByte(),
            OpCodes.OP_HASH160.toByte(),
            0x14.toByte() // push 20 bytes
        ) + pubKeyHash + byteArrayOf(
            OpCodes.OP_EQUALVERIFY.toByte(),
            OpCodes.OP_CHECKSIG.toByte()
        )
    }
    
    /**
     * 生成 P2SH 腳本 (Pay-to-Script-Hash)
     * @param scriptHash 20-byte script hash (HASH160)
     */
    fun pay2sh(scriptHash: ByteArray): ByteArray {
        require(scriptHash.size == 20) { "scriptHash must be 20 bytes" }
        return byteArrayOf(
            OpCodes.OP_HASH160.toByte(),
            0x14.toByte() // push 20 bytes
        ) + scriptHash + byteArrayOf(
            OpCodes.OP_EQUAL.toByte()
        )
    }
    
    /**
     * 生成 P2WPKH 腳本 (Pay-to-Witness-Public-Key-Hash)
     * @param pubKeyHash 20-byte public key hash (HASH160)
     */
    fun pay2wpkh(pubKeyHash: ByteArray): ByteArray {
        require(pubKeyHash.size == 20) { "pubKeyHash must be 20 bytes" }
        return byteArrayOf(
            OpCodes.OP_0.toByte(),
            0x14.toByte() // push 20 bytes
        ) + pubKeyHash
    }
    
    /**
     * 生成 P2WSH 腳本 (Pay-to-Witness-Script-Hash)
     * @param scriptHash 32-byte script hash (SHA256)
     */
    fun pay2wsh(scriptHash: ByteArray): ByteArray {
        require(scriptHash.size == 32) { "scriptHash must be 32 bytes" }
        return byteArrayOf(
            OpCodes.OP_0.toByte(),
            0x20.toByte() // push 32 bytes
        ) + scriptHash
    }
    
    /**
     * 生成 P2TR 腳本 (Pay-to-Taproot)
     * @param outputKey 32-byte x-only public key (tweaked)
     */
    fun pay2tr(outputKey: ByteArray): ByteArray {
        require(outputKey.size == 32) { "outputKey must be 32 bytes (x-only)" }
        return byteArrayOf(
            OpCodes.OP_1.toByte(),
            0x20.toByte() // push 32 bytes
        ) + outputKey
    }
    
    // ========================
    // Script Utilities
    // ========================
    
    /**
     * 從公鑰生成 P2WPKH ScriptCode (用於 BIP143 SegWit 簽名)
     * 這是一個 P2PKH 腳本，使用公鑰的 HASH160
     */
    fun p2wpkhScriptCode(pubKeyHash: ByteArray): ByteArray {
        require(pubKeyHash.size == 20) { "pubKeyHash must be 20 bytes" }
        return pay2pkh(pubKeyHash)
    }
    
    /**
     * 獲取 Witness 版本號
     * @return null 如果不是 native witness script
     */
    fun getWitnessVersion(script: ByteArray): Int? {
        if (script.size < 4 || script.size > 42) return null
        
        val version = script[0].toInt() and 0xFF
        val pushLen = script[1].toInt() and 0xFF
        
        // OP_0 = 0x00, OP_1..OP_16 = 0x51..0x60
        val witnessVersion = when (version) {
            OpCodes.OP_0 -> 0
            in OpCodes.OP_1..OpCodes.OP_16 -> version - OpCodes.OP_1 + 1
            else -> return null
        }
        
        // Witness program must be 2-40 bytes
        if (pushLen !in 2..40) return null
        if (script.size != pushLen + 2) return null
        
        return witnessVersion
    }
    
    /**
     * 序列化數據推送操作
     */
    fun pushData(data: ByteArray): ByteArray {
        return when {
            data.isEmpty() -> byteArrayOf(OpCodes.OP_0.toByte())
            data.size == 1 && data[0].toInt() in 1..16 -> {
                byteArrayOf((OpCodes.OP_1 + data[0].toInt() - 1).toByte())
            }
            data.size < 0x4c -> byteArrayOf(data.size.toByte()) + data
            data.size <= 0xFF -> byteArrayOf(
                OpCodes.OP_PUSHDATA1.toByte(),
                data.size.toByte()
            ) + data
            data.size <= 0xFFFF -> byteArrayOf(
                OpCodes.OP_PUSHDATA2.toByte(),
                (data.size and 0xFF).toByte(),
                ((data.size shr 8) and 0xFF).toByte()
            ) + data
            else -> throw IllegalArgumentException("Data too large for OP_PUSHDATA")
        }
    }
}
