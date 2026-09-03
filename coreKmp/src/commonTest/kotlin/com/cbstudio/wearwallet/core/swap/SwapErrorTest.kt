package com.cbstudio.wearwallet.core.swap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwapErrorTest {

    @Test
    fun testFromException_InsufficientFunds() {
        val error = SwapError.fromException(Exception("insufficient funds for gas * price + value"))
        assertTrue(error is SwapError.InsufficientBalance)
        
        val errorChinese = SwapError.fromException(Exception("餘額不足"))
        assertTrue(errorChinese is SwapError.InsufficientBalance)
    }

    @Test
    fun testFromException_GasLow() {
        val error = SwapError.fromException(Exception("intrinsic gas too low"))
        assertTrue(error is SwapError.InsufficientGas)
    }
    
    @Test
    fun testFromException_Slippage() {
        val error = SwapError.fromException(Exception("Slippage too high"))
        assertTrue(error is SwapError.SlippageTooHigh)
    }
    
    @Test
    fun testFromException_NoRoute() {
        val error = SwapError.fromException(Exception("No route found for this swap"))
        assertTrue(error is SwapError.NoRouteFound)
    }
    
    @Test
    fun testFromException_NetworkError() {
        val error1 = SwapError.fromException(Exception("Socket timeout"))
        assertTrue(error1 is SwapError.NetworkError)
        
        val error2 = SwapError.fromException(Exception("Network connection lost"))
        assertTrue(error2 is SwapError.NetworkError)
    }
    
    @Test
    fun testFromException_Unknown() {
        val msg = "Some random error happened"
        val error = SwapError.fromException(Exception(msg))
        assertTrue(error is SwapError.Unknown)
        assertEquals(msg, error.message)
    }
}
