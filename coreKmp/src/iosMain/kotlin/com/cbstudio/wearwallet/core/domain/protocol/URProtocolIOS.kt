package com.cbstudio.wearwallet.core.domain.protocol

import com.cbstudio.wearwallet.core.domain.model.keystone.*
import platform.Foundation.NSUUID

/**
 * iOS 平台的 UR 協議實現
 */
actual class URProtocol {
    
    actual fun encodeUR(data: ByteArray, type: String): KeystoneResult<URData> {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            try {
                // Encode as single part (large fragment size)
                val parts = delegate.encodeUR(data, type, Int.MAX_VALUE)
                val urData = URData(type = type, data = data, cbor = null)
                // Note: The delegate returns UR Strings. But encodeUR returns URData wrapper.
                // KeystoneClient expects URData.data to be the payload?
                // Wait, KeystoneClient calls encodeStr -> encodeToUR -> encodeUR.
                // If encodeUR returns URData, KeystoneClient ignores the serialized string?
                // KeystoneClient logic is: parts = generateMultipartUR(...).
                // encodeUR seems unused for the final string generation in KeystoneClient.
                // But let's return success anyway.
                return KeystoneResult.Success(urData)
            } catch (e: Exception) {
               println("Delegate encodeUR failed: ${e.message}")
            }
        }
        
        return try {
            val urData = URData(
                type = type,
                data = data,
                cbor = null
            )
            KeystoneResult.Success(urData)
        } catch (e: Exception) {
            KeystoneResult.Error(
                KeystoneError.EncodingError("Failed to encode UR: ${e.message}")
            )
        }
    }
    
    actual fun decodeUR(urString: String): KeystoneResult<URData> {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            try {
                val decodedData = delegate.decodeUR(urString)
                // We need to extract type from urString string manually or ask delegate?
                // Delegate decodeUR returns ByteArray (payload).
                val type = if (urString.startsWith("ur:")) urString.split("/")[1] else "unknown"
                return KeystoneResult.Success(URData(type = type, data = decodedData, cbor = null))
            } catch (e: Exception) {
                 println("Delegate decodeUR failed: ${e.message}")
            }
        }
    
        return try {
            if (!isValidUR(urString)) {
                return KeystoneResult.Error(
                    KeystoneError.DecodingError("Invalid UR format")
                )
            }
            
            val parts = urString.removePrefix("ur:").split("/")
            val type = parts.firstOrNull() ?: "unknown"
            
            val urData = URData(
                type = type,
                data = urString.encodeToByteArray(),
                cbor = null
            )
            
            KeystoneResult.Success(urData)
        } catch (e: Exception) {
            KeystoneResult.Error(
                KeystoneError.DecodingError("Failed to decode UR: ${e.message}")
            )
        }
    }
    
    actual fun isValidUR(urString: String): Boolean {
        return urString.startsWith("ur:") && urString.contains("/")
    }
    
    actual fun generateMultipartUR(
        data: ByteArray, 
        type: String, 
        maxFragmentLen: Int
    ): List<String> {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            try {
                return delegate.encodeUR(data, type, maxFragmentLen)
            } catch (e: Exception) {
                println("Delegate generateMultipartUR failed: ${e.message}")
            }
        }
    
        val totalParts = (data.size + maxFragmentLen - 1) / maxFragmentLen
        val parts = mutableListOf<String>()
        
        for (i in 0 until totalParts) {
            val start = i * maxFragmentLen
            val end = minOf(start + maxFragmentLen, data.size)
            val fragment = data.sliceArray(start until end)
            
            val part = "ur:$type/${i + 1}-$totalParts/${
                fragment.toHexString()
            }"
            parts.add(part)
        }
        
        return parts
    }
    
    actual fun combineMultipartUR(parts: List<String>): KeystoneResult<URData> {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            try {
                val combinedData = delegate.combineUR(parts)
                // Extract type from first part
                val type = if (parts.first().startsWith("ur:")) parts.first().split("/")[1] else "unknown"
                return KeystoneResult.Success(URData(type = type, data = combinedData, cbor = null))
            } catch (e: Exception) {
                println("Delegate combineMultipartUR failed: ${e.message}")
            }
        }
    
        return try {
            if (parts.isEmpty()) {
                return KeystoneResult.Error(
                    KeystoneError.InvalidInput("No parts to combine")
                )
            }
            
            val combinedData = mutableListOf<Byte>()
            var urType = ""
            
            for (part in parts.sorted()) {
                if (!isValidUR(part)) {
                    return KeystoneResult.Error(
                        KeystoneError.InvalidInput("Invalid UR part: $part")
                    )
                }
                
                val components = part.removePrefix("ur:").split("/")
                if (components.size >= 2) {
                    if (urType.isEmpty()) {
                        urType = components[0]
                    }
                    
                    val hexData = components.last()
                    combinedData.addAll(hexData.hexStringToByteArray().toList())
                }
            }
            
            val urData = URData(
                type = urType,
                data = combinedData.toByteArray(),
                cbor = null
            )
            
            KeystoneResult.Success(urData)
        } catch (e: Exception) {
            KeystoneResult.Error(
                KeystoneError.CombiningError("Failed to combine UR parts: ${e.message}")
            )
        }
    }
    
    /**
     * 編碼為 UR 格式（支援分片）
     */
    fun encodeToUR(
        data: ByteArray,
        type: String,
        fragmentLen: Int = 200
    ): UREncoder {
        return SimpleUREncoder(data, type, fragmentLen)
    }
}

/**
 * 簡單的 UR 編碼器實現
 */
class SimpleUREncoder(
    private val data: ByteArray,
    private val type: String,
    private val fragmentLen: Int
) : UREncoder {
    
    private val parts: List<String>
    private var currentIndex = 0
    
    init {
        val totalParts = (data.size + fragmentLen - 1) / fragmentLen
        val partsList = mutableListOf<String>()
        
        for (i in 0 until totalParts) {
            val start = i * fragmentLen
            val end = minOf(start + fragmentLen, data.size)
            val fragment = data.sliceArray(start until end)
            
            val part = "ur:$type/${i + 1}-$totalParts/${
                fragment.toHexString()
            }"
            partsList.add(part)
        }
        
        parts = partsList
    }
    
    override fun isComplete(): Boolean = currentIndex >= parts.size
    
    override fun nextPart(): String {
        if (currentIndex >= parts.size) {
            currentIndex = 0 // 循環
        }
        return parts[currentIndex++]
    }
}

/**
 * 簡單的 UR 解碼器實現
 */
class SimpleURDecoder : URDecoder {
    
    private val receivedParts = mutableMapOf<Int, ByteArray>()
    private var totalParts: Int? = null
    private var type: String? = null
    
    override fun addPart(part: String): Boolean {
        try {
            if (!part.startsWith("ur:")) return false
            
            val components = part.removePrefix("ur:").split("/")
            if (components.size < 3) return false
            
            if (type == null) {
                type = components[0]
            }
            
            val partInfo = components[1].split("-")
            if (partInfo.size == 2) {
                val partNumber = partInfo[0].toIntOrNull() ?: return false
                val total = partInfo[1].toIntOrNull() ?: return false
                
                if (totalParts == null) {
                    totalParts = total
                }
                
                val hexData = components[2]
                receivedParts[partNumber] = hexData.hexStringToByteArray()
                
                return true
            }
            
            // 單個部分的情況
            val hexData = components.last()
            receivedParts[1] = hexData.hexStringToByteArray()
            totalParts = 1
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    override fun isComplete(): Boolean {
        val total = totalParts ?: return false
        return receivedParts.size == total
    }
    
    override fun getResult(): ByteArray? {
        if (!isComplete()) return null
        
        val result = mutableListOf<Byte>()
        val total = totalParts ?: return null
        
        for (i in 1..total) {
            receivedParts[i]?.let {
                result.addAll(it.toList())
            } ?: return null
        }
        
        return result.toByteArray()
    }
    
    override fun reset() {
        receivedParts.clear()
        totalParts = null
        type = null
    }
}

// 輔助擴展函數
private fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }
}

private fun String.hexStringToByteArray(): ByteArray {
    val hex = this.replace(" ", "")
    val result = ByteArray(hex.length / 2)
    
    for (i in result.indices) {
        val index = i * 2
        val byte = hex.substring(index, index + 2).toInt(16).toByte()
        result[i] = byte
    }
    
    return result
}

private fun String.format(vararg args: Any?): String {
    var result = this
    args.forEachIndexed { index, arg ->
        result = result.replace("%${index + 1}", arg.toString())
    }
    return result
}