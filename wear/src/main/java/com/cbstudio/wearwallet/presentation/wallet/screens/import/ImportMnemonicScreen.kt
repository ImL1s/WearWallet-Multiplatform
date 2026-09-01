package com.cbstudio.wearwallet.presentation.wallet.screens.import

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import com.cbstudio.wearwallet.presentation.TestTags
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
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
import com.cbstudio.wearwallet.presentation.components.ErrorCard

/**
 * 助記詞導入畫面 - 完整的 12 詞輸入流程
 *
 * 包含四個步驟：
 * 1. 輸入 12 個助記詞
 * 2. 設置錢包名稱和密碼
 * 3. 導入中...
 * 4. 完成
 */
@Composable
fun ImportMnemonicScreen(
    onNavigateBack: () -> Unit,
    onImportSuccess: () -> Unit,
    viewModel: ImportMnemonicViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity ?: (context as? android.content.ContextWrapper)?.baseContext as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    // FLAG_SECURE 防截圖與防錄影及生命週期管理：在輸入助記詞/密碼時施加 FLAG_SECURE，離開/背景化時清空記憶體
    DisposableEffect(uiState.currentStep, lifecycleOwner, fragmentActivity) {
        if (uiState.currentStep == ImportMnemonicViewModel.ImportStep.INPUT_MNEMONIC ||
            uiState.currentStep == ImportMnemonicViewModel.ImportStep.SET_PASSWORD
        ) {
            fragmentActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (uiState.currentStep == ImportMnemonicViewModel.ImportStep.INPUT_MNEMONIC ||
                    uiState.currentStep == ImportMnemonicViewModel.ImportStep.SET_PASSWORD
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

    // 導入完成後自動導航
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == ImportMnemonicViewModel.ImportStep.COMPLETED) {
            onImportSuccess()
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

    when (uiState.currentStep) {
        ImportMnemonicViewModel.ImportStep.INPUT_MNEMONIC -> {
            InputMnemonicWordsScreen(
                mnemonicWords = uiState.mnemonicWords,
                onWordChange = viewModel::updateMnemonicWord,
                onPaste = viewModel::setMnemonicFromPaste,
                getWordSuggestions = viewModel::getWordSuggestions,
                onNext = viewModel::validateAndProceed,
                onBack = onNavigateBack,
                error = uiState.error
            )
        }
        ImportMnemonicViewModel.ImportStep.SET_PASSWORD -> {
            var password by remember { mutableStateOf("") }
            var confirmPassword by remember { mutableStateOf("") }
            SetPasswordScreen(
                walletName = uiState.walletName,
                password = password,
                confirmPassword = confirmPassword,
                onWalletNameChange = viewModel::setWalletName,
                onPasswordChange = {
                    password = it
                    viewModel.setPassword(it.toCharArray())
                },
                onConfirmPasswordChange = {
                    confirmPassword = it
                    viewModel.setConfirmPassword(it.toCharArray())
                },
                onNext = { triggerBiometricAndImport() },
                onBack = viewModel::backToMnemonicInput,
                error = uiState.error
            )
        }
        ImportMnemonicViewModel.ImportStep.IMPORTING -> {
            ImportingScreen()
        }
        ImportMnemonicViewModel.ImportStep.COMPLETED -> {
            CompletedScreen()
        }
        else -> {
            // 不應該到達這裡
        }
    }
}

/**
 * Step 1: 助記詞輸入畫面
 */
@Composable
private fun InputMnemonicWordsScreen(
    mnemonicWords: List<String>,
    onWordChange: (Int, String) -> Unit,
    onPaste: (String) -> Unit,
    getWordSuggestions: (String) -> List<String>,
    onNext: () -> Unit,
    onBack: () -> Unit,
    error: String?
) {
    val scrollState = rememberScalingLazyListState()
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .testTag(TestTags.MNEMONIC_INPUT_SCREEN)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 24.dp,
                end = 16.dp,
                bottom = 64.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 標題
            item {
                Text(
                    text = "導入錢包",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 說明
            item {
                Text(
                    text = "請依序輸入 12 個助記詞",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB3B3B3),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 🆕 貼上按鈕
            item {
                OutlinedButton(
                    onClick = {
                        clipboardManager.getText()?.text?.let { text ->
                            onPaste(text)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .testTag(TestTags.PASTE_MNEMONIC_BUTTON)
                        .semantics {
                            contentDescription = "貼上助記詞"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("貼上助記詞", style = MaterialTheme.typography.labelMedium)
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }


            // 12 個助記詞輸入框
            items(12) { index ->
                MnemonicInputField(
                    number = index + 1,
                    word = mnemonicWords.getOrElse(index) { "" },
                    onWordChange = { onWordChange(index, it) },
                    getWordSuggestions = getWordSuggestions
                )
            }

            // 錯誤訊息
            if (error != null) {
                item {
                    ErrorCard(
                        message = error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 下一步按鈕
            item {
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(TestTags.NEXT_BUTTON)
                        .semantics {
                            contentDescription = "下一步，進入密碼設置"
                            role = Role.Button
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("下一步", style = MaterialTheme.typography.labelLarge)
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 返回按鈕
            item {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "返回上一步"
                            role = Role.Button
                        }
                ) {
                    Text("返回", style = MaterialTheme.typography.labelLarge)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/**
 * 單個助記詞輸入框組件 - 支持自動完成
 *
 * 符合 Wear OS 無障礙標準：
 * - 最小觸控目標 48dp
 * - contentDescription 語義標籤
 * - editableText 可編輯文字標記
 */
@Composable
private fun MnemonicInputField(
    number: Int,
    word: String,
    onWordChange: (String) -> Unit,
    getWordSuggestions: (String) -> List<String>
) {
    var showSuggestions by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val focusManager = LocalFocusManager.current

    
    // 當輸入變化時更新建議
    LaunchedEffect(word) {
        suggestions = if (word.length >= 2) {
            getWordSuggestions(word)
        } else {
            emptyList()
        }
        showSuggestions = suggestions.isNotEmpty() && !Bip39SuggestionProvider.isValidWord(word)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(Color(0xFF2C2C2C), MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 編號
                Text(
                    text = "$number.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(22.dp)
                )

                // 輸入框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = word,
                        onValueChange = { newValue ->
                            onWordChange(newValue.lowercase().trim())
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.MNEMONIC_WORD_INPUT_PREFIX + number)
                            .semantics {
                                contentDescription = "助記詞第 $number 個單字"
                            },
                        decorationBox = { innerTextField ->
                            if (word.isEmpty()) {
                                Text(
                                    text = "輸入",
                                    style = TextStyle(
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                
                // 有效單詞指示器
                if (Bip39SuggestionProvider.isValidWord(word)) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "有效的 BIP39 單詞",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // 自動完成建議列表
        if (showSuggestions && suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                suggestions.take(3).forEach { suggestion ->
                    FilledTonalButton(
                        onClick = {
                            onWordChange(suggestion)
                            showSuggestions = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = suggestion },
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Step 2: 設置錢包名稱和密碼
 */
@Composable
private fun SetPasswordScreen(
    walletName: String,
    password: String,
    confirmPassword: String,
    onWalletNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    error: String?
) {
    val scrollState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .testTag(TestTags.PASSWORD_SCREEN)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 標題
            item {
                Text(
                    text = "設置密碼",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 錢包名稱
            item {
                Card(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2C)
                    ),
                    enabled = false
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "錢包名稱（可選）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            BasicTextField(
                                value = walletName,
                                onValueChange = onWalletNameChange,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.WALLET_NAME_INPUT),
                                decorationBox = { innerTextField ->
                                    if (walletName.isEmpty()) {
                                        Text(
                                            text = "我的錢包",
                                            style = TextStyle(
                                                color = Color.Gray,
                                                fontSize = 14.sp
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

            // 密碼
            item {
                Card(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2C)
                    ),
                    enabled = false
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "密碼",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            BasicTextField(
                                value = password,
                                onValueChange = onPasswordChange,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.PASSWORD_INPUT),
                                decorationBox = { innerTextField ->
                                    if (password.isEmpty()) {
                                        Text(
                                            text = "至少 6 個字符",
                                            style = TextStyle(
                                                color = Color.Gray,
                                                fontSize = 14.sp
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
                Card(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2C)
                    ),
                    enabled = false
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "確認密碼",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            BasicTextField(
                                value = confirmPassword,
                                onValueChange = onConfirmPasswordChange,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.CONFIRM_PASSWORD_INPUT),
                                decorationBox = { innerTextField ->
                                    if (confirmPassword.isEmpty()) {
                                        Text(
                                            text = "再次輸入密碼",
                                            style = TextStyle(
                                                color = Color.Gray,
                                                fontSize = 14.sp
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
            if (error != null) {
                item {
                    ErrorCard(
                        message = error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 確認按鈕
            item {
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(TestTags.CONFIRM_BUTTON)
                        .semantics {
                            contentDescription = "確認導入錢包"
                            role = Role.Button
                        },
                    enabled = password.isNotEmpty() && confirmPassword.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("確認", style = MaterialTheme.typography.labelLarge)
                }
            }

            // 返回按鈕
            item {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "返回上一步"
                            role = Role.Button
                        }
                ) {
                    Text("返回", style = MaterialTheme.typography.labelLarge)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/**
 * Step 3: 導入中...
 */
@Composable
private fun ImportingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .testTag(TestTags.IMPORTING_SCREEN),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "正在導入錢包"
                    },
                strokeWidth = 4.dp
            )
            Text(
                text = "正在導入錢包...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

/**
 * Step 4: 完成
 */
@Composable
private fun CompletedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .testTag(TestTags.IMPORT_SUCCESS_SCREEN),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "完成",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "錢包導入成功！",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

// ViewModel 已移到獨立文件 ImportMnemonicViewModel.kt
