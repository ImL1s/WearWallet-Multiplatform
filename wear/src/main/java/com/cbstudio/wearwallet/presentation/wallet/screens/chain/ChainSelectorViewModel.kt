package com.cbstudio.wearwallet.presentation.wallet.screens.chain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 區塊鏈選擇 ViewModel
 */
class ChainSelectorViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    
    data class ChainSelectorUiState(
        val activeWallet: WalletAccount? = null,
        val currentChain: ChainType = ChainType.ETHEREUM,
        val availableChains: List<ChainInfo> = emptyList(),
        val searchQuery: String = "",
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    data class ChainInfo(
        val chainType: ChainType,
        val name: String,
        val symbol: String,
        val logoUrl: String? = null,
        val isTestnet: Boolean = false,
        val explorerUrl: String,
        val rpcUrl: String
    )
    
    private val _uiState = MutableStateFlow(ChainSelectorUiState())
    val uiState: StateFlow<ChainSelectorUiState> = _uiState.asStateFlow()
    
    // 過濾後的鏈列表
    val filteredChains: Flow<List<ChainInfo>> = uiState
        .map { state ->
            if (state.searchQuery.isEmpty()) {
                state.availableChains
            } else {
                state.availableChains.filter { chain ->
                    chain.name.contains(state.searchQuery, ignoreCase = true) ||
                    chain.symbol.contains(state.searchQuery, ignoreCase = true)
                }
            }
        }
    
    init {
        loadActiveWallet()
        loadAvailableChains()
    }
    
    private fun loadActiveWallet() {
        viewModelScope.launch {
            try {
                val result = walletRepository.getActiveWallet()
                when (result) {
                    is Result.Success -> {
                        result.data?.let { wallet ->
                            _uiState.update { 
                                it.copy(
                                    activeWallet = wallet,
                                    currentChain = wallet.chainType
                                )
                            }
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "載入錢包失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入錢包失敗")
                _uiState.update { 
                    it.copy(error = "載入錢包時發生錯誤: ${e.message}")
                }
            }
        }
    }
    
    private fun loadAvailableChains() {
        val chains = listOf(
            ChainInfo(
                chainType = ChainType.ETHEREUM,
                name = "Ethereum",
                symbol = "ETH",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://etherscan.io",
                rpcUrl = "https://mainnet.infura.io/v3/"
            ),
            ChainInfo(
                chainType = ChainType.BSC,
                name = "Binance Smart Chain",
                symbol = "BNB",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://bscscan.com",
                rpcUrl = "https://bsc-dataseed.binance.org"
            ),
            ChainInfo(
                chainType = ChainType.POLYGON,
                name = "Polygon",
                symbol = "MATIC",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://polygonscan.com",
                rpcUrl = "https://polygon-rpc.com"
            ),
            ChainInfo(
                chainType = ChainType.AVALANCHE,
                name = "Avalanche",
                symbol = "AVAX",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://snowtrace.io",
                rpcUrl = "https://api.avax.network/ext/bc/C/rpc"
            ),
            ChainInfo(
                chainType = ChainType.ARBITRUM,
                name = "Arbitrum",
                symbol = "ETH",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://arbiscan.io",
                rpcUrl = "https://arb1.arbitrum.io/rpc"
            ),
            ChainInfo(
                chainType = ChainType.OPTIMISM,
                name = "Optimism",
                symbol = "ETH",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://optimistic.etherscan.io",
                rpcUrl = "https://mainnet.optimism.io"
            ),
            ChainInfo(
                chainType = ChainType.CRONOS,
                name = "Cronos",
                symbol = "CRO",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://cronoscan.com",
                rpcUrl = "https://evm.cronos.org"
            ),
            ChainInfo(
                chainType = ChainType.FANTOM,
                name = "Fantom",
                symbol = "FTM",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://ftmscan.com",
                rpcUrl = "https://rpc.ftm.tools"
            ),
            ChainInfo(
                chainType = ChainType.GNOSIS,
                name = "Gnosis",
                symbol = "xDAI",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://gnosisscan.io",
                rpcUrl = "https://rpc.gnosischain.com"
            ),
            ChainInfo(
                chainType = ChainType.CELO,
                name = "Celo",
                symbol = "CELO",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://celoscan.io",
                rpcUrl = "https://forno.celo.org"
            ),
            ChainInfo(
                chainType = ChainType.MOONBEAM,
                name = "Moonbeam",
                symbol = "GLMR",
                logoUrl = null,
                isTestnet = false,
                explorerUrl = "https://moonscan.io",
                rpcUrl = "https://rpc.api.moonbeam.network"
            ),
        )
        
        _uiState.update { 
            it.copy(availableChains = chains)
        }
    }
    
    fun selectChain(chain: ChainInfo) {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        currentChain = chain.chainType,
                        isLoading = true
                    )
                }
                
                // 更新錢包的當前鏈
                _uiState.value.activeWallet?.let { wallet ->
                    val updatedWallet = wallet.copy(chainType = chain.chainType)
                    walletRepository.updateWallet(updatedWallet)
                }
                
                _uiState.update { 
                    it.copy(isLoading = false)
                }
            } catch (e: Exception) {
                Timber.e(e, "切換區塊鏈失敗")
                _uiState.update { 
                    it.copy(
                        error = "切換區塊鏈失敗: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { 
            it.copy(searchQuery = query)
        }
    }
    
    fun clearError() {
        _uiState.update { 
            it.copy(error = null)
        }
    }
}