package com.cbstudio.wearwallet.core.platform.watchos

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook

/**
 * watchOS 平台專屬刪除清理勾子實作 (watchOS Platform Deletion Cleanup Hook)
 */
class WatchOSPlatformDeletionCleanupHook : PlatformDeletionCleanupHook {
    override suspend fun cancelWorkManagerJobs(walletId: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun cancelBackgroundSync(walletId: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun invalidateTiles(): Result<Unit> = Result.Success(Unit)
    override suspend fun invalidateComplications(): Result<Unit> = Result.Success(Unit)
}
