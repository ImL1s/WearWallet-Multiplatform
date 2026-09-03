package com.cbstudio.wearwallet.core.platform

import com.cbstudio.wearwallet.core.common.Result

/**
 * 跨平台刪除清理勾子介面 (Platform Deletion Cleanup Hook)
 * 負責在刪除錢包時清理 Android / Wear OS 或 iOS / watchOS 專屬的子系統（WorkManager、Tiles、Complications、背景同步等）。
 */
interface PlatformDeletionCleanupHook {
    suspend fun cancelWorkManagerJobs(walletId: Long): Result<Unit>
    suspend fun cancelBackgroundSync(walletId: Long): Result<Unit>
    suspend fun invalidateTiles(): Result<Unit>
    suspend fun invalidateComplications(): Result<Unit>
}

