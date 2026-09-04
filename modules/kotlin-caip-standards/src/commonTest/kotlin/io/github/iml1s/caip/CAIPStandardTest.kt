package io.github.iml1s.caip

import io.github.iml1s.caip.validation.CAIPTestSuite
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CAIPStandardTest {

    @Test
    fun testFullTestSuite() = runTest {
        val suite = CAIPTestSuite()
        val results = suite.runFullTestSuite()
        
        println(results.summary)
        
        // Assert that all tests passed
        assertTrue(results.failedTests == 0, "CAIP Test Suite reported ${results.failedTests} failures:\n${results.summary}")
    }
}
