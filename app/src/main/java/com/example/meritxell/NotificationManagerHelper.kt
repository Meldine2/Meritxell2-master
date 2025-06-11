package com.example.meritxell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging

class NotificationManagerHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationHelper"
        private const val DATABASE_URL = "https://ally-user-default-rtdb.asia-southeast1.firebasedatabase.app"
        
        @Volatile
        private var INSTANCE: NotificationManagerHelper? = null
        
        fun getInstance(context: Context): NotificationManagerHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationManagerHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance(DATABASE_URL).reference
    private val enhancedNotificationManager = EnhancedNotificationManager(context)
    
    /**
     * Initialize FCM and register for notifications
     */
    fun initializeNotifications() {
        Log.d(TAG, "Initializing notification system")
        
        // Check if user is authenticated
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w(TAG, "User not authenticated - will register FCM token after login")
            return
        }
        
        // Get FCM token and register
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "FCM token retrieved: $token")
                registerFCMToken(token)
                setupNotificationListeners()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get FCM token: ${e.message}")
            }
        
        // Check battery optimization
        checkBatteryOptimization()
    }
    
    /**
     * Register FCM token with comprehensive error handling and retries
     */
    private fun registerFCMToken(token: String, retryCount: Int = 0) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val maxRetries = 3
        
        Log.d(TAG, "Registering FCM token (attempt ${retryCount + 1})")
        
        val tokenData = hashMapOf(
            "fcmToken" to token,
            "tokenUpdatedAt" to System.currentTimeMillis(),
            "deviceInfo" to mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "osVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT.toString(),
                "brand" to Build.BRAND
            ),
            "notificationSettings" to mapOf(
                "enabled" to true,
                "chatNotifications" to true,
                "adoptionNotifications" to true,
                "donationNotifications" to true,
                "systemNotifications" to true,
                "alertNotifications" to true
            ),
            "lastActive" to System.currentTimeMillis()
        )
        
        // Update user document
        firestore.collection("users").document(user.uid)
            .set(tokenData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "FCM token registered successfully in users collection")
                
                // Also store in dedicated tokens collection
                firestore.collection("fcm_tokens").document(user.uid)
                    .set(mapOf(
                        "token" to token,
                        "userId" to user.uid,
                        "updatedAt" to System.currentTimeMillis(),
                        "isActive" to true,
                        "deviceInfo" to tokenData["deviceInfo"]
                    ))
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token stored in tokens collection")
                        // Send test notification to confirm setup
                        sendTestNotification()
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to store in tokens collection: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register FCM token: ${e.message}")
                
                // Retry mechanism
                if (retryCount < maxRetries) {
                    val delay = (retryCount + 1) * 5000L // Exponential backoff
                    android.os.Handler(context.mainLooper).postDelayed({
                        registerFCMToken(token, retryCount + 1)
                    }, delay)
                } else {
                    Log.e(TAG, "Failed to register FCM token after $maxRetries attempts")
                }
            }
    }
    
    /**
     * Setup real-time listeners for automatic notifications
     */
    private fun setupNotificationListeners() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        Log.d(TAG, "Setting up notification listeners for user: ${user.uid}")
        
        // Check if user is admin to set up appropriate listeners
        checkIfUserIsAdmin(user.uid) { isAdmin ->
            if (isAdmin) {
                // For admins: listen to ALL admin chats to catch system messages
                setupAdminNotificationListeners(user.uid)
            } else {
                // For regular users: only listen to their own chats
                setupUserNotificationListeners(user.uid)
            }
        }
    }
    
    /**
     * Setup notification listeners for regular users
     */
    private fun setupUserNotificationListeners(userId: String) {
        Log.d(TAG, "Setting up user notification listeners for: $userId")
        
        // Listen for chats where user is participant_user
        realtimeDb.child("chats")
            .orderByChild("participant_user")
            .equalTo(userId)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val chatId = snapshot.key ?: return
                    Log.d(TAG, "Setting up message listener for user chat: $chatId")
                    setupChatMessageListener(chatId)
                }
                
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "User chat listener cancelled: ${error.message}")
                }
            })
    }
    
    /**
     * Setup notification listeners for admin users
     */
    private fun setupAdminNotificationListeners(userId: String) {
        Log.d(TAG, "Setting up admin notification listeners for: $userId")
        
        // For admins: listen to ALL chats to catch system messages
        realtimeDb.child("chats")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val chatId = snapshot.key ?: return
                    val participantAdmin = snapshot.child("participant_admin").getValue(String::class.java)
                    val participantUser = snapshot.child("participant_user").getValue(String::class.java)
                    
                    // Only listen to admin chats (chats that have an admin participant)
                    if (participantAdmin != null && participantUser != null) {
                        Log.d(TAG, "Setting up message listener for admin chat: $chatId")
                        setupChatMessageListener(chatId)
                    }
                }
                
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Admin chat listener cancelled: ${error.message}")
                }
            })
    }
    
    /**
     * Setup message listener for a specific chat
     */
    private fun setupChatMessageListener(chatId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        realtimeDb.child("chats").child(chatId).child("messages")
            .orderByChild("timestamp")
            .limitToLast(1) // Only listen for the latest message
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val messageId = snapshot.key ?: return
                    val message = snapshot.getValue(Message::class.java) ?: return
                    
                    // Enhanced logic for admin notifications
                    val shouldNotify = if (message.isSystemMessage && message.senderId == "system") {
                        // For system messages: notify ALL admins, not just the specific receiverId
                        checkIfUserIsAdmin(user.uid) { isAdmin ->
                            if (isAdmin && message.senderId != user.uid) {
                                Log.d(TAG, "System message for admin: ${user.uid} in chat $chatId")
                                handleNewMessage(message, chatId, messageId)
                            }
                        }
                        false // Return false to prevent immediate processing
                    } else {
                        // For regular messages: only notify the specific receiver
                        message.receiverId == user.uid && message.senderId != user.uid
                    }
                    
                    if (shouldNotify) {
                        Log.d(TAG, "New message received in chat $chatId from ${message.senderId}")
                        handleNewMessage(message, chatId, messageId)
                    }
                }
                
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Message listener cancelled for chat $chatId: ${error.message}")
                }
            })
    }
    
    /**
     * Check if user is an admin
     */
    private fun checkIfUserIsAdmin(userId: String, callback: (Boolean) -> Unit) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: "user"
                callback(role == "admin")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check user role: ${e.message}")
                callback(false)
            }
    }
    
    /**
     * Handle new message and send notification if needed
     */
    private fun handleNewMessage(message: Message, chatId: String, messageId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        // Check if user is currently in this chat
        val prefs = context.getSharedPreferences("chat_state", Context.MODE_PRIVATE)
        val currentChatId = prefs.getString("current_chat_id", "")
        val isInChat = prefs.getBoolean("is_in_chat", false)
        
        if (isInChat && currentChatId == chatId) {
            Log.d(TAG, "User is currently in chat $chatId, skipping notification")
            return
        }
        
        // Get sender information
        firestore.collection("users").document(message.senderId).get()
            .addOnSuccessListener { senderDoc ->
                val senderName = senderDoc.getString("username") ?: "Unknown"
                
                // Determine notification type and send
                when {
                    message.isSystemMessage -> {
                        sendSystemMessageNotification(message, senderName)
                    }
                    else -> {
                        sendChatMessageNotification(message, senderName, chatId)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get sender info: ${e.message}")
                // Send notification anyway with unknown sender
                sendChatMessageNotification(message, "Unknown", chatId)
            }
    }
    
    /**
     * Send chat message notification via FCM
     */
    private fun sendChatMessageNotification(message: Message, senderName: String, chatId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        // Get user's FCM token
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val fcmToken = userDoc.getString("fcmToken")
                if (fcmToken.isNullOrEmpty()) {
                    Log.w(TAG, "No FCM token found for user")
                    return@addOnSuccessListener
                }
                
                // Prepare notification data
                val notificationData = hashMapOf(
                    "fcmToken" to fcmToken,
                    "messageType" to "chat",
                    "category" to "message",
                    "title" to "💬 $senderName",
                    "body" to message.message,
                    "senderName" to senderName,
                    "senderId" to message.senderId,
                    "receiverId" to message.receiverId,
                    "chatId" to chatId,
                    "timestamp" to System.currentTimeMillis()
                )
                
                // Send via Cloud Functions
                functions.getHttpsCallable("sendPushNotification")
                    .call(notificationData)
                    .addOnSuccessListener { result ->
                        Log.d(TAG, "Chat notification sent successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send chat notification: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user FCM token: ${e.message}")
            }
    }
    
    /**
     * Send system message notification
     */
    private fun sendSystemMessageNotification(message: Message, senderName: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val fcmToken = userDoc.getString("fcmToken")
                if (fcmToken.isNullOrEmpty()) {
                    Log.w(TAG, "No FCM token found for user")
                    return@addOnSuccessListener
                }
                
                val (title, category) = when {
                    message.message.contains("adoption", ignoreCase = true) -> "👶 Adoption Update" to "adoption"
                    message.message.contains("donation", ignoreCase = true) -> "📦 Donation Update" to "donation"
                    message.message.contains("appointment", ignoreCase = true) -> "📅 Appointment Update" to "appointment"
                    message.message.contains("alert", ignoreCase = true) -> "⚠️ Alert" to "alert"
                    else -> "🔔 System Message" to "system"
                }
                
                val notificationData = hashMapOf(
                    "fcmToken" to fcmToken,
                    "messageType" to "system",
                    "category" to category,
                    "title" to title,
                    "body" to message.message,
                    "senderName" to senderName,
                    "receiverId" to message.receiverId,
                    "donationType" to message.donationType,
                    "timestamp" to System.currentTimeMillis()
                )
                
                functions.getHttpsCallable("sendPushNotification")
                    .call(notificationData)
                    .addOnSuccessListener { result ->
                        Log.d(TAG, "System notification sent successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send system notification: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user FCM token: ${e.message}")
            }
    }
    
    /**
     * Send test notification to confirm setup
     */
    private fun sendTestNotification() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val fcmToken = userDoc.getString("fcmToken")
                val username = userDoc.getString("username") ?: "User"
                
                if (fcmToken.isNullOrEmpty()) return@addOnSuccessListener
                
                val testData = hashMapOf(
                    "fcmToken" to fcmToken,
                    "messageType" to "system",
                    "category" to "system",
                    "title" to "🎉 Notifications Ready!",
                    "body" to "Hi $username! Your notifications are now fully set up and working.",
                    "senderName" to "Meritxell System",
                    "receiverId" to user.uid,
                    "timestamp" to System.currentTimeMillis()
                )
                
                functions.getHttpsCallable("sendPushNotification")
                    .call(testData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Test notification sent successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send test notification: ${e.message}")
                    }
            }
    }
    
    /**
     * Check battery optimization and prompt user to disable if needed
     */
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = context.packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "App is subject to battery optimization")
                
                // Show dialog to user about battery optimization
                if (context is android.app.Activity) {
                    showBatteryOptimizationDialog(context)
                }
            } else {
                Log.d(TAG, "App is whitelisted from battery optimization")
            }
        }
    }
    
    /**
     * Show dialog asking user to disable battery optimization
     */
    private fun showBatteryOptimizationDialog(context: android.app.Activity) {
        AlertDialog.Builder(context)
            .setTitle("Enable Reliable Notifications")
            .setMessage("To ensure you receive notifications even when your phone is asleep, please disable battery optimization for this app.\n\nThis will not significantly impact your battery life.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open battery optimization settings: ${e.message}")
                    // Fallback to general battery settings
                    try {
                        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        context.startActivity(intent)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to open battery settings: ${e2.message}")
                    }
                }
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    

    
    /**
     * Update notification preferences
     */
    fun updateNotificationPreferences(
        chatNotifications: Boolean = true,
        adoptionNotifications: Boolean = true,
        donationNotifications: Boolean = true,
        systemNotifications: Boolean = true,
        alertNotifications: Boolean = true
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        val preferences = hashMapOf(
            "chatNotifications" to chatNotifications,
            "adoptionNotifications" to adoptionNotifications,
            "donationNotifications" to donationNotifications,
            "systemNotifications" to systemNotifications,
            "alertNotifications" to alertNotifications,
            "lastUpdated" to System.currentTimeMillis()
        )
        
        firestore.collection("users").document(user.uid)
            .collection("settings").document("notifications")
            .set(preferences, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Notification preferences updated")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update notification preferences: ${e.message}")
            }
    }
    
    /**
     * Get notification statistics
     */
    fun getNotificationStats(callback: (Map<String, Any>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        firestore.collection("notification_logs")
            .whereEqualTo("receiverId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                val stats = mutableMapOf<String, Any>()
                var totalSent = 0
                var totalSuccessful = 0
                val typeCount = mutableMapOf<String, Int>()
                
                for (doc in documents) {
                    totalSent++
                    if (doc.getBoolean("success") == true) {
                        totalSuccessful++
                    }
                    
                    val messageType = doc.getString("messageType") ?: "unknown"
                    typeCount[messageType] = typeCount.getOrDefault(messageType, 0) + 1
                }
                
                stats["totalSent"] = totalSent
                stats["totalSuccessful"] = totalSuccessful
                stats["successRate"] = if (totalSent > 0) (totalSuccessful.toFloat() / totalSent) * 100 else 0f
                stats["typeBreakdown"] = typeCount
                
                callback(stats)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get notification stats: ${e.message}")
                callback(emptyMap())
            }
    }
} 