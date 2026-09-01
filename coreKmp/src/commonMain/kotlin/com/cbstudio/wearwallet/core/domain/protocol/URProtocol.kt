package com.cbstudio.wearwallet.core.domain.protocol

import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneResult
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneError

/**
 * UR (Uniform Resource) 協議處理介面
 * 用於處理 BC-UR 格式的數據編碼和解碼
 */
expect class URProtocol() {
    
    /**
     * 編碼數據為 UR 格式
     */
    fun encodeUR(data: ByteArray, type: String): KeystoneResult<URData>
    
    /**
     * 解碼 UR 格式數據
     */
    fun decodeUR(urString: String): KeystoneResult<URData>
    
    /**
     * 驗證 UR 格式是否有效
     */
    fun isValidUR(urString: String): Boolean
    
    /**
     * 生成多部分 UR（用於大數據）
     */
    fun generateMultipartUR(data: ByteArray, type: String, maxFragmentLen: Int = 500): List<String>
    
    /**
     * 合併多部分 UR
     */
    fun combineMultipartUR(parts: List<String>): KeystoneResult<URData>
}

/**
 * UR 數據封裝
 */
data class URData(
    val type: String,
    val data: ByteArray,
    val cbor: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as URData

        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        if (cbor != null) {
            if (other.cbor == null) return false
            if (!cbor.contentEquals(other.cbor)) return false
        } else if (other.cbor != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (cbor?.contentHashCode() ?: 0)
        return result
    }
}