package com.cbstudio.mobile.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cbstudio.mobile.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mobile Companion App E2E Tests
 * 
 * Tests the core companion features:
 * - QR Receive Screen Navigation
 * - Address Book Navigation & CRUD
 * - Notification Settings Navigation
 */
@RunWith(AndroidJUnit4::class)
class MobileE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ==================== Navigation Tests ====================

    @Test
    fun testNavigationToReceiveScreen() {
        // Find and click the Receive button using testTag
        composeTestRule.onNodeWithTag("qr_receive_btn").performClick()
        
        // Check if Receive Screen is displayed
        composeTestRule.onNodeWithTag("qr_receive_title").assertIsDisplayed()
        
        // Go back - "Back" or arrow icon usually has "Back" or "Navigate up" description, but here code uses "返回" hardcoded in MobileReceiveScreen line 321!
        // Wait, MobileReceiveScreen.kt line 321: contentDescription = "返回"
        // This is hardcoded Chinese! That is a bug in the app code too if we want i18n.
        // But for the test validation, I must match the app code.
        // However, standard back buttons usually use localizable descriptions.
        // Let's check MobileReceiveScreen.kt again. ContentDescription IS hardcoded "返回".
        // So `onNodeWithContentDescription("返回", substring = true)` is actually correct for the current code.
        // BUT, if the device language is English, maybe accessibility service does something? No, it uses the attribute.
        // So the test failure for "Add Contact" (English in test) vs potentially Chinese on device is the issue.
        // "Add Contact" in AddressBookScreen line 85 uses `stringResource(R.string.add_contact)`.
        
        composeTestRule.onNodeWithContentDescription("返回", substring = true).performClick()
        
        // Verify we are back home
        composeTestRule.onNodeWithTag("address_book_btn").assertIsDisplayed()
    }

    @Test
    fun testNavigationToAddressBook() {
        // Find and click Address Book button using testTag
        composeTestRule.onNodeWithTag("address_book_btn").performClick()

        // Check if Address Book Screen is displayed
        composeTestRule.onNodeWithTag("address_book_title").assertIsDisplayed()
    }
    
    // ==================== Address Book Feature Tests ====================
    
    @Test
    fun testAddressBookAddContactNavigation() {
        // Navigate to Address Book
        composeTestRule.onNodeWithTag("address_book_btn").performClick()
        composeTestRule.onNodeWithTag("address_book_title").assertIsDisplayed()
        
        // Find and click the Add Contact FAB
        val addContactStr = composeTestRule.activity.getString(com.cbstudio.mobile.R.string.add_contact)
        composeTestRule.onNodeWithContentDescription(addContactStr, substring = true)
            .performClick()
        
        // Verify we are on Add Contact screen
        composeTestRule.waitForIdle()
        
        // The Add Contact screen should have input fields for name, address, etc.
        val contactNameStr = composeTestRule.activity.getString(com.cbstudio.mobile.R.string.contact_name)
        composeTestRule.onNodeWithText(contactNameStr, substring = true).assertExists()
    }
    
    @Test
    fun testAddressBookAddContactFlow() {
        // Navigate to Address Book
        composeTestRule.onNodeWithTag("address_book_btn").performClick()
        composeTestRule.onNodeWithTag("address_book_title").assertIsDisplayed()
        
        // Click Add Contact FAB
        val addContactStr = composeTestRule.activity.getString(com.cbstudio.mobile.R.string.add_contact)
        composeTestRule.onNodeWithContentDescription(addContactStr, substring = true)
            .performClick()
        composeTestRule.waitForIdle()
        
        // Fill in contact information
        val testName = "Test Contact"
        val testAddress = "0x1234567890123456789012345678901234567890"
        
        // Enter Name
        val contactNameStr = composeTestRule.activity.getString(com.cbstudio.mobile.R.string.contact_name)
        composeTestRule.onNodeWithText(contactNameStr, substring = true)
            .performTextInput(testName)
        
        // Enter Address
        val walletAddressStr = composeTestRule.activity.getString(com.cbstudio.mobile.R.string.wallet_address)
        composeTestRule.onNodeWithText(walletAddressStr, substring = true)
            .performTextInput(testAddress)
        
        // Verify form is filled
        composeTestRule.onNodeWithText(testName, substring = true).assertExists()
        
        // Note: We don't submit to avoid polluting database in tests
    }
    
    // ==================== QR Receive Feature Tests ====================
    
    @Test
    fun testQRReceiveScreenContent() {
        // Navigate to QR Receive
        composeTestRule.onNodeWithTag("qr_receive_btn").performClick()
        
        // Verify QR Receive screen content
        composeTestRule.onNodeWithTag("qr_receive_title").assertIsDisplayed()
        
        // Check for wallet address display (using both languages)
        val walletAddressStr = composeTestRule.activity.getString(com.cbstudio.mobile.R.string.wallet_address)
        composeTestRule.onNodeWithText(walletAddressStr, substring = true, ignoreCase = true)
            .assertExists()
    }
    
    // ==================== Connection Status Tests ====================
    
    @Test
    fun testConnectionStatusDisplayed() {
        // On home screen, connection status should be visible
        val connectionStatusStr = composeTestRule.activity.getString(com.cbstudio.mobile.R.string.connection_status)
        composeTestRule.onNodeWithText(connectionStatusStr, substring = true, ignoreCase = true)
            .assertExists()
    }
}
