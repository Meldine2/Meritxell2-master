package com.example.meritxell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "chat_notifications"
        private const val NOTIFICATION_ID = 1
        
        // Notification Channels
        private const val CHAT_CHANNEL_ID = "chat_messages"
        private const val SYSTEM_CHANNEL_ID = "system_messages"
        private const val ADOPTION_CHANNEL_ID = "adoption_updates"
        private const val DONATION_CHANNEL_ID = "donation_updates"
        private const val ALERT_CHANNEL_ID = "alert_messages"
        
        // Wake lock timeout (30 seconds)
        private const val WAKE_LOCK_TIMEOUT = 30000L
    }

    private lateinit var enhancedNotificationManager: EnhancedNotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FCM Service created")
        createAllNotificationChannels()
        enhancedNotificationManager = EnhancedNotificationManager(this)
        
        // Initialize token registration
        registerForNotifications()
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed FCM token: $token")
        
        // Store token immediately
        storeTokenInPreferences(token)
        
        // Send token to server with retry mechanism
        sendTokenToServer(token, maxRetries = 3)
        
        // Update user's notification settings
        updateUserNotificationSettings(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "=== FCM MESSAGE RECEIVED ===")
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message ID: ${remoteMessage.messageId}")
        Log.d(TAG, "Data: ${remoteMessage.data}")
        Log.d(TAG, "Notification: ${remoteMessage.notification}")
        
        // Acquire wake lock to ensure processing even if device is sleeping
        acquireWakeLock()
        
        try {
            // Process the message
            processRemoteMessage(remoteMessage)
        } finally {
            // Always release wake lock
            releaseWakeLock()
        }
    }

    private fun processRemoteMessage(remoteMessage: RemoteMessage) {
        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Processing data message")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Processing notification message")
            val title = notification.title ?: "New Message"
            val body = notification.body ?: "You have a new message"
            handleNotificationMessage(title, body, remoteMessage.data)
        }
        
        // Log notification received for analytics
        logNotificationReceived(remoteMessage)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val messageType = data["messageType"] ?: "general"
        val chatId = data["chatId"] ?: ""
        val senderName = data["senderName"] ?: "Unknown"
        val senderId = data["senderId"] ?: ""
        val messageBody = data["body"] ?: "You have a new message"
        val donationType = data["donationType"] ?: ""
        val category = data["category"] ?: "general"
        val title = data["title"] ?: "New Notification"
        
        Log.d(TAG, "Handling data message: type=$messageType, category=$category, sender=$senderName")
        
        when (messageType) {
            "chat", "message" -> {
                if (chatId.isNotEmpty() && senderId.isNotEmpty()) {
                    // Check if user is currently in this chat to avoid duplicate notifications
                    if (!isUserCurrentlyInChat(chatId)) {
                    enhancedNotificationManager.showChatNotification(
                        senderName = senderName,
                        messageText = messageBody,
                        chatUserId = senderId,
                        chatUserName = senderName,
                        chatId = chatId
                    )
                } else {
                        Log.d(TAG, "User is currently in chat $chatId, skipping notification")
                    }
                } else {
                    showBasicNotification(title, messageBody, CHAT_CHANNEL_ID)
                }
            }
            "adoption" -> {
                enhancedNotificationManager.showAdoptionNotification(
                    title,
                    messageBody,
                    senderId
                )
            }
            "donation" -> {
                enhancedNotificationManager.showDonationNotification(
                    title,
                    messageBody,
                    donationType
                )
            }
            "system" -> {
                enhancedNotificationManager.showSystemNotification(
                    title,
                    messageBody
                )
            }
            "alert" -> {
                showAlertNotification(title, messageBody)
            }
            else -> {
                // Handle unknown message types with basic notification
                showBasicNotification(title, messageBody, SYSTEM_CHANNEL_ID)
            }
        }
    }

    private fun handleNotificationMessage(title: String, body: String, data: Map<String, String>) {
        val messageType = data["messageType"] ?: ""
        val category = data["category"] ?: ""
        
        // Try to determine message type from title/body content or use data
        when {
            messageType == "chat" || messageType == "message" -> {
                val chatId = data["chatId"] ?: ""
                val senderId = data["senderId"] ?: ""
                val senderName = data["senderName"] ?: "Unknown"
                
                if (chatId.isNotEmpty() && senderId.isNotEmpty() && !isUserCurrentlyInChat(chatId)) {
                    enhancedNotificationManager.showChatNotification(
                        senderName = senderName,
                        messageText = body,
                        chatUserId = senderId,
                        chatUserName = senderName,
                        chatId = chatId
                    )
                } else {
                    showBasicNotification(title, body, CHAT_CHANNEL_ID)
            }
            }
            messageType == "adoption" || category == "adoption" -> {
                enhancedNotificationManager.showAdoptionNotification(title, body)
            }
            messageType == "donation" || category == "donation" -> {
                enhancedNotificationManager.showDonationNotification(title, body, data["donationType"] ?: "")
            }
            messageType == "alert" || category == "alert" -> {
                showAlertNotification(title, body)
            }
            else -> {
                enhancedNotificationManager.showSystemNotification(title, body)
            }
        }
    }

    private fun showBasicNotification(title: String, body: String, channelId: String = SYSTEM_CHANNEL_ID) {
        val intent = Intent(this, InboxActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 250, 250))

        with(NotificationManagerCompat.from(this)) {
            try {
                notify(generateNotificationId(), notificationBuilder.build())
                Log.d(TAG, "Basic notification shown: $title")
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to show notification: ${e.message}")
            }
        }
    }

    private fun showAlertNotification(title: String, body: String) {
        val intent = Intent(this, InboxActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))

        with(NotificationManagerCompat.from(this)) {
            try {
                notify(generateNotificationId(), notificationBuilder.build())
                Log.d(TAG, "Alert notification shown: $title")
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to show alert notification: ${e.message}")
            }
        }
    }

    private fun createAllNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channels = listOf(
                // Chat Messages Channel (High Priority)
                NotificationChannel(
                    CHAT_CHANNEL_ID,
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for chat messages"
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                },
                
                // System Messages Channel (Default Priority)
                NotificationChannel(
                    SYSTEM_CHANNEL_ID,
                    "System Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "System notifications and alerts"
                    enableVibration(true)
                    setShowBadge(true)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                },
                
                // Adoption Updates Channel (High Priority)
                NotificationChannel(
                    ADOPTION_CHANNEL_ID,
                    "Adoption Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Updates about adoption process"
                    enableVibration(true)
                enableLights(true)
                    setShowBadge(true)
                    vibrationPattern = longArrayOf(0, 300, 100, 300)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                },
                
                // Donation Updates Channel (Default Priority)
                NotificationChannel(
                    DONATION_CHANNEL_ID,
                    "Donation Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Updates about donations"
                    enableVibration(true)
                    setShowBadge(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                },
                
                // Alert Messages Channel (High Priority)
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Alert Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Critical alerts and important notifications"
                enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                    setBypassDnd(true)
            }
            )

            notificationManager.createNotificationChannels(channels)
            Log.d(TAG, "All notification channels created")
        }
    }

    private fun sendTokenToServer(token: String, maxRetries: Int = 3) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(user.uid)
            
            val tokenData = hashMapOf(
                "fcmToken" to token,
                "tokenUpdatedAt" to System.currentTimeMillis(),
                "deviceInfo" to getDeviceInfo(),
                "appVersion" to getAppVersion(),
                "lastActive" to System.currentTimeMillis()
            )
            
            userRef.set(tokenData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token updated successfully in Firestore")
                    
                    // Also update in a separate tokens collection for better management
                    db.collection("fcm_tokens").document(user.uid)
                        .set(mapOf(
                            "token" to token,
                            "userId" to user.uid,
                            "updatedAt" to System.currentTimeMillis(),
                            "deviceInfo" to getDeviceInfo(),
                            "isActive" to true
                        ))
                        .addOnSuccessListener {
                            Log.d(TAG, "FCM token stored in tokens collection")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Failed to store token in tokens collection: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error updating FCM token: ${e.message}")
                    
                    // Retry mechanism
                    if (maxRetries > 0) {
                        Log.d(TAG, "Retrying token update. Attempts left: ${maxRetries - 1}")
                        android.os.Handler(mainLooper).postDelayed({
                            sendTokenToServer(token, maxRetries - 1)
                        }, 5000) // Retry after 5 seconds
                    }
                }
        } else {
            Log.w(TAG, "User not authenticated, storing token locally for later upload")
            storeTokenInPreferences(token)
        }
    }

    private fun updateUserNotificationSettings(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        
        val notificationSettings = hashMapOf(
            "notificationsEnabled" to true,
            "chatNotifications" to true,
            "adoptionNotifications" to true,
            "donationNotifications" to true,
            "systemNotifications" to true,
            "alertNotifications" to true,
            "lastTokenUpdate" to System.currentTimeMillis()
        )
        
        db.collection("users").document(user.uid)
            .collection("settings").document("notifications")
            .set(notificationSettings, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "User notification settings updated")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to update notification settings: ${e.message}")
            }
    }

    private fun registerForNotifications() {
        // Register for token updates when service starts
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // Get current token and register
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "Current FCM token retrieved: $token")
                    sendTokenToServer(token)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to get FCM token: ${e.message}")
                }
        }
    }

    private fun storeTokenInPreferences(token: String) {
        val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        Log.d(TAG, "FCM token stored in preferences")
    }

    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "osVersion" to Build.VERSION.RELEASE,
            "sdkInt" to Build.VERSION.SDK_INT.toString()
        )
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun isUserCurrentlyInChat(chatId: String): Boolean {
        // Check if the user is currently viewing this specific chat
        val prefs = getSharedPreferences("chat_state", Context.MODE_PRIVATE)
        val currentChatId = prefs.getString("current_chat_id", "")
        val isInChat = prefs.getBoolean("is_in_chat", false)
        return isInChat && currentChatId == chatId
    }

    private fun generateNotificationId(): Int {
        return System.currentTimeMillis().toInt()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Meritxell::FCMWakeLock"
            )
        }
        
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT)
        Log.d(TAG, "Wake lock acquired for FCM processing")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
                Log.d(TAG, "Wake lock released")
            }
        }
    }

    private fun logNotificationReceived(remoteMessage: RemoteMessage) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        
        val logData = hashMapOf(
            "userId" to user.uid,
            "messageId" to remoteMessage.messageId,
            "messageType" to (remoteMessage.data["messageType"] ?: "unknown"),
            "category" to (remoteMessage.data["category"] ?: "general"),
            "hasNotification" to (remoteMessage.notification != null),
            "hasData" to remoteMessage.data.isNotEmpty(),
            "receivedAt" to System.currentTimeMillis(),
            "from" to remoteMessage.from
        )
        
        db.collection("notification_logs")
            .add(logData)
            .addOnSuccessListener {
                Log.d(TAG, "Notification received logged successfully")
                }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to log notification received: ${e.message}")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        Log.d(TAG, "FCM Service destroyed")
    }
}