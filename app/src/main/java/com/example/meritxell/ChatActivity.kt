package com.example.meritxell

import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deleted_by_sender: Boolean = false,
    val deleted_by_receiver: Boolean = false,
    val read_by_receiver: Boolean = false,
    val isSystemMessage: Boolean = false,
    val donationId: String = "",
    val donationType: String = "",
    val messageId: String = "",
    val edited: Boolean = false,
    val editedTimestamp: Long = 0L
)

class ChatActivity : AppCompatActivity() {

    private lateinit var messagesContainer: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var scrollView: ScrollView
    private lateinit var tvChatHeader: TextView
    private lateinit var tvUserName: TextView
    private lateinit var btnUserProfile: ImageButton
    private lateinit var tvOnlineStatus: TextView

    private val dbRef = FirebaseDatabase.getInstance("https://ally-user-default-rtdb.asia-southeast1.firebasedatabase.app").reference
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var chatWithUserId: String
    private lateinit var chatWithUserName: String
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var currentUserRole: String = "user"
    private var currentUsername: String = "Unknown"
    private lateinit var chatId: String
    private lateinit var notificationHelper: NotificationHelper
    private var isActivityVisible = false
    private var isInitialLoadComplete = false
    private lateinit var enhancedNotificationManager: EnhancedNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        // Set the activity title to the app name
        title = getString(R.string.app_name)
        
        // Hide the action bar to use custom header
        supportActionBar?.hide()

        chatWithUserId = intent.getStringExtra("chatUserId") ?: ""
        chatWithUserName = intent.getStringExtra("chatUserName") ?: "Chat"

        if (chatWithUserId.isEmpty() || currentUserId.isEmpty()) {
            Toast.makeText(this, "Invalid chat parameters", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        chatId = getChatRoomId(currentUserId, chatWithUserId)
        
        Log.d("ChatActivity", "🔥 CHAT PERSISTENCE DEBUG 🔥")
        Log.d("ChatActivity", "Opening chat: $chatId")
        Log.d("ChatActivity", "Current user: $currentUserId")
        Log.d("ChatActivity", "Chat with user: $chatWithUserId")
        Log.d("ChatActivity", "Chat with user name: $chatWithUserName")
        
        initViews()
        loadCurrentUserRole()
        setupListeners()
        
        // Reset initial load flag
        isInitialLoadComplete = false
        
        listenForMessages()
        
        // Initialize notification helpers
        notificationHelper = NotificationHelper(this)
        enhancedNotificationManager = EnhancedNotificationManager(this)
        
        // Mark messages as read when opening chat
        markMessagesAsRead()
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        
        // Update chat state in preferences for notification management
        updateChatState(chatId, true)
        
        // Cancel any existing notifications for this chat
        enhancedNotificationManager.cancelChatNotifications(chatId)
        
        // Mark messages as read when returning to chat
        markMessagesAsRead()
        
        Log.d("ChatActivity", "Activity resumed - notifications for chat $chatId will be suppressed")
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        
        // Clear chat state in preferences
        updateChatState("", false)
        
        Log.d("ChatActivity", "Activity paused - notifications for chat $chatId will be shown")
    }



    private fun initViews() {
        messagesContainer = findViewById(R.id.messagesContainer)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        scrollView = findViewById(R.id.messageScroll)
        tvChatHeader = findViewById(R.id.tvChatHeader)
        tvUserName = findViewById(R.id.tvUserName)
        btnUserProfile = findViewById(R.id.btnUserProfile)
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus)
        
        // Make user name clickable to view profile
        tvUserName.text = chatWithUserName
        tvUserName.setOnClickListener {
            openUserProfile()
        }
        
        // The background and clickable styling is already set in the XML
    }

    private fun loadCurrentUserRole() {
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                currentUserRole = doc.getString("role") ?: "user"
                currentUsername = doc.getString("username") ?: "Unknown"
                
                // Update online status text
                tvOnlineStatus.text = "Tap name to view profile"
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error loading user role: ${e.message}")
                currentUserRole = "user"
                currentUsername = "Unknown"
            }
    }

    private fun setupListeners() {
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
        }

        btnUserProfile.setOnClickListener {
            openUserProfile()
        }
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Enhanced keyboard handling
        messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Scroll to bottom when input gains focus
                scrollView.post {
                    scrollToBottom()
                }
            }
        }
        
        // Handle keyboard show/hide
        messageInput.setOnClickListener {
            // Force focus and show keyboard
            messageInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(messageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            
            // Scroll to bottom after a short delay
            messageInput.postDelayed({
                scrollToBottom()
            }, 300)
        }
        
        // Handle enter key to send message
        messageInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                val messageText = messageInput.text.toString().trim()
                if (messageText.isNotEmpty()) {
                    sendMessage(messageText)
                }
                true
            } else {
                false
            }
        }
    }

    private fun openUserProfile() {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("userId", chatWithUserId)
        intent.putExtra("username", chatWithUserName)
        startActivity(intent)
    }

    private fun getChatRoomId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_$user2" else "${user2}_$user1"
    }

    private fun sendMessage(text: String) {
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("ChatActivity", "=== STARTING MESSAGE SEND ===")
        Log.d("ChatActivity", "Current User ID: $currentUserId")
        Log.d("ChatActivity", "Chat With User ID: $chatWithUserId")
        Log.d("ChatActivity", "Chat ID: $chatId")
        Log.d("ChatActivity", "Message Text: $text")
        
        // Clear input immediately to prevent double sending
        messageInput.text.clear()
        
        val message = hashMapOf(
            "messageId" to UUID.randomUUID().toString(),
            "senderId" to currentUserId,
            "receiverId" to chatWithUserId,
            "senderName" to currentUsername,
            "message" to text.trim(),
            "timestamp" to System.currentTimeMillis(),
            "serverTimestamp" to ServerValue.TIMESTAMP,
            "read_by_receiver" to false,
            "deleted_by_sender" to false,
            "deleted_by_receiver" to false,
            "isSystemMessage" to false,
            "donationId" to "",
            "donationType" to "",
            "edited" to false,
            "editedTimestamp" to 0L
        )

        val chatRef = dbRef.child("chats").child(chatId)
        
        // First ensure the chat exists
        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.d("ChatActivity", "Creating new chat: $chatId")
                    
                    // Check current user role in real-time to ensure accuracy
                    firestore.collection("users").document(currentUserId).get()
                        .addOnSuccessListener { userDoc ->
                            val actualCurrentUserRole = userDoc.getString("role") ?: "user"
                            
                            // Properly determine who is admin and who is user based on roles
                            val (participantUser, participantAdmin) = if (actualCurrentUserRole == "admin") {
                                // Current user is admin, other user is regular user
                                Pair(chatWithUserId, currentUserId)
                            } else {
                                // Current user is regular user, other user is admin
                                Pair(currentUserId, chatWithUserId)
                            }
                            
                            Log.d("ChatActivity", "🔧 CHAT PARTICIPANT ASSIGNMENT:")
                            Log.d("ChatActivity", "   Current user: $currentUserId (role: $actualCurrentUserRole)")
                            Log.d("ChatActivity", "   Other user: $chatWithUserId")
                            Log.d("ChatActivity", "   Assigned participant_user: $participantUser")
                            Log.d("ChatActivity", "   Assigned participant_admin: $participantAdmin")
                            
                            // Create the chat first
                            val chatData = hashMapOf(
                                "connection_type" to "manual",
                                "last_message" to text.trim(),
                                "last_message_timestamp" to ServerValue.TIMESTAMP,
                                "created_by" to currentUserId,
                                "participant_user" to participantUser,
                                "participant_admin" to participantAdmin,
                                "created_at" to ServerValue.TIMESTAMP,
                                "unread_count" to 1,
                                "last_activity" to ServerValue.TIMESTAMP
                            )
                            
                            chatRef.setValue(chatData)
                                .addOnSuccessListener {
                                    Log.d("ChatActivity", "Chat created successfully, now sending message")
                                    sendActualMessage(chatRef, message, text.trim())
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ChatActivity", "Failed to create chat: ${e.message}", e)
                                    runOnUiThread {
                                        Toast.makeText(this@ChatActivity, "Failed to create chat: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatActivity", "Failed to get user role, defaulting to user: ${e.message}")
                            // Default to assuming current user is regular user
                            val chatData = hashMapOf(
                                "connection_type" to "manual",
                                "last_message" to text.trim(),
                                "last_message_timestamp" to ServerValue.TIMESTAMP,
                                "created_by" to currentUserId,
                                "participant_user" to currentUserId,
                                "participant_admin" to chatWithUserId,
                                "created_at" to ServerValue.TIMESTAMP,
                                "unread_count" to 1,
                                "last_activity" to ServerValue.TIMESTAMP
                            )
                            
                            chatRef.setValue(chatData)
                                .addOnSuccessListener {
                                    Log.d("ChatActivity", "Chat created successfully, now sending message")
                                    sendActualMessage(chatRef, message, text.trim())
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ChatActivity", "Failed to create chat: ${e.message}", e)
                                    runOnUiThread {
                                        Toast.makeText(this@ChatActivity, "Failed to create chat: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                } else {
                    Log.d("ChatActivity", "Chat exists, sending message directly")
                    sendActualMessage(chatRef, message, text.trim())
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Failed to check chat existence: ${error.message}")
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Database error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    
    private fun sendActualMessage(chatRef: DatabaseReference, message: HashMap<String, Any>, messageText: String) {
        Log.d("ChatActivity", "=== SENDING ACTUAL MESSAGE ===")
        
        // Update chat metadata first
        val chatUpdates = hashMapOf(
            "last_message" to messageText,
            "last_message_timestamp" to ServerValue.TIMESTAMP,
            "last_activity" to ServerValue.TIMESTAMP
        )
        
        chatRef.updateChildren(chatUpdates as Map<String, Any>)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Chat metadata updated")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to update chat metadata: ${e.message}")
            }
        
        // Add the message to the messages collection
        val messageRef = chatRef.child("messages").push()
        val messageKey = messageRef.key
        
        Log.d("ChatActivity", "Generated message key: $messageKey")
        
        messageRef.setValue(message)
            .addOnSuccessListener {
                Log.d("ChatActivity", "✅ MESSAGE SENT SUCCESSFULLY!")
                Log.d("ChatActivity", "Message ID: $messageKey")
                Log.d("ChatActivity", "Message content: $messageText")
                
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Message sent!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "❌ FAILED TO SEND MESSAGE: ${e.message}", e)
                
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
                    // Restore the message text if send failed
                    messageInput.setText(messageText)
                }
            }
    }

    private fun listenForMessages() {
        val messagesRef = dbRef.child("chats").child(chatId).child("messages")
        
        Log.d("ChatActivity", "Starting message listener for chat: $chatId")

        messagesRef.orderByChild("timestamp").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val messageId = snapshot.key ?: return
                val msg = snapshot.getValue(Message::class.java)
                
                Log.d("ChatActivity", "Message received: $messageId, content: ${msg?.message}, initialLoadComplete: $isInitialLoadComplete")
                
                msg?.let {
                    if (it.message.isNotEmpty() && !isMessageDeleted(it)) {
                        // Only add new messages after initial load is complete to prevent duplicates
                        if (isInitialLoadComplete) {
                            runOnUiThread {
                                displayMessage(it, messageId)
                                scrollToBottom()
                            }
                        }
                        
                        // Enhanced notification handling
                        if (it.senderId != currentUserId && !isActivityVisible && !it.isSystemMessage) {
                            // Use enhanced notification manager for better notification experience
                            enhancedNotificationManager.showChatNotification(
                                senderName = chatWithUserName,
                                messageText = it.message,
                                chatUserId = chatWithUserId,
                                chatUserName = chatWithUserName,
                                chatId = chatId
                            )
                        }
                        
                        // Handle system messages with appropriate notifications
                        if (it.isSystemMessage && it.receiverId == currentUserId && !isActivityVisible) {
                            when {
                                it.message.contains("adoption", ignoreCase = true) -> {
                                    enhancedNotificationManager.showAdoptionNotification(
                                        "Adoption Update",
                                        it.message,
                                        it.receiverId
                                    )
                                }
                                it.message.contains("donation", ignoreCase = true) -> {
                                    enhancedNotificationManager.showDonationNotification(
                                        "Donation Update",
                                        it.message,
                                        it.donationType
                                    )
                                }
                                else -> {
                                    enhancedNotificationManager.showSystemNotification(
                                        "System Message",
                                        it.message
                                    )
                                }
                            }
                        }
                        
                        // Auto-mark as read if activity is visible and message is for current user
                        if (it.receiverId == currentUserId && isActivityVisible && !it.read_by_receiver) {
                            markSingleMessageAsRead(messageId)
                        }
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                Log.d("ChatActivity", "Message changed: ${snapshot.key}")
                // Handle individual message changes without clearing the entire chat
                val messageId = snapshot.key ?: return
                val msg = snapshot.getValue(Message::class.java)
                
                msg?.let {
                    runOnUiThread {
                        // Find and update the specific message instead of reloading everything
                        updateMessageInUI(messageId, it)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Message listener cancelled: ${error.message}", error.toException())
                Toast.makeText(this@ChatActivity, "Connection lost: ${error.message}", Toast.LENGTH_LONG).show()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                Log.d("ChatActivity", "Message moved: ${snapshot.key}")
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                Log.d("ChatActivity", "Message removed: ${snapshot.key}")
                val messageId = snapshot.key ?: return
                runOnUiThread {
                    removeMessageFromUI(messageId)
                }
            }
        })
        
        // Initial load of all messages
        loadAllMessages()
    }

    private fun loadAllMessages() {
        val messagesRef = dbRef.child("chats").child(chatId).child("messages")
        
        Log.d("ChatActivity", "🔥 LOADING ALL MESSAGES 🔥")
        Log.d("ChatActivity", "Chat ID: $chatId")
        Log.d("ChatActivity", "Messages path: chats/$chatId/messages")
        
        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("ChatActivity", "🔥 MESSAGE LOAD RESULT 🔥")
                Log.d("ChatActivity", "Total messages in database: ${snapshot.childrenCount}")
                
                runOnUiThread {
                    messagesContainer.removeAllViews()
                    
                    val messages = mutableListOf<Pair<String, Message>>()
                    
                    for (messageSnapshot in snapshot.children) {
                        val messageId = messageSnapshot.key ?: continue
                        val msg = messageSnapshot.getValue(Message::class.java)
                        
                        Log.d("ChatActivity", "Processing message: $messageId")
                        Log.d("ChatActivity", "Message content: ${msg?.message}")
                        Log.d("ChatActivity", "Message sender: ${msg?.senderId}")
                        Log.d("ChatActivity", "Message timestamp: ${msg?.timestamp}")
                        Log.d("ChatActivity", "Is system message: ${msg?.isSystemMessage}")
                        
                        msg?.let {
                            if (it.message.isNotEmpty() && !isMessageDeleted(it)) {
                                messages.add(Pair(messageId, it))
                                Log.d("ChatActivity", "✅ Added message to display list: $messageId")
                            } else {
                                Log.d("ChatActivity", "❌ Skipped message (empty or deleted): $messageId")
                            }
                        }
                    }
                    
                    // Sort by timestamp to ensure correct order
                    messages.sortBy { it.second.timestamp }
                    
                    Log.d("ChatActivity", "🔥 FINAL MESSAGE COUNT 🔥")
                    Log.d("ChatActivity", "Messages to display: ${messages.size}")
                    
                    // Add connection header at the top using already loaded messages
                    addConnectionHeaderFromMessages(messages)
                    
                    // Display all messages
                    for ((messageId, message) in messages) {
                        displayMessage(message, messageId)
                        Log.d("ChatActivity", "Displayed message: $messageId - ${message.message.take(50)}")
                    }
                    
                    scrollToBottom()
                    
                    // Mark initial load as complete
                    isInitialLoadComplete = true
                    
                    Log.d("ChatActivity", "🔥 MESSAGE LOADING COMPLETE 🔥")
                    Log.d("ChatActivity", "Displayed ${messages.size} messages in UI")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "🔥 MESSAGE LOADING FAILED 🔥")
                Log.e("ChatActivity", "Error: ${error.message}", error.toException())
                Toast.makeText(this@ChatActivity, "Failed to load messages: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun isMessageDeleted(message: Message): Boolean {
        return if (message.senderId == currentUserId) {
            message.deleted_by_sender
        } else {
            message.deleted_by_receiver
        }
    }

    private fun displayMessage(message: Message, messageId: String) {
        val messageCard = createMessageCard(message, messageId)
        messageCard.tag = messageId // Tag the view with messageId for easy finding
        messagesContainer.addView(messageCard)
        
        // Check if this message should have a separate donation button card
        val shouldShowDonationButton = (message.isSystemMessage && (message.donationId.isNotEmpty() || 
                                       message.message.contains("donation", ignoreCase = true))) ||
                                      (message.message.contains("donation", ignoreCase = true) && 
                                       (message.message.contains("ID:", ignoreCase = true) || 
                                        message.message.contains("submitted", ignoreCase = true)))
        
        if (shouldShowDonationButton) {
            // Extract donation info
            var donationId = message.donationId
            var donationType = message.donationType
            
            // If donationId is empty, try to extract from message text
            if (donationId.isEmpty() && message.message.contains("ID:", ignoreCase = true)) {
                val idPattern = "ID:\\s*([a-zA-Z0-9]+)".toRegex(RegexOption.IGNORE_CASE)
                val matchResult = idPattern.find(message.message)
                donationId = matchResult?.groupValues?.get(1) ?: ""
            }
            
            // If donationType is empty, try to extract from message text
            if (donationType.isEmpty()) {
                when {
                    message.message.contains("toys", ignoreCase = true) -> donationType = "Toys"
                    message.message.contains("clothes", ignoreCase = true) -> donationType = "Clothes"
                    message.message.contains("food", ignoreCase = true) -> donationType = "Food"
                    message.message.contains("education", ignoreCase = true) -> donationType = "Education"
                    message.message.contains("money", ignoreCase = true) -> donationType = "Money"
                    message.message.contains("medicine", ignoreCase = true) -> donationType = "Medicine"
                    else -> donationType = "Donation"
                }
            }
            
            // Create separate donation button card
            val donationButtonCard = createDonationButtonCard(donationId, donationType, messageId)
            messagesContainer.addView(donationButtonCard)
        }
    }
    
    private fun updateMessageInUI(messageId: String, updatedMessage: Message) {
        // Find the existing message view by its tag
        for (i in 0 until messagesContainer.childCount) {
            val child = messagesContainer.getChildAt(i)
            if (child.tag == messageId) {
                // Remove the old message view
                messagesContainer.removeViewAt(i)
                
                // Add the updated message view at the same position
                if (!isMessageDeleted(updatedMessage)) {
                    val updatedCard = createMessageCard(updatedMessage, messageId)
                    messagesContainer.addView(updatedCard, i)
                }
                break
            }
        }
    }
    
    private fun removeMessageFromUI(messageId: String) {
        // Find and remove the message view by its tag
        for (i in 0 until messagesContainer.childCount) {
            val child = messagesContainer.getChildAt(i)
            if (child.tag == messageId) {
                messagesContainer.removeViewAt(i)
                break
            }
        }
    }
    
    private fun addConnectionHeaderFromMessages(messages: List<Pair<String, Message>>) {
        // Check if header already exists to prevent duplicates
        if (messagesContainer.childCount > 0) {
            val firstChild = messagesContainer.getChildAt(0)
            if (firstChild.tag == "connection_header") {
                Log.d("ChatActivity", "Connection header already exists, skipping")
                return
            }
        }
        
        Log.d("ChatActivity", "🏷️ ADDING CONNECTION HEADER FROM LOADED MESSAGES 🏷️")
        Log.d("ChatActivity", "Messages available: ${messages.size}")
        
        // Find donation ID from messages and query Firestore directly
        var donationId: String? = null
        for ((messageId, message) in messages) {
            if (message.donationId.isNotEmpty()) {
                donationId = message.donationId
                Log.d("ChatActivity", "🔍 Found donation ID: $donationId")
                break
            }
        }
        
        if (donationId != null) {
            // Query Firestore directly to get the actual donation type
            queryFirestoreForDonationType(donationId) { actualDonationType ->
                runOnUiThread {
                    val finalConnectionType = actualDonationType ?: "general"
                    Log.d("ChatActivity", "🔥 FIRESTORE RESULT: $finalConnectionType")
                    
                    val headerCard = createConnectionHeaderCard(finalConnectionType)
                    headerCard.tag = "connection_header"
                    messagesContainer.addView(headerCard, 0)
                    
                    Log.d("ChatActivity", "✅ CONNECTION HEADER ADDED FROM FIRESTORE")
                }
            }
        } else {
            // Fallback to general if no donation ID found
            val headerCard = createConnectionHeaderCard("general")
            headerCard.tag = "connection_header"
            messagesContainer.addView(headerCard, 0)
            Log.d("ChatActivity", "✅ CONNECTION HEADER ADDED (GENERAL FALLBACK)")
        }
    }
    
    private fun queryFirestoreForDonationType(donationId: String, callback: (String?) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()
        
        Log.d("ChatActivity", "🔍 QUERYING FIRESTORE FOR DONATION ID: $donationId")
        
        // Check main donations collection first
        firestore.collection("donations").document(donationId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val donationType = document.getString("donationType")
                    Log.d("ChatActivity", "✅ FOUND IN DONATIONS COLLECTION: $donationType")
                    
                    val mappedType = when (donationType?.lowercase()) {
                        "money", "money sponsorship" -> "money_donation"
                        "education", "education sponsorship" -> "education_donation"
                        "medicine", "medicine sponsorship" -> "medicine_donation"
                        else -> "money_donation" // Default for donations collection
                    }
                    callback(mappedType)
                } else {
                    // Check other collections
                    checkOtherCollectionsForDonationType(donationId, callback)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error querying donations collection: ${e.message}")
                checkOtherCollectionsForDonationType(donationId, callback)
            }
    }
    
    private fun checkOtherCollectionsForDonationType(donationId: String, callback: (String?) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()
        val collections = listOf(
            "toysdonation" to "toys_donation",
            "clothesdonation" to "clothes_donation",
            "fooddonation" to "food_donation",
            "educationdonation" to "education_donation"
        )
        
        var collectionsChecked = 0
        var found = false
        
        for ((collectionName, donationType) in collections) {
            firestore.collection(collectionName).document(donationId).get()
                .addOnSuccessListener { document ->
                    collectionsChecked++
                    
                    if (document.exists() && !found) {
                        found = true
                        Log.d("ChatActivity", "✅ FOUND IN $collectionName COLLECTION")
                        callback(donationType)
                        return@addOnSuccessListener
                    }
                    
                    if (collectionsChecked == collections.size && !found) {
                        Log.d("ChatActivity", "❌ NOT FOUND IN ANY COLLECTION")
                        callback(null)
                    }
                }
                .addOnFailureListener { e ->
                    collectionsChecked++
                    Log.e("ChatActivity", "Error checking $collectionName: ${e.message}")
                    
                    if (collectionsChecked == collections.size && !found) {
                        callback(null)
                    }
                }
        }
    }

    private fun addConnectionHeader() {
        // Check if header already exists to prevent duplicates
        if (messagesContainer.childCount > 0) {
            val firstChild = messagesContainer.getChildAt(0)
            if (firstChild.tag == "connection_header") {
                Log.d("ChatActivity", "Connection header already exists, skipping")
                return
            }
        }
        
        Log.d("ChatActivity", "🏷️ ADDING CONNECTION HEADER 🏷️")
        Log.d("ChatActivity", "Chat ID: $chatId")
        
        // Get chat metadata and analyze messages to determine specific connection type
        val chatRef = dbRef.child("chats").child(chatId)
        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connectionType = snapshot.child("connection_type").getValue(String::class.java) ?: "general"
                val messagesCount = snapshot.child("messages").childrenCount
                
                Log.d("ChatActivity", "🏷️ CHAT METADATA LOADED 🏷️")
                Log.d("ChatActivity", "Connection type: $connectionType")
                Log.d("ChatActivity", "Messages count in chat: $messagesCount")
                
                // Analyze messages to get more specific donation type
                analyzeMessagesForDonationType(snapshot) { specificType ->
                    runOnUiThread {
                        // Double-check that header doesn't exist before adding
                        if (messagesContainer.childCount > 0) {
                            val firstChild = messagesContainer.getChildAt(0)
                            if (firstChild.tag == "connection_header") {
                                Log.d("ChatActivity", "Connection header already exists during callback, skipping")
                                return@runOnUiThread
                            }
                        }
                        
                        // FORCE MONEY DONATION DETECTION - check all messages again
                        var forcedType: String? = null
                        for (messageSnapshot in snapshot.child("messages").children) {
                            val message = messageSnapshot.getValue(Message::class.java)
                            if (message?.message?.contains("Money donation", ignoreCase = true) == true || 
                                message?.message?.contains("money donation", ignoreCase = true) == true) {
                                forcedType = "money_donation"
                                Log.d("ChatActivity", "🔥 FORCED MONEY DONATION DETECTION from message: ${message.message}")
                                break
                            }
                        }
                        
                        val finalConnectionType = forcedType ?: specificType ?: connectionType
                        Log.d("ChatActivity", "🏷️ Final connection type: $finalConnectionType")
                        val headerCard = createConnectionHeaderCard(finalConnectionType)
                        headerCard.tag = "connection_header" // Tag to identify the header
                        messagesContainer.addView(headerCard, 0) // Add at the top
                        
                        Log.d("ChatActivity", "✅ CONNECTION HEADER ADDED")
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "❌ Failed to load chat metadata: ${error.message}")
            }
        })
    }
    
    private fun analyzeMessagesForDonationType(chatSnapshot: DataSnapshot, callback: (String?) -> Unit) {
        val messagesSnapshot = chatSnapshot.child("messages")
        var detectedType: String? = null
        var donationId: String? = null
        
        Log.d("ChatActivity", "🔍 ANALYZING MESSAGES FOR DONATION TYPE 🔍")
        Log.d("ChatActivity", "Messages in snapshot: ${messagesSnapshot.childrenCount}")
        
        // Look through messages to find donation-related information
        var messageCount = 0
        for (messageSnapshot in messagesSnapshot.children) {
            messageCount++
            val messageId = messageSnapshot.key
            val message = messageSnapshot.getValue(Message::class.java)
            
            Log.d("ChatActivity", "🔍 Analyzing message $messageCount: $messageId")
            Log.d("ChatActivity", "   Message content: ${message?.message}")
            Log.d("ChatActivity", "   Is system message: ${message?.isSystemMessage}")
            Log.d("ChatActivity", "   Donation ID: ${message?.donationId}")
            Log.d("ChatActivity", "   Donation Type: ${message?.donationType}")
            
            if (message != null) {
                // Check if message has donation type info
                if (message.donationType.isNotEmpty()) {
                    detectedType = "${message.donationType.lowercase()}_donation"
                    Log.d("ChatActivity", "✅ Found donation type from message field: $detectedType")
                    break
                }
                
                // Get donation ID for database lookup
                if (message.donationId.isNotEmpty()) {
                    donationId = message.donationId
                    Log.d("ChatActivity", "✅ Found donation ID: $donationId")
                }
                
                // AGGRESSIVE message content analysis - check ALL messages, not just system messages
                val messageText = message.message.lowercase()
                when {
                    messageText.contains("money donation") || 
                    messageText.contains("monetary donation") || 
                    messageText.contains("cash donation") ||
                    messageText.contains("money sponsorship") ||
                    messageText.contains("gcash") ||
                    messageText.contains("receipt") ||
                    messageText.contains("payment") ||
                    (messageText.contains("money") && messageText.contains("donation")) -> {
                        detectedType = "money_donation"
                        Log.d("ChatActivity", "✅ DETECTED MONEY DONATION from message text: '${message.message}'")
                        break
                    }
                    messageText.contains("toys donation") || messageText.contains("toy donation") -> {
                        detectedType = "toys_donation"
                        Log.d("ChatActivity", "✅ Detected toys donation from message text")
                        break
                    }
                    messageText.contains("clothes donation") || messageText.contains("clothing donation") -> {
                        detectedType = "clothes_donation"
                        Log.d("ChatActivity", "✅ Detected clothes donation from message text")
                        break
                    }
                    messageText.contains("food donation") -> {
                        detectedType = "food_donation"
                        Log.d("ChatActivity", "✅ Detected food donation from message text")
                        break
                    }
                    messageText.contains("education donation") || messageText.contains("educational donation") -> {
                        detectedType = "education_donation"
                        Log.d("ChatActivity", "✅ Detected education donation from message text")
                        break
                    }
                    messageText.contains("medicine sponsorship") || messageText.contains("medicine donation") -> {
                        detectedType = "medicine_donation"
                        Log.d("ChatActivity", "✅ Detected medicine sponsorship from message text")
                        break
                    }
                    messageText.contains("adoption") -> {
                        detectedType = "adoption"
                        Log.d("ChatActivity", "✅ Detected adoption from message text")
                        break
                    }
                }
            }
        }
        
        Log.d("ChatActivity", "🔍 ANALYSIS COMPLETE 🔍")
        Log.d("ChatActivity", "Detected type: $detectedType")
        Log.d("ChatActivity", "Donation ID: $donationId")
        Log.d("ChatActivity", "Total messages analyzed: $messageCount")
        
        // If we detected a type from messages, use it immediately
        if (detectedType != null) {
            Log.d("ChatActivity", "🎯 USING DETECTED TYPE FROM MESSAGES: $detectedType")
            callback(detectedType)
            return
        }
        
        // If we have a donation ID but no detected type, query the database
        if (donationId != null) {
            Log.d("ChatActivity", "🔍 Querying database for donation type...")
            queryDonationTypeFromDatabase(donationId) { dbType ->
                Log.d("ChatActivity", "🔍 Database query result: $dbType")
                callback(dbType)
            }
        } else {
            Log.d("ChatActivity", "🔍 No type detected, using null")
            callback(null)
        }
    }
    
    private fun queryDonationTypeFromDatabase(donationId: String, callback: (String?) -> Unit) {
        Log.d("ChatActivity", "🔍 Querying database for donation type with ID: $donationId")
        
        val firestore = FirebaseFirestore.getInstance()
        
        // First check the main donations collection (for money/education/etc)
        firestore.collection("donations").document(donationId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val dbDonationType = document.getString("donationType") ?: ""
                    Log.d("ChatActivity", "✅ Found donation in 'donations' collection")
                    Log.d("ChatActivity", "   Raw donationType: '$dbDonationType'")
                    
                    val mappedType = when (dbDonationType.lowercase()) {
                        "money" -> "money_donation"
                        "money sponsorship" -> "money_donation"
                        "education sponsorship" -> "education_donation"
                        "education" -> "education_donation"
                        "medicine sponsorship" -> "medicine_donation"
                        "medicine" -> "medicine_donation"
                        "food" -> "food_donation"
                        "toys" -> "toys_donation"
                        "clothes" -> "clothes_donation"
                        else -> "${dbDonationType.lowercase().replace(" ", "_")}_donation"
                    }
                    
                    Log.d("ChatActivity", "   Mapped type: '$mappedType'")
                    callback(mappedType)
                    return@addOnSuccessListener
                }
                
                // If not found in donations collection, check other collections
                checkOtherDonationCollections(donationId, callback)
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error checking donations collection: ${e.message}")
                checkOtherDonationCollections(donationId, callback)
            }
    }
    
    private fun checkOtherDonationCollections(donationId: String, callback: (String?) -> Unit) {
        Log.d("ChatActivity", "🔍 Checking other donation collections for ID: $donationId")
        
        val firestore = FirebaseFirestore.getInstance()
        val collections = listOf(
            "toysdonation" to "toys_donation",
            "clothesdonation" to "clothes_donation", 
            "fooddonation" to "food_donation",
            "educationdonation" to "education_donation"
        )
        
        var foundType: String? = null
        var collectionsChecked = 0
        
        for ((collectionName, donationType) in collections) {
            firestore.collection(collectionName).document(donationId).get()
                .addOnSuccessListener { document ->
                    collectionsChecked++
                    
                    if (document.exists() && foundType == null) {
                        foundType = donationType
                        Log.d("ChatActivity", "✅ Found donation in $collectionName collection, type: $foundType")
                        callback(foundType)
                        return@addOnSuccessListener
                    }
                    
                    // If all collections checked and nothing found
                    if (collectionsChecked == collections.size && foundType == null) {
                        Log.d("ChatActivity", "❌ Donation not found in any collection")
                        callback(null)
                    }
                }
                .addOnFailureListener { e ->
                    collectionsChecked++
                    Log.e("ChatActivity", "Error checking $collectionName: ${e.message}")
                    
                    if (collectionsChecked == collections.size && foundType == null) {
                        callback(null)
                    }
                }
        }
    }
    
    private fun determineDonationTypeFromId(donationId: String): String? {
        // If the donation ID contains type indicators, extract them
        return when {
            donationId.contains("toys", ignoreCase = true) -> "toys_donation"
            donationId.contains("clothes", ignoreCase = true) -> "clothes_donation"
            donationId.contains("food", ignoreCase = true) -> "food_donation"
            donationId.contains("education", ignoreCase = true) -> "education_donation"
            donationId.contains("money", ignoreCase = true) -> "money_donation"
            donationId.contains("medicine", ignoreCase = true) -> "medicine_donation"
            else -> {
                // If we can't determine from ID, we could potentially query the database
                // For now, return null to use the fallback
                Log.d("ChatActivity", "🤷 Could not determine donation type from ID: $donationId")
                null
            }
        }
    }
    
    private fun createDonationButtonCard(donationId: String, donationType: String, parentMessageId: String): View {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 4, 16, 8) // Smaller top margin since it follows the system message
            }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.blue_50))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            gravity = Gravity.CENTER
        }

        // Action text
        val actionText = TextView(this).apply {
            text = "📋 View donation details and manage submission"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.grey_600))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        layout.addView(actionText)

        // View donation button
        val btnViewDonation = Button(this).apply {
            text = "📋 View $donationType Details"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            
            // Modern button design
            background = ContextCompat.getDrawable(context, R.drawable.rounded_button_background)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.orange))
            elevation = 6f
            
            // Add ripple effect
            foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            
            setOnClickListener {
                // Add click animation
                animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                if (donationId.isNotEmpty()) {
                    viewDonationForm(donationId, donationType)
                } else {
                    Toast.makeText(context, "Donation ID not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(btnViewDonation)



        cardView.addView(layout)
        cardView.tag = "${parentMessageId}_donation_actions" // Tag for identification
        return cardView
    }
    
    private fun createConnectionHeaderCard(connectionType: String): View {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 8)
            }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.blue_50))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            gravity = Gravity.CENTER
        }

        // Connection status text
        val connectionText = TextView(this).apply {
            text = "You are now connected with each other"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.grey_600))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        layout.addView(connectionText)

        // Connection type badge
        val connectionTypeText = TextView(this).apply {
            text = getConnectionTypeDisplayName(connectionType)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            
            // Style as a badge
            background = ContextCompat.getDrawable(context, R.drawable.rounded_button_background)
            backgroundTintList = ColorStateList.valueOf(getConnectionTypeColor(connectionType))
            setPadding(16, 8, 16, 8)
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }
        layout.addView(connectionTypeText)

        cardView.addView(layout)
        return cardView
    }
    
    private fun getConnectionTypeDisplayName(connectionType: String): String {
        val type = connectionType.lowercase()
        
        Log.d("ChatActivity", "🏷️ CONNECTION TYPE DEBUG 🏷️")
        Log.d("ChatActivity", "Original connection type: '$connectionType'")
        Log.d("ChatActivity", "Lowercase type: '$type'")
        
        val result = when {
            // Exact matches for donation types
            type == "toys" || type == "toys_donation" -> "🧸 Toys Donation"
            type == "clothes" || type == "clothes_donation" -> "👕 Clothes Donation"
            type == "food" || type == "food_donation" -> "🍎 Food Donation"
            type == "education" || type == "education_donation" -> "📚 Education Donation"
            type == "money" || type == "money_donation" -> "💰 Money Donation"
            type == "medicine" || type == "medicine_donation" -> "💊 Medicine Sponsorship"
            
            // Other connection types
            type == "adoption" -> "👶 Adoption Process"
            type == "support" -> "🆘 Support Request"
            type == "appointment" -> "📅 Appointment"
            type == "manual" -> "💬 Manual Chat"
            
            // Generic donation fallback
            type == "donation" -> "💝 General Donation"
            
            // Handle any donation type with "_donation" suffix
            type.contains("_donation") -> {
                val donationType = type.replace("_donation", "")
                val displayName = donationType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                Log.d("ChatActivity", "Extracted donation type: '$donationType' -> '$displayName'")
                "💝 $displayName Donation"
            }
            
            // Handle donation types that might be stored without "_donation" suffix
            type.contains("donation") -> {
                val cleanType = type.replace("donation", "").trim()
                if (cleanType.isNotEmpty()) {
                    val displayName = cleanType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    "💝 $displayName Donation"
                } else {
                    "💝 General Donation"
                }
            }
            
            // Last resort fallback
            else -> {
                Log.w("ChatActivity", "Unknown connection type: '$type', using General Chat")
                "💬 General Chat"
            }
        }
        
        Log.d("ChatActivity", "Final display name: '$result'")
        return result
    }
    
    private fun getConnectionTypeColor(connectionType: String): Int {
        val type = connectionType.lowercase()
        
        return when {
            // Exact matches for donation types
            type == "toys" || type == "toys_donation" -> ContextCompat.getColor(this, R.color.orange)
            type == "clothes" || type == "clothes_donation" -> ContextCompat.getColor(this, R.color.blue)
            type == "food" || type == "food_donation" -> ContextCompat.getColor(this, R.color.green)
            type == "education" || type == "education_donation" -> ContextCompat.getColor(this, R.color.blue_500)
            type == "money" || type == "money_donation" -> ContextCompat.getColor(this, R.color.green)
            type == "medicine" || type == "medicine_donation" -> ContextCompat.getColor(this, R.color.red)
            
            // Other connection types
            type == "adoption" -> ContextCompat.getColor(this, R.color.red)
            type == "support" -> ContextCompat.getColor(this, R.color.grey_600)
            type == "appointment" -> ContextCompat.getColor(this, R.color.blue_500)
            type == "manual" -> ContextCompat.getColor(this, R.color.grey_500)
            
            // Generic donation fallback
            type == "donation" -> ContextCompat.getColor(this, R.color.orange)
            
            // Handle any donation type with "_donation" suffix
            type.contains("_donation") -> {
                val donationType = type.replace("_donation", "")
                when (donationType) {
                    "toys" -> ContextCompat.getColor(this, R.color.orange)
                    "clothes" -> ContextCompat.getColor(this, R.color.blue)
                    "food" -> ContextCompat.getColor(this, R.color.green)
                    "education" -> ContextCompat.getColor(this, R.color.blue_500)
                    "money" -> ContextCompat.getColor(this, R.color.green)
                    "medicine" -> ContextCompat.getColor(this, R.color.red)
                    else -> ContextCompat.getColor(this, R.color.orange) // Default donation color
                }
            }
            
            // Handle donation types that might be stored without "_donation" suffix
            type.contains("donation") -> ContextCompat.getColor(this, R.color.orange)
            
            // Default fallback
            else -> ContextCompat.getColor(this, R.color.grey_500)
        }
    }

    private fun createMessageCard(message: Message, messageId: String): View {
        val isCurrentUser = message.senderId == currentUserId
        val isSystemMessage = message.isSystemMessage

        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                if (isSystemMessage) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(if (isCurrentUser && !isSystemMessage) 80 else 16, 8, if (!isCurrentUser && !isSystemMessage) 80 else 16, 8)
                gravity = if (isSystemMessage) Gravity.CENTER else if (isCurrentUser) Gravity.END else Gravity.START
            }
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(
                when {
                    isSystemMessage -> ContextCompat.getColor(context, R.color.grey_200)
                    isCurrentUser -> ContextCompat.getColor(context, R.color.blue)
                    else -> ContextCompat.getColor(context, android.R.color.white)
                }
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        // Message text
        val messageText = TextView(this).apply {
            text = message.message
            textSize = 15f
            setTextColor(
                if (isCurrentUser && !isSystemMessage) ContextCompat.getColor(context, android.R.color.white)
                else ContextCompat.getColor(context, android.R.color.black)
            )
            setPadding(0, 0, 0, 8)
        }
        layout.addView(messageText)
        


        // Bottom info layout (timestamp and status)
        val bottomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isCurrentUser) Gravity.END else Gravity.START
        }

        // Enhanced timestamp with date/time
        val timestampText = TextView(this).apply {
            text = formatMessageTimestamp(message.timestamp)
            textSize = 10f
            setTextColor(
                if (isCurrentUser && !isSystemMessage) ContextCompat.getColor(context, android.R.color.white)
                else ContextCompat.getColor(context, R.color.grey_500)
            )
            alpha = 0.8f
        }
        bottomLayout.addView(timestampText)

        // Read status indicator for sent messages
        if (isCurrentUser && !isSystemMessage) {
            val readStatusIcon = TextView(this).apply {
                text = if (message.read_by_receiver) "✓✓" else "✓"
                textSize = 10f
                setTextColor(
                    if (message.read_by_receiver) ContextCompat.getColor(context, R.color.green)
                    else ContextCompat.getColor(context, android.R.color.white)
                )
                setPadding(8, 0, 0, 0)
                alpha = 0.8f
            }
            bottomLayout.addView(readStatusIcon)
        }

        // Edited indicator
        if (message.edited) {
            val editedText = TextView(this).apply {
                text = "edited"
                textSize = 9f
                setTextColor(
                    if (isCurrentUser && !isSystemMessage) ContextCompat.getColor(context, android.R.color.white)
                    else ContextCompat.getColor(context, R.color.grey_500)
                )
                setPadding(8, 0, 0, 0)
                alpha = 0.7f
                setTypeface(null, android.graphics.Typeface.ITALIC)
            }
            bottomLayout.addView(editedText)
        }

        layout.addView(bottomLayout)
        cardView.addView(layout)

        // Long press for message options (only for non-system messages)
        if (!isSystemMessage) {
            cardView.setOnLongClickListener {
                showMessageOptions(message, messageId)
                true
            }
        }

        return cardView
    }

    private fun formatMessageTimestamp(timestamp: Long): String {
        val messageDate = Date(timestamp)
        val now = Date()
        val calendar = Calendar.getInstance()
        
        // Today's date
        calendar.time = now
        val todayStart = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // Yesterday's date
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = calendar.timeInMillis
        
        return when {
            timestamp >= todayStart -> {
                // Today - show only time
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
            }
            timestamp >= yesterdayStart -> {
                // Yesterday - show "Yesterday HH:mm"
                "Yesterday ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)}"
            }
            else -> {
                // Older - show date and time
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(messageDate)
            }
        }
    }

    private fun showMessageOptions(message: Message, messageId: String) {
        val options = if (message.senderId == currentUserId) {
            arrayOf("Delete for me", "Delete for everyone", "Cancel")
        } else {
            arrayOf("Delete for me", "Cancel")
        }

        AlertDialog.Builder(this)
            .setTitle("Message Options")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Delete for me" -> deleteMessageForMe(messageId)
                    "Delete for everyone" -> deleteMessageForEveryone(messageId)
                }
            }
            .show()
    }

    private fun deleteMessageForMe(messageId: String) {
        val deleteField = if (currentUserRole == "admin") "deleted_by_receiver" else "deleted_by_sender"
        
        dbRef.child("chats").child(chatId).child("messages").child(messageId)
            .child(deleteField).setValue(true)
            .addOnSuccessListener {
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete message", Toast.LENGTH_SHORT).show()
                Log.e("ChatActivity", "Failed to delete message", e)
            }
    }

    private fun deleteMessageForEveryone(messageId: String) {
        val updates = mapOf(
            "deleted_by_sender" to true,
            "deleted_by_receiver" to true,
            "message" to "This message was deleted"
        )
        
        dbRef.child("chats").child(chatId).child("messages").child(messageId)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Message deleted for everyone", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete message", Toast.LENGTH_SHORT).show()
                Log.e("ChatActivity", "Failed to delete message", e)
            }
    }

    private fun viewDonationForm(donationId: String, donationType: String) {
        // Use AdminDonationDetailActivity for both admin and regular users
        // The activity will handle different permissions based on user role
        val intent = Intent(this, AdminDonationDetailActivity::class.java)
        intent.putExtra("donationId", donationId)
        intent.putExtra("donationType", donationType)
        intent.putExtra("viewerRole", currentUserRole) // Pass the user role
        
        // Determine collection name based on donation type
        val collectionName = when (donationType.lowercase()) {
            "toys" -> "toysdonation"
            "clothes" -> "clothesdonation"
            "food" -> "fooddonation"
            "education" -> "educationdonation"
            "money" -> "donations"
            else -> "donations"
        }
        intent.putExtra("collectionName", collectionName)
        
        startActivity(intent)
    }
    
    private fun quickApproveDonation(donationId: String, donationType: String) {
        if (currentUserRole != "admin") {
            Toast.makeText(this, "Only admins can approve donations", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Quick Approve Donation")
            .setMessage("Are you sure you want to approve this $donationType donation?\n\nNote: You'll still need to add proof of donation (picture + comment) later.")
            .setPositiveButton("Approve") { _, _ ->
                performQuickApproval(donationId, donationType)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performQuickApproval(donationId: String, donationType: String) {
        val collectionName = when (donationType.lowercase()) {
            "toys" -> "toysdonation"
            "clothes" -> "clothesdonation"
            "food" -> "fooddonation"
            "education" -> "educationdonation"
            "money" -> "donations"
            else -> "donations"
        }
        
        val updates = mapOf(
            "status" to "approved",
            "approvedAt" to com.google.firebase.Timestamp.now(),
            "approvedBy" to currentUserId
        )
        
        firestore.collection(collectionName).document(donationId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Donation approved successfully! Redirecting to donation history...", Toast.LENGTH_SHORT).show()
                
                // Send approval message to chat
                val approvalMessage = "✅ Your $donationType donation has been approved! Please submit proof of donation (picture + comment) to complete the process."
                sendMessage(approvalMessage)
                
                // Navigate to donation history page
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("history_type", "donation")
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Failed to approve donation: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ChatActivity", "Error approving donation: ${e.message}")
            }
    }
    
    private fun showQuickReplyOptions(donationType: String) {
        val quickReplies = arrayOf(
            "Thank you for your $donationType donation! We'll review it shortly.",
            "Your $donationType donation looks great! We'll contact you for pickup/delivery.",
            "Could you please provide more details about your $donationType donation?",
            "We appreciate your generous $donationType donation to help our children!",
            "Your donation is being processed. We'll update you soon.",
            "Custom message..."
        )
        
        AlertDialog.Builder(this)
            .setTitle("Quick Reply Options")
            .setItems(quickReplies) { _, which ->
                if (which == quickReplies.size - 1) {
                    // Custom message option
                    showCustomMessageDialog()
                } else {
                    sendMessage(quickReplies[which])
                }
            }
            .show()
    }
    
    private fun showCustomMessageDialog() {
        val input = EditText(this).apply {
            hint = "Type your custom message..."
            setPadding(16, 16, 16, 16)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Custom Message")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val customMessage = input.text.toString().trim()
                if (customMessage.isNotEmpty()) {
                    sendMessage(customMessage)
                } else {
                    Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scrollToBottom() {
        scrollView.post { 
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun markMessagesAsRead() {
        val messagesRef = dbRef.child("chats").child(chatId).child("messages")
        messagesRef.orderByChild("receiverId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val updates = mutableMapOf<String, Any>()
                    for (messageSnapshot in snapshot.children) {
                        val messageId = messageSnapshot.key ?: continue
                        val message = messageSnapshot.getValue(Message::class.java)
                        if (message != null && !message.read_by_receiver && message.receiverId == currentUserId) {
                            updates["$messageId/read_by_receiver"] = true
                        }
                    }
                    if (updates.isNotEmpty()) {
                        messagesRef.updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d("ChatActivity", "Marked ${updates.size} messages as read")
                            }
                            .addOnFailureListener { e ->
                                Log.e("ChatActivity", "Failed to mark messages as read: ${e.message}")
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Failed to mark messages as read", error.toException())
                }
            })
    }

    private fun markSingleMessageAsRead(messageId: String) {
        val updates = mapOf(
            "$messageId/read_by_receiver" to true
        )
        
        dbRef.child("chats").child(chatId).child("messages").child(messageId)
            .updateChildren(updates)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Marked message as read")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to mark message as read", e)
            }
    }

    /**
     * Update chat state in preferences for notification management
     */
    private fun updateChatState(chatId: String, isInChat: Boolean) {
        val prefs = getSharedPreferences("chat_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("current_chat_id", chatId)
            .putBoolean("is_in_chat", isInChat)
            .apply()
        
        Log.d("ChatActivity", "Updated chat state: chatId=$chatId, isInChat=$isInChat")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear chat state when activity is destroyed
        updateChatState("", false)
        Log.d("ChatActivity", "ChatActivity destroyed")
    }
}
