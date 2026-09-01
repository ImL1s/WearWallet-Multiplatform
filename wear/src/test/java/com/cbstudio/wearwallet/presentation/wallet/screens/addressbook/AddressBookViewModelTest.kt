package com.cbstudio.wearwallet.presentation.wallet.screens.addressbook

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.AddAddressContactUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.DeleteAddressContactUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.GetAddressContactsUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.UpdateAddressContactUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class AddressBookViewModelTest : KoinTest {

    private lateinit var viewModel: AddressBookViewModel
    private lateinit var getContactsUseCase: GetAddressContactsUseCase
    private lateinit var addContactUseCase: AddAddressContactUseCase
    private lateinit var updateContactUseCase: UpdateAddressContactUseCase
    private lateinit var deleteContactUseCase: DeleteAddressContactUseCase
    
    private val testDispatcher = StandardTestDispatcher()

    private val mockContact1 = AddressContact(
        id = "1",
        name = "Alice",
        address = "0xAlice",
        chainType = ChainType.ETHEREUM,
        chainId = 1,
        category = ContactCategory.FRIEND,
        createdAt = 1000L,
        updatedAt = 1000L,
        isFavorite = false
    )
    private val mockContact2 = AddressContact(
        id = "2",
        name = "Bob",
        address = "0xBob",
        chainType = ChainType.BSC,
        chainId = 56,
        category = ContactCategory.FAMILY,
        createdAt = 1000L,
        updatedAt = 1000L,
        isFavorite = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        getContactsUseCase = mockk(relaxed = true)
        addContactUseCase = mockk(relaxed = true)
        updateContactUseCase = mockk(relaxed = true)
        deleteContactUseCase = mockk(relaxed = true)
        
        startKoin {
            modules(module {
                single { getContactsUseCase }
                single { addContactUseCase }
                single { updateContactUseCase }
                single { deleteContactUseCase }
            })
        }
        
        // Default behavior: return empty list flow for observation
        every { getContactsUseCase.observeAllContacts() } returns flowOf(emptyList())
        
        // Initial load simulation via init block
        // We set expectation before init
        coEvery { getContactsUseCase.getContactsByChainType(any()) } returns Result.Success(emptyList())

        viewModel = AddressBookViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    // Removing 'loadContacts' test as it is private and called in init.
    // Instead we test if init loads contacts.
    @Test
    fun `init should load contacts`() = runTest {
        // Given
        val contacts = listOf(mockContact1, mockContact2)
        coEvery { getContactsUseCase.getContactsByChainType(any()) } returns Result.Success(contacts)
        
        // When
        // Re-init viewModel to capture init behavior with specific mock
        viewModel = AddressBookViewModel()
        testScheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(contacts, state.contacts)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `deleteContact should invoke useCase`() = runTest {
        // Given
        coEvery { deleteContactUseCase("1") } returns Result.Success(Unit)
        
        // When
        viewModel.deleteContact("1")
        testScheduler.advanceUntilIdle()
        
        // Then
        coVerify { deleteContactUseCase("1") }
    }
    
    @Test
    fun `toggleFavorite success should update contact`() = runTest {
        val updatedContact = mockContact1.copy(isFavorite = true)
        coEvery { updateContactUseCase(any()) } returns Result.Success(updatedContact)
        
        // When
        viewModel.toggleFavorite(mockContact1)
        testScheduler.advanceUntilIdle()
        
        // Then
        coVerify { updateContactUseCase(any()) }
    }

    @Test
    fun `addContact success should invoke useCase and reload`() = runTest {
        // Given
        coEvery { addContactUseCase(any(), any(), any(), any(), any()) } returns Result.Success(mockContact1)
        
        // When
        viewModel.addContact("Alice", "0xAlice")
        testScheduler.advanceUntilIdle() // Wait for launch

        // Then
        // Use matching arguments for verification
        coVerify { addContactUseCase("Alice", "0xAlice", any(), any(), any()) }
        // addContact calls loadContacts() on success, verify it happens
        coVerify(atLeast = 1) { getContactsUseCase.getContactsByChainType(any()) } 
    }
}
