package com.cbstudio.wearwallet.core.domain.usecase.wallet

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.ScopedMnemonic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class DiscoveredAccount(
    val index: Int,
    val address: String,
    val path: String
)

class AccountDiscoveryUseCase(
    private val transactionRepository: TransactionRepository,
    private val cryptoProvider: CryptoProvider
) {
    
    suspend fun execute(
        mnemonic: CharArray,
        chainType: ChainType,
        gapLimit: Int = 5
    ): Flow<Result<List<DiscoveredAccount>>> = flow {
        emit(Result.Loading())
        val mnemCopy = mnemonic.copyOf()
        try {
            val discoveredAccounts = mutableListOf<DiscoveredAccount>()
            var consecutiveEmptyCount = 0
            var accountIndex = 0
            
            val coinType = chainType.getCoinType()
            
            while (consecutiveEmptyCount < gapLimit) {
                val derivationPath = "m/44'/$coinType'/$accountIndex'/0/0"
                
                val keyPair = cryptoProvider.generateKeyPairFromMnemonic(
                    mnemonic = mnemCopy,
                    derivationPath = derivationPath,
                    chainType = chainType
                )
                val address = cryptoProvider.deriveAddress(keyPair.publicKey)
                
                val history = transactionRepository.getTransactionHistory(address, chainType)
                
                if (history.isNotEmpty()) {
                    discoveredAccounts.add(
                        DiscoveredAccount(
                            index = accountIndex,
                            address = address,
                            path = derivationPath
                        )
                    )
                    consecutiveEmptyCount = 0
                } else {
                    consecutiveEmptyCount++
                }
                
                accountIndex++
                
                if (accountIndex > 100) {
                    break
                }
            }
            
            emit(Result.Success(discoveredAccounts))
            
        } catch (e: Exception) {
            emit(Result.Failure(e))
        } finally {
            mnemCopy.fill('\u0000')
        }
    }

    suspend fun execute(
        scopedMnemonic: ScopedMnemonic,
        chainType: ChainType,
        gapLimit: Int = 5
    ): Flow<Result<List<DiscoveredAccount>>> = scopedMnemonic.use {
        execute(it, chainType, gapLimit)
    }
}
