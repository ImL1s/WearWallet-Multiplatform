package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AddressBookRepositoryImplPersistenceTest {

    private fun createDatabase(): CoreWalletDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CoreWalletDatabase.Schema.create(driver)
        return CoreWalletDatabase(driver)
    }

    @Test
    fun createContact_persistsNotesFavoriteAndVerified() = runTest {
        val database = createDatabase()
        val repository = AddressBookRepositoryImpl(database)

        val created = repository.createContact(
            AddressContact.create(
                name = "Alice",
                address = "0x1111111111111111111111111111111111111111",
                chainType = ChainType.ETHEREUM,
                notes = "VIP",
                isFavorite = true,
                isVerified = true
            )
        )

        assertIs<Result.Success<AddressContact>>(created)
        val contactId = created.data.id

        val reopened = AddressBookRepositoryImpl(database)
        val loaded = reopened.getContact(contactId)

        assertIs<Result.Success<AddressContact?>>(loaded)
        val contact = requireNotNull(loaded.data)
        assertEquals("VIP", contact.notes)
        assertTrue(contact.isFavorite)
        assertTrue(contact.isVerified)
    }

    @Test
    fun updateContact_persistsNotesFavoriteAndVerified() = runTest {
        val database = createDatabase()
        val repository = AddressBookRepositoryImpl(database)

        val created = repository.createContact(
            AddressContact.create(
                name = "Bob",
                address = "0x2222222222222222222222222222222222222222",
                chainType = ChainType.POLYGON,
                notes = "",
                isFavorite = false,
                isVerified = false
            )
        )
        assertIs<Result.Success<AddressContact>>(created)

        val updateResult = repository.updateContact(
            created.data.copy(
                notes = "exchange desk",
                isFavorite = true,
                isVerified = true,
                category = ContactCategory.EXCHANGE
            )
        )
        assertIs<Result.Success<AddressContact>>(updateResult)

        val reopened = AddressBookRepositoryImpl(database)
        val loaded = reopened.getContact(created.data.id)
        assertIs<Result.Success<AddressContact?>>(loaded)
        val contact = requireNotNull(loaded.data)
        assertEquals("exchange desk", contact.notes)
        assertTrue(contact.isFavorite)
        assertTrue(contact.isVerified)
        assertEquals(ContactCategory.EXCHANGE, contact.category)
    }
}
