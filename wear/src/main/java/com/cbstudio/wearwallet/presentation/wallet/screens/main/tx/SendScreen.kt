package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cbstudio.wearwallet.core.security.AndroidPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import com.cbstudio.wearwallet.core.security.AuthOperation
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.presentation.TestTags
import com.cbstudio.wearwallet.presentation.qa.WearQaFixtureBanner
import com.cbstudio.wearwallet.presentation.util.DebugEmulatorAuth

/** 成功狀態綠色（主題無對應語意色票，刻意保留固定值） */
private val SuccessGreen = Color(0xFF4CAF50)

/** 安全升級警示琥珀色（主題無對應語意色票，刻意保留固定值） */
private val WarningAmber = Color(0xFFFFA726)

internal fun amountStepTokenSymbol(
    selectedToken: com.cbstudio.wearwallet.core.domain.model.Token?
): String? = selectedToken?.symbol

/**
 * 發送交易主畫面 - Wear OS Material 3
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SendScreen(
    viewModel: SendTransactionViewModel = koinViewModel(),
    onBackClick: () -> Unit = {},
    onNavigateToUTXOSend: ((chainType: com.cbstudio.wearwallet.core.domain.model.ChainType) -> Unit)? = null,
    onNavigateToKeystoneSend: ((unsignedTx: String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 生命週期監聽：切換至背景時自動廢棄進行中的認證狀態 (Directive R3/P1-1)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 如果是 UTXO 鏈，導航到專門的 UTXO 發送畫面
    LaunchedEffect(uiState.isUTXOChain, uiState.currentChain) {
        if (uiState.isUTXOChain && onNavigateToUTXOSend != null) {
            onNavigateToUTXOSend(uiState.currentChain)
        }
    }

    // Keystone 導航
    LaunchedEffect(uiState.keystoneUnsignedTx) {
        uiState.keystoneUnsignedTx?.let { unsignedTx ->
            onNavigateToKeystoneSend?.invoke(unsignedTx)
            viewModel.resetKeystoneNavigation()
        }
    }
    
    // 動畫狀態
    val animatedProgress by animateFloatAsState(
        targetValue = when(uiState.currentStep) {
            SendTransactionViewModel.TransactionStep.INPUT_ADDRESS -> 0.15f
            SendTransactionViewModel.TransactionStep.INPUT_AMOUNT -> 0.35f
            SendTransactionViewModel.TransactionStep.CONFIRM,
            SendTransactionViewModel.TransactionStep.REVIEWED -> 0.55f
            SendTransactionViewModel.TransactionStep.MIGRATION_REQUIRED -> 0.50f
            SendTransactionViewModel.TransactionStep.MIGRATING -> 0.60f
            SendTransactionViewModel.TransactionStep.AUTH_REQUIRED,
            SendTransactionViewModel.TransactionStep.AUTHENTICATING -> 0.70f
            SendTransactionViewModel.TransactionStep.AUTHORIZED,
            SendTransactionViewModel.TransactionStep.SIGNING -> 0.85f
            SendTransactionViewModel.TransactionStep.BROADCASTING,
            SendTransactionViewModel.TransactionStep.SENDING -> 0.95f
            SendTransactionViewModel.TransactionStep.SUCCESS -> 1f
            SendTransactionViewModel.TransactionStep.AUTH_CANCELLED,
            SendTransactionViewModel.TransactionStep.AUTH_EXPIRED,
            SendTransactionViewModel.TransactionStep.FAILED -> 0f
        },
        animationSpec = tween(500)
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 步驟進度指示器
        if (uiState.currentStep != SendTransactionViewModel.TransactionStep.SUCCESS &&
            uiState.currentStep != SendTransactionViewModel.TransactionStep.FAILED &&
            uiState.currentStep != SendTransactionViewModel.TransactionStep.AUTH_CANCELLED &&
            uiState.currentStep != SendTransactionViewModel.TransactionStep.AUTH_EXPIRED) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )
        }
        
        // 主要內容
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            }
        ) { step ->
            when (step) {
                SendTransactionViewModel.TransactionStep.INPUT_ADDRESS -> {
                    ModernAddressInputScreen(
                        address = uiState.recipientAddress,
                        addressError = uiState.addressError,
                        onAddressChange = viewModel::setRecipientAddress,
                        onNext = viewModel::proceedToAmount,
                        onBack = onBackClick,
                        onAddressBookClick = viewModel::toggleAddressBook,
                        showAddressBook = uiState.showAddressBook,
                        addressBookContacts = uiState.addressBookContacts,
                        addressBookLoading = uiState.addressBookLoading,
                        onContactSelect = viewModel::selectAddressContact,
                        onSearchAddressBook = viewModel::searchAddressBook
                    )
                }
                
                SendTransactionViewModel.TransactionStep.INPUT_AMOUNT -> {
                    ModernAmountInputScreen(
                        amount = uiState.amount,
                        amountError = uiState.amountError,
                        balance = uiState.balance.toPlainString(),
                        selectedToken = amountStepTokenSymbol(uiState.selectedToken),
                        estimatedFee = uiState.estimatedTotalFee,
                        onAmountChange = viewModel::setAmount,
                        onNext = viewModel::proceedToConfirm,
                        onBack = viewModel::goBack,
                        onMaxClick = viewModel::setMaxAmount
                    )
                }
                
                SendTransactionViewModel.TransactionStep.CONFIRM,
                SendTransactionViewModel.TransactionStep.REVIEWED -> {
                    val snapshot = uiState.confirmedSnapshot
                    if (snapshot == null) {
                        LaunchedEffect(Unit) {
                            viewModel.goBack()
                        }
                    } else {
                        ModernConfirmationScreen(
                            recipientAddress = snapshot.recipient.value,
                            amount = snapshot.humanAmount,
                            estimatedFee = snapshot.totalFee,
                            selectedToken = snapshot.tokenSymbol,
                            isSubmitting = uiState.isSubmitting || uiState.isLoading,
                            onConfirm = { viewModel.proceedToAuthorize() },
                            onBack = viewModel::goBack
                        )
                    }
                }

                SendTransactionViewModel.TransactionStep.MIGRATION_REQUIRED -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val fragmentActivity = context as? FragmentActivity ?: (context as? android.content.ContextWrapper)?.baseContext as? FragmentActivity

                    ModernMigrationPromptScreen(
                        walletName = uiState.activeWallet?.displayName ?: "錢包",
                        error = uiState.error,
                        isLoading = uiState.isLoading,
                        onUpgrade = { password ->
                            if (fragmentActivity != null) {
                                val biometricManager = BiometricManager.from(context)
                                val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                val canAuth = biometricManager.canAuthenticate(authenticators)

                                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                                    viewModel.prepareProvisioning(
                                        onReady = { provisioningReq ->
                                            val executor = ContextCompat.getMainExecutor(context)
                                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                .setTitle("驗證身份以升級錢包")
                                                .setSubtitle("請使用生物識別或手錶密碼進行驗證")
                                                .setAllowedAuthenticators(authenticators)
                                                .build()

                                            val biometricPrompt = BiometricPrompt(
                                                fragmentActivity,
                                                executor,
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                        val handle = AndroidPlatformAuthenticator.issueHandle(
                                                            keyId = provisioningReq.stagedAlias,
                                                            sessionId = provisioningReq.sessionId,
                                                            operation = AuthOperation.IMPORT,
                                                            authenticationResult = result,
                                                            walletId = provisioningReq.sessionId,
                                                            validityDurationMs = 15_000L
                                                        )
                                                        viewModel.onPerformLegacyMigration(
                                                            password = password,
                                                            authContext = AuthenticationContext(authHandle = handle, cryptoObject = result.cryptoObject)
                                                        )
                                                    }

                                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                                            errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                                            errorCode != BiometricPrompt.ERROR_CANCELED
                                                        ) {
                                                            viewModel.onAuthError(errString.toString())
                                                        }
                                                    }

                                                    override fun onAuthenticationFailed() {}
                                                }
                                            )
                                            biometricPrompt.authenticate(promptInfo)
                                        },
                                        onError = { err ->
                                            viewModel.onAuthError(err)
                                        }
                                    )
                                } else {
                                    viewModel.onAuthError("裝置不支援生物識別或未設定螢幕鎖定")
                                }
                            } else {
                                viewModel.onAuthError("Activity 不可用，無法發起身份驗證")
                            }
                        },
                        onCancel = {
                            viewModel.onCancelMigration()
                            onBackClick()
                        }
                    )
                }

                SendTransactionViewModel.TransactionStep.MIGRATING -> {
                    ModernSendingScreen(title = "遷移中...", subtitle = "正在升級錢包至安全 KeyVault")
                }

                SendTransactionViewModel.TransactionStep.AUTH_REQUIRED,
                SendTransactionViewModel.TransactionStep.AUTHENTICATING -> {
                    val snapshot = uiState.confirmedSnapshot
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val fragmentActivity = context as? FragmentActivity

                    LaunchedEffect(uiState.currentStep, snapshot) {
                        if (uiState.currentStep != SendTransactionViewModel.TransactionStep.AUTH_REQUIRED ||
                            snapshot == null
                        ) {
                            return@LaunchedEffect
                        }

                        val emulatorHandle = DebugEmulatorAuth.issueSignHandle(
                            keyId = snapshot.keyAlias,
                            walletId = snapshot.walletId,
                            intentFingerprint = snapshot.signingDigestHex
                        )
                        if (emulatorHandle != null) {
                            viewModel.onBiometricAuthSuccess(emulatorHandle)
                            return@LaunchedEffect
                        }

                        if (fragmentActivity == null) {
                            viewModel.onAuthError("Activity 不可用，無法發起身份驗證")
                            return@LaunchedEffect
                        }

                        val biometricManager = BiometricManager.from(context)
                        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        val canAuth = biometricManager.canAuthenticate(authenticators)
                        val targetKey = snapshot.keyAlias

                        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                            val executor = ContextCompat.getMainExecutor(context)
                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("驗證身份以簽名交易")
                                .setSubtitle("發送 ${snapshot.humanAmount} ${snapshot.tokenSymbol ?: snapshot.chain.name}")
                                .setAllowedAuthenticators(authenticators)
                                .build()

                            val biometricPrompt = BiometricPrompt(
                                fragmentActivity,
                                executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        val handle = AndroidPlatformAuthenticator.issueHandle(
                                            keyId = targetKey,
                                            operation = AuthOperation.SIGN,
                                            authenticationResult = result,
                                            walletId = snapshot.walletId,
                                            intentFingerprint = snapshot.signingDigestHex,
                                            validityDurationMs = 10_000L
                                        )
                                        viewModel.onBiometricAuthSuccess(handle)
                                    }

                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                            errorCode == BiometricPrompt.ERROR_CANCELED
                                        ) {
                                            viewModel.onAuthCancel()
                                        } else {
                                            viewModel.onAuthError(errString.toString())
                                        }
                                    }

                                    override fun onAuthenticationFailed() {
                                        // Retry in prompt
                                    }
                                }
                            )
                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            viewModel.onAuthError("裝置不支援生物識別認證或未設定螢幕鎖定")
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        snapshot?.let { snap ->
                            ModernConfirmationScreen(
                                recipientAddress = snap.recipient.value,
                                amount = snap.humanAmount,
                                estimatedFee = snap.totalFee,
                                selectedToken = snap.tokenSymbol,
                                isSubmitting = true,
                                submittingLabel = "認證中…",
                                onConfirm = {},
                                onBack = { viewModel.onAuthCancel() }
                            )
                        }
                    }
                }
                
                SendTransactionViewModel.TransactionStep.AUTHORIZED,
                SendTransactionViewModel.TransactionStep.SIGNING -> {
                    ModernSendingScreen(title = "簽名中...", subtitle = "正在安全簽署交易")
                }
                
                SendTransactionViewModel.TransactionStep.BROADCASTING,
                SendTransactionViewModel.TransactionStep.SENDING -> {
                    ModernSendingScreen(title = "廣播中...", subtitle = "正在提交至區塊鏈網絡")
                }
                
                SendTransactionViewModel.TransactionStep.SUCCESS -> {
                    ModernSuccessScreen(
                        txHash = uiState.txHash ?: "",
                        onDone = {
                            viewModel.resetTransaction()
                            onBackClick()
                        }
                    )
                }

                SendTransactionViewModel.TransactionStep.AUTH_CANCELLED -> {
                    ModernFailedScreen(
                        error = "授權已取消",
                        onRetry = { viewModel.proceedToConfirm() },
                        onCancel = {
                            viewModel.resetTransaction()
                            onBackClick()
                        }
                    )
                }

                SendTransactionViewModel.TransactionStep.AUTH_EXPIRED -> {
                    ModernFailedScreen(
                        error = "授權已逾時，請重新確認交易",
                        onRetry = { viewModel.proceedToConfirm() },
                        onCancel = {
                            viewModel.resetTransaction()
                            onBackClick()
                        }
                    )
                }
                
                SendTransactionViewModel.TransactionStep.FAILED -> {
                    ModernFailedScreen(
                        error = uiState.error ?: "交易失敗",
                        onRetry = { viewModel.goBack() },
                        onCancel = {
                            viewModel.resetTransaction()
                            onBackClick()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 現代化地址輸入畫面
 */
@Composable
private fun ModernAddressInputScreen(
    address: String,
    addressError: String?,
    onAddressChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onAddressBookClick: () -> Unit,
    showAddressBook: Boolean,
    addressBookContacts: List<AddressContact>,
    addressBookLoading: Boolean,
    onContactSelect: (AddressContact) -> Unit,
    onSearchAddressBook: (String) -> Unit
) {
    if (showAddressBook) {
        ModernAddressBookScreen(
            contacts = addressBookContacts,
            isLoading = addressBookLoading,
            onContactSelect = onContactSelect,
            onSearchChange = onSearchAddressBook,
            onBack = onAddressBookClick
        )
    } else {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = rememberScalingLazyListState()
        ) {
            item {
                // 標題
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    Text(
                        text = "發送到",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(TestTags.SEND_SCREEN_TITLE)
                    )
                    
                    IconButton(
                        onClick = onAddressBookClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPage,
                            contentDescription = "地址簿",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            item {
                // 地址輸入卡片
                Card(
                    onClick = { /* no-op */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "接收地址",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        BasicTextField(
                            value = address,
                            onValueChange = onAddressChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.SEND_ADDRESS_INPUT)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = false,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text
                            )
                        )
                        
                        addressError?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                // 下一步按鈕
                Button(
                    onClick = onNext,
                    enabled = address.isNotEmpty() && addressError == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.SEND_ADDRESS_NEXT_BUTTON)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "下一步",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 現代化金額輸入畫面
 */
@Composable
private fun ModernAmountInputScreen(
    amount: String,
    amountError: String?,
    balance: String,
    selectedToken: String?,
    estimatedFee: String?,
    onAmountChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onMaxClick: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        state = rememberScalingLazyListState()
    ) {
        item {
            // 標題列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Text(
                    text = "金額",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
        
        item {
            // 餘額顯示
            Card(
                onClick = { /* no-op */ },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "可用餘額",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$balance ${selectedToken ?: "ETH"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Button(
                        onClick = onMaxClick,
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "MAX",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        item {
            // 金額輸入
            Card(
                onClick = { /* no-op */ },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.SEND_AMOUNT_INPUT)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        textStyle = MaterialTheme.typography.displaySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = selectedToken ?: "ETH",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    amountError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        item {
            // 手續費顯示
            if (estimatedFee != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "預估手續費",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$estimatedFee ETH",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            // 下一步按鈕
            Button(
                onClick = onNext,
                enabled = amount.isNotEmpty() && amountError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SEND_AMOUNT_NEXT_BUTTON)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "確認",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 現代化確認畫面
 */
@Composable
private fun ModernConfirmationScreen(
    recipientAddress: String,
    amount: String,
    estimatedFee: String,
    selectedToken: String?,
    isSubmitting: Boolean = false,
    submittingLabel: String = "處理中...",
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "確認交易",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        item {
            WearQaFixtureBanner()
        }
        
        item {
            // 交易詳情卡片
            Card(
                onClick = { /* no-op */ },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 發送金額
                    Column {
                        Text(
                            text = "發送",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$amount ${selectedToken ?: "ETH"}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    ThemedDivider()
                    
                    // 接收地址
                    Column {
                        Text(
                            text = "接收地址",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${recipientAddress.take(6)}...${recipientAddress.takeLast(4)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    ThemedDivider()
                    
                    // 手續費
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "網絡手續費",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$estimatedFee ETH",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // 總計
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "總計",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$amount ${selectedToken ?: "ETH"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "+ $estimatedFee ETH",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        item {
            if (isSubmitting) {
                // 認證/處理中狀態指示（取代停用的按鈕列，避免看起來像按鈕壞掉）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = submittingLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 按鈕組
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(text = "返回")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TestTags.SEND_CONFIRM_BUTTON)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "確認發送",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** 主題化分隔線（Wear M3 無 Divider 元件） */
@Composable
private fun ThemedDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    )
}

/**
 * 現代化發送中畫面
 */
@Composable
private fun ModernSendingScreen(
    title: String = "發送中...",
    subtitle: String = "請稍候"
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 4.dp
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 現代化成功畫面
 */
@Composable
private fun ModernSuccessScreen(
    txHash: String,
    onDone: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale.value)
                    .background(
                        SuccessGreen,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
            
            Text(
                text = "交易成功！",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            
            if (txHash.isNotEmpty()) {
                Text(
                    text = "交易哈希",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "${txHash.take(8)}...${txHash.takeLast(6)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "完成",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 現代化失敗畫面
 */
@Composable
private fun ModernFailedScreen(
    error: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(50.dp)
                )
            }
            
            Text(
                text = "交易失敗",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            
            Card(
                onClick = { /* no-op */ },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(text = "取消")
                }
                
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "重試", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 現代化地址簿畫面
 */
@Composable
private fun ModernAddressBookScreen(
    contacts: List<AddressContact>,
    isLoading: Boolean,
    onContactSelect: (AddressContact) -> Unit,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Text(
                    text = "地址簿",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
        
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (contacts.isEmpty()) {
            item {
                Card(
                    onClick = { /* no-op */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暫無聯絡人",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(contacts) { contact ->
                Card(
                    onClick = { onContactSelect(contact) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 頭像
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${contact.address.take(6)}...${contact.address.takeLast(4)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 現代化舊版錢包安全升級提示畫面
 */
@Composable
private fun ModernMigrationPromptScreen(
    walletName: String,
    error: String?,
    isLoading: Boolean,
    onUpgrade: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("migration_prompt_list"),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp)
    ) {
        item {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(28.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "安全性升級",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }

        item {
            Text(
                text = "錢包「$walletName」為舊版格式，需輸入密碼升級至安全 KeyVault 才能發送交易。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (error != null) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (password.isEmpty()) {
                            Text(
                                text = "請輸入錢包密碼",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onUpgrade(password) },
                enabled = password.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "升級並繼續",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
