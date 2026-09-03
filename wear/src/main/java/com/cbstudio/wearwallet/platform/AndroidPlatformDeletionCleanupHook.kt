package com.cbstudio.wearwallet.platform

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.Operation
import androidx.work.WorkManager
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook
import com.cbstudio.wearwallet.presentation.complication.GasFeeComplicationService
import com.cbstudio.wearwallet.presentation.complication.TokenChangeComplicationService
import com.cbstudio.wearwallet.presentation.complication.TokenPriceComplicationService
import com.cbstudio.wearwallet.presentation.complication.WalletBalanceComplicationService
import com.cbstudio.wearwallet.presentation.complication.WearWalletComplicationService
import com.cbstudio.wearwallet.presentation.tiles.CryptoInteractiveTileService
import com.cbstudio.wearwallet.tile.MainTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wear OS 平台專屬刪除清理勾子實作 (Android Platform Deletion Cleanup Hook)
 *
 * 負責取消 WorkManager 背景工作、刷新 Wear OS Tiles 快取與觸發錶盤 Complications 刷新。
 */
class AndroidPlatformDeletionCleanupHook(
    private val context: Context
) : PlatformDeletionCleanupHook {

    override suspend fun cancelWorkManagerJobs(walletId: Long): Result<Unit> {
        return try {
            val workManager = WorkManager.getInstance(context)
            val op1 = workManager.cancelAllWorkByTag("wallet_$walletId")
            val op2 = workManager.cancelAllWorkByTag("complication_sync")
            val state1 = withContext(Dispatchers.IO) { op1.result.get() }
            val state2 = withContext(Dispatchers.IO) { op2.result.get() }
            if (state1 !is Operation.State.SUCCESS || state2 !is Operation.State.SUCCESS) {
                return Result.Failure(IllegalStateException("WorkManager cancel operation did not succeed"))
            }
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is Exception) e else RuntimeException(e))
        }
    }

    override suspend fun cancelBackgroundSync(walletId: Long): Result<Unit> {
        return try {
            val workManager = WorkManager.getInstance(context)
            val op = workManager.cancelAllWorkByTag("sync_wallet_$walletId")
            val state = withContext(Dispatchers.IO) { op.result.get() }
            if (state !is Operation.State.SUCCESS) {
                return Result.Failure(IllegalStateException("WorkManager sync cancel operation did not succeed"))
            }
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is Exception) e else RuntimeException(e))
        }
    }

    override suspend fun invalidateTiles(): Result<Unit> {
        return try {
            TileService.getUpdater(context).requestUpdate(MainTileService::class.java)
            TileService.getUpdater(context).requestUpdate(CryptoInteractiveTileService::class.java)
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is Exception) e else RuntimeException(e))
        }
    }

    override suspend fun invalidateComplications(): Result<Unit> {
        return try {
            val services = listOf(
                WalletBalanceComplicationService::class.java,
                TokenPriceComplicationService::class.java,
                TokenChangeComplicationService::class.java,
                GasFeeComplicationService::class.java,
                WearWalletComplicationService::class.java
            )
            val failures = mutableListOf<String>()
            for (service in services) {
                try {
                    val requester = ComplicationDataSourceUpdateRequester.create(
                        context,
                        ComponentName(context, service)
                    )
                    requester.requestUpdateAll()
                } catch (e: Throwable) {
                    failures.add("${service.simpleName}: ${e.message ?: e::class.simpleName}")
                }
            }
            if (failures.isNotEmpty()) {
                return Result.Failure(
                    IllegalStateException("Failed to invalidate complications: ${failures.joinToString("; ")}")
                )
            }
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is Exception) e else RuntimeException(e))
        }
    }
}
