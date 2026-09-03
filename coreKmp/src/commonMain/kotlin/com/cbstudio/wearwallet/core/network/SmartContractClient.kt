package com.cbstudio.wearwallet.core.network

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * 智能合約客戶端
 * 參考 sharedKmp 的 BlockchainApiClient 實現
 * 提供與智能合約交互的功能
 */
class SmartContractClient(
    private val httpClient: HttpClient
) {
    private val rpcClient = EthereumRpcClient(httpClient)
    
    /**
     * ERC20 代幣標準方法簽名
     */
    object ERC20 {
        const val BALANCE_OF = "0x70a08231"      // balanceOf(address)
        const val TRANSFER = "0xa9059cbb"        // transfer(address,uint256)
        const val APPROVE = "0x095ea7b3"         // approve(address,uint256)
        const val ALLOWANCE = "0xdd62ed3e"       // allowance(address,address)
        const val TOTAL_SUPPLY = "0x18160ddd"    // totalSupply()
        const val DECIMALS = "0x313ce567"        // decimals()
        const val SYMBOL = "0x95d89b41"          // symbol()
        const val NAME = "0x06fdde03"            // name()
    }
    
    /**
     * ERC721 NFT 標準方法簽名
     */
    object ERC721 {
        const val BALANCE_OF = "0x70a08231"      // balanceOf(address)
        const val OWNER_OF = "0x6352211e"        // ownerOf(uint256)
        const val SAFE_TRANSFER_FROM = "0x42842e0e" // safeTransferFrom(address,address,uint256)
        const val APPROVE = "0x095ea7b3"         // approve(address,uint256)
        const val GET_APPROVED = "0x081812fc"    // getApproved(uint256)
        const val IS_APPROVED_FOR_ALL = "0xe985e9c5" // isApprovedForAll(address,address)
        const val TOKEN_URI = "0xc87b56dd"       // tokenURI(uint256)
    }
    
    /**
     * 調用智能合約只讀方法
     */
    suspend fun call(
        contractAddress: String,
        data: String,
        chainType: ChainType,
        from: String? = null
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)
            val params = buildJsonObject {
                put("to", JsonPrimitive(contractAddress))
                put("data", JsonPrimitive(data))
                from?.let { put("from", JsonPrimitive(it)) }
            }
            
            val request = JsonRpcRequest(
                method = "eth_call",
                params = buildJsonArray {
                    add(params)
                    add(JsonPrimitive("latest"))
                },
                id = 1
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val result = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(result)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 ERC20 代幣餘額
     */
    suspend fun getERC20Balance(
        tokenAddress: String,
        walletAddress: String,
        chainType: ChainType
    ): Result<String> {
        val data = ERC20.BALANCE_OF + walletAddress.removePrefix("0x").padStart(64, '0')
        return call(tokenAddress, data, chainType)
    }
    
    /**
     * 獲取 ERC20 代幣資訊
     */
    suspend fun getERC20Info(
        tokenAddress: String,
        chainType: ChainType
    ): Result<TokenInfo> {
        return try {
            // 獲取代幣名稱
            val nameResult = call(tokenAddress, ERC20.NAME, chainType)
            val name = if (nameResult is Result.Success) {
                decodeString(nameResult.data)
            } else "Unknown"
            
            // 獲取代幣符號
            val symbolResult = call(tokenAddress, ERC20.SYMBOL, chainType)
            val symbol = if (symbolResult is Result.Success) {
                decodeString(symbolResult.data)
            } else "UNKNOWN"
            
            // 獲取小數位數
            val decimalsResult = call(tokenAddress, ERC20.DECIMALS, chainType)
            val decimals = if (decimalsResult is Result.Success) {
                decodeUint256(decimalsResult.data).toInt()
            } else 18
            
            // 獲取總供應量
            val totalSupplyResult = call(tokenAddress, ERC20.TOTAL_SUPPLY, chainType)
            val totalSupply = if (totalSupplyResult is Result.Success) {
                decodeUint256(totalSupplyResult.data)
            } else "0"
            
            Result.Success(
                TokenInfo(
                    address = tokenAddress,
                    name = name,
                    symbol = symbol,
                    decimals = decimals,
                    totalSupply = totalSupply
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 NFT 擁有者
     */
    suspend fun getNFTOwner(
        nftAddress: String,
        tokenId: String,
        chainType: ChainType
    ): Result<String> {
        val data = ERC721.OWNER_OF + tokenId.removePrefix("0x").padStart(64, '0')
        val result = call(nftAddress, data, chainType)
        
        return when (result) {
            is Result.Success -> {
                val owner = "0x" + result.data.removePrefix("0x").takeLast(40)
                Result.Success(owner)
            }
            is Result.Failure -> result
            is Result.Loading -> result
        }
    }
    
    /**
     * 獲取 NFT 元數據 URI
     */
    suspend fun getNFTTokenURI(
        nftAddress: String,
        tokenId: String,
        chainType: ChainType
    ): Result<String> {
        val data = ERC721.TOKEN_URI + tokenId.removePrefix("0x").padStart(64, '0')
        val result = call(nftAddress, data, chainType)
        
        return when (result) {
            is Result.Success -> {
                Result.Success(decodeString(result.data))
            }
            is Result.Failure -> result
            is Result.Loading -> result
        }
    }
    
    /**
     * 構建 ERC20 轉帳數據
     */
    fun buildERC20TransferData(to: String, amount: String): String {
        val toAddress = to.removePrefix("0x").padStart(64, '0')
        val amountHex = amount.toLongOrNull()?.toString(16)?.padStart(64, '0') ?: "0".padStart(64, '0')
        return ERC20.TRANSFER + toAddress + amountHex
    }
    
    /**
     * 構建 ERC20 授權數據
     */
    fun buildERC20ApproveData(spender: String, amount: String): String {
        val spenderAddress = spender.removePrefix("0x").padStart(64, '0')
        val amountHex = amount.toLongOrNull()?.toString(16)?.padStart(64, '0') ?: "0".padStart(64, '0')
        return ERC20.APPROVE + spenderAddress + amountHex
    }
    
    /**
     * 解碼 uint256
     */
    private fun decodeUint256(hex: String): String {
        val cleanHex = hex.removePrefix("0x")
        return cleanHex.toLongOrNull(16)?.toString() ?: "0"
    }
    
    /**
     * 解碼字符串
     */
    private fun decodeString(hex: String): String {
        return try {
            val cleanHex = hex.removePrefix("0x")
            if (cleanHex.length < 128) return ""
            
            // 跳過偏移量（32 字節）和長度（32 字節）
            val dataHex = cleanHex.substring(128)
            val bytes = dataHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            
            // 去除尾部的零字節並轉換為字符串
            val trimmed = bytes.takeWhile { it != 0.toByte() }.toByteArray()
            trimmed.decodeToString()
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * 代幣資訊
 */
data class TokenInfo(
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val totalSupply: String
)