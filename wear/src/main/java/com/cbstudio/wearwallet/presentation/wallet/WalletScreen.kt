package com.cbstudio.wearwallet.presentation.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.HorizontalPageIndicator
import kotlinx.coroutines.launch
import com.cbstudio.wearwallet.presentation.wallet.screens.main.WalletMainScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.main.WalletMainViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.WalletSettingsScreen

@Composable
fun WalletScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: WalletViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToCreateMnemonic: () -> Unit,
    onNavigateToChainSelector: () -> Unit,
    onNavigateToSend: () -> Unit,
    onNavigateToReceive: () -> Unit,
    onNavigateToSwap: () -> Unit = {},  // NEW: Swap navigation
    onNavigateToTokenSelector: () -> Unit,
    onNavigateToMnemonic: () -> Unit,
    onNavigateToWalletManagement: () -> Unit,
    onNavigateToTokenManagement: () -> Unit,
    onNavigateToTransactionHistory: () -> Unit,
    onNavigateToNotificationList: () -> Unit,
    onNavigateToAddressBook: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToDatabaseDebug: () -> Unit = {},
    onNavigateToKmpTest: () -> Unit = {},
    onNavigateToAIAssistant: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onNavigateToPushProtocolSettings: () -> Unit = {},
    onNavigateToWristTransfer: () -> Unit = {},
    onNavigateToNFCPayment: () -> Unit = {},
    onNavigateToDebitCard: () -> Unit = {},
    onNavigateToWearFi: () -> Unit = {},
    onNavigateToAIInvestmentAdvisor: () -> Unit = {},
    onNavigateToDeFiOneClick: () -> Unit = {},
    onNavigateToImport: () -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = 0) { 2 }  // pageCount = 2
    val coroutineScope = rememberCoroutineScope()
    
    // 在這裡創建 WalletMainViewModel，確保它的生命週期綁定到 WalletScreen
    val walletMainViewModel: WalletMainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
        ) { page ->
            when (page) {
                0 -> WalletMainScreen(
                    viewModel = walletMainViewModel,
                    onNavigateToSend = onNavigateToSend,
                    onNavigateToReceive = onNavigateToReceive,
                    onNavigateToSwap = onNavigateToSwap,
                    onNavigateToTokenSelector = onNavigateToTokenSelector,
                    onNavigateToWalletManagement = onNavigateToWalletManagement,
                    onNavigateToTransactionHistory = onNavigateToTransactionHistory,
                    onNavigateToQrScanner = onNavigateToQrScanner,
                    onNavigateToAIAssistant = onNavigateToAIAssistant,
                    onNavigateToWristTransfer = onNavigateToWristTransfer,
                    onNavigateToNFCPayment = onNavigateToNFCPayment,
                    onNavigateToDebitCard = onNavigateToDebitCard,
                    onNavigateToWearFi = onNavigateToWearFi,
                    onNavigateToAIInvestmentAdvisor = onNavigateToAIInvestmentAdvisor,
                    onNavigateToDeFiOneClick = onNavigateToDeFiOneClick,
                    onNavigateToImport = onNavigateToImport,
                    onNavigateToCreateWallet = onNavigateToCreateMnemonic
                )
                1 -> WalletSettingsScreen(
                    onNavigateToCreateMnemonic = onNavigateToCreateMnemonic,
                    onNavigateToChainSelector = onNavigateToChainSelector,
                    onNavigateToMnemonic = onNavigateToMnemonic,
                    onNavigateToWalletManagement = onNavigateToWalletManagement,
                    onNavigateToTokenManagement = onNavigateToTokenManagement,
                    onNavigateBack = {
                        // 返回到主錢包頁面（頁面 0）
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    },
                    onNavigateToNotificationList = onNavigateToNotificationList,
                    onNavigateToAddressBook = onNavigateToAddressBook,
                    onNavigateToSubscription = onNavigateToSubscription,
                    onNavigateToDatabaseDebug = onNavigateToDatabaseDebug,
                    onNavigateToKmpTest = onNavigateToKmpTest,
                    onNavigateToAIAssistant = onNavigateToAIAssistant,
                    onNavigateToPushProtocolSettings = onNavigateToPushProtocolSettings
                )
            }
        }

        // 使用原生指示器 (放在底部)
        HorizontalPageIndicator(
            pagerState = pagerState, modifier = Modifier
                .padding(bottom = 8.dp)
                .zIndex(20f)
        )
    }
}
