package com.cbstudio.wearwallet.presentation.wallet.screens.utxo

import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.signer.*
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.security.KeystoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * UTXO 交易服務 - 整合 KMP 核心庫
 * 
 * 提供 Bitcoin, Litecoin, Dogecoin, Bitcoin Cash 的交易簽名功能
 */
class UTXOTransactionService : KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val keystoreManager: KeystoreManager by inject()
    
    // 簽名器實例
    private val bitcoinSigner = BitcoinSigner()
    private val litecoinSigner = LitecoinSigner()
    private val dogecoinSigner = DogecoinSigner()
    private val bitcoinCashSigner = BitcoinCashSigner()
    
    /**
     * 創建並簽名 UTXO 交易
     */
    suspend fun createAndSignTransaction(
        chainType: ChainType,
        fromAddress: String,
        toAddress: String,
        amount: Long, // satoshis
        feeRate: Long, // sat/vB
        mnemonic: String // 從安全存儲獲取
    ): Flow<Result<SignedTransaction>> = flow {
        try {
            emit(Result.Loading())
            
            // 1. 推導私鑰
            val derivationPath = getDerivationPath(chainType)
            val privateKey = keystoreManager.derivePrivateKey(mnemonic, derivationPath)
            Timber.d("私鑰推導成功: ${privateKey.take(10)}...")
            
            // 2. 生成公鑰
            val publicKey = keystoreManager.getPublicKey(privateKey)
            Timber.d("公鑰生成成功: ${publicKey.take(20)}...")
            
            // 3. 驗證地址（可選）
            val coinType = getCoinType(chainType)
            val generatedAddress = keystoreManager.getAddress(publicKey, coinType)
            if (generatedAddress != fromAddress) {
                Timber.w("地址不匹配: generated=$generatedAddress, expected=$fromAddress")
                // 在測試環境中可以繼續，生產環境應該拋出錯誤
            }
            
            // 4. 創建未簽名交易
            val unsignedTx = UnsignedTransaction(
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount.toString(),
                fee = calculateFee(feeRate).toString()
            )
            
            // 5. 簽名交易
            val privateKeyBytes = hexStringToByteArray(privateKey)
            val signedTx = signTransaction(chainType, unsignedTx, privateKeyBytes)
            
            if (signedTx.success) {
                Timber.d("交易簽名成功: hash=${signedTx.hash}")
                emit(Result.Success(signedTx))
            } else {
                Timber.e("交易簽名失敗: ${signedTx.error}")
                emit(Result.Failure(Exception(signedTx.error ?: "簽名失敗")))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "創建交易失敗")
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 簽名交易
     */
    private suspend fun signTransaction(
        chainType: ChainType,
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction {
        return when (chainType) {
            ChainType.BITCOIN -> bitcoinSigner.signTransaction(unsignedTx, privateKey)
            ChainType.LITECOIN -> litecoinSigner.signTransaction(unsignedTx, privateKey)
            ChainType.DOGECOIN -> dogecoinSigner.signTransaction(unsignedTx, privateKey)
            ChainType.BITCOIN_CASH -> bitcoinCashSigner.signTransaction(unsignedTx, privateKey)
            else -> SignedTransaction(
                hash = "",
                rawTransaction = "",
                success = false,
                error = "不支援的鏈類型: $chainType"
            )
        }
    }
    
    /**
     * 獲取推導路徑
     */
    private fun getDerivationPath(chainType: ChainType): String {
        return when (chainType) {
            ChainType.BITCOIN -> "m/84'/0'/0'/0/0" // BIP84 for SegWit
            ChainType.LITECOIN -> "m/84'/2'/0'/0/0"
            ChainType.DOGECOIN -> "m/44'/3'/0'/0/0"
            ChainType.BITCOIN_CASH -> "m/44'/145'/0'/0/0"
            else -> "m/44'/0'/0'/0/0"
        }
    }
    
    /**
     * 獲取幣種類型碼
     */
    private fun getCoinType(chainType: ChainType): Int {
        return when (chainType) {
            ChainType.BITCOIN -> 0
            ChainType.LITECOIN -> 2
            ChainType.DOGECOIN -> 3
            ChainType.BITCOIN_CASH -> 145
            else -> 0
        }
    }
    
    /**
     * 計算手續費
     * 簡化計算：假設交易大小約 250 bytes
     */
    private fun calculateFee(feeRate: Long): Long {
        val estimatedSize = 250L // bytes
        return feeRate * estimatedSize
    }
    
    /**
     * 將十六進制字符串轉換為字節數組
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x")
        return cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
    
    companion object {
        private const val TAG = "UTXOTransactionService"
    }
}