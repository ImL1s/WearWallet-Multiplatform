package com.cbstudio.wearwallet.core.recovery

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 阻塞式 StartupRecoveryCoordinator 真實實作
 *
 * 保證：
 * 1. Mutex 併發保護：多個 Caller 並行呼叫 startReconciliation() 時，唯有單一對帳流程在執行，其餘等待現有結果。
 * 2. 嚴格 Fail-Closed：WalletRepository.reconcileStartupState() 任何 Failure/Exception 均會轉移至 FAILED 狀態，並保存 reconciliationError。
 * 3. 阻塞 awaitReady()：Caller 會被掛起直到狀態脫離 INITIALIZING 與 RECONCILING。
 */
class RealStartupRecoveryCoordinator(
    private val walletRepository: WalletRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : StartupRecoveryCoordinator {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<StartupRecoveryState>(StartupRecoveryState.Initializing)
    override val state: StateFlow<StartupRecoveryState> = _state.asStateFlow()

    private val _reconciliationError = MutableStateFlow<Throwable?>(null)
    override val reconciliationError: StateFlow<Throwable?> = _reconciliationError.asStateFlow()

    override suspend fun startReconciliation(): StartupRecoveryState {
        mutex.withLock {
            // 若已經處於 Ready，直接返回
            if (_state.value is StartupRecoveryState.Ready) {
                return _state.value
            }

            _state.value = StartupRecoveryState.Reconciling(stage = "Reconciling startup state", progress = 0.1f)
            _reconciliationError.value = null

            try {
                when (val result = walletRepository.reconcileStartupState()) {
                    is Result.Success -> {
                        _state.value = StartupRecoveryState.Ready
                        _reconciliationError.value = null
                    }
                    is Result.Failure -> {
                        val ex = result.exception
                        _reconciliationError.value = ex
                        _state.value = StartupRecoveryState.Failed(
                            error = ex,
                            message = ex.message ?: "Startup reconciliation failed"
                        )
                    }
                    is Result.Loading -> {
                        // Keep reconciling
                    }
                }
            } catch (e: Throwable) {
                _reconciliationError.value = e
                _state.value = StartupRecoveryState.Failed(
                    error = e,
                    message = e.message ?: "Unexpected error during startup reconciliation"
                )
            }
            return _state.value
        }
    }

    override suspend fun awaitReady(): Result<Unit> {
        // 若當前仍處於 Initializing，自動啟動對帳
        if (_state.value is StartupRecoveryState.Initializing) {
            startReconciliation()
        }

        val terminalState = _state.first { s ->
            s !is StartupRecoveryState.Initializing && s !is StartupRecoveryState.Reconciling
        }

        return when (terminalState) {
            is StartupRecoveryState.Ready -> Result.Success(Unit)
            is StartupRecoveryState.Failed -> Result.Failure(
                if (terminalState.error is Exception) terminalState.error else RuntimeException(terminalState.error)
            )
            is StartupRecoveryState.RecoveryRequired -> Result.Failure(
                IllegalStateException("Recovery required: ${terminalState.reason}")
            )
            else -> Result.Failure(IllegalStateException("Unexpected startup state: $terminalState"))
        }
    }

    override fun retry() {
        scope.launch {
            if (_state.value is StartupRecoveryState.Failed || _state.value is StartupRecoveryState.RecoveryRequired) {
                _state.value = StartupRecoveryState.Initializing
            }
            startReconciliation()
        }
    }
}
