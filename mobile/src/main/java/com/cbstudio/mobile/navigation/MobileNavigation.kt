package com.cbstudio.mobile.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cbstudio.mobile.ui.addressbook.*
import com.cbstudio.mobile.ui.home.HomeScreen
import com.cbstudio.mobile.ui.home.MobileReceiveScreen
import com.cbstudio.mobile.ui.nftwatchface.NftWatchFaceConfigurationScreen
import com.cbstudio.mobile.ui.notifications.NotificationPlaceholderScreen
import com.cbstudio.wearwallet.core.domain.model.ChainType

object MobileRoutes {
    const val HOME = "home"
    const val ADDRESS_BOOK = "address_book"
    const val ADD_CONTACT = "add_contact"
    const val CONTACT_DETAIL = "contact_detail/{contactId}"
    const val EDIT_CONTACT = "edit_contact/{contactId}"
    const val QR_RECEIVE = "qr_receive"
    const val SEND = "send/{address}/{chainType}"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val NFT_WATCH_FACE_CONFIG = "nft_watch_face_config"
    
    fun contactDetail(contactId: String) = "contact_detail/$contactId"
    fun editContact(contactId: String) = "edit_contact/$contactId"
    fun send(address: String, chainType: String) = "send/$address/$chainType"
}

@Composable
fun MobileNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = MobileRoutes.HOME
    ) {
        composable(MobileRoutes.HOME) {
            HomeScreen(
                onNavigateToNotificationSettings = {
                    navController.navigate(MobileRoutes.NOTIFICATION_SETTINGS)
                },
                onNavigateToAddressBook = {
                    navController.navigate(MobileRoutes.ADDRESS_BOOK)
                },
                onNavigateToNftWatchFaceConfig = {
                    navController.navigate(MobileRoutes.NFT_WATCH_FACE_CONFIG)
                },
                onNavigateToQrReceive = {
                    navController.navigate(MobileRoutes.QR_RECEIVE)
                }
            )
        }
        
        // 地址簿相關畫面
        composable(MobileRoutes.ADDRESS_BOOK) {
            AddressBookScreen(
                onNavigateToAddContact = {
                    navController.navigate(MobileRoutes.ADD_CONTACT)
                },
                onNavigateToContactDetail = { contactId ->
                    navController.navigate(MobileRoutes.contactDetail(contactId))
                }
            )
        }
        
        composable(MobileRoutes.ADD_CONTACT) {
            AddContactScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = MobileRoutes.CONTACT_DETAIL,
            arguments = listOf(
                navArgument("contactId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            ContactDetailScreen(
                contactId = contactId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEdit = { id ->
                    navController.navigate(MobileRoutes.editContact(id))
                }
            )
        }
        
        composable(
            route = MobileRoutes.EDIT_CONTACT,
            arguments = listOf(
                navArgument("contactId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            EditContactScreen(
                contactId = contactId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        
        composable(MobileRoutes.QR_RECEIVE) {
            MobileReceiveScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(MobileRoutes.NOTIFICATION_SETTINGS) {
            NotificationPlaceholderScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(MobileRoutes.NFT_WATCH_FACE_CONFIG) {
            NftWatchFaceConfigurationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    onNavigateBack: () -> Unit,
    title: String,
    description: String
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}