package com.cbstudio.wearwallet.core.domain.usecase.contact

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class ContactUseCasesTest {

    @Mock
    private lateinit var contactRepository: ContactRepository

    private lateinit var addContactUseCase: AddContactUseCase
    private lateinit var updateContactUseCase: UpdateContactUseCase
    private lateinit var deleteContactUseCase: DeleteContactUseCase
    private lateinit var getAllContactsUseCase: GetAllContactsUseCase
    private lateinit var getContactByIdUseCase: GetContactByIdUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        addContactUseCase = AddContactUseCase(contactRepository)
        updateContactUseCase = UpdateContactUseCase(contactRepository)
        deleteContactUseCase = DeleteContactUseCase(contactRepository)
        getAllContactsUseCase = GetAllContactsUseCase(contactRepository)
        getContactByIdUseCase = GetContactByIdUseCase(contactRepository)
    }

    @Test
    fun `AddContactUseCase calls insertContact on repository`() {
        runBlocking {
            val contact = Contact(
                id = "1",
                name = "Test User",
                address = "0x123",
                chainType = ChainType.ETHEREUM
            )

            addContactUseCase(contact)

            verify(contactRepository).insertContact(contact)
        }
    }

    @Test
    fun `UpdateContactUseCase calls updateContact on repository`() {
        runBlocking {
            val contact = Contact(
                id = "1",
                name = "Updated User",
                address = "0x123",
                chainType = ChainType.ETHEREUM
            )

            updateContactUseCase(contact)

            verify(contactRepository).updateContact(contact)
        }
    }

    @Test
    fun `DeleteContactUseCase calls deleteContact with contact object`() {
        runBlocking {
            val contact = Contact(
                id = "1",
                name = "Test User",
                address = "0x123",
                chainType = ChainType.ETHEREUM
            )

            deleteContactUseCase(contact)

            verify(contactRepository).deleteContact(contact)
        }
    }

    @Test
    fun `DeleteContactUseCase calls deleteContact with id`() {
        runBlocking {
            val contactId = "1"

            deleteContactUseCase(contactId)

            verify(contactRepository).deleteContact(contactId)
        }
    }

    @Test
    fun `GetAllContactsUseCase returns flow from repository`() {
        runBlocking {
            val contacts = listOf(
                Contact(id = "1", name = "User 1"),
                Contact(id = "2", name = "User 2")
            )
            Mockito.`when`(contactRepository.getAllContacts()).thenReturn(flowOf(contacts))

            val result = getAllContactsUseCase()

            result.collect {
                assertEquals(contacts, it)
            }
            verify(contactRepository).getAllContacts()
        }
    }

    @Test
    fun `GetContactByIdUseCase returns flow from repository`() {
        runBlocking {
            val contact = Contact(id = "1", name = "User 1")
            val contactId = "1"
            Mockito.`when`(contactRepository.getContactById(contactId)).thenReturn(flowOf(contact))

            val result = getContactByIdUseCase(contactId)

            result.collect {
                assertEquals(contact, it)
            }
            verify(contactRepository).getContactById(contactId)
        }
    }
}
