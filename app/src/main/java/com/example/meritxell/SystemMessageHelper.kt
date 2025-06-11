package com.example.meritxell

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import com.google.firebase.functions.FirebaseFunctions

/**
 * SystemMessageHelper provides comprehensive automatic messaging for adoption center app
 * Handles all system messaging for adoption processes, donations, appointments, and admin-user communication
 */
class SystemMessageHelper {
    
    companion object {
        private const val TAG = "SystemMessageHelper"
        private const val DATABASE_URL = "https://ally-user-default-rtdb.asia-southeast1.firebasedatabase.app"
        
        // System message types
        const val ADOPTION_STARTED = "adoption_started"
        const val DONATION_SUBMITTED = "donation_submitted"
        const val DONATION_APPROVED = "donation_approved"
        const val DONATION_REJECTED = "donation_rejected"
        const val APPOINTMENT_SCHEDULED = "appointment_scheduled"
        const val APPOINTMENT_CANCELLED = "appointment_cancelled"
        const val MATCHING_COMPLETED = "matching_completed"
        const val STEP_COMPLETED = "step_completed"
        const val ADMIN_NOTIFICATION = "admin_notification"
        const val USER_NOTIFICATION = "user_notification"
        const val SYSTEM_ALERT = "system_alert"
        
        // Message emojis for better UX
        private const val ADOPTION_EMOJI = "👶"
        private const val DONATION_EMOJI = "📦"
        private const val APPROVED_EMOJI = "✅"
        private const val REJECTED_EMOJI = "❌"
        private const val CALENDAR_EMOJI = "📅"
        private const val HEART_EMOJI = "💕"
        private const val COMPLETED_EMOJI = "📋"
        private const val CELEBRATION_EMOJI = "🎉"
        private const val WARNING_EMOJI = "⚠️"
        
        /**
         * Initialize system messaging when user starts adoption process
         * Creates automatic admin-user chat connection
         */
        fun sendAdoptionStartedMessage(userId: String, username: String, adminId: String) {
            val message = "$ADOPTION_EMOJI $username has started the adoption process! Please guide them through the 10 steps."
            sendSystemMessageToAdmin(userId, username, adminId, message, ADOPTION_STARTED)
            
            val userMessage = "$CELEBRATION_EMOJI Welcome to the adoption process! Your admin will guide you through the next steps."
            sendSystemMessageToUser(userId, adminId, userMessage, ADOPTION_STARTED)
            
            Log.d(TAG, "Adoption started messages sent for user: $username")
        }
        
        /**
         * Send system message when user submits any type of donation
         * Automatically connects donor with admin for review process
         */
        fun sendDonationSubmittedMessage(userId: String, username: String, donationType: String, donationId: String) {
            if (userId.isEmpty() || donationType.isEmpty() || donationId.isEmpty()) {
                Log.e(TAG, "Invalid parameters for donation submission message")
                return
            }
            
            findAdminAndSendMessage(userId, username) { adminId ->
                val message = "$DONATION_EMOJI $username has submitted a $donationType donation (ID: $donationId). Please review and approve."
                sendSystemMessageToAdmin(userId, username, adminId, message, DONATION_SUBMITTED, donationId, donationType)
                
                val userMessage = "$DONATION_EMOJI Your $donationType donation has been submitted successfully! Admin will review it soon."
                sendSystemMessageToUser(userId, adminId, userMessage, DONATION_SUBMITTED, donationId, donationType)
                
                Log.d(TAG, "Donation submission messages sent for $donationType donation: $donationId")
            }
        }
        
        /**
         * Send system message when admin approves donation
         * Notifies user of approval and provides next steps
         */
        fun sendDonationApprovedMessage(userId: String, username: String, adminId: String, donationType: String, donationId: String) {
            if (userId.isEmpty() || adminId.isEmpty() || donationType.isEmpty() || donationId.isEmpty()) {
                Log.e(TAG, "Invalid parameters for donation approval message")
                return
            }
            
            val userMessage = "$APPROVED_EMOJI Your $donationType donation has been approved! You can now submit pictures and comments."
            sendSystemMessageToUser(userId, adminId, userMessage, DONATION_APPROVED, donationId, donationType)
            
            val adminMessage = "$APPROVED_EMOJI You approved $username's $donationType donation. It will move to history in 24 hours."
            sendSystemMessageToAdmin(userId, username, adminId, adminMessage, DONATION_APPROVED, donationId, donationType)
            
            Log.d(TAG, "Donation approval messages sent for $donationType donation: $donationId")
        }
        
        /**
         * Send system message when admin rejects donation
         */
        fun sendDonationRejectedMessage(userId: String, username: String, adminId: String, donationType: String, donationId: String, reason: String = "") {
            if (userId.isEmpty() || adminId.isEmpty() || donationType.isEmpty() || donationId.isEmpty()) {
                Log.e(TAG, "Invalid parameters for donation rejection message")
                return
            }
            
            val rejectionReason = if (reason.isNotEmpty()) " Reason: $reason" else ""
            val userMessage = "$REJECTED_EMOJI Your $donationType donation has been rejected.$rejectionReason Please contact admin for more information."
            sendSystemMessageToUser(userId, adminId, userMessage, DONATION_REJECTED, donationId, donationType)
            
            val adminMessage = "$REJECTED_EMOJI You rejected $username's $donationType donation.$rejectionReason"
            sendSystemMessageToAdmin(userId, username, adminId, adminMessage, DONATION_REJECTED, donationId, donationType)
            
            Log.d(TAG, "Donation rejection messages sent for $donationType donation: $donationId")
        }
        
        /**
         * Send system message when appointment is scheduled
         */
        fun sendAppointmentScheduledMessage(userId: String, username: String, appointmentDate: String, appointmentTime: String) {
            if (userId.isEmpty() || username.isEmpty() || appointmentDate.isEmpty() || appointmentTime.isEmpty()) {
                Log.e(TAG, "Invalid parameters for appointment scheduled message")
                return
            }
            
            findAdminAndSendMessage(userId, username) { adminId ->
                val message = "$CALENDAR_EMOJI $username has scheduled an appointment for $appointmentDate at $appointmentTime."
                sendSystemMessageToAdmin(userId, username, adminId, message, APPOINTMENT_SCHEDULED)
                
                val userMessage = "$CALENDAR_EMOJI Your appointment has been scheduled for $appointmentDate at $appointmentTime."
                sendSystemMessageToUser(userId, adminId, userMessage, APPOINTMENT_SCHEDULED)
                
                Log.d(TAG, "Appointment scheduled messages sent for user: $username")
            }
        }
        
        /**
         * Send system message when appointment is cancelled
         */
        fun sendAppointmentCancelledMessage(userId: String, username: String, adminId: String, appointmentDate: String) {
            if (userId.isEmpty() || adminId.isEmpty() || appointmentDate.isEmpty()) {
                Log.e(TAG, "Invalid parameters for appointment cancellation message")
                return
            }
            
            val userMessage = "$REJECTED_EMOJI Your appointment for $appointmentDate has been cancelled."
            sendSystemMessageToUser(userId, adminId, userMessage, APPOINTMENT_CANCELLED)
            
            val adminMessage = "$REJECTED_EMOJI Appointment with $username for $appointmentDate has been cancelled and moved to history."
            sendSystemMessageToAdmin(userId, username, adminId, adminMessage, APPOINTMENT_CANCELLED)
            
            Log.d(TAG, "Appointment cancellation messages sent for user: $username")
        }
        
        /**
         * Send system message when matching is completed
         */
        fun sendMatchingCompletedMessage(userId: String, username: String, childName: String) {
            if (userId.isEmpty() || username.isEmpty() || childName.isEmpty()) {
                Log.e(TAG, "Invalid parameters for matching completed message")
                return
            }
            
            findAdminAndSendMessage(userId, username) { adminId ->
                val message = "$HEART_EMOJI Matching completed! $username has been matched with $childName. 3-day acceptance period starts now."
                sendSystemMessageToAdmin(userId, username, adminId, message, MATCHING_COMPLETED)
                
                val userMessage = "$HEART_EMOJI Great news! You've been matched with $childName. You have 3 days to accept this match."
                sendSystemMessageToUser(userId, adminId, userMessage, MATCHING_COMPLETED)
                
                Log.d(TAG, "Matching completed messages sent for user: $username with child: $childName")
            }
        }
        
        /**
         * Send system message when user completes an adoption step
         */
        fun sendStepCompletedMessage(userId: String, username: String, stepNumber: Int) {
            if (userId.isEmpty() || username.isEmpty() || stepNumber < 1 || stepNumber > 10) {
                Log.e(TAG, "Invalid parameters for step completion message")
                return
            }
            
            findAdminAndSendMessage(userId, username) { adminId ->
                val message = "$COMPLETED_EMOJI $username has completed Step $stepNumber of the adoption process."
                sendSystemMessageToAdmin(userId, username, adminId, message, STEP_COMPLETED)
                
                // Send congratulatory message to user for ALL steps
                val userMessage = when (stepNumber) {
                    1 -> "$CELEBRATION_EMOJI Congratulations! You've completed Step 1 - Personal Information! 🎉\n\nGreat start to your adoption journey! Your admin will guide you to the next step."
                    2 -> "$CELEBRATION_EMOJI Amazing! You've completed Step 2 - Background Check! ✅\n\nYou're making excellent progress. Keep going!"
                    3 -> "$CELEBRATION_EMOJI Fantastic! You've completed Step 3 - Financial Assessment! 💰\n\nYou're showing great commitment to this process!"
                    4 -> "$CELEBRATION_EMOJI Wonderful! You've completed Step 4 - Home Study! 🏠\n\nYour home is being prepared for a new family member!"
                    5 -> "$CELEBRATION_EMOJI Excellent! You've completed Step 5 - References Check! 👥\n\nHalfway through the process - you're doing great!"
                    6 -> "$CELEBRATION_EMOJI Outstanding! You've completed Step 6 - Medical Examination! 🏥\n\nYour health clearance is an important milestone!"
                    7 -> "$CELEBRATION_EMOJI Impressive! You've completed Step 7 - Training Program! 📚\n\nYou're well-prepared for parenthood!"
                    8 -> "$CELEBRATION_EMOJI Remarkable! You've completed Step 8 - Legal Documentation! 📋\n\nAlmost there - just 2 more steps to go!"
                    9 -> "$CELEBRATION_EMOJI Incredible! You've completed Step 9 - Final Interview! 🗣️\n\nYou're so close to completing your adoption journey!"
                    10 -> "$CELEBRATION_EMOJI CONGRATULATIONS! You've completed Step 10 - Final Approval! 🎊\n\nYou've successfully completed ALL 10 steps of the adoption process! Your case will now move to history and you're ready for the next phase!"
                    else -> "$COMPLETED_EMOJI You've completed Step $stepNumber! Keep up the great work!"
                }
                
                sendSystemMessageToUser(userId, adminId, userMessage, STEP_COMPLETED)
                
                Log.d(TAG, "Step $stepNumber completion message sent for user: $username")
            }
        }
        
        /**
         * Send system message when a new adoption process starts automatically after completion
         */
        fun sendNewAdoptionStartedMessage(userId: String, username: String, completedAdoptionNumber: Long, newAdoptionNumber: Long) {
            if (userId.isEmpty() || username.isEmpty()) {
                Log.e(TAG, "Invalid parameters for new adoption started message")
                return
            }
            
            findAdminAndSendMessage(userId, username) { adminId ->
                val adminMessage = "$CELEBRATION_EMOJI $username has completed Adoption #$completedAdoptionNumber! A new Adoption #$newAdoptionNumber has been automatically started and is ready for guidance."
                sendSystemMessageToAdmin(userId, username, adminId, adminMessage, ADOPTION_STARTED)
                
                val userMessage = "$CELEBRATION_EMOJI Congratulations on completing Adoption #$completedAdoptionNumber! 🎉\n\n" +
                        "✨ Your completed adoption is now safely stored in your history.\n" +
                        "🆕 Adoption #$newAdoptionNumber has been automatically started for you!\n" +
                        "📋 You can begin the process again whenever you're ready.\n\n" +
                        "Your admin is here to guide you through the next steps!"
                sendSystemMessageToUser(userId, adminId, userMessage, ADOPTION_STARTED)
                
                Log.d(TAG, "New adoption started messages sent for user: $username (completed #$completedAdoptionNumber, started #$newAdoptionNumber)")
            }
        }
        
        /**
         * Send generic admin notification
         */
        fun sendAdminNotification(userId: String, username: String, message: String) {
            if (userId.isEmpty() || username.isEmpty() || message.isEmpty()) {
                Log.e(TAG, "Invalid parameters for admin notification")
                return
            }
            
            findAdminAndSendMessage(userId, username) { adminId ->
                sendSystemMessageToAdmin(userId, username, adminId, message, ADMIN_NOTIFICATION)
                Log.d(TAG, "Admin notification sent: $message")
            }
        }
        
        /**
         * Send generic user notification
         */
        fun sendUserNotification(userId: String, adminId: String, message: String) {
            if (userId.isEmpty() || adminId.isEmpty() || message.isEmpty()) {
                Log.e(TAG, "Invalid parameters for user notification")
                return
            }
            
            sendSystemMessageToUser(userId, adminId, message, USER_NOTIFICATION)
            Log.d(TAG, "User notification sent: $message")
        }
        
        /**
         * Send system alert (high priority message)
         */
        fun sendSystemAlert(userId: String, message: String, isForAdmin: Boolean = false) {
            if (userId.isEmpty() || message.isEmpty()) {
                Log.e(TAG, "Invalid parameters for system alert")
                return
            }
            
            val alertMessage = "$WARNING_EMOJI ALERT: $message"
            
            if (isForAdmin) {
                // Get user data and send to admin
                FirebaseFirestore.getInstance().collection("users").document(userId).get()
                    .addOnSuccessListener { userDoc ->
                        val username = userDoc.getString("username") ?: "User"
                        findAdminAndSendMessage(userId, username) { adminId ->
                            sendSystemMessageToAdmin(userId, username, adminId, alertMessage, SYSTEM_ALERT)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get user data for system alert: ${e.message}")
                    }
            } else {
                // Send to user - need to find their connected admin
                findAdminAndSendMessage(userId, "User") { adminId ->
                    sendSystemMessageToUser(userId, adminId, alertMessage, SYSTEM_ALERT)
                }
            }
            
            Log.d(TAG, "System alert sent: $message")
        }
        
        /**
         * Internal method to send system message to admin
         */
        private fun sendSystemMessageToAdmin(
            userId: String, 
            username: String, 
            adminId: String, 
            message: String, 
            messageType: String,
            relatedId: String = "",
            donationType: String = ""
        ) {
            val chatId = getChatId(userId, adminId)
            createSystemMessage(chatId, "system", adminId, message, messageType, relatedId, donationType, username)
        }
        
        /**
         * Internal method to send system message to user
         */
        private fun sendSystemMessageToUser(
            userId: String,
            adminId: String,
            message: String,
            messageType: String,
            relatedId: String = "",
            donationType: String = "",
            senderName: String = "System"
        ) {
            val chatId = getChatId(userId, adminId)
            createSystemMessage(chatId, "system", userId, message, messageType, relatedId, donationType, senderName)
        }
        
        /**
         * Core method to create and store system messages
         */
        private fun createSystemMessage(
            chatId: String,
            senderId: String,
            receiverId: String,
            message: String,
            messageType: String,
            relatedId: String = "",
            donationType: String = "",
            senderName: String = "System"
        ) {
            val realtimeDb = FirebaseDatabase.getInstance(DATABASE_URL).reference
            
            // Ensure chat exists first
            val chatRef = realtimeDb.child("chats").child(chatId)
            chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        // Create chat if it doesn't exist
                        val chatData = hashMapOf(
                            "connection_type" to "system",
                            "last_message" to message,
                            "last_message_timestamp" to ServerValue.TIMESTAMP,
                            "created_by" to senderId,
                            "participant_user" to if (senderId == "system") receiverId else senderId,
                            "participant_admin" to if (receiverId != senderId) receiverId else senderId,
                            "created_at" to ServerValue.TIMESTAMP,
                            "unread_count" to 1,
                            "last_activity" to ServerValue.TIMESTAMP
                        )
                        
                        chatRef.setValue(chatData)
                            .addOnSuccessListener {
                                sendSystemMessageToChat(chatRef, senderId, receiverId, message, messageType, relatedId, donationType, senderName)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to create chat for system message: ${e.message}")
                            }
                    } else {
                        // Chat exists, send message directly
                        sendSystemMessageToChat(chatRef, senderId, receiverId, message, messageType, relatedId, donationType, senderName)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to check chat existence: ${error.message}")
                }
            })
        }
        
        /**
         * Send the actual system message to the chat
         */
        private fun sendSystemMessageToChat(
            chatRef: DatabaseReference,
            senderId: String,
            receiverId: String,
            message: String,
            messageType: String,
            relatedId: String,
            donationType: String,
            senderName: String
        ) {
            // Update chat metadata
            val chatUpdates = hashMapOf(
                "last_message" to message,
                "last_message_timestamp" to ServerValue.TIMESTAMP,
                "last_activity" to ServerValue.TIMESTAMP
            )
            
            // Increment unread count for receiver
            chatRef.child("unread_count").setValue(ServerValue.increment(1))
            chatRef.updateChildren(chatUpdates as Map<String, Any>)
            
            // Create comprehensive system message
            val systemMessage = hashMapOf(
                "messageId" to chatRef.child("messages").push().key,
                "senderId" to senderId,
                "receiverId" to receiverId,
                "senderName" to senderName,
                "message" to message,
                "timestamp" to System.currentTimeMillis(),
                "serverTimestamp" to ServerValue.TIMESTAMP,
                "isSystemMessage" to true,
                "messageType" to messageType,
                "donationId" to relatedId,
                "donationType" to donationType,
                "deleted_by_sender" to false,
                "deleted_by_receiver" to false,
                "read_by_receiver" to false,
                "priority" to if (messageType == SYSTEM_ALERT) "high" else "normal",
                "created_at" to ServerValue.TIMESTAMP
            )
            
            // Add to chat messages
            chatRef.child("messages").push().setValue(systemMessage)
                .addOnSuccessListener {
                    Log.d(TAG, "System message sent successfully: $messageType - $message")
                    
                    // Send FCM notification if needed
                    sendNotificationIfNeeded(receiverId, senderName, message, messageType)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send system message: ${e.message}")
                }
        }
        
        /**
         * Find and connect with admin for messaging
         */
        private fun findAdminAndSendMessage(userId: String, username: String, callback: (String) -> Unit) {
            val firestore = FirebaseFirestore.getInstance()
            
            // Try to find an admin user
            firestore.collection("users")
                .whereEqualTo("role", "admin")
                .limit(1)
                .get()
                .addOnSuccessListener { adminSnapshot ->
                    if (!adminSnapshot.isEmpty) {
                        val adminDoc = adminSnapshot.documents[0]
                        val adminId = adminDoc.id
                        callback(adminId)
                    } else {
                        // Create default admin if none exists
                        createDefaultAdminForMessages(userId, username, callback)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error finding admin: ${e.message}")
                    createDefaultAdminForMessages(userId, username, callback)
                }
        }
        
        /**
         * Create default admin account if none exists
         */
        private fun createDefaultAdminForMessages(_userId: String, _username: String, callback: (String) -> Unit) {
            val firestore = FirebaseFirestore.getInstance()
            val defaultAdminId = "meritxell_default_admin"
            
            val defaultAdminData = hashMapOf(
                "username" to "MeritxellAdmin",
                "firstName" to "Meritxell",
                "lastName" to "Administrator",
                "middleName" to "System",
                "email" to "admin@meritxell.org",
                "role" to "admin",
                "isVerified" to true,
                "birthdate" to "1990-01-01",
                "joinDate" to System.currentTimeMillis(),
                "isOnline" to true,
                "lastActive" to System.currentTimeMillis()
            )
            
            firestore.collection("users").document(defaultAdminId)
                .set(defaultAdminData)
                .addOnSuccessListener {
                    Log.d(TAG, "Default admin created for system messages")
                    callback(defaultAdminId)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to create default admin: ${e.message}")
                }
        }
        
        /**
         * Generate consistent chat ID for two users
         */
        private fun getChatId(user1: String, user2: String): String {
            return if (user1 < user2) "${user1}_$user2" else "${user2}_$user1"
        }
        
        /**
         * Send FCM notification for system messages
         */
        private fun sendNotificationIfNeeded(receiverId: String, senderName: String, message: String, messageType: String) {
            // Check if user has FCM token and send notification
            FirebaseFirestore.getInstance().collection("users").document(receiverId).get()
                .addOnSuccessListener { userDoc ->
                    val fcmToken = userDoc.getString("fcmToken")
                    if (!fcmToken.isNullOrEmpty()) {
                        // Send FCM notification via Cloud Functions
                        sendFCMNotification(receiverId, senderName, message, messageType, fcmToken)
                    } else {
                        Log.d(TAG, "No FCM token found for user: $receiverId")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get FCM token for notification: ${e.message}")
                }
        }
        
        /**
         * Send FCM notification via Cloud Functions
         */
        private fun sendFCMNotification(
            receiverId: String, 
            senderName: String, 
            message: String, 
            messageType: String,
            fcmToken: String
        ) {
            // Prepare notification data
            val notificationData = hashMapOf(
                "fcmToken" to fcmToken,
                "messageType" to messageType,
                "senderName" to senderName,
                "body" to message,
                "receiverId" to receiverId,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Add specific data based on message type
            when (messageType) {
                ADOPTION_STARTED, STEP_COMPLETED, MATCHING_COMPLETED -> {
                    notificationData["title"] = "👶 Adoption Update"
                    notificationData["category"] = "adoption"
                }
                DONATION_SUBMITTED, DONATION_APPROVED, DONATION_REJECTED -> {
                    notificationData["title"] = "📦 Donation Update"
                    notificationData["category"] = "donation"
                }
                APPOINTMENT_SCHEDULED, APPOINTMENT_CANCELLED -> {
                    notificationData["title"] = "📅 Appointment Update"
                    notificationData["category"] = "appointment"
                }
                ADMIN_NOTIFICATION, USER_NOTIFICATION -> {
                    notificationData["title"] = "💬 New Message"
                    notificationData["category"] = "message"
                }
                SYSTEM_ALERT -> {
                    notificationData["title"] = "⚠️ System Alert"
                    notificationData["category"] = "alert"
                }
                else -> {
                    notificationData["title"] = "🔔 Notification"
                    notificationData["category"] = "general"
                }
            }
            
            // Call Cloud Function to send notification
            FirebaseFunctions.getInstance()
                .getHttpsCallable("sendPushNotification")
                .call(notificationData)
                .addOnSuccessListener { result ->
                    Log.d(TAG, "FCM notification sent successfully for $messageType")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send FCM notification: ${e.message}")
                }
        }
        
        /**
         * Mark system messages as read
         */
        fun markSystemMessagesAsRead(chatId: String, userId: String) {
            val realtimeDb = FirebaseDatabase.getInstance(DATABASE_URL).reference
            val chatRef = realtimeDb.child("chats").child(chatId)
            
            // Reset unread count
            chatRef.child("unread_count").setValue(0)
            
            // Mark all messages as read for this user
            chatRef.child("messages").orderByChild("receiverId").equalTo(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (messageSnapshot in snapshot.children) {
                            messageSnapshot.ref.child("read_by_receiver").setValue(true)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Failed to mark messages as read: ${error.message}")
                    }
                })
        }
        
        /**
         * Get system message statistics
         */
        fun getSystemMessageStats(userId: String, callback: (Map<String, Int>) -> Unit) {
            val realtimeDb = FirebaseDatabase.getInstance(DATABASE_URL).reference
            
            // Find user's chats and count system messages
            realtimeDb.child("chats").orderByChild("participant_user").equalTo(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val stats = mutableMapOf<String, Int>()
                        var totalChats = 0
                        var processedChats = 0
                        
                        for (chatSnapshot in snapshot.children) {
                            totalChats++
                            
                            chatSnapshot.child("messages").children.forEach { messageSnapshot ->
                                val isSystemMessage = messageSnapshot.child("isSystemMessage").getValue(Boolean::class.java) ?: false
                                val messageType = messageSnapshot.child("messageType").getValue(String::class.java) ?: ""
                                
                                if (isSystemMessage && messageType.isNotEmpty()) {
                                    stats[messageType] = stats.getOrDefault(messageType, 0) + 1
                                }
                            }
                            
                            processedChats++
                            if (processedChats == totalChats) {
                                callback(stats)
                            }
                        }
                        
                        if (totalChats == 0) {
                            callback(emptyMap())
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Failed to get system message stats: ${error.message}")
                        callback(emptyMap())
                    }
                })
        }
    }
} 