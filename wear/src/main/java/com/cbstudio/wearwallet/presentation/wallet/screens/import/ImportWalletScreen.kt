package com.cbstudio.wearwallet.presentation.wallet.screens.import
import timber.log.Timber


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.cbstudio.wearwallet.core.domain.model.ChainType

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.cbstudio.wearwallet.core.security.AndroidPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 導入錢包畫面 - 連接到 coreKmp
 */
@Composable
fun ImportWalletScreen(
    onNavigateBack: () -> Unit,
    onWalletImported: () -> Unit,
    onNavigateToMnemonicImport: () -> Unit,
    viewModel: ImportWalletViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity ?: (context as? android.content.ContextWrapper)?.baseContext as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    // FLAG_SECURE 防截圖與防錄影及生命週期管理：在輸入助記詞/私鑰/密碼時施加 FLAG_SECURE，離開/背景化時清空記憶體
    DisposableEffect(uiState.currentStep, lifecycleOwner, fragmentActivity) {
        if (uiState.currentStep == ImportWalletViewModel.ImportStep.INPUT_DATA ||
            uiState.currentStep == ImportWalletViewModel.ImportStep.INPUT_MNEMONIC ||
            uiState.currentStep == ImportWalletViewModel.ImportStep.SET_PASSWORD ||
            uiState.currentStep == ImportWalletViewModel.ImportStep.PASSWORD_INPUT
        ) {
            fragmentActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (uiState.currentStep == ImportWalletViewModel.ImportStep.INPUT_DATA ||
                    uiState.currentStep == ImportWalletViewModel.ImportStep.INPUT_MNEMONIC ||
                    uiState.currentStep == ImportWalletViewModel.ImportStep.SET_PASSWORD ||
                    uiState.currentStep == ImportWalletViewModel.ImportStep.PASSWORD_INPUT
                ) {
                    viewModel.wipeEphemeralSecrets()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            fragmentActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 當錢包導入完成時導航
    LaunchedEffect(uiState.walletImported) {
        if (uiState.walletImported) {
            onWalletImported()
        }
    }

    val triggerBiometricAndImport: () -> Unit = {
        if (fragmentActivity != null) {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuth = biometricManager.canAuthenticate(authenticators)

            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                viewModel.prepareProvisioning(
                    onReady = { provisioningReq ->
                        val executor = ContextCompat.getMainExecutor(context)
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("驗證身份以導入錢包")
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
                                    viewModel.importWallet(AuthenticationContext(authHandle = handle, cryptoObject = result.cryptoObject))
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
    
    // 根據當前步驟顯示不同內容
    when (uiState.currentStep) {
        ImportWalletViewModel.ImportStep.SELECT_TYPE -> {
            SelectImportTypeScreen(
                onSelectMnemonic = onNavigateToMnemonicImport,
                onSelectPrivateKey = { viewModel.selectImportType(ImportWalletViewModel.ImportType.PRIVATE_KEY) },
                onNavigateBack = onNavigateBack
            )
        }
        ImportWalletViewModel.ImportStep.INPUT_DATA -> {
            Timber.d("ImportWalletScreen: Showing INPUT_DATA")
            InputDataScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { viewModel.resetImportType() }
            )
        }
        ImportWalletViewModel.ImportStep.INPUT_MNEMONIC -> {
            // 使用與 INPUT_DATA 相同的畫面
            InputDataScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { viewModel.resetImportType() }
            )
        }
        ImportWalletViewModel.ImportStep.SET_PASSWORD,
        ImportWalletViewModel.ImportStep.PASSWORD_INPUT -> {
            Timber.d("ImportWalletScreen: Showing PASSWORD_INPUT")
            PasswordInputScreen(
                error = uiState.error,
                onPasswordChange = { viewModel.setPassword(it.toCharArray()) },
                onConfirmPasswordChange = { viewModel.setConfirmPassword(it.toCharArray()) },
                onConfirm = { triggerBiometricAndImport() },
                onBack = { viewModel.resetImportType() }
            )
        }
        ImportWalletViewModel.ImportStep.IMPORTING -> {
            ImportingScreen()
        }
        ImportWalletViewModel.ImportStep.COMPLETED -> {
            // 由 LaunchedEffect 處理導航
        }
    }
}

@Composable
private fun SelectImportTypeScreen(
    onSelectMnemonic: () -> Unit,
    onSelectPrivateKey: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        anchorType = ScalingLazyListAnchorType.ItemStart,
        autoCentering = null,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 標題
        item {
            Text(
                text = "導入錢包",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        
        // 導入方式選擇
        item {
            TitleCard(
                onClick = onSelectMnemonic,
                title = { Text("助記詞導入") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.IMPORT_TYPE_MNEMONIC)
            ) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        item {
            TitleCard(
                onClick = onSelectPrivateKey,
                title = { Text("私鑰導入") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        item {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun InputDataScreen(
    uiState: ImportWalletViewModel.ImportWalletUiState,
    viewModel: ImportWalletViewModel,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    var input by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        anchorType = ScalingLazyListAnchorType.ItemStart,
        autoCentering = null,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 64.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "導入錢包",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        if (uiState.importType != null) {
            // 顯示導入類型
            item {
                Text(
                    text = when (uiState.importType) {
                        ImportWalletViewModel.ImportType.MNEMONIC -> "輸入助記詞"
                        ImportWalletViewModel.ImportType.PRIVATE_KEY -> "輸入私鑰"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            // 輸入框 - 使用 Compose 局部暫態狀態，離開畫面即刻被 GC 回收
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                viewModel.updateInput(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                                .testTag(TestTags.MNEMONIC_TEXT_FIELD),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            visualTransformation = if (uiState.importType == ImportWalletViewModel.ImportType.PRIVATE_KEY) {
                                VisualTransformation.None // PasswordVisualTransformation() CRASHES EMULATOR
                            } else {
                                VisualTransformation.None
                            },
                            decorationBox = { innerTextField ->
                                if (input.isEmpty()) {
                                    Text(
                                        text = "點擊輸入...",
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

            if (uiState.importType == ImportWalletViewModel.ImportType.PRIVATE_KEY) {
                item {
                    OutlinedButton(
                        onClick = {
                            val pasted = clipboardManager.getText()?.text?.trim().orEmpty()
                            if (pasted.isEmpty()) {
                                viewModel.notifyClipboardEmpty()
                            } else {
                                input = pasted
                                viewModel.updateInput(pasted)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("貼上私鑰")
                    }
                }
            }
            
            // 輸入提示
            item {
                Text(
                    text = when (uiState.importType) {
                        ImportWalletViewModel.ImportType.MNEMONIC -> "請輸入12或24個助記詞，用空格分隔"
                        ImportWalletViewModel.ImportType.PRIVATE_KEY -> "請輸入私鑰（64個十六進制字符）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 鏈選擇
            item {
                Text(
                    text = "選擇區塊鏈",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            
            item {
                TitleCard(
                    onClick = { viewModel.toggleChainSelection() },
                    title = { Text(uiState.selectedChain.name) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "▼",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // 錢包名稱
            item {
                Text(
                    text = "錢包名稱（可選）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            
            // 使用 ViewModel 狀態確保滾動時不丟失
            item {
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
                            value = uiState.walletName,
                            onValueChange = { viewModel.updateWalletName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (uiState.walletName.isEmpty()) {
                                    Text(
                                        text = "輸入名稱",
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
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 按鈕組
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { 
                            Timber.d("ImportWalletScreen: InputData NEXT_BUTTON clicked. Validating...")
                            viewModel.proceedToPasswordInput() 
                        },
                        enabled = !uiState.isLoading && uiState.inputValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.NEXT_BUTTON)
                    ) {
                        Text("下一步")
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
        
        // 錯誤訊息
        uiState.error?.let { error ->
            item {
                Card(
                    onClick = { viewModel.clearError() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordInputScreen(
    error: String?,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        anchorType = ScalingLazyListAnchorType.ItemStart,
        autoCentering = null,
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
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        BasicTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                onPasswordChange(it)
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            visualTransformation = VisualTransformation.None, // PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
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
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        BasicTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                onConfirmPasswordChange(it)
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            visualTransformation = VisualTransformation.None, // PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
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
private fun ImportingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = "正在導入錢包...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}