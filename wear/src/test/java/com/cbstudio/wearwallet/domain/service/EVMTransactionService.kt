package com.cbstudio.wearwallet.domain.service

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.security.CapabilityGate

/**
 * Test fixture for EVMTransactionService (purged from production).
 * Used exclusively in test scopes to verify exclusion from production DI and graphs.
 */
class EVMTransactionService(
    private val rpcClient: EthereumRpcClient,
    private val capabilityGate: CapabilityGate
) {
    suspend fun sendTransaction(
        from: String,
        to: String,
        value: String,
        data: String = "0x",
        privateKey: String,
        chainType: ChainType,
        gasLimit: String? = null,
        gasPrice: String? = null
    ): Result<String> {
        return Result.Failure(UnsupportedOperationException("EVMTransactionService is disabled and purged from production"))
    }
}
