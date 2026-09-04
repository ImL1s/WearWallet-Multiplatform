package com.cbstudio.wearwallet.core.e2e

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.data.repository.TokenRepositoryImpl
import com.cbstudio.wearwallet.core.security.CryptoService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock

/**
 * Comprehensive Wallet Functionality Test Suite
 * 
 * Tests:
 * 1. Native balance fetching
 * 2. Token balance fetching  
 * 3. Address generation
 * 4. Multi-chain configuration
 */
class ComprehensiveWalletTest {
    
    companion object {
        const val MNEMONIC_1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        const val MNEMONIC_2 = "iron mind drip glad load second merge rough music cloud fresh heavy"
        const val USDT_ADDRESS = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val BALANCE_HEX = "0x2386f26fc10000"
        const val TOKEN_BALANCE_HEX = "0x5f5e100"
    }
    
    private fun createMockRpcClient(responseHex: String): EthereumRpcClient {
        val rpcResponse = """{"jsonrpc": "2.0", "id": 1, "result": "$responseHex"}"""
        val mockEngine = MockEngine { respond(
            content = ByteReadChannel(rpcResponse),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )}
        return EthereumRpcClient(HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        })
    }

    // ==================== 1. Native Balance Tests ====================
    
    @Test
    fun nativeBalance_ethereum_fetchesCorrectly() = runBlocking {
        val rpcClient = createMockRpcClient(BALANCE_HEX)
        val result = rpcClient.getNativeBalance("0xTest", ChainType.ETHEREUM)
        assertTrue("Should fetch ETH balance", result.isSuccess())
    }
    
    @Test
    fun nativeBalance_allEvmChains_work() = runBlocking {
        val rpcClient = createMockRpcClient(BALANCE_HEX)
        val chains = listOf(
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.BASE,
            ChainType.AVALANCHE, ChainType.FANTOM, ChainType.CRONOS
        )
        chains.forEach { chain ->
            val result = rpcClient.getNativeBalance("0xTest", chain)
            assertTrue("${chain.displayName} should work", result.isSuccess())
        }
        println("All ${chains.size} EVM chains work ✅")
    }
    
    // ==================== 2. Token Balance Tests ====================
    
    @Test
    fun tokenBalance_erc20_fetchesCorrectly() = runBlocking {
        val rpcClient = createMockRpcClient(TOKEN_BALANCE_HEX)
        val priceApiClient = mock<com.cbstudio.wearwallet.core.network.PriceApiClient>()
        val tokenRepository = TokenRepositoryImpl(
            rpcClient = rpcClient,
            priceApiClient = priceApiClient,
            database = com.cbstudio.wearwallet.core.database.createInMemoryCoreWalletDatabase()
        )
        
        val balance = tokenRepository.getTokenBalance(
            walletAddress = "0xTest",
            tokenAddress = USDT_ADDRESS,
            chainType = ChainType.ETHEREUM
        )
        
        assertNotNull("Token balance should not be null", balance)
        println("Token balance: $balance ✅")
    }
    
    @Test
    fun tokenBalance_multipleChains_work() = runBlocking {
        val rpcClient = createMockRpcClient(TOKEN_BALANCE_HEX)
        val priceApiClient = mock<com.cbstudio.wearwallet.core.network.PriceApiClient>()
        val tokenRepository = TokenRepositoryImpl(
            rpcClient = rpcClient,
            priceApiClient = priceApiClient,
            database = com.cbstudio.wearwallet.core.database.createInMemoryCoreWalletDatabase()
        )
        
        listOf(ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON).forEach { chain ->
            val balance = tokenRepository.getTokenBalance("0xTest", "0xToken", chain)
            assertNotNull("${chain.displayName} token balance should work", balance)
        }
        println("Token balance works on all chains ✅")
    }
    
    // ==================== 3. Address Derivation Tests ====================
    
    @Test
    fun addressDerivation_ethereum_fromPublicKey() {
        val cryptoService = CryptoService()
        val publicKey = "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737"
        
        val address = cryptoService.deriveAddress(publicKey, ChainType.ETHEREUM)
        
        assertTrue("Address should start with 0x", address.startsWith("0x"))
        assertEquals("Address should be 42 chars", 42, address.length)
        println("Address derivation: $address ✅")
    }
    
    @Test
    fun chainType_allHaveValidDerivationPath() {
        val evmChains = ChainType.entries.filter { it.isEVM() }
        evmChains.forEach { chain ->
            val path = chain.getDefaultDerivationPath()
            assertTrue("${chain.name} should have valid path", path.startsWith("m/44'/"))
        }
        println("All ${evmChains.size} chains have valid derivation paths ✅")
    }
    
    // ==================== 4. Multi-Chain Config Tests ====================
    
    @Test
    fun allEvmChains_haveRequiredConfig() {
        val allChains = ChainType.entries.filter { it.isEVM() }
        allChains.forEach { chain ->
            assertTrue("${chain.name} displayName", chain.displayName.isNotBlank())
            assertTrue("${chain.name} nativeToken", chain.nativeToken.isNotBlank())
        }
        println("All ${allChains.size} EVM chains have valid config ✅")
    }
    
    @Test
    fun testnets_identifiedCorrectly() {
        assertTrue(ChainType.SEPOLIA.isTestnet())
        assertTrue(ChainType.GOERLI.isTestnet())
        assertFalse(ChainType.ETHEREUM.isTestnet())
        assertFalse(ChainType.BSC.isTestnet())
        println("Testnet identification works ✅")
    }
}
