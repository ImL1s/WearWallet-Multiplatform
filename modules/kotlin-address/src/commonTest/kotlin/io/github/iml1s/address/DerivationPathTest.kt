package io.github.iml1s.address

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DerivationPathTest {

    @Test
    fun testParseBIP44Path() {
        val path = DerivationPath.parse("m/44'/0'/0'/0/0")
        assertNotNull(path)
        assertEquals(44, path.purpose)
        assertEquals(0, path.coinType)
        assertEquals(0, path.account)
        assertEquals(0, path.change)
        assertEquals(0, path.addressIndex)
    }

    @Test
    fun testParseBIP84Path() {
        val path = DerivationPath.parse("m/84'/0'/0'/0/5")
        assertNotNull(path)
        assertEquals(84, path.purpose)
        assertEquals(5, path.addressIndex)
    }

    @Test
    fun testToPathString() {
        val path = DerivationPath.bip84Bitcoin(account = 1, change = 0, index = 10)
        assertEquals("m/84'/0'/1'/0/10", path.toPathString())
    }

    @Test
    fun testGetAddressType() {
        assertEquals(AddressType.P2PKH, DerivationPath.bip44Bitcoin().getAddressType())
        assertEquals(AddressType.P2WPKH, DerivationPath.bip84Bitcoin().getAddressType())
        assertEquals(AddressType.P2TR, DerivationPath.bip86Bitcoin().getAddressType())
    }

    @Test
    fun testNextAddress() {
        val path = DerivationPath.bip84Bitcoin(index = 0)
        val next = path.nextAddress()
        assertEquals(1, next.addressIndex)
    }

    @Test
    fun testChangeAddress() {
        val path = DerivationPath.bip84Bitcoin(change = 0, index = 5)
        val changePath = path.changeAddress(index = 3)
        assertEquals(1, changePath.change)
        assertEquals(3, changePath.addressIndex)
    }

    @Test
    fun testEthereumPath() {
        val path = DerivationPath.bip44Ethereum()
        assertEquals(44, path.purpose)
        assertEquals(60, path.coinType)
        assertEquals("m/44'/60'/0'/0/0", path.toPathString())
    }

    @Test
    fun testInvalidPathReturnsNull() {
        assertNull(DerivationPath.parse("invalid"))
        assertNull(DerivationPath.parse("44'/0'/0'/0/0"))  // 缺少 m/
    }
}
