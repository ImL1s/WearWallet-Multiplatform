package com.cbstudio.wearwallet.examples

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.security.KeystoreManager
import com.cbstudio.wearwallet.domain.service.SolanaTransactionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Solana 交易示例 ViewModel
 *
 * 展示如何使用 SolanaTransactionService 進行 SOL 轉帳
 */
class SolanaTransactionExampleViewModel : ViewModel(), KoinComponent {

    // 注入服務
    private val solanaService: SolanaTransactionService by inject()
    private val keystoreManager: KeystoreManager by inject()

    // UI 狀態
    private val _uiState = MutableStateFlow<TransactionUiState>(TransactionUiState.Idle)
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    /**
     * 發送 SOL 轉帳
     *
     * @param mnemonic 助記詞（從安全存儲獲取）
     * @param toAddress 接收方地址（Base58 編碼）
     * @param amountInSol 轉帳金額（SOL）
     * @param recentBlockhash 最新區塊哈希（從 RPC 獲取）
     */
    fun sendSolTransfer(
        mnemonic: String,
        toAddress: String,
        amountInSol: Double,
        recentBlockhash: String
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = TransactionUiState.Loading

                // 1. 推導 Solana 私鑰
                Timber.d("正在推導 Solana 私鑰...")
                val derivationPath = "m/44'/501'/0'/0'" // Solana 標準 BIP44 路徑
                val privateKey = keystoreManager.derivePrivateKey(mnemonic, derivationPath)

                // 2. 獲取公鑰（即 Solana 地址）
                Timber.d("正在生成公鑰...")
                val publicKey = keystoreManager.getPublicKey(privateKey)
                Timber.d("發送方地址: $publicKey")

                // 3. 轉換金額為 lamports
                val lamports = (amountInSol * SolanaTransactionService.LAMPORTS_PER_SOL).toLong()
                Timber.d("轉帳金額: $amountInSol SOL ($lamports lamports)")

                // 4. 估算手續費
                val feeResult = solanaService.estimateTransactionFee()
                val fee = feeResult.getOrThrow()
                Timber.d("預估手續費: $fee lamports (${fee / SolanaTransactionService.LAMPORTS_PER_SOL.toDouble()} SOL)")

                // 5. 檢查餘額（這裡需要實際的 RPC 調用）
                // val balance = solanaRpcClient.getBalance(publicKey)
                // if (balance < lamports + fee) {
                //     throw Exception("餘額不足")
                // }

                // 6. 發送交易
                Timber.d("正在構建並簽名交易...")
                val result = solanaService.sendSolTransaction(
                    fromPublicKey = publicKey,
                    toPublicKey = toAddress,
                    amount = lamports,
                    privateKeyHex = privateKey,
                    recentBlockhash = recentBlockhash
                )

                // 7. 處理結果
                result.fold(
                    onSuccess = { signature ->
                        Timber.i("交易簽名成功: $signature")
                        _uiState.value = TransactionUiState.Success(
                            signature = signature,
                            explorerUrl = "https://explorer.solana.com/tx/$signature"
                        )
                    },
                    onFailure = { error ->
                        Timber.e(error, "交易簽名失敗")
                        _uiState.value = TransactionUiState.Error(
                            message = error.message ?: "未知錯誤"
                        )
                    }
                )

            } catch (e: Exception) {
                Timber.e(e, "發送交易失敗")
                _uiState.value = TransactionUiState.Error(
                    message = e.message ?: "未知錯誤"
                )
            }
        }
    }

    /**
     * 估算交易費用
     */
    fun estimateFee() {
        viewModelScope.launch {
            try {
                val result = solanaService.estimateTransactionFee()
                result.fold(
                    onSuccess = { feeInLamports ->
                        val feeInSol = feeInLamports / SolanaTransactionService.LAMPORTS_PER_SOL.toDouble()
                        Timber.d("預估手續費: $feeInSol SOL ($feeInLamports lamports)")
                        _uiState.value = TransactionUiState.FeeEstimated(
                            lamports = feeInLamports,
                            sol = feeInSol
                        )
                    },
                    onFailure = { error ->
                        Timber.e(error, "估算手續費失敗")
                        _uiState.value = TransactionUiState.Error(
                            message = error.message ?: "估算失敗"
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "估算手續費異常")
                _uiState.value = TransactionUiState.Error(
                    message = e.message ?: "未知錯誤"
                )
            }
        }
    }

    /**
     * 重置狀態
     */
    fun resetState() {
        _uiState.value = TransactionUiState.Idle
    }
}

/**
 * 交易 UI 狀態
 */
sealed class TransactionUiState {
    /** 閒置狀態 */
    object Idle : TransactionUiState()

    /** 載入中 */
    object Loading : TransactionUiState()

    /** 手續費已估算 */
    data class FeeEstimated(
        val lamports: Long,
        val sol: Double
    ) : TransactionUiState()

    /** 交易成功 */
    data class Success(
        val signature: String,
        val explorerUrl: String
    ) : TransactionUiState()

    /** 發生錯誤 */
    data class Error(
        val message: String
    ) : TransactionUiState()
}

/**
 * Compose UI 使用示例
 */
/*
@Composable
fun SolanaTransactionScreen(
    viewModel: SolanaTransactionExampleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is TransactionUiState.Idle -> {
                Text("準備發送交易")
                Button(onClick = {
                    viewModel.sendSolTransfer(
                        mnemonic = "你的助記詞",
                        toAddress = "接收方地址",
                        amountInSol = 0.001,
                        recentBlockhash = "最新區塊哈希"
                    )
                }) {
                    Text("發送 0.001 SOL")
                }
            }

            is TransactionUiState.Loading -> {
                CircularProgressIndicator()
                Text("交易處理中...")
            }

            is TransactionUiState.FeeEstimated -> {
                Text("預估手續費: ${state.sol} SOL")
            }

            is TransactionUiState.Success -> {
                Text("交易成功！", color = Color.Green)
                Text("簽名: ${state.signature.take(16)}...")
                Button(onClick = {
                    // 開啟瀏覽器查看交易
                }) {
                    Text("在瀏覽器查看")
                }
            }

            is TransactionUiState.Error -> {
                Text("交易失敗", color = Color.Red)
                Text(state.message)
                Button(onClick = {
                    viewModel.resetState()
                }) {
                    Text("重試")
                }
            }
        }
    }
}
*/

/**
 * 進階示例：批量轉帳
 */
/*
fun sendBatchTransfers(
    mnemonic: String,
    transfers: List<TransferRequest>,
    recentBlockhash: String
) {
    viewModelScope.launch {
        transfers.forEach { transfer ->
            try {
                Timber.d("處理轉帳: ${transfer.toAddress}, ${transfer.amount} SOL")

                val result = sendSolTransfer(
                    mnemonic = mnemonic,
                    toAddress = transfer.toAddress,
                    amountInSol = transfer.amount,
                    recentBlockhash = recentBlockhash
                )

                // 等待一段時間避免 rate limit
                delay(500)

            } catch (e: Exception) {
                Timber.e(e, "批量轉帳失敗: ${transfer.toAddress}")
            }
        }
    }
}

data class TransferRequest(
    val toAddress: String,
    val amount: Double
)
*/

/**
 * 進階示例：帶重試的轉帳
 */
/*
suspend fun sendWithRetry(
    mnemonic: String,
    toAddress: String,
    amountInSol: Double,
    maxRetries: Int = 3
): Result<String> {
    var lastError: Throwable? = null

    repeat(maxRetries) { attempt ->
        try {
            Timber.d("嘗試發送交易 (第 ${attempt + 1} 次)")

            // 獲取最新的區塊哈希
            val recentBlockhash = solanaRpcClient.getRecentBlockhash()

            // 發送交易
            return sendSolTransfer(
                mnemonic = mnemonic,
                toAddress = toAddress,
                amountInSol = amountInSol,
                recentBlockhash = recentBlockhash
            )

        } catch (e: Exception) {
            lastError = e
            Timber.w(e, "交易失敗，準備重試...")

            // 等待一段時間再重試
            delay(2000 * (attempt + 1))
        }
    }

    return Result.failure(lastError ?: Exception("交易失敗"))
}
*/
