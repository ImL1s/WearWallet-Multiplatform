package io.github.iml1s.crypto

import org.junit.Test
import kotlin.test.assertEquals

class AndroidBigIntegerTest {
    @Test
    fun testSimpleDivision() {
        // Test basic division
        val ten = 10
        val two = 2
        val result = ten / two
        assertEquals(5, result)
    }

    @Test
    fun testSimpleModulo() {
        // Test basic modulo
        val ten = 10
        val three = 3
        val result = ten % three
        assertEquals(1, result)
    }
}