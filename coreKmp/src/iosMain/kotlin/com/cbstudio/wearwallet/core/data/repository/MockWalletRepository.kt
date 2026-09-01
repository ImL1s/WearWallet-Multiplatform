package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Fail-closed repository for iOS/watchOS pending SQLDelight integration
 */
class MockWalletRepository : WalletRepository {
    
    private val wallets = mutableListOf<WalletAccount>()
    private val activeWalletFlow = MutableStateFlow<WalletAccount?>(null)
    
    override suspend fun createWallet(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Result<WalletAccount> {
        return Result.Failure(UnsupportedOperationException("MockWalletRepository createWallet is disabled in production source set."))
    }

    override suspend fun importFromMnemonic(
        name: String, 
        mnemonic: CharArray, 
        password: CharArray,
        chainType: ChainType,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Result<WalletAccount> {
        return Result.Failure(UnsupportedOperationException("MockWalletRepository importFromMnemonic is disabled in production source set."))
    }
    
    override suspend fun importFromMnemonicWithKeyPair(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType,
        keyPair: com.cbstudio.wearwallet.core.security.KeyPair,
        address: String,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Result<WalletAccount> {
        return Result.Failure(UnsupportedOperationException("MockWalletRepository importFromMnemonicWithKeyPair is disabled in production source set."))
    }

    override suspend fun importFromPrivateKey(
        name: String,
        privateKey: com.cbstudio.wearwallet.core.security.ScopedPrivateKey,
        password: CharArray,
        chainType: ChainType,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Result<WalletAccount> {
        return Result.Failure(UnsupportedOperationException("MockWalletRepository importFromPrivateKey is disabled in production source set."))
    }
    
    override suspend fun importKeystoneWallet(
        name: String,
        xpub: String,
        derivationPath: String,
        masterFingerprint: String,
        chainType: ChainType,
        policy: com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy
    ): Result<WalletAccount> {
        return Result.Failure(UnsupportedOperationException("MockWalletRepository importKeystoneWallet is disabled in production source set."))
    }
    
    override suspend fun getAllWallets(): Result<List<WalletAccount>> {
        return Result.Success(wallets.toList())
    }
    
    override suspend fun getWallet(id: String): Result<WalletAccount?> {
        return Result.Success(wallets.find { it.id == id })
    }
    
    override suspend fun getWalletByAddress(address: String): Result<WalletAccount?> {
        return Result.Success(wallets.find { it.address == address })
    }
    
    override suspend fun getActiveWallet(): Result<WalletAccount?> {
        return Result.Success(wallets.find { it.isActive })
    }
    
    override suspend fun getKeystoneWallets(): Result<List<WalletAccount>> {
        return Result.Success(wallets.filter { it.walletType == WalletType.KEYSTONE || it.walletType == WalletType.KEYSTONE_COLD })
    }
    
    override suspend fun updateWallet(wallet: WalletAccount): Result<Unit> {
        val index = wallets.indexOfFirst { it.id == wallet.id }
        if (index >= 0) {
            wallets[index] = wallet
            if (wallet.isActive) {
                activeWalletFlow.value = wallet
            }
        }
        return Result.Success(Unit)
    }
    
    override suspend fun deleteWallet(id: String, authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext?): Result<Unit> {
        val iterator = wallets.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == id) {
                iterator.remove()
                break
            }
        }
        if (activeWalletFlow.value?.id == id) {
            activeWalletFlow.value = wallets.firstOrNull()
        }
        return Result.Success(Unit)
    }
    
    override suspend fun setActiveWallet(walletId: String): Result<Unit> {
        wallets.forEachIndexed { index, wallet ->
            wallets[index] = wallet.copy(isActive = wallet.id == walletId)
        }
        activeWalletFlow.value = wallets.find { it.id == walletId }
        return Result.Success(Unit)
    }
    
    override suspend fun updateKeystoneData(
        walletId: String,
        signRequest: String?,
        syncData: String?
    ): Result<Unit> {
        return Result.Failure(UnsupportedOperationException("MockWalletRepository updateKeystoneData is disabled."))
    }
    
    override fun observeWallets(): Flow<List<WalletAccount>> {
        return flowOf(wallets.toList())
    }
    
    override fun observeActiveWallet(): Flow<WalletAccount?> {
        return activeWalletFlow
    }
}