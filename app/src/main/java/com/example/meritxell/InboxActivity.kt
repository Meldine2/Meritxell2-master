package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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

data class UserInfo(
    val userId: String,
    val username: String,
    val role: String,
    val lastMessage: String = "",
    val connectionType: String = "general",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

class InboxActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var realtimeDb: DatabaseReference
    
    private lateinit var spinnerFilter: Spinner
    private lateinit var etSearchUsers: EditText
    private lateinit var llUsersContainer: LinearLayout
    private lateinit var tvNoUsers: TextView
    private lateinit var progressBar: ProgressBar
    
    private val allUsersList = mutableListOf<UserInfo>()
    private val filteredUsersList = mutableListOf<UserInfo>()
    private var currentFilter = "All Users"
    private var searchQuery = ""
    private var currentUserRole = "user"
    private var currentUserId = ""
    private val userIds = mutableSetOf<String>()
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper

    companion object {
        private const val TAG = "InboxActivity"
        private const val DATABASE_URL = "https://ally-user-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        realtimeDb = FirebaseDatabase.getInstance(DATABASE_URL).reference

        currentUserId = auth.currentUser?.uid ?: ""
        
        // Initialize notification permission helper
        notificationPermissionHelper = NotificationPermissionHelper(this)
        
        if (currentUserId.isEmpty()) {
            Log.e(TAG, "No authenticated user found")
            Toast.makeText(this, "Please log in to access messages", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Starting InboxActivity for user: $currentUserId")

        initViews()
        setupListeners()
        
        // Load user role and then load users
        loadCurrentUserRole()
        
        // Check notification permissions
        checkNotificationPermissions()
        
        // Setup real-time unread count monitoring for admins
        setupRealtimeUnreadMonitoring()
    }

    private fun initViews() {
        spinnerFilter = findViewById(R.id.spinnerFilter)
        etSearchUsers = findViewById(R.id.etSearchUsers)
        llUsersContainer = findViewById(R.id.llUsersContainer)
        tvNoUsers = findViewById(R.id.tvNoUsers)
        progressBar = findViewById(R.id.progressBar)
        
        // Setup back button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Setup refresh button
        findViewById<ImageView>(R.id.btnRefresh).setOnClickListener {
            forceRefreshUsers()
        }
        
        setupFilterSpinner()
        setupSearchFunctionality()
    }
    
    private fun setupSearchFunctionality() {
        // Implement search functionality as per requirements
        etSearchUsers.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                etSearchUsers.hint = "Search by username, adoption, donation, etc."
            }
        }
    }

    private fun setupFilterSpinner() {
        val filterOptions = if (currentUserRole == "admin") {
            // Admin sees all management options
            arrayOf(
                "All Users",
                "Adoption Users", 
                "Donation Users",
                "Active Chats",
                "Admins", 
                "Regular Users"
            )
            } else {
            // Regular users only see admin connection types
            arrayOf(
                "All Admin Contacts",
                "Adoption Contacts",
                "Donation Contacts",
                "Active Chats"
            )
        }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter
        
        // Set default filter based on user role
        currentFilter = if (currentUserRole == "admin") "All Users" else "All Admin Contacts"
        
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentFilter = filterOptions[position]
                Log.d(TAG, "Filter changed to: $currentFilter")
                applyFilters()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupListeners() {
        etSearchUsers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                Log.d(TAG, "Search query changed to: '$searchQuery'")
                applyFilters()
            }
        })
    }

    private fun loadCurrentUserRole() {
        Log.d(TAG, "🔍 Loading current user role for: $currentUserId")
        
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentUserRole = doc.getString("role") ?: "user"
                    val currentUsername = doc.getString("username") ?: "Unknown"
                    Log.d(TAG, "✅ ROLE LOADED: $currentUserRole, username: $currentUsername")
                    Log.d(TAG, "🔔 Setting up unread monitoring for role: $currentUserRole")
                    loadUsers()
                } else {
                    Log.w(TAG, "⚠️ Current user document not found in Firestore")
                    currentUserRole = "user"
                    loadUsers()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error loading current user role: ${e.message}", e)
                Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                currentUserRole = "user"
                loadUsers()
            }
    }

    private fun loadUsers() {
        Log.d(TAG, "Starting to load users from Firestore for role: $currentUserRole")
        progressBar.visibility = View.VISIBLE
        
        // Clear both lists to prevent duplicates
        allUsersList.clear()
        filteredUsersList.clear()

        if (currentUserRole == "admin") {
            // Admins can see all users with existing chat connections
            loadUsersForAdmin()
        } else {
            // Regular users see admins + users they have active chat connections with
            loadUsersForRegularUser()
        }
    }
    
    private fun loadUsersForAdmin() {
        Log.d(TAG, "Loading users for admin - finding ALL user conversations")
        
        val database = com.google.firebase.database.FirebaseDatabase.getInstance(DATABASE_URL)
        val chatsRef = database.reference.child("chats")
        
        // Find ALL chats that have ANY admin as participant_admin
        // This ensures all admins can see all user conversations
        chatsRef.orderByKey()
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    progressBar.visibility = View.GONE
                    
                    Log.d(TAG, "Scanning ${snapshot.childrenCount} total chats for admin conversations")
                    
                    val userIds = mutableSetOf<String>()
                    
                    for (chatSnapshot in snapshot.children) {
                        val participantAdmin = chatSnapshot.child("participant_admin").getValue(String::class.java)
                        val participantUser = chatSnapshot.child("participant_user").getValue(String::class.java)
                        
                        // Check if this chat has an admin participant (any admin)
                        if (participantAdmin != null && participantUser != null) {
                            // Verify the participant_admin is actually an admin by checking if it's not the current user
                            // and if the participant_user is not an admin
                            if (participantUser != currentUserId) {
                                userIds.add(participantUser)
                                Log.d(TAG, "Found admin chat: admin=$participantAdmin, user=$participantUser")
                            }
                        }
                    }
                    
                    Log.d(TAG, "Found ${userIds.size} unique users with admin chats")
                    
                    if (userIds.isEmpty()) {
                        showNoUsers("No user conversations found.")
                        return
                    }
                    
                    // Load user info for each user ID
                    var loadedUsers = 0
                    val totalUsers = userIds.size
                    
                    for (userId in userIds) {
                        firestore.collection("users").document(userId)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val username = document.getString("username") ?: "Unknown User"
                                    val role = document.getString("role") ?: "user"
                                    
                                    Log.d(TAG, "Loaded admin chat user: $username ($role)")
                                    
                                    val userInfo = UserInfo(
                                        userId = userId,
                                        username = username,
                                        role = role,
                                        lastMessage = "",
                                        connectionType = "general",
                                        lastMessageTimestamp = 0L,
                                        unreadCount = 0,
                                        isOnline = false
                                    )
                                    
                                    allUsersList.add(userInfo)
                                }
                                
                                loadedUsers++
                                if (loadedUsers == totalUsers) {
                                    Log.d(TAG, "Admin loaded ${allUsersList.size} users with chat connections")
                                    runOnUiThread {
                                        applyFilters()
                                        loadChatDataInBackground()
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error loading user $userId: ${e.message}")
                                loadedUsers++
                                if (loadedUsers == totalUsers) {
                                    runOnUiThread {
                                        applyFilters()
                                        loadChatDataInBackground()
                                    }
                                }
                            }
                    }
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    progressBar.visibility = View.GONE
                    Log.e(TAG, "Error loading admin chats: ${error.message}")
                    showNoUsers("Error loading conversations. Please try again.")
                }
            })
    }
    
    private fun loadUsersForRegularUser() {
        Log.d(TAG, "Loading admin users for regular user - using same logic as admin")
        
        // Use EXACTLY the same logic as admin, but filter for admins only
        firestore.collection("users")
            .get()
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                
                if (task.isSuccessful) {
                    val querySnapshot = task.result
                    Log.d(TAG, "Regular user query successful. Total documents: ${querySnapshot?.size() ?: 0}")
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty) {
                        for (document in querySnapshot.documents) {
                            val userId = document.id
                            
                            // Skip current user
                            if (userId == currentUserId) continue
                            
                            val username = document.getString("username") ?: "Unknown User"
                            val role = document.getString("role") ?: "user"
                            
                            Log.d(TAG, "Regular user found user: $username ($role) with ID: $userId")
                            
                            // Only add admin users for regular users
                            if (role == "admin") {
                                val userInfo = UserInfo(
                                    userId = userId,
                                    username = username,
                                    role = role,
                                    lastMessage = "",
                                    connectionType = "general",
                                    lastMessageTimestamp = 0L,
                                    unreadCount = 0,
                                    isOnline = false
                                )
                                
                                allUsersList.add(userInfo)
                                Log.d(TAG, "✅ Added admin user: $username")
                            }
                        }
                        
                        Log.d(TAG, "Regular user loaded ${allUsersList.size} admin users")
                        
                        if (allUsersList.isEmpty()) {
                            Log.w(TAG, "No admin users found, showing fallback")
                            showAdminContactsAlternative()
                        } else {
                            applyFilters()
                            loadChatDataInBackground()
                        }
                    } else {
                        Log.w(TAG, "Empty query result")
                        showAdminContactsAlternative()
                    }
                } else {
                    val exception = task.exception
                    Log.e(TAG, "Error loading users for regular user: ${exception?.message}", exception)
                    
                    if (exception?.message?.contains("PERMISSION_DENIED") == true) {
                        Log.w(TAG, "Permission denied, showing fallback")
                        showAdminContactsAlternative()
                    } else {
                        Toast.makeText(this, "Failed to load users: ${exception?.message}", Toast.LENGTH_LONG).show()
                        showAdminContactsAlternative()
                    }
                }
            }
    }
    
    private fun loadExistingChatConnections() {
        Log.d(TAG, "Loading existing chat connections for user: $currentUserId")
        
        // Load from Firebase Realtime Database to find existing chat connections
        val database = com.google.firebase.database.FirebaseDatabase.getInstance(DATABASE_URL)
        val chatsRef = database.reference.child("chats")
        
        chatsRef.orderByKey()
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    progressBar.visibility = View.GONE
                    
                    var chatConnectionsFound = 0
                    
                    for (chatSnapshot in snapshot.children) {
                        val chatId = chatSnapshot.key ?: continue
                        
                        // Check if this chat involves the current user
                        if (chatId.contains(currentUserId)) {
                            val otherUserId = if (chatId.startsWith(currentUserId)) {
                                chatId.substringAfter("${currentUserId}_")
                            } else {
                                chatId.substringBefore("_$currentUserId")
                            }
                            
                            if (otherUserId.isNotEmpty() && otherUserId != currentUserId) {
                                // Check if we already have this user in our list
                                val existingUser = allUsersList.find { it.userId == otherUserId }
                                if (existingUser == null) {
                                    // Load user info for this chat connection
                                    loadUserInfoForChatConnection(otherUserId, chatSnapshot)
                                    chatConnectionsFound++
                                }
                            }
                        }
                    }
                    
                    Log.d(TAG, "Found $chatConnectionsFound additional chat connections")
                    
                    // Display results
                    if (allUsersList.isEmpty()) {
                        Log.w(TAG, "No contacts found for regular user")
                        showAdminContactsAlternative()
                    } else {
                        Log.d(TAG, "Regular user has ${allUsersList.size} total contacts")
                        applyFilters()
                        loadChatDataInBackground()
                    }
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    progressBar.visibility = View.GONE
                    Log.e(TAG, "Error loading chat connections: ${error.message}")
                    
                    if (allUsersList.isEmpty()) {
                        showAdminContactsAlternative()
                    } else {
                        applyFilters()
                    }
                }
            })
    }
    
    private fun loadUserInfoForChatConnection(userId: String, chatSnapshot: com.google.firebase.database.DataSnapshot) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("username") ?: "Unknown User"
                    val role = document.getString("role") ?: "user"
                    
                    // Determine connection type from chat metadata
                    val connectionType = chatSnapshot.child("connection_type").getValue(String::class.java) ?: "general"
                    val lastMessage = chatSnapshot.child("last_message").getValue(String::class.java) ?: ""
                    val lastMessageTimestamp = chatSnapshot.child("last_message_timestamp").getValue(Long::class.java) ?: 0L
                    
                    Log.d(TAG, "Loaded chat connection: $username ($role) - $connectionType")
                    
                    val userInfo = UserInfo(
                        userId = userId,
                        username = username,
                        role = role,
                        lastMessage = lastMessage,
                        connectionType = connectionType,
                        lastMessageTimestamp = lastMessageTimestamp,
                        unreadCount = 0,
                        isOnline = false
                    )
                    
                    allUsersList.add(userInfo)
                    
                    // Refresh display
                    runOnUiThread {
                        applyFilters()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading user info for chat connection $userId: ${e.message}")
            }
    }
    
    private fun showAdminContactsAlternative() {
        Log.d(TAG, "Showing alternative admin contacts view as fallback")
        
        // Show message to user about why we're showing fallback contacts
        Toast.makeText(this, "No admin users found in system. Showing default contacts.", Toast.LENGTH_LONG).show()
        
        // Create a predefined list of admin contacts for regular users
        val adminContacts = listOf(
            UserInfo(
                userId = "admin_system",
                username = "System Admin",
                role = "admin",
                lastMessage = "Contact for system support",
                connectionType = "support",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                isOnline = true
            ),
            UserInfo(
                userId = "admin_adoption",
                username = "Adoption Support",
                role = "admin", 
                lastMessage = "Contact for adoption process",
                connectionType = "adoption",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                isOnline = true
            ),
            UserInfo(
                userId = "admin_donation",
                username = "Donation Support",
                role = "admin",
                lastMessage = "Contact for donation queries", 
                connectionType = "donation",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                isOnline = true
            )
        )
        
        allUsersList.addAll(adminContacts)
        Log.d(TAG, "Added ${adminContacts.size} predefined admin contacts as fallback")
        applyFilters()
    }
    


    private fun loadChatDataInBackground() {
        Log.d(TAG, "Loading chat data in background for ${allUsersList.size} users")
        
        for (i in allUsersList.indices) {
            val user = allUsersList[i]
            loadUserChatDataQuickly(user.userId, user.username, user.role, i)
        }
    }

    private fun loadUserChatDataQuickly(userId: String, username: String, role: String, userIndex: Int) {
        val chatId = getChatId(currentUserId, userId)
        val chatRef = FirebaseDatabase.getInstance(DATABASE_URL).reference
            .child("chats").child(chatId)
        
        // Use a timeout to prevent hanging
        chatRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                try {
                    var lastMessage = ""
                    var lastMessageTimestamp = 0L
                    var connectionType = "general"
                    var unreadCount = 0
                    
                    if (snapshot.exists()) {
                        lastMessage = snapshot.child("last_message").getValue(String::class.java) ?: ""
                        lastMessageTimestamp = snapshot.child("last_message_timestamp").getValue(Long::class.java) ?: 0L
                        connectionType = snapshot.child("connection_type").getValue(String::class.java) ?: "general"
                        
                        // Enhanced unread message counting with better logging
                        val messagesSnapshot = snapshot.child("messages")
                        var totalMessages = 0
                        var unreadMessages = 0
                        
                        for (messageSnapshot in messagesSnapshot.children) {
                            try {
                                totalMessages++
                                val message = messageSnapshot.getValue(com.example.meritxell.Message::class.java)
                                if (message != null && !message.read_by_receiver) {
                                    // Enhanced logic for admins to see ALL system messages as unread
                                    val shouldCount = if (currentUserRole == "admin") {
                                        // Admin should count: 
                                        // 1. Messages sent specifically to them
                                        // 2. ANY system message in admin chats (regardless of specific receiverId)
                                        message.receiverId == currentUserId || 
                                        (message.isSystemMessage && message.senderId == "system")
                                    } else {
                                        // Regular users should count: only messages sent to them (not system messages)
                                        message.receiverId == currentUserId && !message.isSystemMessage
                                    }
                                    
                                    Log.d(TAG, "📧 UNREAD MESSAGE CHECK 📧")
                                    Log.d(TAG, "   User: $username")
                                    Log.d(TAG, "   Current user role: $currentUserRole")
                                    Log.d(TAG, "   Current user ID: $currentUserId")
                                    Log.d(TAG, "   Message receiver ID: ${message.receiverId}")
                                    Log.d(TAG, "   Message sender ID: ${message.senderId}")
                                    Log.d(TAG, "   Is system message: ${message.isSystemMessage}")
                                    Log.d(TAG, "   Message read: ${message.read_by_receiver}")
                                    Log.d(TAG, "   Should count: $shouldCount")
                                    Log.d(TAG, "   Message: ${message.message.take(50)}")
                                    
                                    if (currentUserRole == "admin" && message.isSystemMessage) {
                                        Log.d(TAG, "   🎯 ADMIN SYSTEM MESSAGE DETECTED")
                                        Log.d(TAG, "   Admin ID matches: ${message.receiverId == currentUserId}")
                                        Log.d(TAG, "   System message flag: ${message.isSystemMessage}")
                                    }
                                    
                                    if (shouldCount) {
                                        unreadMessages++
                                        unreadCount++
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip problematic messages
                                Log.w(TAG, "Skipping message due to parsing error: ${e.message}")
                            }
                        }
                        
                        Log.d(TAG, "Unread count for $username: total=$totalMessages, unread=$unreadMessages, final=$unreadCount")
                    }
                    
                    // Update the user in place if still valid
                    if (userIndex < allUsersList.size && allUsersList[userIndex].userId == userId) {
                        val updatedUserInfo = UserInfo(
                            userId = userId,
                            username = username,
                            role = role,
                            lastMessage = lastMessage,
                            connectionType = connectionType,
                            lastMessageTimestamp = lastMessageTimestamp,
                            unreadCount = unreadCount,
                            isOnline = false
                        )
                        
                        allUsersList[userIndex] = updatedUserInfo
                        
                        // Refresh the display if this user is currently visible
                        runOnUiThread {
                            applyFilters()
                        }
                        
                        Log.d(TAG, "📊 FINAL CHAT DATA for ${username}:")
                        Log.d(TAG, "   Unread count: $unreadCount")
                        Log.d(TAG, "   Last message: '$lastMessage'")
                        Log.d(TAG, "   Current user role: $currentUserRole")
                        Log.d(TAG, "   Current user ID: $currentUserId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing chat data for user $userId: ${e.message}")
                }
            }
            
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "Chat data loading cancelled for user $userId: ${error.message}")
                // Don't fail, just leave the user with basic info
            }
        })
    }

    private fun getChatId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_$user2" else "${user2}_$user1"
    }

    private fun applyFilters() {
        Log.d(TAG, "Applying filters: currentFilter='$currentFilter', searchQuery='$searchQuery'")
        
        // Clear filtered list to prevent duplicates
        filteredUsersList.clear()
        
        var tempList = allUsersList.toList()
        
        // Apply filter by type - different logic for admin vs regular users
        tempList = when (currentFilter) {
            // Admin filters (admin can see all users)
            "All Users" -> tempList
            "Adoption Users" -> tempList.filter { it.connectionType.contains("adoption", true) }
            "Donation Users" -> tempList.filter { it.connectionType.contains("donation", true) }
            "Active Chats" -> tempList.filter { it.lastMessage.isNotEmpty() }
            "Admins" -> tempList.filter { it.role == "admin" }
            "Regular Users" -> tempList.filter { it.role == "user" }
            
            // Regular user filters (users only see admins)
            "All Admin Contacts" -> tempList // All items are already admins for regular users
            "Adoption Contacts" -> tempList.filter { it.connectionType.contains("adoption", true) }
            "Donation Contacts" -> tempList.filter { it.connectionType.contains("donation", true) }
            
            else -> tempList
        }
        
        // Apply search filter - enhanced for messaging requirements
        if (searchQuery.isNotEmpty()) {
            tempList = tempList.filter { user ->
                user.username.contains(searchQuery, ignoreCase = true) ||
                user.connectionType.contains(searchQuery, ignoreCase = true) ||
                user.role.contains(searchQuery, ignoreCase = true) ||
                user.lastMessage.contains(searchQuery, ignoreCase = true) ||
                // Search by process type
                searchQuery.lowercase().let { query ->
                    (query.contains("adoption") && user.connectionType.contains("adoption", true)) ||
                    (query.contains("donation") && user.connectionType.contains("donation", true)) ||
                    (query.contains("food") && user.connectionType.contains("food", true)) ||
                    (query.contains("toys") && user.connectionType.contains("toys", true)) ||
                    (query.contains("clothes") && user.connectionType.contains("clothes", true)) ||
                    (query.contains("education") && user.connectionType.contains("education", true))
                }
            }
        }
        
        // Remove any potential duplicates based on userId before adding to filtered list
        val uniqueUsers = tempList.distinctBy { it.userId }
        filteredUsersList.addAll(uniqueUsers)
        
        Log.d(TAG, "After filtering: ${filteredUsersList.size} unique users remain from ${allUsersList.size} total")
        
        displayUsers()
    }

    private fun displayUsers() {
        llUsersContainer.removeAllViews()
        
        Log.d(TAG, "Displaying ${filteredUsersList.size} users")
        
        if (filteredUsersList.isEmpty()) {
            val message = when {
                searchQuery.isNotEmpty() -> "No users found matching '$searchQuery'"
                currentFilter != "All Users" && currentFilter != "All Admin Contacts" -> "No users found for filter '$currentFilter'"
                else -> "No users available for messaging"
            }
            showNoUsers(message)
            return
        }

        tvNoUsers.visibility = View.GONE
        llUsersContainer.visibility = View.VISIBLE

        for (user in filteredUsersList.sortedBy { it.username }) {
            val userCard = createUserCard(user)
            llUsersContainer.addView(userCard)
        }
    }

    private fun createUserCard(user: UserInfo): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 12f
            cardElevation = if (user.unreadCount > 0) 6f else 4f
            setOnClickListener { openChat(user) }
            setOnLongClickListener { 
                showDeleteConversationDialog(user)
                true
            }
            // Highlight cards with unread messages
            setCardBackgroundColor(
                if (user.unreadCount > 0) ContextCompat.getColor(context, R.color.blue_50)
                else ContextCompat.getColor(context, android.R.color.white)
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // User avatar placeholder with online indicator
        val avatarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(56, 56).apply {
                marginEnd = 16
            }
            gravity = android.view.Gravity.CENTER
        }

        val ivAvatar = ImageView(this).apply {
            setImageResource(R.drawable.ic_profile)
            layoutParams = LinearLayout.LayoutParams(48, 48)
            background = ContextCompat.getDrawable(context, R.drawable.circle_background)
            backgroundTintList = ContextCompat.getColorStateList(context, 
                if (user.role == "admin") R.color.blue_500 else R.color.green)
            setPadding(8, 8, 8, 8)
        }
        avatarLayout.addView(ivAvatar)

        // Online status indicator (small dot)
        if (user.isOnline) {
            val onlineIndicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(12, 12).apply {
                    setMargins(-16, -8, 0, 0)
                }
                background = ContextCompat.getDrawable(context, R.drawable.circle_background)
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.green)
            }
            avatarLayout.addView(onlineIndicator)
        }

        layout.addView(avatarLayout)

        // User info layout
        val userInfoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // Username and timestamp row
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Username
        val tvUsername = TextView(this).apply {
            text = user.username
            textSize = 16f
            setTypeface(null, if (user.unreadCount > 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        headerLayout.addView(tvUsername)

        // Timestamp
        if (user.lastMessageTimestamp > 0) {
            val tvTimestamp = TextView(this).apply {
                text = formatInboxTimestamp(user.lastMessageTimestamp)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.grey_500))
                gravity = android.view.Gravity.END
            }
            headerLayout.addView(tvTimestamp)
        }

        userInfoLayout.addView(headerLayout)

        // Role badge and connection type
        val badgeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4
            }
        }

        val tvRole = TextView(this).apply {
            text = user.role.uppercase()
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            background = ContextCompat.getDrawable(context, R.drawable.rounded_badge_background)
            backgroundTintList = ContextCompat.getColorStateList(context, 
                if (user.role == "admin") R.color.blue_500 else R.color.green)
            setPadding(6, 3, 6, 3)
        }
        badgeLayout.addView(tvRole)

        // Connection type badge
        if (user.connectionType != "general") {
            val tvConnectionType = TextView(this).apply {
                text = user.connectionType.uppercase()
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                background = ContextCompat.getDrawable(context, R.drawable.rounded_badge_background)
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.orange)
                setPadding(6, 3, 6, 3)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 8
                }
            }
            badgeLayout.addView(tvConnectionType)
        }

        userInfoLayout.addView(badgeLayout)

        // Last message (if available)
        if (user.lastMessage.isNotEmpty()) {
            val tvLastMessage = TextView(this).apply {
                text = user.lastMessage
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.grey_600))
                setTypeface(null, if (user.unreadCount > 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6
                }
            }
            userInfoLayout.addView(tvLastMessage)
        }

        layout.addView(userInfoLayout)

        // Enhanced unread message count badge with admin-specific styling
        if (user.unreadCount > 0) {
            val unreadBadge = TextView(this).apply {
                text = if (user.unreadCount > 99) "99+" else user.unreadCount.toString()
                textSize = if (currentUserRole == "admin") 13f else 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                background = ContextCompat.getDrawable(context, R.drawable.circle_background)
                
                // Enhanced styling for admin unread badges
                backgroundTintList = if (currentUserRole == "admin") {
                    ContextCompat.getColorStateList(context, R.color.red)
                } else {
                    ContextCompat.getColorStateList(context, R.color.orange)
                }
                
                gravity = android.view.Gravity.CENTER
                minWidth = if (currentUserRole == "admin") 28 else 24
                minHeight = if (currentUserRole == "admin") 28 else 24
                elevation = if (currentUserRole == "admin") 6f else 4f
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 12
                }
                
                // Add pulsing animation for admin badges with high unread count
                if (currentUserRole == "admin" && user.unreadCount > 5) {
                    val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                    pulseAnimation.duration = 1000
                    pulseAnimation.repeatCount = android.view.animation.Animation.INFINITE
                    pulseAnimation.repeatMode = android.view.animation.Animation.REVERSE
                    startAnimation(pulseAnimation)
                }
            }
            layout.addView(unreadBadge)
            
            // Add additional visual indicator for admins with many unread messages
            if (currentUserRole == "admin" && user.unreadCount > 10) {
                val urgentIndicator = TextView(this).apply {
                    text = "🔥"
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 4
                    }
                }
                layout.addView(urgentIndicator)
            }
        }

        card.addView(layout)
        return card
    }

    private fun formatInboxTimestamp(timestamp: Long): String {
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
        
        // This week
        calendar.time = now
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = calendar.timeInMillis
        
        return when {
            timestamp >= todayStart -> {
                // Today - show only time
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
            }
            timestamp >= yesterdayStart -> {
                // Yesterday
                "Yesterday"
            }
            timestamp >= weekStart -> {
                // This week - show day name
                SimpleDateFormat("EEEE", Locale.getDefault()).format(messageDate)
            }
            else -> {
                // Older - show date
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(messageDate)
            }
        }
    }

    private fun showNoUsers(message: String) {
        Log.d(TAG, "Showing no users message: $message")
        tvNoUsers.visibility = View.VISIBLE
        llUsersContainer.visibility = View.GONE
        tvNoUsers.text = message
    }

    private fun openChat(user: UserInfo) {
        Log.d(TAG, "Opening chat with user: ${user.username} (${user.userId})")
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chatUserId", user.userId)
        intent.putExtra("chatUserName", user.username)
        startActivity(intent)
    }
    
    private fun setupRealtimeUnreadMonitoring() {
        Log.d(TAG, "Setting up real-time unread monitoring for user role: $currentUserRole")
        
        // Listen for new messages across all chats (for both admin and regular users)
        realtimeDb.child("chats").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // New chat created - refresh user list
                runOnUiThread {
                    forceRefreshUsers()
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Chat updated (new message, read status change, etc.)
                val chatId = snapshot.key ?: return
                
                // Check if this chat involves the current user (admin or regular user)
                val participantAdmin = snapshot.child("participant_admin").getValue(String::class.java)
                val participantUser = snapshot.child("participant_user").getValue(String::class.java)
                
                // For admins: monitor ALL admin chats (any admin can see all conversations)
                // For regular users: only monitor chats where they are the participant_user
                val shouldMonitor = if (currentUserRole == "admin") {
                    // Admin should monitor any chat that has an admin participant
                    participantAdmin != null && participantUser != null
                } else {
                    // Regular user should only monitor their own chats
                    participantUser == currentUserId
                }
                
                if (shouldMonitor) {
                    // Update unread count for this specific chat
                    updateUnreadCountForChat(chatId, snapshot)
                }
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Chat deleted - refresh user list
                runOnUiThread {
                    forceRefreshUsers()
                }
            }
            
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Real-time monitoring cancelled: ${error.message}")
            }
        })
    }
    
    private fun updateUnreadCountForChat(chatId: String, chatSnapshot: DataSnapshot) {
        try {
            val participantUser = chatSnapshot.child("participant_user").getValue(String::class.java) ?: return
            val participantAdmin = chatSnapshot.child("participant_admin").getValue(String::class.java) ?: return
            
            // Determine which user to update in our list based on current user role
            val targetUserId = if (currentUserRole == "admin") {
                // Admin sees regular users in their list, so update the participant_user
                participantUser
            } else {
                // Regular user sees admins in their list, so update the participant_admin
                participantAdmin
            }
            
            // Find the user in our list
            val userIndex = allUsersList.indexOfFirst { it.userId == targetUserId }
            if (userIndex == -1) {
                Log.d(TAG, "🔍 User $targetUserId not found in list for role $currentUserRole")
                return
            }
            
            val user = allUsersList[userIndex]
            
            // Count unread messages
            var unreadCount = 0
            val messagesSnapshot = chatSnapshot.child("messages")
            for (messageSnapshot in messagesSnapshot.children) {
                try {
                    val message = messageSnapshot.getValue(com.example.meritxell.Message::class.java)
                    if (message != null && !message.read_by_receiver) {
                        // Enhanced logic for admins to see ALL system messages as unread
                        val shouldCount = if (currentUserRole == "admin") {
                            // Admin should count: 
                            // 1. Messages sent specifically to them
                            // 2. ANY system message in admin chats (regardless of specific receiverId)
                            message.receiverId == currentUserId || 
                            (message.isSystemMessage && message.senderId == "system")
                        } else {
                            // Regular users should count: only messages sent to them (not system messages)
                            message.receiverId == currentUserId && !message.isSystemMessage
                        }
                        
                        Log.d(TAG, "🔄 UNREAD COUNT DEBUG 🔄")
                        Log.d(TAG, "   Current user role: $currentUserRole")
                        Log.d(TAG, "   Current user ID: $currentUserId")
                        Log.d(TAG, "   Message receiver ID: ${message.receiverId}")
                        Log.d(TAG, "   Message sender ID: ${message.senderId}")
                        Log.d(TAG, "   Is system message: ${message.isSystemMessage}")
                        Log.d(TAG, "   Message read: ${message.read_by_receiver}")
                        Log.d(TAG, "   Should count: $shouldCount")
                        Log.d(TAG, "   Message content: ${message.message.take(50)}")
                        
                        if (shouldCount) {
                            unreadCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing message in real-time update: ${e.message}")
                }
            }
            
            // Update the user's unread count
            val updatedUser = user.copy(unreadCount = unreadCount)
            allUsersList[userIndex] = updatedUser
            
            // Update the filtered list if this user is visible
            val filteredIndex = filteredUsersList.indexOfFirst { it.userId == targetUserId }
            if (filteredIndex != -1) {
                filteredUsersList[filteredIndex] = updatedUser
                
                // Refresh the display on the main thread
                runOnUiThread {
                    displayUsers()
                }
            }
            
            Log.d(TAG, "🔔 UNREAD COUNT UPDATE:")
            Log.d(TAG, "   Current user role: $currentUserRole")
            Log.d(TAG, "   Target user: ${user.username} (${targetUserId})")
            Log.d(TAG, "   Unread count: $unreadCount")
            Log.d(TAG, "   Chat ID: $chatId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in real-time unread count update: ${e.message}")
        }
    }

    private fun showNoAdminUsersFound() {
        Log.d(TAG, "Showing no admin users found message")
        tvNoUsers.visibility = View.VISIBLE
        llUsersContainer.visibility = View.GONE
        
        // Create a container for the no admin users state
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        
        val messageText = TextView(this).apply {
            text = "No admin contacts found.\n\n📱 Connect with an admin by:\n• Starting the adoption process\n• Making a donation\n\nThis will automatically create your admin chat connection."
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.grey_600))
        }
        container.addView(messageText)
        
        val startAdoptionButton = android.widget.Button(this).apply {
            text = "Start Adoption Process"
            setOnClickListener {
                // Navigate to adoption activity
                val intent = Intent(this@InboxActivity, UserAdoptActivity::class.java)
                startActivity(intent)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
        }
        container.addView(startAdoptionButton)
        
        val makeDonationButton = android.widget.Button(this).apply {
            text = "Make Donation"
            setOnClickListener {
                // Navigate to donation activity
                val intent = Intent(this@InboxActivity, UserDonationHubActivity::class.java)
                startActivity(intent)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }
        container.addView(makeDonationButton)
        
        val forceRefreshButton = android.widget.Button(this).apply {
            text = "Refresh"
            setOnClickListener {
                forceRefreshUsers()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }
        container.addView(forceRefreshButton)
        
        llUsersContainer.removeAllViews()
        llUsersContainer.addView(container)
        llUsersContainer.visibility = View.VISIBLE
        tvNoUsers.visibility = View.GONE
    }

    private fun forceRefreshUsers() {
        Log.d(TAG, "Force refreshing users")
        allUsersList.clear()
        filteredUsersList.clear()
        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        // Only refresh data if the user is authenticated and we don't already have users loaded
        if (::auth.isInitialized && auth.currentUser != null) {
            if (allUsersList.isEmpty()) {
                Log.d(TAG, "No users loaded, refreshing data")
                loadUsers()
            } else {
                Log.d(TAG, "Users already loaded, refreshing chat data to update unread counts")
                // Refresh chat data to ensure unread counts are up to date
                loadChatDataInBackground()
            }
        }
    }

    private fun checkNotificationPermissions() {
        // Don't show permission dialogs immediately if user already has permissions
        if (!notificationPermissionHelper.hasNotificationPermission()) {
            Log.d(TAG, "Notifications not enabled - will show setup option in UI")
        }
    }
    
    private fun showDeleteConversationDialog(user: UserInfo) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete your conversation with ${user.username}?\n\nThis action cannot be undone and will permanently remove all messages.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Delete") { _, _ ->
                deleteConversation(user)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // Style the dialog buttons
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(ContextCompat.getColor(this@InboxActivity, R.color.red))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(ContextCompat.getColor(this@InboxActivity, R.color.grey_600))
            }
        }
        
        dialog.show()
    }
    
    private fun deleteConversation(user: UserInfo) {
        val chatId = getChatId(currentUserId, user.userId)
        
        Log.d(TAG, "🗑️ Deleting conversation with ${user.username} (chatId: $chatId)")
        
        // Show progress by disabling the card and showing a toast
        Toast.makeText(this, "Deleting conversation...", Toast.LENGTH_SHORT).show()
        
        // Delete from Firebase Realtime Database
        val database = FirebaseDatabase.getInstance(DATABASE_URL)
        val chatRef = database.reference.child("chats").child(chatId)
        
        chatRef.removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Successfully deleted conversation with ${user.username}")
                Toast.makeText(this, "Conversation with ${user.username} deleted", Toast.LENGTH_SHORT).show()
                
                // Remove user from lists and refresh UI
                allUsersList.removeAll { it.userId == user.userId }
                filteredUsersList.removeAll { it.userId == user.userId }
                
                runOnUiThread {
                    displayUsers()
                }
                
                // If no users left, show appropriate message
                if (allUsersList.isEmpty()) {
                    runOnUiThread {
                        if (currentUserRole == "admin") {
                            showNoUsers("No conversations found.")
                        } else {
                            showNoAdminUsersFound()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to delete conversation with ${user.username}: ${e.message}")
                Toast.makeText(this, "Failed to delete conversation: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notificationPermissionHelper.handlePermissionResult(
            requestCode, 
            permissions, 
            grantResults,
            onPermissionGranted = {
                Toast.makeText(this, "✅ Great! You'll now receive message notifications.", Toast.LENGTH_LONG).show()
            },
            onPermissionDenied = {
                Toast.makeText(this, "⚠️ You won't receive notifications for new messages.", Toast.LENGTH_LONG).show()
            }
        )
    }
}