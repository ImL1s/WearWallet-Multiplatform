package com.cbstudio.wearwallet.presentation.wallet

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainSelection
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局鏈狀態管理器
 * 用於在不同畫面間共享當前選中的鏈與網路環境 (Mainnet / Testnet)
 */
object ChainStateManager {
    private val _currentSelection = MutableStateFlow(ChainSelection.default())
    val currentSelection: StateFlow<ChainSelection> = _currentSelection.asStateFlow()

    private val _currentChain = MutableStateFlow(ChainType.ETHEREUM)
    val currentChain: StateFlow<ChainType> = _currentChain.asStateFlow()

    fun selectChain(selection: ChainSelection) {
        _currentSelection.value = selection
        _currentChain.value = selection.toChainExecutionContext().chain
    }

    fun setSelection(selection: ChainSelection) {
        selectChain(selection)
    }

    fun getSelection(): ChainSelection {
        return _currentSelection.value
    }

    fun setCurrentChain(chain: ChainType) {
        try {
            val selection = ChainSelection.fromChainType(chain)
            selectChain(selection)
        } catch (e: Exception) {
            _currentChain.value = chain
        }
    }

    fun setCurrentChain(chain: ChainType, networkType: NetworkType) {
        try {
            val selection = ChainSelection.fromChainType(chain, networkType)
            selectChain(selection)
        } catch (e: Exception) {
            _currentChain.value = chain
        }
    }

    fun getCurrentChain(): ChainType {
        return _currentChain.value
    }

    val currentChainType: ChainType
        get() = _currentChain.value

    val currentNetworkType: NetworkType
        get() = _currentSelection.value.networkType

    val currentMultiChainType: MultiChainType
        get() = _currentSelection.value.multiChainType

    val currentChainId: Long
        get() = _currentSelection.value.chainId

    fun isTestnet(): Boolean = _currentSelection.value.isTestnet()
}