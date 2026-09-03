package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.core.security.AndroidPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import com.cbstudio.wearwallet.presentation.wallet.components.PasswordInputDialog
import org.koin.androidx.compose.koinViewModel

/**
 * 顯示助記詞畫面
 * 安全地顯示錢包的恢復短語 (Milestone 4 / P1-5 Hardening: FLAG_SECURE, 30s timer, ephemeral memory)
 */
@Composable
fun ShowMnemonicScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShowMnemonicViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context.findActivity()
    val fragmentActivity = activity as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    // FLAG_SECURE 防截圖與防錄影及生命週期管理：畫面處於前景時嚴格施加 FLAG_SECURE，離開時移除並清空記憶體助記詞
    DisposableEffect(activity, lifecycleOwner) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            viewModel.clearMnemonic()
        }
    }

    val triggerBiometricAndReveal: (String) -> Unit = { password ->
        val wallet = uiState.activeWallet
        if (wallet != null && fragmentActivity != null) {
            val targetKey = wallet.keyAlias ?: wallet.address
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuth = biometricManager.canAuthenticate(authenticators)

            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                val executor = ContextCompat.getMainExecutor(context)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("驗證身份以顯示助記詞")
                    .setSubtitle("請使用生物識別或手錶密碼進行驗證")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                val biometricPrompt = BiometricPrompt(
                    fragmentActivity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val handle = AndroidPlatformAuthenticator.issueHandle(
                                keyId = targetKey,
                                operation = AuthOperation.REVEAL,
                                authenticationResult = result,
                                walletId = wallet.id,
                                intentFingerprint = wallet.address,
                                validityDurationMs = 10_000L
                            )
                            viewModel.onAuthSuccess(handle, password)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_CANCELED
                            ) {
                                viewModel.onAuthCancelled()
                            } else {
                                viewModel.onAuthError(errString.toString())
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // 提示重試
                        }
                    }
                )
                biometricPrompt.authenticate(promptInfo)
            } else {
                viewModel.onAuthError("裝置不支援生物識別或未設定螢幕鎖定")
            }
        } else if (fragmentActivity == null) {
            viewModel.onAuthError("Activity 不可用，無法發起身份驗證")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        when {
            // 顯示警告
            uiState.showWarning -> {
                WarningScreen(
                    onAccept = { viewModel.acceptWarning() },
                    onDecline = onNavigateBack
                )
            }

            // 逾時狀態
            uiState.status == RevealStatus.EXPIRED -> {
                ExpiredScreen(
                    onReAuthenticate = { triggerBiometricAndReveal("") },
                    onBack = onNavigateBack
                )
            }

            // 顯示助記詞
            uiState.isRevealed && uiState.mnemonicWords != null -> {
                MnemonicDisplayScreen(
                    mnemonic = uiState.mnemonicWords ?: emptyList(),
                    remainingSeconds = uiState.remainingSeconds,
                    onBack = {
                        viewModel.hideMnemonic()
                        onNavigateBack()
                    }
                )
            }

            // 需要驗證
            uiState.requiresPassword && !uiState.isRevealed -> {
                PasswordPromptScreen(
                    onAuthenticate = { triggerBiometricAndReveal("") },
                    onRequestPassword = { showPasswordDialog = true },
                    onBack = onNavigateBack,
                    error = uiState.error
                )
            }

            // 載入中
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // 錯誤狀態
            else -> {
                ErrorScreen(
                    error = uiState.error ?: "未知錯誤",
                    onBack = onNavigateBack
                )
            }
        }

        // 密碼輸入對話框
        PasswordInputDialog(
            title = "輸入密碼",
            message = "需要密碼才能顯示助記詞",
            isVisible = showPasswordDialog,
            onPasswordSubmit = { password ->
                showPasswordDialog = false
                triggerBiometricAndReveal(password)
            },
            onDismiss = {
                showPasswordDialog = false
            },
            error = uiState.error,
            isLoading = uiState.isLoading
        )
    }
}

@Composable
private fun PasswordPromptScreen(
    onAuthenticate: () -> Unit,
    onRequestPassword: () -> Unit,
    onBack: () -> Unit,
    error: String?
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 24.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            }
        }

        item {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 16.dp)
            )
        }

        item {
            Text(
                text = "需要驗證",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Text(
                text = "顯示助記詞需要身份驗證",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        error?.let {
            item {
                Card(
                    onClick = { /* no-op */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Button(
                onClick = onAuthenticate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("生物識別驗證")
            }
        }

        item {
            TextButton(
                onClick = onRequestPassword,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text(
                    text = "使用錢包密碼驗證",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun WarningScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 24.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 16.dp)
            )
        }

        item {
            Text(
                text = "安全警告",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Text(
                text = "助記詞是您錢包的唯一恢復方式",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        item {
            Text(
                text = "請確保周圍無人且環境安全",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        item {
            Text(
                text = "系統已啟用防截圖保護",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("我了解風險")
            }
        }

        item {
            TextButton(
                onClick = onDecline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun ExpiredScreen(
    onReAuthenticate: () -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 24.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = Icons.Default.TimerOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 16.dp)
            )
        }

        item {
            Text(
                text = "顯示已逾時",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Text(
                text = "為確保安全性，30 秒倒數結束後已自動清空記憶體中的助記詞",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Button(
                onClick = onReAuthenticate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新驗證查看")
            }
        }

        item {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("返回設定")
            }
        }
    }
}

@Composable
private fun MnemonicDisplayScreen(
    mnemonic: List<String>,
    remainingSeconds: Int,
    onBack: () -> Unit
) {
    val scrollState = androidx.wear.compose.foundation.lazy.rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        anchorType = ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 48.dp,
            start = 8.dp,
            end = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 頂部導航欄與倒數指示器
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "助記詞",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        // 30 秒自動清空倒數指示卡片
        item {
            Card(
                onClick = { /* no-op */ },
                colors = CardDefaults.cardColors(
                    containerColor = if (remainingSeconds <= 10) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (remainingSeconds <= 10) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "剩餘 ${remainingSeconds} 秒自動清空",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remainingSeconds <= 10) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 警告卡片
        item {
            Card(
                onClick = { /* no-op */ },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "請勿拍照或抄漏",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 助記詞標題
        item {
            Text(
                text = "您的 ${mnemonic.size} 個助記詞",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // 助記詞網格 - 每行2個（優化圓形螢幕顯示）
        mnemonic.chunked(2).forEachIndexed { rowIndex, rowWords ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    rowWords.forEachIndexed { colIndex, word ->
                        val wordIndex = rowIndex * 2 + colIndex + 1
                        MnemonicWordItem(
                            index = wordIndex,
                            word = word,
                            modifier = Modifier
                                .width(76.dp)
                                .padding(horizontal = 3.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 提示文字
        item {
            Card(
                onClick = { /* no-op */ },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2C)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ 重要提醒",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "請按順序抄寫在紙上",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "並妥善保管在安全的地方",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 完成按鈕
        item {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("完成查看")
            }
        }
    }
}

@Composable
private fun MnemonicWordItem(
    index: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { /* no-op */ },
        modifier = modifier.padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    error: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}