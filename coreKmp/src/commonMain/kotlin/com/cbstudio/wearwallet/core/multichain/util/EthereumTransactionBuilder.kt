package com.cbstudio.wearwallet.core.multichain.util

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Ethereum 交易建構器
 * 支援 Legacy 和 EIP-1559 交易類型
 */
class EthereumTransactionBuilder(
    private val chainType: MultiChainType
) {

    /**
     * 建構 Legacy 交易（Type 0）
     */
    fun buildLegacyTransaction(
        nonce: Long,
        gasPrice: String,
        gasLimit: String,
        to: String,
        value: String,
        data: String = ""
    ): ByteArray {
        val chainId = getChainId(chainType)

        // Legacy 交易結構: [nonce, gasPrice, gasLimit, to, value, data, chainId, 0, 0]
        val rlpList = listOf(
            nonce,
            hexToBigInt(gasPrice),
            hexToBigInt(gasLimit),
            to,
            hexToBigInt(value),
            data,
            chainId,
            0,
            0
        )

        return RLPEncoder.encode(rlpList)
    }

    /**
     * 建構 EIP-1559 交易（Type 2）
     */
    fun buildEIP1559Transaction(
        nonce: Long,
        maxPriorityFeePerGas: String,
        maxFeePerGas: String,
        gasLimit: String,
        to: String,
        value: String,
        data: String = ""
    ): ByteArray {
        val chainId = getChainId(chainType)

        // EIP-1559 交易結構: [chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList]
        val rlpList = listOf(
            chainId,
            nonce,
            hexToBigInt(maxPriorityFeePerGas),
            hexToBigInt(maxFeePerGas),
            hexToBigInt(gasLimit),
            to,
            hexToBigInt(value),
            data,
            emptyList<Any>() // accessList（空列表）
        )

        val rlpEncoded = RLPEncoder.encode(rlpList)

        // Type 2 交易需要在前面加上 0x02 前綴
        return byteArrayOf(0x02) + rlpEncoded
    }

    /**
     * 建構 ERC20 轉帳數據
     */
    fun buildERC20TransferData(
        toAddress: String,
        amount: String,
        decimals: Int
    ): String {
        // ERC20 transfer 方法簽名: transfer(address,uint256)
        val methodId = "a9059cbb"

        // 移除地址前綴並補齊到 64 字元（32 bytes）
        val paddedAddress = toAddress.removePrefix("0x").padStart(64, '0')

        // 將金額轉換為 Wei 並補齊到 64 字元
        val amountInSmallestUnit = convertToSmallestUnit(amount, decimals)
        val paddedAmount = amountInSmallestUnit.toString(16).padStart(64, '0')

        return "0x$methodId$paddedAddress$paddedAmount"
    }

    /**
     * 獲取鏈 ID
     */
    private fun getChainId(chainType: MultiChainType): Long {
        return when (chainType) {
            MultiChainType.ETHEREUM -> 1L       // Mainnet
            MultiChainType.BSC -> 56L
            MultiChainType.POLYGON -> 137L
            MultiChainType.AVALANCHE -> 43114L
            MultiChainType.ARBITRUM -> 42161L
            MultiChainType.OPTIMISM -> 10L
            MultiChainType.FANTOM -> 250L
            MultiChainType.CRONOS -> 25L
            MultiChainType.BASE -> 8453L
            MultiChainType.CELO -> 42220L
            MultiChainType.MOONBEAM -> 1284L
            else -> 1L
        }
    }

    /**
     * 十六進制字串轉 BigInteger
     */
    private fun hexToBigInt(hex: String): BigInteger {
        val cleanHex = hex.removePrefix("0x")
        if (cleanHex.isEmpty() || cleanHex == "0") {
            return BigInteger.ZERO
        }
        return BigInteger.parseString(cleanHex, 16)
    }

    /**
     * 將金額轉換為最小單位（如 Wei）
     */
    private fun convertToSmallestUnit(amount: String, decimals: Int): BigInteger {
        val amountValue = amount.toDoubleOrNull() ?: 0.0
        val multiplier = BigInteger.TEN.pow(decimals)
        val amountInSmallestUnit = (amountValue * multiplier.doubleValue(true)).toLong()
        return BigInteger.fromLong(amountInSmallestUnit)
    }
}
