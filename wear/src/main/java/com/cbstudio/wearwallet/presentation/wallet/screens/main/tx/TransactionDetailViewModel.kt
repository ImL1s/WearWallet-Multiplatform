package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import com.cbstudio.wearwallet.presentation.navigation.decodeTransactionDetailId
import com.cbstudio.wearwallet.presentation.qa.WearQaFixtures
import com.cbstudio.wearwallet.presentation.qa.WearQaHarness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 交易詳情 ViewModel — 依交易 hash 從歷史記錄中載入完整資訊
 */
class TransactionDetailViewModel : ViewModel(), KoinComponent {

    private val walletRepository: WalletRepository by inject()
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase by inject()

    data class UiState(
        val isLoading: Boolean = true,
        val transaction: Transaction? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(transactionId: String) {
        viewModelScope.launch {
            val decodedId = decodeTransactionDetailId(transactionId)
            _uiState.value = UiState(isLoading = true)
            try {
                val walletResult = walletRepository.getActiveWallet()
                val wallet = (walletResult as? Result.Success)?.data
                if (wallet == null) {
                    val fixture = WearQaFixtures.findTransaction(
                        decodedId,
                        emptyList(),
                        WearQaHarness.isActive()
                    )
                    _uiState.value = UiState(
                        isLoading = false,
                        transaction = fixture,
                        error = WearQaFixtures.retainedLoadError(
                            networkError = "找不到啟用中的錢包",
                            overlayNonEmpty = fixture != null
                        )
                    )
                    return@launch
                }

                var found: Transaction? = null
                getTransactionHistoryUseCase(
                    walletAddress = wallet.address,
                    chainType = ChainStateManager.getCurrentChain(),
                    limit = 50
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            found = WearQaFixtures.findTransaction(
                                decodedId,
                                result.data,
                                WearQaHarness.isActive()
                            )
                        }
                        is Result.Failure -> throw result.exception
                        is Result.Loading -> Unit
                    }
                }

                if (found == null) {
                    found = WearQaFixtures.findTransaction(
                        decodedId,
                        emptyList(),
                        WearQaHarness.isActive()
                    )
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transaction = found,
                        error = WearQaFixtures.retainedLoadError(
                            networkError = if (found == null) "找不到此交易" else null,
                            overlayNonEmpty = found != null
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "載入交易詳情失敗")
                val fixture = WearQaFixtures.findTransaction(
                    decodedId,
                    emptyList(),
                    WearQaHarness.isActive()
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transaction = fixture,
                        error = WearQaFixtures.retainedLoadError(
                            networkError = "載入交易詳情失敗: ${e.message}",
                            overlayNonEmpty = fixture != null
                        )
                    )
                }
            }
        }
    }
}
