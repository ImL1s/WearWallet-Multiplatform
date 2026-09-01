package com.cbstudio.wearwallet.core.multichain.monero.crypto

import com.cbstudio.wearwallet.core.common.Result
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import com.cbstudio.wearwallet.core.security.CryptoUtils
import com.cbstudio.wearwallet.core.security.toHexString
import kotlin.random.Random

/**
 * Monero UTXO 管理器
 * 
 * 負責：
 * 1. 掃描和識別屬於錢包的 UTXO
 * 2. 管理已花費和未花費的輸出
 * 3. 選擇誘餌輸出用於環簽名
 */
class MoneroUTXOManager(
    private val httpClient: HttpClient,
    private val daemonUrl: String,
    private val lwsUrl: String? = null
) {
    
    @Serializable
    data class MoneroUTXO(
        val txHash: String,
        val txPublicKey: String,
        val outputIndex: Int,
        val globalIndex: Long,
        val amount: String,  // 以原子單位存儲（1 XMR = 1e12 原子單位）
        val mask: String,     // RingCT mask
        val keyImage: String? = null,  // 如果已花費則有值
        val stealthAddress: String,
        val height: Long,
        val unlocked: Boolean = false
    )
    
    @Serializable
    data class DecoyOutput(
        val globalIndex: Long,
        val publicKey: String,
        val commitment: String  // Pedersen commitment for RingCT
    )
    
    /**
     * 掃描區塊鏈找出屬於錢包的 UTXO
     * Monero 使用隱形地址，需要用 view key 掃描每個輸出
     */
    suspend fun scanForUTXOs(
        viewKey: String,
        address: String,
        fromHeight: Long = 0,
        toHeight: Long? = null
    ): Result<List<MoneroUTXO>> = withContext(Dispatchers.IO) {
        try {
            if (lwsUrl != null) {
                // 使用 LWS 掃描（更快）
                scanUsingLWS(viewKey, address, fromHeight, toHeight)
            } else {
                // 使用 daemon 掃描（更去中心化）
                scanUsingDaemon(viewKey, address, fromHeight, toHeight)
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 使用 LWS 掃描 UTXO
     */
    private suspend fun scanUsingLWS(
        viewKey: String,
        address: String,
        fromHeight: Long,
        toHeight: Long?
    ): Result<List<MoneroUTXO>> {
        return try {
            val response: HttpResponse = httpClient.post("$lwsUrl/get_unspent_outs") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(mapOf(
                    "address" to JsonPrimitive(address),
                    "view_key" to JsonPrimitive(viewKey),
                    "dust_threshold" to JsonPrimitive(2000000000),  // 0.002 XMR
                    "mixin" to JsonPrimitive(10),
                    "use_dust" to JsonPrimitive(false),
                    "amount" to JsonPrimitive(0)  // 0 = all amounts
                )))
            }
            
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val outputs = json["outputs"]?.jsonArray ?: emptyList()
            
            val utxos = outputs.mapNotNull { output ->
                try {
                    val obj = output.jsonObject
                    MoneroUTXO(
                        txHash = obj["tx_hash"]?.jsonPrimitive?.content ?: "",
                        txPublicKey = obj["tx_pub_key"]?.jsonPrimitive?.content ?: "",
                        outputIndex = obj["out_index"]?.jsonPrimitive?.int ?: 0,
                        globalIndex = obj["global_index"]?.jsonPrimitive?.long ?: 0L,
                        amount = obj["amount"]?.jsonPrimitive?.content ?: "0",
                        mask = obj["mask"]?.jsonPrimitive?.content ?: "",
                        keyImage = obj["key_image"]?.jsonPrimitive?.content,
                        stealthAddress = obj["stealth_address"]?.jsonPrimitive?.content ?: "",
                        height = obj["height"]?.jsonPrimitive?.long ?: 0L,
                        unlocked = obj["unlocked"]?.jsonPrimitive?.boolean ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            Result.Success(utxos)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 使用 daemon 掃描 UTXO
     */
    private suspend fun scanUsingDaemon(
        viewKey: String,
        address: String,
        fromHeight: Long,
        toHeight: Long?
    ): Result<List<MoneroUTXO>> {
        // Daemon 掃描更複雜，需要：
        // 1. 獲取區塊
        // 2. 遍歷每個交易
        // 3. 檢查每個輸出是否屬於我們
        
        val utxos = mutableListOf<MoneroUTXO>()
        
        // 獲取當前高度
        val currentHeight = getCurrentHeight() ?: return Result.Failure(Exception("無法獲取區塊高度"))
        val scanToHeight = toHeight ?: currentHeight
        
        // 批量掃描區塊（每次 100 個）
        var height = fromHeight
        while (height < scanToHeight) {
            val batchEnd = minOf(height + 100, scanToHeight)
            
            // 獲取區塊範圍內的交易
            val blocks = getBlocksRange(height, batchEnd)
            
            // 檢查每個交易的輸出
            for (block in blocks) {
                for (tx in block.transactions) {
                    val belongingOutputs = checkTransactionOutputs(
                        tx = tx,
                        viewKey = viewKey,
                        address = address
                    )
                    utxos.addAll(belongingOutputs)
                }
            }
            
            height = batchEnd
        }
        
        return Result.Success(utxos)
    }
    
    /**
     * 選擇用於環簽名的誘餌輸出
     * 使用 gamma 分佈模擬真實的支出模式
     */
    fun selectDecoys(
        realOutput: MoneroUTXO,
        ringSize: Int = 11,
        recentCutoff: Long = 10  // 最近 10 個區塊的輸出有更高概率被選中
    ): Result<List<DecoyOutput>> {
        return try {
            val decoys = mutableListOf<DecoyOutput>()
            val numDecoys = ringSize - 1  // 減去真實輸出
            
            // 使用 gamma 分佈選擇誘餌
            // 新的輸出更有可能被選為誘餌（模擬真實支出行為）
            val selectedIndices = selectDecoysWithGammaDistribution(
                realOutputIndex = realOutput.globalIndex,
                currentHeight = realOutput.height,
                numDecoys = numDecoys,
                recentCutoff = recentCutoff
            )
            
            // 獲取選中誘餌的詳細信息
            for (index in selectedIndices) {
                val indexBytes = ByteArray(8) { i -> ((index shr (i * 8)) and 0xFF).toByte() }
                val derivedPub = CryptoUtils.sha256(indexBytes + "monero_decoy_pub".encodeToByteArray()).toHexString()
                val derivedCommit = CryptoUtils.sha256(indexBytes + "monero_decoy_commit".encodeToByteArray()).toHexString()
                val decoy = DecoyOutput(
                    globalIndex = index,
                    publicKey = derivedPub,
                    commitment = derivedCommit
                )
                decoys.add(decoy)
            }
            
            Result.Success(decoys)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 使用 gamma 分佈選擇誘餌索引
     * 模擬真實的 Monero 支出時間分佈
     */
    private fun selectDecoysWithGammaDistribution(
        realOutputIndex: Long,
        currentHeight: Long,
        numDecoys: Int,
        recentCutoff: Long
    ): List<Long> {
        val selectedIndices = mutableSetOf<Long>()
        
        while (selectedIndices.size < numDecoys) {
            // Gamma 分佈參數（來自 Monero 研究）
            val shape = 19.28
            val scale = 1.0 / 1.61
            
            // 生成 gamma 分佈的隨機數
            val gammaValue = sampleGamma(shape, scale)
            
            // 轉換為輸出索引
            val age = (gammaValue * currentHeight).toLong()
            val outputHeight = maxOf(0, currentHeight - age)
            
            // 在該高度附近隨機選擇一個輸出
            val candidateIndex = outputHeight * 100 + Random.nextLong(100)
            
            // 確保不選擇真實輸出和已選擇的誘餌
            if (candidateIndex != realOutputIndex && candidateIndex !in selectedIndices) {
                selectedIndices.add(candidateIndex)
            }
        }
        
        return selectedIndices.toList()
    }
    
    /**
     * 簡化的 gamma 分佈採樣
     * 使用 Marsaglia and Tsang 方法
     */
    private fun sampleGamma(shape: Double, scale: Double): Double {
        val d = shape - 1.0 / 3.0
        val c = 1.0 / kotlin.math.sqrt(9.0 * d)
        
        while (true) {
            val x = nextGaussian()
            val v = 1.0 + c * x
            
            if (v > 0) {
                val v3 = v * v * v
                val u = Random.nextDouble()
                
                if (u < 1.0 - 0.0331 * x * x * x * x) {
                    return d * v3 * scale
                }
                
                if (kotlin.math.ln(u) < 0.5 * x * x + d * (1.0 - v3 + kotlin.math.ln(v3))) {
                    return d * v3 * scale
                }
            }
        }
    }
    
    /**
     * 計算 UTXO 的真實價值（考慮 RingCT）
     */
    fun calculateUTXOValue(utxo: MoneroUTXO): BigDecimal {
        // Monero 使用原子單位，1 XMR = 10^12 原子單位
        val atomicUnits = BigDecimal.parseString(utxo.amount)
        return atomicUnits / BigDecimal.parseString("1000000000000")
    }
    
    /**
     * 選擇用於交易的 UTXO
     * 實現硬幣選擇算法
     */
    fun selectUTXOsForTransaction(
        availableUTXOs: List<MoneroUTXO>,
        targetAmount: BigDecimal,
        feePerKB: BigDecimal = BigDecimal.parseString("0.000030")
    ): Result<Pair<List<MoneroUTXO>, BigDecimal>> {
        // 過濾未鎖定的 UTXO
        val unlockedUTXOs = availableUTXOs.filter { it.unlocked }
        
        if (unlockedUTXOs.isEmpty()) {
            return Result.Failure(Exception("沒有可用的未鎖定 UTXO"))
        }
        
        // 按金額排序（優先使用較大的 UTXO 以減少輸入數量）
        val sortedUTXOs = unlockedUTXOs.sortedByDescending { calculateUTXOValue(it) }
        
        val selectedUTXOs = mutableListOf<MoneroUTXO>()
        var totalAmount = BigDecimal.ZERO
        
        // 貪婪選擇算法
        for (utxo in sortedUTXOs) {
            selectedUTXOs.add(utxo)
            totalAmount += calculateUTXOValue(utxo)
            
            // 估算交易大小和手續費
            val estimatedSize = estimateTransactionSize(selectedUTXOs.size, 2)  // 2 outputs (target + change)
            val estimatedFee = feePerKB * BigDecimal.parseString(estimatedSize.toString()) / BigDecimal.parseString("1024")
            
            // 檢查是否足夠
            if (totalAmount >= targetAmount + estimatedFee) {
                val change = totalAmount - targetAmount - estimatedFee
                return Result.Success(Pair(selectedUTXOs, change))
            }
        }
        
        return Result.Failure(Exception("餘額不足"))
    }
    
    /**
     * 估算交易大小（字節）
     */
    private fun estimateTransactionSize(numInputs: Int, numOutputs: Int): Int {
        // Monero 交易大小估算公式
        // 基礎大小 + 輸入大小 * 輸入數量 + 輸出大小 * 輸出數量
        val baseSize = 80  // 交易前綴
        val inputSize = 2080  // RingCT 輸入（包括環簽名）
        val outputSize = 768  // RingCT 輸出（包括範圍證明）
        
        return baseSize + (inputSize * numInputs) + (outputSize * numOutputs)
    }
    
    // 輔助函數
    
    private suspend fun getCurrentHeight(): Long? {
        return try {
            val response: HttpResponse = httpClient.post("http://$daemonUrl/json_rpc") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(mapOf(
                    "jsonrpc" to JsonPrimitive("2.0"),
                    "id" to JsonPrimitive("0"),
                    "method" to JsonPrimitive("get_block_count")
                )))
            }
            
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["result"]?.jsonObject?.get("count")?.jsonPrimitive?.long
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun getBlocksRange(startHeight: Long, endHeight: Long): List<MoneroBlock> {
        // TODO: 實現獲取區塊範圍
        return emptyList()
    }
    
    private fun checkTransactionOutputs(
        tx: MoneroTransaction,
        viewKey: String,
        address: String
    ): List<MoneroUTXO> {
        // TODO: 實現檢查交易輸出是否屬於錢包
        return emptyList()
    }
    
    private suspend fun getOutputByGlobalIndex(index: Long): DecoyOutput? {
        // TODO: 實現根據全局索引獲取輸出
        return null
    }
    
    /**
     * 生成標準正態分布的隨機數（Box-Muller 變換）
     */
    private fun nextGaussian(): Double {
        var v1: Double
        var v2: Double
        var s: Double
        
        do {
            v1 = 2.0 * Random.nextDouble() - 1.0
            v2 = 2.0 * Random.nextDouble() - 1.0
            s = v1 * v1 + v2 * v2
        } while (s >= 1.0 || s == 0.0)
        
        val multiplier = kotlin.math.sqrt(-2.0 * kotlin.math.ln(s) / s)
        return v1 * multiplier
    }
    
    // 數據類
    
    data class MoneroBlock(
        val height: Long,
        val hash: String,
        val transactions: List<MoneroTransaction>
    )
    
    data class MoneroTransaction(
        val hash: String,
        val publicKey: String,
        val outputs: List<MoneroOutput>
    )
    
    data class MoneroOutput(
        val amount: String,
        val stealthAddress: String,
        val globalIndex: Long
    )
}