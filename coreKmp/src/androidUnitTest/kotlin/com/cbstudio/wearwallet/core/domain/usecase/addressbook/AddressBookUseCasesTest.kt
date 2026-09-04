package com.cbstudio.wearwallet.core.domain.usecase.addressbook

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookFilter
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository
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

class AddressBookUseCasesTest {

    @Mock
    private lateinit var addressBookRepository: AddressBookRepository

    private lateinit var getAddressContactsUseCase: GetAddressContactsUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getAddressContactsUseCase = GetAddressContactsUseCase(addressBookRepository)
    }

    @Test
    fun `getAllContacts returns result from repository`() {
        runBlocking {
            val contacts = listOf(
                AddressContact.create("User 1", "0x1", ChainType.ETHEREUM)
            )
            Mockito.`when`(addressBookRepository.getAllContacts())
                .thenReturn(Result.Success(contacts))

            val result = getAddressContactsUseCase.getAllContacts()

            assertTrue(result is Result.Success)
            assertEquals(contacts, (result as Result.Success).data)
            verify(addressBookRepository).getAllContacts()
        }
    }

    @Test
    fun `observeAllContacts returns flow from repository`() {
        val contacts = listOf(
            AddressContact.create("User 1", "0x1", ChainType.ETHEREUM)
        )
        Mockito.`when`(addressBookRepository.observeAllContacts())
            .thenReturn(flowOf(contacts))

        runBlocking {
            val result = getAddressContactsUseCase.observeAllContacts()

            result.collect {
                assertEquals(contacts, it)
            }
            verify(addressBookRepository).observeAllContacts()
        }
    }

    @Test
    fun `getContactsByCategory calls repository`() {
        runBlocking {
            val category = ContactCategory.FRIEND
            val contacts = listOf(
                AddressContact.create("Friend", "0x1", ChainType.ETHEREUM, category = category)
            )
            Mockito.`when`(addressBookRepository.getContactsByCategory(category))
                .thenReturn(Result.Success(contacts))

            val result = getAddressContactsUseCase.getContactsByCategory(category)

            assertTrue(result is Result.Success)
            assertEquals(contacts, (result as Result.Success).data)
        }
    }

    @Test
    fun `getContactsByChainType calls repository`() {
        runBlocking {
            val chainType = ChainType.BSC
            val contacts = listOf(
                AddressContact.create("BSC User", "0x1", chainType)
            )
            Mockito.`when`(addressBookRepository.getContactsByChain(chainType))
                .thenReturn(Result.Success(contacts))

            val result = getAddressContactsUseCase.getContactsByChainType(chainType)

            assertTrue(result is Result.Success)
            assertEquals(contacts, (result as Result.Success).data)
        }
    }

    @Test
    fun `getFavoriteContacts calls repository`() {
        runBlocking {
            val contacts = listOf(
                AddressContact.create("Fav", "0x1", ChainType.ETHEREUM, isFavorite = true)
            )
            Mockito.`when`(addressBookRepository.getFavoriteContacts())
                .thenReturn(Result.Success(contacts))

            val result = getAddressContactsUseCase.getFavoriteContacts()

            assertTrue(result is Result.Success)
            assertEquals(contacts, (result as Result.Success).data)
        }
    }

    @Test
    fun `searchContacts with empty query calls getAllContacts`() {
        runBlocking {
            val contacts = listOf(AddressContact.create("User", "0x1", ChainType.ETHEREUM))
            Mockito.`when`(addressBookRepository.getAllContacts())
                .thenReturn(Result.Success(contacts))

            val result = getAddressContactsUseCase.searchContacts("  ")

            verify(addressBookRepository).getAllContacts()
            verify(addressBookRepository, Mockito.never()).searchContacts(Mockito.anyString())
            assertEquals(contacts, (result as Result.Success).data)
        }
    }

    @Test
    fun `searchContacts with query calls searchContacts on repository`() {
        runBlocking {
            val query = "User"
            val contacts = listOf(AddressContact.create("User", "0x1", ChainType.ETHEREUM))
            Mockito.`when`(addressBookRepository.searchContacts(query))
                .thenReturn(Result.Success(contacts))

            val result = getAddressContactsUseCase.searchContacts(query)

            verify(addressBookRepository).searchContacts(query)
            assertEquals(contacts, (result as Result.Success).data)
        }
    }
}
