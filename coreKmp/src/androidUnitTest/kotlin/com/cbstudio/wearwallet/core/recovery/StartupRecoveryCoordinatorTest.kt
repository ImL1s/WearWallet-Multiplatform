package com.cbstudio.wearwallet.core.recovery

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy
import com.cbstudio.wearwallet.core.security.KeyPair
import com.cbstudio.wearwallet.core.security.ProvisioningRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StartupRecoveryCoordinatorTest {

    private class MockWalletRepository : WalletRepository {
        var reconcileResult: Result<Unit> = Result.Success(Unit)
        var throwExceptionOnReconcile: Throwable? = null
        var reconcileCallCount = 0
        var reconcileDeferred: CompletableDeferred<Unit>? = null

        override suspend fun reconcileStartupState(): Result<Unit> {
            reconcileCallCount++
            reconcileDeferred?.await()
            throwExceptionOnReconcile?.let { throw it }
            return reconcileResult
        }

        override suspend fun prepareProvisioning(): Result<ProvisioningRequest> = Result.Failure(UnsupportedOperationException())
        override suspend fun createWallet(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importFromMnemonic(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importFromMnemonicWithKeyPair(name: String, mnemonic: CharArray, password: CharArray, chainType: ChainType, keyPair: KeyPair, address: String, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importFromPrivateKey(name: String, privateKey: com.cbstudio.wearwallet.core.security.ScopedPrivateKey, password: CharArray, chainType: ChainType, authContext: AuthenticationContext): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun importKeystoneWallet(name: String, xpub: String, derivationPath: String, masterFingerprint: String, chainType: ChainType, policy: ExtendedPublicKeyPolicy): Result<WalletAccount> = Result.Failure(UnsupportedOperationException())
        override suspend fun getAllWallets(): Result<List<WalletAccount>> = Result.Success(emptyList())
        override suspend fun getWallet(id: String): Result<WalletAccount?> = Result.Success(null)
        override suspend fun getWalletByAddress(address: String): Result<WalletAccount?> = Result.Success(null)
        override suspend fun getActiveWallet(): Result<WalletAccount?> = Result.Success(null)
        override suspend fun getKeystoneWallets(): Result<List<WalletAccount>> = Result.Success(emptyList())
        override suspend fun updateWallet(wallet: WalletAccount): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteWallet(id: String, authContext: AuthenticationContext?): Result<Unit> = Result.Success(Unit)
        override suspend fun setActiveWallet(walletId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun updateKeystoneData(walletId: String, signRequest: String?, syncData: String?): Result<Unit> = Result.Success(Unit)
        override fun observeWallets(): Flow<List<WalletAccount>> = emptyFlow()
        override fun observeActiveWallet(): Flow<WalletAccount?> = emptyFlow()
    }

    @Test
    fun testInitialStateIsInitializing() {
        val repo = MockWalletRepository()
        val coordinator = RealStartupRecoveryCoordinator(repo)

        assertEquals(StartupRecoveryState.Initializing, coordinator.state.value)
        assertNull(coordinator.reconciliationError.value)
        assertEquals(0, repo.reconcileCallCount)
    }

    @Test
    fun testStartReconciliationSuccessTransitionsToReady() = runBlocking {
        val repo = MockWalletRepository()
        repo.reconcileResult = Result.Success(Unit)
        val coordinator = RealStartupRecoveryCoordinator(repo)

        val finalState = coordinator.startReconciliation()

        assertEquals(StartupRecoveryState.Ready, finalState)
        assertEquals(StartupRecoveryState.Ready, coordinator.state.value)
        assertNull(coordinator.reconciliationError.value)
        assertEquals(1, repo.reconcileCallCount)
    }

    @Test
    fun testStartReconciliationFailureTransitionsToFailed() = runBlocking {
        val repo = MockWalletRepository()
        val failureException = IllegalStateException("Database disk I/O failure during reconciliation")
        repo.reconcileResult = Result.Failure(failureException)
        val coordinator = RealStartupRecoveryCoordinator(repo)

        val finalState = coordinator.startReconciliation()

        assertTrue(finalState is StartupRecoveryState.Failed)
        val failedState = finalState as StartupRecoveryState.Failed
        assertEquals("Database disk I/O failure during reconciliation", failedState.message)
        assertEquals(failureException, failedState.error)
        assertEquals(failureException, coordinator.reconciliationError.value)
        assertEquals(1, repo.reconcileCallCount)
    }

    @Test
    fun testStartReconciliationExceptionTransitionsToFailed() = runBlocking {
        val repo = MockWalletRepository()
        val uncaughtException = RuntimeException("Corrupted SQLite header")
        repo.throwExceptionOnReconcile = uncaughtException
        val coordinator = RealStartupRecoveryCoordinator(repo)

        val finalState = coordinator.startReconciliation()

        assertTrue(finalState is StartupRecoveryState.Failed)
        val failedState = finalState as StartupRecoveryState.Failed
        assertEquals(uncaughtException, failedState.error)
        assertEquals(uncaughtException, coordinator.reconciliationError.value)
        assertEquals(1, repo.reconcileCallCount)
    }

    @Test
    fun testAwaitReadyBlocksAndReturnsSuccessWhenReady() = runBlocking {
        val repo = MockWalletRepository()
        repo.reconcileResult = Result.Success(Unit)
        val coordinator = RealStartupRecoveryCoordinator(repo)

        val readyResult = coordinator.awaitReady()

        assertTrue(readyResult is Result.Success)
        assertEquals(StartupRecoveryState.Ready, coordinator.state.value)
    }

    @Test
    fun testAwaitReadyReturnsFailureWhenReconciliationFails() = runBlocking {
        val repo = MockWalletRepository()
        val error = IllegalStateException("CAS Mismatch on Staging Journal")
        repo.reconcileResult = Result.Failure(error)
        val coordinator = RealStartupRecoveryCoordinator(repo)

        val readyResult = coordinator.awaitReady()

        assertTrue(readyResult is Result.Failure)
        assertEquals(error.message, (readyResult as Result.Failure).exception.message)
        assertTrue(coordinator.state.value is StartupRecoveryState.Failed)
    }

    @Test
    fun testRetryReExecutesReconciliation() = runBlocking {
        val repo = MockWalletRepository()
        val error = IllegalStateException("Temporary DB lock")
        repo.reconcileResult = Result.Failure(error)
        val coordinator = RealStartupRecoveryCoordinator(repo)

        coordinator.startReconciliation()
        assertTrue(coordinator.state.value is StartupRecoveryState.Failed)
        assertEquals(1, repo.reconcileCallCount)

        // Now fix the repository state and retry
        repo.reconcileResult = Result.Success(Unit)
        val retryDeferred = CompletableDeferred<Unit>()
        val job = launch {
            coordinator.awaitReady()
            retryDeferred.complete(Unit)
        }

        coordinator.retry()
        retryDeferred.await()
        job.join()

        assertEquals(StartupRecoveryState.Ready, coordinator.state.value)
        assertNull(coordinator.reconciliationError.value)
        assertEquals(2, repo.reconcileCallCount)
    }

    @Test
    fun testConcurrentReconciliationExecutesSafelyUnderMutex() = runBlocking {
        val repo = MockWalletRepository()
        val barrier = CompletableDeferred<Unit>()
        repo.reconcileDeferred = barrier
        val coordinator = RealStartupRecoveryCoordinator(repo)

        // Launch 20 concurrent coroutines calling startReconciliation()
        val tasks = (1..20).map {
            async(Dispatchers.Default) {
                coordinator.startReconciliation()
            }
        }

        // Release the barrier so reconciliation can complete
        barrier.complete(Unit)
        val results = tasks.awaitAll()

        assertEquals(StartupRecoveryState.Ready, coordinator.state.value)
        results.forEach { state ->
            assertEquals(StartupRecoveryState.Ready, state)
        }
        // Exactly 1 call executed by the first holder of the mutex; subsequent calls see Ready
        assertEquals(1, repo.reconcileCallCount)
    }
}
