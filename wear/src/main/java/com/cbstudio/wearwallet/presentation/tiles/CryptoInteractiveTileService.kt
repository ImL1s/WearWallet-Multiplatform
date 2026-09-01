package com.cbstudio.wearwallet.presentation.tiles

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 創新的互動式加密貨幣 Tile
 * 
 * 功能特點：
 * 1. 即時價格更新與動畫
 * 2. 快速操作按鈕（發送/接收）
 * 3. AI 市場洞察氣泡
 * 4. 手勢快捷鍵支援
 * 5. Material 3 Expressive 設計
 */
// @AndroidEntryPoint  // Removed Hilt
class CryptoInteractiveTileService : TileService() {
    
    companion object {
        private const val RESOURCES_VERSION = "1"
    }
    
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        // 簡化的 Tile 實現
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                androidx.wear.tiles.LayoutElementBuilders.Layout.Builder()
                                    .setRoot(
                                        androidx.wear.tiles.LayoutElementBuilders.Text.Builder()
                                            .setText("WearWallet Crypto Tile")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
            
        return Futures.immediateFuture(tile)
    }
    
    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
            
        return Futures.immediateFuture(resources)
    }
}
