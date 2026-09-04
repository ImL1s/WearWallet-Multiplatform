package com.cbstudio.mobile.ui.addressbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.cbstudio.mobile.R
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(
    onNavigateToAddContact: () -> Unit,
    onNavigateToContactDetail: (String) -> Unit,
    viewModel: AddressBookViewModel = koinViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter { contact ->
                contact.name.contains(searchQuery, ignoreCase = true) ||
                contact.address.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.address_book),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("address_book_title")
                    )
                },
                actions = {
                    IconButton(onClick = { 
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) {
                            searchQuery = "" // 清空搜索查詢當關閉搜索時
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_contacts)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddContact,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_contact)
                )
            }
        }
    ) { paddingValues ->
        if (filteredContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.no_contacts),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.no_contacts_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 搜索輸入框（當搜索可見時）
                if (isSearchVisible) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text(stringResource(R.string.search_contacts)) },
                            placeholder = { Text("搜尋聯絡人名稱或地址...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            singleLine = true
                        )
                    }
                }
                
                items(
                    items = filteredContacts,
                    key = { it.id }
                ) { contact ->
                    ContactListItem(
                        contact = contact,
                        onClick = { onNavigateToContactDetail(contact.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactListItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 頭像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when (contact.chainType) {
                            ChainType.ETHEREUM -> Color(0xFF627EEA)
                            ChainType.BSC -> Color(0xFFF3BA2F)
                            ChainType.POLYGON -> Color(0xFF8247E5)
                            ChainType.CRONOS -> Color(0xFF002D74)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 聯絡人資訊
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${contact.address.take(6)}...${contact.address.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 鏈標籤
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (contact.chainType) {
                            ChainType.ETHEREUM -> Color(0xFF627EEA)
                            ChainType.BSC -> Color(0xFFF3BA2F)
                            ChainType.POLYGON -> Color(0xFF8247E5)
                            ChainType.CRONOS -> Color(0xFF002D74)
                            else -> MaterialTheme.colorScheme.primary
                        }.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = when (contact.chainType) {
                                ChainType.ETHEREUM -> "ETH"
                                ChainType.BSC -> "BSC"
                                ChainType.POLYGON -> "MATIC"
                                ChainType.CRONOS -> "CRO"
                                else -> contact.chainType.name
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (contact.chainType) {
                                ChainType.ETHEREUM -> Color(0xFF627EEA)
                                ChainType.BSC -> Color(0xFFF3BA2F)
                                ChainType.POLYGON -> Color(0xFF8247E5)
                                ChainType.CRONOS -> Color(0xFF002D74)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    
                    // 備註標示（如果有）
                    if (!contact.note.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stringResource(R.string.note),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}