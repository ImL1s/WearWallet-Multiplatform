package com.cbstudio.wearwallet.core.domain.protocol

import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneResult
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneError
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import com.sparrowwallet.hummingbird.URDecoder
import com.sparrowwallet.hummingbird.registry.RegistryType

/**
 * Android 平台的 UR Protocol 實現
 * 使用 Hummingbird 庫處理 BC-UR 格式
 */
actual class URProtocol {
    
    actual fun encodeUR(data: ByteArray, type: String): KeystoneResult<URData> {
        return try {
            val ur = UR(type, data)
            val encoder = UREncoder(ur, 500, 10, 0)
            val encoded = encoder.nextPart()
            
            KeystoneResult.Success(
                URData(
                    type = type,
                    data = data,
                    cbor = data  // 使用原始數據，因為我們已經有了
                )
            )
        } catch (e: Exception) {
            KeystoneResult.Error(
                KeystoneError.UnknownError("Failed to encode UR: ${e.message}", e)
            )
        }
    }
    
    actual fun decodeUR(urString: String): KeystoneResult<URData> {
        return try {
            val decoder = URDecoder()
            
            // 處理單個或多個 UR 片段
            val cleanedUR = urString.trim().uppercase()
            
            if (cleanedUR.startsWith("UR:")) {
                decoder.receivePart(cleanedUR)
                
                val result = decoder.result
                if (result != null) {
                    // 將 UR 數據轉換為 ByteArray
                    val data = result.ur.toBytes()
                    
                    KeystoneResult.Success(
                        URData(
                            type = result.type.toString(),
                            data = data,
                            cbor = data
                        )
                    )
                } else {
                    KeystoneResult.Error(
                        KeystoneError.ValidationFailed("Incomplete UR data, progress: ${decoder.estimatedPercentComplete}%")
                    )
                }
            } else {
                KeystoneResult.Error(
                    KeystoneError.InvalidQRCode("Invalid UR format")
                )
            }
        } catch (e: Exception) {
            KeystoneResult.Error(
                KeystoneError.UnknownError("Failed to decode UR: ${e.message}", e)
            )
        }
    }
    
    actual fun isValidUR(urString: String): Boolean {
        return try {
            val cleaned = urString.trim().uppercase()
            cleaned.startsWith("UR:") && cleaned.contains("/")
        } catch (e: Exception) {
            false
        }
    }
    
    actual fun generateMultipartUR(data: ByteArray, type: String, maxFragmentLen: Int): List<String> {
        return try {
            val ur = UR(type, data)
            // Use minFragmentLen=1 and seqLen determined by encoder
            val encoder = UREncoder(ur, maxFragmentLen, 1, 0)
            val parts = mutableListOf<String>()

            // Get the total number of parts needed from the encoder
            val totalParts = encoder.seqLen

            // Generate all sequential parts
            for (i in 0 until totalParts) {
                val part = encoder.nextPart().uppercase()
                parts.add(part)
            }

            // Sort by sequence number (format: ur:type/seq-total/data)
            parts.sortedWith { a, b ->
                // Extract sequence number from format like "UR:BYTES/1-5/..."
                val aMatch = Regex("/(\\d+)-(\\d+)/").find(a)
                val bMatch = Regex("/(\\d+)-(\\d+)/").find(b)

                if (aMatch != null && bMatch != null) {
                    val aIndex = aMatch.groupValues[1].toIntOrNull() ?: 0
                    val bIndex = bMatch.groupValues[1].toIntOrNull() ?: 0
                    aIndex.compareTo(bIndex)
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            println("URProtocol: Failed to generate multipart UR: ${e.message}")
            listOf()
        }
    }
    
    actual fun combineMultipartUR(parts: List<String>): KeystoneResult<URData> {
        return try {
            val decoder = URDecoder()

            // Feed all parts to the decoder
            for (part in parts) {
                val cleanedPart = part.trim().uppercase()
                if (cleanedPart.startsWith("UR:")) {
                    decoder.receivePart(cleanedPart)

                    // Check if we have a complete result after each part
                    val result = decoder.result
                    if (result != null) {
                        val data = result.ur.toBytes()
                        return KeystoneResult.Success(
                            URData(
                                type = result.type.toString().lowercase(),
                                data = data,
                                cbor = data
                            )
                        )
                    }
                }
            }

            // If we processed all parts but still no result, check progress
            val progress = decoder.estimatedPercentComplete
            KeystoneResult.Error(
                KeystoneError.ValidationFailed("Incomplete UR data after combining ${parts.size} parts. Progress: ${(progress * 100).toInt()}%")
            )
        } catch (e: Exception) {
            KeystoneResult.Error(
                KeystoneError.UnknownError("Failed to combine multipart UR: ${e.message}", e)
            )
        }
    }
}