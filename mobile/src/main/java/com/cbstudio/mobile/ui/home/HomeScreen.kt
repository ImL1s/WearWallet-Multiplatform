package com.cbstudio.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Watch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cbstudio.mobile.R
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

@Composable
fun HomeScreen(
    onNavigateToNotificationSettings: () -> Unit,
    onNavigateToAddressBook: () -> Unit,
    onNavigateToNftWatchFaceConfig: () -> Unit,
    onNavigateToQrReceive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var connectedNodes by remember { mutableStateOf<List<Node>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            connectedNodes = nodes
        } catch (_: Exception) {
            connectedNodes = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 標題區域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.intro_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.intro_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        
        // 連接狀態卡片
        ConnectionStatusCard(
            isLoading = isLoading,
            connectedNodes = connectedNodes
        )
        
        Spacer(Modifier.height(24.dp))
        
        // 功能卡片區域
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                FilledTonalButton(
                    onClick = onNavigateToNotificationSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notification_settings_btn"),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.notification_settings),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                FilledTonalButton(
                    onClick = onNavigateToAddressBook,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_book_btn"),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.address_book),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // NFT 錶盤配置按鈕
                FilledTonalButton(
                    onClick = onNavigateToNftWatchFaceConfig,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(
                        Icons.Default.Watch,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nft_watchface_config),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                FilledTonalButton(
                    onClick = onNavigateToQrReceive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_receive_btn"),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.show_qr_receive),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 其他設定按鈕（未來擴充用）
                OutlinedButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    enabled = false
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.general_settings),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        // 使用說明
        Text(
            text = stringResource(R.string.usage_instruction),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    isLoading: Boolean,
    connectedNodes: List<Node>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLoading -> MaterialTheme.colorScheme.surface
                connectedNodes.isNotEmpty() -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = when {
                            isLoading -> MaterialTheme.colorScheme.outline
                            connectedNodes.isNotEmpty() -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.error
                        },
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.connection_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isLoading -> MaterialTheme.colorScheme.onSurface
                        connectedNodes.isNotEmpty() -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Text(
                    text = when {
                        isLoading -> stringResource(R.string.checking_connection)
                        connectedNodes.isNotEmpty() -> stringResource(
                            R.string.connected_devices,
                            connectedNodes.size
                        )
                        else -> stringResource(R.string.no_connected_devices)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isLoading -> MaterialTheme.colorScheme.onSurface
                        connectedNodes.isNotEmpty() -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }
    }
}

@Composable
fun MobileReceiveScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Mock data for now - in real implementation, get from wallet repository
    val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = stringResource(R.string.qr_receive_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("qr_receive_title")
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // QR Code
        Card(
            modifier = Modifier.size(280.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for QR Code - replace with actual QR Code generation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = Color.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "QR Code Placeholder",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Address display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.wallet_address),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = walletAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Copy button
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Wallet Address", walletAddress)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.address_copied), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.copy_address))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Info text
        Text(
            text = stringResource(R.string.share_qr_address_instruction),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
