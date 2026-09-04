package com.cbstudio.wearwallet.presentation.wallet.screens.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.presentation.TestTags
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

import com.cbstudio.wearwallet.presentation.wallet.screens.main.components.BalanceCard
import com.cbstudio.wearwallet.presentation.wallet.screens.main.components.TransactionButtonsWithQR
import com.cbstudio.wearwallet.presentation.wallet.screens.main.components.TransactionHistoryCard
import com.cbstudio.wearwallet.presentation.wallet.screens.main.components.TokenManagementCard
import com.cbstudio.wearwallet.presentation.wallet.screens.main.components.EmptyWalletGuideCard
import com.cbstudio.wearwallet.presentation.gesture.rememberDoublePinchGestureDetector
import com.cbstudio.wearwallet.presentation.gesture.GestureContext
import com.cbstudio.wearwallet.presentation.gesture.GestureType
import com.cbstudio.wearwallet.presentation.wallet.screens.main.components.WalletSwitcher

/**
 * 錢包主畫面 - 完整功能恢復
 * ULTRATHINK Phase 20 - UI 介面恢復
 */
@Composable
fun WalletMainScreen(
    onNavigateToSend: () -> Unit = {},
    onNavigateToReceive: () -> Unit = {},
    onNavigateToSwap: () -> Unit = {},  // NEW: Swap navigation
    onNavigateToTokenSelector: () -> Unit = {},
    onNavigateToWalletManagement: () -> Unit = {},
    onNavigateToTransactionHistory: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToAIAssistant: () -> Unit = {},
    onNavigateToWristTransfer: () -> Unit = {},
    onNavigateToNFCPayment: () -> Unit = {},
    onNavigateToDebitCard: () -> Unit = {},
    onNavigateToWearFi: () -> Unit = {},
    onNavigateToAIInvestmentAdvisor: () -> Unit = {},
    onNavigateToDeFiOneClick: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onNavigateToCreateWallet: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WalletMainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    
    // 雙重捏合手勢檢測器 - 維護模式暫時停用
    /*
    val gestureDetector = rememberDoublePinchGestureDetector(
        gestureContext = GestureContext.GENERAL,
        onGesture = { event ->
            when (event.type) {
                GestureType.DOUBLE_PINCH -> {
                    when (event.context) {
                        GestureContext.QUICK_RECEIVE -> onNavigateToReceive()
                        GestureContext.PORTFOLIO_CHECK -> {
                            Toast.makeText(context, "總資產: ${uiState.balance.amount} ${uiState.balance.coin.symbol}", Toast.LENGTH_SHORT).show()
                        }
                        GestureContext.AI_ASSISTANT -> onNavigateToAIAssistant()
                        else -> {
                            Toast.makeText(context, "雙重捏合手勢", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                else -> {}
            }
        }
    )
    */

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(TestTags.MAIN_SCREEN)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            contentPadding = PaddingValues(top = 20.dp, bottom = 76.dp)
        ) {
            // 空錢包引導（當沒有錢包且非載入狀態時顯示）
            if (uiState.currentWallet == null && !uiState.isLoading) {
                item {
                    EmptyWalletGuideCard(
                        onNavigateToCreate = onNavigateToCreateWallet,
                        onNavigateToImport = onNavigateToImport
                    )
                }
                item { Spacer(modifier = Modifier.height(6.dp)) }
            }

            // 有錢包時顯示完整功能
            if (uiState.currentWallet != null) {
            item {
                WalletSwitcher(
                    currentWallet = uiState.currentWallet,
                    walletCount = uiState.walletCount,
                    onManageWallets = onNavigateToWalletManagement
                )
            }
            item { Spacer(modifier = Modifier.height(6.dp)) }
            uiState.error?.let { errorMessage ->
                item {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(6.dp)) }
            }
            item {
                BalanceCard(
                    currentWallet = uiState.currentWallet,
                    chainType = uiState.currentChain,
                    balance = uiState.nativeBalance,
                    balanceUsd = uiState.nativeBalanceUsd,
                    tokenPrice = uiState.currentTokenPrice,
                    isLoading = uiState.isLoading,
                    isScanningTokens = uiState.isScanningTokens,
                    onRefresh = viewModel::refresh,
                    onSelectToken = onNavigateToTokenSelector,
                    onScanTokens = viewModel::scanTokens
                )
            }
            item { Spacer(modifier = Modifier.height(6.dp)) }
            item {
                TokenManagementCard(
                    onNavigateToTokenList = onNavigateToTokenSelector,
                    enabled = !uiState.isLoading,  // Removed error check
                    tokenCount = uiState.tokenCount,
                    totalValue = uiState.tokensTotalValue
                )
            }
            // TODO: QuickTokenActionsCard 功能未實作，暫時隱藏
            /*
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                QuickTokenActionsCard(
                    onSwapClick = { /* TODO: Navigate to swap */ },
                    onBridgeClick = { /* TODO: Navigate to bridge */ },
                    onStakeClick = { /* TODO: Navigate to stake */ },
                    enabled = !uiState.isLoading && uiState.error == null
                )
            }
            */
            item { Spacer(modifier = Modifier.height(6.dp)) }
            item {
                TransactionHistoryCard(
                    onNavigateToHistory = onNavigateToTransactionHistory
                )
            }
            item {
                // 底部空間（避開固定操作鈕）
                Spacer(modifier = Modifier.height(16.dp))
            }
            }
        }

        if (uiState.currentWallet != null) {
            TransactionButtonsWithQR(
                onSendClick = onNavigateToSend,
                onReceiveClick = onNavigateToReceive,
                onSwapClick = onNavigateToSwap,
                onScanQrClick = onNavigateToQrScanner,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 22.dp)
            )
        }
    }

    // 錯誤提示 - 維護模式暫時停用
    /*
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    */
}
