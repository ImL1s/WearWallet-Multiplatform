package com.cbstudio.wearwallet.core.utils

import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object ContactJsonSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    fun serialize(contact: Contact): String {
        return json.encodeToString(contact)
    }
    
    fun deserialize(jsonString: String): Contact {
        return json.decodeFromString(jsonString)
    }
    
    fun serializeList(contacts: List<Contact>): String {
        return json.encodeToString(contacts)
    }
    
    fun deserializeList(jsonString: String): List<Contact> {
        return json.decodeFromString(jsonString)
    }
    
    // 別名方法以保持向後相容
    fun jsonToContact(jsonString: String): Contact = deserialize(jsonString)
    fun jsonToContacts(jsonString: String): List<Contact> = deserializeList(jsonString)
    fun contactToJson(contact: Contact): String = serialize(contact)
    fun contactsToJson(contacts: List<Contact>): String = serializeList(contacts)
}