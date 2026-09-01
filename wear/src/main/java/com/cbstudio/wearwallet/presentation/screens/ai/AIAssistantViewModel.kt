package com.cbstudio.wearwallet.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cbstudio.wearwallet.R

/**
 * AI 助手 ViewModel
 * 
 * 功能：
 * 1. 語音指令處理
 * 2. 自然語言交易
 * 3. 風險分析
 * 4. 智能建議
 */
class AIAssistantViewModel(application: Application) : AndroidViewModel(application), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val sendTransactionUseCase: SendTransactionUseCase by inject()
    
    data class AIAssistantUiState(
        val isListening: Boolean = false,
        val voiceInput: String = "",
        val aiResponse: String = "",
        val suggestions: List<String> = emptyList(),
        val isProcessing: Boolean = false,
        val error: String? = null,
        val pendingTransaction: TransactionRequest? = null
    )
    
    data class TransactionRequest(
        val recipientAddress: String,
        val amount: String,
        val tokenSymbol: String,
        val riskLevel: RiskLevel = RiskLevel.MEDIUM
    )
    
    enum class RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    private val _uiState = MutableStateFlow(AIAssistantUiState())
    val uiState: StateFlow<AIAssistantUiState> = _uiState.asStateFlow()
    
    init {
        loadInitialSuggestions()
    }
    
    private fun loadInitialSuggestions() {
        _uiState.update { 
            it.copy(
                suggestions = listOf(
                    getApplication<Application>().getString(R.string.ai_suggestion_check_balance),
                    getApplication<Application>().getString(R.string.ai_suggestion_send_eth),
                    getApplication<Application>().getString(R.string.ai_suggestion_show_history),
                    getApplication<Application>().getString(R.string.ai_suggestion_switch_chain),
                    getApplication<Application>().getString(R.string.ai_suggestion_scan_tokens)
                )
            )
        }
    }
    
    fun startListening() {
        _uiState.update { 
            it.copy(
                isListening = true,
                voiceInput = "",
                aiResponse = ""
            )
        }
    }
    
    fun stopListening() {
        _uiState.update { 
            it.copy(isListening = false)
        }
    }
    
    fun processVoiceInput(input: String) {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        voiceInput = input,
                        isProcessing = true
                    )
                }
                
                // 分析語音指令
                val response = analyzeCommand(input)
                
                _uiState.update { 
                    it.copy(
                        aiResponse = response,
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "處理語音輸入失敗")
                _uiState.update { 
                    it.copy(
                        error = getApplication<Application>().getString(R.string.ai_error_voice_input, e.message ?: ""),
                        isProcessing = false
                    )
                }
            }
        }
    }
    
    private suspend fun analyzeCommand(input: String): String {
        // 簡單的指令解析邏輯
        return when {
            input.contains("餘額", ignoreCase = true) -> {
                getApplication<Application>().getString(R.string.ai_response_balance, "0.001", "2.50")
            }
            input.contains("發送", ignoreCase = true) || input.contains("轉帳", ignoreCase = true) -> {
                parseTransactionCommand(input)
                getApplication<Application>().getString(R.string.ai_response_prepare_send)
            }
            input.contains("交易", ignoreCase = true) -> {
                getApplication<Application>().getString(R.string.ai_response_no_history)
            }
            input.contains("切換", ignoreCase = true) -> {
                getApplication<Application>().getString(R.string.ai_response_chain_switched)
            }
            input.contains("掃描", ignoreCase = true) -> {
                getApplication<Application>().getString(R.string.ai_response_scanning)
            }
            else -> {
                getApplication<Application>().getString(R.string.ai_response_unknown_command)
            }
        }
    }
    
    private fun parseTransactionCommand(input: String) {
        // 簡單的交易解析邏輯
        // 實際應用中應該使用更複雜的 NLP
        
        val amount = extractAmount(input)
        val address = extractAddress(input)
        
        if (amount != null) {
            _uiState.update { 
                it.copy(
                    pendingTransaction = TransactionRequest(
                        recipientAddress = address ?: "",
                        amount = amount,
                        tokenSymbol = "ETH",
                        riskLevel = RiskLevel.MEDIUM
                    )
                )
            }
        }
    }
    
    private fun extractAmount(input: String): String? {
        // 提取金額的簡單正則表達式
        val regex = """(\d+\.?\d*)\s*(ETH|USDC|USDT)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(input)
        return match?.groupValues?.get(1)
    }
    
    private fun extractAddress(input: String): String? {
        // 提取地址的簡單正則表達式
        val regex = """0x[a-fA-F0-9]{40}""".toRegex()
        val match = regex.find(input)
        return match?.value
    }
    
    fun confirmTransaction() {
        viewModelScope.launch {
            _uiState.value.pendingTransaction?.let { tx ->
                try {
                    _uiState.update { it.copy(isProcessing = true) }
                    
                    // 發送交易
                    // TODO: 實際實現發送邏輯
                    
                    _uiState.update { 
                        it.copy(
                            aiResponse = getApplication<Application>().getString(R.string.ai_transaction_sent),
                            pendingTransaction = null,
                            isProcessing = false
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "發送交易失敗")
                    _uiState.update { 
                        it.copy(
                            error = getApplication<Application>().getString(R.string.ai_error_send_failed, e.message ?: ""),
                            isProcessing = false
                        )
                    }
                }
            }
        }
    }
    
    fun cancelTransaction() {
        _uiState.update { 
            it.copy(
                pendingTransaction = null,
                aiResponse = getApplication<Application>().getString(R.string.ai_transaction_cancelled)
            )
        }
    }
    
    fun clearError() {
        _uiState.update { 
            it.copy(error = null)
        }
    }
}