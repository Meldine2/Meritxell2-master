package com.example.meritxell

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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

data class HistoryRecord(
    val id: String,
    val type: String, // "adoption", "donation", "matching", "appointment"
    val userId: String,
    val username: String,
    val title: String,
    val description: String,
    val status: String,
    val timestamp: Date,
    val completionDate: Date?,
    val details: Map<String, Any>
)

class HistoryActivity : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var currentUserId: String
    private var currentUserRole: String = "user"
    
    // UI Components
    private lateinit var btnBack: ImageView
    private lateinit var etSearchHistory: EditText
    private lateinit var spinnerHistoryFilter: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var llHistoryContainer: LinearLayout
    private lateinit var tvNoHistory: TextView
    private lateinit var btnRefresh: ImageView
    
    // Data
    private var allHistoryRecords = mutableListOf<HistoryRecord>()
    private var filteredHistoryRecords = mutableListOf<HistoryRecord>()
    private var searchQuery = ""
    private var selectedFilter = "All Records"
    private var pageType: String = "all" // Track what type of history this page should show
    
    companion object {
        private const val TAG = "HistoryActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        // Hide action bar
        supportActionBar?.hide()
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""
        
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        initViews()
        setupListeners()
        
        // Handle history type from intent (from HistoryFragment)
        val historyType = intent.getStringExtra("history_type")
        
        // Load user role first, then setup filters and load data
        loadCurrentUserRole(historyType)
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        etSearchHistory = findViewById(R.id.etSearchHistory)
        spinnerHistoryFilter = findViewById(R.id.spinnerHistoryFilter)
        progressBar = findViewById(R.id.progressBar)
        llHistoryContainer = findViewById(R.id.llHistoryContainer)
        tvNoHistory = findViewById(R.id.tvNoHistory)
        btnRefresh = findViewById(R.id.btnRefresh)
    }
    
    private fun setupFilters() {
        val filterOptions = when (pageType) {
            "adoption" -> {
                if (currentUserRole == "admin") {
                    arrayOf("All Adoptions", "Completed Adoptions")
                } else {
                    arrayOf("All My Adoptions", "My Completed Adoptions")
                }
            }
            "donation" -> {
                if (currentUserRole == "admin") {
                    arrayOf("All Donations", "Approved Donations", "Rejected Donations")
                } else {
                    arrayOf("All My Donations", "My Approved Donations", "My Rejected Donations")
                }
            }
            "matching" -> {
                if (currentUserRole == "admin") {
                    arrayOf("All Matches", "Successful Matches")
                } else {
                    arrayOf("All My Matches", "My Successful Matches")
                }
            }
            "appointment" -> {
                if (currentUserRole == "admin") {
                    arrayOf("All Appointments", "Completed Appointments", "Cancelled Appointments")
                } else {
                    arrayOf("All My Appointments", "My Completed Appointments", "My Cancelled Appointments")
                }
            }
            else -> {
                // General history page - show all types
                if (currentUserRole == "admin") {
                    arrayOf("All Records", "Completed Adoptions", "Donation History", "Successful Matches", "Completed Appointments")
                } else {
                    arrayOf("All Records", "My Completed Adoptions", "My Donation History", "My Successful Matches", "My Completed Appointments")
                }
            }
        }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerHistoryFilter.adapter = adapter
    }
    
    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        
        btnRefresh.setOnClickListener {
            loadHistoryRecords()
        }
        
        etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                applyFilters()
            }
        })
        
        spinnerHistoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFilter = parent?.getItemAtPosition(position) as String
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun loadCurrentUserRole(historyType: String?) {
        // Set the page type based on intent
        pageType = historyType ?: "all"
        
        firestore.collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                currentUserRole = document.getString("role") ?: "user"
                setupFilters()
                
                // Set initial filter based on history type from intent
                when (historyType) {
                    "adoption" -> {
                        val filterText = if (currentUserRole == "admin") "All Adoptions" else "All My Adoptions"
                        setFilterSelection(filterText)
                    }
                    "donation" -> {
                        val filterText = if (currentUserRole == "admin") "All Donations" else "All My Donations"
                        setFilterSelection(filterText)
                    }
                    "matching" -> {
                        val filterText = if (currentUserRole == "admin") "All Matches" else "All My Matches"
                        setFilterSelection(filterText)
                    }
                    "appointment" -> {
                        val filterText = if (currentUserRole == "admin") "All Appointments" else "All My Appointments"
                        setFilterSelection(filterText)
                    }
                    else -> selectedFilter = "All Records"
                }
                
                loadHistoryRecords()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading user role: ${e.message}")
                currentUserRole = "user"
                setupFilters()
                loadHistoryRecords()
            }
    }
    
    private fun setFilterSelection(filterText: String) {
        val adapter = spinnerHistoryFilter.adapter as? ArrayAdapter<String>
        if (adapter != null) {
            val position = adapter.getPosition(filterText)
            if (position >= 0) {
                spinnerHistoryFilter.setSelection(position)
                selectedFilter = filterText
            }
        }
    }
    
    private fun loadHistoryRecords() {
        progressBar.visibility = View.VISIBLE
        llHistoryContainer.removeAllViews()
        allHistoryRecords.clear()
        
        // Load only the specific type of records based on pageType
        when (pageType) {
            "adoption" -> loadCompletedAdoptions()
            "donation" -> loadApprovedDonations()
            "matching" -> loadSuccessfulMatches()
            "appointment" -> loadCompletedAppointments()
            else -> {
                // General history page - load all types
                loadCompletedAdoptions()
                loadApprovedDonations()
                loadSuccessfulMatches()
                loadCompletedAppointments()
            }
        }
    }
    
    private fun loadCompletedAdoptions() {
        Log.d(TAG, "🔍 Loading completed adoptions for pageType: $pageType, userRole: $currentUserRole, userId: $currentUserId")
        
        var completedQueries = 0
        val totalQueries = 1 // Only adoption_progress - using your existing setup
        
        // Load from adoption_progress (both old and new versioned structures)
        if (currentUserRole == "admin") {
            // Admin: Load all adoption progress documents
            firestore.collection("adoption_progress")
                .get()
                .addOnSuccessListener { snapshot ->
                    Log.d(TAG, "📋 Found ${snapshot.size()} adoption progress documents (admin)")
                    
                    for (document in snapshot.documents) {
                        val data = document.data ?: continue
                        val userId = document.id
                        val username = data["username"] as? String ?: "Unknown User"
                        
                        Log.d(TAG, "📄 Processing adoption progress document: ${document.id}")
                        
                        // Check if this is versioned structure
                        if (data.containsKey("adoptions")) {
                            // New versioned structure - process all completed adoptions
                            val adoptions = data["adoptions"] as? Map<String, Any> ?: mapOf()
                            
                            for ((adoptionKey, adoptionData) in adoptions) {
                                val adoption = adoptionData as? Map<String, Any> ?: continue
                                val status = adoption["status"] as? String ?: "in_progress"
                                
                                if (status == "completed") {
                                    val adoptProgressMap = adoption["adopt_progress"] as? Map<String, String>
                                    val completedAt = adoption["completedAt"] as? com.google.firebase.Timestamp
                                    val startedAt = adoption["startedAt"] as? com.google.firebase.Timestamp
                                    
                                    // Verify all 10 steps are complete
                                    var completedSteps = 0
                                    if (adoptProgressMap != null) {
                                        for (i in 1..10) {
                                            val stepStatus = adoptProgressMap["step$i"]
                                            if (stepStatus == "complete") {
                                                completedSteps++
                                            }
                                        }
                                    }
                                    
                                    if (completedSteps == 10) {
                                        val record = HistoryRecord(
                                            id = "${document.id}_adoption_$adoptionKey",
                                            type = "adoption",
                                            userId = userId,
                                            username = username,
                                            title = "Completed Adoption #$adoptionKey",
                                            description = "All 10 adoption steps completed successfully",
                                            status = "completed",
                                            timestamp = completedAt?.toDate() ?: startedAt?.toDate() ?: Date(),
                                            completionDate = completedAt?.toDate(),
                                            details = adoption
                                        )
                                        allHistoryRecords.add(record)
                                        Log.d(TAG, "✅ Added completed adoption record: ${record.title}")
                                    }
                                }
                            }
                        } else {
                            // Old structure - check if all 10 steps are complete
                            val adoptProgressMap = data["adopt_progress"] as? Map<String, String>
                            
                            var completedSteps = 0
                            if (adoptProgressMap != null) {
                                for (i in 1..10) {
                                    val stepStatus = adoptProgressMap["step$i"]
                                    if (stepStatus == "complete") {
                                        completedSteps++
                                    }
                                }
                            }
                            
                            Log.d(TAG, "   Completed steps: $completedSteps/10")
                            
                            // Only show fully completed adoptions (all 10 steps)
                            if (completedSteps == 10) {
                                val timestamp = data["timestamp"] as? com.google.firebase.Timestamp
                                
                                val record = HistoryRecord(
                                    id = document.id,
                                    type = "adoption",
                                    userId = userId,
                                    username = username,
                                    title = "Completed Adoption Process",
                                    description = "All 10 adoption steps completed successfully",
                                    status = "completed",
                                    timestamp = timestamp?.toDate() ?: Date(),
                                    completionDate = timestamp?.toDate(),
                                    details = data
                                )
                                allHistoryRecords.add(record)
                                Log.d(TAG, "✅ Added completed adoption record: ${record.title}")
                            }
                        }
                    }
                    
                    completedQueries++
                    if (completedQueries == totalQueries) {
                        Log.d(TAG, "📊 Total adoption records added: ${allHistoryRecords.filter { it.type == "adoption" }.size}")
                        applyFilters()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error loading adoption progress: ${e.message}")
                    completedQueries++
                    if (completedQueries == totalQueries) {
                        applyFilters()
                    }
                }
        } else {
            // User: Load their specific document (document ID = user ID)
            firestore.collection("adoption_progress")
                .document(currentUserId)
                .get()
                .addOnSuccessListener { snapshot ->
                    Log.d(TAG, "📋 Checking user adoption progress document: $currentUserId")
                    
                    if (snapshot.exists()) {
                        val data = snapshot.data ?: return@addOnSuccessListener
                        val username = data["username"] as? String ?: "Unknown User"
                        
                        Log.d(TAG, "📄 Processing user adoption progress document")
                        
                        // Check if this is versioned structure
                        if (data.containsKey("adoptions")) {
                            // New versioned structure - process all completed adoptions
                            val adoptions = data["adoptions"] as? Map<String, Any> ?: mapOf()
                            
                            for ((adoptionKey, adoptionData) in adoptions) {
                                val adoption = adoptionData as? Map<String, Any> ?: continue
                                val status = adoption["status"] as? String ?: "in_progress"
                                
                                if (status == "completed") {
                                    val adoptProgressMap = adoption["adopt_progress"] as? Map<String, String>
                                    val completedAt = adoption["completedAt"] as? com.google.firebase.Timestamp
                                    val startedAt = adoption["startedAt"] as? com.google.firebase.Timestamp
                                    
                                    // Verify all 10 steps are complete
                                    var completedSteps = 0
                                    if (adoptProgressMap != null) {
                                        for (i in 1..10) {
                                            val stepStatus = adoptProgressMap["step$i"]
                                            if (stepStatus == "complete") {
                                                completedSteps++
                                            }
                                        }
                                    }
                                    
                                    if (completedSteps == 10) {
                                        val record = HistoryRecord(
                                            id = "${currentUserId}_adoption_$adoptionKey",
                                            type = "adoption",
                                            userId = currentUserId,
                                            username = username,
                                            title = "My Completed Adoption #$adoptionKey",
                                            description = "All 10 adoption steps completed successfully",
                                            status = "completed",
                                            timestamp = completedAt?.toDate() ?: startedAt?.toDate() ?: Date(),
                                            completionDate = completedAt?.toDate(),
                                            details = adoption
                                        )
                                        allHistoryRecords.add(record)
                                        Log.d(TAG, "✅ Added user's completed adoption record: ${record.title}")
                                    }
                                }
                            }
                        } else {
                            // Old structure - check if all 10 steps are complete
                            val adoptProgressMap = data["adopt_progress"] as? Map<String, String>
                            
                            var completedSteps = 0
                            if (adoptProgressMap != null) {
                                for (i in 1..10) {
                                    val stepStatus = adoptProgressMap["step$i"]
                                    if (stepStatus == "complete") {
                                        completedSteps++
                                    }
                                }
                            }
                            
                            Log.d(TAG, "   User completed steps: $completedSteps/10")
                            
                            // Only show fully completed adoptions (all 10 steps)
                            if (completedSteps == 10) {
                                val timestamp = data["timestamp"] as? com.google.firebase.Timestamp
                                
                                val record = HistoryRecord(
                                    id = currentUserId,
                                    type = "adoption",
                                    userId = currentUserId,
                                    username = username,
                                    title = "My Completed Adoption Process",
                                    description = "All 10 adoption steps completed successfully",
                                    status = "completed",
                                    timestamp = timestamp?.toDate() ?: Date(),
                                    completionDate = timestamp?.toDate(),
                                    details = data
                                )
                                allHistoryRecords.add(record)
                                Log.d(TAG, "✅ Added user's completed adoption record: ${record.title}")
                            } else {
                                Log.d(TAG, "❌ User adoption not fully completed: $completedSteps/10 steps")
                            }
                        }
                    } else {
                        Log.d(TAG, "❌ No adoption progress document found for user: $currentUserId")
                    }
                    
                    completedQueries++
                    if (completedQueries == totalQueries) {
                        Log.d(TAG, "📊 Total adoption records added: ${allHistoryRecords.filter { it.type == "adoption" }.size}")
                        applyFilters()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error loading user adoption progress: ${e.message}")
                    completedQueries++
                    if (completedQueries == totalQueries) {
                        applyFilters()
                    }
                }
        }
    }
    
    private fun loadApprovedDonations() {
        val donationCollections = listOf("toysdonation", "clothesdonation", "fooddonation", "educationdonation", "donations")
        var completedCollections = 0
        
        for (collection in donationCollections) {
            // Simplify query to avoid index issues
            var query = firestore.collection(collection) as com.google.firebase.firestore.Query
            
            if (currentUserRole != "admin") {
                query = query.whereEqualTo("userId", currentUserId)
            }
            
            query.get()
                .addOnSuccessListener { snapshot ->
                    for (document in snapshot.documents) {
                        val data = document.data ?: continue
                        
                        // Filter by status in code to avoid index issues - ONLY show approved and rejected
                        val status = data["status"] as? String ?: "unknown"
                        if (!listOf("approved", "rejected").contains(status)) {
                            continue
                        }
                        val userId = data["userId"] as? String ?: ""
                        val username = data["username"] as? String ?: "Unknown User"
                        val donationType = when (collection) {
                            "toysdonation" -> "Toys"
                            "clothesdonation" -> "Clothes"
                            "fooddonation" -> "Food"
                            "educationdonation" -> "Education"
                            "donations" -> data["donationType"] as? String ?: "Money"
                            else -> collection.capitalize()
                        }
                        val amount = when (val amountField = data["amount"]) {
                            is String -> amountField
                            is Number -> amountField.toString()
                            else -> "Unknown"
                        }
                        
                        val timestampField = data["timestamp"]
                        val timestamp = when (timestampField) {
                            is com.google.firebase.Timestamp -> timestampField.toDate()
                            is String -> {
                                try {
                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(timestampField)
                                } catch (e: Exception) {
                                    Date()
                                }
                            }
                            else -> Date()
                        }
                        
                        val record = HistoryRecord(
                            id = document.id,
                            type = "donation",
                            userId = userId,
                            username = username,
                            title = "$donationType Donation - $status",
                            description = "Amount: $amount | Status: $status",
                            status = status,
                            timestamp = timestamp,
                            completionDate = timestamp,
                            details = data
                        )
                        allHistoryRecords.add(record)
                    }
                    
                    completedCollections++
                    if (completedCollections == donationCollections.size) {
                        applyFilters()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading donations from $collection: ${e.message}")
                    completedCollections++
                    if (completedCollections == donationCollections.size) {
                        applyFilters()
                    }
                }
        }
    }
    
    private fun loadSuccessfulMatches() {
        var query = firestore.collection("matching_preferences")
            .whereIn("status", listOf("matched", "accepted", "completed"))
        
        if (currentUserRole != "admin") {
            query = query.whereEqualTo("senderId", currentUserId)
        }
        
        query.get()
            .addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    val data = document.data ?: continue
                    val senderId = data["senderId"] as? String ?: ""
                    val senderUsername = data["senderUsername"] as? String ?: "Unknown User"
                    val receiverId = data["receiverId"] as? String ?: ""
                    val status = data["status"] as? String ?: "unknown"
                    
                    // Get matched child details
                    val matchedChild = data["matchedChildDetails"] as? Map<String, Any>
                    val childName = matchedChild?.get("name") as? String ?: "Unknown Child"
                    
                    val timestamp = when (val timestampField = data["actionTimestamp"]) {
                        is Number -> Date(timestampField.toLong())
                        else -> Date()
                    }
                    
                    val record = HistoryRecord(
                        id = document.id,
                        type = "matching",
                        userId = senderId,
                        username = senderUsername,
                        title = "Match with $childName - $status",
                        description = "Matched with child: $childName | Status: $status",
                        status = status,
                        timestamp = timestamp,
                        completionDate = timestamp,
                        details = data
                    )
                    allHistoryRecords.add(record)
                }
                applyFilters()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading successful matches: ${e.message}")
                applyFilters()
            }
    }
    
    private fun loadCompletedAppointments() {
        // Since appointments collection might not exist, handle gracefully
        var query = firestore.collection("appointments")
            .whereIn("status", listOf("completed", "cancelled"))
        
        if (currentUserRole != "admin") {
            query = query.whereEqualTo("userId", currentUserId)
        }
        
        query.get()
            .addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    val data = document.data ?: continue
                    val userId = data["userId"] as? String ?: ""
                    val username = data["username"] as? String ?: "Unknown User"
                    val status = data["status"] as? String ?: "unknown"
                    val appointmentType = data["appointmentType"] as? String ?: "Appointment"
                    // Fix: Look for 'date' and 'time' fields instead of 'appointmentDate'
                    val appointmentDate = data["date"] as? String ?: "Unknown Date"
                    val appointmentTime = data["time"] as? String ?: ""
                    
                    // Use completion timestamp or scheduled timestamp
                    val timestamp = (data["completedAt"] ?: data["cancelledAt"] ?: data["scheduledTimestamp"]) as? com.google.firebase.Timestamp
                    
                    val dateTimeDisplay = if (appointmentTime.isNotEmpty() && appointmentDate != "Unknown Date") {
                        "$appointmentDate at $appointmentTime"
                    } else {
                        appointmentDate
                    }
                    
                    val record = HistoryRecord(
                        id = document.id,
                        type = "appointment",
                        userId = userId,
                        username = username,
                        title = "$appointmentType - $status",
                        description = "Scheduled: $dateTimeDisplay | Status: $status",
                        status = status,
                        timestamp = timestamp?.toDate() ?: Date(),
                        completionDate = timestamp?.toDate(),
                        details = data
                    )
                    allHistoryRecords.add(record)
                }
                applyFilters()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading completed appointments: ${e.message}")
                // Appointments collection might not exist, which is fine
                applyFilters()
            }
    }
    
    private fun applyFilters() {
        filteredHistoryRecords.clear()
        
        for (record in allHistoryRecords) {
            // Apply type filter
            val matchesFilter = when (selectedFilter) {
                // General history page filters
                "All Records" -> true
                "Completed Adoptions", "My Completed Adoptions" -> record.type == "adoption"
                "Donation History", "My Donation History" -> record.type == "donation"
                "Successful Matches", "My Successful Matches" -> record.type == "matching"
                "Completed Appointments", "My Completed Appointments" -> record.type == "appointment"
                
                // Adoption page filters
                "All Adoptions", "All My Adoptions" -> record.type == "adoption"
                
                // Donation page filters
                "All Donations", "All My Donations" -> record.type == "donation"
                "Approved Donations", "My Approved Donations" -> record.type == "donation" && record.status == "approved"
                "Rejected Donations", "My Rejected Donations" -> record.type == "donation" && record.status == "rejected"
                
                // Matching page filters
                "All Matches", "All My Matches" -> record.type == "matching"
                
                // Appointment page filters
                "All Appointments", "All My Appointments" -> record.type == "appointment"
                "Cancelled Appointments", "My Cancelled Appointments" -> record.type == "appointment" && record.status == "cancelled"
                
                else -> true
            }
            
            // Apply search filter
            val matchesSearch = if (searchQuery.isEmpty()) {
                true
            } else {
                record.title.contains(searchQuery, ignoreCase = true) ||
                record.description.contains(searchQuery, ignoreCase = true) ||
                record.username.contains(searchQuery, ignoreCase = true) ||
                record.status.contains(searchQuery, ignoreCase = true)
            }
            
            if (matchesFilter && matchesSearch) {
                filteredHistoryRecords.add(record)
            }
        }
        
        // Sort by timestamp (newest first)
        filteredHistoryRecords.sortByDescending { it.timestamp }
        
        displayHistoryRecords()
    }
    
    private fun displayHistoryRecords() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            llHistoryContainer.removeAllViews()
            
            if (filteredHistoryRecords.isEmpty()) {
                tvNoHistory.visibility = View.VISIBLE
                tvNoHistory.text = if (searchQuery.isNotEmpty()) {
                    "No records found matching '$searchQuery'"
                } else {
                    "No completed records found"
                }
            } else {
                tvNoHistory.visibility = View.GONE
                
                for (record in filteredHistoryRecords) {
                    val cardView = createHistoryCardView(record)
                    llHistoryContainer.addView(cardView)
                }
            }
        }
    }
    
    private fun createHistoryCardView(record: HistoryRecord): CardView {
        val cardView = CardView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(16, 8, 16, 8)
        cardView.layoutParams = layoutParams
        cardView.cardElevation = 4f
        cardView.radius = 8f
        
        // Create content layout
        val contentLayout = LinearLayout(this)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.setPadding(16, 16, 16, 16)
        
        // Title
        val titleTextView = TextView(this)
        titleTextView.text = record.title
        titleTextView.textSize = 16f
        titleTextView.setTypeface(null, android.graphics.Typeface.BOLD)
        titleTextView.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        contentLayout.addView(titleTextView)
        
        // Description
        val descriptionTextView = TextView(this)
        descriptionTextView.text = record.description
        descriptionTextView.textSize = 14f
        descriptionTextView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        contentLayout.addView(descriptionTextView)
        
        // User info (for admin)
        if (currentUserRole == "admin") {
            val userTextView = TextView(this)
            userTextView.text = "User: ${record.username} (${record.userId})"
            userTextView.textSize = 12f
            userTextView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            contentLayout.addView(userTextView)
        }
        
        // Timestamp
        val timestampTextView = TextView(this)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        timestampTextView.text = "Completed: ${dateFormat.format(record.timestamp)}"
        timestampTextView.textSize = 12f
        timestampTextView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        contentLayout.addView(timestampTextView)
        
        // Status badge
        val statusTextView = TextView(this)
        statusTextView.text = record.status.uppercase()
        statusTextView.textSize = 10f
        statusTextView.setTypeface(null, android.graphics.Typeface.BOLD)
        statusTextView.setPadding(8, 4, 8, 4)
        
        // Set status color
        when (record.status.lowercase()) {
            "completed", "approved" -> {
                statusTextView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                statusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            "matched", "accepted" -> {
                statusTextView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                statusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            "cancelled", "rejected" -> {
                statusTextView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                statusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            else -> {
                statusTextView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                statusTextView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
        }
        
        contentLayout.addView(statusTextView)
        cardView.addView(contentLayout)
        
        return cardView
    }
} 