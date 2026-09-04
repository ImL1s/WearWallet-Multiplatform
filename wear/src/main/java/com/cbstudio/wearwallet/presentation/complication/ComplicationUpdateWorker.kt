package com.cbstudio.wearwallet.presentation.complication

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result

/**
 * Complication 更新 Worker - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終語法修復
 */
class ComplicationUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            // MAINTENANCE MODE: No actual update
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}