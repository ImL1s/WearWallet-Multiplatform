package com.cbstudio.wearwallet.presentation.screens.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiCommandClassifierTest {

    @Test
    fun `english check-balance chip is classified as balance`() {
        assertEquals(AiCommandKind.BALANCE, classifyAiCommand("check balance"))
    }

    @Test
    fun `traditional chinese balance command is classified`() {
        assertEquals(AiCommandKind.BALANCE, classifyAiCommand("查看餘額"))
    }

    @Test
    fun `english send chip is classified as send`() {
        assertEquals(AiCommandKind.SEND, classifyAiCommand("send 0.001 ETH"))
    }

    @Test
    fun `unknown stays unknown`() {
        assertEquals(AiCommandKind.UNKNOWN, classifyAiCommand("hello watch"))
    }
}
