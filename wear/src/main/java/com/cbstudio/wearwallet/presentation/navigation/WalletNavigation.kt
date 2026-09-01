package com.cbstudio.wearwallet.presentation.navigation

import org.koin.androidx.compose.koinViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.wear.compose.navigation.composable
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.SendScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.SendTransactionViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.ReceiveScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.ReceiveViewModel
import com.cbstudio.wearwallet.presentation.ui.token.TokenListScreen
import com.cbstudio.wearwallet.presentation.ui.token.TokenListViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.main.WalletMainViewModel
import androidx.compose.runtime.collectAsState
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.WalletManagementScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.WalletManagementViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.ShowMnemonicScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.ShowMnemonicViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.TransactionHistoryScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.history.TransactionHistoryViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.chain.ChainSelectorScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.chain.ChainSelectorViewModel
import com.cbstudio.wearwallet.presentation.screens.ai.AIAssistantScreen
import com.cbstudio.wearwallet.presentation.screens.ai.AIAssistantViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.main.nfc.WristToWristTransferScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.token.TokenManagementScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.addressbook.AddressBookScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.addressbook.AddContactScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet.ConnectKeystoneWalletScreenV2
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet.ConnectKeystoneWalletViewModelV2
import com.cbstudio.wearwallet.presentation.service.WearCommunicationRepository
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// 暫時註解掉不存在的畫面
// import com.cbstudio.wearwallet.presentation.screens.notification.NotificationListScreen
// import com.cbstudio.wearwallet.presentation.screens.subscription.SubscriptionScreen
// import com.cbstudio.wearwallet.presentation.screens.debug.DatabaseDebugScreen
// import com.cbstudio.wearwallet.presentation.screens.test.KmpTestScreen
import com.cbstudio.wearwallet.presentation.qrscanner.QrScannerScreen
// import com.cbstudio.wearwallet.presentation.screens.push.PushProtocolSettingsScreen
// import com.cbstudio.wearwallet.presentation.screens.payment.NFCPaymentScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.debitcard.CryptoDebitCardScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.bitcoin.BitcoinWalletScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.bitcoin.BitcoinWalletViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.utxo.UTXOSendScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.utxo.UTXOSendViewModel
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.presentation.wallet.screens.`import`.ImportWalletScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.`import`.ImportMnemonicScreen
// import com.cbstudio.wearwallet.presentation.screens.wearfi.WearFiScreen
// import com.cbstudio.wearwallet.presentation.screens.ai.AIInvestmentAdvisorScreen
// import com.cbstudio.wearwallet.presentation.screens.defi.DeFiOneClickScreen

/**
 * 錢包導航路徑
 */
object WalletRoute {
    const val SEND = "send"
    const val RECEIVE = "receive"
    const val TOKEN_SELECTOR = "token_selector"
    const val WALLET_MANAGEMENT = "wallet_management"
    const val TRANSACTION_HISTORY = "transaction_history"
    const val CHAIN_SELECTOR = "chain_selector"
    const val AI_ASSISTANT = "ai_assistant"
    const val SWAP = "swap"  // Cross-chain and same-chain swap
    const val WRIST_TRANSFER = "wrist_transfer"
    const val MNEMONIC_DISPLAY = "mnemonic_display"
    const val TOKEN_MANAGEMENT = "token_management"
    const val ADDRESS_BOOK = "address_book"
    const val ADD_CONTACT = "add_contact"
    const val KEYSTONE_CONNECT = "keystone_connect"
    const val NOTIFICATION_LIST = "notification_list"
    const val SUBSCRIPTION = "subscription"
    const val DATABASE_DEBUG = "database_debug"
    const val KMP_TEST = "kmp_test"
    const val QR_SCANNER = "qr_scanner"
    const val PUSH_PROTOCOL_SETTINGS = "push_protocol_settings"
    const val NFC_PAYMENT = "nfc_payment"
    const val DEBIT_CARD = "debit_card"
    const val WEAR_FI = "wear_fi"
    const val AI_INVESTMENT_ADVISOR = "ai_investment_advisor"
    const val DEFI_ONE_CLICK = "defi_one_click"
    const val BITCOIN_WALLET = "bitcoin_wallet/{walletId}"
    const val UTXO_SEND = "utxo_send/{chainType}"
    const val KEYSTONE_SEND = "keystone_send/{unsignedTx}"
    const val IMPORT_WALLET = "import_wallet"
    const val IMPORT_MNEMONIC = "import_mnemonic"
}

/**
 * 錢包導航圖 - 連接所有畫面到 coreKmp UseCases
 */
fun NavGraphBuilder.walletNavigation(
    navController: NavController
) {
    // 發送交易
    composable(WalletRoute.SEND) {
        val viewModel: SendTransactionViewModel = koinViewModel()
        SendScreen(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() },
            onNavigateToUTXOSend = { chainType ->
                navController.navigateToUTXOSend(chainType)
            },
            onNavigateToKeystoneSend = { unsignedTx ->
                navController.navigateToKeystoneSend(unsignedTx)
            }
        )
    }
    
    // 接收交易
    composable(WalletRoute.RECEIVE) {
        val viewModel: ReceiveViewModel = koinViewModel()
        ReceiveScreen(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() }
        )
    }
    
    // 代幣選擇與管理
    composable(WalletRoute.TOKEN_SELECTOR) {
        // 從當前錢包狀態獲取資訊
        val walletViewModel: com.cbstudio.wearwallet.presentation.wallet.screens.main.WalletMainViewModel = koinViewModel()
        val walletState = walletViewModel.uiState.collectAsState()
        
        TokenListScreen(
            walletAddress = walletState.value.currentWallet?.address ?: "",
            chainType = walletState.value.currentMultiChain,
            onTokenClick = { token ->
                // TODO: 導航到代幣詳情或轉帳
                navController.popBackStack()
            },
            onBackClick = { navController.popBackStack() }
        )
    }
    
    // 錢包管理
    composable(WalletRoute.WALLET_MANAGEMENT) {
        WalletManagementScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToKeystoneConnect = { navController.navigateToKeystoneConnect() },
            onNavigateToImportWallet = { navController.navigateToImportMnemonic() }
        )
    }
    
    // 交易歷史
    composable(WalletRoute.TRANSACTION_HISTORY) {
        val viewModel: TransactionHistoryViewModel = koinViewModel()
        TransactionHistoryScreen(
            onBackClick = { navController.popBackStack() },
            onTransactionClick = { transaction ->
                // TODO: 導航到交易詳情
            }
        )
    }
    
    // 鏈選擇
    composable(WalletRoute.CHAIN_SELECTOR) {
        ChainSelectorScreen(
            onBackClick = { navController.popBackStack() },
            onChainSelected = { chainType ->
                // 不需要在這裡 popBackStack — ChainSelectorScreen 的 onBackClick 已經處理了導航
                // 之前的雙重 pop 導致白屏（導航到了不存在的畫面）
            }
        )
    }
    
    // AI 助手
    composable(WalletRoute.AI_ASSISTANT) {
        val viewModel: AIAssistantViewModel = koinViewModel()
        AIAssistantScreen(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() }
        )
    }
    
    // Swap - Cross-chain and same-chain swap
    composable(WalletRoute.SWAP) {
        com.cbstudio.wearwallet.presentation.wallet.screens.swap.SwapScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    // 手腕對手腕傳輸
    composable(WalletRoute.WRIST_TRANSFER) {
        WristToWristTransferScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    // 助記詞顯示
    composable(WalletRoute.MNEMONIC_DISPLAY) {
        ShowMnemonicScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    // 導入錢包
    composable(WalletRoute.IMPORT_WALLET) {
        ImportWalletScreen(
            onNavigateBack = { navController.popBackStack() },
            onWalletImported = { navController.popBackStack() },
            onNavigateToMnemonicImport = { navController.navigateToImportMnemonic() }
        )
    }
    
    // 助記詞導入
    composable(WalletRoute.IMPORT_MNEMONIC) {
        ImportMnemonicScreen(
            onNavigateBack = { navController.popBackStack() },
            onImportSuccess = {
                // 回到錢包管理頁面
                navController.popBackStack(WalletRoute.WALLET_MANAGEMENT, inclusive = false)
            }
        )
    }
    
    // 代幣管理
    composable(WalletRoute.TOKEN_MANAGEMENT) {
        TokenManagementScreen(
            onBackClick = { navController.popBackStack() }
        )
    }
    
    // 地址簿
    composable(WalletRoute.ADDRESS_BOOK) {
        AddressBookScreen(
            onBackClick = { navController.popBackStack() },
            onAddContact = { navController.navigate(WalletRoute.ADD_CONTACT) },
            onContactClick = { contact ->
                // TODO: 處理聯絡人點擊
            }
        )
    }
    
    // 新增聯絡人
    composable(WalletRoute.ADD_CONTACT) {
        AddContactScreen(
            onBackClick = { navController.popBackStack() },
            onContactSaved = { 
                // 聯絡人保存成功後返回地址簿
                navController.popBackStack()
            }
        )
    }
    
    // Keystone 硬體錢包連接
    composable(WalletRoute.KEYSTONE_CONNECT) {
        val context = LocalContext.current
        val viewModel: ConnectKeystoneWalletViewModelV2 = koinViewModel()
        val communicationRepository = WearCommunicationRepository.getInstance()
        
        ConnectKeystoneWalletScreenV2(
            onNavigateBack = { navController.popBackStack() },
            onConnectSuccess = { walletAddress ->
                // 連接成功後返回
                navController.popBackStack()
            },
            onScanQr = {
                // 發送掃描請求到手機
                CoroutineScope(Dispatchers.IO).launch {
                    communicationRepository.requestKeystoneConnectScan(context)
                }
            },
            viewModel = viewModel
        )
    }
    
    // Keystone 發送 (簽名)
    composable(
        route = WalletRoute.KEYSTONE_SEND,
        arguments = listOf(
            androidx.navigation.navArgument("unsignedTx") {
                type = androidx.navigation.NavType.StringType
            }
        )
    ) { backStackEntry ->
        val unsignedTx = backStackEntry.arguments?.getString("unsignedTx") ?: ""
        // manually inject with parameter
        val viewModel: com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.KeystoneSendViewModel = org.koin.androidx.compose.koinViewModel(
            parameters = { org.koin.core.parameter.parametersOf(unsignedTx) }
        )
        
        com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.KeystoneSendScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onTransactionSent = { txHash ->
                 // Back to SendScreen or Main?
                 // If success, maybe go back to SendScreen and let it show success?
                 // Or navigate to Tx History?
                 // For now, pop back twice?
                 navController.popBackStack() 
                 navController.popBackStack() // Back to Wallet Main
            }
        )
    }
    
    // 暫時註解掉不存在的畫面
    /*
    // 通知列表
    composable(WalletRoute.NOTIFICATION_LIST) {
        // TODO: 實現 NotificationListScreen
    }
    
    // 訂閱
    composable(WalletRoute.SUBSCRIPTION) {
        // TODO: 實現 SubscriptionScreen
    }
    
    // 資料庫除錯
    composable(WalletRoute.DATABASE_DEBUG) {
        // TODO: 實現 DatabaseDebugScreen
    }
    
    // KMP 測試
    composable(WalletRoute.KMP_TEST) {
        // TODO: 實現 KmpTestScreen
    }
    
    // QR 掃描器
    composable(WalletRoute.QR_SCANNER) {
        val viewModel: com.cbstudio.wearwallet.presentation.qrscanner.QrScannerViewModel = koinViewModel()
        QrScannerScreen(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() },
            onQrCodeScanned = { result ->
                // TODO: 處理掃描結果，例如導航到發送頁面並填入地址
                // 目前先返回並印出 Log (實際邏輯需視需求而定)
                navController.previousBackStackEntry?.savedStateHandle?.set("qr_scan_result", result)
                navController.popBackStack()
            }
        )
    }
    
    // Push Protocol 設定
    composable(WalletRoute.PUSH_PROTOCOL_SETTINGS) {
        // TODO: 實現 PushProtocolSettingsScreen
    }
    
    // NFC 支付
    composable(WalletRoute.NFC_PAYMENT) {
        // TODO: 實現 NFCPaymentScreen
    }
    */
    
    // 加密借記卡
    composable(WalletRoute.DEBIT_CARD) {
        CryptoDebitCardScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    // Bitcoin 錢包
    composable(
        route = WalletRoute.BITCOIN_WALLET,
        arguments = listOf(
            androidx.navigation.navArgument("walletId") {
                type = androidx.navigation.NavType.StringType
            }
        )
    ) { backStackEntry ->
        val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
        val viewModel: BitcoinWalletViewModel = koinViewModel()
        BitcoinWalletScreen(
            walletId = walletId,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSend = { navController.navigateToUTXOSend(ChainType.BITCOIN) },
            onNavigateToReceive = { navController.navigateToReceive() },
            viewModel = viewModel
        )
    }
    
    // UTXO 發送
    composable(
        route = WalletRoute.UTXO_SEND,
        arguments = listOf(
            androidx.navigation.navArgument("chainType") {
                type = androidx.navigation.NavType.StringType
            }
        )
    ) { backStackEntry ->
        val chainTypeStr = backStackEntry.arguments?.getString("chainType") ?: "BITCOIN"
        val chainType = try {
            ChainType.valueOf(chainTypeStr)
        } catch (e: Exception) {
            ChainType.BITCOIN
        }
        
        val viewModel: UTXOSendViewModel = koinViewModel()
        UTXOSendScreen(
            chainType = chainType,
            onNavigateBack = { navController.popBackStack() },
            onTransactionSent = { txHash ->
                // TODO: 顯示交易成功畫面或返回主畫面
                navController.popBackStack()
            },
            viewModel = viewModel
        )
    }
    
    // 暫時註解掉不存在的畫面
    /*
    // WearFi
    composable(WalletRoute.WEAR_FI) {
        // TODO: 實現 WearFiScreen
    }
    
    // AI 投資顧問
    composable(WalletRoute.AI_INVESTMENT_ADVISOR) {
        // TODO: 實現 AIInvestmentAdvisorScreen
    }
    
    // DeFi 一鍵操作
    composable(WalletRoute.DEFI_ONE_CLICK) {
        // TODO: 實現 DeFiOneClickScreen
    }
    */
}

/**
 * 安全導航擴展 - 防止雙擊或生命週期競態條件導致的 IllegalArgumentException 崩潰
 */
private fun NavController.safeNavigate(route: String) {
    try {
        navigate(route)
    } catch (e: IllegalArgumentException) {
        // 當導航目標在 NavGraph 中找不到時（通常是雙擊 / 快速切換時序問題）
        android.util.Log.w("WalletNavigation", "Navigation to '$route' failed: ${e.message}")
    }
}

/**
 * 導航擴展函數
 */
fun NavController.navigateToSend() = safeNavigate(WalletRoute.SEND)
fun NavController.navigateToReceive() = safeNavigate(WalletRoute.RECEIVE)
fun NavController.navigateToTokenSelector() = safeNavigate(WalletRoute.TOKEN_SELECTOR)
fun NavController.navigateToWalletManagement() = safeNavigate(WalletRoute.WALLET_MANAGEMENT)
fun NavController.navigateToTransactionHistory() = safeNavigate(WalletRoute.TRANSACTION_HISTORY)
fun NavController.navigateToChainSelector() = safeNavigate(WalletRoute.CHAIN_SELECTOR)
fun NavController.navigateToAIAssistant() = safeNavigate(WalletRoute.AI_ASSISTANT)
fun NavController.navigateToWristTransfer() = safeNavigate(WalletRoute.WRIST_TRANSFER)
fun NavController.navigateToMnemonicDisplay() = safeNavigate(WalletRoute.MNEMONIC_DISPLAY)
fun NavController.navigateToTokenManagement() = safeNavigate(WalletRoute.TOKEN_MANAGEMENT)
fun NavController.navigateToAddressBook() = safeNavigate(WalletRoute.ADDRESS_BOOK)
fun NavController.navigateToKeystoneConnect() = safeNavigate(WalletRoute.KEYSTONE_CONNECT)
fun NavController.navigateToBitcoinWallet(walletId: String) = safeNavigate("bitcoin_wallet/$walletId")
fun NavController.navigateToUTXOSend(chainType: ChainType) = safeNavigate("utxo_send/${chainType.name}")
fun NavController.navigateToNotificationList() = safeNavigate(WalletRoute.NOTIFICATION_LIST)
fun NavController.navigateToSubscription() = safeNavigate(WalletRoute.SUBSCRIPTION)
fun NavController.navigateToDatabaseDebug() = safeNavigate(WalletRoute.DATABASE_DEBUG)
fun NavController.navigateToKmpTest() = safeNavigate(WalletRoute.KMP_TEST)
fun NavController.navigateToQrScanner() = safeNavigate(WalletRoute.QR_SCANNER)
fun NavController.navigateToPushProtocolSettings() = safeNavigate(WalletRoute.PUSH_PROTOCOL_SETTINGS)
fun NavController.navigateToNFCPayment() = safeNavigate(WalletRoute.NFC_PAYMENT)
fun NavController.navigateToDebitCard() = safeNavigate(WalletRoute.DEBIT_CARD)
fun NavController.navigateToWearFi() = safeNavigate(WalletRoute.WEAR_FI)
fun NavController.navigateToAIInvestmentAdvisor() = safeNavigate(WalletRoute.AI_INVESTMENT_ADVISOR)
fun NavController.navigateToDeFiOneClick() = safeNavigate(WalletRoute.DEFI_ONE_CLICK)
fun NavController.navigateToKeystoneSend(unsignedTx: String) = safeNavigate(
    WalletRoute.KEYSTONE_SEND.replace("{unsignedTx}", java.net.URLEncoder.encode(unsignedTx, "UTF-8"))
)
fun NavController.navigateToSwap() = safeNavigate(WalletRoute.SWAP)
fun NavController.navigateToImportWallet() = safeNavigate(WalletRoute.IMPORT_WALLET)
fun NavController.navigateToImportMnemonic() = safeNavigate(WalletRoute.IMPORT_MNEMONIC)