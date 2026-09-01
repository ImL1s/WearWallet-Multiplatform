package com.cbstudio.wearwallet.core.domain.usecase.nft

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.nft.NftToken
import com.cbstudio.wearwallet.core.domain.repository.NftRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class GetNftsUseCaseTest {

    @Mock
    private lateinit var nftRepository: NftRepository

    private lateinit var getNftsUseCase: GetNftsUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getNftsUseCase = GetNftsUseCase(nftRepository)
    }

    @Test
    fun `getAllNfts returns result from repository`() {
        runBlocking {
            val nfts = listOf(createMockNft())
            Mockito.`when`(nftRepository.getAllNfts()).thenReturn(Result.Success(nfts))

            val result = getNftsUseCase.getAllNfts()

            assertTrue(result is Result.Success)
            assertEquals(nfts, (result as Result.Success).data)
            verify(nftRepository).getAllNfts()
        }
    }

    @Test
    fun `observeAllNfts returns flow from repository`() {
        val nfts = listOf(createMockNft())
        Mockito.`when`(nftRepository.observeAllNfts()).thenReturn(flowOf(nfts))

        runBlocking {
            val result = getNftsUseCase.observeAllNfts()

            result.collect {
                assertEquals(nfts, it)
            }
            verify(nftRepository).observeAllNfts()
        }
    }

    @Test
    fun `getNftsByWallet returns result from repository`() {
        runBlocking {
            val walletAddress = "0xWallet"
            val nfts = listOf(createMockNft())
            Mockito.`when`(nftRepository.getNftsByWalletAddress(walletAddress))
                .thenReturn(Result.Success(nfts))

            val result = getNftsUseCase.getNftsByWallet(walletAddress)

            assertTrue(result is Result.Success)
            assertEquals(nfts, (result as Result.Success).data)
        }
    }

    @Test
    fun `getNftsByChain returns result from repository`() {
        runBlocking {
            val chainType = ChainType.ETHEREUM
            val nfts = listOf(createMockNft())
            Mockito.`when`(nftRepository.getNftsByChainType(chainType))
                .thenReturn(Result.Success(nfts))

            val result = getNftsUseCase.getNftsByChain(chainType)

            assertTrue(result is Result.Success)
            assertEquals(nfts, (result as Result.Success).data)
        }
    }
    
    @Test
    fun `getFavoriteNfts returns result from repository`() {
        runBlocking {
            val nfts = listOf(createMockNft(isFavorite = true))
            Mockito.`when`(nftRepository.getFavoriteNfts()).thenReturn(Result.Success(nfts))

            val result = getNftsUseCase.getFavoriteNfts()

            assertTrue(result is Result.Success)
            assertEquals(nfts, (result as Result.Success).data)
        }
    }

    private fun createMockNft(
        id: String = "1", 
        isFavorite: Boolean = false
    ): NftToken {
        return NftToken(
            id = id,
            tokenId = "123",
            contractAddress = "0xContract",
            walletAddress = "0xWallet",
            chainType = ChainType.ETHEREUM,
            chainId = 1,
            createdAt = 1000L,
            updatedAt = 1000L,
            isFavorite = isFavorite
        )
    }
}
