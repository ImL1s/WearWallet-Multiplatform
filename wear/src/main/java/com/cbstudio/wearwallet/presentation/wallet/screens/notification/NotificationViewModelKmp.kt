package com.cbstudio.wearwallet.presentation.wallet.screens.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationHistoryModel
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationPreferences
import com.cbstudio.wearwallet.core.domain.model.notification.PushSubscription
import com.cbstudio.wearwallet.core.domain.model.notification.NotificationType
import com.cbstudio.wearwallet.core.domain.usecase.notification.ManageNotificationsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 通知管理 ViewModel - KMP 架構實現
 * 使用 coreKmp UseCase 進行業務邏輯處理
 */
class NotificationViewModelKmp : ViewModel(), KoinComponent {

    // 注入 UseCase（來自 coreKmp）
    private val manageNotificationsUseCase: ManageNotificationsUseCase by inject()

    // UI 狀態
    data class NotificationUiState(
        val notifications: List<NotificationHistoryModel> = emptyList(),
        val filteredNotifications: List<NotificationHistoryModel> = emptyList(),
        val unreadNotifications: List<NotificationHistoryModel> = emptyList(),
        val preferences: NotificationPreferences? = null,
        val subscriptions: List<PushSubscription> = emptyList(),
        val isLoading: Boolean = false,
        val isUpdatingPreferences: Boolean = false,
        val searchQuery: String = "",
        val selectedType: NotificationType? = null,
        val showUnreadOnly: Boolean = false,
        val unreadCount: Int = 0,
        val currentWalletId: String? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        observeNotifications()
    }

    /**
     * 設置當前錢包並載入通知
     */
    fun setCurrentWallet(walletId: String) {
        _uiState.update { it.copy(currentWalletId = walletId) }
        loadNotifications(walletId)
        loadPreferences(walletId)
        loadSubscriptions(walletId)
    }

    /**
     * 載入通知列表
     */
    fun loadNotifications(walletId: String? = null) {
        val id = walletId ?: _uiState.value.currentWalletId
        if (id == null) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val result = manageNotificationsUseCase.getAllNotifications(id)
                when (result) {
                    is Result.Success -> {
                        val notifications = result.data
                        val unreadNotifications = notifications.filter { !it.isRead }
                        
                        _uiState.update { 
                            it.copy(
                                notifications = notifications,
                                filteredNotifications = applyFilters(notifications),
                                unreadNotifications = unreadNotifications,
                                unreadCount = unreadNotifications.size,
                                isLoading = false
                            )
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "載入通知失敗: ${result.exception.message}"
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
                        error = "載入通知異常: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 觀察通知變化
     */
    private fun observeNotifications() {
        viewModelScope.launch {
            manageNotificationsUseCase.observeNotifications("")
                .catch { e ->
                    _uiState.update { 
                        it.copy(error = "觀察通知失敗: ${e.message}")
                    }
                }
                .collect { notifications ->
                    val currentWalletId = _uiState.value.currentWalletId
                    if (currentWalletId != null) {
                        val walletNotifications = notifications.filter { it.walletId == currentWalletId }
                        val unreadNotifications = walletNotifications.filter { !it.isRead }
                        
                        _uiState.update { 
                            it.copy(
                                notifications = walletNotifications,
                                filteredNotifications = applyFilters(walletNotifications),
                                unreadNotifications = unreadNotifications,
                                unreadCount = unreadNotifications.size
                            )
                        }
                    }
                }
        }
    }

    /**
     * 載入通知偏好設置
     */
    private fun loadPreferences(walletId: String) {
        viewModelScope.launch {
            try {
                val result = manageNotificationsUseCase.getNotificationPreferences(walletId)
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(preferences = result.data)
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
     * 載入 Push 訂閱
     */
    private fun loadSubscriptions(walletId: String) {
        viewModelScope.launch {
            try {
                val result = manageNotificationsUseCase.getRecentSubscriptions(walletId, 20)
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(subscriptions = result.data)
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
     * 搜索通知
     */
    fun searchNotifications(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val filteredNotifications = applyFilters(_uiState.value.notifications)
        _uiState.update { it.copy(filteredNotifications = filteredNotifications) }
    }

    /**
     * 按類型過濾
     */
    fun filterByType(type: NotificationType?) {
        _uiState.update { it.copy(selectedType = type) }
        val filteredNotifications = applyFilters(_uiState.value.notifications)
        _uiState.update { it.copy(filteredNotifications = filteredNotifications) }
    }

    /**
     * 切換只顯示未讀
     */
    fun toggleUnreadOnly() {
        _uiState.update { it.copy(showUnreadOnly = !it.showUnreadOnly) }
        val filteredNotifications = applyFilters(_uiState.value.notifications)
        _uiState.update { it.copy(filteredNotifications = filteredNotifications) }
    }

    /**
     * 標記通知為已讀
     */
    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                val result = manageNotificationsUseCase.markAsRead(notificationId)
                when (result) {
                    is Result.Success -> {
                        // 成功後重新載入
                        _uiState.value.currentWalletId?.let { loadNotifications(it) }
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
     * 批量標記為已讀
     */
    fun markMultipleAsRead(notificationIds: List<String>) {
        viewModelScope.launch {
            try {
                val result = manageNotificationsUseCase.markMultipleAsRead(notificationIds)
                when (result) {
                    is Result.Success -> {
                        // 成功後重新載入
                        _uiState.value.currentWalletId?.let { loadNotifications(it) }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "批量標記已讀失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 標記中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "批量標記已讀異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 標記所有為已讀
     */
    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                val result = manageNotificationsUseCase.markAllAsRead(walletId)
                when (result) {
                    is Result.Success -> {
                        // 成功後重新載入
                        loadNotifications(walletId)
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "標記所有已讀失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 標記中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "標記所有已讀異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 刪除通知
     */
    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            try {
                val result = manageNotificationsUseCase.deleteNotification(notificationId)
                when (result) {
                    is Result.Success -> {
                        // 成功後重新載入
                        _uiState.value.currentWalletId?.let { loadNotifications(it) }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "刪除通知失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 刪除中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "刪除通知異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 清除舊通知
     */
    fun clearOldNotifications(daysOld: Int = 30) {
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                val result = manageNotificationsUseCase.clearOldNotifications(walletId, daysOld)
                when (result) {
                    is Result.Success -> {
                        // 成功後重新載入
                        loadNotifications(walletId)
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "清除舊通知失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 清除中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "清除舊通知異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 更新通知偏好設置
     */
    fun updatePreferences(preferences: NotificationPreferences) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUpdatingPreferences = true, error = null) }
                
                val result = manageNotificationsUseCase.updateNotificationPreferences(preferences)
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                preferences = result.data,
                                isUpdatingPreferences = false
                            )
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isUpdatingPreferences = false,
                                error = "更新設置失敗: ${result.exception.message}"
                            )
                        }
                    }
                    is Result.Loading -> {
                        // 保持更新狀態
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isUpdatingPreferences = false,
                        error = "更新設置異常: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 切換價格提醒通知
     */
    fun togglePriceAlerts() {
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                val result = manageNotificationsUseCase.togglePriceAlerts(walletId)
                when (result) {
                    is Result.Success -> {
                        // 重新載入偏好設置
                        loadPreferences(walletId)
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "切換價格提醒失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 切換中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "切換價格提醒異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 切換交易通知
     */
    fun toggleTransactionNotifications() {
        viewModelScope.launch {
            try {
                val walletId = _uiState.value.currentWalletId
                if (walletId == null) return@launch
                
                val result = manageNotificationsUseCase.toggleTransactionNotifications(walletId)
                when (result) {
                    is Result.Success -> {
                        // 重新載入偏好設置
                        loadPreferences(walletId)
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "切換交易通知失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 切換中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "切換交易通知異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 添加 Push 訂閱
     */
    fun addSubscription(subscription: PushSubscription) {
        viewModelScope.launch {
            try {
                val result = manageNotificationsUseCase.addSubscription(
                    subscription.walletAddress, 
                    subscription.channelAddress
                )
                when (result) {
                    is Result.Success -> {
                        // 重新載入訂閱
                        _uiState.value.currentWalletId?.let { loadSubscriptions(it) }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "添加訂閱失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 添加中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "添加訂閱異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 更新訂閱狀態
     */
    fun updateSubscriptionStatus(subscriptionId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                // 這個功能目前 UseCase 不支援，暫時跳過
                _uiState.value.currentWalletId?.let { loadSubscriptions(it) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "更新訂閱狀態異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 刪除訂閱
     */
    fun deleteSubscription(walletAddress: String, channelAddress: String) {
        viewModelScope.launch {
            try {
                val result = manageNotificationsUseCase.deleteSubscription(walletAddress, channelAddress)
                when (result) {
                    is Result.Success -> {
                        // 重新載入訂閱
                        _uiState.value.currentWalletId?.let { loadSubscriptions(it) }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "刪除訂閱失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 刪除中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "刪除訂閱異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 應用過濾條件
     */
    private fun applyFilters(notifications: List<NotificationHistoryModel>): List<NotificationHistoryModel> {
        var filtered = notifications
        
        // 按類型過濾
        _uiState.value.selectedType?.let { type ->
            filtered = filtered.filter { it.type == type }
        }
        
        // 只顯示未讀
        if (_uiState.value.showUnreadOnly) {
            filtered = filtered.filter { !it.isRead }
        }
        
        // 按搜索查詢過濾
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            filtered = filtered.filter { notification ->
                notification.title.contains(query, ignoreCase = true) ||
                notification.content.contains(query, ignoreCase = true)
            }
        }
        
        return filtered.sortedByDescending { it.createdAt }
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
            loadNotifications(walletId)
            loadPreferences(walletId)
            loadSubscriptions(walletId)
        }
    }
}