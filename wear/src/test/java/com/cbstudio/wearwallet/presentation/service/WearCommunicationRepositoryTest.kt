package com.cbstudio.wearwallet.presentation.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * WearCommunicationRepository 單元測試
 * 
 * 驗證跨設備通信層的核心功能：
 * - QR 掃描結果處理
 * - Keystone 連接結果處理
 * - 簽名交易結果處理
 * - 地址簿同步功能
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearCommunicationRepositoryTest {

    private lateinit var repository: WearCommunicationRepository

    @Before
    fun setup() {
        repository = WearCommunicationRepository.getInstance()
    }

    @Test
    fun `repository singleton should return same instance`() {
        val instance1 = WearCommunicationRepository.getInstance()
        val instance2 = WearCommunicationRepository.getInstance()
        assertEquals(instance1, instance2)
    }

    @Test
    fun `qrScanResults flow should emit received QR data`() = runTest {
        val testAddress = "0x1234567890abcdef1234567890abcdef12345678"
        
        // 啟動收集器（先啟動，讓 SharedFlow 可以接收）
        val deferred = async { repository.qrScanResults.first() }
        
        // 給收集器時間啟動
        delay(100)
        
        // 發送 QR 掃描結果
        repository.onQRScanResult(testAddress)
        
        // 驗證
        val receivedAddress = deferred.await()
        assertEquals(testAddress, receivedAddress)
    }

    @Test
    fun `keystoneConnectResults flow should emit UR data`() = runTest {
        val testUrData = "ur:crypto-hdkey/oxadykaxhdclae..."
        
        val deferred = async { repository.keystoneConnectResults.first() }
        delay(100)
        
        repository.onKeystoneConnectResult(testUrData)
        
        val receivedUrData = deferred.await()
        assertEquals(testUrData, receivedUrData)
    }

    @Test
    fun `signedTxResults flow should emit signed transaction`() = runTest {
        val testTx = "0xf86c82...signed_tx_data"
        
        val deferred = async { repository.signedTxResults.first() }
        delay(100)
        
        repository.onSignedTxResult(testTx)
        
        val receivedTx = deferred.await()
        assertEquals(testTx, receivedTx)
    }

    @Test
    fun `addressBookSync flow should emit contacts JSON`() = runTest {
        val testContactsJson = """[{"id":"1","name":"Test","address":"0x123"}]"""
        
        val deferred = async { repository.addressBookSync.first() }
        delay(100)
        
        repository.onAddressBookSync(testContactsJson)
        
        val receivedJson = deferred.await()
        assertEquals(testContactsJson, receivedJson)
    }

    @Test
    fun `addressBookAdd flow should emit new contact JSON`() = runTest {
        val testContactJson = """{"id":"2","name":"New Contact","address":"0x456"}"""
        
        val deferred = async { repository.addressBookAdd.first() }
        delay(100)
        
        repository.onAddressBookAdd(testContactJson)
        
        val receivedJson = deferred.await()
        assertEquals(testContactJson, receivedJson)
    }

    @Test
    fun `addressBookUpdate flow should emit updated contact JSON`() = runTest {
        val testContactJson = """{"id":"1","name":"Updated Name","address":"0x123"}"""
        
        val deferred = async { repository.addressBookUpdate.first() }
        delay(100)
        
        repository.onAddressBookUpdate(testContactJson)
        
        val receivedJson = deferred.await()
        assertEquals(testContactJson, receivedJson)
    }

    @Test
    fun `addressBookDelete flow should emit contact ID`() = runTest {
        val testContactId = "contact_123"
        
        val deferred = async { repository.addressBookDelete.first() }
        delay(100)
        
        repository.onAddressBookDelete(testContactId)
        
        val receivedId = deferred.await()
        assertEquals(testContactId, receivedId)
    }

    @Test
    fun `all flows should be accessible`() {
        assertNotNull(repository.qrScanResults)
        assertNotNull(repository.signedTxResults)
        assertNotNull(repository.keystoneConnectResults)
        assertNotNull(repository.addressBookSync)
        assertNotNull(repository.addressBookAdd)
        assertNotNull(repository.addressBookUpdate)
        assertNotNull(repository.addressBookDelete)
    }
}
