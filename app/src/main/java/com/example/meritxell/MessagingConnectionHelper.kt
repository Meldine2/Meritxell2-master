package com.example.meritxell

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class MessagingConnectionHelper {
    
    private val realtimeDb = FirebaseDatabase.getInstance("https://ally-user-default-rtdb.asia-southeast1.firebasedatabase.app").reference
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "MessagingConnectionHelper"
        private const val DATABASE_URL = "https://ally-user-default-rtdb.asia-southeast1.firebasedatabase.app"
        
        @Volatile
        private var INSTANCE: MessagingConnectionHelper? = null
        
        fun getInstance(): MessagingConnectionHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessagingConnectionHelper().also { INSTANCE = it }
            }
        }
        
        /**
         * Creates automatic chat connection when user starts adoption process
         */
        fun createAdoptionConnection(userId: String, username: String) {
            Log.d(TAG, "Creating adoption connection for user: $username ($userId)")
            
            getFirstAdminUser { adminUserId, adminUsername ->
                if (adminUserId != null && adminUsername != null) {
                    createChatConnection(
                        userId = userId,
                        username = username,
                        adminId = adminUserId,
                        adminName = adminUsername,
                        connectionType = "adoption",
                        systemMessage = "$username started their adoption process. They are now connected to admin support for guidance and assistance."
                    )
                } else {
                    Log.w(TAG, "No admin user found for adoption connection")
                }
            }
        }
        
        /**
         * Creates automatic chat connection when user submits donation
         */
        fun createDonationConnection(
            userId: String, 
            username: String, 
            donationType: String,
            donationId: String? = null
        ) {
            Log.d(TAG, "🎯 CREATING DONATION CONNECTION 🎯")
            Log.d(TAG, "User: $username ($userId)")
            Log.d(TAG, "Donation Type: $donationType")
            Log.d(TAG, "Donation ID: $donationId")
            
            // First check if user already has existing chats to use the same admin
            checkExistingChatForUser(userId) { existingAdminId ->
                if (existingAdminId != null) {
                    Log.d(TAG, "🔄 USER HAS EXISTING CHAT WITH ADMIN: $existingAdminId")
                    Log.d(TAG, "Using same admin to maintain chat continuity")
                    
                    val donationTypeDisplay = when (donationType.lowercase()) {
                        "toys" -> "Toys Donation"
                        "clothes" -> "Clothes Donation" 
                        "food" -> "Food Donation"
                        "education" -> "Education Donation"
                        "money" -> "Money Donation"
                        "medicine" -> "Medicine Sponsorship"
                        else -> "$donationType Donation"
                    }
                    
                    val systemMessage = if (donationId != null) {
                        "$username submitted a $donationTypeDisplay (ID: $donationId). Admin can review the submission details and provide assistance."
                    } else {
                        "$username initiated a $donationTypeDisplay process. They are now connected to admin support."
                    }
                    
                    createChatConnection(
                        userId = userId,
                        username = username,
                        adminId = existingAdminId,
                        adminName = "Admin", // We'll get the real name later
                        connectionType = "${donationType.lowercase().replace(" ", "_")}_donation",
                        systemMessage = systemMessage,
                        donationId = donationId,
                        donationType = donationType
                    ) { chatId ->
                        if (donationId != null) {
                            sendDonationFormToChat(chatId, donationId, donationType)
                        }
                    }
                } else {
                    Log.d(TAG, "🆕 NO EXISTING CHAT - SELECTING NEW ADMIN")
                    
                    val donationTypeDisplay = when (donationType.lowercase()) {
                        "toys" -> "Toys Donation"
                        "clothes" -> "Clothes Donation" 
                        "food" -> "Food Donation"
                        "education" -> "Education Donation"
                        "money" -> "Money Donation"
                        "medicine" -> "Medicine Sponsorship"
                        else -> "$donationType Donation"
                    }
                    
                    getFirstAdminUser { adminUserId, adminUsername ->
                        if (adminUserId != null && adminUsername != null) {
                            val systemMessage = if (donationId != null) {
                                "$username submitted a $donationTypeDisplay (ID: $donationId). Admin can review the submission details and provide assistance."
                            } else {
                                "$username initiated a $donationTypeDisplay process. They are now connected to admin support."
                            }
                            
                            createChatConnection(
                                userId = userId,
                                username = username,
                                adminId = adminUserId,
                                adminName = adminUsername,
                                connectionType = "${donationType.lowercase().replace(" ", "_")}_donation",
                                systemMessage = systemMessage,
                                donationId = donationId,
                                donationType = donationType
                            ) { chatId ->
                                if (donationId != null) {
                                    sendDonationFormToChat(chatId, donationId, donationType)
                                }
                            }
                        } else {
                            Log.w(TAG, "No admin user found for donation connection")
                        }
                    }
                }
            }
        }
        
        private fun checkExistingChatForUser(userId: String, callback: (String?) -> Unit) {
            val database = FirebaseDatabase.getInstance(DATABASE_URL)
            val chatsRef = database.reference.child("chats")
            
            Log.d(TAG, "🔍 CHECKING EXISTING CHATS FOR USER: $userId")
            
            chatsRef.orderByChild("participant_user").equalTo(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists() && snapshot.childrenCount > 0) {
                            // User has existing chats, get the admin from the first one
                            val firstChat = snapshot.children.first()
                            val adminId = firstChat.child("participant_admin").getValue(String::class.java)
                            Log.d(TAG, "✅ Found existing chat with admin: $adminId")
                            callback(adminId)
                        } else {
                            Log.d(TAG, "❌ No existing chats found for user")
                            callback(null)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error checking existing chats: ${error.message}")
                        callback(null)
                    }
                })
        }
        
        /**
         * Creates automatic chat connection when user schedules appointment
         */
        fun createAppointmentConnection(
            userId: String,
            username: String,
            appointmentType: String,
            appointmentDate: String
        ) {
            Log.d(TAG, "Creating appointment connection for user: $username ($userId)")
            
            getFirstAdminUser { adminUserId, adminUsername ->
                if (adminUserId != null && adminUsername != null) {
                    createChatConnection(
                        userId = userId,
                        username = username,
                        adminId = adminUserId,
                        adminName = adminUsername,
                        connectionType = "appointment",
                        systemMessage = "$username scheduled a $appointmentType appointment for $appointmentDate. Admin can provide pre-appointment guidance and support."
                    )
                } else {
                    Log.w(TAG, "No admin user found for appointment connection")
                }
            }
        }
        
        private fun createChatConnection(
            userId: String,
            username: String,
            adminId: String,
            adminName: String,
            connectionType: String,
            systemMessage: String,
            donationId: String? = null,
            donationType: String? = null,
            onChatCreated: ((String) -> Unit)? = null
        ) {
            val chatId = getChatRoomId(userId, adminId)
            val database = FirebaseDatabase.getInstance(DATABASE_URL)
            val chatRef = database.reference.child("chats").child(chatId)
            
            Log.d(TAG, "🔥🔥🔥 CHAT CONNECTION DEBUG 🔥🔥🔥")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Username: $username")
            Log.d(TAG, "Admin ID: $adminId")
            Log.d(TAG, "Admin Name: $adminName")
            Log.d(TAG, "Generated Chat ID: $chatId")
            Log.d(TAG, "Connection Type: $connectionType")
            Log.d(TAG, "Donation ID: $donationId")
            Log.d(TAG, "Donation Type: $donationType")
            Log.d(TAG, "🔥🔥🔥 END DEBUG 🔥🔥🔥")
            
            // First check if chat already exists
            chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val timestamp = System.currentTimeMillis()
                    
                    if (!snapshot.exists()) {
                        // Create new chat
                        Log.d(TAG, "🆕 CREATING NEW CHAT 🆕")
                        Log.d(TAG, "Chat ID: $chatId does not exist, creating new one")
                        Log.d(TAG, "Connection type: $connectionType")
                        
                        val chatData = hashMapOf(
                            "connection_type" to connectionType,
                            "last_message" to systemMessage,
                            "last_message_timestamp" to ServerValue.TIMESTAMP,
                            "created_by" to userId,
                            "participant_user" to userId,
                            "participant_admin" to adminId,
                            "created_at" to ServerValue.TIMESTAMP,
                            "unread_count" to 1,
                            "last_activity" to ServerValue.TIMESTAMP,
                            "auto_created" to true
                        )
                        
                        chatRef.setValue(chatData)
                            .addOnSuccessListener {
                                Log.d(TAG, "✅ NEW CHAT CREATED SUCCESSFULLY")
                                Log.d(TAG, "Chat ID: $chatId")
                                sendSystemMessage(chatRef, userId, adminId, username, systemMessage, connectionType, donationId, donationType)
                                onChatCreated?.invoke(chatId)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "❌ Failed to create chat: ${e.message}")
                            }
                    } else {
                        // Chat exists, just send system message without changing connection type
                        // This preserves the chat history and prevents confusion
                        Log.d(TAG, "♻️ CHAT EXISTS - ADDING MESSAGE ♻️")
                        Log.d(TAG, "Chat ID: $chatId already exists")
                        Log.d(TAG, "Existing connection type: ${snapshot.child("connection_type").getValue(String::class.java)}")
                        Log.d(TAG, "New connection type: $connectionType")
                        Log.d(TAG, "Existing messages count: ${snapshot.child("messages").childrenCount}")
                        
                        val updates = hashMapOf<String, Any>(
                            "last_message" to systemMessage,
                            "last_message_timestamp" to ServerValue.TIMESTAMP,
                            "last_activity" to ServerValue.TIMESTAMP
                        )
                        
                        chatRef.updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d(TAG, "✅ EXISTING CHAT UPDATED SUCCESSFULLY")
                                sendSystemMessage(chatRef, userId, adminId, username, systemMessage, connectionType, donationId, donationType)
                                onChatCreated?.invoke(chatId)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "❌ Failed to update chat: ${e.message}")
                            }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ Database error: ${error.message}")
                }
            })
        }
        
        private fun sendSystemMessage(
            chatRef: DatabaseReference,
            userId: String,
            adminId: String,
            username: String,
            systemMessage: String,
            connectionType: String,
            donationId: String? = null,
            donationType: String? = null
        ) {
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            Log.d(TAG, "📨 SENDING SYSTEM MESSAGE 📨")
            Log.d(TAG, "Message ID: $messageId")
            Log.d(TAG, "Chat ID: ${getChatRoomId(userId, adminId)}")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Admin ID: $adminId")
            Log.d(TAG, "Username: $username")
            Log.d(TAG, "Message: $systemMessage")
            Log.d(TAG, "Connection Type: $connectionType")
            Log.d(TAG, "Donation ID: $donationId")
            Log.d(TAG, "Donation Type: $donationType")
            
            val message = hashMapOf(
                "messageId" to messageId,
                "senderId" to "system",
                "receiverId" to adminId,
                "senderName" to "System",
                "message" to systemMessage,
                "timestamp" to timestamp,
                "serverTimestamp" to ServerValue.TIMESTAMP,
                "read_by_receiver" to false,
                "deleted_by_sender" to false,
                "deleted_by_receiver" to false,
                "isSystemMessage" to true,
                "connectionType" to connectionType,
                "userId" to userId,
                "username" to username,
                "donationId" to (donationId ?: ""),
                "donationType" to (donationType ?: ""),
                "edited" to false,
                "editedTimestamp" to 0L
            )
            
            chatRef.child("messages").child(messageId).setValue(message)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ SYSTEM MESSAGE SENT SUCCESSFULLY")
                    Log.d(TAG, "Message ID: $messageId")
                    Log.d(TAG, "Message sent to chat: ${getChatRoomId(userId, adminId)}")
                    
                    // Update chat metadata
                    val chatUpdates = hashMapOf<String, Any>(
                        "last_message" to systemMessage,
                        "last_message_timestamp" to ServerValue.TIMESTAMP,
                        "unread_count" to ServerValue.increment(1),
                        "last_activity" to ServerValue.TIMESTAMP
                    )
                    
                    chatRef.updateChildren(chatUpdates)
                    
                    // Send notification to admin about new connection
                    sendConnectionNotification(adminId, username, connectionType, systemMessage)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ FAILED TO SEND SYSTEM MESSAGE")
                    Log.e(TAG, "Error: ${e.message}")
                    Log.e(TAG, "Message ID: $messageId")
                }
        }
        
        private fun sendConnectionNotification(
            adminId: String,
            username: String,
            connectionType: String,
            message: String
        ) {
            val notificationData = hashMapOf(
                "title" to "🔗 New Connection: $username",
                "body" to message,
                "type" to "new_connection",
                "connection_type" to connectionType,
                "username" to username,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Use the existing NotificationManagerHelper to send notification
            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("notification_logs")
                    .add(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Connection notification logged")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to log connection notification: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending connection notification: ${e.message}")
            }
        }
        
        private fun getFirstAdminUser(callback: (String?, String?) -> Unit) {
            val firestore = FirebaseFirestore.getInstance()
            
            // Get ALL admin users first to ensure consistent selection
            firestore.collection("users")
                .whereEqualTo("role", "admin")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        // Sort by document ID to ensure consistent ordering across all calls
                        val adminDocs = querySnapshot.documents.sortedBy { it.id }
                        val document = adminDocs.first()
                        val adminId = document.id
                        val adminUsername = document.getString("username") ?: "Admin"
                        
                        Log.d(TAG, "🔥 ADMIN SELECTION DEBUG 🔥")
                        Log.d(TAG, "✅ Selected admin user: $adminUsername ($adminId)")
                        Log.d(TAG, "Total admin documents: ${querySnapshot.size()}")
                        Log.d(TAG, "All admin IDs: ${adminDocs.map { it.id }}")
                        Log.d(TAG, "Selected admin is ALWAYS the first by ID sort")
                        callback(adminId, adminUsername)
                    } else {
                        Log.w(TAG, "⚠️ No admin users found in database")
                        callback(null, null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error finding admin user: ${e.message}")
                    callback(null, null)
                }
        }
        
        private fun getChatRoomId(user1: String, user2: String): String {
            return if (user1 < user2) "${user1}_$user2" else "${user2}_$user1"
        }
        
        /**
         * Update existing connection type for a user
         */
        fun updateConnectionType(userId: String, newConnectionType: String) {
            Log.d(TAG, "Updating connection type for user $userId to $newConnectionType")
            
            getFirstAdminUser { adminId, _ ->
                if (adminId != null) {
                    val chatId = getChatRoomId(userId, adminId)
                    val database = FirebaseDatabase.getInstance(DATABASE_URL)
                    val chatRef = database.reference.child("chats").child(chatId)
                    
                    chatRef.child("connection_type").setValue(newConnectionType)
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ Connection type updated to $newConnectionType")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ Failed to update connection type: ${e.message}")
                        }
                }
            }
        }

        /**
         * Creates general support chat connection
         */
        fun createSupportChatConnection(userId: String, username: String = "", reason: String = "general") {
            Log.d(TAG, "Creating support chat connection for user: $userId, reason: $reason")
            
            getAdminUsers { adminUsers ->
                if (adminUsers.isNotEmpty()) {
                    val supportAdmin = adminUsers.find { it.contains("system") } ?: adminUsers.first()
                    
                    createChatConnection(
                        userId = userId,
                        username = username,
                        adminId = supportAdmin,
                        adminName = "Support Admin",
                        connectionType = "support",
                        systemMessage = "User $username requested support ($reason). How can we assist you?"
                    )
                }
            }
        }
        
        private fun getAdminUsers(callback: (List<String>) -> Unit) {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users")
                .whereEqualTo("role", "admin")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val adminIds = mutableListOf<String>()
                    for (document in querySnapshot.documents) {
                        adminIds.add(document.id)
                    }
                    Log.d(TAG, "Found ${adminIds.size} admin users")
                    callback(adminIds)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading admin users: ${e.message}")
                    // Fallback to predefined admin IDs
                    callback(listOf("admin_system", "admin_adoption", "admin_donation"))
                }
        }
        
        /**
         * Allows viewing donation form directly in chat for admins
         */
        fun sendDonationFormMessage(
            chatId: String,
            donationId: String,
            donationType: String,
            formData: Map<String, Any>
        ) {
            val realtimeDb = FirebaseDatabase.getInstance(DATABASE_URL).reference
            val formMessage = buildDonationFormMessage(donationType, formData)
            
            val messageData = hashMapOf(
                "messageId" to java.util.UUID.randomUUID().toString(),
                "senderId" to "system",
                "receiverId" to "admin",
                "senderName" to "Donation System",
                "message" to formMessage,
                "timestamp" to System.currentTimeMillis(),
                "serverTimestamp" to ServerValue.TIMESTAMP,
                "read_by_receiver" to false,
                "deleted_by_sender" to false,
                "deleted_by_receiver" to false,
                "isSystemMessage" to true,
                "donationId" to donationId,
                "donationType" to donationType,
                "edited" to false,
                "editedTimestamp" to 0L,
                "isDonationForm" to true
            )
            
            realtimeDb.child("chats").child(chatId).child("messages").push()
                .setValue(messageData)
                .addOnSuccessListener {
                    Log.d(TAG, "Donation form message sent to chat: $chatId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send donation form message: ${e.message}")
                }
        }
        
        private fun buildDonationFormMessage(donationType: String, formData: Map<String, Any>): String {
            val builder = StringBuilder()
            builder.append("📋 ${donationType.capitalize()} Donation Details:\n\n")
            
            formData.forEach { (key, value) ->
                if (value.toString().isNotEmpty()) {
                    val formattedKey = key.replace("_", " ").split(" ")
                        .joinToString(" ") { it.capitalize() }
                    builder.append("$formattedKey: $value\n")
                }
            }
            
            builder.append("\n📞 Contact the user to coordinate collection/delivery.")
            return builder.toString()
        }

        /**
         * Sends donation form details to chat after donation is submitted
         */
        private fun sendDonationFormToChat(chatId: String, donationId: String, donationType: String) {
            Log.d(TAG, "Sending donation form details to chat: $chatId for donation: $donationId")
            
            val collectionName = when (donationType.lowercase()) {
                "toys" -> "toysdonation"
                "clothes" -> "clothesdonation"
                "food" -> "fooddonation"
                "education" -> "educationdonation"
                "money" -> "donations"
                else -> "donations" // fallback for generic donations
            }
            
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection(collectionName).document(donationId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val formData = document.data ?: return@addOnSuccessListener
                        sendDonationFormMessage(chatId, donationId, donationType, formData)
                    } else {
                        Log.w(TAG, "Donation document not found: $donationId in $collectionName")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch donation details for chat: ${e.message}")
                }
        }
    }
} 