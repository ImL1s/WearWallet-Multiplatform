package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
import java.text.DecimalFormat
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.presentation.TestTags

/**
 * 發送交易主畫面 - 重新設計的美觀 UI
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0F0F1E)
                    )
                )
            )
    ) {
        // 進度指示器
        if (uiState.currentStep != SendTransactionViewModel.TransactionStep.SUCCESS &&
            uiState.currentStep != SendTransactionViewModel.TransactionStep.FAILED &&
            uiState.currentStep != SendTransactionViewModel.TransactionStep.AUTH_CANCELLED &&
            uiState.currentStep != SendTransactionViewModel.TransactionStep.AUTH_EXPIRED) {
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }
        
        // 主要內容
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() with
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
                        selectedToken = null,
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
                        if (uiState.currentStep == SendTransactionViewModel.TransactionStep.AUTH_REQUIRED &&
                            fragmentActivity != null &&
                            snapshot != null
                        ) {
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
                        } else if (fragmentActivity == null && uiState.currentStep == SendTransactionViewModel.TransactionStep.AUTH_REQUIRED) {
                            viewModel.onAuthError("Activity 不可用，無法發起身份驗證")
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
                                onConfirm = {},
                                onBack = {}
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
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        text = "發送到",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(TestTags.SEND_SCREEN_TITLE)
                    )
                    
                    IconButton(
                        onClick = onAddressBookClick,
                        modifier = Modifier.size(40.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
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
                                    Color.Black.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White
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
                                style = MaterialTheme.typography.labelSmall,
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
                    ),
                    shape = RoundedCornerShape(24.dp)
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "金額",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
        
        item {
            // 餘額顯示
            Card(
                onClick = { /* no-op */ },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "可用餘額",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$balance ${selectedToken ?: "ETH"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Button(
                        onClick = onMaxClick,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "MAX",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        item {
            // 金額輸入
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.SEND_AMOUNT_INPUT)
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            color = Color.White,
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
                            style = MaterialTheme.typography.labelSmall,
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
                            Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "預估手續費",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "$estimatedFee ETH",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
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
                ),
                shape = RoundedCornerShape(24.dp)
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
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        
        item {
            // 交易詳情卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 發送金額
                    Column {
                        Text(
                            text = "發送",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "$amount ${selectedToken ?: "ETH"}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    
                    // 接收地址
                    Column {
                        Text(
                            text = "接收地址",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "${recipientAddress.take(6)}...${recipientAddress.takeLast(4)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    
                    // 手續費
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "網絡手續費",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "$estimatedFee ETH",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "+ $estimatedFee ETH",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
        
        item {
            // 按鈕組
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBack,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        disabledContainerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "返回",
                        color = Color.White
                    )
                }
                
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SEND_CONFIRM_BUTTON)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = if (isSubmitting) "處理中..." else "確認發送",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 現代化發送中畫面
 */
@Composable
private fun ModernSendingScreen(
    title: String = "發送中...",
    subtitle: String = "請稍候"
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
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
                    .scale(scale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
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
                        Color(0xFF4CAF50),
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
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            if (txHash.isNotEmpty()) {
                Text(
                    text = "交易哈希",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
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
                ),
                shape = RoundedCornerShape(24.dp)
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
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("取消", color = Color.White)
                }
                
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("重試", fontWeight = FontWeight.Bold)
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
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0F0F1E)
                    )
                )
            ),
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "地址簿",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(40.dp))
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
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (contacts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.05f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暫無聯絡人",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
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
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${contact.address.take(6)}...${contact.address.takeLast(4)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
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
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(28.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "安全性升級",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        item {
            Text(
                text = "錢包「$walletName」為舊版格式，需輸入密碼升級至安全 KeyVault 才能發送交易。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B0C0),
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
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A3E))
                    .border(1.dp, Color(0xFF3F3F5A), RoundedCornerShape(12.dp))
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
                                color = Color(0xFF707088),
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
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
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
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB0B0C0)
                )
            }
        }
    }
}