package com.cbstudio.wearwallet.presentation.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.cbstudio.wearwallet.R

/**
 * AI 助手畫面 - 連接到 coreKmp
 */
@Composable
fun AIAssistantScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AIAssistantViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = PaddingValues(
                top = 24.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = 40.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.ai_voice_assistant),
                    style = MaterialTheme.typography.title2,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 顯示 AI 回應
            if (uiState.aiResponse.isNotEmpty()) {
                item {
                    AppCard(
                        onClick = {},
                        appName = {},
                        time = {},
                        title = {},
                        modifier = Modifier.fillMaxWidth(),
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = MaterialTheme.colors.surface,
                            endBackgroundColor = MaterialTheme.colors.surface
                        )
                    ) {
                        Text(
                            text = uiState.aiResponse,
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                }
            }
            
            // 顯示語音輸入
            if (uiState.voiceInput.isNotEmpty()) {
                item {
                    AppCard(
                        onClick = {},
                        appName = {},
                        time = {},
                        title = {},
                        modifier = Modifier.fillMaxWidth(),
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = MaterialTheme.colors.primary,
                            endBackgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Text(
                            text = uiState.voiceInput,
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                }
            }
            
            // 語音輸入按鈕
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isListening) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            indicatorColor = MaterialTheme.colors.primary
                        )
                    } else {
                        Button(
                            onClick = { viewModel.startListening() },
                            modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
                            enabled = !uiState.isProcessing
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = stringResource(R.string.voice_send_crypto),
                                modifier = Modifier.size(ButtonDefaults.LargeIconSize)
                            )
                        }
                    }
                }
            }
            
            // 快速操作建議
            item {
                Text(
                    text = stringResource(R.string.quick_commands),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip(
                        onClick = { viewModel.processVoiceInput(context.getString(R.string.ai_command_check_balance)) },
                        label = { Text(stringResource(R.string.ai_suggestion_check_balance), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                    Chip(
                        onClick = { viewModel.processVoiceInput(context.getString(R.string.ai_command_send_tx)) },
                        label = { Text(stringResource(R.string.ai_suggestion_send_eth), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip(
                        onClick = { viewModel.processVoiceInput(context.getString(R.string.ai_command_check_price)) },
                        label = { Text(stringResource(R.string.ai_suggestion_check_price), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                    Chip(
                        onClick = { viewModel.processVoiceInput(context.getString(R.string.ai_command_tx_history)) },
                        label = { Text(stringResource(R.string.ai_suggestion_view_history), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            // 返回按鈕作為列表項，確保在圓形螢幕底部安全區域
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CompactButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        }
    }
}