package com.cbstudio.wearwallet.presentation.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result as CoreResult
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import timber.log.Timber

/**
 * WalletViewModel - 管理錢包應用的主要狀態
 * 包括檢查錢包是否存在的邏輯與啟動恢復攔截
 */
class WalletViewModel(
    walletRepository: WalletRepository? = null,
    startupRecoveryCoordinator: StartupRecoveryCoordinator? = null
) : ViewModel(), KoinComponent {

    private val repository: WalletRepository by lazy {
        walletRepository ?: getKoin().get()
    }
    private val coordinator: StartupRecoveryCoordinator by lazy {
        startupRecoveryCoordinator ?: getKoin().get()
    }

    // UI 狀態
    data class WalletAppState(
        val isLoading: Boolean = true,
        val hasWallet: Boolean = false,
        val shouldNavigateToCreate: Boolean = false,
        val isBlocked: Boolean = false,
        val error: String? = null
    )

    private val _appState = MutableStateFlow(WalletAppState())
    val appState: StateFlow<WalletAppState> = _appState.asStateFlow()

    init {
        initAfterStartupRecovery()
    }

    private fun initAfterStartupRecovery() {
        viewModelScope.launch {
            try {
                // 在 Coordinator 達到 READY 前不得載入錢包
                val readyResult = coordinator.awaitReady()
                if (readyResult is CoreResult.Success) {
                    checkWalletExists()
                } else {
                    val ex = (readyResult as? CoreResult.Failure)?.exception
                    Timber.e(ex, "StartupRecoveryCoordinator 未就緒，錢包載入已阻塞")
                    _appState.value = WalletAppState(
                        isLoading = false,
                        hasWallet = false,
                        shouldNavigateToCreate = false,
                        isBlocked = true,
                        error = ex?.message ?: "啟動對帳未就緒"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "等待啟動對帳就緒時發生異常")
                _appState.value = WalletAppState(
                    isLoading = false,
                    hasWallet = false,
                    shouldNavigateToCreate = false,
                    isBlocked = true,
                    error = e.message ?: "啟動對帳異常"
                )
            }
        }
    }

    /**
     * 檢查是否存在錢包
     */
    private suspend fun checkWalletExists() {
        try {
            val result = repository.getAllWallets()
            when (result) {
                is CoreResult.Success -> {
                    val hasWallet = result.data.isNotEmpty()
                    _appState.value = WalletAppState(
                        isLoading = false,
                        hasWallet = hasWallet,
                        shouldNavigateToCreate = !hasWallet,
                        isBlocked = false,
                        error = null
                    )
                    Timber.i("錢包檢查完成: hasWallet = $hasWallet")
                }
                is CoreResult.Failure -> {
                    Timber.e("獲取錢包列表失敗: ${result.exception.message}")
                    // 遇到 DB 查詢異常時嚴禁誤判為 hasWallet = false, shouldNavigateToCreate = true
                    _appState.value = WalletAppState(
                        isLoading = false,
                        hasWallet = false,
                        shouldNavigateToCreate = false,
                        isBlocked = true,
                        error = result.exception.message
                    )
                }
                is CoreResult.Loading -> {
                    _appState.value = _appState.value.copy(isLoading = true)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "檢查錢包失敗")
            // 嚴禁在異常時導引用戶建立新錢包覆蓋損壞 DB
            _appState.value = WalletAppState(
                isLoading = false,
                hasWallet = false,
                shouldNavigateToCreate = false,
                isBlocked = true,
                error = e.message
            )
        }
    }

    /**
     * 重置導航狀態
     */
    fun resetNavigation() {
        _appState.value = _appState.value.copy(shouldNavigateToCreate = false)
    }

    /**
     * 重試啟動對帳與錢包載入
     */
    fun retry() {
        _appState.value = WalletAppState(isLoading = true, isBlocked = false, error = null)
        initAfterStartupRecovery()
    }

    /**
     * 錢包創建後調用
     */
    fun onWalletCreated() {
        _appState.value = _appState.value.copy(
            hasWallet = true,
            shouldNavigateToCreate = false,
            isBlocked = false,
            error = null
        )
    }
}