package com.cbstudio.wearwallet.core.domain.model.intent

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.ionspin.kotlin.bignum.integer.BigInteger
import org.junit.Assert.*
import org.junit.Test

class ConfirmedEvmTransactionIntentTest {

    private val walletId = "wallet_test_123"
    private val keyAlias = "wallet_test_123_alias"
    private val sender = EvmAddress.fromString("0x9d8A62f656a8d1615C1294fd71e9CFb3E4855A4F")
    private val recipient = EvmAddress.fromString("0x3535353535353535353535353535353535353535")
    private val chain = MultiChainType.ETHEREUM
    private val executionContext = ChainExecutionContextRegistry.resolve(MultiChainType.ETHEREUM, NetworkType.MAINNET)
    private val envelopeType = EvmEnvelope.LEGACY
    private val humanAmount = "1.5"
    private val baseUnitAmount = BaseUnitAmount.fromDecimalString("1.5", 18)
    private val nativeValue = Wei.fromWei(baseUnitAmount.value)
    private val calldata = Calldata.EMPTY
    private val nonce = Nonce.fromLong(5L)
    private val gasPrice = Wei.fromWeiDecimal("20000000000") // 20 Gwei
    private val gasLimit = GasLimit.fromLong(21000L)
    private val fee = Wei.fromWei(gasPrice.value * BigInteger.fromLong(gasLimit.value))

    private fun createValidFingerprint(
        customWalletId: String = walletId,
        customKeyAlias: String = keyAlias,
        tokenContract: EvmAddress? = null,
        tokenSymbol: String? = null,
        tokenDecimals: Int? = null,
        customFee: Wei = fee,
        customHumanAmount: String = humanAmount,
        customCalldata: Calldata = calldata,
        customContext: ChainExecutionContext = executionContext
    ): String {
        return ConfirmedEvmTransactionIntent.createFingerprint(
            walletId = customWalletId,
            keyAlias = customKeyAlias,
            sender = sender,
            chain = chain,
            executionContext = customContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = tokenContract,
            tokenSymbol = tokenSymbol,
            tokenDecimals = tokenDecimals,
            humanAmount = customHumanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = customCalldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = customFee
        )
    }

    @Test
    fun `valid native intent creation succeeds`() {
        val fingerprint = createValidFingerprint()
        val intent = ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )

        assertEquals(walletId, intent.walletId)
        assertEquals(keyAlias, intent.keyAlias)
        assertEquals(sender, intent.sender)
        assertEquals(recipient, intent.recipient)
        assertEquals(fingerprint, intent.canonicalFingerprint)
    }

    @Test
    fun `valid token intent creation succeeds`() {
        val tokenContract = EvmAddress.fromString("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val tokenSymbol = "USDC"
        val tokenDecimals = 6
        val fingerprint = createValidFingerprint(
            tokenContract = tokenContract,
            tokenSymbol = tokenSymbol,
            tokenDecimals = tokenDecimals
        )

        val intent = ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = tokenContract,
            tokenSymbol = tokenSymbol,
            tokenDecimals = tokenDecimals,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )

        assertEquals(tokenContract, intent.tokenContract)
        assertEquals("USDC", intent.tokenSymbol)
        assertEquals(6, intent.tokenDecimals)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched canonical fingerprint throws IllegalArgumentException`() {
        ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = "invalid_tampered_fingerprint"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank walletId throws IllegalArgumentException`() {
        val fingerprint = createValidFingerprint(customWalletId = "  ")
        ConfirmedEvmTransactionIntent(
            walletId = "  ",
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank keyAlias throws IllegalArgumentException`() {
        val fingerprint = createValidFingerprint(customKeyAlias = "  ")
        ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = "  ",
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tokenContract present without tokenDecimals throws IllegalArgumentException`() {
        val tokenContract = EvmAddress.fromString("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val fingerprint = createValidFingerprint(tokenContract = tokenContract, tokenSymbol = "USDC", tokenDecimals = null)
        ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = tokenContract,
            tokenSymbol = "USDC",
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid tokenDecimals out of range throws IllegalArgumentException`() {
        val tokenContract = EvmAddress.fromString("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val fingerprint = createValidFingerprint(tokenContract = tokenContract, tokenSymbol = "USDC", tokenDecimals = 100)
        ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = tokenContract,
            tokenSymbol = "USDC",
            tokenDecimals = 100,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )
    }

    @Test
    fun `fingerprint is sensitive to keyAlias change`() {
        val fpOriginal = createValidFingerprint()
        val fpTampered = createValidFingerprint(customKeyAlias = "different_key_alias")
        assertNotEquals(fpOriginal, fpTampered)
    }

    @Test
    fun `fingerprint is sensitive to recipient change`() {
        val fpOriginal = createValidFingerprint()
        val recipient2 = EvmAddress.fromString("0x4444444444444444444444444444444444444444")
        val fpTampered = ConfirmedEvmTransactionIntent.createFingerprint(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient2,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee
        )
        assertNotEquals(fpOriginal, fpTampered)
    }

    @Test
    fun `fingerprint is sensitive to gasPrice or fee change`() {
        val fpOriginal = createValidFingerprint()
        val tamperedGasPrice = Wei.fromWeiDecimal("25000000000")
        val fpTampered = ConfirmedEvmTransactionIntent.createFingerprint(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = tamperedGasPrice,
            gasLimit = gasLimit,
            fee = fee
        )
        assertNotEquals(fpOriginal, fpTampered)
    }

    @Test
    fun `signingDigestHex produces exact 64-character lowercase hex string`() {
        val fingerprint = createValidFingerprint()
        val intent = ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint
        )

        val digest = intent.signingDigest
        val digestHex = intent.signingDigestHex

        assertEquals(32, digest.size)
        assertEquals(64, digestHex.length)
        assertTrue(digestHex.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `signingDigest is sensitive to transaction parameters`() {
        val fingerprint1 = createValidFingerprint()
        val intent1 = ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint1
        )

        val nonce2 = Nonce.fromLong(6L)
        val fingerprint2 = ConfirmedEvmTransactionIntent.createFingerprint(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce2,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee
        )
        val intent2 = ConfirmedEvmTransactionIntent(
            walletId = walletId,
            keyAlias = keyAlias,
            sender = sender,
            chain = chain,
            executionContext = executionContext,
            envelopeType = envelopeType,
            recipient = recipient,
            tokenContract = null,
            tokenSymbol = null,
            tokenDecimals = null,
            humanAmount = humanAmount,
            baseUnitAmount = baseUnitAmount,
            nativeValue = nativeValue,
            calldata = calldata,
            nonce = nonce2,
            gasPrice = gasPrice,
            gasLimit = gasLimit,
            fee = fee,
            canonicalFingerprint = fingerprint2
        )

        assertNotEquals(intent1.signingDigestHex, intent2.signingDigestHex)
        assertFalse(intent1.signingDigest.contentEquals(intent2.signingDigest))
    }
}
