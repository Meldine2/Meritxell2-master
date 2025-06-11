package com.example.meritxell

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue

class ViewMatchingStatusActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: ImageView
    private lateinit var textViewRequestStatus: TextView
    private lateinit var textViewSubmittedPreferences: TextView
    private lateinit var textViewMatchDetailsLabel: TextView
    private lateinit var textViewMatchedChildDetailsStatus: TextView
    private lateinit var buttonAcceptMatch: Button
    private lateinit var buttonDeclineMatch: Button
    private lateinit var buttonCancelRequest: Button
    private lateinit var buttonRequestNewMatch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_matching_status)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.hide()

        // Initialize UI components
        backButton = findViewById(R.id.back_button)
        textViewRequestStatus = findViewById(R.id.text_view_request_status)
        textViewSubmittedPreferences = findViewById(R.id.text_view_submitted_preferences)
        textViewMatchDetailsLabel = findViewById(R.id.text_view_match_details_label)
        textViewMatchedChildDetailsStatus = findViewById(R.id.text_view_matched_child_details_status)
        buttonAcceptMatch = findViewById(R.id.button_accept_match)
        buttonDeclineMatch = findViewById(R.id.button_decline_match)
        buttonCancelRequest = findViewById(R.id.button_cancel_request)
        buttonRequestNewMatch = findViewById(R.id.button_request_new_match)

        setupListeners()
        loadMatchingStatus()
    }

    override fun onResume() {
        super.onResume()
        Log.d("ActivityLifecycle", "ViewMatchingStatusActivity: onResume called.")
        // It's good practice to reload status on resume in case of external changes
        loadMatchingStatus()
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            onBackPressed()
        }

        buttonAcceptMatch.setOnClickListener {
            updateMatchStatus("accepted")
        }

        buttonDeclineMatch.setOnClickListener {
            updateMatchStatus("declined")
        }

        buttonCancelRequest.setOnClickListener {
            updateMatchStatus("cancelled")
        }

        buttonRequestNewMatch.setOnClickListener {
            // When requesting a new match, we treat it as cancelling the current one
            // This will delete the current request and make the child available again
            updateMatchStatus("cancelled_and_new_request") // Use a specific status for this action
        }
    }

    private fun loadMatchingStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("FirebaseAuth", "User is NOT logged in. Redirecting to LoginActivity.")
            Toast.makeText(this, "Please log in to view your matching status.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, LoginActivity::class.java)
            Log.d("Navigation", "Navigating to LoginActivity from loadMatchingStatus (no user).")
            startActivity(intent)
            finish()
            return
        }

        val userId = currentUser.uid
        Log.d("FirebaseAuth", "User is logged in. UID: $userId")

        db.collection("matching_preferences")
            .whereEqualTo("senderId", userId)
            .orderBy("requestTimestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val latestRequest = querySnapshot.documents[0]
                    val status = latestRequest.getString("status") ?: "Unknown"
                    val genderPreference = latestRequest.getString("genderPreference") ?: "N/A"
                    val skinColorPreference = latestRequest.getString("skinColorPreference") ?: "N/A"
                    val characteristicsPreference = latestRequest.getString("characteristicsPreference") ?: "N/A"
                    val preferredSize = latestRequest.getString("preferredSize") ?: "N/A"
                    val preferredAge = latestRequest.getString("preferredAge") ?: "N/A"
                    val otherPreferences = latestRequest.getString("otherPreferences") ?: "None"
                    val matchedChildDetailsMap = latestRequest.get("matchedChildDetails") as? Map<String, Any>

                    textViewRequestStatus.text = status
                    textViewSubmittedPreferences.text = """
                        Gender: $genderPreference
                        Skin Color: $skinColorPreference
                        Characteristics: $characteristicsPreference
                        Preferred Size: $preferredSize
                        Preferred Age: $preferredAge
                        Other: $otherPreferences
                    """.trimIndent()

                    // Hide all action buttons by default, then show relevant ones
                    buttonAcceptMatch.visibility = View.GONE
                    buttonDeclineMatch.visibility = View.GONE
                    buttonCancelRequest.visibility = View.GONE
                    buttonRequestNewMatch.visibility = View.GONE
                    textViewMatchDetailsLabel.visibility = View.GONE
                    textViewMatchedChildDetailsStatus.visibility = View.GONE

                    // Remove any dynamically added schedule button before processing new status
                    val currentRequestSection = findViewById<LinearLayout>(R.id.current_request_section)
                    val existingScheduleButton = currentRequestSection.findViewWithTag<Button>("schedule_button")
                    existingScheduleButton?.let { currentRequestSection.removeView(it) }


                    when (status) {
                        "pending" -> {
                            // If a child has been matched by an admin, display their details and show accept/decline.
                            if (matchedChildDetailsMap != null) {
                                textViewMatchDetailsLabel.visibility = View.VISIBLE
                                textViewMatchedChildDetailsStatus.visibility = View.VISIBLE
                                val childName = matchedChildDetailsMap["name"]?.toString() ?: "N/A"
                                textViewMatchedChildDetailsStatus.text = """
                                    A match has been proposed for you with:
                                    Name: $childName
                                    Gender: ${matchedChildDetailsMap["gender"] ?: "N/A"}
                                    Skin Color: ${matchedChildDetailsMap["skinColor"] ?: "N/A"}
                                    Age: ${matchedChildDetailsMap["age"] ?: "N/A"}
                                    Characteristics: ${matchedChildDetailsMap["characteristics"] ?: "N/A"}
                                    Size: ${matchedChildDetailsMap["size"] ?: "N/A"}
                                """.trimIndent()
                                // The user (sender) can accept or decline this proposed match
                                buttonAcceptMatch.visibility = View.VISIBLE
                                buttonDeclineMatch.visibility = View.VISIBLE
                            } else {
                                // User (sender) has submitted preferences but no child has been proposed yet
                                textViewMatchDetailsLabel.visibility = View.GONE
                                textViewMatchedChildDetailsStatus.visibility = View.VISIBLE // Still show the text view
                                textViewMatchedChildDetailsStatus.text = "Awaiting admin review for your preferences and a potential child match."
                                buttonCancelRequest.visibility = View.VISIBLE // Allow cancellation of the initial request
                            }
                        }
                        "accepted" -> {
                            textViewMatchDetailsLabel.visibility = View.VISIBLE
                            textViewMatchedChildDetailsStatus.visibility = View.VISIBLE
                            if (matchedChildDetailsMap != null) {
                                val childName = matchedChildDetailsMap["name"]?.toString() ?: "N/A"
                                textViewMatchedChildDetailsStatus.text = "Congratulations! Your match with $childName has been accepted. Please proceed to schedule an appointment."
                            }
                            buttonRequestNewMatch.visibility = View.VISIBLE

                            // Dynamically add the "Schedule Appointment" button
                            val scheduleButton = Button(this).apply {
                                text = "Schedule Appointment"
                                setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6EC6FF"))) // meritxell_blue
                                setTextColor(Color.parseColor("#FFFFFF")) // white
                                setPadding(12, 12, 12, 12)
                                textSize = 16f
                                tag = "schedule_button" // Add a tag to easily find and remove it later
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    gravity = LinearLayout.HORIZONTAL
                                    topMargin = 16
                                }
                                setOnClickListener {
                                    val intent = Intent(this@ViewMatchingStatusActivity, ScheduleAppointmentActivity::class.java)
                                    intent.putExtra("matchId", latestRequest.id)
                                    intent.putExtra("user1Id", latestRequest.getString("senderId"))
                                    intent.putExtra("user2Id", latestRequest.getString("receiverId"))
                                    Log.d("Navigation", "Navigating to ScheduleAppointmentActivity from Schedule button click.")
                                    startActivity(intent)
                                }
                            }
                            findViewById<LinearLayout>(R.id.current_request_section).addView(scheduleButton)
                        }
                        "declined", "cancelled" -> {
                            // If the request was declined or cancelled, allow the user to request a new match.
                            textViewMatchDetailsLabel.visibility = View.GONE
                            textViewMatchedChildDetailsStatus.visibility = View.GONE
                            buttonRequestNewMatch.visibility = View.VISIBLE
                            textViewRequestStatus.text = "Your previous request was $status. Please submit new preferences."
                        }
                        else -> {
                            // For any other status or unknown status, allow requesting new match.
                            buttonRequestNewMatch.visibility = View.VISIBLE
                        }
                    }

                } else {
                    textViewRequestStatus.text = "No active matching request found."
                    textViewSubmittedPreferences.text = "Submit your preferences in the 'User Matching' section."
                    textViewMatchDetailsLabel.visibility = View.GONE
                    textViewMatchedChildDetailsStatus.visibility = View.GONE
                    buttonAcceptMatch.visibility = View.GONE
                    buttonDeclineMatch.visibility = View.GONE
                    buttonCancelRequest.visibility = View.GONE
                    buttonRequestNewMatch.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading matching status: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
                textViewRequestStatus.text = "Failed to load status."
                textViewSubmittedPreferences.text = "Please try again later."
            }
    }

    private fun updateMatchStatus(newStatus: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            Log.d("Navigation", "Navigating to LoginActivity from updateMatchStatus (no user).")
            startActivity(intent)
            finish()
            return
        }

        val userId = currentUser.uid

        db.collection("matching_preferences")
            .whereEqualTo("senderId", userId)
            .orderBy("requestTimestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val documentSnapshot = querySnapshot.documents[0]
                    val documentId = documentSnapshot.id
                    val senderId = documentSnapshot.getString("senderId")
                    val matchedChildId = documentSnapshot.getString("receiverId") // receiverId is the child's ID

                    Log.d("FirestoreUpdate", "Attempting to update match status to: $newStatus for document: $documentId")

                    if (newStatus == "declined" || newStatus == "cancelled" || newStatus == "cancelled_and_new_request") {
                        db.collection("matching_preferences").document(documentId)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Request $newStatus and removed.", Toast.LENGTH_SHORT).show()
                                senderId?.let { id ->
                                    db.collection("users").document(id)
                                        .update("pendingRequestsSentCount", FieldValue.increment(-1))
                                        .addOnSuccessListener { Log.d("FirestoreUpdate", "User pendingRequestsSentCount decremented.") }
                                        .addOnFailureListener { e -> Log.e("FirestoreUpdate", "Error decrementing sender count: ${e.message}", e)
                                            Toast.makeText(this, "Error decrementing sender count: ${e.message}", Toast.LENGTH_LONG).show() }
                                }
                                // When declined/cancelled/requesting new match, the child becomes 'Available' again
                                matchedChildId?.let { id ->
                                    Log.d("FirestoreUpdate", "Updating child $id status to Available")
                                    db.collection("children").document(id)
                                        .update("status", "Available")
                                        .addOnSuccessListener { Log.d("FirestoreUpdate", "Child status updated to Available successfully for $id") }
                                        .addOnFailureListener { e -> Log.e("FirestoreUpdate", "Failed to update child $id status to Available: ${e.message}", e)
                                            Toast.makeText(this, "Error updating child status: ${e.message}", Toast.LENGTH_LONG).show() }
                                }

                                if (newStatus == "cancelled_and_new_request") {
                                    // Navigate to UserMatchingActivity only after cancellation is complete
                                    val intent = Intent(this, UserMatchingActivity::class.java)
                                    Log.d("Navigation", "Navigating to UserMatchingActivity after 'cancelled_and_new_request'.")
                                    startActivity(intent)
                                    finish() // Finish this activity
                                } else {
                                    loadMatchingStatus() // Reload status for normal decline/cancel
                                }

                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error $newStatus request: ${e.message}", Toast.LENGTH_LONG).show()
                                Log.e("FirestoreUpdate", "Error deleting matching_preferences document: ${e.message}", e)
                            }
                    } else if (newStatus == "accepted") {
                        // REQUIREMENT: "After 3 days of user acceptance, matching records are deleted and moved to history"
                        // Add acceptedAt timestamp for 3-day timer tracking
                        db.collection("matching_preferences").document(documentId)
                            .update(
                                "status", newStatus,
                                "actionTimestamp", System.currentTimeMillis(),
                                "acceptedAt", com.google.firebase.Timestamp.now()
                            )
                            .addOnSuccessListener {
                                Toast.makeText(this, "Match status updated to: $newStatus", Toast.LENGTH_SHORT).show()
                                // ************ FIX: Child status now turns to 'Matched' when accepted ************
                                matchedChildId?.let { id ->
                                    Log.d("FirestoreUpdate", "Updating child $id status to Matched")
                                    db.collection("children").document(id)
                                        .update("status", "Matched")
                                        .addOnSuccessListener { Log.d("FirestoreUpdate", "Child status updated to Matched successfully for $id") }
                                        .addOnFailureListener { e -> Log.e("FirestoreUpdate", "Failed to update child $id status to Matched: ${e.message}", e)
                                            Toast.makeText(this, "Error updating child status to Matched: ${e.message}", Toast.LENGTH_LONG).show() }
                                }
                                loadMatchingStatus()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error updating status: ${e.message}", Toast.LENGTH_LONG).show()
                                Log.e("FirestoreUpdate", "Error updating matching_preferences status: ${e.message}", e)
                            }
                    }
                } else {
                    Toast.makeText(this, "No active request found to update.", Toast.LENGTH_SHORT).show()
                    Log.d("FirestoreUpdate", "No active request found for UID: $userId to update.")
                    // If no request is found when 'Request New Match' button tries to cancel,
                    // just navigate directly to UserMatchingActivity.
                    if (newStatus == "cancelled_and_new_request") {
                        val intent = Intent(this, UserMatchingActivity::class.java)
                        Log.d("Navigation", "Navigating to UserMatchingActivity directly as no active request found for new match.")
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching request to update: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("FirestoreUpdate", "Error fetching matching_preferences: ${e.message}", e)
            }
    }
}