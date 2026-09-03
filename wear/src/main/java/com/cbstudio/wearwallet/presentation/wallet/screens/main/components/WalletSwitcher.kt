package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.*
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.presentation.theme.ColdWalletBlue
import com.cbstudio.wearwallet.presentation.theme.HotWalletOrange

@Composable
fun WalletSwitcher(
    currentWallet: WalletAccount?,
    walletCount: Int,
    onManageWallets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onManageWallets,
        modifier = modifier
            .fillMaxWidth()
            .testTag("wallet_switcher"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        if (currentWallet != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 錢包類型圖示
                    Icon(
                        imageVector = when (currentWallet.walletType) {
                            WalletType.HOT_WALLET -> Icons.Outlined.LocalFireDepartment
                            WalletType.KEYSTONE_COLD -> Icons.Outlined.AcUnit
                            else -> Icons.Outlined.AccountBalance
                        },
                        contentDescription = null,
                        tint = when (currentWallet.walletType) {
                            WalletType.HOT_WALLET -> HotWalletOrange
                            WalletType.KEYSTONE_COLD -> ColdWalletBlue
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = currentWallet.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${currentWallet.address.take(6)}...${currentWallet.address.takeLast(4)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (walletCount > 1) {
                        Text(
                            text = "$walletCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.wallet_management),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            // 沒有錢包的狀態
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_wallet),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
