package com.cbstudio.wearwallet.core.domain.usecase.nft

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.nft.NftToken
import com.cbstudio.wearwallet.core.domain.repository.NftRepository
import com.cbstudio.wearwallet.core.network.blockchain.BlockchainApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class ManageNftsUseCaseTest {

    @Mock
    private lateinit var nftRepository: NftRepository
    
    @Mock
    private lateinit var blockchainApiClient: BlockchainApiClient

    private lateinit var manageNftsUseCase: ManageNftsUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        manageNftsUseCase = ManageNftsUseCase(nftRepository, blockchainApiClient)
    }

    @Test
    fun `addNft validates and creates NFT`() {
        runBlocking {
            val nft = createMockNft()
            Mockito.`when`(nftRepository.validateNft(nft)).thenReturn(Result.Success(true))
            Mockito.`when`(nftRepository.createNft(nft)).thenReturn(Result.Success(nft))

            val result = manageNftsUseCase.addNft(nft)

            assertTrue(result is Result.Success)
            verify(nftRepository).validateNft(nft)
            verify(nftRepository).createNft(nft)
        }
    }

    @Test
    fun `updateNft validates and updates NFT`() {
        runBlocking {
            val nft = createMockNft()
            Mockito.`when`(nftRepository.validateNft(nft)).thenReturn(Result.Success(true))
            Mockito.`when`(nftRepository.updateNft(nft)).thenReturn(Result.Success(nft))

            val result = manageNftsUseCase.updateNft(nft)

            assertTrue(result is Result.Success)
            verify(nftRepository).validateNft(nft)
            verify(nftRepository).updateNft(nft)
        }
    }

    @Test
    fun `toggleFavorite updates favorite status`() {
        runBlocking {
            val nftId = "nft_1"
            val nft = createMockNft(id = nftId, isFavorite = false)
            Mockito.`when`(nftRepository.getNft(nftId)).thenReturn(Result.Success(nft))
            Mockito.`when`(nftRepository.updateFavoriteStatus(nftId, true)).thenReturn(Result.Success(Unit))

            val result = manageNftsUseCase.toggleFavorite(nftId)

            assertTrue(result is Result.Success)
            verify(nftRepository).updateFavoriteStatus(nftId, true)
        }
    }
    
    @Test
    fun `toggleHidden updates hidden status`() {
        runBlocking {
            val nftId = "nft_1"
            val nft = createMockNft(id = nftId, isHidden = false)
            Mockito.`when`(nftRepository.getNft(nftId)).thenReturn(Result.Success(nft))
            Mockito.`when`(nftRepository.updateHiddenStatus(nftId, true)).thenReturn(Result.Success(Unit))

            val result = manageNftsUseCase.toggleHidden(nftId)

            assertTrue(result is Result.Success)
            verify(nftRepository).updateHiddenStatus(nftId, true)
        }
    }

    @Test
    fun `deleteNft calls repository delete`() {
        runBlocking {
            val nftId = "nft_1"
            Mockito.`when`(nftRepository.deleteNft(nftId)).thenReturn(Result.Success(Unit))

            val result = manageNftsUseCase.deleteNft(nftId)

            assertTrue(result is Result.Success)
            verify(nftRepository).deleteNft(nftId)
        }
    }

    private fun createMockNft(
        id: String = "1", 
        isFavorite: Boolean = false,
        isHidden: Boolean = false
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
            isFavorite = isFavorite,
            isHidden = isHidden
        )
    }
}
