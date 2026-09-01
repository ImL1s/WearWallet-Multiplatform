package com.cbstudio.wearwallet.presentation.wallet.screens.swap.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.material.*
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.components.TokenRow

/**
 * Select Token Screen for Wear OS
 * 
 * Displays available tokens with icons and prices from Rango Metadata
 */
@Composable
fun SelectTokenScreen(
    title: String,
    tokens: List<RangoTokenMeta>,
    selectedChain: String? = null,
    onTokenSelected: (RangoTokenMeta) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val filteredTokens = if (selectedChain != null) {
        tokens.filter { it.blockchain.equals(selectedChain, ignoreCase = true) }
    } else {
        tokens
    }
    
    // Group tokens by chain for better organization
    val groupedTokens = filteredTokens.groupBy { it.blockchain }
    
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(com.cbstudio.wearwallet.presentation.TestTags.SWAP_TOKEN_LIST),
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 16.dp,
            start = 8.dp,
            end = 8.dp
        ),
        anchorType = ScalingLazyListAnchorType.ItemStart
    ) {
        // Header
        item {
            Text(
                text = "Select $title",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
        
        // Tokens grouped by chain
        groupedTokens.forEach { (chain, chainTokens) ->
            // Chain header (if multiple chains)
            if (groupedTokens.size > 1) {
                item {
                    Text(
                        text = chain,
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                    )
                }
            }
            
            // Token items
            items(chainTokens.size) { index ->
                val token = chainTokens[index]
                TokenRow(
                    token = token,
                    onClick = { onTokenSelected(token) },
                    showPrice = true,
                    showChain = groupedTokens.size == 1, // Show chain only if single group
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag(com.cbstudio.wearwallet.presentation.TestTags.SWAP_TOKEN_ROW)
                )
            }
        }
        
        // Empty state
        if (filteredTokens.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tokens available",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Chain Selection Screen
 */
@Composable
fun SelectChainScreen(
    chains: List<String>,
    onChainSelected: (String) -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 16.dp,
            start = 8.dp,
            end = 8.dp
        )
    ) {
        item {
            Text(
                text = "Select Chain",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
        
        items(chains.size) { index ->
            val chain = chains[index]
            Chip(
                onClick = { onChainSelected(chain) },
                label = {
                    Text(
                        text = chain,
                        style = MaterialTheme.typography.button
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = ChipDefaults.chipColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)
                )
            )
        }
    }
}
