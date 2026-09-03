package com.cbstudio.wearwallet.presentation.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.presentation.MainActivity
import timber.log.Timber

/**
 * 通知顯示服務
 * 處理 Wear OS 上的通知顯示和管理
 */
class NotificationDisplayService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "wearwallet_notifications"
        private const val CHANNEL_NAME = "WearWallet 通知"
        private const val ONGOING_NOTIFICATION_ID = 1001
        private const val TRANSACTION_NOTIFICATION_ID = 2001
        
        private const val ACTION_SHOW_NOTIFICATION = "com.cbstudio.wearwallet.SHOW_NOTIFICATION"
        private const val ACTION_HIDE_NOTIFICATION = "com.cbstudio.wearwallet.HIDE_NOTIFICATION"
        
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TYPE = "type"
        
        fun showNotification(context: Context, title: String, message: String, type: NotificationType = NotificationType.INFO) {
            val intent = Intent(context, NotificationDisplayService::class.java).apply {
                action = ACTION_SHOW_NOTIFICATION
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_TYPE, type.name)
            }
            context.startService(intent)
        }
        
        fun hideNotification(context: Context, notificationId: Int) {
            val intent = Intent(context, NotificationDisplayService::class.java).apply {
                action = ACTION_HIDE_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            context.startService(intent)
        }
    }
    
    enum class NotificationType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        TRANSACTION
    }
    
    private lateinit var notificationManager: NotificationManager
    
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_SHOW_NOTIFICATION -> {
                    val title = it.getStringExtra(EXTRA_TITLE) ?: "WearWallet"
                    val message = it.getStringExtra(EXTRA_MESSAGE) ?: ""
                    val typeStr = it.getStringExtra(EXTRA_TYPE) ?: NotificationType.INFO.name
                    val type = try {
                        NotificationType.valueOf(typeStr)
                    } catch (e: Exception) {
                        NotificationType.INFO
                    }
                    
                    showNotificationInternal(title, message, type)
                }
                ACTION_HIDE_NOTIFICATION -> {
                    val notificationId = it.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                    if (notificationId != -1) {
                        hideNotificationInternal(notificationId)
                    }
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "WearWallet 交易和狀態通知"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotificationInternal(title: String, message: String, type: NotificationType) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notificationId = when (type) {
                NotificationType.TRANSACTION -> TRANSACTION_NOTIFICATION_ID
                else -> ONGOING_NOTIFICATION_ID
            }
            
            // 選擇圖標
            val icon = when (type) {
                NotificationType.SUCCESS -> R.drawable.ic_complication_wallet_balance
                NotificationType.WARNING -> R.drawable.ic_complication_gas_fee
                NotificationType.ERROR -> R.drawable.ic_complication_gas_fee
                NotificationType.TRANSACTION -> R.drawable.ic_complication_token_price
                else -> R.drawable.ic_portfolio
            }
            
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            
            // 對於交易通知，添加振動
            if (type == NotificationType.TRANSACTION) {
                notificationBuilder.setVibrate(longArrayOf(0, 250, 250, 250))
                notificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH)
            }
            
            // Wear OS 特定設置
            notificationBuilder.setOngoing(type == NotificationType.INFO)
            notificationBuilder.setLocalOnly(true)
            
            val notification = notificationBuilder.build()
            
            notificationManager.notify(notificationId, notification)
            
            Timber.d("顯示通知: $title - $message (類型: $type)")
            
        } catch (e: Exception) {
            Timber.e(e, "顯示通知失敗")
        }
    }
    
    private fun hideNotificationInternal(notificationId: Int) {
        try {
            notificationManager.cancel(notificationId)
            Timber.d("隱藏通知: $notificationId")
        } catch (e: Exception) {
            Timber.e(e, "隱藏通知失敗")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理所有通知
        notificationManager.cancelAll()
    }
}