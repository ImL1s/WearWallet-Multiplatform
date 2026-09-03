package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.database.Wallet
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.common.Result as CoreResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull

import com.cbstudio.wearwallet.core.security.CanonicalAad
import com.cbstudio.wearwallet.core.security.VersionedEncryptedEnvelope
import com.cbstudio.wearwallet.core.security.EnvelopeIntegrityException
import com.cbstudio.wearwallet.core.security.KeyMaterialUnavailableException
import com.cbstudio.wearwallet.core.security.encodeToUtf8Bytes
import io.github.iml1s.crypto.SecureByteArray
import io.github.iml1s.crypto.Secp256k1Pure
import com.cbstudio.wearwallet.core.security.SideEffectTracker
import com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker

import com.cbstudio.wearwallet.core.database.WalletQueries
import com.cbstudio.wearwallet.core.database.StagingJournalQueries
import com.cbstudio.wearwallet.core.database.DeletionJournalQueries
import com.cbstudio.wearwallet.core.security.SecureKeyManager
import com.cbstudio.wearwallet.core.security.KeyVaultReconciliationCapability
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.AuthenticationRequiredException
import com.cbstudio.wearwallet.core.security.AuthHandleRegistry
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.ProvisioningSession
import com.cbstudio.wearwallet.core.security.ProvisioningRequest
import com.cbstudio.wearwallet.core.security.ProvisioningState
import com.cbstudio.wearwallet.core.security.DeletionState
import com.cbstudio.wearwallet.core.security.StagingJournalException
import com.cbstudio.wearwallet.core.security.JournalCasMismatchException
import com.cbstudio.wearwallet.core.security.JournalWriteException
import com.cbstudio.wearwallet.core.security.KeyRollbackFailedException
import com.cbstudio.wearwallet.core.security.DeletionJournalException
import com.cbstudio.wearwallet.core.security.DeletionCasMismatchException
import com.cbstudio.wearwallet.core.security.DeletionAuthorizationGrant
import com.cbstudio.wearwallet.core.security.DeletionAuthorizationService
import com.cbstudio.wearwallet.core.security.KeyVaultDeletionCapability
import com.cbstudio.wearwallet.core.security.DeletionStep
import com.cbstudio.wearwallet.core.security.DeletionStepStatus
import com.cbstudio.wearwallet.core.security.DeletionIncompleteException
import com.cbstudio.wearwallet.core.platform.PlatformDeletionCleanupHook
import com.cbstudio.wearwallet.core.cache.GlobalCacheManager
import com.cbstudio.wearwallet.core.security.CryptoUtils
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner

/**
 * 使用 SQLDelight 實現的錢包儲存庫
 * 提供完整的錢包資料持久化功能
 */
class WalletRepositoryImpl(
    private val databaseDriverFactory: DatabaseDriverFactory,
    private val cryptoProvider: CryptoProvider,
    private val ethereumRpcClient: EthereumRpcClient,
    private val secureKeyManager: SecureKeyManager,
    private val platformDeletionCleanupHook: PlatformDeletionCleanupHook,
    private val sideEffectTracker: SideEffectTracker = GlobalSideEffectTracker.instance,
    private val customWalletQueries: com.cbstudio.wearwallet.core.database.WalletQueries? = null,
    private val customStagingJournalQueries: com.cbstudio.wearwallet.core.database.StagingJournalQueries? = null,
    private val customDeletionJournalQueries: com.cbstudio.wearwallet.core.database.DeletionJournalQueries? = null,
    private val customDeletionStepLedgerQueries: com.cbstudio.wearwallet.core.database.DeletionStepLedgerQueries? = null
) : WalletRepository {
    
    private val database by lazy { CoreWalletDatabase(databaseDriverFactory.createDriver()) }
    private val walletQueries get() = customWalletQueries ?: database.walletQueries
    private val stagingJournalQueries get() = customStagingJournalQueries ?: if (customWalletQueries != null) null else database.stagingJournalQueries
    private val deletionJournalQueries get() = customDeletionJournalQueries ?: if (customWalletQueries != null) null else database.deletionJournalQueries
    private val deletionStepLedgerQueries get() = customDeletionStepLedgerQueries ?: if (customWalletQueries != null) null else database.deletionStepLedgerQueries

    private fun recordJournalState(sessionId: String, state: ProvisioningState): Result<Unit> {
        val queries = stagingJournalQueries ?: return Result.Success(Unit)
        return try {
            queries.updateJournalState(state.name, sessionId)
            val updated = queries.selectBySessionId(sessionId)?.executeAsOneOrNull()
            if (updated != null && updated.state != state.name) {
                Result.Failure(JournalWriteException(sessionId, "Failed to update staging journal state to $state"))
            } else {
                Result.Success(Unit)
            }
        } catch (e: Throwable) {
            Result.Failure(if (e is StagingJournalException) e else JournalWriteException(sessionId, "Error recording journal state: ${e.message}", e))
        }
    }

    override suspend fun prepareProvisioning(): Result<ProvisioningRequest> {
        return try {
            val session = secureKeyManager.startProvisioningSession()
            val journalInsertResult = insertJournalEntry(session, ProvisioningState.PREPARED)
            if (journalInsertResult is Result.Failure) {
                return Result.Failure(journalInsertResult.exception)
            }
            Result.Success(session.toProvisioningRequest())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun insertJournalEntry(session: ProvisioningSession, initialState: ProvisioningState): Result<Unit> {
        val queries = stagingJournalQueries ?: return Result.Success(Unit)
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val expiresAt = session.createdAtMs + session.maxValidityDurationMs
            queries.insertJournal(
                session_id = session.sessionId,
                staged_alias = session.stagedKeyAlias,
                backup_id = session.backupId,
                state = initialState.name,
                created_at = now,
                expires_at = expiresAt
            )
            Result.Success(Unit)
        } catch (e: Throwable) {
            try {
                val existing = queries.selectBySessionId(session.sessionId)?.executeAsOneOrNull()
                if (existing != null && (existing.state == initialState.name || existing.state == ProvisioningState.PREPARED.name)) {
                    return Result.Success(Unit)
                }
            } catch (_: Throwable) {}
            Result.Failure(if (e is StagingJournalException) e else JournalWriteException(session.sessionId, "Failed to persist PREPARED staging journal: ${e.message}", e))
        }
    }

    private fun updateJournalCas(
        sessionId: String,
        expectedState: ProvisioningState,
        newState: ProvisioningState
    ): Result<Unit> {
        val queries = stagingJournalQueries ?: return Result.Success(Unit)
        return try {
            queries.transaction {
                queries.updateJournalStateCas(
                    newState = newState.name,
                    sessionId = sessionId,
                    expectedState = expectedState.name
                )
                val changes = queries.changesCount().executeAsOne()
                if (changes == 0L) {
                    val current = queries.selectBySessionId(sessionId).executeAsOneOrNull()
                    throw JournalCasMismatchException(
                        sessionId = sessionId,
                        expectedState = expectedState.name,
                        targetState = newState.name,
                        message = "CAS update affected 0 rows for session '$sessionId': expected '${expectedState.name}', but current state in DB is '${current?.state ?: "NON_EXISTENT"}'"
                    )
                }
                if (changes > 1L) {
                    throw IllegalStateException("Invariant violation: CAS update affected $changes rows (expected 1) for session '$sessionId'")
                }
                val after = queries.selectBySessionId(sessionId).executeAsOneOrNull()
                if (after == null || after.state != newState.name) {
                    throw JournalCasMismatchException(
                        sessionId = sessionId,
                        expectedState = expectedState.name,
                        targetState = newState.name,
                        message = "CAS update failed to apply new state '${newState.name}', current state is '${after?.state}'"
                    )
                }
            }
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is StagingJournalException) e else JournalWriteException(sessionId, "Failed to update journal state via CAS: ${e.message}", e))
        }
    }

    private suspend fun performRollback(session: ProvisioningSession, fromState: ProvisioningState?): Result<Unit> {
        var casFailure: Throwable? = null
        if (fromState != null) {
            val res = updateJournalCas(
                sessionId = session.sessionId,
                expectedState = fromState,
                newState = ProvisioningState.ROLLBACK_PENDING
            )
            if (res is Result.Failure) casFailure = res.exception
        } else {
            val res = recordJournalState(session.sessionId, ProvisioningState.ROLLBACK_PENDING)
            if (res is Result.Failure) casFailure = res.exception
        }

        var keyVaultFailure: Throwable? = null
        if (!session.isCommitted) {
            val rbResult = secureKeyManager.rollbackProvisioningSession(session)
            if (rbResult is Result.Failure) {
                keyVaultFailure = rbResult.exception
            }
        }

        val finalCasRes = updateJournalCas(
            sessionId = session.sessionId,
            expectedState = ProvisioningState.ROLLBACK_PENDING,
            newState = ProvisioningState.ROLLED_BACK
        )

        if (keyVaultFailure != null) {
            return Result.Failure(KeyRollbackFailedException(session.sessionId, session.stagedKeyAlias, "KeyVault rollback failed: ${keyVaultFailure.message}", keyVaultFailure))
        }
        if (casFailure != null) {
            return Result.Failure(casFailure as? Exception ?: RuntimeException(casFailure))
        }
        if (finalCasRes is Result.Failure) {
            return Result.Failure(finalCasRes.exception)
        }
        return Result.Success(Unit)
    }

    private fun insertDeletionJournalEntry(walletId: Long, keyAlias: String?, initialState: DeletionState): Result<Unit> {
        val queries = deletionJournalQueries ?: return Result.Success(Unit)
        val now = Clock.System.now().toEpochMilliseconds()
        return try {
            queries.insertDeletionJournal(
                wallet_id = walletId,
                key_alias = keyAlias,
                state = initialState.name,
                last_error = null,
                retry_count = 0L,
                created_at = now,
                updated_at = now
            )
            Result.Success(Unit)
        } catch (e: Throwable) {
            try {
                val existing = queries.selectByWalletId(walletId)?.executeAsOneOrNull()
                if (existing != null) {
                    return Result.Success(Unit)
                }
            } catch (_: Throwable) {}
            Result.Failure(if (e is DeletionJournalException) e else DeletionJournalException("Failed to insert deletion journal: ${e.message}", e))
        }
    }

    private fun updateDeletionCas(
        walletId: Long,
        expectedState: DeletionState,
        newState: DeletionState,
        lastError: String? = null
    ): Result<Unit> {
        val queries = deletionJournalQueries ?: return Result.Success(Unit)
        val now = Clock.System.now().toEpochMilliseconds()
        return try {
            queries.transaction {
                queries.updateDeletionStateCas(
                    newState = newState.name,
                    lastError = lastError,
                    updatedAt = now,
                    walletId = walletId,
                    expectedState = expectedState.name
                )
                val changes = queries.changesCount().executeAsOne()
                if (changes == 0L) {
                    val current = queries.selectByWalletId(walletId).executeAsOneOrNull()
                    throw DeletionCasMismatchException(
                        walletId = walletId,
                        expectedState = expectedState.name,
                        targetState = newState.name,
                        message = "CAS update affected 0 rows for wallet '$walletId': expected '${expectedState.name}', but current state in DB is '${current?.state ?: "NON_EXISTENT"}'"
                    )
                }
                if (changes > 1L) {
                    throw IllegalStateException("Invariant violation: CAS update affected $changes rows (expected 1) for wallet '$walletId'")
                }
                val after = queries.selectByWalletId(walletId).executeAsOneOrNull()
                if (after == null || after.state != newState.name) {
                    throw DeletionCasMismatchException(
                        walletId = walletId,
                        expectedState = expectedState.name,
                        targetState = newState.name,
                        message = "CAS update failed to apply new state '${newState.name}', current state is '${after?.state}'"
                    )
                }
            }
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is DeletionJournalException) e else DeletionJournalException("Failed to update deletion journal via CAS: ${e.message}", e))
        }
    }

    private fun recordStepStatus(
        walletId: Long,
        step: DeletionStep,
        status: DeletionStepStatus,
        errorMessage: String? = null
    ): Result<Unit> {
        val queries = deletionStepLedgerQueries ?: return Result.Success(Unit)
        val now = Clock.System.now().toEpochMilliseconds()
        return try {
            val existing = queries.selectStep(walletId, step.name).executeAsOneOrNull()
            val retryCount = (existing?.retry_count ?: 0L) + if (status == DeletionStepStatus.FAILED) 1L else 0L
            queries.upsertStep(
                wallet_id = walletId,
                step_name = step.name,
                status = status.name,
                error_message = errorMessage,
                retry_count = retryCount,
                updated_at = now
            )
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is Exception) e else RuntimeException(e))
        }
    }

    private fun initDeletionStepLedger(walletId: Long): Result<Unit> {
        val queries = deletionStepLedgerQueries ?: return Result.Success(Unit)
        val now = Clock.System.now().toEpochMilliseconds()
        return try {
            queries.transaction {
                for (step in DeletionStep.values()) {
                    val existing = queries.selectStep(walletId, step.name).executeAsOneOrNull()
                    if (existing == null) {
                        queries.upsertStep(
                            wallet_id = walletId,
                            step_name = step.name,
                            status = DeletionStepStatus.PENDING.name,
                            error_message = null,
                            retry_count = 0L,
                            updated_at = now
                        )
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is Exception) e else RuntimeException(e))
        }
    }

    private fun assertAll17StepsPass(walletId: Long): Result<Unit> {
        val queries = deletionStepLedgerQueries ?: return Result.Success(Unit)
        val allSteps = queries.selectStepsByWalletId(walletId).executeAsList()
        val expectedCount = DeletionStep.values().size
        val unpassed = allSteps.filter { it.status != DeletionStepStatus.PASS.name }
        val missingSteps = DeletionStep.values().map { it.name }.toSet() - allSteps.map { it.step_name }.toSet()
        if (allSteps.size != expectedCount || unpassed.isNotEmpty() || missingSteps.isNotEmpty()) {
            val err = "Cannot complete deletion: Not all 17 steps are PASS (unpassed: ${unpassed.map { it.step_name }}, missing: $missingSteps, count: ${allSteps.size}/$expectedCount)"
            return Result.Failure(DeletionIncompleteException(walletId, err))
        }
        return Result.Success(Unit)
    }

    private suspend fun perform17LayerCleanup(wallet: Wallet?, walletId: Long): Result<Unit> {
        val failedSteps = mutableListOf<String>()

        fun isStepPassed(step: DeletionStep): Boolean {
            val queries = deletionStepLedgerQueries ?: return false
            val record = queries.selectStep(walletId, step.name).executeAsOneOrNull()
            return record?.status == DeletionStepStatus.PASS.name
        }

        fun safeRecordPass(step: DeletionStep) {
            val recRes = recordStepStatus(walletId, step, DeletionStepStatus.PASS)
            if (recRes is Result.Failure) {
                recordStepStatus(walletId, step, DeletionStepStatus.FAILED, "Ledger write failed: ${recRes.exception.message}")
                failedSteps.add("${step.name}: recordStepStatus failed: ${recRes.exception.message}")
            }
        }

        val walletAddress = wallet?.address

        // Step 03: NFT_ROWS
        if (!isStepPassed(DeletionStep.NFT_ROWS)) {
            try {
                if (!walletAddress.isNullOrBlank()) {
                    database.nftQueries.deleteByWalletAddress(walletAddress)
                }
                safeRecordPass(DeletionStep.NFT_ROWS)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.NFT_ROWS, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.NFT_ROWS}: ${e.message}")
            }
        }

        // Step 04: PUSH_SUBSCRIPTIONS
        if (!isStepPassed(DeletionStep.PUSH_SUBSCRIPTIONS)) {
            try {
                if (!walletAddress.isNullOrBlank()) {
                    database.pushSubscriptionQueries.deleteAllByWallet(walletAddress)
                }
                safeRecordPass(DeletionStep.PUSH_SUBSCRIPTIONS)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.PUSH_SUBSCRIPTIONS, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.PUSH_SUBSCRIPTIONS}: ${e.message}")
            }
        }

        // Step 05: NOTIFICATION_HISTORY
        if (!isStepPassed(DeletionStep.NOTIFICATION_HISTORY)) {
            try {
                database.notificationHistoryQueries.deleteAllByWallet(walletId.toString())
                safeRecordPass(DeletionStep.NOTIFICATION_HISTORY)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.NOTIFICATION_HISTORY, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.NOTIFICATION_HISTORY}: ${e.message}")
            }
        }

        // Step 06: NOTIFICATION_PREFERENCES
        if (!isStepPassed(DeletionStep.NOTIFICATION_PREFERENCES)) {
            try {
                database.notificationPreferencesQueries.deleteByWalletId(walletId.toString())
                safeRecordPass(DeletionStep.NOTIFICATION_PREFERENCES)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.NOTIFICATION_PREFERENCES, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.NOTIFICATION_PREFERENCES}: ${e.message}")
            }
        }

        // Step 07: KEYSTONE_DATA (Fail-Closed: Never catch and swallow error!)
        if (!isStepPassed(DeletionStep.KEYSTONE_DATA)) {
            try {
                database.keystoneDataQueries.delete(walletId)
                safeRecordPass(DeletionStep.KEYSTONE_DATA)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.KEYSTONE_DATA, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.KEYSTONE_DATA}: ${e.message}")
            }
        }

        // Step 08: TOKEN_ROWS
        if (!isStepPassed(DeletionStep.TOKEN_ROWS)) {
            try {
                database.tokenQueries.deleteByWalletId(walletId)
                safeRecordPass(DeletionStep.TOKEN_ROWS)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.TOKEN_ROWS, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.TOKEN_ROWS}: ${e.message}")
            }
        }

        // Step 09: TRANSACTION_ROWS
        if (!isStepPassed(DeletionStep.TRANSACTION_ROWS)) {
            try {
                database.transactionQueries.deleteByWalletId(walletId)
                safeRecordPass(DeletionStep.TRANSACTION_ROWS)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.TRANSACTION_ROWS, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.TRANSACTION_ROWS}: ${e.message}")
            }
        }

        // Step 10: PRICE_ALERT_ROWS (Real deletion & count == 0 assertion)
        if (!isStepPassed(DeletionStep.PRICE_ALERT_ROWS)) {
            try {
                database.priceAlertQueries.deleteByWalletId(walletId.toString())
                val remaining = database.priceAlertQueries.countByWalletId(walletId.toString()).executeAsOne()
                if (remaining != 0L) {
                    throw IllegalStateException("Remaining price alerts for wallet $walletId: $remaining")
                }
                safeRecordPass(DeletionStep.PRICE_ALERT_ROWS)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.PRICE_ALERT_ROWS, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.PRICE_ALERT_ROWS}: ${e.message}")
            }
        }

        // Step 11: WORK_MANAGER_JOBS
        if (!isStepPassed(DeletionStep.WORK_MANAGER_JOBS)) {
            try {
                val res = platformDeletionCleanupHook.cancelWorkManagerJobs(walletId)
                if (res is Result.Success) {
                    safeRecordPass(DeletionStep.WORK_MANAGER_JOBS)
                } else {
                    val errMsg = (res as Result.Failure).exception.message ?: "WorkManager cancel failed"
                    recordStepStatus(walletId, DeletionStep.WORK_MANAGER_JOBS, DeletionStepStatus.FAILED, errMsg)
                    failedSteps.add("${DeletionStep.WORK_MANAGER_JOBS}: $errMsg")
                }
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.WORK_MANAGER_JOBS, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.WORK_MANAGER_JOBS}: ${e.message}")
            }
        }

        // Step 12: BACKGROUND_SYNC
        if (!isStepPassed(DeletionStep.BACKGROUND_SYNC)) {
            try {
                val res = platformDeletionCleanupHook.cancelBackgroundSync(walletId)
                if (res is Result.Success) {
                    safeRecordPass(DeletionStep.BACKGROUND_SYNC)
                } else {
                    val errMsg = (res as Result.Failure).exception.message ?: "BackgroundSync cancel failed"
                    recordStepStatus(walletId, DeletionStep.BACKGROUND_SYNC, DeletionStepStatus.FAILED, errMsg)
                    failedSteps.add("${DeletionStep.BACKGROUND_SYNC}: $errMsg")
                }
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.BACKGROUND_SYNC, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.BACKGROUND_SYNC}: ${e.message}")
            }
        }

        // Step 13: TILES
        if (!isStepPassed(DeletionStep.TILES)) {
            try {
                val res = platformDeletionCleanupHook.invalidateTiles()
                if (res is Result.Success) {
                    safeRecordPass(DeletionStep.TILES)
                } else {
                    val errMsg = (res as Result.Failure).exception.message ?: "Tiles invalidation failed"
                    recordStepStatus(walletId, DeletionStep.TILES, DeletionStepStatus.FAILED, errMsg)
                    failedSteps.add("${DeletionStep.TILES}: $errMsg")
                }
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.TILES, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.TILES}: ${e.message}")
            }
        }

        // Step 14: COMPLICATIONS
        if (!isStepPassed(DeletionStep.COMPLICATIONS)) {
            try {
                val res = platformDeletionCleanupHook.invalidateComplications()
                if (res is Result.Success) {
                    safeRecordPass(DeletionStep.COMPLICATIONS)
                } else {
                    val errMsg = (res as Result.Failure).exception.message ?: "Complications invalidation failed"
                    recordStepStatus(walletId, DeletionStep.COMPLICATIONS, DeletionStepStatus.FAILED, errMsg)
                    failedSteps.add("${DeletionStep.COMPLICATIONS}: $errMsg")
                }
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.COMPLICATIONS, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.COMPLICATIONS}: ${e.message}")
            }
        }

        // Step 15: CACHES (Real GlobalCacheManager cleanup & count/entry verification)
        if (!isStepPassed(DeletionStep.CACHES)) {
            try {
                sideEffectTracker.onDbWrite()
                GlobalCacheManager.walletCache.remove(walletId.toString())
                if (walletAddress != null) {
                    GlobalCacheManager.nftCache.remove(walletAddress)
                }
                val cachedWallet = GlobalCacheManager.walletCache.get(walletId.toString())
                if (cachedWallet != null) {
                    throw IllegalStateException("Wallet cache entry for $walletId still exists after removal")
                }
                safeRecordPass(DeletionStep.CACHES)
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.CACHES, DeletionStepStatus.FAILED, e.message)
                failedSteps.add("${DeletionStep.CACHES}: ${e.message}")
            }
        }

        val pendingOrFailed = deletionStepLedgerQueries?.selectPendingOrFailedSteps(walletId)?.executeAsList()
            ?.filter { record ->
                val step = try { DeletionStep.valueOf(record.step_name) } catch (_: Throwable) { null }
                step != null && step != DeletionStep.WALLET_TOMBSTONE && step != DeletionStep.KEY_VAULT && step != DeletionStep.ACTIVE_POINTER && step != DeletionStep.WALLET_DB_ROW
            } ?: emptyList()

        if (failedSteps.isNotEmpty() || pendingOrFailed.isNotEmpty()) {
            val allErrors = (failedSteps + pendingOrFailed.map { "${it.step_name}: ${it.error_message ?: it.status}" }).distinct().joinToString("; ")
            return Result.Failure(DeletionIncompleteException(walletId, "17-layer deletion cleanup incomplete: $allErrors"))
        }

        return Result.Success(Unit)
    }

    private fun parseRawPrivateKeyBytes(bytes: ByteArray): ByteArray {
        if (bytes.size == 32) {
            return bytes.copyOf()
        }
        var offset = 0
        var length = bytes.size
        if (length >= 2 && bytes[0] == '0'.code.toByte() && (bytes[1] == 'x'.code.toByte() || bytes[1] == 'X'.code.toByte())) {
            offset += 2
            length -= 2
        }
        if (length == 64) {
            val result = ByteArray(32)
            for (i in 0 until 32) {
                val high = parseHexNibble(bytes[offset + i * 2])
                val low = parseHexNibble(bytes[offset + i * 2 + 1])
                result[i] = ((high shl 4) or low).toByte()
            }
            return result
        }
        throw EnvelopeIntegrityException("Invalid private key byte length: ${bytes.size}")
    }

    private fun parseHexNibble(byte: Byte): Int {
        val c = byte.toInt().toChar()
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw EnvelopeIntegrityException("Invalid hex character in private key payload: $c")
        }
    }

    private fun generateOpaqueUuid(prefix: String): String {
        val randomBytes = CryptoUtils.randomBytes(16)
        randomBytes[6] = ((randomBytes[6].toInt() and 0x0f) or 0x40).toByte()
        randomBytes[8] = ((randomBytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hexChars = "0123456789abcdef"
        val hex = buildString(32) {
            for (b in randomBytes) {
                val i = b.toInt() and 0xFF
                append(hexChars[i ushr 4])
                append(hexChars[i and 0x0F])
            }
        }
        val uuid = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        return "$prefix$uuid"
    }
    
    override suspend fun createWallet(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType,
        authContext: AuthenticationContext
    ): Result<WalletAccount> {
        val session = authContext.authHandle?.let { handle ->
            if (handle.sessionId.isNotBlank()) {
                secureKeyManager.getActiveProvisioningSession(handle.sessionId)
            } else null
        } ?: secureKeyManager.startProvisioningSession()
        val journalInsertResult = insertJournalEntry(session, ProvisioningState.PREPARED)
        if (journalInsertResult is Result.Failure) {
            return Result.Failure(journalInsertResult.exception)
        }

        val passwordBytes = password.encodeToUtf8Bytes()
        var privBytes: ByteArray? = null
        var mnemBytes: ByteArray? = null
        var insertedWalletId: Long? = null
        try {
            println("[CoreKmp] 🔧 RealWalletRepository.createWallet 開始")
            
            // 使用 CryptoProvider 從助記詞生成密鑰對
            println("[CoreKmp] 🔧 正在生成密鑰對...")
            val derivationPath = chainType.getDefaultDerivationPath()
            val keyPair = cryptoProvider.generateKeyPairFromMnemonic(
                mnemonic = mnemonic,
                derivationPath = derivationPath,
                chainType = chainType
            )
            println("[CoreKmp] 🔧 密鑰對生成成功")
            
            println("[CoreKmp] 🔧 正在導出地址...")
            val address = cryptoProvider.deriveAddress(keyPair.publicKey)
            println("[CoreKmp] 🔧 地址導出成功: $address")

            if (walletQueries.existsByAddress(address).executeAsOne()) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(Exception("Wallet already exists"))
            }

            // a. Store staged private key in KeyVault
            val rawPrivKey = keyPair.privateKeyBytes.copyOf()
            privBytes = rawPrivKey
            val storeResult = secureKeyManager.storeStagedPrivateKey(
                session = session,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = authContext
            )
            if (storeResult is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(storeResult.exception)
            }

            // b. Verify KeyVault contains the staged alias
            if (!secureKeyManager.hasPrivateKey(session.stagedKeyAlias)) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(IllegalStateException("KeyVault verification failed for keyAlias: ${session.stagedKeyAlias}"))
            }
            val casKeyStaged = updateJournalCas(session.sessionId, ProvisioningState.PREPARED, ProvisioningState.KEY_STAGED)
            if (casKeyStaged is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(casKeyStaged.exception)
            }

            val keyBackend = secureKeyManager.getSecurityLevel().level.name

            // c. 加密私鑰和助記詞 (使用 VersionedEncryptedEnvelope + Canonical AAD)
            println("[CoreKmp] 🔧 正在加密私鑰和助記詞...")
            privBytes = rawPrivKey.copyOf()
            val mnemEncoded = mnemonic.encodeToUtf8Bytes()
            mnemBytes = mnemEncoded

            val privEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = rawPrivKey.copyOf(),
                password = passwordBytes,
                keyId = session.stagedKeyAlias,
                aad = CanonicalAad.forWalletStorage(session.stagedKeyAlias, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
            )
            val mnemEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = mnemEncoded,
                password = passwordBytes,
                keyId = session.backupId,
                aad = CanonicalAad.forWalletStorage(session.backupId, CanonicalAad.KEY_TYPE_MNEMONIC)
            )
            val encryptedPrivateKey = privEnvelope.serializeToBase64()
            val encryptedMnemonic = mnemEnvelope.serializeToBase64()
            println("[CoreKmp] 🔧 加密完成")
            
            val chainId = chainType.getChainId()
            
            // d. 插入到數據庫 (with atomic rollback compensation)
            println("[CoreKmp] 🔧 正在插入數據庫...")
            sideEffectTracker.onDbWrite()
            var insertedWallet: Wallet? = null
            walletQueries.transaction {
                walletQueries.insert(
                    name = name,
                    address = address,
                    public_key = keyPair.publicKey,
                    encrypted_private_key = encryptedPrivateKey,
                    encrypted_mnemonic = encryptedMnemonic,
                    derivation_path = derivationPath,
                    chain_type = chainType.name,
                    wallet_type = WalletType.HOT_WALLET.name,
                    is_watch_only = 0L,
                    master_fingerprint = null,
                    keystone_sign_request = null,
                    keystone_sync_data = null,
                    metadata = "{}",
                    avatar_id = null,
                    chain_id = chainId,
                    key_alias = session.stagedKeyAlias,
                    key_backend = keyBackend,
                    key_format_version = 2L,
                    requires_auth = 1L,
                    is_deletion_pending = 0L
                )
                val walletId = walletQueries.lastInsertRowId().executeAsOne()
                insertedWalletId = walletId
                walletQueries.setActiveWallet(walletId)
                insertedWallet = walletQueries.selectById(walletId).executeAsOne()
                stagingJournalQueries?.let { sjQueries ->
                    sjQueries.updateJournalStateCas(
                        newState = ProvisioningState.DB_WRITTEN.name,
                        sessionId = session.sessionId,
                        expectedState = ProvisioningState.KEY_STAGED.name
                    )
                    val changes = sjQueries.changesCount().executeAsOne()
                    if (changes == 0L) {
                        val current = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update affected 0 rows for session '${session.sessionId}': expected '${ProvisioningState.KEY_STAGED.name}', but current state is '${current?.state ?: "NON_EXISTENT"}'"
                        )
                    }
                    if (changes > 1L) {
                        throw IllegalStateException("Invariant violation: CAS update affected $changes rows (expected 1) for session '${session.sessionId}'")
                    }
                    val afterDb = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                    if (afterDb == null || afterDb.state != ProvisioningState.DB_WRITTEN.name) {
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update failed: expected '${ProvisioningState.KEY_STAGED.name}' -> '${ProvisioningState.DB_WRITTEN.name}', but state is '${afterDb?.state}'"
                        )
                    }
                }
            }
            println("[CoreKmp] 🔧 數據庫插入完成")
            println("[CoreKmp] 🔧 錢包創建成功！")

            val commitResult = secureKeyManager.commitProvisioningSession(session)
            if (commitResult is Result.Failure) {
                insertedWalletId?.let { id ->
                    try {
                        walletQueries.transaction {
                            walletQueries.delete(id)
                            val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                            if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                        }
                    } catch (_: Throwable) {}
                }
                performRollback(session, ProvisioningState.DB_WRITTEN)
                return Result.Failure(commitResult.exception)
            }
            val casCommit = updateJournalCas(session.sessionId, ProvisioningState.DB_WRITTEN, ProvisioningState.COMMITTED)
            if (casCommit is Result.Failure) {
                return Result.Failure(casCommit.exception)
            }

            return Result.Success(insertedWallet!!.toWalletAccount())
        } catch (e: Throwable) {
            insertedWalletId?.let { id ->
                try {
                    walletQueries.transaction {
                        walletQueries.delete(id)
                        val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                        if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                    }
                } catch (_: Throwable) {}
            }
            performRollback(session, null)
            println("[CoreKmp] ❌ RealWalletRepository.createWallet 失敗: ${e.message}")
            return Result.Failure(if (e is Exception) e else RuntimeException(e))
        } finally {
            mnemonic.fill('\u0000')
            password.fill('\u0000')
            SecureByteArray.secureZero(passwordBytes)
            privBytes?.let { SecureByteArray.secureZero(it) }
            mnemBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    
    override suspend fun importKeystoneWallet(
        name: String,
        xpub: String,
        derivationPath: String,
        masterFingerprint: String,
        chainType: ChainType,
        policy: com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy
    ): Result<WalletAccount> {
        try {
            policy.validate(masterFingerprint, xpub, derivationPath, isTestnet = chainType.isTestnet())
            // 從 xpub 導出地址
            val address = cryptoProvider.deriveAddressFromXpub(xpub, derivationPath, isTestnet = chainType.isTestnet(), policy = policy)
            
            // 檢查地址是否已存在
            if (walletQueries.existsByAddress(address).executeAsOne()) {
                return Result.Failure(Exception("Wallet already exists"))
            }
            
            // P1-5: Use actual chainType instead of hardcoded Ethereum
            val chainId = chainType.getChainId()
            
            // 插入 Keystone 硬體錢包
            walletQueries.insert(
                name = name,
                address = address,
                public_key = xpub,
                encrypted_private_key = "", // 硬體錢包沒有私鑰
                encrypted_mnemonic = null,
                derivation_path = derivationPath,
                chain_type = chainType.name,
                wallet_type = WalletType.KEYSTONE.name,
                is_watch_only = 0L,
                master_fingerprint = masterFingerprint,
                keystone_sign_request = null,
                keystone_sync_data = null,
                metadata = "{}",
                avatar_id = null,
                chain_id = chainId,
                key_alias = null,
                key_backend = null,
                key_format_version = 1L,
                requires_auth = 0L,
                is_deletion_pending = 0L
            )
            
            // 獲取插入的錢包 ID
            val walletId = walletQueries.lastInsertRowId().executeAsOne()
            
            // 查詢並返回創建的錢包
            val wallet = walletQueries.selectById(walletId).executeAsOne()
            return Result.Success(wallet.toWalletAccount())
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun getAllWallets(): Result<List<WalletAccount>> {
        try {
            val wallets = walletQueries.selectAllActiveWallets().executeAsList()
            return Result.Success(wallets.map { it.toWalletAccount() })
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun getWallet(id: String): Result<WalletAccount?> {
        try {
            val walletId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid wallet ID")
            )
            val wallet = walletQueries.selectById(walletId).executeAsOneOrNull()
            return Result.Success(wallet?.toWalletAccount())
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun getWalletByAddress(address: String): Result<WalletAccount?> {
        try {
            val wallet = walletQueries.selectByAddress(address).executeAsOneOrNull()
            return Result.Success(wallet?.toWalletAccount())
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun getActiveWallet(): Result<WalletAccount?> {
        try {
            val wallet = walletQueries.selectActiveWallet().executeAsOneOrNull()
            return Result.Success(wallet?.toWalletAccount())
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun setActiveWallet(walletId: String): Result<Unit> {
        try {
            val id = walletId.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid wallet ID")
            )
            walletQueries.setActiveWallet(id)
            return Result.Success(Unit)
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun deleteWallet(id: String, authContext: AuthenticationContext?): Result<Unit> {
        try {
            val walletId = id.toLongOrNull() ?: return Result.Failure(
                IllegalArgumentException("Invalid wallet ID: $id")
            )
            val wallet = walletQueries.selectById(walletId).executeAsOneOrNull()
                ?: return Result.Failure(NoSuchElementException("Wallet not found with ID: $id"))

            // Phase 1: DELETE_AUTHORIZED (Platform Auth Handle Atomic Validation & Deletion Grant Issuance)
            val grantResult: Result<DeletionAuthorizationGrant>
            if (wallet.requires_auth != 0L) {
                if (authContext == null) {
                    return Result.Failure(
                        AuthenticationRequiredException("Authentication is required to delete wallet '${wallet.name}' but authContext is null")
                    )
                }
                val handle = authContext.authHandle
                if (handle == null) {
                    return Result.Failure(
                        AuthenticationRequiredException("Authentication is required to delete wallet '${wallet.name}' but authHandle is null")
                    )
                }
                val expectedKeyId = if (handle.keyId == wallet.address) {
                    wallet.address
                } else {
                    wallet.key_alias ?: wallet.address
                }
                grantResult = DeletionAuthorizationService.issueDeletionGrant(
                    handle = handle,
                    walletId = id,
                    keyAlias = expectedKeyId
                )
            } else {
                val expectedKeyId = wallet.key_alias ?: wallet.address
                grantResult = DeletionAuthorizationService.issueUnauthenticatedGrant(
                    walletId = id,
                    keyAlias = expectedKeyId
                )
            }

            if (grantResult is Result.Failure) {
                return Result.Failure(grantResult.exception)
            }
            val grant = (grantResult as Result.Success).data

            // Record State 1: DELETE_AUTHORIZED in deletion_journal & init 17-layer ledger
            val journalInit = insertDeletionJournalEntry(walletId, wallet.key_alias, DeletionState.DELETE_AUTHORIZED)
            if (journalInit is Result.Failure) {
                return Result.Failure(journalInit.exception)
            }
            val ledgerInit = initDeletionStepLedger(walletId)
            if (ledgerInit is Result.Failure) {
                return Result.Failure(ledgerInit.exception)
            }

            // Phase 2: TOMBSTONED (Fail-Closed DB Tombstone Set & CAS)
            sideEffectTracker.onDbWrite()
            try {
                walletQueries.markDeletionPending(walletId)
                val recTomb = recordStepStatus(walletId, DeletionStep.WALLET_TOMBSTONE, DeletionStepStatus.PASS)
                if (recTomb is Result.Failure) {
                    updateDeletionCas(walletId, DeletionState.DELETE_AUTHORIZED, DeletionState.RECOVERY_REQUIRED, recTomb.exception.message)
                    return Result.Failure(recTomb.exception)
                }
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.WALLET_TOMBSTONE, DeletionStepStatus.FAILED, e.message)
                updateDeletionCas(walletId, DeletionState.DELETE_AUTHORIZED, DeletionState.RECOVERY_REQUIRED, e.message)
                return Result.Failure(IllegalStateException("Failed to mark wallet deletion pending: ${e.message}", e))
            }
            val casToTombstone = updateDeletionCas(walletId, DeletionState.DELETE_AUTHORIZED, DeletionState.TOMBSTONED)
            if (casToTombstone is Result.Failure) {
                return Result.Failure(casToTombstone.exception)
            }

            // Phase 3: KEY_DELETED (Physical Key Deleted in Vault with Grant & CAS)
            if (!wallet.key_alias.isNullOrBlank()) {
                val presence = secureKeyManager.checkKeyPresence(wallet.key_alias)
                when (presence) {
                    is com.cbstudio.wearwallet.core.security.KeyPresence.Present -> {
                        val keyVaultCap = secureKeyManager as? KeyVaultDeletionCapability
                        val keyDeleteResult = if (keyVaultCap != null) {
                            keyVaultCap.deletePrivateKeyWithGrant(grant, expectedWalletId = walletId.toString())
                        } else {
                            secureKeyManager.deletePrivateKey(wallet.key_alias, authContext, expectedWalletId = walletId.toString())
                        }
                        if (keyDeleteResult is Result.Failure) {
                            recordStepStatus(walletId, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, keyDeleteResult.exception.message)
                            updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.RECOVERY_REQUIRED, keyDeleteResult.exception.message)
                            return Result.Failure(keyDeleteResult.exception)
                        }

                        // P1-4: Re-verify KeyPresence is Absent after deletion
                        val postDeletePresence = secureKeyManager.checkKeyPresence(wallet.key_alias)
                        if (postDeletePresence !is com.cbstudio.wearwallet.core.security.KeyPresence.Absent) {
                            val errMsg = "Key '${wallet.key_alias}' was not verified Absent after deletion (found $postDeletePresence)"
                            recordStepStatus(walletId, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, errMsg)
                            updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.RECOVERY_REQUIRED, errMsg)
                            return Result.Failure(com.cbstudio.wearwallet.core.security.KeyStorageException(errMsg))
                        }

                        val recKey = recordStepStatus(walletId, DeletionStep.KEY_VAULT, DeletionStepStatus.PASS)
                        if (recKey is Result.Failure) {
                            updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.RECOVERY_REQUIRED, recKey.exception.message)
                            return Result.Failure(recKey.exception)
                        }
                    }
                    is com.cbstudio.wearwallet.core.security.KeyPresence.Absent -> {
                        val recKey = recordStepStatus(walletId, DeletionStep.KEY_VAULT, DeletionStepStatus.PASS)
                        if (recKey is Result.Failure) {
                            updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.RECOVERY_REQUIRED, recKey.exception.message)
                            return Result.Failure(recKey.exception)
                        }
                    }
                    is com.cbstudio.wearwallet.core.security.KeyPresence.Partial -> {
                        val errMsg = "Key presence partial for alias '${wallet.key_alias}': ${presence.details}"
                        recordStepStatus(walletId, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, errMsg)
                        updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.RECOVERY_REQUIRED, errMsg)
                        return Result.Failure(com.cbstudio.wearwallet.core.security.KeyStorageException(errMsg))
                    }
                    is com.cbstudio.wearwallet.core.security.KeyPresence.Unavailable -> {
                        val errMsg = "Key presence check unavailable for alias '${wallet.key_alias}': ${presence.cause.message}"
                        recordStepStatus(walletId, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, errMsg)
                        updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.RECOVERY_REQUIRED, errMsg)
                        return Result.Failure(com.cbstudio.wearwallet.core.security.KeyStorageException(errMsg, presence.cause))
                    }
                }
            } else {
                val recKey = recordStepStatus(walletId, DeletionStep.KEY_VAULT, DeletionStepStatus.PASS)
                if (recKey is Result.Failure) {
                    updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.RECOVERY_REQUIRED, recKey.exception.message)
                    return Result.Failure(recKey.exception)
                }
            }
            val casToKeyDeleted = updateDeletionCas(walletId, DeletionState.TOMBSTONED, DeletionState.KEY_DELETED)
            if (casToKeyDeleted is Result.Failure) {
                return Result.Failure(casToKeyDeleted.exception)
            }

            // Phase 4: REFERENCES_CLEARED (Clean 17-Layer Subsystems & CAS)
            val cleanupResult = perform17LayerCleanup(wallet, walletId)
            if (cleanupResult is Result.Failure) {
                val errMsg = cleanupResult.exception.message ?: "Failed during 17-layer cleanup"
                updateDeletionCas(walletId, DeletionState.KEY_DELETED, DeletionState.RECOVERY_REQUIRED, errMsg)
                return Result.Failure(cleanupResult.exception)
            }
            val casToRefCleared = updateDeletionCas(walletId, DeletionState.KEY_DELETED, DeletionState.REFERENCES_CLEARED)
            if (casToRefCleared is Result.Failure) {
                return Result.Failure(casToRefCleared.exception)
            }

            // Phase 5: COMPLETED (Active Pointer Transition & Wallet DB Record Removed & CAS)
            sideEffectTracker.onDbWrite()
            try {
                if (wallet.is_active != 0L) {
                    val remainingActive = walletQueries.selectAllActiveWallets().executeAsList().filter { it.id != walletId }
                    if (remainingActive.isNotEmpty()) {
                        walletQueries.setActiveWallet(remainingActive.first().id)
                    }
                }
                val recActive = recordStepStatus(walletId, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.PASS)
                if (recActive is Result.Failure) {
                    updateDeletionCas(walletId, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recActive.exception.message)
                    return Result.Failure(recActive.exception)
                }
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.FAILED, e.message)
                updateDeletionCas(walletId, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                return Result.Failure(if (e is Exception) e else RuntimeException(e))
            }

            // Prior steps check before deleting DB wallet row
            val queries = deletionStepLedgerQueries
            if (queries != null) {
                val priorSteps = queries.selectStepsByWalletId(walletId).executeAsList()
                val unpassedPrior = priorSteps.filter { it.step_name != DeletionStep.WALLET_DB_ROW.name && it.status != DeletionStepStatus.PASS.name }
                if (unpassedPrior.isNotEmpty()) {
                    val err = "Cannot delete wallet DB row: prior steps not PASS: ${unpassedPrior.map { "${it.step_name}:${it.status}" }}"
                    updateDeletionCas(walletId, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, err)
                    return Result.Failure(DeletionIncompleteException(walletId, err))
                }
            }

            try {
                walletQueries.delete(walletId)
                val recDb = recordStepStatus(walletId, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.PASS)
                if (recDb is Result.Failure) {
                    updateDeletionCas(walletId, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recDb.exception.message)
                    return Result.Failure(recDb.exception)
                }
            } catch (e: Throwable) {
                recordStepStatus(walletId, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.FAILED, e.message)
                updateDeletionCas(walletId, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                return Result.Failure(if (e is Exception) e else RuntimeException(e))
            }

            // Final 17-step full PASS assertion gate
            val gateRes = assertAll17StepsPass(walletId)
            if (gateRes is Result.Failure) {
                updateDeletionCas(walletId, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, gateRes.exception.message)
                return gateRes
            }

            val casToCompleted = updateDeletionCas(walletId, DeletionState.REFERENCES_CLEARED, DeletionState.COMPLETED)
            if (casToCompleted is Result.Failure) {
                return Result.Failure(casToCompleted.exception)
            }

            return Result.Success(Unit)
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun updateWallet(wallet: WalletAccount): Result<Unit> {
        try {
            val walletId = wallet.id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid wallet ID")
            )
            walletQueries.update(
                name = wallet.name,
                avatar_id = wallet.avatarId?.toLong(),
                metadata = wallet.metadata ?: "{}",
                chain_type = wallet.chainType.name,
                id = walletId
            )
            return Result.Success(Unit)
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun updateKeystoneData(
        walletId: String,
        signRequest: String?,
        syncData: String?
    ): Result<Unit> {
        try {
            val id = walletId.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid wallet ID")
            )
            walletQueries.updateKeystoneData(
                keystone_sign_request = signRequest,
                keystone_sync_data = syncData,
                id = id
            )
            return Result.Success(Unit)
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }
    
    override suspend fun getKeystoneWallets(): Result<List<WalletAccount>> {
        try {
            val wallets = walletQueries.selectKeystoneWallets().executeAsList()
            return Result.Success(wallets.map { it.toWalletAccount() })
        } catch (e: Exception) {
            return Result.Failure(e)
        }
    }

    // =========================================================================
    // 舊版資料庫與金鑰遷移 (P1-4 / M4: Atomic Legacy Migration with KeyVault Provisioning)
    // =========================================================================

    /**
     * 若錢包尚未完成 KeyVault 遷移 (key_format_version < 2 或缺少 key_alias) 則執行遷移；若已遷移則保證冪等。
     */
    /**
     * 若錢包尚未完成 KeyVault 遷移 (key_format_version < 2 或缺少 key_alias) 則執行遷移；若已遷移則保證冪等。
     */
    override suspend fun migrateLegacyWalletIfNeeded(
        walletId: String,
        password: CharArray,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Result<WalletAccount> {
        val idLong = walletId.toLongOrNull() ?: return Result.Failure(
            IllegalArgumentException("Invalid wallet ID: $walletId")
        )
        val wallet = walletQueries.selectById(idLong).executeAsOneOrNull()
            ?: return Result.Failure(IllegalArgumentException("Wallet not found: $walletId"))

        // Hardware / Keystone 錢包沒有保存在 KeyVault 中的私鑰
        if (wallet.wallet_type == WalletType.KEYSTONE.name || wallet.wallet_type == "KEYSTONE_COLD") {
            return Result.Success(wallet.toWalletAccount())
        }

        // 檢查是否已完成遷移至 KeyVault
        if (wallet.key_alias != null && wallet.key_format_version >= 2L) {
            if (secureKeyManager.hasPrivateKey(wallet.key_alias)) {
                return Result.Success(wallet.toWalletAccount())
            } else {
                // Downgrade protection: 標記為已遷移但 KeyVault 缺金鑰者嚴禁降級或 fallback，必須 Fail-Closed
                return Result.Failure(
                    KeyMaterialUnavailableException(
                        "Key material unavailable in KeyVault for keyAlias: ${wallet.key_alias}. Downgrade or fallback to raw signing is prohibited."
                    )
                )
            }
        }

        return migrateLegacyWallet(walletId, password, authContext)
    }

    /**
     * 將舊版資料庫記錄與金鑰原子性遷移至 KeyVault
     */
    override suspend fun migrateLegacyWallet(
        walletId: String,
        password: CharArray,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Result<WalletAccount> {
        val idLong = walletId.toLongOrNull() ?: return Result.Failure(
            IllegalArgumentException("Invalid wallet ID: $walletId")
        )
        val wallet = walletQueries.selectById(idLong).executeAsOneOrNull()
            ?: return Result.Failure(IllegalArgumentException("Wallet not found: $walletId"))

        // Hardware / Keystone 錢包沒有保存在 KeyVault 中的私鑰
        if (wallet.wallet_type == WalletType.KEYSTONE.name || wallet.wallet_type == "KEYSTONE_COLD") {
            return Result.Success(wallet.toWalletAccount())
        }

        // 冪等性檢查 (Idempotency): 若已完成遷移且 KeyVault 存在對應 keyAlias，直接返回既有資料，不產生重複 key
        if (wallet.key_alias != null && wallet.key_format_version >= 2L) {
            if (secureKeyManager.hasPrivateKey(wallet.key_alias)) {
                return Result.Success(wallet.toWalletAccount())
            } else {
                // Downgrade safety
                return Result.Failure(
                    KeyMaterialUnavailableException(
                        "Key material unavailable in KeyVault for keyAlias: ${wallet.key_alias}. Downgrade or fallback to raw signing is prohibited."
                    )
                )
            }
        }

        val session = authContext.authHandle?.let { handle ->
            if (handle.sessionId.isNotBlank()) {
                secureKeyManager.getActiveProvisioningSession(handle.sessionId)
            } else null
        } ?: secureKeyManager.startProvisioningSession()
        val journalInsertResult = insertJournalEntry(session, ProvisioningState.PREPARED)
        if (journalInsertResult is Result.Failure) {
            return Result.Failure(journalInsertResult.exception)
        }

        val passwordBytes = password.encodeToUtf8Bytes()
        var decryptedPrivateKeyBytes: ByteArray? = null
        var decryptedMnemonicBytes: ByteArray? = null

        try {
            // 1. 驗證舊記錄並解密 (Verify old record & Decrypt)
            val encryptedPrivKey = wallet.encrypted_private_key
            val encryptedMnem = wallet.encrypted_mnemonic

            if (encryptedPrivKey.isBlank() && (encryptedMnem == null || encryptedMnem.isBlank())) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(IllegalStateException("Wallet contains no encrypted private key or mnemonic"))
            }

            if (encryptedPrivKey.isNotBlank()) {
                val privAad = CanonicalAad.forWalletStorage(wallet.address, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
                val decBytes = if (VersionedEncryptedEnvelope.isLegacyFormat(encryptedPrivKey)) {
                    val migrated = VersionedEncryptedEnvelope.migrateLegacy(
                        legacyString = encryptedPrivKey,
                        password = password,
                        keyId = wallet.address,
                        aad = privAad
                    )
                    migrated.decrypt(passwordBytes, expectedAad = privAad)
                } else {
                    val env = VersionedEncryptedEnvelope.deserializeFromBase64(encryptedPrivKey)
                    env.decrypt(passwordBytes, expectedAad = privAad)
                }
                try {
                    decryptedPrivateKeyBytes = parseRawPrivateKeyBytes(decBytes)
                } finally {
                    SecureByteArray.secureZero(decBytes)
                }
            } else {
                // 從助記詞衍生私鑰
                val mnemAad = CanonicalAad.forWalletStorage(wallet.address, CanonicalAad.KEY_TYPE_MNEMONIC)
                val mnemDecBytes = if (VersionedEncryptedEnvelope.isLegacyFormat(encryptedMnem!!)) {
                    val migrated = VersionedEncryptedEnvelope.migrateLegacy(
                        legacyString = encryptedMnem,
                        password = password,
                        keyId = wallet.address,
                        aad = mnemAad
                    )
                    migrated.decrypt(passwordBytes, expectedAad = mnemAad)
                } else {
                    val env = VersionedEncryptedEnvelope.deserializeFromBase64(encryptedMnem)
                    env.decrypt(passwordBytes, expectedAad = mnemAad)
                }
                decryptedMnemonicBytes = mnemDecBytes
                val chainType = ChainType.valueOf(wallet.chain_type)
                val derivationPath = wallet.derivation_path
                val mnemChars = mnemDecBytes.decodeToString().toCharArray()
                val keyPair = try {
                    cryptoProvider.generateKeyPairFromMnemonic(
                        mnemonic = mnemChars,
                        derivationPath = derivationPath,
                        chainType = chainType
                    )
                } finally {
                    mnemChars.fill('\u0000')
                }
                decryptedPrivateKeyBytes = keyPair.privateKeyBytes.copyOf()
            }

            // 若存在助記詞且尚未解密，一併解密
            if (decryptedMnemonicBytes == null && !encryptedMnem.isNullOrBlank()) {
                val mnemAad = CanonicalAad.forWalletStorage(wallet.address, CanonicalAad.KEY_TYPE_MNEMONIC)
                val mnemDecBytes = if (VersionedEncryptedEnvelope.isLegacyFormat(encryptedMnem)) {
                    val migrated = VersionedEncryptedEnvelope.migrateLegacy(
                        legacyString = encryptedMnem,
                        password = password,
                        keyId = wallet.address,
                        aad = mnemAad
                    )
                    migrated.decrypt(passwordBytes, expectedAad = mnemAad)
                } else {
                    val env = VersionedEncryptedEnvelope.deserializeFromBase64(encryptedMnem)
                    env.decrypt(passwordBytes, expectedAad = mnemAad)
                }
                decryptedMnemonicBytes = mnemDecBytes
            }

            // 2. 衍生地址與密碼學簽名預驗證 (Derived address & cryptographic pre-validation on ephemeral bytes)
            val rawPrivKey = decryptedPrivateKeyBytes ?: throw EnvelopeIntegrityException("Decrypted private key bytes missing")
            if (rawPrivKey.size != 32) {
                throw EnvelopeIntegrityException("Decrypted private key must be 32 bytes, got ${rawPrivKey.size}")
            }
            val testDigest = CryptoUtils.sha256("WearWallet-Migration-Verification-${wallet.address}".encodeToByteArray())
            val signature = Secp256k1Pure.signWithRecovery(testDigest, rawPrivKey)
            val z = Secp256k1Pure.BigInteger.fromByteArray(testDigest)
            val r = Secp256k1Pure.BigInteger.fromByteArray(signature.r)
            val s = Secp256k1Pure.BigInteger.fromByteArray(signature.s)
            val pointQ = Secp256k1Pure.recoverPublicKeyPoint(z, r, s, signature.yParity)
                ?: throw IllegalStateException("Failed to recover public key point during migration pre-validation")
            val uncompressed = Secp256k1Pure.encodePublicKey(pointQ, compressed = false)
            val recoveredAddress = EthereumSigner.toEthereumAddress(uncompressed)
            if (!recoveredAddress.equals(wallet.address, ignoreCase = true)) {
                throw EnvelopeIntegrityException("Sanity check failed: recovered address '$recoveredAddress' does not match stored address '${wallet.address}'")
            }

            // 3. 寫入 KeyVault (Provision in KeyVault with ProvisioningSession)
            val storeResult = secureKeyManager.storeStagedPrivateKey(
                session = session,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = authContext
            )
            if (storeResult is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(storeResult.exception)
            }

            // 驗證 KeyVault 寫入成功
            if (!secureKeyManager.hasPrivateKey(session.stagedKeyAlias)) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(IllegalStateException("KeyVault verification failed for keyAlias: ${session.stagedKeyAlias}"))
            }
            val casKeyStaged = updateJournalCas(session.sessionId, ProvisioningState.PREPARED, ProvisioningState.KEY_STAGED)
            if (casKeyStaged is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(casKeyStaged.exception)
            }

            val keyBackend = secureKeyManager.getSecurityLevel().level.name

            // 加密現代 WWEN 備份信封
            val newPrivEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = rawPrivKey,
                password = passwordBytes,
                keyId = session.stagedKeyAlias,
                aad = CanonicalAad.forWalletStorage(session.stagedKeyAlias, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
            )

            val newEncryptedMnemonic: String? = if (decryptedMnemonicBytes != null) {
                val newMnemEnvelope = VersionedEncryptedEnvelope.encrypt(
                    plaintext = decryptedMnemonicBytes,
                    password = passwordBytes,
                    keyId = session.backupId,
                    aad = CanonicalAad.forWalletStorage(session.backupId, CanonicalAad.KEY_TYPE_MNEMONIC)
                )
                newMnemEnvelope.serializeToBase64()
            } else {
                null
            }

            // 4. 資料庫原子更新與回滾補償 (Atomic DB update with rollback compensation)
            sideEffectTracker.onDbWrite()
            walletQueries.transaction {
                walletQueries.updateEncryptedSecrets(
                    encrypted_private_key = newPrivEnvelope.serializeToBase64(),
                    encrypted_mnemonic = newEncryptedMnemonic,
                    key_alias = session.stagedKeyAlias,
                    key_backend = keyBackend,
                    key_format_version = 2L,
                    requires_auth = 1L,
                    id = wallet.id
                )
                stagingJournalQueries?.let { sjQueries ->
                    sjQueries.updateJournalStateCas(
                        newState = ProvisioningState.DB_WRITTEN.name,
                        sessionId = session.sessionId,
                        expectedState = ProvisioningState.KEY_STAGED.name
                    )
                    val changes = sjQueries.changesCount().executeAsOne()
                    if (changes == 0L) {
                        val current = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update affected 0 rows for session '${session.sessionId}': expected '${ProvisioningState.KEY_STAGED.name}', but current state is '${current?.state ?: "NON_EXISTENT"}'"
                        )
                    }
                    if (changes > 1L) {
                        throw IllegalStateException("Invariant violation: CAS update affected $changes rows (expected 1) for session '${session.sessionId}'")
                    }
                    val afterDb = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                    if (afterDb == null || afterDb.state != ProvisioningState.DB_WRITTEN.name) {
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update failed: expected '${ProvisioningState.KEY_STAGED.name}' -> '${ProvisioningState.DB_WRITTEN.name}', but state is '${afterDb?.state}'"
                        )
                    }
                }
            }

            val commitResult = secureKeyManager.commitProvisioningSession(session)
            if (commitResult is Result.Failure) {
                performRollback(session, ProvisioningState.DB_WRITTEN)
                return Result.Failure(commitResult.exception)
            }
            val casCommit = updateJournalCas(session.sessionId, ProvisioningState.DB_WRITTEN, ProvisioningState.COMMITTED)
            if (casCommit is Result.Failure) {
                return Result.Failure(casCommit.exception)
            }

            val updatedWallet = walletQueries.selectById(wallet.id).executeAsOne()
            return Result.Success(updatedWallet.toWalletAccount())
        } catch (e: Throwable) {
            println("[CoreKmp] ❌ migrateLegacyWallet 失敗: ${e.message}")
            performRollback(session, null)
            return Result.Failure(if (e is Exception) e else RuntimeException(e))
        } finally {
            password.fill('\u0000')
            SecureByteArray.secureZero(passwordBytes)
            decryptedPrivateKeyBytes?.let { SecureByteArray.secureZero(it) }
            decryptedMnemonicBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    /**
     * 解密並執行原子性 Legacy 資料遷移 (相容 helper)
     */
    suspend fun decryptAndMigrateWalletSecrets(
        wallet: Wallet,
        password: String,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Pair<ByteArray, ByteArray?> {
        val pwChars = password.toCharArray()
        val migrationResult = try {
            migrateLegacyWallet(wallet.id.toString(), pwChars, authContext)
        } finally {
            pwChars.fill('\u0000')
        }
        if (migrationResult is Result.Failure) {
            throw migrationResult.exception
        }
        val passwordBytes = password.encodeToByteArray()
        try {
            val updatedWallet = walletQueries.selectById(wallet.id).executeAsOne()
            val privAad = CanonicalAad.forWalletStorage(updatedWallet.key_alias ?: updatedWallet.address, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
            val privEnv = VersionedEncryptedEnvelope.deserializeFromBase64(updatedWallet.encrypted_private_key)
            val privBytes = privEnv.decrypt(passwordBytes, expectedAad = privAad)

            val mnemBytes = if (!updatedWallet.encrypted_mnemonic.isNullOrBlank()) {
                val mnemEnv = VersionedEncryptedEnvelope.deserializeFromBase64(updatedWallet.encrypted_mnemonic)
                val mnemAad = CanonicalAad.forWalletStorage(mnemEnv.keyId, CanonicalAad.KEY_TYPE_MNEMONIC)
                mnemEnv.decrypt(passwordBytes, expectedAad = mnemAad)
            } else {
                null
            }
            return Pair(privBytes, mnemBytes)
        } finally {
            SecureByteArray.secureZero(passwordBytes)
        }
    }

    override suspend fun importFromMnemonic(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType,
        authContext: AuthenticationContext
    ): Result<WalletAccount> {
        val session = authContext.authHandle?.let { handle ->
            if (handle.sessionId.isNotBlank()) {
                secureKeyManager.getActiveProvisioningSession(handle.sessionId)
            } else null
        } ?: secureKeyManager.startProvisioningSession()
        val journalInsertResult = insertJournalEntry(session, ProvisioningState.PREPARED)
        if (journalInsertResult is Result.Failure) {
            return Result.Failure(journalInsertResult.exception)
        }

        val passwordBytes = password.encodeToUtf8Bytes()
        var privBytes: ByteArray? = null
        var mnemBytes: ByteArray? = null
        var insertedWalletId: Long? = null
        try {
            // 驗證助記詞
            if (!cryptoProvider.validateMnemonic(mnemonic)) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(Exception("Invalid mnemonic"))
            }

            // 使用 ChainType 的標準衍生路徑 (BIP44/SLIP-0044)
            val derivationPath = chainType.getDefaultDerivationPath()

            // 生成密鑰對
            val keyPair = cryptoProvider.generateKeyPairFromMnemonic(
                mnemonic = mnemonic,
                derivationPath = derivationPath,
                chainType = chainType
            )
            val address = cryptoProvider.deriveAddress(keyPair.publicKey)

            // 檢查地址是否已存在
            if (walletQueries.existsByAddress(address).executeAsOne()) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(Exception("Wallet already exists"))
            }

            // a. Store staged private key in KeyVault
            val rawPrivKey = keyPair.privateKeyBytes.copyOf()
            privBytes = rawPrivKey
            val storeResult = secureKeyManager.storeStagedPrivateKey(
                session = session,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = authContext
            )
            if (storeResult is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(storeResult.exception)
            }

            // b. Verify KeyVault contains the staged alias
            if (!secureKeyManager.hasPrivateKey(session.stagedKeyAlias)) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(IllegalStateException("KeyVault verification failed for keyAlias: ${session.stagedKeyAlias}"))
            }
            val casKeyStaged = updateJournalCas(session.sessionId, ProvisioningState.PREPARED, ProvisioningState.KEY_STAGED)
            if (casKeyStaged is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(casKeyStaged.exception)
            }

            val keyBackend = secureKeyManager.getSecurityLevel().level.name

            // c. 加密私鑰和助記詞 (使用 VersionedEncryptedEnvelope + Canonical AAD)
            privBytes = rawPrivKey.copyOf()
            val mnemEncoded = mnemonic.encodeToUtf8Bytes()
            mnemBytes = mnemEncoded

            val privEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = rawPrivKey.copyOf(),
                password = passwordBytes,
                keyId = session.stagedKeyAlias,
                aad = CanonicalAad.forWalletStorage(session.stagedKeyAlias, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
            )
            val mnemEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = mnemEncoded,
                password = passwordBytes,
                keyId = session.backupId,
                aad = CanonicalAad.forWalletStorage(session.backupId, CanonicalAad.KEY_TYPE_MNEMONIC)
            )
            val encryptedPrivateKey = privEnvelope.serializeToBase64()
            val encryptedMnemonic = mnemEnvelope.serializeToBase64()

            // d. 插入錢包 (with atomic rollback compensation)
            sideEffectTracker.onDbWrite()
            var insertedWallet: Wallet? = null
            walletQueries.transaction {
                walletQueries.insert(
                    name = name,
                    address = address,
                    public_key = keyPair.publicKey,
                    encrypted_private_key = encryptedPrivateKey,
                    encrypted_mnemonic = encryptedMnemonic,
                    derivation_path = derivationPath,
                    chain_type = chainType.name,
                    wallet_type = WalletType.MNEMONIC.name,
                    is_watch_only = 0L,
                    master_fingerprint = null,
                    keystone_sign_request = null,
                    keystone_sync_data = null,
                    metadata = "{}",
                    avatar_id = null,
                    chain_id = chainType.getChainId(),
                    key_alias = session.stagedKeyAlias,
                    key_backend = keyBackend,
                    key_format_version = 2L,
                    requires_auth = 1L,
                    is_deletion_pending = 0L
                )
                val walletId = walletQueries.lastInsertRowId().executeAsOne()
                insertedWalletId = walletId
                walletQueries.setActiveWallet(walletId)
                insertedWallet = walletQueries.selectById(walletId).executeAsOne()
                stagingJournalQueries?.let { sjQueries ->
                    sjQueries.updateJournalStateCas(
                        newState = ProvisioningState.DB_WRITTEN.name,
                        sessionId = session.sessionId,
                        expectedState = ProvisioningState.KEY_STAGED.name
                    )
                    val changes = sjQueries.changesCount().executeAsOne()
                    if (changes == 0L) {
                        val current = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update affected 0 rows for session '${session.sessionId}': expected '${ProvisioningState.KEY_STAGED.name}', but current state is '${current?.state ?: "NON_EXISTENT"}'"
                        )
                    }
                    if (changes > 1L) {
                        throw IllegalStateException("Invariant violation: CAS update affected $changes rows (expected 1) for session '${session.sessionId}'")
                    }
                    val afterDb = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                    if (afterDb == null || afterDb.state != ProvisioningState.DB_WRITTEN.name) {
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update failed: expected '${ProvisioningState.KEY_STAGED.name}' -> '${ProvisioningState.DB_WRITTEN.name}', but state is '${afterDb?.state}'"
                        )
                    }
                }
            }

            val commitResult = secureKeyManager.commitProvisioningSession(session)
            if (commitResult is Result.Failure) {
                insertedWalletId?.let { id ->
                    try {
                        walletQueries.transaction {
                            walletQueries.delete(id)
                            val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                            if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                        }
                    } catch (_: Throwable) {}
                }
                performRollback(session, ProvisioningState.DB_WRITTEN)
                return Result.Failure(commitResult.exception)
            }
            val casCommit = updateJournalCas(session.sessionId, ProvisioningState.DB_WRITTEN, ProvisioningState.COMMITTED)
            if (casCommit is Result.Failure) {
                return Result.Failure(casCommit.exception)
            }

            return Result.Success(insertedWallet!!.toWalletAccount())
        } catch (e: Throwable) {
            insertedWalletId?.let { id ->
                try {
                    walletQueries.transaction {
                        walletQueries.delete(id)
                        val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                        if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                    }
                } catch (_: Throwable) {}
            }
            performRollback(session, null)
            return Result.Failure(if (e is Exception) e else RuntimeException(e))
        } finally {
            mnemonic.fill('\u0000')
            password.fill('\u0000')
            SecureByteArray.secureZero(passwordBytes)
            privBytes?.let { SecureByteArray.secureZero(it) }
            mnemBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    /**
     * 優化版導入：使用預先計算的 KeyPair，避免重複的加密運算
     */
    override suspend fun importFromMnemonicWithKeyPair(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType,
        keyPair: com.cbstudio.wearwallet.core.security.KeyPair,
        address: String,
        authContext: AuthenticationContext
    ): Result<WalletAccount> {
        val session = authContext.authHandle?.let { handle ->
            if (handle.sessionId.isNotBlank()) {
                secureKeyManager.getActiveProvisioningSession(handle.sessionId)
            } else null
        } ?: secureKeyManager.startProvisioningSession()
        val journalInsertResult = insertJournalEntry(session, ProvisioningState.PREPARED)
        if (journalInsertResult is Result.Failure) {
            return Result.Failure(journalInsertResult.exception)
        }

        val passwordBytes = password.encodeToUtf8Bytes()
        var privBytes: ByteArray? = null
        var mnemBytes: ByteArray? = null
        var insertedWalletId: Long? = null
        try {
            val derivationPath = chainType.getDefaultDerivationPath()

            // 檢查地址是否已存在
            if (walletQueries.existsByAddress(address).executeAsOne()) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(Exception("Wallet already exists"))
            }

            // a. Store staged private key in KeyVault
            val rawPrivKey = keyPair.privateKeyBytes.copyOf()
            privBytes = rawPrivKey
            val storeResult = secureKeyManager.storeStagedPrivateKey(
                session = session,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = authContext
            )
            if (storeResult is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(storeResult.exception)
            }

            // b. Verify KeyVault contains the staged alias
            if (!secureKeyManager.hasPrivateKey(session.stagedKeyAlias)) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(IllegalStateException("KeyVault verification failed for keyAlias: ${session.stagedKeyAlias}"))
            }
            val casKeyStaged = updateJournalCas(session.sessionId, ProvisioningState.PREPARED, ProvisioningState.KEY_STAGED)
            if (casKeyStaged is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(casKeyStaged.exception)
            }

            val keyBackend = secureKeyManager.getSecurityLevel().level.name

            // 直接使用傳入的 keyPair，使用 VersionedEncryptedEnvelope
            privBytes = rawPrivKey.copyOf()
            val mnemEncoded = mnemonic.encodeToUtf8Bytes()
            mnemBytes = mnemEncoded

            val privEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = rawPrivKey.copyOf(),
                password = passwordBytes,
                keyId = session.stagedKeyAlias,
                aad = CanonicalAad.forWalletStorage(session.stagedKeyAlias, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
            )
            val mnemEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = mnemEncoded,
                password = passwordBytes,
                keyId = session.backupId,
                aad = CanonicalAad.forWalletStorage(session.backupId, CanonicalAad.KEY_TYPE_MNEMONIC)
            )
            val encryptedPrivateKey = privEnvelope.serializeToBase64()
            val encryptedMnemonic = mnemEnvelope.serializeToBase64()

            // 插入錢包 (with atomic rollback compensation)
            sideEffectTracker.onDbWrite()
            var insertedWallet: Wallet? = null
            walletQueries.transaction {
                walletQueries.insert(
                    name = name,
                    address = address,
                    public_key = keyPair.publicKey,
                    encrypted_private_key = encryptedPrivateKey,
                    encrypted_mnemonic = encryptedMnemonic,
                    derivation_path = derivationPath,
                    chain_type = chainType.name,
                    wallet_type = WalletType.MNEMONIC.name,
                    is_watch_only = 0L,
                    master_fingerprint = null,
                    keystone_sign_request = null,
                    keystone_sync_data = null,
                    metadata = "{}",
                    avatar_id = null,
                    chain_id = chainType.getChainId(),
                    key_alias = session.stagedKeyAlias,
                    key_backend = keyBackend,
                    key_format_version = 2L,
                    requires_auth = 1L,
                    is_deletion_pending = 0L
                )
                val walletId = walletQueries.lastInsertRowId().executeAsOne()
                insertedWalletId = walletId
                walletQueries.setActiveWallet(walletId)
                insertedWallet = walletQueries.selectById(walletId).executeAsOne()
                stagingJournalQueries?.let { sjQueries ->
                    sjQueries.updateJournalStateCas(
                        newState = ProvisioningState.DB_WRITTEN.name,
                        sessionId = session.sessionId,
                        expectedState = ProvisioningState.KEY_STAGED.name
                    )
                    val changes = sjQueries.changesCount().executeAsOne()
                    if (changes == 0L) {
                        val current = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update affected 0 rows for session '${session.sessionId}': expected '${ProvisioningState.KEY_STAGED.name}', but current state is '${current?.state ?: "NON_EXISTENT"}'"
                        )
                    }
                    if (changes > 1L) {
                        throw IllegalStateException("Invariant violation: CAS update affected $changes rows (expected 1) for session '${session.sessionId}'")
                    }
                    val afterDb = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                    if (afterDb == null || afterDb.state != ProvisioningState.DB_WRITTEN.name) {
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update failed: expected '${ProvisioningState.KEY_STAGED.name}' -> '${ProvisioningState.DB_WRITTEN.name}', but state is '${afterDb?.state}'"
                        )
                    }
                }
            }

            val commitResult = secureKeyManager.commitProvisioningSession(session)
            if (commitResult is Result.Failure) {
                insertedWalletId?.let { id ->
                    try {
                        walletQueries.transaction {
                            walletQueries.delete(id)
                            val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                            if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                        }
                    } catch (_: Throwable) {}
                }
                performRollback(session, ProvisioningState.DB_WRITTEN)
                return Result.Failure(commitResult.exception)
            }
            val casCommit = updateJournalCas(session.sessionId, ProvisioningState.DB_WRITTEN, ProvisioningState.COMMITTED)
            if (casCommit is Result.Failure) {
                return Result.Failure(casCommit.exception)
            }

            return Result.Success(insertedWallet!!.toWalletAccount())
        } catch (e: Throwable) {
            insertedWalletId?.let { id ->
                try {
                    walletQueries.transaction {
                        walletQueries.delete(id)
                        val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                        if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                    }
                } catch (_: Throwable) {}
            }
            performRollback(session, null)
            return Result.Failure(if (e is Exception) e else RuntimeException(e))
        } finally {
            mnemonic.fill('\u0000')
            password.fill('\u0000')
            SecureByteArray.secureZero(passwordBytes)
            privBytes?.let { SecureByteArray.secureZero(it) }
            mnemBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    override suspend fun importFromPrivateKey(
        name: String,
        privateKey: com.cbstudio.wearwallet.core.security.ScopedPrivateKey,
        password: CharArray,
        chainType: ChainType,
        authContext: AuthenticationContext
    ): Result<WalletAccount> {
        val session = authContext.authHandle?.let { handle ->
            if (handle.sessionId.isNotBlank()) {
                secureKeyManager.getActiveProvisioningSession(handle.sessionId)
            } else null
        } ?: secureKeyManager.startProvisioningSession()
        val journalInsertResult = insertJournalEntry(session, ProvisioningState.PREPARED)
        if (journalInsertResult is Result.Failure) {
            return Result.Failure(journalInsertResult.exception)
        }

        val passwordBytes = password.encodeToUtf8Bytes()
        var privBytes: ByteArray? = null
        var insertedWalletId: Long? = null
        try {
            val rawPrivKey = privateKey.use { it.copyOf() }
            privBytes = rawPrivKey

            // 生成密鑰對
            val keyPair = cryptoProvider.generateKeyPairFromPrivateKey(rawPrivKey)
            val address = cryptoProvider.deriveAddress(keyPair.publicKey)

            // 檢查地址是否已存在
            if (walletQueries.existsByAddress(address).executeAsOne()) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(Exception("Wallet already exists"))
            }

            // a. Store staged private key in KeyVault
            val storeResult = secureKeyManager.storeStagedPrivateKey(
                session = session,
                privateKey = rawPrivKey,
                requireAuth = true,
                authContext = authContext
            )
            if (storeResult is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(storeResult.exception)
            }

            // b. Verify KeyVault contains the staged alias
            if (!secureKeyManager.hasPrivateKey(session.stagedKeyAlias)) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(IllegalStateException("KeyVault verification failed for keyAlias: ${session.stagedKeyAlias}"))
            }
            val casKeyStaged = updateJournalCas(session.sessionId, ProvisioningState.PREPARED, ProvisioningState.KEY_STAGED)
            if (casKeyStaged is Result.Failure) {
                performRollback(session, ProvisioningState.PREPARED)
                return Result.Failure(casKeyStaged.exception)
            }

            val keyBackend = secureKeyManager.getSecurityLevel().level.name

            // 加密私鑰 (使用 VersionedEncryptedEnvelope + Canonical AAD)
            val privEnvelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = rawPrivKey.copyOf(),
                password = passwordBytes,
                keyId = session.stagedKeyAlias,
                aad = CanonicalAad.forWalletStorage(session.stagedKeyAlias, CanonicalAad.KEY_TYPE_PRIVATE_KEY)
            )
            val encryptedPrivateKey = privEnvelope.serializeToBase64()

            val chainId = chainType.getChainId()

            // 插入錢包 (with atomic rollback compensation)
            sideEffectTracker.onDbWrite()
            var insertedWallet: Wallet? = null
            walletQueries.transaction {
                walletQueries.insert(
                    name = name,
                    address = address,
                    public_key = keyPair.publicKey,
                    encrypted_private_key = encryptedPrivateKey,
                    encrypted_mnemonic = null,
                    derivation_path = "",
                    chain_type = chainType.name,
                    wallet_type = WalletType.PRIVATE_KEY.name,
                    is_watch_only = 0L,
                    master_fingerprint = null,
                    keystone_sign_request = null,
                    keystone_sync_data = null,
                    metadata = "{}",
                    avatar_id = null,
                    chain_id = chainId,
                    key_alias = session.stagedKeyAlias,
                    key_backend = keyBackend,
                    key_format_version = 2L,
                    requires_auth = 1L,
                    is_deletion_pending = 0L
                )
                val walletId = walletQueries.lastInsertRowId().executeAsOne()
                insertedWalletId = walletId
                walletQueries.setActiveWallet(walletId)
                insertedWallet = walletQueries.selectById(walletId).executeAsOne()
                stagingJournalQueries?.let { sjQueries ->
                    sjQueries.updateJournalStateCas(
                        newState = ProvisioningState.DB_WRITTEN.name,
                        sessionId = session.sessionId,
                        expectedState = ProvisioningState.KEY_STAGED.name
                    )
                    val changes = sjQueries.changesCount().executeAsOne()
                    if (changes == 0L) {
                        val current = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update affected 0 rows for session '${session.sessionId}': expected '${ProvisioningState.KEY_STAGED.name}', but current state is '${current?.state ?: "NON_EXISTENT"}'"
                        )
                    }
                    if (changes > 1L) {
                        throw IllegalStateException("Invariant violation: CAS update affected $changes rows (expected 1) for session '${session.sessionId}'")
                    }
                    val afterDb = sjQueries.selectBySessionId(session.sessionId).executeAsOneOrNull()
                    if (afterDb == null || afterDb.state != ProvisioningState.DB_WRITTEN.name) {
                        throw JournalCasMismatchException(
                            session.sessionId,
                            ProvisioningState.KEY_STAGED.name,
                            ProvisioningState.DB_WRITTEN.name,
                            "CAS update failed: expected '${ProvisioningState.KEY_STAGED.name}' -> '${ProvisioningState.DB_WRITTEN.name}', but state is '${afterDb?.state}'"
                        )
                    }
                }
            }

            val commitResult = secureKeyManager.commitProvisioningSession(session)
            if (commitResult is Result.Failure) {
                insertedWalletId?.let { id ->
                    try {
                        walletQueries.transaction {
                            walletQueries.delete(id)
                            val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                            if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                        }
                    } catch (_: Throwable) {}
                }
                performRollback(session, ProvisioningState.DB_WRITTEN)
                return Result.Failure(commitResult.exception)
            }
            val casCommit = updateJournalCas(session.sessionId, ProvisioningState.DB_WRITTEN, ProvisioningState.COMMITTED)
            if (casCommit is Result.Failure) {
                return Result.Failure(casCommit.exception)
            }

            return Result.Success(insertedWallet!!.toWalletAccount())
        } catch (e: Throwable) {
            insertedWalletId?.let { id ->
                try {
                    walletQueries.transaction {
                        walletQueries.delete(id)
                        val remaining = walletQueries.selectAllActiveWallets().executeAsList()
                        if (remaining.isNotEmpty()) walletQueries.setActiveWallet(remaining.first().id)
                    }
                } catch (_: Throwable) {}
            }
            performRollback(session, null)
            return Result.Failure(if (e is Exception) e else RuntimeException(e))
        } finally {
            password.fill('\u0000')
            SecureByteArray.secureZero(passwordBytes)
            privBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    override suspend fun reconcileStartupState(): Result<Unit> {
        return try {
            // 1. Reconcile Staging Journal (Fail-Closed, 5-Layer Anti-Spoofing)
            val pendingJournals = stagingJournalQueries?.selectPendingJournals()?.executeAsList() ?: emptyList()

            val reconciler = secureKeyManager as? KeyVaultReconciliationCapability

            for (journal in pendingJournals) {
                val verdict = validateReconciliationCandidate(
                    journal = journal,
                    expectedSessionId = journal.session_id,
                    expectedKeyAlias = journal.staged_alias,
                    walletQueries = walletQueries
                )

                when (verdict) {
                    is ReconciliationVerdict.ActiveWalletProtection -> {
                        // 活躍錢包防護：DB 內已存在合法活躍錢包，嚴禁刪除金鑰，將 Journal 推進至 COMMITTED
                        val fromState = ProvisioningState.valueOf(journal.state)
                        val casResult = updateJournalCas(
                            sessionId = journal.session_id,
                            expectedState = fromState,
                            newState = ProvisioningState.COMMITTED
                        )
                        if (casResult is Result.Failure) {
                            return casResult
                        }
                    }

                    is ReconciliationVerdict.SafeToRollback -> {
                        // 通過全部 5 層防偽檢驗，確認為無關聯活躍錢包的孤兒暫存金鑰
                        if (reconciler != null) {
                            val rollbackResult = reconciler.rollbackStagedKeyInternal(verdict.grant)
                            if (rollbackResult is Result.Failure) {
                                return rollbackResult
                            }
                            val fromState = ProvisioningState.valueOf(journal.state)
                            val casResult = updateJournalCas(
                                sessionId = journal.session_id,
                                expectedState = fromState,
                                newState = ProvisioningState.ROLLED_BACK
                            )
                            if (casResult is Result.Failure) {
                                return casResult
                            }
                        } else {
                            return Result.Failure(
                                IllegalStateException("KeyVaultReconciliationCapability is missing during SafeToRollback for session ${journal.session_id}")
                            )
                        }
                    }

                    is ReconciliationVerdict.Rejected -> {
                        // 驗證未通過，Fail-Closed：不執行任何 KeyVault 刪除操作
                    }
                }
            }

            // 2. Reconcile Deletion Journal (Persistent 5-State Machine Crash Recovery with 17-Layer Step Ledger)
            val pendingDeletions = deletionJournalQueries?.selectPendingDeletions()?.executeAsList() ?: emptyList()

            for (deletion in pendingDeletions) {
                val state = DeletionState.valueOf(deletion.state)
                val wallet = walletQueries.selectById(deletion.wallet_id).executeAsOneOrNull()
                val ledgerInit = initDeletionStepLedger(deletion.wallet_id)
                if (ledgerInit is Result.Failure) {
                    return Result.Failure(ledgerInit.exception)
                }

                when (state) {
                    DeletionState.DELETE_AUTHORIZED, DeletionState.TOMBSTONED -> {
                        walletQueries.markDeletionPending(deletion.wallet_id)
                        val recTomb = recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_TOMBSTONE, DeletionStepStatus.PASS)
                        if (recTomb is Result.Failure) {
                            updateDeletionCas(deletion.wallet_id, state, DeletionState.RECOVERY_REQUIRED, recTomb.exception.message)
                            return Result.Failure(recTomb.exception)
                        }

                        val alias = deletion.key_alias
                        val presence = if (!alias.isNullOrBlank()) {
                            secureKeyManager.checkKeyPresence(alias)
                        } else com.cbstudio.wearwallet.core.security.KeyPresence.Absent

                        var keyDeletedSuccessfully = true
                        when (presence) {
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Present -> {
                                val res = if (alias != null) {
                                    val unauthGrant = DeletionAuthorizationService.issueUnauthenticatedGrant(deletion.wallet_id.toString(), alias)
                                    if (unauthGrant is Result.Success) {
                                        (secureKeyManager as? KeyVaultDeletionCapability)?.deletePrivateKeyWithGrant(unauthGrant.data, expectedWalletId = deletion.wallet_id.toString())
                                            ?: secureKeyManager.deletePrivateKey(alias, authContext = null, expectedWalletId = deletion.wallet_id.toString())
                                    } else {
                                        secureKeyManager.deletePrivateKey(alias, authContext = null, expectedWalletId = deletion.wallet_id.toString())
                                    }
                                } else Result.Success(Unit)
                                if (res is Result.Failure) {
                                    keyDeletedSuccessfully = false
                                } else if (alias != null) {
                                    val postPresence = secureKeyManager.checkKeyPresence(alias)
                                    if (postPresence !is com.cbstudio.wearwallet.core.security.KeyPresence.Absent) {
                                        keyDeletedSuccessfully = false
                                    }
                                }
                            }
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Absent -> {
                                keyDeletedSuccessfully = true
                            }
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Partial -> {
                                keyDeletedSuccessfully = false
                            }
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Unavailable -> {
                                keyDeletedSuccessfully = false
                            }
                        }

                        if (keyDeletedSuccessfully) {
                            val recKey = recordStepStatus(deletion.wallet_id, DeletionStep.KEY_VAULT, DeletionStepStatus.PASS)
                            if (recKey is Result.Failure) {
                                updateDeletionCas(deletion.wallet_id, state, DeletionState.RECOVERY_REQUIRED, recKey.exception.message)
                                return Result.Failure(recKey.exception)
                            }
                            val cas1 = updateDeletionCas(deletion.wallet_id, state, DeletionState.KEY_DELETED)
                            if (cas1 is Result.Failure) return cas1

                            val cleanupResult = perform17LayerCleanup(wallet, deletion.wallet_id)
                            if (cleanupResult is Result.Failure) {
                                val recCas = updateDeletionCas(
                                    walletId = deletion.wallet_id,
                                    expectedState = DeletionState.KEY_DELETED,
                                    newState = DeletionState.RECOVERY_REQUIRED,
                                    lastError = cleanupResult.exception.message ?: "Startup recovery 17-layer cleanup failed"
                                )
                                if (recCas is Result.Failure) return recCas
                                return Result.Failure(cleanupResult.exception)
                            }
                            val cas2 = updateDeletionCas(deletion.wallet_id, DeletionState.KEY_DELETED, DeletionState.REFERENCES_CLEARED)
                            if (cas2 is Result.Failure) return cas2

                            // Step 16: ACTIVE_POINTER
                            try {
                                if (wallet?.is_active != 0L) {
                                    val remaining = walletQueries.selectAllActiveWallets().executeAsList().filter { it.id != deletion.wallet_id }
                                    if (remaining.isNotEmpty()) {
                                        walletQueries.setActiveWallet(remaining.first().id)
                                    }
                                }
                                val recActive = recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.PASS)
                                if (recActive is Result.Failure) {
                                    updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recActive.exception.message)
                                    return Result.Failure(recActive.exception)
                                }
                            } catch (e: Throwable) {
                                recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.FAILED, e.message)
                                updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                                return Result.Failure(if (e is Exception) e else RuntimeException(e))
                            }

                            // Step 17: WALLET_DB_ROW
                            try {
                                walletQueries.delete(deletion.wallet_id)
                                val recDb = recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.PASS)
                                if (recDb is Result.Failure) {
                                    updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recDb.exception.message)
                                    return Result.Failure(recDb.exception)
                                }
                            } catch (e: Throwable) {
                                recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.FAILED, e.message)
                                updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                                return Result.Failure(if (e is Exception) e else RuntimeException(e))
                            }

                            val gateRes = assertAll17StepsPass(deletion.wallet_id)
                            if (gateRes is Result.Failure) {
                                updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, gateRes.exception.message)
                                return gateRes
                            }

                            val cas3 = updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.COMPLETED)
                            if (cas3 is Result.Failure) return cas3
                        } else {
                            recordStepStatus(deletion.wallet_id, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, "Recovery key deletion failed")
                            val recCas = updateDeletionCas(deletion.wallet_id, state, DeletionState.RECOVERY_REQUIRED, "Recovery key deletion failed")
                            if (recCas is Result.Failure) return recCas
                            return Result.Failure(
                                KeyRollbackFailedException(
                                    sessionId = "del_recov_${deletion.wallet_id}",
                                    stagedAlias = deletion.key_alias ?: "",
                                    message = "Failed to delete key during startup recovery for wallet ${deletion.wallet_id}"
                                )
                            )
                        }
                    }

                    DeletionState.KEY_DELETED -> {
                        val cleanupResult = perform17LayerCleanup(wallet, deletion.wallet_id)
                        if (cleanupResult is Result.Failure) {
                            val recCas = updateDeletionCas(
                                walletId = deletion.wallet_id,
                                expectedState = DeletionState.KEY_DELETED,
                                newState = DeletionState.RECOVERY_REQUIRED,
                                lastError = cleanupResult.exception.message ?: "Startup recovery 17-layer cleanup failed"
                            )
                            if (recCas is Result.Failure) return recCas
                            return Result.Failure(cleanupResult.exception)
                        }
                        val cas = updateDeletionCas(deletion.wallet_id, DeletionState.KEY_DELETED, DeletionState.REFERENCES_CLEARED)
                        if (cas is Result.Failure) return cas

                        // Step 16: ACTIVE_POINTER
                        try {
                            if (wallet?.is_active != 0L) {
                                val remaining = walletQueries.selectAllActiveWallets().executeAsList().filter { it.id != deletion.wallet_id }
                                if (remaining.isNotEmpty()) {
                                    walletQueries.setActiveWallet(remaining.first().id)
                                }
                            }
                            val recActive = recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.PASS)
                            if (recActive is Result.Failure) {
                                updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recActive.exception.message)
                                return Result.Failure(recActive.exception)
                            }
                        } catch (e: Throwable) {
                            recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.FAILED, e.message)
                            updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                            return Result.Failure(if (e is Exception) e else RuntimeException(e))
                        }

                        // Step 17: WALLET_DB_ROW
                        try {
                            walletQueries.delete(deletion.wallet_id)
                            val recDb = recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.PASS)
                            if (recDb is Result.Failure) {
                                updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recDb.exception.message)
                                return Result.Failure(recDb.exception)
                            }
                        } catch (e: Throwable) {
                            recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.FAILED, e.message)
                            updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                            return Result.Failure(if (e is Exception) e else RuntimeException(e))
                        }

                        val gateRes = assertAll17StepsPass(deletion.wallet_id)
                        if (gateRes is Result.Failure) {
                            updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, gateRes.exception.message)
                            return gateRes
                        }

                        val casComp = updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.COMPLETED)
                        if (casComp is Result.Failure) return casComp
                    }

                    DeletionState.REFERENCES_CLEARED -> {
                        // Step 16: ACTIVE_POINTER
                        try {
                            if (wallet?.is_active != 0L) {
                                val remaining = walletQueries.selectAllActiveWallets().executeAsList().filter { it.id != deletion.wallet_id }
                                if (remaining.isNotEmpty()) {
                                    walletQueries.setActiveWallet(remaining.first().id)
                                }
                            }
                            val recActive = recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.PASS)
                            if (recActive is Result.Failure) {
                                updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recActive.exception.message)
                                return Result.Failure(recActive.exception)
                            }
                        } catch (e: Throwable) {
                            recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.FAILED, e.message)
                            updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                            return Result.Failure(if (e is Exception) e else RuntimeException(e))
                        }

                        // Step 17: WALLET_DB_ROW
                        try {
                            walletQueries.delete(deletion.wallet_id)
                            val recDb = recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.PASS)
                            if (recDb is Result.Failure) {
                                updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, recDb.exception.message)
                                return Result.Failure(recDb.exception)
                            }
                        } catch (e: Throwable) {
                            recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.FAILED, e.message)
                            updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, e.message)
                            return Result.Failure(if (e is Exception) e else RuntimeException(e))
                        }

                        val gateRes = assertAll17StepsPass(deletion.wallet_id)
                        if (gateRes is Result.Failure) {
                            updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.RECOVERY_REQUIRED, gateRes.exception.message)
                            return gateRes
                        }

                        val casComp = updateDeletionCas(deletion.wallet_id, DeletionState.REFERENCES_CLEARED, DeletionState.COMPLETED)
                        if (casComp is Result.Failure) return casComp
                    }

                    DeletionState.RECOVERY_REQUIRED -> {
                        val alias = deletion.key_alias
                        val presence = if (!alias.isNullOrBlank()) {
                            secureKeyManager.checkKeyPresence(alias)
                        } else com.cbstudio.wearwallet.core.security.KeyPresence.Absent

                        when (presence) {
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Present -> {
                                val res = if (alias != null) {
                                    val unauthGrant = DeletionAuthorizationService.issueUnauthenticatedGrant(deletion.wallet_id.toString(), alias)
                                    if (unauthGrant is Result.Success) {
                                        (secureKeyManager as? KeyVaultDeletionCapability)?.deletePrivateKeyWithGrant(unauthGrant.data, expectedWalletId = deletion.wallet_id.toString())
                                            ?: secureKeyManager.deletePrivateKey(alias, authContext = null, expectedWalletId = deletion.wallet_id.toString())
                                    } else {
                                        secureKeyManager.deletePrivateKey(alias, authContext = null, expectedWalletId = deletion.wallet_id.toString())
                                    }
                                } else Result.Success(Unit)
                                if (res is Result.Failure) {
                                    recordStepStatus(deletion.wallet_id, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, "Recovery key deletion failed: ${res.exception.message}")
                                    return Result.Failure(res.exception)
                                }
                                if (alias != null) {
                                    val postPresence = secureKeyManager.checkKeyPresence(alias)
                                    if (postPresence !is com.cbstudio.wearwallet.core.security.KeyPresence.Absent) {
                                        val errMsg = "Recovery key deletion for alias '$alias' did not achieve Absent state: $postPresence"
                                        recordStepStatus(deletion.wallet_id, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, errMsg)
                                        return Result.Failure(com.cbstudio.wearwallet.core.security.KeyStorageException(errMsg))
                                    }
                                }
                            }
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Absent -> {
                                // Key is already absent
                            }
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Partial -> {
                                val errMsg = "Key presence partial during recovery for alias '$alias': ${presence.details}"
                                recordStepStatus(deletion.wallet_id, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, errMsg)
                                return Result.Failure(com.cbstudio.wearwallet.core.security.KeyStorageException(errMsg))
                            }
                            is com.cbstudio.wearwallet.core.security.KeyPresence.Unavailable -> {
                                val errMsg = "Key presence check unavailable for alias '$alias': ${presence.cause.message}"
                                recordStepStatus(deletion.wallet_id, DeletionStep.KEY_VAULT, DeletionStepStatus.FAILED, errMsg)
                                return Result.Failure(com.cbstudio.wearwallet.core.security.KeyStorageException(errMsg, presence.cause))
                            }
                        }
                        recordStepStatus(deletion.wallet_id, DeletionStep.KEY_VAULT, DeletionStepStatus.PASS)

                        val cleanupResult = perform17LayerCleanup(wallet, deletion.wallet_id)
                        if (cleanupResult is Result.Failure) {
                            val recCas = updateDeletionCas(
                                walletId = deletion.wallet_id,
                                expectedState = DeletionState.RECOVERY_REQUIRED,
                                newState = DeletionState.RECOVERY_REQUIRED,
                                lastError = cleanupResult.exception.message ?: "Startup recovery 17-layer cleanup failed"
                            )
                            if (recCas is Result.Failure) return recCas
                            return Result.Failure(cleanupResult.exception)
                        }

                        // Step 16: ACTIVE_POINTER
                        try {
                            if (wallet?.is_active != 0L) {
                                val remaining = walletQueries.selectAllActiveWallets().executeAsList().filter { it.id != deletion.wallet_id }
                                if (remaining.isNotEmpty()) {
                                    walletQueries.setActiveWallet(remaining.first().id)
                                }
                            }
                            val recActive = recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.PASS)
                            if (recActive is Result.Failure) return Result.Failure(recActive.exception)
                        } catch (e: Throwable) {
                            recordStepStatus(deletion.wallet_id, DeletionStep.ACTIVE_POINTER, DeletionStepStatus.FAILED, e.message)
                            return Result.Failure(if (e is Exception) e else RuntimeException(e))
                        }

                        // Step 17: WALLET_DB_ROW
                        try {
                            walletQueries.delete(deletion.wallet_id)
                            val recDb = recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.PASS)
                            if (recDb is Result.Failure) return Result.Failure(recDb.exception)
                        } catch (e: Throwable) {
                            recordStepStatus(deletion.wallet_id, DeletionStep.WALLET_DB_ROW, DeletionStepStatus.FAILED, e.message)
                            return Result.Failure(if (e is Exception) e else RuntimeException(e))
                        }

                        val gateRes = assertAll17StepsPass(deletion.wallet_id)
                        if (gateRes is Result.Failure) {
                            return gateRes
                        }

                        val casComp = updateDeletionCas(deletion.wallet_id, DeletionState.RECOVERY_REQUIRED, DeletionState.COMPLETED)
                        if (casComp is Result.Failure) return casComp
                    }

                    DeletionState.COMPLETED -> {
                        // Already completed
                    }
                }
            }

            // 2b. Reconcile legacy tombstoned wallets not tracked in deletion_journal
            val tombstonedWallets = walletQueries.selectDeletionPending().executeAsList()
            for (tw in tombstonedWallets) {
                val keyExists = if (!tw.key_alias.isNullOrBlank()) {
                    secureKeyManager.hasPrivateKey(tw.key_alias)
                } else false

                if (!keyExists) {
                    val cleanupResult = perform17LayerCleanup(tw, tw.id)
                    if (cleanupResult is Result.Failure) {
                        return cleanupResult
                    }
                    walletQueries.transaction {
                        walletQueries.delete(tw.id)
                        if (tw.is_active != 0L) {
                            val remainingActive = walletQueries.selectAllActiveWallets().executeAsList()
                            if (remainingActive.isNotEmpty()) {
                                walletQueries.setActiveWallet(remainingActive.first().id)
                            }
                        }
                    }
                }
            }

            // 3. Purge completed deletion journals (24h retention)
            val now = Clock.System.now().toEpochMilliseconds()
            deletionJournalQueries?.purgeCompletedDeletions(now - 86_400_000L)

            // 4. Purge completed staging journals (24h retention)
            stagingJournalQueries?.purgeExpiredJournals(now - 86_400_000L)

            // 5. Final Zero-Pending Assertion before Ready
            val remainingStaging = stagingJournalQueries?.selectPendingJournals()?.executeAsList() ?: emptyList()
            if (remainingStaging.isNotEmpty()) {
                return Result.Failure(
                    IllegalStateException("Startup reconciliation incomplete: ${remainingStaging.size} pending staging journals remaining")
                )
            }

            val remainingDeletions = deletionJournalQueries?.selectPendingDeletions()?.executeAsList() ?: emptyList()
            if (remainingDeletions.isNotEmpty()) {
                return Result.Failure(
                    IllegalStateException("Startup reconciliation incomplete: ${remainingDeletions.size} pending deletion journals remaining")
                )
            }

            val remainingTombstones = walletQueries.selectDeletionPending().executeAsList()
            if (remainingTombstones.isNotEmpty()) {
                return Result.Failure(
                    IllegalStateException("Startup reconciliation incomplete: ${remainingTombstones.size} tombstoned wallets remaining")
                )
            }

            Result.Success(Unit)
        } catch (e: Throwable) {
            Result.Failure(if (e is Exception) e else RuntimeException(e))
        }
    }
    
    override fun observeWallets(): Flow<List<WalletAccount>> {
        return walletQueries.selectAllActiveWallets()
            .asFlow()
            .mapToList(kotlinx.coroutines.Dispatchers.Default)
            .map { wallets ->
                wallets.map { it.toWalletAccount() }
            }
    }
    
    override fun observeActiveWallet(): Flow<WalletAccount?> {
        return walletQueries.selectActiveWallet()
            .asFlow()
            .mapToOneOrNull(kotlinx.coroutines.Dispatchers.Default)
            .map { it?.toWalletAccount() }
    }
    
    /**
     * 獲取原生代幣餘額
     * 覆寫接口默認方法，實際調用 EthereumRpcClient
     */
    override suspend fun getNativeBalance(address: String, chainType: ChainType): Double {
        return try {
            val result = ethereumRpcClient.getNativeBalance(address, chainType)
            when (result) {
                is CoreResult.Success -> {
                    val hexBalance = result.data.removePrefix("0x")
                    if (hexBalance.isEmpty() || hexBalance == "0") {
                        0.0
                    } else {
                        // 使用 KMP 兼容的 BigInteger 處理大數字
                        val wei = com.ionspin.kotlin.bignum.integer.BigInteger.parseString(hexBalance, 16)
                        val divisor = com.ionspin.kotlin.bignum.integer.BigInteger.TEN.pow(18)
                        
                        // 使用 BigDecimal 進行精確除法
                        val weiDecimal = com.ionspin.kotlin.bignum.decimal.BigDecimal.fromBigInteger(wei)
                        val divisorDecimal = com.ionspin.kotlin.bignum.decimal.BigDecimal.fromBigInteger(divisor)
                        val ethValue = weiDecimal.divide(divisorDecimal, com.ionspin.kotlin.bignum.decimal.DecimalMode(
                            decimalPrecision = 18,
                            roundingMode = com.ionspin.kotlin.bignum.decimal.RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
                        )).doubleValue(false)
                        
                        println("[WalletRepository] getNativeBalance success: wei=$wei, eth=$ethValue")
                        ethValue
                    }
                }
                is CoreResult.Failure -> {
                    println("[WalletRepository] getNativeBalance failed: ${result.exception.message}")
                    0.0
                }
                else -> 0.0
            }
        } catch (e: Exception) {
            println("[WalletRepository] getNativeBalance error: ${e.message}")
            0.0
        }
    }
}

/**
 * 擴展函數：將數據庫 Wallet 轉換為領域模型 WalletAccount
 */
private fun Wallet.toWalletAccount(): WalletAccount {
    return WalletAccount(
        id = id.toString(),
        name = name,
        address = address,
        publicKey = public_key,
        keyAlias = key_alias,
        keyBackend = key_backend,
        keyFormatVersion = key_format_version.toInt(),
        requiresAuth = requires_auth != 0L,
        chainType = ChainType.valueOf(chain_type),
        walletType = WalletType.valueOf(wallet_type),
        isActive = is_active != 0L,
        isWatchOnly = is_watch_only != 0L,
        derivationPath = derivation_path,
        avatarId = avatar_id?.toString(),
        metadata = metadata,
        isDeletionPending = is_deletion_pending != 0L,
        createdAt = created_at,
        // Keystone 相關
        masterFingerprint = master_fingerprint,
        keystoneSignRequest = keystone_sign_request,
        keystoneSyncData = keystone_sync_data
    )
}

private fun String.hexToByteArray(): ByteArray {
    val clean = removePrefix("0x")
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

class ReconciliationRejectedException(
    val sessionId: String,
    val stagedAlias: String,
    override val message: String
) : IllegalStateException(message)

internal sealed class ReconciliationVerdict {
    data class SafeToRollback(
        val journal: com.cbstudio.wearwallet.core.database.Staging_journal,
        val state: ProvisioningState,
        val grant: com.cbstudio.wearwallet.core.security.RecoveryGrant
    ) : ReconciliationVerdict()

    data class ActiveWalletProtection(
        val journal: com.cbstudio.wearwallet.core.database.Staging_journal,
        val activeWalletId: Long
    ) : ReconciliationVerdict()

    data class Rejected(val reason: String) : ReconciliationVerdict()
}

internal fun validateReconciliationCandidate(
    journal: com.cbstudio.wearwallet.core.database.Staging_journal?,
    expectedSessionId: String,
    expectedKeyAlias: String,
    walletQueries: WalletQueries
): ReconciliationVerdict {
    // Layer 1: Verify journal entry exists in DB
    if (journal == null) {
        return ReconciliationVerdict.Rejected("Layer 1 Violation: Staging journal entry does not exist for session '$expectedSessionId'")
    }

    // Layer 2: Verify sessionId and alias match exactly
    if (journal.session_id != expectedSessionId || journal.staged_alias != expectedKeyAlias) {
        return ReconciliationVerdict.Rejected(
            "Layer 2 Violation: Session ID or Alias mismatch. Journal(session=${journal.session_id}, alias=${journal.staged_alias}) vs Candidate(session=$expectedSessionId, alias=$expectedKeyAlias)"
        )
    }

    // Layer 3: Verify state is a valid recoverable state
    val state = try {
        ProvisioningState.valueOf(journal.state)
    } catch (_: Exception) {
        return ReconciliationVerdict.Rejected("Layer 3 Violation: Unknown or corrupted provisioning state '${journal.state}'")
    }
    if (state !in listOf(ProvisioningState.PREPARED, ProvisioningState.KEY_STAGED, ProvisioningState.DB_WRITTEN, ProvisioningState.ROLLBACK_PENDING)) {
        return ReconciliationVerdict.Rejected("Layer 3 Violation: State '$state' is not a recoverable state")
    }

    // Layer 4: Verify alias is NOT referenced by any active wallet row in the DB (Fail-Closed on DB query error)
    val referencedWallet = try {
        walletQueries.selectByKeyAlias(expectedKeyAlias).executeAsOneOrNull()
    } catch (e: Throwable) {
        return ReconciliationVerdict.Rejected("Layer 4 Violation: DB query error: ${e.message}")
    }
    if (referencedWallet != null && referencedWallet.is_deletion_pending == 0L) {
        return ReconciliationVerdict.ActiveWalletProtection(journal, referencedWallet.id)
    }

    // Layer 5: Verify state is NOT COMMITTED or ROLLED_BACK
    if (state == ProvisioningState.COMMITTED || state == ProvisioningState.ROLLED_BACK) {
        return ReconciliationVerdict.Rejected("Layer 5 Violation: Journal is already in final state '$state'")
    }

    val rowHash = "${journal.session_id}:${journal.staged_alias}:${journal.state}"
    val grant = com.cbstudio.wearwallet.core.security.RecoveryGrant.create(
        journalRowHash = rowHash,
        sessionId = journal.session_id,
        alias = journal.staged_alias,
        state = journal.state,
        zeroActiveReferenceProof = "ZERO_ACTIVE_REF_VALIDATED"
    )
    com.cbstudio.wearwallet.core.security.RecoveryGrantRegistry.register(grant)

    return ReconciliationVerdict.SafeToRollback(journal, state, grant)
}