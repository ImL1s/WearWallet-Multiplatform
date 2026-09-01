package com.cbstudio.wearwallet.presentation.wallet.screens.pricealert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.pricealert.PriceAlert
import com.cbstudio.wearwallet.core.domain.model.pricealert.AlertType
import com.cbstudio.wearwallet.core.domain.usecase.pricealert.ManagePriceAlertsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 價格提醒 ViewModel - KMP 架構實現
 * 使用 coreKmp UseCase 進行業務邏輯處理
 */
class PriceAlertViewModelKmp : ViewModel(), KoinComponent {

    // 注入 UseCase（來自 coreKmp）
    private val managePriceAlertsUseCase: ManagePriceAlertsUseCase by inject()

    // UI 狀態
    data class PriceAlertUiState(
        val alerts: List<PriceAlert> = emptyList(),
        val filteredAlerts: List<PriceAlert> = emptyList(),
        val activeAlerts: List<PriceAlert> = emptyList(),
        val triggeredAlerts: List<PriceAlert> = emptyList(),
        val isLoading: Boolean = false,
        val isCreatingAlert: Boolean = false,
        val searchQuery: String = "",
        val selectedChain: ChainType? = null,
        val selectedAlertType: AlertType? = null,
        val currentWalletId: String? = null,
        val error: String? = null,
        val supportedTokens: List<String> = emptyList()
    )

    private val _uiState = MutableStateFlow(PriceAlertUiState())
    val uiState: StateFlow<PriceAlertUiState> = _uiState.asStateFlow()

    init {
        observeAlerts()
    }

    /**
     * 設置當前錢包並載入價格提醒
     */
    fun setCurrentWallet(walletId: String) {
        _uiState.update { it.copy(currentWalletId = walletId) }
        loadAlerts(walletId)
    }

    /**
     * 載入價格提醒列表
     */
    fun loadAlerts(walletId: String? = null) {
        val id = walletId ?: _uiState.value.currentWalletId
        if (id == null) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val result = managePriceAlertsUseCase.getAllAlerts()
                when (result) {
                    is Result.Success -> {
                        val alerts = result.data
                        
                        _uiState.update { 
                            it.copy(
                                alerts = alerts,
                                filteredAlerts = alerts,
                                isLoading = false
                            )
                        }
                        
                        // 載入其他數據
                        loadActiveAlerts(id)
                        loadTriggeredAlerts(id)
                        loadSupportedTokens()
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "載入價格提醒失敗: ${result.exception.message}"
                            )
                        }
                    }
                    is Result.Loading -> {
                        // 保持載入狀態
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "載入價格提醒異常: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 觀察價格提醒變化
     */
    private fun observeAlerts() {
        viewModelScope.launch {
            managePriceAlertsUseCase.observeAllAlerts()
                .catch { e ->
                    _uiState.update { 
                        it.copy(error = "觀察價格提醒失敗: ${e.message}")
                    }
                }
                .collect { alerts ->
                    val currentWalletId = _uiState.value.currentWalletId
                    if (currentWalletId != null) {
                        val walletAlerts = alerts.filter { it.walletId == currentWalletId }
                        _uiState.update { 
                            it.copy(
                                alerts = walletAlerts,
                                filteredAlerts = applyFilters(walletAlerts)
                            )
                        }
                    }
                }
        }
    }

    /**
     * 搜索價格提醒
     */
    fun searchAlerts(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                val result = if (query.isBlank()) {
                    managePriceAlertsUseCase.getAllAlerts()
                } else {
                    managePriceAlertsUseCase.searchAlerts(query)
                }
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(filteredAlerts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "搜索失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 搜索中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "搜索異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 按區塊鏈過濾
     */
    fun filterByChain(chainType: ChainType?) {
        _uiState.update { it.copy(selectedChain = chainType) }
        
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                val result = if (chainType == null) {
                    managePriceAlertsUseCase.getAllAlerts()
                } else {
                    managePriceAlertsUseCase.getAlertsByChain(chainType)
                }
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(filteredAlerts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "過濾失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 過濾中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "過濾異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 按提醒類型過濾
     */
    fun filterByAlertType(alertType: AlertType?) {
        _uiState.update { it.copy(selectedAlertType = alertType) }
        
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                val result = if (alertType == null) {
                    managePriceAlertsUseCase.getAllAlerts()
                } else {
                    managePriceAlertsUseCase.getAlertsByType(alertType)
                }
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(filteredAlerts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "過濾失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 過濾中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "過濾異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 創建價格提醒
     */
    fun createAlert(
        tokenSymbol: String,
        chainType: ChainType,
        targetPrice: Double,
        alertType: AlertType,
        note: String? = null
    ) {
        val walletId = _uiState.value.currentWalletId
        if (walletId == null) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCreatingAlert = true, error = null) }
                
                val result = managePriceAlertsUseCase.createAlert(
                    walletId = walletId,
                    assetSymbol = tokenSymbol,
                    assetName = tokenSymbol,
                    chainType = chainType,
                    alertType = alertType,
                    targetPrice = targetPrice,
                    currentPrice = targetPrice, // 使用目標價格作為當前價格的初始值
                    userNotes = note
                )
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { it.copy(isCreatingAlert = false) }
                        // 成功後重新載入
                        loadAlerts(walletId)
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isCreatingAlert = false,
                                error = "創建價格提醒失敗: ${result.exception.message}"
                            )
                        }
                    }
                    is Result.Loading -> {
                        // 保持創建狀態
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isCreatingAlert = false,
                        error = "創建價格提醒異常: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 切換提醒啟用狀態
     */
    fun toggleAlert(alertId: String) {
        viewModelScope.launch {
            try {
                val result = managePriceAlertsUseCase.toggleAlertEnabled(alertId)
                when (result) {
                    is Result.Success -> {
                        // 成功後重新載入
                        _uiState.value.currentWalletId?.let { loadAlerts(it) }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "切換提醒狀態失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 切換中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "切換提醒狀態異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 刪除價格提醒
     */
    fun deleteAlert(alertId: String) {
        viewModelScope.launch {
            try {
                val result = managePriceAlertsUseCase.deleteAlert(alertId)
                when (result) {
                    is Result.Success -> {
                        // 成功後重新載入
                        _uiState.value.currentWalletId?.let { loadAlerts(it) }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "刪除價格提醒失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 刪除中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "刪除價格提醒異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 標記已觸發提醒為已讀
     */
    fun markTriggeredAsRead(alertId: String) {
        viewModelScope.launch {
            try {
                // 重置觸發狀態
                val result = managePriceAlertsUseCase.resetTriggerStatus(alertId)
                when (result) {
                    is Result.Success -> {
                        // 重新載入已觸發提醒
                        _uiState.value.currentWalletId?.let { loadTriggeredAlerts(it) }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "標記已讀失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 標記中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "標記已讀異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 清理舊提醒
     */
    fun cleanupOldAlerts() {
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                // 獲取所有提醒，過濾出舊的，然後批量刪除
                val result = managePriceAlertsUseCase.getAllAlerts()
                when (result) {
                    is Result.Success -> {
                        val thirtyDaysAgo = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - (30L * 24 * 60 * 60 * 1000)
                        val oldAlerts = result.data.filter { it.createdAt < thirtyDaysAgo }
                        if (oldAlerts.isNotEmpty()) {
                            val deleteResult = managePriceAlertsUseCase.deleteAlerts(oldAlerts.map { it.id })
                            when (deleteResult) {
                                is Result.Success -> loadAlerts(walletId)
                                is Result.Failure -> {
                                    _uiState.update { 
                                        it.copy(error = "清理舊提醒失敗: ${deleteResult.exception.message}")
                                    }
                                }
                                is Result.Loading -> {}
                            }
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "清理舊提醒失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 清理中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "清理舊提醒異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 載入活躍提醒
     */
    private fun loadActiveAlerts(walletId: String) {
        viewModelScope.launch {
            try {
                val result = managePriceAlertsUseCase.getEnabledAlerts()
                when (result) {
                    is Result.Success -> {
                        val activeAlerts = result.data.filter { !it.isTriggered }
                        _uiState.update { 
                            it.copy(activeAlerts = activeAlerts)
                        }
                    }
                    is Result.Failure -> {
                        // 不影響主要功能，靜默失敗
                    }
                    is Result.Loading -> {
                        // 載入中
                    }
                }
            } catch (e: Exception) {
                // 不影響主要功能，靜默失敗
            }
        }
    }

    /**
     * 載入已觸發提醒
     */
    private fun loadTriggeredAlerts(walletId: String) {
        viewModelScope.launch {
            try {
                val result = managePriceAlertsUseCase.getAllAlerts()
                when (result) {
                    is Result.Success -> {
                        val triggeredAlerts = result.data.filter { it.isTriggered }
                        _uiState.update { 
                            it.copy(triggeredAlerts = triggeredAlerts)
                        }
                    }
                    is Result.Failure -> {
                        // 不影響主要功能，靜默失敗
                    }
                    is Result.Loading -> {
                        // 載入中
                    }
                }
            } catch (e: Exception) {
                // 不影響主要功能，靜默失敗
            }
        }
    }

    /**
     * 載入支援的代幣
     */
    private fun loadSupportedTokens() {
        viewModelScope.launch {
            try {
                // 暫時硬編碼支援的代幣列表
                val supportedTokens = listOf(
                    "BTC", "ETH", "BNB", "MATIC", "AVAX", "SOL", "ADA", "DOT",
                    "USDT", "USDC", "DAI", "BUSD", "LINK", "UNI", "AAVE", "SUSHI"
                )
                _uiState.update { 
                    it.copy(supportedTokens = supportedTokens)
                }
            } catch (e: Exception) {
                // 不影響主要功能，靜默失敗
            }
        }
    }

    /**
     * 應用過濾條件
     */
    private fun applyFilters(alerts: List<PriceAlert>): List<PriceAlert> {
        var filtered = alerts
        
        // 按區塊鏈過濾
        _uiState.value.selectedChain?.let { chain ->
            filtered = filtered.filter { it.chainType == chain }
        }
        
        // 按提醒類型過濾
        _uiState.value.selectedAlertType?.let { type ->
            filtered = filtered.filter { it.alertType == type }
        }
        
        // 按搜索查詢過濾
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            filtered = filtered.filter { alert ->
                alert.assetSymbol.contains(query, ignoreCase = true) ||
                alert.userNotes?.contains(query, ignoreCase = true) == true
            }
        }
        
        return filtered
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 刷新數據
     */
    fun refresh() {
        _uiState.value.currentWalletId?.let { walletId ->
            loadAlerts(walletId)
        }
    }
}