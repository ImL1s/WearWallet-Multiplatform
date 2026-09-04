package com.cbstudio.mobile.testing

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataItemBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import org.json.JSONObject

/**
 * Fake Message Client for Testing
 * 
 * Simulates the MessageClient for Watch-Phone communication testing
 * without needing actual device pairing.
 */
class FakeMessageClient {
    
    private val sentMessages = mutableListOf<SentMessage>()
    private val listeners = mutableListOf<MessageListener>()
    
    data class SentMessage(
        val nodeId: String,
        val path: String,
        val data: ByteArray
    )
    
    fun interface MessageListener {
        fun onMessageReceived(nodeId: String, path: String, data: ByteArray)
    }
    
    /**
     * Simulate sending a message to a node
     */
    fun sendMessage(nodeId: String, path: String, data: ByteArray): Result<Int> {
        sentMessages.add(SentMessage(nodeId, path, data))
        return Result.success(data.size)
    }
    
    /**
     * Send a JSON message (helper method)
     */
    fun sendJsonMessage(nodeId: String, path: String, json: JSONObject): Result<Int> {
        return sendMessage(nodeId, path, json.toString().toByteArray())
    }
    
    /**
     * Add a message listener
     */
    fun addListener(listener: MessageListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove a message listener
     */
    fun removeListener(listener: MessageListener) {
        listeners.remove(listener)
    }
    
    /**
     * Simulate receiving a message (for testing)
     */
    fun simulateMessageReceived(nodeId: String, path: String, data: ByteArray) {
        listeners.forEach { it.onMessageReceived(nodeId, path, data) }
    }
    
    /**
     * Simulate receiving a JSON message (for testing)
     */
    fun simulateJsonMessageReceived(nodeId: String, path: String, json: JSONObject) {
        simulateMessageReceived(nodeId, path, json.toString().toByteArray())
    }
    
    /**
     * Get all sent messages (for assertions)
     */
    fun getSentMessages(): List<SentMessage> = sentMessages.toList()
    
    /**
     * Clear sent messages
     */
    fun clearSentMessages() {
        sentMessages.clear()
    }
    
    /**
     * Get the last sent message
     */
    fun getLastSentMessage(): SentMessage? = sentMessages.lastOrNull()
}

/**
 * Fake Node Client for Testing
 * 
 * Simulates connected Wear OS nodes.
 */
class FakeNodeClient {
    
    private val connectedNodes = mutableListOf<FakeNode>()
    
    data class FakeNode(
        val id: String,
        val displayName: String,
        val isNearby: Boolean = true
    )
    
    /**
     * Add a connected node (for testing setup)
     */
    fun addConnectedNode(node: FakeNode) {
        connectedNodes.add(node)
    }
    
    /**
     * Remove a connected node
     */
    fun removeConnectedNode(nodeId: String) {
        connectedNodes.removeAll { it.id == nodeId }
    }
    
    /**
     * Get connected nodes
     */
    fun getConnectedNodes(): List<FakeNode> = connectedNodes.toList()
    
    /**
     * Check if any nodes are connected
     */
    fun hasConnectedNodes(): Boolean = connectedNodes.isNotEmpty()
    
    /**
     * Clear all nodes
     */
    fun clearNodes() {
        connectedNodes.clear()
    }
}

/**
 * Fake Data Client for Testing
 * 
 * Simulates the DataClient for synced data between Watch and Phone.
 */
class FakeDataClient {
    
    private val dataItems = mutableMapOf<String, ByteArray>()
    private val listeners = mutableListOf<DataChangeListener>()
    
    fun interface DataChangeListener {
        fun onDataChanged(path: String, data: ByteArray)
    }
    
    /**
     * Put data item
     */
    fun putDataItem(path: String, data: ByteArray): Result<Unit> {
        dataItems[path] = data
        listeners.forEach { it.onDataChanged(path, data) }
        return Result.success(Unit)
    }
    
    /**
     * Get data item
     */
    fun getDataItem(path: String): ByteArray? = dataItems[path]
    
    /**
     * Delete data item
     */
    fun deleteDataItem(path: String): Result<Unit> {
        dataItems.remove(path)
        return Result.success(Unit)
    }
    
    /**
     * Add data change listener
     */
    fun addListener(listener: DataChangeListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove data change listener
     */
    fun removeListener(listener: DataChangeListener) {
        listeners.remove(listener)
    }
    
    /**
     * Clear all data
     */
    fun clearAll() {
        dataItems.clear()
    }
    
    /**
     * Get all data paths
     */
    fun getAllPaths(): Set<String> = dataItems.keys.toSet()
}
