package com.cbstudio.wearwallet.core.multichain.monero

import android.util.Log
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import com.cbstudio.wearwallet.core.multichain.monero.crypto.AndroidMoneroCryptoProvider
import com.cbstudio.wearwallet.core.multichain.monero.crypto.MLSAGSignature
import com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroNetwork
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MoneroEmpiricalChallengeTest {

    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.i(anyString(), anyString()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.w(anyString(), anyString()) }.thenReturn(0)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun testRealWallet2WrapperNativeLoadingIsShortCircuited() {
        // Must return false without throwing UnsatisfiedLinkError or attempting System.loadLibrary
        val result = RealWallet2Wrapper.loadRealWallet2Library()
        assertFalse(result, "loadRealWallet2Library must return false in production release")

        // Check that all functions return safe stubs
        assertEquals(0L, RealWallet2Wrapper.createRealWalletFromMnemonic("test", 0))
        assertEquals("", RealWallet2Wrapper.getRealWalletAddress(100L))
        assertFalse(RealWallet2Wrapper.setRealDaemonAddress(100L, "http://localhost"))
        assertFalse(RealWallet2Wrapper.startRealRefresh(100L))
        assertFalse(RealWallet2Wrapper.refreshRealWallet(100L))
        assertFalse(RealWallet2Wrapper.setRealRefreshFromBlockHeight(100L, 0L))
        assertEquals(0L, RealWallet2Wrapper.getRealSyncHeight(100L))
        assertEquals(0L, RealWallet2Wrapper.getRealDaemonHeight(100L))
        assertFalse(RealWallet2Wrapper.isRealWalletSynced(100L))
        assertEquals(0L, RealWallet2Wrapper.getRealBalance(100L))
        assertEquals(0L, RealWallet2Wrapper.getRealUnlockedBalance(100L))
        assertTrue(RealWallet2Wrapper.getRealTransactionHistory(100L).isEmpty())
        assertEquals(0L, RealWallet2Wrapper.createRealTransaction(100L, "addr", 1000L))
        assertEquals(0L, RealWallet2Wrapper.getRealTransactionFee(100L))
        assertEquals("", RealWallet2Wrapper.getRealTransactionHash(100L))
        assertFalse(RealWallet2Wrapper.commitRealTransaction(100L, 200L))
        RealWallet2Wrapper.closeRealWallet(100L)

        val syncRes = RealWallet2Wrapper.createAndSyncEmotionWallet("test", "http://localhost")
        assertFalse(syncRes.success)
        assertEquals("RealWallet2 library loading is disabled in production release", syncRes.error)
    }

    @Test
    fun testAll18MoneroCryptoProviderOperationsFailClosedWithTypedException() {
        runBlocking {
            val provider = AndroidMoneroCryptoProvider
            val dummyBytes = ByteArray(32)

            // Operation 1: deriveKeysFromMnemonic
            val res1 = provider.deriveKeysFromMnemonic("test mnemonic", "pass")
            assertIs<Result.Failure>(res1)
            assertIs<TypedUnsupportedTransactionException>(res1.exception)
            assertEquals("Monero operation is unsupported in release", res1.exception.message)

            // Operation 2: generateAddress
            val res2 = provider.generateAddress(dummyBytes, dummyBytes, MoneroNetwork.MAINNET)
            assertIs<Result.Failure>(res2)
            assertIs<TypedUnsupportedTransactionException>(res2.exception)
            assertEquals("Monero operation is unsupported in release", res2.exception.message)

            // Operation 3: scanForUTXOs
            val res3 = provider.scanForUTXOs("viewKey", "address", 0L, 100L)
            assertIs<Result.Failure>(res3)
            assertIs<TypedUnsupportedTransactionException>(res3.exception)
            assertEquals("Monero operation is unsupported in release", res3.exception.message)

            // Operation 4: createTransaction
            val res4 = provider.createTransaction(emptyList(), emptyList(), "changeAddress", BigDecimal.ZERO)
            assertIs<Result.Failure>(res4)
            assertIs<TypedUnsupportedTransactionException>(res4.exception)
            assertEquals("Monero operation is unsupported in release", res4.exception.message)

            // Operation 5: signTransaction
            val res5 = provider.signTransaction("tx", emptyList())
            assertIs<Result.Failure>(res5)
            assertIs<TypedUnsupportedTransactionException>(res5.exception)
            assertEquals("Monero operation is unsupported in release", res5.exception.message)

            // Operation 6: broadcastTransaction
            val res6 = provider.broadcastTransaction("signedTx")
            assertIs<Result.Failure>(res6)
            assertIs<TypedUnsupportedTransactionException>(res6.exception)
            assertEquals("Monero operation is unsupported in release", res6.exception.message)

            // Operation 7: generateKeyImage
            val res7 = provider.generateKeyImage(dummyBytes, dummyBytes)
            assertIs<Result.Failure>(res7)
            assertIs<TypedUnsupportedTransactionException>(res7.exception)
            assertEquals("Monero operation is unsupported in release", res7.exception.message)

            // Operation 8: createMLSAGSignature
            val res8 = provider.createMLSAGSignature(dummyBytes, emptyList(), emptyList(), 0, null)
            assertIs<Result.Failure>(res8)
            assertIs<TypedUnsupportedTransactionException>(res8.exception)
            assertEquals("Monero operation is unsupported in release", res8.exception.message)

            // Operation 9: verifyMLSAGSignature
            val mlsag = MLSAGSignature(emptyList(), "", emptyList())
            val res9 = provider.verifyMLSAGSignature(dummyBytes, mlsag, emptyList())
            assertIs<Result.Failure>(res9)
            assertIs<TypedUnsupportedTransactionException>(res9.exception)
            assertEquals("Monero operation is unsupported in release", res9.exception.message)

            // Operation 10: createPedersenCommitment
            val res10 = provider.createPedersenCommitment(BigDecimal.ZERO, dummyBytes)
            assertIs<Result.Failure>(res10)
            assertIs<TypedUnsupportedTransactionException>(res10.exception)
            assertEquals("Monero operation is unsupported in release", res10.exception.message)

            // Operation 11: createBulletproof
            val res11 = provider.createBulletproof(emptyList(), emptyList())
            assertIs<Result.Failure>(res11)
            assertIs<TypedUnsupportedTransactionException>(res11.exception)
            assertEquals("Monero operation is unsupported in release", res11.exception.message)

            // Operation 12: ed25519ScalarMultBase
            val res12 = provider.ed25519ScalarMultBase(dummyBytes)
            assertIs<Result.Failure>(res12)
            assertIs<TypedUnsupportedTransactionException>(res12.exception)
            assertEquals("Monero operation is unsupported in release", res12.exception.message)

            // Operation 13: ed25519ScalarMult
            val res13 = provider.ed25519ScalarMult(dummyBytes, dummyBytes)
            assertIs<Result.Failure>(res13)
            assertIs<TypedUnsupportedTransactionException>(res13.exception)
            assertEquals("Monero operation is unsupported in release", res13.exception.message)

            // Operation 14: ed25519PointAdd
            val res14 = provider.ed25519PointAdd(dummyBytes, dummyBytes)
            assertIs<Result.Failure>(res14)
            assertIs<TypedUnsupportedTransactionException>(res14.exception)
            assertEquals("Monero operation is unsupported in release", res14.exception.message)

            // Operation 15: keccak256
            val res15 = provider.keccak256(dummyBytes)
            assertIs<Result.Failure>(res15)
            assertIs<TypedUnsupportedTransactionException>(res15.exception)
            assertEquals("Monero operation is unsupported in release", res15.exception.message)

            // Operation 16: sha256
            val res16 = provider.sha256(dummyBytes)
            assertIs<Result.Failure>(res16)
            assertIs<TypedUnsupportedTransactionException>(res16.exception)
            assertEquals("Monero operation is unsupported in release", res16.exception.message)

            // Operation 17: base58Encode
            val res17 = provider.base58Encode(dummyBytes)
            assertIs<Result.Failure>(res17)
            assertIs<TypedUnsupportedTransactionException>(res17.exception)
            assertEquals("Monero operation is unsupported in release", res17.exception.message)

            // Operation 18: base58Decode
            val res18 = provider.base58Decode("4123456789")
            assertIs<Result.Failure>(res18)
            assertIs<TypedUnsupportedTransactionException>(res18.exception)
            assertEquals("Monero operation is unsupported in release", res18.exception.message)
        }
    }

    @Test
    fun testMoneroWalletManagerAllOperationsFailClosed() {
        runBlocking {
            val manager = MoneroWalletManager()

            val r1 = manager.initializeWallet("w1", "mnemonic", "password")
            assertIs<Result.Failure>(r1)
            assertIs<TypedUnsupportedTransactionException>(r1.exception)
            assertEquals("Monero operation is unsupported in release", r1.exception.message)

            val r2 = manager.initializeViewOnlyWallet("w1", "address", "viewKey")
            assertIs<Result.Failure>(r2)
            assertIs<TypedUnsupportedTransactionException>(r2.exception)
            assertEquals("Monero operation is unsupported in release", r2.exception.message)

            val r3 = manager.syncAndGetBalance("w1")
            assertIs<Result.Failure>(r3)
            assertIs<TypedUnsupportedTransactionException>(r3.exception)
            assertEquals("Monero operation is unsupported in release", r3.exception.message)

            val r4 = manager.createAccount("w1", "label")
            assertIs<Result.Failure>(r4)
            assertIs<TypedUnsupportedTransactionException>(r4.exception)
            assertEquals("Monero operation is unsupported in release", r4.exception.message)

            val r5 = manager.createSubaddress("w1", 0, "label")
            assertIs<Result.Failure>(r5)
            assertIs<TypedUnsupportedTransactionException>(r5.exception)
            assertEquals("Monero operation is unsupported in release", r5.exception.message)

            val r6 = manager.createTransaction("w1", "dest", 1.0)
            assertIs<Result.Failure>(r6)
            assertIs<TypedUnsupportedTransactionException>(r6.exception)
            assertEquals("Monero operation is unsupported in release", r6.exception.message)

            val r7 = manager.getTransactionHistory("w1")
            assertIs<Result.Failure>(r7)
            assertIs<TypedUnsupportedTransactionException>(r7.exception)
            assertEquals("Monero operation is unsupported in release", r7.exception.message)
        }
    }
}
