package io.github.iml1s.tx.ethereum

import io.github.iml1s.crypto.RLP

/**
 * Ethereum 交易類型
 */
enum class TransactionType(val typeByte: Byte?) {
    LEGACY(null),
    EIP2930(0x01),
    EIP1559(0x02)
}

/**
 * Ethereum 交易介面
 */
sealed interface EthereumTransaction {
    val nonce: Long
    val gasLimit: Long
    val to: String? // null for contract creation
    val value: ByteArray // BigInteger encoded bytes
    val data: ByteArray
    val chainId: Long?
    
    // 簽名資料
    val v: Long?
    val r: ByteArray?
    val s: ByteArray?

    fun getType(): TransactionType
    
    /**
     * 編碼為 RLP 格式（用於簽名或傳輸）
     * @param forSigning 是否用於簽名（不包含 v, r, s，或包含 chainId 用於 EIP-155）
     */
    fun encode(forSigning: Boolean = false): ByteArray
}

/**
 * Legacy 交易 (包含了 EIP-155)
 */
data class LegacyTransaction(
    override val nonce: Long,
    val gasPrice: ByteArray, // Wei
    override val gasLimit: Long,
    override val to: String?,
    override val value: ByteArray,
    override val data: ByteArray,
    override val chainId: Long? = null,
    override val v: Long? = null,
    override val r: ByteArray? = null,
    override val s: ByteArray? = null
) : EthereumTransaction {
    
    override fun getType(): TransactionType = TransactionType.LEGACY

    override fun encode(forSigning: Boolean): ByteArray {
        val list = mutableListOf<Any>()
        list.add(nonce)
        list.add(gasPrice)
        list.add(gasLimit)
        list.add(to ?: byteArrayOf()) // null address encoded as empty byte array
        list.add(value)
        list.add(data)

        if (forSigning) {
            // EIP-155 Replay Attack Protection
            if (chainId != null) {
                list.add(chainId)
                list.add(0)
                list.add(0)
            }
        } else {
            // Signed transaction
            if (v != null && r != null && s != null) {
                list.add(v)
                list.add(r)
                list.add(s)
            }
        }

        return RLP.encodeList(list)
    }
}

/**
 * EIP-1559 交易
 */
data class Eip1559Transaction(
    override val chainId: Long?, // EIP-1559 要求包含 ChainID
    override val nonce: Long,
    val maxPriorityFeePerGas: ByteArray,
    val maxFeePerGas: ByteArray,
    override val gasLimit: Long,
    override val to: String?,
    override val value: ByteArray,
    override val data: ByteArray,
    val accessList: List<Any> = emptyList(), // TODO: Define AccessList structure
    override val v: Long? = null,
    override val r: ByteArray? = null,
    override val s: ByteArray? = null
) : EthereumTransaction {

    override fun getType(): TransactionType = TransactionType.EIP1559

    override fun encode(forSigning: Boolean): ByteArray {
        val list = mutableListOf<Any>()
        list.add(chainId ?: 1L)
        list.add(nonce)
        list.add(maxPriorityFeePerGas)
        list.add(maxFeePerGas)
        list.add(gasLimit)
        list.add(to ?: byteArrayOf())
        list.add(value)
        list.add(data)
        list.add(accessList) // Encodable as list of lists

        if (!forSigning && v != null && r != null && s != null) {
            // EIP-1559 簽名是 yParity (0 or 1), r, s
            // v 在這裡是 yParity
            list.add(v)
            list.add(r)
            list.add(s)
        }

        val rlpEncoded = RLP.encodeList(list)
        
        // EIP-2718 Envelope: Type (0x02) || RLP(...)
        return byteArrayOf(TransactionType.EIP1559.typeByte!!) + rlpEncoded
    }
}
