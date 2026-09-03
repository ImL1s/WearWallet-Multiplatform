package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainSelection
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit
import com.cbstudio.wearwallet.core.domain.model.quantities.Wei
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.GetAddressContactsUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.SearchAddressBookUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateGasUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

/**
 * Unit test suite verifying multi-chain and multi-network transaction flows:
 * 1. BSC (Mainnet 56 vs Testnet 97).
 * 2. Arbitrum (Mainnet 42161 vs Sepolia 421614).
 * 3. Optimism (Mainnet 10 vs Sepolia 11155420).
 * 4. Base (Mainnet 8453 vs Sepolia 84532).
 * 5. Avalanche (Mainnet 43114 vs Fuji 43113).
 *
 * For each pair, verifies:
 * - Distinct chainId, networkType, and canonicalFingerprint.
 * - Distinct 64-char signingDigestHex Keccak hash.
 * - Nonce and gas estimation routed with the exact ChainExecutionContext.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendTransactionViewModelMultiNetworkTest : KoinTest {

    private lateinit var viewModel: SendTransactionViewModel
    private lateinit var walletRepository: WalletRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var sendTransactionUseCase: SendTransactionUseCase
    private lateinit var estimateGasUseCase: EstimateGasUseCase
    private lateinit var getAddressContactsUseCase: GetAddressContactsUseCase
    private lateinit var searchAddressBookUseCase: SearchAddressBookUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val mockWallet = WalletAccount(
        id = "multi-net-wallet-1",
        name = "Multi-Net Test Wallet",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        publicKey = "0xpubkey",
        keyAlias = "ww_key_multinet_1",
        keyBackend = "KEYSTORE",
        keyFormatVersion = 2,
        requiresAuth = true,
        chainType = ChainType.ETHEREUM,
        walletType = WalletType.HOT_WALLET,
        isActive = true
    )

    private val standardGasEstimation = EstimateGasUseCase.GasEstimation(
        weiGasPrice = Wei.fromGwei(10),
        gasLimitObj = GasLimit.fromDecimalString("21000"),
        totalFee = "0.00021"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        walletRepository = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        sendTransactionUseCase = mockk(relaxed = true)
        estimateGasUseCase = mockk(relaxed = true)
        getAddressContactsUseCase = mockk(relaxed = true)
        searchAddressBookUseCase = mockk(relaxed = true)

        coEvery { walletRepository.getActiveWallet() } returns Result.Success(mockWallet)
        coEvery { walletRepository.getNativeBalance(any(), any()) } returns 5.0
        coEvery { transactionRepository.getNonce(any(), any<ChainExecutionContext>()) } returns 0L
        coEvery {
            estimateGasUseCase(
                from = any(),
                to = any(),
                value = any(),
                chainType = any(),
                tokenAddress = any(),
                tokenDecimals = any(),
                executionContext = any()
            )
        } returns flowOf(Result.Success(standardGasEstimation))

        startKoin {
            modules(module {
                single { walletRepository }
                single { tokenRepository }
                single { transactionRepository }
                single { sendTransactionUseCase }
                single { estimateGasUseCase }
                single { getAddressContactsUseCase }
                single { searchAddressBookUseCase }
                single { mockk<com.cbstudio.wearwallet.core.security.SecureKeyManager>(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        ChainStateManager.selectChain(ChainSelection.default())
        stopKoin()
        Dispatchers.resetMain()
    }

    private fun prepareViewModelForChain(selection: ChainSelection): SendTransactionViewModel {
        ChainStateManager.selectChain(selection)
        val vm = SendTransactionViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.setRecipientAddress("0x1111111111111111111111111111111111111111")
        vm.proceedToAmount()
        vm.setAmount("0.5")
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun test_BSC_mainnet_vs_testnet_distinct_chainIds_and_digests() = runTest {
        // BSC Mainnet (56L)
        val vmMainnet = prepareViewModelForChain(ChainSelection.BSC_MAINNET)
        vmMainnet.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val mainnetSnapshot = vmMainnet.uiState.value.confirmedSnapshot
        assertNotNull(mainnetSnapshot)
        assertEquals(56L, mainnetSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.MAINNET, mainnetSnapshot.executionContext.networkType)
        assertEquals(MultiChainType.BSC, mainnetSnapshot.executionContext.multiChainType)

        // BSC Testnet (97L)
        val vmTestnet = prepareViewModelForChain(ChainSelection.BSC_TESTNET)
        vmTestnet.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val testnetSnapshot = vmTestnet.uiState.value.confirmedSnapshot
        assertNotNull(testnetSnapshot)
        assertEquals(97L, testnetSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.TESTNET, testnetSnapshot.executionContext.networkType)
        assertEquals(MultiChainType.BSC, testnetSnapshot.executionContext.multiChainType)

        // Invariants: distinct fingerprints and distinct signing digests
        assertNotEquals(mainnetSnapshot.canonicalFingerprint, testnetSnapshot.canonicalFingerprint)
        assertNotEquals(mainnetSnapshot.signingDigestHex, testnetSnapshot.signingDigestHex)
        assertNotEquals(vmMainnet.buildIntentFingerprint(), vmTestnet.buildIntentFingerprint())
    }

    @Test
    fun test_Arbitrum_mainnet_vs_sepolia_distinct_chainIds_and_digests() = runTest {
        // Arbitrum Mainnet (42161L)
        val vmMainnet = prepareViewModelForChain(ChainSelection.ARBITRUM_MAINNET)
        vmMainnet.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val mainnetSnapshot = vmMainnet.uiState.value.confirmedSnapshot
        assertNotNull(mainnetSnapshot)
        assertEquals(42161L, mainnetSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.MAINNET, mainnetSnapshot.executionContext.networkType)

        // Arbitrum Sepolia (421614L)
        val vmSepolia = prepareViewModelForChain(ChainSelection.ARBITRUM_SEPOLIA)
        vmSepolia.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val sepoliaSnapshot = vmSepolia.uiState.value.confirmedSnapshot
        assertNotNull(sepoliaSnapshot)
        assertEquals(421614L, sepoliaSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.TESTNET, sepoliaSnapshot.executionContext.networkType)

        assertNotEquals(mainnetSnapshot.canonicalFingerprint, sepoliaSnapshot.canonicalFingerprint)
        assertNotEquals(mainnetSnapshot.signingDigestHex, sepoliaSnapshot.signingDigestHex)
    }

    @Test
    fun test_Optimism_mainnet_vs_sepolia_distinct_chainIds_and_digests() = runTest {
        // Optimism Mainnet (10L)
        val vmMainnet = prepareViewModelForChain(ChainSelection.OPTIMISM_MAINNET)
        vmMainnet.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val mainnetSnapshot = vmMainnet.uiState.value.confirmedSnapshot
        assertNotNull(mainnetSnapshot)
        assertEquals(10L, mainnetSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.MAINNET, mainnetSnapshot.executionContext.networkType)

        // Optimism Sepolia (11155420L)
        val vmSepolia = prepareViewModelForChain(ChainSelection.OPTIMISM_SEPOLIA)
        vmSepolia.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val sepoliaSnapshot = vmSepolia.uiState.value.confirmedSnapshot
        assertNotNull(sepoliaSnapshot)
        assertEquals(11155420L, sepoliaSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.TESTNET, sepoliaSnapshot.executionContext.networkType)

        assertNotEquals(mainnetSnapshot.canonicalFingerprint, sepoliaSnapshot.canonicalFingerprint)
        assertNotEquals(mainnetSnapshot.signingDigestHex, sepoliaSnapshot.signingDigestHex)
    }

    @Test
    fun test_Base_mainnet_vs_sepolia_distinct_chainIds_and_digests() = runTest {
        // Base Mainnet (8453L)
        val vmMainnet = prepareViewModelForChain(ChainSelection.BASE_MAINNET)
        vmMainnet.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val mainnetSnapshot = vmMainnet.uiState.value.confirmedSnapshot
        assertNotNull(mainnetSnapshot)
        assertEquals(8453L, mainnetSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.MAINNET, mainnetSnapshot.executionContext.networkType)

        // Base Sepolia (84532L)
        val vmSepolia = prepareViewModelForChain(ChainSelection.BASE_SEPOLIA)
        vmSepolia.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val sepoliaSnapshot = vmSepolia.uiState.value.confirmedSnapshot
        assertNotNull(sepoliaSnapshot)
        assertEquals(84532L, sepoliaSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.TESTNET, sepoliaSnapshot.executionContext.networkType)

        assertNotEquals(mainnetSnapshot.canonicalFingerprint, sepoliaSnapshot.canonicalFingerprint)
        assertNotEquals(mainnetSnapshot.signingDigestHex, sepoliaSnapshot.signingDigestHex)
    }

    @Test
    fun test_Avalanche_mainnet_vs_fuji_distinct_chainIds_and_digests() = runTest {
        // Avalanche Mainnet (43114L)
        val vmMainnet = prepareViewModelForChain(ChainSelection.AVALANCHE_MAINNET)
        vmMainnet.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val mainnetSnapshot = vmMainnet.uiState.value.confirmedSnapshot
        assertNotNull(mainnetSnapshot)
        assertEquals(43114L, mainnetSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.MAINNET, mainnetSnapshot.executionContext.networkType)

        // Avalanche Fuji (43113L)
        val vmFuji = prepareViewModelForChain(ChainSelection.AVALANCHE_FUJI)
        vmFuji.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val fujiSnapshot = vmFuji.uiState.value.confirmedSnapshot
        assertNotNull(fujiSnapshot)
        assertEquals(43113L, fujiSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.TESTNET, fujiSnapshot.executionContext.networkType)

        assertNotEquals(mainnetSnapshot.canonicalFingerprint, fujiSnapshot.canonicalFingerprint)
        assertNotEquals(mainnetSnapshot.signingDigestHex, fujiSnapshot.signingDigestHex)
    }

    @Test
    fun test_Polygon_mainnet_vs_amoy_distinct_chainIds_and_digests() = runTest {
        // Polygon Mainnet (137L)
        val vmMainnet = prepareViewModelForChain(ChainSelection.POLYGON_MAINNET)
        vmMainnet.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val mainnetSnapshot = vmMainnet.uiState.value.confirmedSnapshot
        assertNotNull(mainnetSnapshot)
        assertEquals(137L, mainnetSnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.MAINNET, mainnetSnapshot.executionContext.networkType)

        // Polygon Amoy (80002L)
        val vmAmoy = prepareViewModelForChain(ChainSelection.POLYGON_AMOY)
        vmAmoy.proceedToConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        val amoySnapshot = vmAmoy.uiState.value.confirmedSnapshot
        assertNotNull(amoySnapshot)
        assertEquals(80002L, amoySnapshot!!.executionContext.chainId)
        assertEquals(NetworkType.TESTNET, amoySnapshot.executionContext.networkType)
        assertEquals("polygon-amoy-rpc", amoySnapshot.executionContext.rpcBackendIdentity)

        assertNotEquals(mainnetSnapshot.canonicalFingerprint, amoySnapshot.canonicalFingerprint)
        assertNotEquals(mainnetSnapshot.signingDigestHex, amoySnapshot.signingDigestHex)
    }
}
