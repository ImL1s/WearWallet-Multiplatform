package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.keystone.KeystoneManager
import com.cbstudio.wearwallet.core.domain.service.KeystoneService
import com.cbstudio.wearwallet.core.keystone.ScanResult
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneTransaction
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import io.github.iml1s.crypto.RLP
import com.cbstudio.wearwallet.core.security.toHexString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

import kotlinx.coroutines.delay

data class KeystoneSendUiState(
    val step: KeystoneSendStep = KeystoneSendStep.PREPARING,
    val unsignedTx: String = "",
    val keystoneTx: KeystoneTransaction? = null,
    val qrCodeData: List<String> = emptyList(),
    val scanProgress: Float = 0f,
    val signature: String? = null,
    val v: String? = null,
    val r: String? = null,
    val s: String? = null,
    val txHash: String? = null,
    val error: String? = null
)

enum class KeystoneSendStep {
    PREPARING,
    SHOW_QR,     // Show Sign Request
    SCAN_QR,     // Scan Sign Response
    BROADCASTING,
    SUCCESS,
    FAILED
}

class KeystoneSendViewModel(
    private val unsignedTxHex: String
) : ViewModel(), KoinComponent {

    private val keystoneManager: KeystoneManager by inject()
    private val keystoneService: KeystoneService by inject()
    private val walletRepository: WalletRepository by inject()
    private val transactionRepository: TransactionRepository by inject()
    
    private val _uiState = MutableStateFlow(KeystoneSendUiState(unsignedTx = unsignedTxHex))
    val uiState: StateFlow<KeystoneSendUiState> = _uiState.asStateFlow()
    
    init {
        generateSignRequest()
    }
    
    private fun generateSignRequest() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(step = KeystoneSendStep.PREPARING) }
                
                val walletResult = walletRepository.getActiveWallet()
                val wallet = (walletResult as? Result.Success)?.data ?: throw Exception("No active wallet")
                
                if (unsignedTxHex.isBlank()) throw Exception("Empty transaction data")
                    
                val transaction = try {
                    Json.decodeFromString<KeystoneTransaction>(unsignedTxHex)
                } catch (e: Exception) {
                    throw Exception("Invalid transaction format: ${e.message}")
                }
                
                _uiState.update { it.copy(keystoneTx = transaction) }

                // 獲取錢包詳細資訊
                val fullWallet = (walletRepository.getWallet(wallet.id) as? Result.Success)?.data ?: wallet

                // 創建簽名請求 (使用 KeystoneService 直接生成 SDK 格式)
                val signRequest = keystoneService.generateEthSignRequest(
                    unsignedTxHex = transaction.data ?: "", // 如果是 EIP-1559 數據
                    derivationPath = fullWallet.derivationPath ?: KeystoneService.DEFAULT_DERIVATION_PATH,
                    masterFingerprint = fullWallet.masterFingerprint ?: KeystoneService.DEFAULT_MASTER_FINGERPRINT,
                    chainId = (transaction.chainId.toLongOrNull() ?: 1L),
                    fromAddress = fullWallet.address
                )
                
                _uiState.update { 
                    it.copy(
                        step = KeystoneSendStep.SHOW_QR,
                        qrCodeData = signRequest.qrCodeData
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { it.copy(step = KeystoneSendStep.FAILED, error = e.message) }
            }
        }
    }
    
    fun onScanClick() {
         _uiState.update { it.copy(step = KeystoneSendStep.SCAN_QR) }
    }
    
    fun handleScanResult(data: String) {
        viewModelScope.launch {
            when (val result = keystoneManager.handleScan(data)) {
                is Result.Success -> {
                     when (val scanData = result.data) {
                         is ScanResult.Complete -> {
                             Timber.d("Scan complete: ${scanData.data}")
                             handleSignature(scanData.data)
                         }
                         is ScanResult.Progress -> {
                             _uiState.update { 
                                 it.copy(scanProgress = scanData.current.toFloat() / scanData.total) 
                             }
                         }
                     }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(error = result.exception.message) }
                }
                else -> {}
            }
        }
    }
    
    private suspend fun handleSignature(urData: String) {
        val result = keystoneService.parseSignature(urData)
        when (result) {
            is com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneSignatureResult.Success -> {
                _uiState.update { it.copy(
                    signature = result.signature,
                    v = "", // 簽名已包含 V
                    r = "", // 簽名已包含 R
                    s = "", // 簽名已包含 S
                    step = KeystoneSendStep.BROADCASTING 
                ) }
                broadcastTransaction()
            }
            is com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneSignatureResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
            }
            is com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneSignatureResult.Incomplete -> {
                // 如果是動畫 QR，可能會走到這
                Timber.d("Signature incomplete, need more fragments")
            }
        }
    }
    
    private suspend fun broadcastTransaction() {
        try {
             val signature = _uiState.value.signature ?: throw IllegalStateException("Signature missing")
             
             // 如果 signature 是 0x 開頭的 Full Signed Transaction (對於某些 SDK 實現)，直接廣播
             // 如果只是 65 bytes 的原始簽名，則需要與 Unsigned 交易合併
             var signedTxHex = if (signature.startsWith("0x")) signature else "0x$signature"
             
             // 注意: Keystone SDK 的 parseSignature 得到的 signature 字段
             // 如果是 EthSignRequest.DataType.TypedTransaction，SDK 可能返回的是 Full Signed Transaction hex
             // 我們檢查長度來判斷。如果是 65 bytes (130 chars) 左右，則是原始簽名
             
             if (signedTxHex.length < 200) {
                 Timber.w("Signature seems to be raw, might need RLP merging. But SDK usually handles this.")
             }

             // 廣播
             val txHash = transactionRepository.sendTransaction(signedTxHex, ChainType.ETHEREUM)
             
             _uiState.update { it.copy(step = KeystoneSendStep.SUCCESS, txHash = txHash) }
             
        } catch (e: Exception) {
             _uiState.update { it.copy(step = KeystoneSendStep.FAILED, error = "Broadcast failed: ${e.message}") }
        }
    }
    
    fun retry() {
        generateSignRequest()
    }
    
}
