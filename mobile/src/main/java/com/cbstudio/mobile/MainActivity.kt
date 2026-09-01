package com.cbstudio.mobile

import android.content.Intent
import android.os.Bundle
import timber.log.Timber
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.cbstudio.mobile.navigation.MobileNavigation
import com.cbstudio.mobile.ui.theme.WearWalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WearWalletTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MobileNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 啟動 WearListenerService 以確保能接收來自手錶的消息
        val serviceIntent = Intent(this, WearListenerService::class.java)
        startService(serviceIntent)
        Timber.tag("MainActivity").d("Started WearListenerService")
    }
}