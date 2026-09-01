package com.cbstudio.wearwallet.core.blockchain.transaction

import com.cbstudio.wearwallet.core.blockchain.model.*
import com.cbstudio.wearwallet.core.blockchain.utxo.UTXOSelector
import kotlin.experimental.and

/**
 * Bitcoin 交易構造器
 * 負責構建未簽名的 Bitcoin 交易
 */
class BitcoinTransactionBuilder {
    
    companion object {
        const val SIGHASH_ALL = 0x01
        const val SIGHASH_NONE = 0x02
        const val SIGHASH_SINGLE = 0x03
        const val SIGHASH_ANYONECANPAY = 0x80
        
        const val OP_DUP = 0x76.toByte()
        const val OP_HASH160 = 0xA9.toByte()
        const val OP_EQUALVERIFY = 0x88.toByte()
        const val OP_CHECKSIG = 0xAC.toByte()
        const val OP_0 = 0x00.toByte()
        const val OP_EQUAL = 0x87.toByte()
    }
    
    /**
     * 構建交易
     */
    fun buildTransaction(
        params: TransactionBuilderParams
    ): BitcoinTransaction {
        val inputs = buildInputs(params.inputs)
        val outputs = buildOutputs(params)
        
        return BitcoinTransaction(
            hash = "", // 將在簽名後生成
            from = params.changeAddress,
            to = params.outputs.firstOrNull()?.address ?: "",
            value = params.outputs.sumOf { it.amount }.toString(),
            fee = calculateFee(params).toString(),
            inputs = inputs,
            outputs = outputs,
            version = 2,
            lockTime = 0
        )
    }
    
    /**
     * 構建原始交易（用於簽名）
     */
    fun buildRawTransaction(
        utxos: List<UTXO>,
        outputs: List<TransactionOutputParams>,
        changeAddress: String,
        changeAmount: Long
    ): ByteArray {
        val buffer = mutableListOf<Byte>()
        
        // Version (4 bytes, little-endian)
        buffer.addAll(intToLittleEndianBytes(2))
        
        // Marker + Flag for SegWit (optional)
        // 0x00 0x01
        
        // Input count (variable length integer)
        buffer.addAll(encodeVarInt(utxos.size.toLong()))
        
        // Inputs
        for (utxo in utxos) {
            // Previous transaction hash (32 bytes, reversed)
            buffer.addAll(hexToBytes(utxo.txid).reversed())
            
            // Previous output index (4 bytes, little-endian)
            buffer.addAll(intToLittleEndianBytes(utxo.vout))
            
            // Script length (variable length integer) - 0 for unsigned
            buffer.add(0x00)
            
            // Sequence (4 bytes, little-endian) - 0xFFFFFFFD for RBF
            buffer.addAll(intToLittleEndianBytes(0xFFFFFFFD.toInt()))
        }
        
        // Output count
        val outputCount = if (changeAmount > UTXOSelector.DUST_THRESHOLD) {
            outputs.size + 1
        } else {
            outputs.size
        }
        buffer.addAll(encodeVarInt(outputCount.toLong()))
        
        // Outputs
        for (output in outputs) {
            // Amount (8 bytes, little-endian)
            buffer.addAll(longToLittleEndianBytes(output.amount))
            
            // Script
            val script = createOutputScript(output.address)
            buffer.addAll(encodeVarInt(script.size.toLong()))
            buffer.addAll(script.toList())
        }
        
        // Change output (if needed)
        if (changeAmount > UTXOSelector.DUST_THRESHOLD) {
            buffer.addAll(longToLittleEndianBytes(changeAmount))
            val changeScript = createOutputScript(changeAddress)
            buffer.addAll(encodeVarInt(changeScript.size.toLong()))
            buffer.addAll(changeScript.toList())
        }
        
        // Witness data (for SegWit transactions)
        // Added during signing
        
        // Lock time (4 bytes, little-endian)
        buffer.addAll(intToLittleEndianBytes(0))
        
        return buffer.toByteArray()
    }
    
    /**
     * 構建交易輸入
     */
    private fun buildInputs(utxos: List<UTXO>): List<BitcoinInput> {
        return utxos.map { utxo ->
            BitcoinInput(
                previousTxHash = utxo.txid,
                previousIndex = utxo.vout,
                script = ByteArray(0), // 未簽名
                sequence = 0xFFFFFFFD.toInt() // RBF enabled
            )
        }
    }
    
    /**
     * 構建交易輸出
     */
    private fun buildOutputs(params: TransactionBuilderParams): List<BitcoinOutput> {
        val outputs = mutableListOf<BitcoinOutput>()
        
        // 目標輸出
        for (output in params.outputs) {
            outputs.add(
                BitcoinOutput(
                    value = output.amount,
                    script = createOutputScript(output.address),
                    address = output.address
                )
            )
        }
        
        // 計算找零
        val totalInput = params.inputs.sumOf { it.value }
        val totalOutput = params.outputs.sumOf { it.amount }
        val fee = calculateFee(params)
        val change = totalInput - totalOutput - fee
        
        // 添加找零輸出（如果大於 dust threshold）
        if (change > UTXOSelector.DUST_THRESHOLD) {
            outputs.add(
                BitcoinOutput(
                    value = change,
                    script = createOutputScript(params.changeAddress),
                    address = params.changeAddress
                )
            )
        }
        
        return outputs
    }
    
    /**
     * 創建輸出腳本
     */
    fun createOutputScript(address: String): ByteArray {
        return when {
            // Native SegWit (Bech32)
            address.startsWith("bc1") || address.startsWith("tb1") -> {
                createP2WPKHScript(address)
            }
            // P2SH (包括 SegWit-wrapped)
            address.startsWith("3") || address.startsWith("2") -> {
                createP2SHScript(address)
            }
            // Legacy P2PKH
            else -> {
                createP2PKHScript(address)
            }
        }
    }
    
    /**
     * 創建 P2PKH 腳本
     * OP_DUP OP_HASH160 <pubKeyHash> OP_EQUALVERIFY OP_CHECKSIG
     */
    private fun createP2PKHScript(address: String): ByteArray {
        val pubKeyHash = decodeBase58Address(address)
        val script = mutableListOf<Byte>()
        
        script.add(OP_DUP)
        script.add(OP_HASH160)
        script.add(0x14) // Push 20 bytes
        script.addAll(pubKeyHash.toList())
        script.add(OP_EQUALVERIFY)
        script.add(OP_CHECKSIG)
        
        return script.toByteArray()
    }
    
    /**
     * 創建 P2SH 腳本
     * OP_HASH160 <scriptHash> OP_EQUAL
     */
    private fun createP2SHScript(address: String): ByteArray {
        val scriptHash = decodeBase58Address(address)
        val script = mutableListOf<Byte>()
        
        script.add(OP_HASH160)
        script.add(0x14) // Push 20 bytes
        script.addAll(scriptHash.toList())
        script.add(OP_EQUAL)
        
        return script.toByteArray()
    }
    
    /**
     * 創建 P2WPKH 腳本
     * OP_0 <pubKeyHash>
     */
    private fun createP2WPKHScript(address: String): ByteArray {
        val decoded = decodeBech32Address(address)
        val script = mutableListOf<Byte>()
        
        script.add(OP_0)
        script.add(0x14) // Push 20 bytes
        script.addAll(decoded.toList())
        
        return script.toByteArray()
    }
    
    /**
     * 計算交易手續費
     */
    private fun calculateFee(params: TransactionBuilderParams): Long {
        val inputCount = params.inputs.size
        val outputCount = params.outputs.size + 1 // 包括找零
        
        // 估算交易大小
        val baseSize = 10 // 版本 + 鎖定時間
        val inputSize = inputCount * 68 // P2WPKH 輸入
        val outputSize = outputCount * 31 // P2WPKH 輸出
        
        val totalSize = baseSize + inputSize + outputSize
        return totalSize * params.feeRate
    }
    
    /**
     * 編碼變長整數
     */
    private fun encodeVarInt(value: Long): List<Byte> {
        return when {
            value < 0xFD -> listOf(value.toByte())
            value <= 0xFFFF -> {
                listOf(0xFD.toByte()) + shortToLittleEndianBytes(value.toShort())
            }
            value <= 0xFFFFFFFF -> {
                listOf(0xFE.toByte()) + intToLittleEndianBytes(value.toInt())
            }
            else -> {
                listOf(0xFF.toByte()) + longToLittleEndianBytes(value)
            }
        }
    }
    
    /**
     * 轉換為小端序
     */
    private fun intToLittleEndianBytes(value: Int): List<Byte> {
        return listOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToLittleEndianBytes(value: Short): List<Byte> {
        return listOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
    
    private fun longToLittleEndianBytes(value: Long): List<Byte> {
        return (0..7).map { i ->
            ((value shr (i * 8)) and 0xFF).toByte()
        }
    }
    
    /**
     * 十六進制轉字節
     */
    private fun hexToBytes(hex: String): List<Byte> {
        val cleanHex = hex.removePrefix("0x")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }
    }
    
    /**
     * 解碼 Base58 地址（簡化版）
     */
    private fun decodeBase58Address(address: String): ByteArray {
        // 實際實現需要完整的 Base58 解碼
        // 這裡返回 20 字節的佔位數據
        return ByteArray(20) { it.toByte() }
    }
    
    /**
     * 解碼 Bech32 地址（簡化版）
     */
    private fun decodeBech32Address(address: String): ByteArray {
        // 實際實現需要完整的 Bech32 解碼
        // 這裡返回 20 字節的佔位數據
        return ByteArray(20) { it.toByte() }
    }
}

/**
 * 交易序列化器
 */
object TransactionSerializer {
    
    /**
     * 序列化交易為十六進制
     */
    fun serialize(transaction: BitcoinTransaction): String {
        val buffer = mutableListOf<Byte>()
        
        // Version
        buffer.addAll(intToBytes(transaction.version))
        
        // Inputs
        buffer.addAll(encodeVarInt(transaction.inputs.size))
        for (input in transaction.inputs) {
            buffer.addAll(hexToBytes(input.previousTxHash).reversed())
            buffer.addAll(intToBytes(input.previousIndex))
            buffer.addAll(encodeVarInt(input.script.size))
            buffer.addAll(input.script.toList())
            buffer.addAll(intToBytes(input.sequence))
        }
        
        // Outputs
        buffer.addAll(encodeVarInt(transaction.outputs.size))
        for (output in transaction.outputs) {
            buffer.addAll(longToBytes(output.value))
            buffer.addAll(encodeVarInt(output.script.size))
            buffer.addAll(output.script.toList())
        }
        
        // Lock time
        buffer.addAll(intToBytes(transaction.lockTime.toInt()))
        
        return buffer.toByteArray().toHex()
    }
    
    private fun intToBytes(value: Int): List<Byte> {
        return listOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun longToBytes(value: Long): List<Byte> {
        return (0..7).map { i ->
            ((value shr (i * 8)) and 0xFF).toByte()
        }
    }
    
    private fun encodeVarInt(value: Int): List<Byte> {
        return when {
            value < 0xFD -> listOf(value.toByte())
            value <= 0xFFFF -> {
                listOf(0xFD.toByte()) + intToBytes(value).take(2)
            }
            else -> {
                listOf(0xFE.toByte()) + intToBytes(value)
            }
        }
    }
    
    private fun hexToBytes(hex: String): List<Byte> {
        return hex.chunked(2).map { it.toInt(16).toByte() }
    }
    
    private fun ByteArray.toHex(): String {
        return joinToString("") { byte ->
            val hex = byte.toInt() and 0xFF
            hex.toString(16).padStart(2, '0')
        }
    }
}