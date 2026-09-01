package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXOTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UTXOInput
import com.cbstudio.wearwallet.core.blockchain.model.UTXOOutput
import com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus as UTXOTransactionStatus
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.TransactionDirection
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.security.CryptoProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TransactionRepositoryTest {

    private lateinit var utxoApiClient: UTXOApiClient
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var rpcClient: EthereumRpcClient
    private lateinit var httpClient: HttpClient

    private val testAddress = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"

    @Before
    fun setup() {
        utxoApiClient = mock()
        cryptoProvider = mock()
        rpcClient = mock()
        
        val mockEngine = MockEngine { request ->
            respond(content = "{}", status = io.ktor.http.HttpStatusCode.OK)
        }
        httpClient = HttpClient(mockEngine)

        transactionRepository = TransactionRepositoryImpl(
            cryptoProvider = cryptoProvider,
            rpcClient = rpcClient,
            httpClient = httpClient,
            utxoApiClient = utxoApiClient
        )
    }

    @Test
    fun getTransactionHistory_utxo_maps_receive_transaction_correctly() = runBlocking {
        // Arrange
        val txId = "tx_hash_receive"
        val amount = 50000L
        val fee = 1000L
        val timestamp = Clock.System.now()

        val mockUtxoTx = UTXOTransaction(
            txId = txId,
            status = UTXOTransactionStatus.CONFIRMED,
            fee = fee,
            inputs = listOf(
                UTXOInput(address = "other_sender", value = amount + fee, vout = 0, txId = "prev_tx", scriptSig = "", sequence = 0)
            ),
            outputs = listOf(
                UTXOOutput(address = testAddress, value = amount, index = 0, scriptPubKey = "")
            ),
            timestamp = timestamp,
            confirmations = 6,
            chainType = ChainType.BITCOIN, // Explicitly set chain type
            blockHeight = 100,
            size = 200 // Mock size
        )

        whenever(utxoApiClient.getTransactionHistory(any(), any(), any(), any())).thenReturn(listOf(mockUtxoTx))

        // Act
        val transactions = transactionRepository.getTransactionHistory(testAddress, ChainType.BITCOIN)

        // Assert
        assertEquals(1, transactions.size)
        
        val tx = transactions.first()
        assertEquals(txId, tx.hash)
        // Since we are receiving (output == testAddress), it should be INCOMING
        assertEquals(TransactionDirection.INCOMING, tx.direction)
        assertEquals(amount.toString(), tx.value)
        assertEquals(8, tx.tokenDecimals) // BTC has 8 decimals
        assertEquals("BTC", tx.tokenSymbol)
    }

    @Test
    fun getTransactionHistory_utxo_maps_send_transaction_correctly() = runBlocking {
        // Arrange
        val txId = "tx_hash_send"
        val totalInput = 100000L
        val sendAmount = 50000L
        val fee = 2000L
        val changeAmount = totalInput - sendAmount - fee // 48000
        val timestamp = Clock.System.now()

        val mockUtxoTx = UTXOTransaction(
            txId = txId,
            status = UTXOTransactionStatus.CONFIRMED,
            fee = fee,
            inputs = listOf(
                UTXOInput(address = testAddress, value = totalInput, vout = 0, txId = "prev_tx", scriptSig = "", sequence = 0)
            ),
            outputs = listOf(
                UTXOOutput(address = "other_receiver", value = sendAmount, index = 0, scriptPubKey = ""),
                UTXOOutput(address = testAddress, value = changeAmount, index = 1, scriptPubKey = "") // Change back to self
            ),
            timestamp = timestamp,
            confirmations = 1,
            chainType = ChainType.LITECOIN, // Testing LTC
            blockHeight = 200,
            size = 220
        )

        whenever(utxoApiClient.getTransactionHistory(any(), any(), any(), any())).thenReturn(listOf(mockUtxoTx))

        // Act
        val transactions = transactionRepository.getTransactionHistory(testAddress, ChainType.LITECOIN)

        // Assert
        val tx = transactions.first()
        assertEquals(TransactionDirection.OUTGOING, tx.direction)
        // Logic: input(100000) - output_change(48000) - fee(2000) = 50000
        assertEquals(sendAmount.toString(), tx.value) 
        assertEquals(8, tx.tokenDecimals)
        assertEquals("LTC", tx.tokenSymbol)
    }

    @Test
    fun getTransactionHistory_utxo_maps_decimals_correctly() = runBlocking {
         // Arrange
         val timestamp = Clock.System.now()
         val mockBtcTx = UTXOTransaction(
             txId = "btc_tx", 
             status = UTXOTransactionStatus.CONFIRMED, fee=100, 
             inputs=emptyList(), outputs=listOf(UTXOOutput(0, 1000, "", testAddress)), 
             timestamp=timestamp, confirmations=1, chainType=ChainType.BITCOIN, blockHeight=1, 
             size=100
         )
         
         val mockDogeTx = UTXOTransaction(
             txId = "doge_tx", 
             status = UTXOTransactionStatus.CONFIRMED, fee=100, 
             inputs=emptyList(), outputs=listOf(UTXOOutput(0, 1000, "", testAddress)), 
             timestamp=timestamp, confirmations=1, chainType=ChainType.DOGECOIN, blockHeight=1, 
             size=100
         )

         whenever(utxoApiClient.getTransactionHistory(any(), org.mockito.kotlin.eq(ChainType.BITCOIN), any(), any()))
             .thenReturn(listOf(mockBtcTx))
             
         whenever(utxoApiClient.getTransactionHistory(any(), org.mockito.kotlin.eq(ChainType.DOGECOIN), any(), any()))
             .thenReturn(listOf(mockDogeTx))

         // Act & Assert
         val btcTx = transactionRepository.getTransactionHistory(testAddress, ChainType.BITCOIN).first()
         assertEquals(8, btcTx.tokenDecimals)

         val dogeTx = transactionRepository.getTransactionHistory(testAddress, ChainType.DOGECOIN).first()
         assertEquals(8, dogeTx.tokenDecimals)
    }
}
