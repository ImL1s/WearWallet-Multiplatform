package com.cbstudio.wearwallet.core.domain.protocol

import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneResult
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneError
import kotlinx.serialization.json.Json
import platform.Foundation.NSUUID

/**
 * watchOS 平台的 UR 協議簡化實現
 * 
 * 由於 watchOS 的特殊限制（無相機、有限的計算資源）
 * 這個實現主要用於橋接，實際的 UR 編解碼在 iPhone 端完成
 * 
 * 架構說明：
 * - watchOS 端：準備數據，發送到 iPhone
 * - iPhone 端：實際的 UR 編解碼、QR Code 生成/掃描
 */
actual class URProtocol {
    
    /**
     * 編碼數據為 UR 格式
     * watchOS 上不實際編碼，而是準備數據發送到 iPhone
     */
    actual fun encodeUR(data: ByteArray, type: String): KeystoneResult<URData> {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            try {
                // Encode as single part
                val parts = delegate.encodeUR(data, type, Int.MAX_VALUE)
                val urData = URData(type = type, data = data, cbor = null)
                return KeystoneResult.Success(urData)
            } catch (e: Exception) {
               println("WatchOS Delegate encodeUR failed: ${e.message}")
            }
        }
        
        return try {
            // 簡化實現：將數據包裝成 URData
            val urData = URData(
                type = type,
                data = data,
                cbor = null // watchOS 不處理 CBOR
            )
            KeystoneResult.Success(urData)
        } catch (e: Exception) {
            KeystoneResult.Error(
                KeystoneError.EncodingError("Failed to prepare UR data: ${e.message}")
            )
        }
    }
    
    /**
     * 解碼 UR 格式數據
     * watchOS 接收已解碼的數據
     */
    actual fun decodeUR(urString: String): KeystoneResult<URData> {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            try {
                val decodedData = delegate.decodeUR(urString)
                val type = if (urString.startsWith("ur:")) urString.split("/")[1] else "unknown"
                return KeystoneResult.Success(URData(type = type, data = decodedData, cbor = null))
            } catch (e: Exception) {
                 println("WatchOS Delegate decodeUR failed: ${e.message}")
            }
        }
    
        return try {
            // 簡化實現：假設收到的是已處理的數據
            if (!isValidUR(urString)) {
                return KeystoneResult.Error(
                    KeystoneError.DecodingError("Invalid UR format")
                )
            }
            
            // 提取類型和數據（簡化邏輯）
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
    
    /**
     * 驗證 UR 格式是否有效
     */
    actual fun isValidUR(urString: String): Boolean {
        // 簡化驗證：只檢查格式
        return urString.startsWith("ur:") && urString.contains("/")
    }
    
    /**
     * 生成多部分 UR（用於大數據）
     * watchOS 上準備分片請求，實際生成在 iPhone 端
     */
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
                println("WatchOS Delegate generateMultipartUR failed: ${e.message}")
            }
        }
    
        // 簡化實現：生成片段標識符
        val totalParts = (data.size + maxFragmentLen - 1) / maxFragmentLen
        val parts = mutableListOf<String>()
        
        for (i in 0 until totalParts) {
            val start = i * maxFragmentLen
            val end = minOf(start + maxFragmentLen, data.size)
            val fragment = data.sliceArray(start until end)
            
            // 簡化的 UR 格式
            val part = "ur:$type/${i + 1}-$totalParts/${
                fragment.toHexString()
            }"
            parts.add(part)
        }
        
        return parts
    }
    
    /**
     * 合併多部分 UR
     */
    actual fun combineMultipartUR(parts: List<String>): KeystoneResult<URData> {
        val delegate = com.cbstudio.wearwallet.core.security.NativeCrypto.delegateOrNull
        if (delegate != null) {
            try {
                val combinedData = delegate.combineUR(parts)
                val type = if (parts.first().startsWith("ur:")) parts.first().split("/")[1] else "unknown"
                return KeystoneResult.Success(URData(type = type, data = combinedData, cbor = null))
            } catch (e: Exception) {
                println("WatchOS Delegate combineMultipartUR failed: ${e.message}")
            }
        }
    
        return try {
            if (parts.isEmpty()) {
                return KeystoneResult.Error(
                    KeystoneError.InvalidInput("No parts to combine")
                )
            }
            
            // 簡化實現：提取並合併數據
            val combinedData = mutableListOf<Byte>()
            var urType = ""
            
            for (part in parts.sorted()) {
                if (!isValidUR(part)) {
                    return KeystoneResult.Error(
                        KeystoneError.InvalidInput("Invalid UR part: $part")
                    )
                }
                
                // 提取類型和數據（簡化邏輯）
                val components = part.removePrefix("ur:").split("/")
                if (components.size >= 2) {
                    if (urType.isEmpty()) {
                        urType = components[0]
                    }
                    
                    // 提取數據部分（最後一個組件）
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

// 格式化擴展
private fun String.format(vararg args: Any?): String {
    var result = this
    args.forEachIndexed { index, arg ->
        result = result.replace("%${index + 1}", arg.toString())
    }
    return result
}