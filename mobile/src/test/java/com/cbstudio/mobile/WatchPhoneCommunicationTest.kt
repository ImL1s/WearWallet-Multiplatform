package com.cbstudio.mobile

import com.cbstudio.mobile.testing.FakeDataClient
import com.cbstudio.mobile.testing.FakeMessageClient
import com.cbstudio.mobile.testing.FakeNodeClient
import com.cbstudio.mobile.testing.MessageHandlerResult
import com.cbstudio.mobile.testing.TestWearMessageHandler
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration Tests for Watch-Phone Communication
 * 
 * Tests the complete message flow between Watch and Phone
 * using fake clients (no real device needed).
 */
class WatchPhoneCommunicationTest {
    
    private lateinit var fakeMessageClient: FakeMessageClient
    private lateinit var fakeNodeClient: FakeNodeClient
    private lateinit var fakeDataClient: FakeDataClient
    private lateinit var messageHandler: TestWearMessageHandler
    
    @Before
    fun setup() {
        fakeMessageClient = FakeMessageClient()
        fakeNodeClient = FakeNodeClient()
        fakeDataClient = FakeDataClient()
        messageHandler = TestWearMessageHandler()
        
        // Setup a connected watch node
        fakeNodeClient.addConnectedNode(
            FakeNodeClient.FakeNode(
                id = "watch-node-123",
                displayName = "Wear OS Watch",
                isNearby = true
            )
        )
    }
    
    // ==================== Connection Tests ====================
    
    @Test
    fun testWatchNodeConnected() {
        assertTrue(fakeNodeClient.hasConnectedNodes())
        assertEquals(1, fakeNodeClient.getConnectedNodes().size)
        assertEquals("watch-node-123", fakeNodeClient.getConnectedNodes()[0].id)
    }
    
    @Test
    fun testMultipleNodesConnected() {
        fakeNodeClient.addConnectedNode(
            FakeNodeClient.FakeNode(
                id = "watch-node-456",
                displayName = "Second Watch"
            )
        )
        
        assertEquals(2, fakeNodeClient.getConnectedNodes().size)
    }
    
    @Test
    fun testNodeDisconnection() {
        fakeNodeClient.removeConnectedNode("watch-node-123")
        assertFalse(fakeNodeClient.hasConnectedNodes())
    }
    
    // ==================== Message Sending Tests ====================
    
    @Test
    fun testSendMessageToWatch() {
        val result = fakeMessageClient.sendMessage(
            nodeId = "watch-node-123",
            path = "/test_path",
            data = "test data".toByteArray()
        )
        
        assertTrue(result.isSuccess)
        assertEquals(1, fakeMessageClient.getSentMessages().size)
        
        val sentMessage = fakeMessageClient.getLastSentMessage()
        assertNotNull(sentMessage)
        assertEquals("watch-node-123", sentMessage?.nodeId)
        assertEquals("/test_path", sentMessage?.path)
    }
    
    @Test
    fun testSendJsonMessageToWatch() {
        val json = JSONObject().apply {
            put("type", "keystone_connect_result")
            put("success", true)
            put("urData", "ur:crypto-hdkey/1-1/test")
        }
        
        val result = fakeMessageClient.sendJsonMessage(
            nodeId = "watch-node-123",
            path = "/keystone_result",
            json = json
        )
        
        assertTrue(result.isSuccess)
        
        val sentMessage = fakeMessageClient.getLastSentMessage()
        val sentJson = JSONObject(String(sentMessage!!.data))
        assertEquals("keystone_connect_result", sentJson.getString("type"))
        assertTrue(sentJson.getBoolean("success"))
    }
    
    // ==================== Message Receiving Tests ====================
    
    @Test
    fun testReceiveMessageFromWatch() {
        var receivedPath: String? = null
        var receivedData: ByteArray? = null
        
        fakeMessageClient.addListener { _, path, data ->
            receivedPath = path
            receivedData = data
        }
        
        fakeMessageClient.simulateMessageReceived(
            nodeId = "watch-node-123",
            path = "/qr_scan_request",
            data = "request".toByteArray()
        )
        
        assertEquals("/qr_scan_request", receivedPath)
        assertNotNull(receivedData)
    }
    
    @Test
    fun testReceiveJsonMessageFromWatch() {
        var receivedJson: JSONObject? = null
        
        fakeMessageClient.addListener { _, _, data ->
            receivedJson = JSONObject(String(data))
        }
        
        val requestJson = JSONObject().apply {
            put("type", "keystone_connect_request")
            put("timestamp", System.currentTimeMillis())
        }
        
        fakeMessageClient.simulateJsonMessageReceived(
            nodeId = "watch-node-123",
            path = "/wc_message",
            json = requestJson
        )
        
        assertNotNull(receivedJson)
        assertEquals("keystone_connect_request", receivedJson?.getString("type"))
    }
    
    // ==================== Keystone Integration Tests ====================
    
    @Test
    fun testKeystoneConnectRequestHandling() {
        val requestData = JSONObject().apply {
            put("action", "scan_keystone_connect")
        }
        
        val result = messageHandler.handleKeystoneConnectRequest(requestData)
        
        assertTrue(result is MessageHandlerResult.ActionRequired)
        val actionResult = result as MessageHandlerResult.ActionRequired
        assertEquals("launch_qr_scanner", actionResult.action)
        assertEquals("keystone_connect", actionResult.params["mode"])
    }
    
    @Test
    fun testKeystoneSignRequestHandling() {
        val requestData = JSONObject().apply {
            put("transactionData", "0x1234567890abcdef")
            put("chainId", 1)
            put("fromAddress", "0xabcdef")
        }
        
        val result = messageHandler.handleKeystoneSignRequest(requestData)
        
        assertTrue(result is MessageHandlerResult.ActionRequired)
        val actionResult = result as MessageHandlerResult.ActionRequired
        assertEquals("launch_qr_scanner", actionResult.action)
        assertEquals("keystone_sign", actionResult.params["mode"])
        assertEquals(1, actionResult.params["chain_id"])
    }
    
    @Test
    fun testKeystoneSignRequestWithMissingData() {
        val requestData = JSONObject() // Missing transactionData
        
        val result = messageHandler.handleKeystoneSignRequest(requestData)
        
        assertTrue(result is MessageHandlerResult.Error)
        val errorResult = result as MessageHandlerResult.Error
        assertEquals("INVALID_TX_DATA", errorResult.errorCode)
    }
    
    // ==================== Address Book Sync Tests ====================
    
    @Test
    fun testAddressBookSyncHandling() {
        val contactsJson = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "contact-1")
                put("name", "Alice")
                put("address", "0x1234567890123456789012345678901234567890")
                put("chainType", "ETHEREUM")
            })
            put(JSONObject().apply {
                put("id", "contact-2")
                put("name", "Bob")
                put("address", "0xabcdef1234567890abcdef1234567890abcdef12")
                put("chainType", "BSC")
            })
        }.toString()
        
        val result = messageHandler.handleAddressBookSync(contactsJson)
        
        assertTrue(result is MessageHandlerResult.Success)
        assertEquals(2, messageHandler.contacts.size)
        assertEquals("Alice", messageHandler.contacts[0].name)
        assertEquals("Bob", messageHandler.contacts[1].name)
    }
    
    @Test
    fun testAddressBookAddHandling() {
        val contactJson = JSONObject().apply {
            put("id", "new-contact")
            put("name", "Charlie")
            put("address", "0x9999999999999999999999999999999999999999")
            put("chainType", "POLYGON")
            put("note", "Test note")
        }.toString()
        
        val result = messageHandler.handleAddressBookAdd(contactJson)
        
        assertTrue(result is MessageHandlerResult.Success)
        assertEquals(1, messageHandler.contacts.size)
        assertEquals("Charlie", messageHandler.contacts[0].name)
        assertEquals("Test note", messageHandler.contacts[0].note)
    }
    
    @Test
    fun testAddressBookUpdateHandling() {
        // First add a contact
        messageHandler.handleAddressBookAdd(
            JSONObject().apply {
                put("id", "update-test")
                put("name", "Original Name")
                put("address", "0x1111111111111111111111111111111111111111")
                put("chainType", "ETHEREUM")
            }.toString()
        )
        
        // Then update it
        val result = messageHandler.handleAddressBookUpdate(
            JSONObject().apply {
                put("id", "update-test")
                put("name", "Updated Name")
                put("address", "0x1111111111111111111111111111111111111111")
                put("chainType", "ETHEREUM")
            }.toString()
        )
        
        assertTrue(result is MessageHandlerResult.Success)
        assertEquals(1, messageHandler.contacts.size)
        assertEquals("Updated Name", messageHandler.contacts[0].name)
    }
    
    @Test
    fun testAddressBookDeleteHandling() {
        // First add a contact
        messageHandler.handleAddressBookAdd(
            JSONObject().apply {
                put("id", "delete-test")
                put("name", "To Delete")
                put("address", "0x2222222222222222222222222222222222222222")
                put("chainType", "ETHEREUM")
            }.toString()
        )
        
        assertEquals(1, messageHandler.contacts.size)
        
        // Then delete it
        val result = messageHandler.handleAddressBookDelete("delete-test")
        
        assertTrue(result is MessageHandlerResult.Success)
        assertEquals(0, messageHandler.contacts.size)
    }
    
    @Test
    fun testAddressBookDeleteNotFound() {
        val result = messageHandler.handleAddressBookDelete("non-existent-id")
        
        assertTrue(result is MessageHandlerResult.Error)
        assertEquals("NOT_FOUND", (result as MessageHandlerResult.Error).errorCode)
    }
    
    // ==================== Data Client Tests ====================
    
    @Test
    fun testDataSyncBetweenDevices() {
        // Simulate phone putting data
        fakeDataClient.putDataItem(
            path = "/wallet_address",
            data = "0x1234567890abcdef".toByteArray()
        )
        
        // Watch retrieves data
        val data = fakeDataClient.getDataItem("/wallet_address")
        
        assertNotNull(data)
        assertEquals("0x1234567890abcdef", String(data!!))
    }
    
    @Test
    fun testDataChangeListener() {
        var changedPath: String? = null
        var changedData: ByteArray? = null
        
        fakeDataClient.addListener { path, data ->
            changedPath = path
            changedData = data
        }
        
        fakeDataClient.putDataItem(
            path = "/balance",
            data = "1.5 ETH".toByteArray()
        )
        
        assertEquals("/balance", changedPath)
        assertEquals("1.5 ETH", String(changedData!!))
    }
    
    // ==================== Complete Flow Tests ====================
    
    @Test
    fun testCompleteKeystoneConnectFlow() = runTest {
        // 1. Watch sends connect request
        val connectRequest = JSONObject().apply {
            put("type", "keystone_connect_request")
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject().apply {
                put("action", "scan_keystone_connect")
            })
        }
        
        fakeMessageClient.sendJsonMessage(
            nodeId = "watch-node-123",
            path = "/wc_message",
            json = connectRequest
        )
        
        // 2. Phone handles request
        val handleResult = messageHandler.handleKeystoneConnectRequest(
            connectRequest.getJSONObject("data")
        )
        
        assertTrue(handleResult is MessageHandlerResult.ActionRequired)
        
        // 3. Phone sends result back (after QR scan)
        val resultMessage = JSONObject().apply {
            put("type", "keystone_connect_result")
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject().apply {
                put("urData", "ur:crypto-hdkey/1-1/test-hdkey-data")
                put("success", true)
                put("isUrProtocol", true)
            })
        }
        
        fakeMessageClient.sendJsonMessage(
            nodeId = "watch-node-123",
            path = "/wc_result",
            json = resultMessage
        )
        
        // 4. Verify messages were sent correctly
        assertEquals(2, fakeMessageClient.getSentMessages().size)
        
        val lastMessage = fakeMessageClient.getLastSentMessage()
        val lastJson = JSONObject(String(lastMessage!!.data))
        assertEquals("keystone_connect_result", lastJson.getString("type"))
        assertTrue(lastJson.getJSONObject("data").getBoolean("success"))
    }
    
    @Test
    fun testCompleteAddressBookSyncFlow() = runTest {
        // 1. Watch requests sync
        val syncRequest = JSONObject().apply {
            put("type", "address_book_sync_request")
            put("timestamp", System.currentTimeMillis())
        }
        
        // 2. Phone provides contacts
        val contacts = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1")
                put("name", "Alice")
                put("address", "0x1234")
                put("chainType", "ETHEREUM")
            })
            put(JSONObject().apply {
                put("id", "2")
                put("name", "Bob")
                put("address", "0x5678")
                put("chainType", "BSC")
            })
        }
        
        // 3. Phone syncs contacts
        val syncResult = messageHandler.handleAddressBookSync(contacts.toString())
        
        assertTrue(syncResult is MessageHandlerResult.Success)
        
        // 4. Verify contacts are synced
        assertEquals(2, messageHandler.contacts.size)
        
        // 5. Phone sends sync completion to watch
        val syncCompleteMessage = JSONObject().apply {
            put("type", "address_book_sync_complete")
            put("data", JSONObject().apply {
                put("count", messageHandler.contacts.size)
                put("success", true)
            })
        }
        
        fakeMessageClient.sendJsonMessage(
            nodeId = "watch-node-123",
            path = "/address_book_result",
            json = syncCompleteMessage
        )
        
        val lastMessage = fakeMessageClient.getLastSentMessage()
        val lastJson = JSONObject(String(lastMessage!!.data))
        assertEquals(2, lastJson.getJSONObject("data").getInt("count"))
    }
}
