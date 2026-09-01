package com.cbstudio.wearwallet.presentation.wallet.screens.addressbook

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.AddAddressContactUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddContactViewModelTest : KoinTest {

    private lateinit var viewModel: AddContactViewModel
    private lateinit var addAddressContactUseCase: AddAddressContactUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockContact = AddressContact(
        id = "1",
        name = "Alice",
        address = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
        chainType = ChainType.ETHEREUM,
        chainId = 1,
        category = ContactCategory.FRIEND,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        addAddressContactUseCase = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { addAddressContactUseCase }
            })
        }
        
        viewModel = AddContactViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `updateName should update state`() = runTest {
        viewModel.updateName("Alice")
        assertEquals("Alice", viewModel.uiState.value.name)
    }

    @Test
    fun `updateAddress with valid ETH address should set isAddressValid true`() = runTest {
        // Valid ETH address
        val validEthAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        viewModel.updateAddress(validEthAddress)
        
        assertTrue(viewModel.uiState.value.isAddressValid)
        assertEquals(validEthAddress, viewModel.uiState.value.address)
    }

    @Test
    fun `updateAddress with invalid ETH address should set isAddressValid false`() = runTest {
        viewModel.updateAddress("invalid-address")
        assertFalse(viewModel.uiState.value.isAddressValid)
    }

    @Test
    fun `updateChainType should re-validate address`() = runTest {
        // Given invalid ETH address but maybe valid for another chain (unlikely for specific invalid string, but logic check)
        // or setup valid ETH address, change to Bitcoin, should become invalid (format differs)
        val ethAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        viewModel.updateAddress(ethAddress)
        assertTrue(viewModel.uiState.value.isAddressValid)
        
        // When change to Bitcoin
        viewModel.updateChainType(ChainType.BITCOIN)
        
        // Then should be invalid for Bitcoin
        assertFalse(viewModel.uiState.value.isAddressValid)
        assertEquals(ChainType.BITCOIN, viewModel.uiState.value.chainType)
    }

    @Test
    fun `addTag and removeTag should update tags list`() = runTest {
        viewModel.addTag("tag1")
        viewModel.addTag("tag2")
        assertEquals(listOf("tag1", "tag2"), viewModel.uiState.value.tags)
        
        viewModel.removeTag("tag1")
        assertEquals(listOf("tag2"), viewModel.uiState.value.tags)
    }

    @Test
    fun `saveContact success should update state`() = runTest {
        // Given
        val validAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        viewModel.updateName("Alice")
        viewModel.updateAddress(validAddress)
        viewModel.updateChainType(ChainType.ETHEREUM)
        
        coEvery { 
            addAddressContactUseCase(any(), any(), any(), any(), any()) 
        } returns Result.Success(mockContact)
        
        // When
        viewModel.saveContact()
        testScheduler.advanceUntilIdle()
        
        // Then
        assertTrue(viewModel.uiState.value.contactSaved)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `saveContact failure should set error`() = runTest {
        // Given
        val validAddress = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F"
        viewModel.updateName("Alice")
        viewModel.updateAddress(validAddress)
        
        coEvery { 
            addAddressContactUseCase(any(), any(), any(), any(), any()) 
        } returns Result.Failure(Exception("Save Failed"))
        
        // When
        viewModel.saveContact()
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals("Save Failed", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.contactSaved)
    }
    
    @Test
    fun `saveContact validation failure`() = runTest {
        // Given empty name
        viewModel.updateName("")
        
        // When
        viewModel.saveContact()
        testScheduler.advanceUntilIdle()
        
        // Then
        assertEquals("請輸入聯絡人名稱", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { addAddressContactUseCase(any(), any(), any(), any(), any()) }
    }
}
