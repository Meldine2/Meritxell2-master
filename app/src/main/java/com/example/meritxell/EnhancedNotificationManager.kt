package com.example.meritxell

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class EnhancedNotificationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedNotification"
        private const val CHAT_CHANNEL_ID = "chat_messages"
        private const val SYSTEM_CHANNEL_ID = "system_messages"
        private const val ADOPTION_CHANNEL_ID = "adoption_updates"
        private const val DONATION_CHANNEL_ID = "donation_updates"
        
        private const val CHAT_CHANNEL_NAME = "Chat Messages"
        private const val SYSTEM_CHANNEL_NAME = "System Messages"
        private const val ADOPTION_CHANNEL_NAME = "Adoption Updates"
        private const val DONATION_CHANNEL_NAME = "Donation Updates"
        
        // Notification IDs
        private const val CHAT_NOTIFICATION_ID = 1001
        private const val SYSTEM_NOTIFICATION_ID = 1002
        private const val ADOPTION_NOTIFICATION_ID = 1003
        private const val DONATION_NOTIFICATION_ID = 1004
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Chat Messages Channel (High Priority)
            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID,
                CHAT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for chat messages"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            
            // System Messages Channel (Default Priority)
            val systemChannel = NotificationChannel(
                SYSTEM_CHANNEL_ID,
                SYSTEM_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "System notifications and alerts"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Adoption Updates Channel (High Priority)
            val adoptionChannel = NotificationChannel(
                ADOPTION_CHANNEL_ID,
                ADOPTION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Updates about adoption process"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            
            // Donation Updates Channel (Default Priority)
            val donationChannel = NotificationChannel(
                DONATION_CHANNEL_ID,
                DONATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates about donations"
                enableVibration(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannels(listOf(
                chatChannel, systemChannel, adoptionChannel, donationChannel
            ))
            
            Log.d(TAG, "Notification channels created")
        }
    }
    
    /**
     * Show notification for new chat message
     */
    fun showChatNotification(
        senderName: String,
        messageText: String,
        chatUserId: String,
        chatUserName: String,
        chatId: String
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission granted")
            return
        }
        
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("chatUserId", chatUserId)
            putExtra("chatUserName", chatUserName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💬 $senderName")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setColor(ContextCompat.getColor(context, R.color.blue_500))
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                CHAT_NOTIFICATION_ID + chatId.hashCode(),
                notification
            )
            Log.d(TAG, "Chat notification shown for $senderName")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to show notification: ${e.message}")
        }
    }
    
    /**
     * Show notification for adoption process updates
     */
    fun showAdoptionNotification(title: String, message: String, userId: String? = null) {
        if (!hasNotificationPermission()) return
        
        val intent = if (userId != null) {
            Intent(context, HistoryActivity::class.java).apply {
                putExtra("history_type", "adoption")
            }
        } else {
            Intent(context, UserHomeActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            ADOPTION_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, ADOPTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("👶 $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .setColor(ContextCompat.getColor(context, R.color.green))
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(ADOPTION_NOTIFICATION_ID, notification)
            Log.d(TAG, "Adoption notification shown: $title")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to show adoption notification: ${e.message}")
        }
    }
    
    /**
     * Show notification for donation updates
     */
    fun showDonationNotification(title: String, message: String, donationType: String = "") {
        if (!hasNotificationPermission()) return
        
        val intent = Intent(context, DonationHistoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            DONATION_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val icon = when (donationType.lowercase()) {
            "toys" -> "🧸"
            "clothes" -> "👕"
            "food" -> "🍎"
            "education" -> "📚"
            "money" -> "💰"
            else -> "📦"
        }
        
        val notification = NotificationCompat.Builder(context, DONATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$icon $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .setColor(ContextCompat.getColor(context, R.color.orange))
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(DONATION_NOTIFICATION_ID, notification)
            Log.d(TAG, "Donation notification shown: $title")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to show donation notification: ${e.message}")
        }
    }
    
    /**
     * Show system notification
     */
    fun showSystemNotification(title: String, message: String) {
        if (!hasNotificationPermission()) return
        
        val intent = Intent(context, InboxActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            SYSTEM_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, SYSTEM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔔 $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.blue_500))
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(SYSTEM_NOTIFICATION_ID, notification)
            Log.d(TAG, "System notification shown: $title")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to show system notification: ${e.message}")
        }
    }
    
    /**
     * Cancel notifications for specific chat
     */
    fun cancelChatNotifications(chatId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(CHAT_NOTIFICATION_ID + chatId.hashCode())
            Log.d(TAG, "Cancelled notifications for chat: $chatId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel chat notifications: ${e.message}")
        }
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        try {
            NotificationManagerCompat.from(context).cancelAll()
            Log.d(TAG, "All notifications cancelled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel all notifications: ${e.message}")
        }
    }
    
    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre Android 13, notification permission not required
        }
    }
    
    /**
     * Check if notifications are enabled for the app
     */
    fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
} 