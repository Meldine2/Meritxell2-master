package com.example.meritxell

import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class DonationHistoryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    
    private lateinit var btnBack: ImageView
    private lateinit var spinnerFilter: Spinner
    private lateinit var llDonationsContainer: LinearLayout
    private lateinit var tvNoDonations: TextView
    private lateinit var progressBar: ProgressBar
    
    private var currentUserRole: String = "user"
    private val donationHistory = mutableListOf<DonationHistoryRecord>()

    data class DonationHistoryRecord(
        val id: String,
        val userId: String,
        val username: String,
        val submissionDate: Date,
        val donationType: String,
        val amount: String,
        val status: String,
        val originalData: Map<String, Any>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donation_history)

        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Check if user is authenticated
        if (auth.currentUser == null) {
            finish()
            return
        }

        initViews()
        setupListeners()
        loadCurrentUserRole()
    }

    private fun initViews() {
        try {
            btnBack = findViewById(R.id.btnBack) ?: throw Exception("btnBack not found")
            spinnerFilter = findViewById(R.id.spinnerFilter) ?: throw Exception("spinnerFilter not found")
            llDonationsContainer = findViewById(R.id.llDonationsContainer) ?: throw Exception("llDonationsContainer not found")
            tvNoDonations = findViewById(R.id.tvNoDonations) ?: throw Exception("tvNoDonations not found")
            progressBar = findViewById(R.id.progressBar) ?: throw Exception("progressBar not found")
            
            setupFilterSpinner()
        } catch (e: Exception) {
            Log.e("DonationHistoryActivity", "Error initializing views: ${e.message}")
            Toast.makeText(this, "Error loading page: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupFilterSpinner() {
        val filterOptions = arrayOf("All Donations", "Money", "Toys", "Clothes", "Food", "Education")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter
        
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                applyFilter()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadCurrentUserRole() {
        val user = auth.currentUser ?: return
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentUserRole = doc.getString("role") ?: "user"
                } else {
                    currentUserRole = "user"
                }
                loadDonationHistory()
            }
            .addOnFailureListener { e ->
                Log.e("DonationHistoryActivity", "Error loading user role: ${e.message}")
                currentUserRole = "user"
                loadDonationHistory()
            }
    }

    private fun loadDonationHistory() {
        progressBar.visibility = View.VISIBLE
        donationHistory.clear()

        val currentUserId = auth.currentUser?.uid ?: return
        Log.d("DonationHistory", "Loading donations for user: $currentUserId, role: $currentUserRole")
        
        // Load completed/approved donations from donation collections
        val donationCollections = listOf("toysdonation", "clothesdonation", "fooddonation", "educationdonation", "donations")
        var completedCollections = 0

        for (collection in donationCollections) {
            // Start with a simpler query to avoid index issues
            var query = firestore.collection(collection) as com.google.firebase.firestore.Query
                
            // Apply role-based filtering first (this is the most selective filter)
            if (currentUserRole != "admin") {
                query = query.whereEqualTo("userId", currentUserId)
                Log.d("DonationHistory", "Filtering $collection for userId: $currentUserId")
            } else {
                Log.d("DonationHistory", "Admin access - loading all donations from $collection")
            }
            
            query.get()
                .addOnSuccessListener { snapshot ->
                    Log.d("DonationHistory", "Loading from $collection: found ${snapshot.size()} documents")
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        
                        // Filter by status in code to avoid index issues - ONLY show approved and rejected for history
                        val status = data["status"] as? String ?: "pending"
                        Log.d("DonationHistory", "Document ${doc.id} from $collection: status=$status")
                        // ONLY include approved and rejected for complete history - NO PENDING
                        if (!listOf("approved", "rejected").contains(status)) {
                            Log.d("DonationHistory", "Skipping document ${doc.id} with status: $status (not approved/rejected)")
                            continue
                        }
                        
                        // Get username from data or fetch it
                        val username = data["username"] as? String ?: "Unknown"
                        val timestamp = when {
                            data["timestamp"] is com.google.firebase.Timestamp -> 
                                (data["timestamp"] as com.google.firebase.Timestamp).toDate()
                            data["submittedAt"] is com.google.firebase.Timestamp ->
                                (data["submittedAt"] as com.google.firebase.Timestamp).toDate()
                            data["timestamp"] is String -> {
                                try {
                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(data["timestamp"] as String) ?: Date()
                                } catch (e: Exception) {
                                    Date()
                                }
                            }
                            else -> Date()
                        }
                        val amount = when (collection) {
                            "donations" -> {
                                when (val amountField = data["amount"]) {
                                    is String -> amountField
                                    is Number -> amountField.toString()
                                    else -> "0"
                                }
                            }
                            else -> "In-kind"
                        }
                        
                        val donationType = when (collection) {
                            "toysdonation" -> "Toys"
                            "clothesdonation" -> "Clothes"
                            "fooddonation" -> "Food"
                            "educationdonation" -> "Education"
                            "donations" -> {
                                // For donations collection, get the donationType from the document
                                data["donationType"] as? String ?: "Money"
                            }
                            else -> collection.capitalize()
                        }
                        
                        val record = DonationHistoryRecord(
                            id = doc.id,
                            userId = data["userId"] as? String ?: "",
                            username = username,
                            submissionDate = timestamp,
                            donationType = donationType,
                            amount = amount,
                            status = status,
                            originalData = data
                        )
                        
                        donationHistory.add(record)
                        Log.d("DonationHistory", "Added donation record: ID=${doc.id}, Type=${record.donationType}, Status=${record.status}, Amount=${record.amount}, UserId=${record.userId}, Collection=$collection")
                    }
                    
                    completedCollections++
                    if (completedCollections == donationCollections.size) {
                        Log.d("DonationHistory", "All collections loaded. Total donations found: ${donationHistory.size}")
                        progressBar.visibility = View.GONE
                        applyFilter()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("DonationHistoryActivity", "Error loading from $collection: ${e.message}")
                    completedCollections++
                    if (completedCollections == donationCollections.size) {
                        progressBar.visibility = View.GONE
                        applyFilter()
                    }
                }
        }
    }

    private fun applyFilter() {
        val selectedType = spinnerFilter.selectedItem.toString()
        
        val filteredHistory = if (selectedType == "All Donations") {
            donationHistory
        } else {
            donationHistory.filter { it.donationType.equals(selectedType, ignoreCase = true) }
        }
        
        displayHistory(filteredHistory)
    }

    private fun displayHistory(history: List<DonationHistoryRecord>) {
        Log.d("DonationHistory", "Displaying ${history.size} donation records")
        llDonationsContainer.removeAllViews()

        if (history.isEmpty()) {
            Log.d("DonationHistory", "No donations to display")
            tvNoDonations.visibility = View.VISIBLE
            llDonationsContainer.visibility = View.GONE
            tvNoDonations.text = "No donations found."
            return
        }

        tvNoDonations.visibility = View.GONE
        llDonationsContainer.visibility = View.VISIBLE

        for (record in history.sortedByDescending { it.submissionDate }) {
            val recordCard = createHistoryRecordCard(record)
            llDonationsContainer.addView(recordCard)
        }
    }

    private fun createHistoryRecordCard(record: DonationHistoryRecord): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 12f
            cardElevation = 4f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Header with donation type and date
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvType = TextView(this).apply {
            text = record.donationType
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(12, 6, 12, 6)
            background = ContextCompat.getDrawable(context, R.drawable.rounded_badge_background)
        }

        val tvDate = TextView(this).apply {
            text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(record.submissionDate)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.grey_500))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END
            }
            gravity = android.view.Gravity.END
        }

        headerLayout.addView(tvType)
        headerLayout.addView(tvDate)
        layout.addView(headerLayout)

        // User info (for admin)
        if (currentUserRole == "admin") {
            val tvUser = TextView(this).apply {
                text = "👤 ${record.username}"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                setPadding(0, 8, 0, 4)
            }
            layout.addView(tvUser)
        }

        // Amount info
        val tvAmount = TextView(this).apply {
            text = if (record.amount == "In-kind") "In-kind donation" else "Amount: ${record.amount}"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        layout.addView(tvAmount)

        // Status badge
        val tvStatus = TextView(this).apply {
            text = record.status.uppercase()
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            background = ContextCompat.getDrawable(context, R.drawable.rounded_badge_background)
            backgroundTintList = ContextCompat.getColorStateList(context, getStatusColor(record.status))
            setPadding(8, 4, 8, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 0)
            }
        }
        layout.addView(tvStatus)

        card.addView(layout)
        return card
    }

    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "approved", "completed" -> R.color.green
            "pending", "in_progress" -> R.color.orange
            "rejected", "cancelled" -> R.color.red
            else -> R.color.grey_500
        }
    }
} 