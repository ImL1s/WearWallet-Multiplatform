package com.cbstudio.wearwallet.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.cbstudio.wearwallet.presentation.theme.WearWalletTheme
import kotlinx.coroutines.launch

/**
 * Push Protocol 設置嚮導畫面
 * 
 * 引導用戶完成：
 * 1. 了解 Push Protocol 功能
 * 2. 檢查 PUSH token 餘額
 * 3. 創建通知頻道
 * 4. 配置通知偏好
 */
@Composable
fun PushProtocolSetupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTokenPurchase: () -> Unit,
    viewModel: PushProtocolSetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colors.surface,
                        MaterialTheme.colors.surface.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        when (uiState.currentStep) {
            SetupStep.INTRODUCTION -> {
                IntroductionStep(
                    onContinue = { viewModel.moveToNextStep() },
                    onSkip = onNavigateBack
                )
            }
            
            SetupStep.CHECK_BALANCE -> {
                CheckBalanceStep(
                    pushBalance = uiState.pushTokenBalance,
                    requiredAmount = uiState.requiredTokenAmount,
                    onCheckBalance = { viewModel.checkPushTokenBalance() },
                    onPurchaseTokens = onNavigateToTokenPurchase,
                    onContinue = { viewModel.moveToNextStep() },
                    onBack = { viewModel.moveToPreviousStep() }
                )
            }
            
            SetupStep.CREATE_CHANNEL -> {
                CreateChannelStep(
                    isCreating = uiState.isCreatingChannel,
                    channelAddress = uiState.channelAddress,
                    onCreate = { privateKey ->
                        coroutineScope.launch {
                            viewModel.createChannel(privateKey)
                        }
                    },
                    onContinue = { viewModel.moveToNextStep() },
                    onBack = { viewModel.moveToPreviousStep() }
                )
            }
            
            SetupStep.CONFIGURE_NOTIFICATIONS -> {
                ConfigureNotificationsStep(
                    priceAlerts = uiState.priceAlertsEnabled,
                    transactionAlerts = uiState.transactionAlertsEnabled,
                    securityAlerts = uiState.securityAlertsEnabled,
                    defiAlerts = uiState.defiAlertsEnabled,
                    onTogglePriceAlerts = { viewModel.togglePriceAlerts() },
                    onToggleTransactionAlerts = { viewModel.toggleTransactionAlerts() },
                    onToggleSecurityAlerts = { viewModel.toggleSecurityAlerts() },
                    onToggleDefiAlerts = { viewModel.toggleDefiAlerts() },
                    onComplete = {
                        coroutineScope.launch {
                            viewModel.completeSetup()
                            onNavigateBack()
                        }
                    },
                    onBack = { viewModel.moveToPreviousStep() }
                )
            }
            
            SetupStep.COMPLETED -> {
                CompletedStep(
                    channelAddress = uiState.channelAddress,
                    onDone = onNavigateBack
                )
            }
        }
        
        // 進度指示器
        if (uiState.currentStep != SetupStep.COMPLETED) {
            SetupProgressIndicator(
                currentStep = uiState.currentStep,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        // 錯誤對話框
        uiState.errorMessage?.let { error ->
            Dialog(
                showDialog = true,
                onDismissRequest = { viewModel.clearError() }
            ) {
                Alert(
                    title = { Text("錯誤") },
                    negativeButton = {
                        Button(
                            onClick = { viewModel.clearError() },
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Text("確定")
                        }
                    },
                    positiveButton = {},
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) }
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun IntroductionStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colors.primary
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Text(
                text = "Push Protocol",
                style = MaterialTheme.typography.title2,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Text(
                text = "去中心化通知系統",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    FeatureItem(
                        icon = Icons.Default.TrendingUp,
                        title = "價格提醒",
                        description = "代幣價格變動通知"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureItem(
                        icon = Icons.Default.SwapHoriz,
                        title = "交易通知",
                        description = "即時交易狀態更新"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureItem(
                        icon = Icons.Default.Security,
                        title = "安全警報",
                        description = "異常活動即時提醒"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureItem(
                        icon = Icons.Default.AccountBalance,
                        title = "DeFi 活動",
                        description = "流動性挖礦收益通知"
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        item {
            Chip(
                onClick = onContinue,
                label = { Text("開始設置") },
                icon = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                colors = ChipDefaults.primaryChipColors()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Chip(
                onClick = onSkip,
                label = { Text("稍後設置") },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@Composable
private fun CheckBalanceStep(
    pushBalance: String,
    requiredAmount: Int,
    onCheckBalance: () -> Unit,
    onPurchaseTokens: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val hasEnoughTokens = pushBalance.toDoubleOrNull()?.let { it >= requiredAmount } ?: false
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (hasEnoughTokens) Color.Green else MaterialTheme.colors.primary
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Text(
                text = "PUSH Token 餘額",
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (pushBalance == "0") "檢查中..." else "$pushBalance PUSH",
                        style = MaterialTheme.typography.title2,
                        color = if (hasEnoughTokens) Color.Green else MaterialTheme.colors.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "需要 $requiredAmount PUSH",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    
                    if (!hasEnoughTokens && pushBalance != "0") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "餘額不足",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        if (pushBalance == "0") {
            item {
                Chip(
                    onClick = onCheckBalance,
                    label = { Text("檢查餘額") },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        } else if (!hasEnoughTokens) {
            item {
                Chip(
                    onClick = onPurchaseTokens,
                    label = { Text("購買 PUSH") },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                Chip(
                    onClick = onCheckBalance,
                    label = { Text("重新檢查") },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        } else {
            item {
                Chip(
                    onClick = onContinue,
                    label = { Text("繼續") },
                    icon = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Chip(
                onClick = onBack,
                label = { Text("返回") },
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@Composable
private fun CreateChannelStep(
    isCreating: Boolean,
    channelAddress: String?,
    onCreate: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    var privateKey by remember { mutableStateOf("") }
    var showPrivateKeyDialog by remember { mutableStateOf(false) }
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = if (channelAddress != null) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (channelAddress != null) Color.Green else MaterialTheme.colors.primary
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Text(
                text = if (channelAddress != null) "頻道已創建" else "創建通知頻道",
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (channelAddress != null) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                Card(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "頻道地址",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${channelAddress.take(6)}...${channelAddress.takeLast(4)}",
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            item {
                Chip(
                    onClick = onContinue,
                    label = { Text("繼續配置") },
                    icon = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                Card(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "注意事項",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• 需要 50 PUSH tokens",
                            style = MaterialTheme.typography.caption3
                        )
                        Text(
                            text = "• 頻道創建後不可刪除",
                            style = MaterialTheme.typography.caption3
                        )
                        Text(
                            text = "• 私鑰將安全存儲",
                            style = MaterialTheme.typography.caption3
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            item {
                Chip(
                    onClick = { showPrivateKeyDialog = true },
                    label = { 
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("創建頻道")
                        }
                    },
                    icon = { 
                        if (!isCreating) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    },
                    enabled = !isCreating,
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Chip(
                onClick = onBack,
                label = { Text("返回") },
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                enabled = !isCreating,
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
    
    // 私鑰輸入對話框
    if (showPrivateKeyDialog) {
        Dialog(
            showDialog = true,
            onDismissRequest = { showPrivateKeyDialog = false }
        ) {
            Alert(
                title = { Text("輸入私鑰") },
                negativeButton = {
                    Button(
                        onClick = { showPrivateKeyDialog = false },
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text("取消")
                    }
                },
                positiveButton = {
                    Button(
                        onClick = {
                            onCreate(privateKey)
                            showPrivateKeyDialog = false
                        },
                        enabled = privateKey.isNotBlank()
                    ) {
                        Text("確認")
                    }
                },
                icon = { Icon(Icons.Default.Key, contentDescription = null) }
            ) {
                Text(
                    text = "請輸入用於創建 Push Protocol 頻道的私鑰。此私鑰將被安全加密存儲。",
                    style = MaterialTheme.typography.caption2
                )
            }
        }
    }
}

@Composable
private fun ConfigureNotificationsStep(
    priceAlerts: Boolean,
    transactionAlerts: Boolean,
    securityAlerts: Boolean,
    defiAlerts: Boolean,
    onTogglePriceAlerts: () -> Unit,
    onToggleTransactionAlerts: () -> Unit,
    onToggleSecurityAlerts: () -> Unit,
    onToggleDefiAlerts: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colors.primary
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Text(
                text = "通知偏好設置",
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            ToggleChip(
                checked = priceAlerts,
                onCheckedChange = { onTogglePriceAlerts() },
                label = { Text("價格提醒") },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(priceAlerts),
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            ToggleChip(
                checked = transactionAlerts,
                onCheckedChange = { onToggleTransactionAlerts() },
                label = { Text("交易通知") },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(transactionAlerts),
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            ToggleChip(
                checked = securityAlerts,
                onCheckedChange = { onToggleSecurityAlerts() },
                label = { Text("安全警報") },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(securityAlerts),
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            ToggleChip(
                checked = defiAlerts,
                onCheckedChange = { onToggleDefiAlerts() },
                label = { Text("DeFi 活動") },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(defiAlerts),
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        item {
            Chip(
                onClick = onComplete,
                label = { Text("完成設置") },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                colors = ChipDefaults.primaryChipColors()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Chip(
                onClick = onBack,
                label = { Text("返回") },
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@Composable
private fun CompletedStep(
    channelAddress: String?,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Green
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "設置完成！",
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Push Protocol 通知系統已啟用",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        
        if (channelAddress != null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "頻道地址",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${channelAddress.take(8)}...${channelAddress.takeLast(6)}",
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Chip(
            onClick = onDone,
            label = { Text("完成") },
            icon = { Icon(Icons.Default.Done, contentDescription = null) },
            colors = ChipDefaults.primaryChipColors()
        )
    }
}

@Composable
private fun SetupProgressIndicator(
    currentStep: SetupStep,
    modifier: Modifier = Modifier
) {
    val progress = when (currentStep) {
        SetupStep.INTRODUCTION -> 0.2f
        SetupStep.CHECK_BALANCE -> 0.4f
        SetupStep.CREATE_CHANNEL -> 0.6f
        SetupStep.CONFIGURE_NOTIFICATIONS -> 0.8f
        SetupStep.COMPLETED -> 1.0f
    }
    
    // Use a simple progress bar with Box
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.1f), MaterialTheme.shapes.small)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colors.primary, MaterialTheme.shapes.small)
        )
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colors.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.caption2,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

/**
 * 設置步驟枚舉
 */
enum class SetupStep {
    INTRODUCTION,
    CHECK_BALANCE,
    CREATE_CHANNEL,
    CONFIGURE_NOTIFICATIONS,
    COMPLETED
}
