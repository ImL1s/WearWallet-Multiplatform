// TODO: This file depends on NftWatchFaceConfigurationViewModel which needs implementation
// Temporarily commented out to allow project compilation
/*
package com.cbstudio.mobile.ui.nftwatchface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cbstudio.mobile.R
import com.cbstudio.wearwallet.core.domain.model.nft.NftToken
import com.cbstudio.wearwallet.core.domain.model.nft.NftDisplayMode
import com.cbstudio.wearwallet.core.domain.model.nft.NftItem

/**
 * NFT 錶盤配置主頁面
 * 提供完整的 NFT 收藏瀏覽、選擇和配置功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NftWatchFaceConfigurationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NftWatchFaceConfigurationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.nft_watchface_config),
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.refreshNftCollection() }
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh_collection)
                    )
                }
            }
        )
        
        // Main Content
        when {
            uiState.isLoading -> {
                LoadingContent()
            }
            uiState.error != null -> {
                ErrorContent(
                    error = uiState.error,
                    onRetry = { viewModel.retryLoading() }
                )
            }
            else -> {
                MainContent(
                    uiState = uiState,
                    onNftSelected = viewModel::selectNft,
                    onSettingsChanged = viewModel::updateSettings,
                    onSaveConfiguration = viewModel::saveConfiguration
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading_nfts),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = error ?: "Unknown error",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            FilledTonalButton(
                onClick = onRetry
            ) {
                Text(stringResource(R.string.refresh_collection))
            }
        }
    }
}

@Composable
private fun MainContent(
    uiState: NftWatchFaceConfigurationUiState,
    onNftSelected: (NftItem) -> Unit,
    onSettingsChanged: (NftWatchFaceConfigurationUiState) -> Unit,
    onSaveConfiguration: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Configuration Toggle and Preview
            ConfigurationSection(
                uiState = uiState,
                onSettingsChanged = onSettingsChanged
            )
        }
        
        if (uiState.isEnabled) {
            item {
                // Display Settings
                DisplaySettingsSection(
                    uiState = uiState,
                    onSettingsChanged = onSettingsChanged
                )
            }
            
            item {
                // NFT Collection Grid
                NftCollectionSection(
                    nfts = uiState.nftCollection,
                    selectedNft = uiState.selectedNft,
                    onNftSelected = onNftSelected
                )
            }
            
            item {
                // Save Button
                Button(
                    onClick = onSaveConfiguration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 32.dp),
                    enabled = uiState.selectedNft != null
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save_configuration))
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSection(
    uiState: NftWatchFaceConfigurationUiState,
    onSettingsChanged: (NftWatchFaceConfigurationUiState) -> Unit
) {
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
                text = stringResource(R.string.nft_watchface_config_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.enable_nft_complication),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = uiState.isEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(uiState.copy(isEnabled = enabled))
                    }
                )
            }
            
            if (uiState.isEnabled && uiState.selectedNft != null) {
                Spacer(modifier = Modifier.height(16.dp))
                WatchFacePreview(
                    nft = uiState.selectedNft,
                    displayMode = uiState.displayMode
                )
            }
        }
    }
}

@Composable
private fun DisplaySettingsSection(
    uiState: NftWatchFaceConfigurationUiState,
    onSettingsChanged: (NftWatchFaceConfigurationUiState) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.display_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Display Mode Selection
            Text(
                text = stringResource(R.string.display_mode),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            NftDisplayMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.displayMode == mode,
                        onClick = {
                            onSettingsChanged(uiState.copy(displayMode = mode))
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (mode) {
                            NftDisplayMode.IMAGE_ONLY -> stringResource(R.string.image_only)
                            NftDisplayMode.IMAGE_WITH_NAME -> stringResource(R.string.image_with_name)
                            NftDisplayMode.IMAGE_WITH_TOKEN_ID -> stringResource(R.string.image_with_token_id)
                            NftDisplayMode.IMAGE_WITH_VALUE -> stringResource(R.string.image_with_value)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NftCollectionSection(
    nfts: List<NftItem>,
    selectedNft: NftItem?,
    onNftSelected: (NftItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.nft_collection),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (nfts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_nfts_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(400.dp)
                ) {
                    items(nfts) { nft ->
                        NftGridItem(
                            nft = nft,
                            isSelected = selectedNft?.getUniqueId() == nft.getUniqueId(),
                            onSelected = { onNftSelected(nft) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NftGridItem(
    nft: NftItem,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        onClick = onSelected,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(nft.getWatchFaceImageUrl())
                    .crossfade(true)
                    .build(),
                contentDescription = nft.getDisplayName(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = nft.getDisplayName(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Text(
                text = nft.collectionName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun WatchFacePreview(
    nft: NftItem,
    displayMode: NftDisplayMode
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Mock watch face preview
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(nft.getWatchFaceImageUrl())
                        .crossfade(true)
                        .build(),
                    contentDescription = nft.getDisplayName(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Overlay text based on display mode
                if (displayMode != NftDisplayMode.IMAGE_ONLY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = when (displayMode) {
                                    NftDisplayMode.IMAGE_WITH_NAME -> nft.getDisplayName()
                                    NftDisplayMode.IMAGE_WITH_TOKEN_ID -> "#${nft.tokenId}"
                                    NftDisplayMode.IMAGE_WITH_VALUE -> nft.estimatedValueUsd?.let { "$${it.toInt()}" } ?: "N/A"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}*/
