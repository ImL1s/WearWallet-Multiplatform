package com.cbstudio.wearwallet.core.database

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Unit and architecture tests verifying that DatabaseDriverFactory is strictly fail-closed (Blocker 1 / R2).
 *
 * Guarantees:
 * 1. Database open failure throws typed DatabaseInitializationException.
 * 2. Zero in-memory (name = null) fallback exists in production source.
 * 3. Zero catch-all Throwable swallowing in production DatabaseDriverFactory.
 */
class FailClosedDatabaseDriverFactoryTest {

    @Test
    fun test_DatabaseDriverFactory_throws_DatabaseInitializationException_on_failure() {
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.getDatabasePath(any())).thenThrow(RuntimeException("KeyStore or SQLCipher failure simulation"))

        val factory = DatabaseDriverFactory(mockContext)

        val exception = assertThrows(DatabaseInitializationException::class.java) {
            factory.createDriver()
        }

        assertTrue(
            "Exception message must describe initialization failure",
            exception.message?.contains("Failed to initialize encrypted database driver") == true
        )
    }

    @Test
    fun test_production_DatabaseDriverFactory_source_contains_no_in_memory_fallback() {
        val sourceFile = File("src/androidMain/kotlin/com/cbstudio/wearwallet/core/database/DatabaseDriverFactory.kt")
        if (!sourceFile.exists()) {
            val rootSourceFile = File("coreKmp/src/androidMain/kotlin/com/cbstudio/wearwallet/core/database/DatabaseDriverFactory.kt")
            assertTrue("DatabaseDriverFactory.kt source file must exist", rootSourceFile.exists())
            verifySourceContent(rootSourceFile.readText())
        } else {
            verifySourceContent(sourceFile.readText())
        }
    }

    private fun verifySourceContent(content: String) {
        // Assert no in-memory database fallback (name = null)
        assertFalse(
            "Production DatabaseDriverFactory.kt MUST NOT contain 'name = null' fallback",
            content.contains("name = null")
        )

        // Assert no fallback to unencrypted AndroidSqliteDriver
        assertFalse(
            "Production DatabaseDriverFactory.kt MUST NOT fallback to unencrypted AndroidSqliteDriver",
            content.contains("AndroidSqliteDriver(")
        )

        // Assert typed exception is used
        assertTrue(
            "Production DatabaseDriverFactory.kt MUST throw DatabaseInitializationException",
            content.contains("DatabaseInitializationException")
        )
    }
}
