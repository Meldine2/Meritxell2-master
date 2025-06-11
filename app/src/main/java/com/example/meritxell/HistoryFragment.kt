package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class HistoryFragment : Fragment() {

    private lateinit var btnBack: View
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // UI Elements
    private lateinit var tvDonationCount: TextView
    private lateinit var tvAdoptionCount: TextView
    private lateinit var tvMatchingCount: TextView
    private lateinit var tvAppointmentCount: TextView
    private lateinit var cardDonation: CardView
    private lateinit var cardAdoption: CardView
    private lateinit var cardMatching: CardView
    private lateinit var cardAppointment: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        // Initialize btnBack here after the view has been inflated
        btnBack = view.findViewById(R.id.btnBack)

        // Set up back button functionality to go to UserHomeActivity
        btnBack.setOnClickListener {
            val intent = Intent(activity, UserHomeActivity::class.java)
            // Optional: Clear the back stack so the user can't go back to HistoryFragment
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            activity?.finish() // Finish the current activity if you don't want to keep it in the stack
        }

        initViews(view)
        setupClickListeners()
        loadHistoryCounts()

        return view
    }

    private fun initViews(view: View) {
        tvDonationCount = view.findViewById(R.id.tvDonationCount)
        tvAdoptionCount = view.findViewById(R.id.tvAdoptionCount)
        tvMatchingCount = view.findViewById(R.id.tvMatchingCount)
        tvAppointmentCount = view.findViewById(R.id.tvAppointmentCount)

        cardDonation = view.findViewById(R.id.cardDonationHistory)
        cardAdoption = view.findViewById(R.id.cardAdoptionHistory)
        cardMatching = view.findViewById(R.id.cardMatchingHistory)
        cardAppointment = view.findViewById(R.id.cardAppointmentHistory)
    }

    private fun setupClickListeners() {
        cardDonation.setOnClickListener {
            val intent = Intent(context, DonationHistoryActivity::class.java)
            startActivity(intent)
        }

        cardAdoption.setOnClickListener {
            val intent = Intent(context, HistoryActivity::class.java)
            intent.putExtra("history_type", "adoption")
            startActivity(intent)
        }

        cardMatching.setOnClickListener {
            val intent = Intent(context, HistoryActivity::class.java)
            intent.putExtra("history_type", "matching")
            startActivity(intent)
        }

        cardAppointment.setOnClickListener {
            val intent = Intent(context, HistoryActivity::class.java)
            intent.putExtra("history_type", "appointment")
            startActivity(intent)
        }
    }

    private fun loadHistoryCounts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("HistoryFragment", "User not authenticated")
            return
        }

        val currentUserId = currentUser.uid

        // Check user role first
        firestore.collection("users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { userDoc ->
                val userRole = userDoc.getString("role") ?: "user"

                if (userRole == "admin") {
                    // Admin can see all completed records
                    loadAdminHistoryCounts()
                } else {
                    // User can only see their own completed records
                    loadUserHistoryCounts(currentUserId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("HistoryFragment", "Error getting user role: ${e.message}")
                // Default to user view if error
                loadUserHistoryCounts(currentUserId)
            }
    }

    private fun loadUserHistoryCounts(userId: String) {
        // Count completed donations from all donation collections
        loadCompletedDonationCounts(userId, false)

        // Count completed adoption progress
        loadCompletedAdoptionCounts(userId, false)

        // Count completed matching records
        loadCompletedMatchingCounts(userId, false)

        // Count completed appointment records
        loadCompletedAppointmentCounts(userId, false)
    }

    private fun loadAdminHistoryCounts() {
        // Count all completed donations
        loadCompletedDonationCounts(null, true)

        // Count all completed adoption progress
        loadCompletedAdoptionCounts(null, true)

        // Count all completed matching records
        loadCompletedMatchingCounts(null, true)

        // Count all completed appointment records
        loadCompletedAppointmentCounts(null, true)
    }

    private fun loadCompletedDonationCounts(userId: String?, isAdmin: Boolean) {
        val donationCollections = listOf("toysdonation", "clothesdonation", "fooddonation", "educationdonation", "donations")
        var totalCompletedDonations = 0
        var completedCollections = 0

        for (collection in donationCollections) {
            // Simplify query to avoid index issues
            var query = firestore.collection(collection) as com.google.firebase.firestore.Query

            if (!isAdmin && userId != null) {
                query = query.whereEqualTo("userId", userId)
            }

            query.get()
                .addOnSuccessListener { snapshot ->
                    // Filter by status in code
                    val filteredCount = snapshot.documents.count { doc ->
                        val data = doc.data
                        val status = data?.get("status") as? String ?: "pending"
                        listOf("approved", "completed", "evidence_submitted").contains(status)
                    }
                    totalCompletedDonations += filteredCount
                    completedCollections++
                    if (completedCollections == donationCollections.size) {
                        activity?.runOnUiThread {
                            tvDonationCount.text = "$totalCompletedDonations completed"
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HistoryFragment", "Error loading completed $collection count: ${e.message}")
                    completedCollections++
                    if (completedCollections == donationCollections.size) {
                        activity?.runOnUiThread {
                            tvDonationCount.text = "$totalCompletedDonations completed"
                        }
                    }
                }
        }
    }

    private fun loadCompletedAdoptionCounts(userId: String?, isAdmin: Boolean) {
        if (isAdmin) {
            // For admin: count all completed adoptions from all users (including versioned structure)
            firestore.collection("adoption_progress")
                .get()
                .addOnSuccessListener { snapshot ->
                    var completedAdoptions = 0
                    for (document in snapshot.documents) {
                        val data = document.data

                        // Check if this is versioned structure
                        if (data?.containsKey("adoptions") == true) {
                            // New versioned structure - count all completed adoptions
                            val adoptions = data["adoptions"] as? Map<String, Any> ?: mapOf()

                            for ((_, adoptionData) in adoptions) {
                                val adoption = adoptionData as? Map<String, Any> ?: continue
                                val status = adoption["status"] as? String ?: "in_progress"

                                if (status == "completed") {
                                    val adoptProgressMap = adoption["adopt_progress"] as? Map<String, String>

                                    // Verify all 10 steps are complete
                                    var allStepsComplete = true
                                    if (adoptProgressMap != null) {
                                        for (i in 1..10) {
                                            val stepStatus = adoptProgressMap["step$i"]
                                            if (stepStatus != "complete") {
                                                allStepsComplete = false
                                                break
                                            }
                                        }
                                        if (allStepsComplete) {
                                            completedAdoptions++
                                        }
                                    }
                                }
                            }
                        } else {
                            // Old structure - check if all 10 steps are complete
                            val adoptProgressMap = data?.get("adopt_progress") as? Map<String, String>

                            var allStepsComplete = true
                            if (adoptProgressMap != null) {
                                for (i in 1..10) {
                                    val stepStatus = adoptProgressMap["step$i"]
                                    if (stepStatus != "complete") {
                                        allStepsComplete = false
                                        break
                                    }
                                }
                                if (allStepsComplete) {
                                    completedAdoptions++
                                }
                            }
                        }
                    }
                    activity?.runOnUiThread {
                        tvAdoptionCount.text = "$completedAdoptions completed"
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HistoryFragment", "Error loading admin adoption counts: ${e.message}")
                    activity?.runOnUiThread {
                        tvAdoptionCount.text = "0 completed"
                    }
                }
        } else if (userId != null) {
            // For user: check their own adoption progress completion (including versioned structure)
            firestore.collection("adoption_progress")
                .document(userId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val data = snapshot.data

                        // Check if this is versioned structure
                        if (data?.containsKey("adoptions") == true) {
                            // New versioned structure - count completed adoptions and show current progress
                            val adoptions = data["adoptions"] as? Map<String, Any> ?: mapOf()
                            val currentAdoptionNumber = data["currentAdoption"] as? Long ?: 1L
                            var completedAdoptions = 0
                            var currentProgress = "0/10 steps"

                            for ((adoptionKey, adoptionData) in adoptions) {
                                val adoption = adoptionData as? Map<String, Any> ?: continue
                                val status = adoption["status"] as? String ?: "in_progress"
                                val adoptProgressMap = adoption["adopt_progress"] as? Map<String, String>

                                if (status == "completed") {
                                    // Count completed adoptions
                                    var allStepsComplete = true
                                    if (adoptProgressMap != null) {
                                        for (i in 1..10) {
                                            val stepStatus = adoptProgressMap["step$i"]
                                            if (stepStatus != "complete") {
                                                allStepsComplete = false
                                                break
                                            }
                                        }
                                        if (allStepsComplete) {
                                            completedAdoptions++
                                        }
                                    }
                                } else if (adoptionKey == currentAdoptionNumber.toString()) {
                                    // Show current adoption progress
                                    var completedSteps = 0
                                    if (adoptProgressMap != null) {
                                        for (i in 1..10) {
                                            val stepStatus = adoptProgressMap["step$i"]
                                            if (stepStatus == "complete") {
                                                completedSteps++
                                            }
                                        }
                                    }
                                    currentProgress = "$completedSteps/10 steps"
                                }
                            }

                            activity?.runOnUiThread {
                                if (completedAdoptions > 0) {
                                    tvAdoptionCount.text = "$completedAdoptions completed + $currentProgress"
                                } else {
                                    tvAdoptionCount.text = currentProgress
                                }
                            }
                        } else {
                            // Old structure - check if all 10 steps are complete
                            val adoptProgressMap = data?.get("adopt_progress") as? Map<String, String>

                            var completedSteps = 0
                            if (adoptProgressMap != null) {
                                for (i in 1..10) {
                                    val stepStatus = adoptProgressMap["step$i"]
                                    if (stepStatus == "complete") {
                                        completedSteps++
                                    }
                                }
                            }

                            val isFullyCompleted = completedSteps == 10
                            activity?.runOnUiThread {
                                if (isFullyCompleted) {
                                    tvAdoptionCount.text = "1 completed"
                                } else {
                                    tvAdoptionCount.text = "$completedSteps/10 steps"
                                }
                            }
                        }
                    } else {
                        activity?.runOnUiThread {
                            tvAdoptionCount.text = "0/10 steps"
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HistoryFragment", "Error loading user adoption progress: ${e.message}")
                    activity?.runOnUiThread {
                        tvAdoptionCount.text = "0 records"
                    }
                }
        }
    }

    private fun loadCompletedMatchingCounts(userId: String?, isAdmin: Boolean) {
        var query = firestore.collection("matching_preferences")
            .whereIn("status", listOf("matched", "accepted", "completed"))

        if (!isAdmin && userId != null) {
            query = query.whereEqualTo("senderId", userId)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.size()
                activity?.runOnUiThread {
                    tvMatchingCount.text = "$count completed"
                }
            }
            .addOnFailureListener { e ->
                Log.e("HistoryFragment", "Error loading completed matching count: ${e.message}")
                activity?.runOnUiThread {
                    tvMatchingCount.text = "0 completed"
                }
            }
    }

    private fun loadCompletedAppointmentCounts(userId: String?, isAdmin: Boolean) {
        // Since appointments collection doesn't exist in your data, we'll check if it exists
        var query = firestore.collection("appointments")
            .whereIn("status", listOf("completed", "cancelled"))

        if (!isAdmin && userId != null) {
            query = query.whereEqualTo("userId", userId)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.size()
                activity?.runOnUiThread {
                    tvAppointmentCount.text = "$count completed"
                }
            }
            .addOnFailureListener { e ->
                Log.e("HistoryFragment", "Error loading completed appointment count: ${e.message}")
                // If appointments collection doesn't exist, show 0
                activity?.runOnUiThread {
                    tvAppointmentCount.text = "0 completed"
                }
            }
    }

    override fun onResume() {
        super.onResume()
        loadHistoryCounts() // Refresh counts when fragment becomes visible
    }
}