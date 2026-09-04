package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import com.cbstudio.wearwallet.presentation.wallet.utils.QRCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 接收功能 ViewModel
 * 
 * 負責：
 * 1. 獲取當前錢包地址
 * 2. 生成 QR 碼
 * 3. 處理複製地址功能
 * 4. 支援多鏈地址顯示
 * 
 * 只使用 coreKmp 的 WalletRepository
 */
class ReceiveViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val qrCodeGenerator: QRCodeGenerator by inject()
    
    // 當前選中的鏈（可從外部設置）
    private var currentChainType: ChainType? = null
    
    /**
     * UI 狀態
     */
    data class ReceiveUiState(
        val walletAddress: String = "",
        val walletName: String = "",
        val chainName: String = "Ethereum",
        val qrCodeBitmap: ImageBitmap? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val copySuccess: Boolean = false,
        val showFullAddress: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()
    
    init {
        loadWalletAddress()
        
        // 監聽鏈狀態變化
        viewModelScope.launch {
            ChainStateManager.currentChain.collect { chainType ->
                currentChainType = chainType
                loadWalletAddress()
            }
        }
    }
    
    /**
     * 設置當前鏈類型
     */
    fun setChainType(chainType: ChainType) {
        currentChainType = chainType
        loadWalletAddress()
    }
    
    /**
     * 載入當前活動錢包的地址
     */
    private fun loadWalletAddress() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                // 從 coreKmp 獲取活動錢包
                val result = walletRepository.getActiveWallet()
                
                when (result) {
                    is Result.Success -> {
                        val wallet = result.data
                        if (wallet != null) {
                            // 使用當前選中的鏈，如果沒有則使用錢包默認的鏈
                            val chainType = currentChainType ?: wallet.chainType
                            
                            // 根據不同的鏈類型獲取對應的地址
                            val address = getAddressForChain(wallet, chainType)
                            
                            _uiState.update { state ->
                                state.copy(
                                    walletAddress = address,
                                    walletName = wallet.name,
                                    chainName = chainType.displayName,
                                    isLoading = false
                                )
                            }
                            // 異步生成 QR 碼
                            generateQrCode(address)
                        } else {
                            _uiState.update { 
                                it.copy(
                                    error = "沒有找到活動錢包",
                                    isLoading = false
                                )
                            }
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                error = "載入錢包失敗: ${result.exception.message}",
                                isLoading = false
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
                        error = "載入錢包時發生錯誤: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * 根據鏈類型獲取對應的地址
     */
    private fun getAddressForChain(
        wallet: WalletAccount,
        chainType: ChainType
    ): String {
        return wallet.address
    }
    
    /**
     * 生成 QR 碼
     */
    private fun generateQrCode(address: String) {
        viewModelScope.launch {
            try {
                val qrBitmap = qrCodeGenerator.generateQrCode(address)
                _uiState.update { it.copy(qrCodeBitmap = qrBitmap) }
            } catch (e: Exception) {
                // QR 碼生成失敗不影響地址顯示
                Timber.e(e, "QR 碼生成失敗")
            }
        }
    }
    
    /**
     * 標記地址已複製
     */
    fun onAddressCopied() {
        _uiState.update { it.copy(copySuccess = true) }
        // 3 秒後重置複製成功狀態
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.update { it.copy(copySuccess = false) }
        }
    }
    
    /**
     * 切換地址顯示模式（縮略/完整）
     */
    fun toggleAddressDisplay() {
        _uiState.update { it.copy(showFullAddress = !it.showFullAddress) }
    }
    
    /**
     * 刷新數據
     */
    fun refresh() {
        loadWalletAddress()
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * 格式化地址顯示（縮略版本）
     */
    fun formatAddress(address: String, showFull: Boolean): String {
        return if (showFull || address.length <= 12) {
            address
        } else {
            "${address.take(6)}...${address.takeLast(4)}"
        }
    }
}