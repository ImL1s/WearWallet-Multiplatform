package com.cbstudio.wearwallet.presentation.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import com.cbstudio.wearwallet.core.security.RiskLevel
import com.cbstudio.wearwallet.core.security.SecurityReport

/**
 * 設備安全警告畫面
 *
 * 當檢測到設備可能已被破解（Root/Jailbreak）時顯示警告
 * 使用全屏畫面代替對話框，更適合 Wear OS
 *
 * @param securityReport 安全檢測報告
 * @param onDismiss 用戶選擇繼續使用時的回調
 * @param onForceExit 用戶選擇退出應用時的回調
 *
 * @author WearWallet Security Team
 * @since 2025-10-28
 */
@Composable
fun SecurityWarningDialog(
    securityReport: SecurityReport,
    onDismiss: () -> Unit,
    onForceExit: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 標題和圖標
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 40.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = stringResource(com.cbstudio.wearwallet.R.string.security_warning_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // 風險等級
        item {
            RiskLevelChip(riskLevel = securityReport.riskLevel)
        }

        // 警告訊息
        item {
            Text(
                text = stringResource(com.cbstudio.wearwallet.R.string.security_breach_detected),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 檢測到的問題數量
        item {
            Text(
                text = stringResource(com.cbstudio.wearwallet.R.string.security_triggered_checks, securityReport.detectedCount),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 建議
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = securityReport.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 退出按鈕
        item {
            Button(
                onClick = onForceExit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(com.cbstudio.wearwallet.R.string.exit_app),
                    fontSize = 14.sp
                )
            }
        }

        // 繼續使用按鈕
        item {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(com.cbstudio.wearwallet.R.string.accept_risk_continue),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 免責聲明
        item {
            Text(
                text = stringResource(com.cbstudio.wearwallet.R.string.security_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 風險等級標籤
 *
 * @param riskLevel 風險等級
 */
@Composable
private fun RiskLevelChip(riskLevel: RiskLevel) {
    val backgroundColor = when (riskLevel) {
        RiskLevel.SAFE -> Color(0xFF4CAF50)
        RiskLevel.LOW -> Color(0xFFFFC107)
        RiskLevel.MEDIUM -> Color(0xFFFF9800)
        RiskLevel.HIGH -> Color(0xFFFF5722)
        RiskLevel.CRITICAL -> Color(0xFFF44336)
    }

    val emoji = when (riskLevel) {
        RiskLevel.SAFE -> "✅"
        RiskLevel.LOW -> "⚠️"
        RiskLevel.MEDIUM -> "⚠️"
        RiskLevel.HIGH -> "🚨"
        RiskLevel.CRITICAL -> "🚨"
    }

    Surface(
        modifier = Modifier.padding(vertical = 4.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 16.sp
            )
            Text(
                text = riskLevel.getDisplayName(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * 檢測詳情畫面
 *
 * 顯示所有檢測結果的詳細信息
 *
 * @param securityReport 安全檢測報告
 * @param onDismiss 關閉畫面的回調
 */
@Composable
fun SecurityDetailsScreen(
    securityReport: SecurityReport,
    onDismiss: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 標題
        item {
            Text(
                text = stringResource(com.cbstudio.wearwallet.R.string.detection_details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 風險等級
        item {
            RiskLevelChip(riskLevel = securityReport.riskLevel)
        }

        // 檢測到的問題
        if (securityReport.detectedIssues.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(com.cbstudio.wearwallet.R.string.detected_issues),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(securityReport.detectedIssues) { issue ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "• $issue",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        } else {
            item {
                Text(
                    text = "✅ " + stringResource(com.cbstudio.wearwallet.R.string.no_issues_detected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // 建議
        item {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "建議:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = securityReport.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 關閉按鈕
        item {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(com.cbstudio.wearwallet.R.string.close))
            }
        }
    }
}
