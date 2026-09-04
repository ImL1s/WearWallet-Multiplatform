package io.github.iml1s.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SecureStorageTest {

    // Mock implementation for testing logic flow
    class MockSecureStorage : SecureStorage {
        private val storage = mutableMapOf<String, String>()

        override suspend fun put(key: String, value: String) {
            storage[key] = value
        }

        override suspend fun get(key: String): String? {
            return storage[key]
        }

        override suspend fun delete(key: String) {
            storage.remove(key)
        }

        override suspend fun clear() {
            storage.clear()
        }
    }

    @Test
    fun testSecureStorageFlow() = runTest {
        val storage = MockSecureStorage()
        
        // 1. Put
        storage.put("key1", "secret_value")
        
        // 2. Get
        val retrieved = storage.get("key1")
        assertEquals("secret_value", retrieved)
        
        // 3. Delete
        storage.delete("key1")
        assertNull(storage.get("key1"))
        
        // 4. Clear
        storage.put("key2", "val2")
        storage.put("key3", "val3")
        storage.clear()
        assertNull(storage.get("key2"))
        assertNull(storage.get("key3"))
    }
}
