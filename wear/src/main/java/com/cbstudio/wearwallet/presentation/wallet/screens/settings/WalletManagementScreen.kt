package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.core.security.AndroidPlatformAuthenticator
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import org.koin.androidx.compose.koinViewModel

/**
 * 錢包管理畫面
 * 管理多個錢包、切換活動錢包、創建/刪除錢包
 */
@Composable
fun WalletManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToKeystoneConnect: () -> Unit = {},
    onNavigateToCreateWallet: () -> Unit = {},
    onNavigateToImportWallet: () -> Unit = {},
    viewModel: WalletManagementViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newWalletName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity

    // 生命週期監聽：切換至背景時取消進行中的刪除認證 (Fail-Closed)
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

    // 生物識別刪除授權提示
    LaunchedEffect(uiState.deleteStep, uiState.walletToDelete) {
        if (uiState.isDeleteAuthRequired && uiState.walletToDelete != null && fragmentActivity != null) {
            val wallet = uiState.walletToDelete!!
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuth = biometricManager.canAuthenticate(authenticators)
            val targetKey = wallet.keyAlias ?: wallet.address

            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                val executor = ContextCompat.getMainExecutor(context)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("驗證身份以刪除錢包")
                    .setSubtitle("刪除錢包「${wallet.name}」")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                val biometricPrompt = BiometricPrompt(
                    fragmentActivity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val handle = AndroidPlatformAuthenticator.issueHandle(
                                keyId = targetKey,
                                operation = AuthOperation.DELETE,
                                authenticationResult = result,
                                walletId = wallet.id,
                                intentFingerprint = wallet.address,
                                validityDurationMs = 10_000L
                            )
                            viewModel.onBiometricAuthSuccess(
                                AuthenticationContext(
                                    authHandle = handle,
                                    cryptoObject = result.cryptoObject
                                )
                            )
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_CANCELED
                            ) {
                                viewModel.onDeleteAuthCancelled()
                            } else {
                                viewModel.onDeleteAuthError(errString.toString())
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // Retry in prompt
                        }
                    }
                )
                biometricPrompt.authenticate(promptInfo)
            } else {
                viewModel.onDeleteAuthError("裝置不支援生物識別認證或未設定螢幕鎖定")
            }
        } else if (fragmentActivity == null && uiState.isDeleteAuthRequired) {
            viewModel.onDeleteAuthError("Activity 不可用，無法發起身份驗證")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            anchorType = ScalingLazyListAnchorType.ItemStart,
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 60.dp
            )
        ) {
            // 標題
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Text(
                        text = "錢包管理",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("add_wallet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增錢包",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // 錢包列表
            if (uiState.wallets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "尚無錢包",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.wallets) { wallet ->
                    WalletItem(
                        wallet = wallet,
                        isActive = wallet.id == uiState.activeWallet?.id,
                        onSelect = { viewModel.switchWallet(wallet) },
                        onDelete = { viewModel.showDeleteWalletDialog(wallet) }
                    )
                }
            }
            
            // Keystone 硬體錢包連接按鈕
            item {
                Card(
                    onClick = onNavigateToKeystoneConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hardware,
                                contentDescription = "硬體錢包",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "連接 Keystone",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "連接硬體錢包",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // 載入狀態
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // 錯誤提示
            uiState.error?.let { error ->
                item {
                    Card(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "關閉",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 創建錢包對話框（簡化版）
        if (showCreateDialog) {
            // 錢包選項對話框
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    onClick = { /* no-op */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "選擇操作",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // 創建熱錢包按鈕
                        Button(
                            onClick = {
                                showCreateDialog = false
                                onNavigateToCreateWallet()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_hot_wallet_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("創建熱錢包")
                        }
                        
                        // 導入助記詞按鈕
                        Button(
                            onClick = {
                                showCreateDialog = false
                                onNavigateToImportWallet()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("導入助記詞")
                        }
                        
                        // 連接硬體錢包按鈕
                        Button(
                            onClick = {
                                showCreateDialog = false
                                onNavigateToKeystoneConnect()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hardware,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("連接 Keystone")
                        }
                        
                        // 取消按鈕
                        TextButton(
                            onClick = {
                                showCreateDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("取消")
                        }
                    }
                }
            }
        }
        
        // 刪除確認對話框（簡化版）
        if (uiState.showDeleteDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    onClick = { /* no-op */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.isDeleteAuthRequired) "身份驗證" else "確認刪除",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.isDeleteAuthRequired) "請驗證身份以確認刪除「${uiState.walletToDelete?.name}」" else "確定要刪除錢包「${uiState.walletToDelete?.name}」嗎？",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "此操作無法復原",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.isDeleteAuthRequired) {
                                Button(
                                    onClick = {
                                        uiState.walletToDelete?.let { viewModel.requestDeleteWallet(it) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("驗證")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.confirmDeleteWallet(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("刪除")
                                }
                            }
                            Button(
                                onClick = { viewModel.hideDeleteWalletDialog() }
                            ) {
                                Text("取消")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletItem(
    wallet: com.cbstudio.wearwallet.core.domain.model.WalletAccount,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = if (isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()  // 使用預設顏色
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 錢包圖標
            Icon(
                imageVector = if (wallet.isHardwareWallet) {
                    Icons.Default.Security
                } else {
                    Icons.Default.AccountBalanceWallet
                },
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 錢包信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "活動錢包",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Text(
                    text = "${wallet.address.take(6)}...${wallet.address.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = wallet.chainType.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // 刪除按鈕（非活動錢包才顯示）
            if (!isActive) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "刪除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}