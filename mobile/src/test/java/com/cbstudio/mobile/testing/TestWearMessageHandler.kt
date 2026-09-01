package com.cbstudio.mobile.testing

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Contact
import org.json.JSONArray
import org.json.JSONObject

/**
 * Message Handler Interface for Testing
 * 
 * Abstracts the message handling logic from WearListenerService
 * so it can be tested without Android dependencies.
 */
interface WearMessageHandler {
    
    /**
     * Handle incoming Keystone connect request
     */
    fun handleKeystoneConnectRequest(data: JSONObject): MessageHandlerResult
    
    /**
     * Handle incoming Keystone sign request
     */
    fun handleKeystoneSignRequest(data: JSONObject): MessageHandlerResult
    
    /**
     * Handle address book sync request
     */
    fun handleAddressBookSync(contactsJson: String): MessageHandlerResult
    
    /**
     * Handle address book add request
     */
    fun handleAddressBookAdd(contactJson: String): MessageHandlerResult
    
    /**
     * Handle address book update request
     */
    fun handleAddressBookUpdate(contactJson: String): MessageHandlerResult
    
    /**
     * Handle address book delete request
     */
    fun handleAddressBookDelete(contactId: String): MessageHandlerResult
}

/**
 * Result of message handling
 */
sealed class MessageHandlerResult {
    data class Success(val responseJson: JSONObject? = null) : MessageHandlerResult()
    data class Error(val errorCode: String, val message: String) : MessageHandlerResult()
    data class ActionRequired(val action: String, val params: Map<String, Any>) : MessageHandlerResult()
}

/**
 * Test implementation of WearMessageHandler
 * 
 * Used for testing message routing and response logic.
 */
class TestWearMessageHandler : WearMessageHandler {
    
    // Track handled messages for assertions
    val handledMessages = mutableListOf<HandledMessage>()
    val contacts = mutableListOf<Contact>()
    
    data class HandledMessage(
        val type: String,
        val data: Any?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    override fun handleKeystoneConnectRequest(data: JSONObject): MessageHandlerResult {
        handledMessages.add(HandledMessage("keystone_connect_request", data))
        
        // Return success with action required (need to launch camera)
        return MessageHandlerResult.ActionRequired(
            action = "launch_qr_scanner",
            params = mapOf(
                "mode" to "keystone_connect",
                "result_path" to "/keystone_connect_result"
            )
        )
    }
    
    override fun handleKeystoneSignRequest(data: JSONObject): MessageHandlerResult {
        handledMessages.add(HandledMessage("keystone_sign_request", data))
        
        val transactionData = data.optString("transactionData")
        val chainId = data.optInt("chainId", 1)
        
        if (transactionData.isEmpty()) {
            return MessageHandlerResult.Error(
                errorCode = "INVALID_TX_DATA",
                message = "Transaction data is required"
            )
        }
        
        return MessageHandlerResult.ActionRequired(
            action = "launch_qr_scanner",
            params = mapOf(
                "mode" to "keystone_sign",
                "result_path" to "/keystone_sign_result",
                "transaction_data" to transactionData,
                "chain_id" to chainId
            )
        )
    }
    
    override fun handleAddressBookSync(contactsJson: String): MessageHandlerResult {
        handledMessages.add(HandledMessage("address_book_sync", contactsJson))
        
        return try {
            val jsonArray = JSONArray(contactsJson)
            contacts.clear()
            
            for (i in 0 until jsonArray.length()) {
                val contactJson = jsonArray.getJSONObject(i)
                contacts.add(parseContact(contactJson))
            }
            
            MessageHandlerResult.Success(JSONObject().apply {
                put("synced_count", contacts.size)
                put("success", true)
            })
        } catch (e: Exception) {
            MessageHandlerResult.Error("PARSE_ERROR", "Failed to parse contacts: ${e.message}")
        }
    }
    
    override fun handleAddressBookAdd(contactJson: String): MessageHandlerResult {
        handledMessages.add(HandledMessage("address_book_add", contactJson))
        
        return try {
            val json = JSONObject(contactJson)
            val contact = parseContact(json)
            contacts.add(contact)
            
            MessageHandlerResult.Success(JSONObject().apply {
                put("added_id", contact.id)
                put("success", true)
            })
        } catch (e: Exception) {
            MessageHandlerResult.Error("PARSE_ERROR", "Failed to parse contact: ${e.message}")
        }
    }
    
    override fun handleAddressBookUpdate(contactJson: String): MessageHandlerResult {
        handledMessages.add(HandledMessage("address_book_update", contactJson))
        
        return try {
            val json = JSONObject(contactJson)
            val updatedContact = parseContact(json)
            
            val index = contacts.indexOfFirst { it.id == updatedContact.id }
            if (index >= 0) {
                contacts[index] = updatedContact
                MessageHandlerResult.Success(JSONObject().apply {
                    put("updated_id", updatedContact.id)
                    put("success", true)
                })
            } else {
                MessageHandlerResult.Error("NOT_FOUND", "Contact not found: ${updatedContact.id}")
            }
        } catch (e: Exception) {
            MessageHandlerResult.Error("PARSE_ERROR", "Failed to parse contact: ${e.message}")
        }
    }
    
    override fun handleAddressBookDelete(contactId: String): MessageHandlerResult {
        handledMessages.add(HandledMessage("address_book_delete", contactId))
        
        val removed = contacts.removeAll { it.id == contactId }
        return if (removed) {
            MessageHandlerResult.Success(JSONObject().apply {
                put("deleted_id", contactId)
                put("success", true)
            })
        } else {
            MessageHandlerResult.Error("NOT_FOUND", "Contact not found: $contactId")
        }
    }
    
    private fun parseContact(json: JSONObject): Contact {
        return Contact(
            id = json.getString("id"),
            name = json.getString("name"),
            address = json.getString("address"),
            chainType = ChainType.valueOf(json.optString("chainType", "ETHEREUM")),
            note = json.optString("note", null)
        )
    }
    
    // Helper methods for testing
    fun clearHandledMessages() {
        handledMessages.clear()
    }
    
    fun getLastHandledMessage(): HandledMessage? = handledMessages.lastOrNull()
    
    fun getHandledMessagesByType(type: String): List<HandledMessage> {
        return handledMessages.filter { it.type == type }
    }
}
