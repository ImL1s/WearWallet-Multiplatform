package com.cbstudio.wearwallet.presentation

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.presentation.theme.WearWalletTheme
import com.cbstudio.wearwallet.presentation.wallet.WalletScreen
import com.cbstudio.wearwallet.presentation.wallet.WalletViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.create.CreateWalletScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.onboarding.WalletOnboardingScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.import.ImportWalletScreen
import com.cbstudio.wearwallet.presentation.wallet.screens.import.ImportMnemonicScreen
import com.cbstudio.wearwallet.presentation.navigation.walletNavigation
import com.cbstudio.wearwallet.presentation.navigation.*
import com.cbstudio.wearwallet.core.security.DeviceSecurityChecker
import com.cbstudio.wearwallet.core.security.SecurityReport
import com.cbstudio.wearwallet.core.utils.Logger
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryState
import com.cbstudio.wearwallet.presentation.recovery.StartupReconcilingScreen
import com.cbstudio.wearwallet.presentation.recovery.StartupRecoveryErrorScreen
import com.cbstudio.wearwallet.presentation.security.SecurityWarningDialog
import org.koin.core.context.GlobalContext
import kotlin.system.exitProcess
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            WearWalletTheme {
                WearApp()
            }
        }
    }
}

@Composable
fun WearApp(
    recoveryCoordinator: StartupRecoveryCoordinator? = remember {
        GlobalContext.getOrNull()?.getOrNull<StartupRecoveryCoordinator>()
    }
) {
    if (recoveryCoordinator != null) {
        val recoveryState by recoveryCoordinator.state.collectAsState()

        when (val state = recoveryState) {
            is StartupRecoveryState.Initializing,
            is StartupRecoveryState.Reconciling -> {
                StartupReconcilingScreen(
                    stage = (state as? StartupRecoveryState.Reconciling)?.stage ?: "正在進行啟動對帳..."
                )
                return
            }
            is StartupRecoveryState.Failed -> {
                StartupRecoveryErrorScreen(
                    title = "啟動對帳失敗",
                    message = state.message,
                    onRetry = { recoveryCoordinator.retry() },
                    onExit = { exitProcess(0) }
                )
                return
            }
            is StartupRecoveryState.RecoveryRequired -> {
                StartupRecoveryErrorScreen(
                    title = "錢包需要維護",
                    message = state.reason,
                    onRetry = { recoveryCoordinator.retry() },
                    onExit = { exitProcess(0) }
                )
                return
            }
            is StartupRecoveryState.Ready -> {
                MainWalletApp()
            }
        }
    } else {
        StartupRecoveryErrorScreen(
            title = "啟動初始化失敗",
            message = "無法初始化安全協調器 (StartupRecoveryCoordinator is missing)",
            onRetry = { exitProcess(0) },
            onExit = { exitProcess(0) }
        )
    }
}

@Composable
fun MainWalletApp() {
    val navController = rememberSwipeDismissableNavController()
    val walletViewModel: WalletViewModel = viewModel()
    val appState by walletViewModel.appState.collectAsState()

    // 設備安全檢查
    var showSecurityWarning by remember { mutableStateOf(false) }
    var securityReport by remember { mutableStateOf<SecurityReport?>(null) }

    // 在應用啟動時進行安全檢查
    LaunchedEffect(Unit) {
        val securityChecker = DeviceSecurityChecker()
        val report = securityChecker.getSecurityReport()

        if (report.isCompromised) {
            securityReport = report
            showSecurityWarning = true
        }
    }

    if (appState.isBlocked) {
        StartupRecoveryErrorScreen(
            title = "錢包載入受阻",
            message = appState.error ?: "系統未就緒，啟動對帳或錢包載入失敗",
            onRetry = { walletViewModel.retry() },
            onExit = { exitProcess(0) }
        )
        return
    }
    
    // 根據錢包存在狀態決定起始頁面
    LaunchedEffect(appState) {
        when {
            appState.shouldNavigateToCreate -> {
                navController.navigate("onboarding") {
                    popUpTo("main") { inclusive = true }
                }
                walletViewModel.resetNavigation()
            }
            // 監測錢包狀態變化，若在 Onboarding 頁面且錢包已存在，自動跳轉主頁
            appState.hasWallet && navController.currentBackStackEntry?.destination?.route == "onboarding" -> {
                navController.navigate("main") {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        }
    }
    
    if (appState.isLoading) {
        // 顯示載入畫面
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = if (appState.hasWallet) "main" else "onboarding"
        ) {
            // 主錢包畫面
            composable("main") {
                WalletScreen(
                    viewModel = walletViewModel,
                    onNavigateToCreateMnemonic = {
                        navController.navigate("create_wallet")
                    },
                    onNavigateToChainSelector = { navController.navigateToChainSelector() },
                    onNavigateToSend = { navController.navigateToSend() },
                    onNavigateToReceive = { navController.navigateToReceive() },
                    onNavigateToTokenSelector = { navController.navigateToTokenSelector() },
                    onNavigateToMnemonic = { navController.navigateToMnemonicDisplay() },
                    onNavigateToWalletManagement = { navController.navigateToWalletManagement() },
                    onNavigateToTokenManagement = { navController.navigateToTokenManagement() },
                    onNavigateToTransactionHistory = { navController.navigateToTransactionHistory() },
                    onNavigateToNotificationList = { navController.navigateToNotificationList() },
                    onNavigateToAddressBook = { navController.navigateToAddressBook() },
                    onNavigateToSubscription = { navController.navigateToSubscription() },
                    onNavigateToDatabaseDebug = { navController.navigateToDatabaseDebug() },
                    onNavigateToKmpTest = { navController.navigateToKmpTest() },
                    onNavigateToAIAssistant = { navController.navigateToAIAssistant() },
                    onNavigateToQrScanner = { navController.navigateToQrScanner() },
                    onNavigateToPushProtocolSettings = { navController.navigateToPushProtocolSettings() },
                    onNavigateToWristTransfer = { navController.navigateToWristTransfer() },
                    onNavigateToNFCPayment = { navController.navigateToNFCPayment() },
                    onNavigateToDebitCard = { navController.navigateToDebitCard() },
                    onNavigateToWearFi = { navController.navigateToWearFi() },
                    onNavigateToAIInvestmentAdvisor = { navController.navigateToAIInvestmentAdvisor() },
                    onNavigateToDeFiOneClick = { navController.navigateToDeFiOneClick() },
                    onNavigateToSwap = { navController.navigateToSwap() },
                    onNavigateToImport = { navController.navigate("import_wallet") }
                )
            }
            
            // 錢包新手引導畫面（選擇創建或導入）
            composable("onboarding") {
                WalletOnboardingScreen(
                    onNavigateToCreate = {
                        navController.navigate("create_wallet")
                    },
                    onNavigateToImport = {
                        navController.navigate("import_wallet")
                    }
                )
            }
            
            // 創建錢包畫面
            composable("create_wallet") {
                CreateWalletScreen(
                    onNavigateBack = { 
                        navController.popBackStack() 
                    },
                    onWalletCreated = {
                        // 創建成功後更新狀態並返回主畫面
                        walletViewModel.onWalletCreated()
                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            
            // 導入錢包畫面（選擇導入類型：助記詞或私鑰）
            composable("import_wallet") {
                ImportWalletScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onWalletImported = {
                        // 導入成功後更新狀態並返回主畫面
                        walletViewModel.onWalletCreated()
                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onNavigateToMnemonicImport = {
                        navController.navigate("import_mnemonic")
                    }
                )
            }

            // 助記詞導入畫面（專門的 12 詞輸入界面）
            composable("import_mnemonic") {
                ImportMnemonicScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onImportSuccess = {
                        // 導入成功後更新狀態並返回主畫面
                        walletViewModel.onWalletCreated()
                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            
            // 添加所有其他導航路徑
            walletNavigation(navController)
        }

        // 顯示安全警告對話框
        if (showSecurityWarning && securityReport != null) {
            SecurityWarningDialog(
                securityReport = securityReport!!,
                onDismiss = {
                    // 用戶選擇繼續使用，關閉警告
                    showSecurityWarning = false
                },
                onForceExit = {
                    // 用戶選擇退出應用
                    exitProcess(0)
                }
            )
        }
    }
}