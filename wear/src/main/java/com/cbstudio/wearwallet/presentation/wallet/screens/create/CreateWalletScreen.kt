package com.cbstudio.wearwallet.presentation.wallet.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.presentation.TestTags
import com.cbstudio.wearwallet.presentation.wallet.components.PasswordInputDialog

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cbstudio.wearwallet.core.security.AndroidPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.presentation.util.isEmulatorDevice
import com.cbstudio.wearwallet.presentation.util.DebugEmulatorAuth
import kotlinx.coroutines.delay

/**
 * 創建錢包畫面
 * 
 * 引導用戶完成錢包創建流程 (P1-4: Ephemeral UI, FLAG_SECURE, lifecycle wiping)
 */
@Composable
fun CreateWalletScreen(
    onNavigateBack: () -> Unit,
    onWalletCreated: () -> Unit,
    viewModel: CreateWalletViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity ?: (context as? android.content.ContextWrapper)?.baseContext as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    // FLAG_SECURE 防截圖與防錄影及生命週期管理：顯示助記詞時施加 FLAG_SECURE，離開/背景化時清空記憶體助記詞
    DisposableEffect(uiState.currentStep, lifecycleOwner, fragmentActivity) {
        if (uiState.currentStep == CreateWalletViewModel.CreationStep.SHOW_MNEMONIC) {
            fragmentActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (uiState.currentStep == CreateWalletViewModel.CreationStep.SHOW_MNEMONIC) {
                    viewModel.wipeEphemeralMnemonic()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            fragmentActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 當錢包創建完成時導航到主畫面
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == CreateWalletViewModel.CreationStep.COMPLETED) {
            onWalletCreated()
        }
    }

    val triggerBiometricAndCreate: () -> Unit = {
        if (DebugEmulatorAuth.canUse()) {
            viewModel.prepareProvisioning(
                onReady = { provisioningReq ->
                    val handle = DebugEmulatorAuth.issueImportHandle(
                        keyId = provisioningReq.stagedAlias,
                        sessionId = provisioningReq.sessionId
                    )
                    if (handle != null) {
                        viewModel.confirmPasswordAndCreate(AuthenticationContext(authHandle = handle))
                    } else {
                        viewModel.onAuthError("Emulator auth bypass failed")
                    }
                },
                onError = { err -> viewModel.onAuthError(err) }
            )
        } else if (fragmentActivity != null) {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuth = biometricManager.canAuthenticate(authenticators)

            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                viewModel.prepareProvisioning(
                    onReady = { provisioningReq ->
                        val executor = ContextCompat.getMainExecutor(context)
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("驗證身份以創建錢包")
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
                                    viewModel.confirmPasswordAndCreate(AuthenticationContext(authHandle = handle, cryptoObject = result.cryptoObject))
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                        errorCode != BiometricPrompt.ERROR_CANCELED
                                    ) {
                                        viewModel.onAuthError(errString.toString())
                                    }
                                }

                                override fun onAuthenticationFailed() {
                                    // 提示重試
                                }
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
    }

    LaunchedEffect(uiState.currentStep) {
        if (!isEmulatorDevice()) return@LaunchedEffect
        when (uiState.currentStep) {
            CreateWalletViewModel.CreationStep.SHOW_WARNING -> {
                delay(400)
                viewModel.acknowledgeWarning()
            }
            CreateWalletViewModel.CreationStep.SHOW_MNEMONIC,
            CreateWalletViewModel.CreationStep.CONFIRM_BACKUP -> {
                delay(800)
                viewModel.confirmBackup()
            }
            else -> Unit
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        when (uiState.currentStep) {
            CreateWalletViewModel.CreationStep.INITIAL -> {
                InitialScreen(
                    onStartCreation = { name -> viewModel.startWalletCreation(name) },
                    onNavigateBack = onNavigateBack
                )
            }
            
            CreateWalletViewModel.CreationStep.PASSWORD_INPUT -> {
                var password by remember { mutableStateOf("") }
                var confirmPassword by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    if (isEmulatorDevice()) {
                        val demoPassword = "123456"
                        password = demoPassword
                        confirmPassword = demoPassword
                        viewModel.setPassword(demoPassword.toCharArray())
                        viewModel.setConfirmPassword(demoPassword.toCharArray())
                        delay(600)
                        triggerBiometricAndCreate()
                    }
                }
                PasswordInputStep(
                    walletName = uiState.walletName,
                    password = password,
                    confirmPassword = confirmPassword,
                    error = uiState.error,
                    onPasswordChange = {
                        password = it
                        viewModel.setPassword(it.toCharArray())
                    },
                    onConfirmPasswordChange = {
                        confirmPassword = it
                        viewModel.setConfirmPassword(it.toCharArray())
                    },
                    onConfirm = { triggerBiometricAndCreate() },
                    onBack = { viewModel.backToPasswordInput() }
                )
            }
            
            CreateWalletViewModel.CreationStep.GENERATING -> {
                GeneratingScreen()
            }
            
            CreateWalletViewModel.CreationStep.SHOW_WARNING -> {
                SafetyWarningScreen(
                    onAcknowledge = { viewModel.acknowledgeWarning() }
                )
            }
            
            CreateWalletViewModel.CreationStep.SHOW_MNEMONIC -> {
                MnemonicDisplayScreen(
                    mnemonicHolder = uiState.mnemonicHolder,
                    onConfirmBackup = { viewModel.confirmBackup() }
                )
            }
            
            CreateWalletViewModel.CreationStep.CONFIRM_BACKUP -> {
                ConfirmBackupScreen(
                    onConfirm = { viewModel.confirmBackup() }
                )
            }
            
            CreateWalletViewModel.CreationStep.COMPLETED -> {
                CompletedScreen()
            }
        }
        
        // 錯誤對話框
        uiState.error?.let { errorMessage ->
            AlertDialog(
                visible = true,
                onDismissRequest = { viewModel.clearError() },
                title = { Text("錯誤") },
                text = { Text(errorMessage) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("確定")
                    }
                }
            )
        }
    }
}

@Composable
private fun InitialScreen(
    onStartCreation: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var walletName by remember { mutableStateOf(if (isEmulatorDevice()) "Demo Wallet" else "") }
    val scrollState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        if (isEmulatorDevice() && walletName.isNotBlank()) {
            delay(500)
            onStartCreation(walletName)
        }
    }
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        horizontalAlignment = Alignment.CenterHorizontally,
        autoCentering = null,
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 64.dp,
            start = 16.dp,
            end = 16.dp
        ),
        anchorType = ScalingLazyListAnchorType.ItemStart
    ) {
        item {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        item {
            Text(
                text = "創建新錢包",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }
        
        // 錢包名稱輸入
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "錢包名稱",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
                
                Card(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = walletName,
                            onValueChange = { walletName = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (walletName.isEmpty()) {
                                    Text(
                                        text = "輸入名稱",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.WALLET_NAME_INPUT)
                        )
                    }
                }
            }
        }
        
        // 按鈕組
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onStartCreation(walletName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.START_CREATION_BUTTON),
                ) {
                    Text("開始創建")
                }

                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("返回")
                }
            }
        }
    }
}

@Composable
private fun GeneratingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "正在生成錢包...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun SafetyWarningScreen(
    onAcknowledge: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.SAFETY_WARNING_SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFFFAB00)
            )
        }
        
        item {
            Text(
                text = "⚠️ 請妥善保管",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFFFAB00),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        item {
            Card(
                onClick = { /* No action needed for informational card */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2C)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "助記詞是您錢包的唯一憑證",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 請將助記詞抄寫在紙上",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 不要截圖或拍照保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 不要分享給任何人",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 遺失將無法恢復",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6B6B)
                    )
                }
            }
        }
        
        item {
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .testTag(TestTags.ACKNOWLEDGE_WARNING_BUTTON),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFAB00)
                )
            ) {
                Text("我已了解", color = Color.Black)
            }
        }
    }
}

@Composable
private fun MnemonicDisplayScreen(
    mnemonicHolder: com.cbstudio.wearwallet.core.security.EphemeralMnemonicHolder?,
    onConfirmBackup: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    val mnemonic = mnemonicHolder?.getWords() ?: emptyList()

    ScalingLazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.MNEMONIC_DISPLAY_SCREEN),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(
                text = "您的助記詞",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // 顯示助記詞 - 每行 2 個，更緊湊
        mnemonic.chunked(2).forEachIndexed { rowIndex, words ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    words.forEachIndexed { colIndex, word ->
                        val wordNumber = rowIndex * 2 + colIndex + 1
                        MnemonicWordCard(
                            number = wordNumber,
                            word = word,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(TestTags.MNEMONIC_WORD_TEXT)
                        )
                    }
                    // 如果是奇數個詞，最後一行補空白
                    if (words.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Text(
                text = "請按順序抄寫並妥善保管",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        item {
            Button(
                onClick = onConfirmBackup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag(TestTags.CONFIRM_BACKUP_BUTTON)
            ) {
                Text("我已備份")
            }
        }
    }
}

@Composable
private fun MnemonicWordCard(
    number: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { /* No action needed for word card */ },
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2C)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "$number.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(18.dp)
            )
            Text(
                text = word,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ConfirmBackupScreen(
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF4CAF50)
            )
            Text(
                text = "請確認已備份",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("確認")
            }
        }
    }
}

@Composable
private fun PasswordInputStep(
    walletName: String,
    password: String,
    confirmPassword: String,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TestTags.CREATE_WALLET_SCREEN),
        state = scrollState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "設置密碼",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        
        item {
            Text(
                text = walletName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 密碼輸入
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "密碼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
                
                Card(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        BasicTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.PASSWORD_INPUT),
                            decorationBox = { innerTextField ->
                                if (password.isEmpty()) {
                                    Text(
                                        text = "輸入密碼",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }
        }
        
        // 確認密碼
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "確認密碼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
                
                Card(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        BasicTextField(
                            value = confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.CONFIRM_PASSWORD_INPUT),
                            decorationBox = { innerTextField ->
                                if (confirmPassword.isEmpty()) {
                                    Text(
                                        text = "再次輸入",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }
        }
        
        // 錯誤訊息
        error?.let {
            item {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 按鈕
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {


                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.CONFIRM_BUTTON),
                    enabled = password.isNotEmpty() && confirmPassword.isNotEmpty()
                ) {
                    Text("確認")
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("返回")
                }
                
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
        
        item {
            Text(
                text = "密碼至少需要6個字符",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CompletedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.COMPLETED_SCREEN),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF4CAF50)
            )
            Text(
                text = "錢包創建成功！",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "正在進入主畫面...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}