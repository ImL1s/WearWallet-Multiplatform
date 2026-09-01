package com.cbstudio.wearwallet.presentation.wallet.screens.settings.chain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 鏈選擇器 ViewModel
 * 使用 coreKmp 的 ChainType 和 WalletRepository
 */
class ChainSelectorViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    
    data class ChainSelectorUiState(
        val currentChain: ChainType = ChainType.ETHEREUM,
        val mainnetChains: List<ChainType> = listOf(
            // EVM 鏈
            ChainType.ETHEREUM,
            ChainType.BSC,
            ChainType.POLYGON,
            ChainType.ARBITRUM,
            ChainType.OPTIMISM,
            ChainType.AVALANCHE,
            ChainType.BASE,
            ChainType.CRONOS,
            // UTXO 鏈
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        ),
        val testnetChains: List<ChainType> = listOf(
            ChainType.SEPOLIA,
            ChainType.GOERLI,
            ChainType.MUMBAI
        ),
        val showTestnets: Boolean = false,
        val isLoading: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(ChainSelectorUiState())
    val uiState: StateFlow<ChainSelectorUiState> = _uiState.asStateFlow()
    
    init {
        loadCurrentChain()
    }
    
    /**
     * 載入當前選擇的鏈
     * 永遠從 WalletRepository 讀取，確保持久化的鏈設定被正確載入
     */
    private fun loadCurrentChain() {
        viewModelScope.launch {
            try {
                // 從 WalletRepository 獲取當前活動錢包的鏈
                val result = walletRepository.getActiveWallet()
                when (result) {
                    is com.cbstudio.wearwallet.core.common.Result.Success -> {
                        result.data?.let { wallet ->
                            _uiState.update { 
                                it.copy(currentChain = wallet.chainType)
                            }
                            // 同步到全局鏈狀態管理器
                            ChainStateManager.setCurrentChain(wallet.chainType)
                            Timber.d("從錢包載入鏈: ${wallet.chainType.displayName}")
                        }
                    }
                    else -> {
                        // fallback: 使用 ChainStateManager 的值
                        val fallback = ChainStateManager.getCurrentChain()
                        _uiState.update { it.copy(currentChain = fallback) }
                        Timber.e("無法載入當前鏈, fallback to: ${fallback.displayName}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入當前鏈時發生錯誤")
            }
        }
    }
    
    /**
     * 選擇鏈
     * 注意：ChainSelectorScreen 會在呼叫後立即 navigate back，
     * 所以不使用 isLoading 避免白屏（loading overlay 在導航後無法關閉）
     */
    fun selectChain(chain: ChainType) {
        // 立即更新 UI state（不設 isLoading，因為畫面會馬上 navigate back）
        _uiState.update { it.copy(currentChain = chain) }
        // 立即同步到全局鏈狀態管理器，讓 Dashboard 馬上收到變更
        ChainStateManager.setCurrentChain(chain)
        
        viewModelScope.launch {
            try {
                Timber.d("正在切換到鏈: ${chain.displayName}")
                
                // 獲取當前活動錢包
                val activeWalletResult = walletRepository.getActiveWallet()
                when (activeWalletResult) {
                    is com.cbstudio.wearwallet.core.common.Result.Success -> {
                        activeWalletResult.data?.let { wallet ->
                            // 更新錢包的鏈類型（持久化）
                            val updatedWallet = wallet.copy(chainType = chain)
                            val updateResult = walletRepository.updateWallet(updatedWallet)
                            
                            when (updateResult) {
                                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                                    Timber.i("成功切換到鏈: ${chain.displayName}")
                                }
                                else -> {
                                    Timber.e("更新錢包鏈失敗")
                                }
                            }
                        } ?: run {
                            Timber.e("沒有找到活動錢包")
                        }
                    }
                    else -> {
                        Timber.e("無法獲取活動錢包")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "切換鏈時發生錯誤")
            }
        }
    }
    
    /**
     * 切換測試網顯示
     */
    fun toggleTestnets() {
        _uiState.update { 
            it.copy(showTestnets = !it.showTestnets)
        }
    }
}