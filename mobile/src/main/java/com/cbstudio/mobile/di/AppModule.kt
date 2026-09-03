package com.cbstudio.mobile.di

import android.content.Context
// import com.cbstudio.mobile.domain.usecase.contact.*
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository
import com.cbstudio.wearwallet.core.domain.model.Contact
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // 簡化的 Repository 實現 - 暫時返回空實現避免 Room Database 依賴
    @Provides
    @Singleton
    fun provideContactRepository(): ContactRepository {
        return object : ContactRepository {
            override fun getAllContacts() = kotlinx.coroutines.flow.flowOf(emptyList<Contact>())
            override fun getContactById(contactId: String) = kotlinx.coroutines.flow.flowOf(null)
            override suspend fun getContactsByAddress(address: String) = emptyList<Contact>()
            override suspend fun insertContact(contact: Contact) {}
            override suspend fun updateContact(contact: Contact) {}
            override suspend fun deleteContact(contact: Contact) {}
            override suspend fun deleteContact(contactId: String) {}
            override suspend fun deleteAllContacts() {}
        }
    }
    
    // Contact UseCase 提供者
    @Provides
    @Singleton
    fun provideGetAllContactsUseCase(
        contactRepository: ContactRepository
    ): com.cbstudio.wearwallet.core.domain.usecase.contact.GetAllContactsUseCase {
        return com.cbstudio.wearwallet.core.domain.usecase.contact.GetAllContactsUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideGetContactByIdUseCase(
        contactRepository: ContactRepository
    ): com.cbstudio.wearwallet.core.domain.usecase.contact.GetContactByIdUseCase {
        return com.cbstudio.wearwallet.core.domain.usecase.contact.GetContactByIdUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideAddContactUseCase(
        contactRepository: ContactRepository
    ): com.cbstudio.wearwallet.core.domain.usecase.contact.AddContactUseCase {
        return com.cbstudio.wearwallet.core.domain.usecase.contact.AddContactUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideUpdateContactUseCase(
        contactRepository: ContactRepository
    ): com.cbstudio.wearwallet.core.domain.usecase.contact.UpdateContactUseCase {
        return com.cbstudio.wearwallet.core.domain.usecase.contact.UpdateContactUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideDeleteContactUseCase(
        contactRepository: ContactRepository
    ): com.cbstudio.wearwallet.core.domain.usecase.contact.DeleteContactUseCase {
        return com.cbstudio.wearwallet.core.domain.usecase.contact.DeleteContactUseCase(contactRepository)
    }
    
    // KMP FIRST：Legacy UseCase 提供者 - 保留備用
    /*
    @Provides
    @Singleton
    fun provideGetAllContactsUseCase(
        contactRepository: ContactRepository
    ): GetAllContactsUseCase {
        return GetAllContactsUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideGetContactByIdUseCase(
        contactRepository: ContactRepository
    ): GetContactByIdUseCase {
        return GetContactByIdUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideAddContactUseCase(
        contactRepository: ContactRepository
    ): AddContactUseCase {
        return AddContactUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideUpdateContactUseCase(
        contactRepository: ContactRepository
    ): UpdateContactUseCase {
        return UpdateContactUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideDeleteContactUseCase(
        contactRepository: ContactRepository
    ): DeleteContactUseCase {
        return DeleteContactUseCase(contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideGetContactsByAddressUseCase(
        contactRepository: ContactRepository
    ): GetContactsByAddressUseCase {
        return GetContactsByAddressUseCase(contactRepository)
    }
    */
}