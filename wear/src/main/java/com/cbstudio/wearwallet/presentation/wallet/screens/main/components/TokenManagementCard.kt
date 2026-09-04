package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.cbstudio.wearwallet.presentation.theme.ErrorRed
import com.cbstudio.wearwallet.presentation.theme.SuccessGreen
import java.math.BigDecimal

/**
 * Token management card for wallet main screen
 * Provides quick access to token list and management features
 */
@Composable
fun TokenManagementCard(
    onNavigateToTokenList: () -> Unit,
    enabled: Boolean = true,
    tokenCount: Int = 0,
    totalValue: BigDecimal? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { if (enabled) onNavigateToTokenList() },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = Icons.Outlined.Token,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Title and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "代幣管理",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (tokenCount > 0) "$tokenCount 個代幣" else "查看代幣",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Value (if any)
            totalValue?.let { value ->
                Text(
                    text = "$${"%.0f".format(value)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = SuccessGreen
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Quick token actions card
 * Provides quick access to common token operations
 */
@Composable
fun QuickTokenActionsCard(
    onSwapClick: () -> Unit,
    onBridgeClick: () -> Unit,
    onStakeClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swap button
            QuickActionButton(
                icon = "🔄",
                label = "交換",
                onClick = onSwapClick,
                enabled = enabled,
                color = Color(0xFF2196F3)
            )
            
            // Bridge button
            QuickActionButton(
                icon = "🌉",
                label = "跨鏈",
                onClick = onBridgeClick,
                enabled = enabled,
                color = Color(0xFF9C27B0)
            )
            
            // Stake button
            QuickActionButton(
                icon = "💎",
                label = "質押",
                onClick = onStakeClick,
                enabled = enabled,
                color = Color(0xFFFF9800)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    CompactButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.2f),
            contentColor = color
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = icon,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Popular tokens quick view
 * Shows top tokens by value
 */
@Composable
fun PopularTokensCard(
    tokens: List<TokenQuickInfo>,
    onTokenClick: (TokenQuickInfo) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onViewAllClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = "熱門代幣，查看所有熱門代幣"
                role = Role.Button
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "熱門代幣",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Show top 3 tokens
            tokens.take(3).forEach { token ->
                TokenQuickItem(
                    token = token,
                    onClick = { onTokenClick(token) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // View all button
            if (tokens.size > 3) {
                Text(
                    text = "查看全部 (${tokens.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TokenQuickItem(
    token: TokenQuickInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Token info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = token.symbol,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = token.balance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Value
        Text(
            text = token.value,
            style = MaterialTheme.typography.labelSmall,
            color = if (token.isPositive) SuccessGreen else ErrorRed
        )
    }
}

/**
 * Data class for quick token info
 */
data class TokenQuickInfo(
    val symbol: String,
    val balance: String,
    val value: String,
    val isPositive: Boolean = true
)