package com.cbstudio.wearwallet.core.blockchain.utxo

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UTXOSelection

/**
 * UTXO 選擇器
 * 實現多種 UTXO 選擇策略
 */
class UTXOSelector {
    
    companion object {
        const val DUST_THRESHOLD = 546L // satoshis
        const val BASE_TX_SIZE = 10 // 基礎交易大小
        const val INPUT_SIZE_P2PKH = 148 // P2PKH 輸入大小
        const val INPUT_SIZE_P2WPKH = 68 // P2WPKH 輸入大小
        const val OUTPUT_SIZE_P2PKH = 34 // P2PKH 輸出大小
        const val OUTPUT_SIZE_P2WPKH = 31 // P2WPKH 輸出大小
    }
    
    /**
     * 使用最優算法選擇 UTXO
     * 優先使用大額 UTXO 以減少輸入數量
     */
    fun selectOptimal(
        utxos: List<UTXO>,
        targetAmount: Long,
        feeRate: Long
    ): UTXOSelection {
        // 過濾已確認的 UTXO 並按價值降序排序
        val confirmedUTXOs = utxos
            .filter { it.confirmed }
            .sortedByDescending { it.value }
        
        if (confirmedUTXOs.isEmpty()) {
            throw InsufficientFundsException("No confirmed UTXOs available")
        }
        
        // 嘗試多種策略並選擇最優
        val strategies = listOf(
            { selectLargestFirst(confirmedUTXOs, targetAmount, feeRate) },
            { selectBranchAndBound(confirmedUTXOs, targetAmount, feeRate) },
            { selectSmallestFirst(confirmedUTXOs, targetAmount, feeRate) }
        )
        
        var bestSelection: UTXOSelection? = null
        var lowestFee = Long.MAX_VALUE
        
        for (strategy in strategies) {
            try {
                val selection = strategy()
                if (selection.estimatedFee < lowestFee) {
                    bestSelection = selection
                    lowestFee = selection.estimatedFee
                }
            } catch (e: InsufficientFundsException) {
                // 嘗試下一個策略
            }
        }
        
        return bestSelection ?: throw InsufficientFundsException(
            "Unable to select UTXOs. Required: $targetAmount satoshis"
        )
    }
    
    /**
     * Largest First 策略
     * 優先選擇最大的 UTXO
     */
    private fun selectLargestFirst(
        utxos: List<UTXO>,
        targetAmount: Long,
        feeRate: Long
    ): UTXOSelection {
        var total = 0L
        val selected = mutableListOf<UTXO>()
        
        for (utxo in utxos) {
            selected.add(utxo)
            total += utxo.value
            
            val estimatedFee = estimateFee(
                inputCount = selected.size,
                outputCount = 2, // 目標地址 + 找零
                feeRate = feeRate
            )
            
            if (total >= targetAmount + estimatedFee) {
                val change = total - targetAmount - estimatedFee
                
                // 如果找零小於 dust threshold，將其加入手續費
                val finalChange = if (change < DUST_THRESHOLD) {
                    0L
                } else {
                    change
                }
                
                val finalFee = if (change < DUST_THRESHOLD) {
                    estimatedFee + change
                } else {
                    estimatedFee
                }
                
                return UTXOSelection(
                    selectedUTXOs = selected,
                    totalValue = total,
                    change = finalChange,
                    estimatedFee = finalFee
                )
            }
        }
        
        throw InsufficientFundsException(
            "Insufficient funds. Available: $total, Required: ${targetAmount + estimateFee(utxos.size, 2, feeRate)}"
        )
    }
    
    /**
     * Branch and Bound 策略
     * 尋找最接近目標金額的 UTXO 組合
     */
    private fun selectBranchAndBound(
        utxos: List<UTXO>,
        targetAmount: Long,
        feeRate: Long
    ): UTXOSelection {
        val maxTries = 100000
        var tries = 0
        
        val estimatedFee = estimateFee(
            inputCount = 2, // 初始估算
            outputCount = 2,
            feeRate = feeRate
        )
        
        val targetWithFee = targetAmount + estimatedFee
        var bestSet: List<UTXO>? = null
        var bestValue = Long.MAX_VALUE
        
        fun search(
            available: List<UTXO>,
            selected: List<UTXO>,
            currentValue: Long,
            depth: Int
        ) {
            tries++
            if (tries > maxTries) return
            
            if (currentValue >= targetWithFee) {
                val actualFee = estimateFee(selected.size, 2, feeRate)
                val actualTarget = targetAmount + actualFee
                
                if (currentValue >= actualTarget && currentValue < bestValue) {
                    bestSet = selected.toList()
                    bestValue = currentValue
                }
                return
            }
            
            if (depth >= available.size) return
            
            // 嘗試包含當前 UTXO
            search(
                available,
                selected + available[depth],
                currentValue + available[depth].value,
                depth + 1
            )
            
            // 嘗試不包含當前 UTXO
            search(available, selected, currentValue, depth + 1)
        }
        
        search(utxos.take(10), emptyList(), 0, 0) // 限制搜索深度
        
        return bestSet?.let { set ->
            val total = set.sumOf { it.value }
            val fee = estimateFee(set.size, 2, feeRate)
            val change = total - targetAmount - fee
            
            UTXOSelection(
                selectedUTXOs = set,
                totalValue = total,
                change = if (change >= DUST_THRESHOLD) change else 0,
                estimatedFee = if (change < DUST_THRESHOLD) fee + change else fee
            )
        } ?: selectLargestFirst(utxos, targetAmount, feeRate)
    }
    
    /**
     * Smallest First 策略
     * 優先選擇最小的 UTXO（清理灰塵）
     */
    private fun selectSmallestFirst(
        utxos: List<UTXO>,
        targetAmount: Long,
        feeRate: Long
    ): UTXOSelection {
        val sorted = utxos.sortedBy { it.value }
        var total = 0L
        val selected = mutableListOf<UTXO>()
        
        for (utxo in sorted) {
            selected.add(utxo)
            total += utxo.value
            
            val estimatedFee = estimateFee(
                inputCount = selected.size,
                outputCount = 2,
                feeRate = feeRate
            )
            
            if (total >= targetAmount + estimatedFee) {
                val change = total - targetAmount - estimatedFee
                
                return UTXOSelection(
                    selectedUTXOs = selected,
                    totalValue = total,
                    change = if (change >= DUST_THRESHOLD) change else 0,
                    estimatedFee = if (change < DUST_THRESHOLD) estimatedFee + change else estimatedFee
                )
            }
        }
        
        throw InsufficientFundsException("Insufficient funds")
    }
    
    /**
     * 估算交易手續費
     */
    fun estimateFee(
        inputCount: Int,
        outputCount: Int,
        feeRate: Long,
        inputType: InputType = InputType.P2WPKH
    ): Long {
        val inputSize = when (inputType) {
            InputType.P2PKH -> INPUT_SIZE_P2PKH
            InputType.P2WPKH -> INPUT_SIZE_P2WPKH
        }
        
        val outputSize = OUTPUT_SIZE_P2WPKH // 默認使用 SegWit 輸出
        
        val size = BASE_TX_SIZE + (inputCount * inputSize) + (outputCount * outputSize)
        return size * feeRate
    }
    
    /**
     * 驗證 UTXO 選擇是否有效
     */
    fun validateSelection(
        selection: UTXOSelection,
        targetAmount: Long,
        feeRate: Long
    ): Boolean {
        val totalInput = selection.selectedUTXOs.sumOf { it.value }
        val estimatedFee = estimateFee(
            selection.selectedUTXOs.size,
            if (selection.change > 0) 2 else 1,
            feeRate
        )
        
        return totalInput >= targetAmount + estimatedFee
    }
    
    enum class InputType {
        P2PKH,
        P2WPKH
    }
}

/**
 * 資金不足異常
 */
class InsufficientFundsException(message: String) : Exception(message)