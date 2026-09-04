package com.cbstudio.wearwallet.presentation.wallet.screens.utxo

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.utxo.SendUTXOTransactionUseCase
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.TestPlatformAuthenticator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UTXOSendViewModelTest : KoinTest {

    private lateinit var viewModel: UTXOSendViewModel
    private lateinit var sendUTXOTransactionUseCase: SendUTXOTransactionUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sendUTXOTransactionUseCase = mockk(relaxed = true)
        startKoin {
            modules(module {
                single<WalletRepository> { mockk(relaxed = true) }
                single { mockk<UTXOApiClient>(relaxed = true) }
                single { sendUTXOTransactionUseCase }
            })
        }
        viewModel = UTXOSendViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `sendTransaction without auth fails closed and does not call use case`() = runTest {
        val password = "secret12".toCharArray()

        viewModel.sendTransaction(password, authContext = null)
        testScheduler.advanceUntilIdle()

        assertEquals("需要有效授權才能發送", viewModel.uiState.value.error)
        coVerify(exactly = 0) {
            sendUTXOTransactionUseCase(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `sendTransaction zeroizes password chars even when auth is missing`() = runTest {
        val password = "secret12".toCharArray()

        viewModel.sendTransaction(password, authContext = null)
        testScheduler.advanceUntilIdle()

        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun `sendTransaction with non-sign handle fails closed`() = runTest {
        val password = "secret12".toCharArray()
        val authContext = AuthenticationContext(
            authHandle = TestPlatformAuthenticator.issueHandle(
                keyId = "utxo_key",
                operation = AuthOperation.IMPORT
            )
        )

        viewModel.sendTransaction(password, authContext)
        testScheduler.advanceUntilIdle()

        assertEquals("需要有效授權才能發送", viewModel.uiState.value.error)
        coVerify(exactly = 0) {
            sendUTXOTransactionUseCase(any(), any(), any(), any(), any())
        }
    }
}
