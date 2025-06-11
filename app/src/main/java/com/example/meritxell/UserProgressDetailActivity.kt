package com.example.meritxell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserProgressDetailActivity : AppCompatActivity() {

    private val TAG = "UserProgressDetailAct"
    private lateinit var db: FirebaseFirestore
    private var userId: String? = null

    private lateinit var detailUserName: TextView

    private fun getNextStep(currentStep: String): String? {
        return when (currentStep) {
            "step1" -> "step2"
            "step2" -> "step3"
            "step3" -> "step4"
            "step4" -> "step5"
            "step5" -> "step6"
            "step6" -> "step7"
            "step7" -> "step8"
            "step8" -> null
            else -> null
        }
    }

    private lateinit var btnBack: ImageView

    private lateinit var step1Header: LinearLayout
    private lateinit var step2Header: LinearLayout
    private lateinit var step3Header: LinearLayout
    private lateinit var step4Header: LinearLayout
    private lateinit var step5Header: LinearLayout
    private lateinit var step6Header: LinearLayout
    private lateinit var step7Header: LinearLayout
    private lateinit var step8Header: LinearLayout
    private lateinit var step9Header: LinearLayout
    private lateinit var step10Header: LinearLayout

    private lateinit var step9Title: TextView
    private lateinit var step10Title: TextView


    private lateinit var step1Status: TextView
    private lateinit var step2Status: TextView
    private lateinit var step3Status: TextView
    private lateinit var step4Status: TextView
    private lateinit var step5Status: TextView
    private lateinit var step6Status: TextView
    private lateinit var step7Status: TextView
    private lateinit var step8Status: TextView
    private lateinit var step9Status: TextView
    private lateinit var step10Status: TextView

    private lateinit var step1Comment: TextView
    private lateinit var step2Comment: TextView
    private lateinit var step3Comment: TextView
    private lateinit var step4Comment: TextView
    private lateinit var step5Comment: TextView
    private lateinit var step6Comment: TextView
    private lateinit var step7Comment: TextView
    private lateinit var step8Comment: TextView
    private lateinit var step9Comment: TextView
    private lateinit var step10Comment: TextView

    private lateinit var step1AdminCommentInput: EditText
    private lateinit var step1AdminCommentBtn: Button
    private lateinit var step2AdminCommentInput: EditText
    private lateinit var step2AdminCommentBtn: Button
    private lateinit var step3AdminCommentInput: EditText
    private lateinit var step3AdminCommentBtn: Button
    private lateinit var step4AdminCommentInput: EditText
    private lateinit var step4AdminCommentBtn: Button
    private lateinit var step5AdminCommentInput: EditText
    private lateinit var step5AdminCommentBtn: Button
    private lateinit var step6AdminCommentInput: EditText
    private lateinit var step6AdminCommentBtn: Button
    private lateinit var step7AdminCommentInput: EditText
    private lateinit var step7AdminCommentBtn: Button
    private lateinit var step8AdminCommentInput: EditText
    private lateinit var step8AdminCommentBtn: Button
    private lateinit var step9AdminCommentInput: EditText
    private lateinit var step9AdminCommentBtn: Button
    private lateinit var step10AdminCommentInput: EditText
    private lateinit var step10AdminCommentBtn: Button

    // Complete buttons for each step
    private lateinit var step1MarkCompleteBtn: Button
    private lateinit var varstep2MarkCompleteBtn: Button
    private lateinit var step3MarkCompleteBtn: Button
    private lateinit var step4MarkCompleteBtn: Button
    private lateinit var step5MarkCompleteBtn: Button
    private lateinit var step6MarkCompleteBtn: Button
    private lateinit var step7MarkCompleteBtn: Button
    private lateinit var step8MarkCompleteBtn: Button
    private lateinit var step9MarkCompleteBtn: Button
    private lateinit var step10MarkCompleteBtn: Button

    private lateinit var step9InProgressBtn: Button
    private lateinit var step10InProgressBtn: Button

    private lateinit var step1Content: LinearLayout
    private lateinit var step2Content: LinearLayout
    private lateinit var step3Content: LinearLayout
    private lateinit var step4Content: LinearLayout
    private lateinit var step5Content: LinearLayout
    private lateinit var step6Content: LinearLayout
    private lateinit var step7Content: LinearLayout
    private lateinit var step8Content: LinearLayout
    private lateinit var step9Content: LinearLayout
    private lateinit var step10Content: LinearLayout

    private lateinit var step2DocumentsContainer: LinearLayout
    private lateinit var step3DocumentsContainer: LinearLayout
    private lateinit var step4DocumentsContainer: LinearLayout
    private lateinit var step5DocumentsContainer: LinearLayout
    private lateinit var step6DocumentsContainer: LinearLayout
    private lateinit var step7DocumentsContainer: LinearLayout
    private lateinit var step8DocumentsContainer: LinearLayout
    private lateinit var step9DocumentsContainer: LinearLayout
    private lateinit var step10DocumentsContainer: LinearLayout

    private val dateFormatter = SimpleDateFormat("MMM dd,yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_progress_detail)


        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            onBackPressed()
        }

        db = FirebaseFirestore.getInstance()
        userId = intent.getStringExtra("userId")
        val username = intent.getStringExtra("username")

        supportActionBar?.title = null
        supportActionBar?.hide()

        detailUserName = findViewById(R.id.detailUserName)
        detailUserName.text = "User: $username"

        step1Header = findViewById(R.id.step1Header)
        step2Header = findViewById(R.id.step2Header)
        step3Header = findViewById(R.id.step3Header)
        step4Header = findViewById(R.id.step4Header)
        step5Header = findViewById(R.id.step5Header)
        step6Header = findViewById(R.id.step6Header)
        step7Header = findViewById(R.id.step7Header)
        step8Header = findViewById(R.id.step8Header)
        step9Header = findViewById(R.id.step9Header)
        step10Header = findViewById(R.id.step10Header)

        step9Title = findViewById(R.id.step9Title)
        step10Title = findViewById(R.id.step10Title)


        step1Status = findViewById(R.id.step1Status)
        step2Status = findViewById(R.id.step2Status)
        step3Status = findViewById(R.id.step3Status)
        step4Status = findViewById(R.id.step4Status)
        step5Status = findViewById(R.id.step5Status)
        step6Status = findViewById(R.id.step6Status)
        step7Status = findViewById(R.id.step7Status)
        step8Status = findViewById(R.id.step8Status)
        step9Status = findViewById(R.id.step9Status)
        step10Status = findViewById(R.id.step10Status)

        step1Comment = findViewById(R.id.step1Comment)
        step2Comment = findViewById(R.id.step2Comment)
        step3Comment = findViewById(R.id.step3Comment)
        step4Comment = findViewById(R.id.step4Comment)
        step5Comment = findViewById(R.id.step5Comment)
        step6Comment = findViewById(R.id.step6Comment)
        step7Comment = findViewById(R.id.step7Comment)
        step8Comment = findViewById(R.id.step8Comment)
        step9Comment = findViewById(R.id.step9Comment)
        step10Comment = findViewById(R.id.step10Comment)

        step1AdminCommentInput = findViewById(R.id.step1AdminCommentInput)
        step1AdminCommentBtn = findViewById(R.id.step1AdminCommentBtn)
        step2AdminCommentInput = findViewById(R.id.step2AdminCommentInput)
        step2AdminCommentBtn = findViewById(R.id.step2AdminCommentBtn)
        step3AdminCommentInput = findViewById(R.id.step3AdminCommentInput)
        step3AdminCommentBtn = findViewById(R.id.step3AdminCommentBtn)
        step4AdminCommentInput = findViewById(R.id.step4AdminCommentInput)
        step4AdminCommentBtn = findViewById(R.id.step4AdminCommentBtn)
        step5AdminCommentInput = findViewById(R.id.step5AdminCommentInput)
        step5AdminCommentBtn = findViewById(R.id.step5AdminCommentBtn)
        step6AdminCommentInput = findViewById(R.id.step6AdminCommentInput)
        step6AdminCommentBtn = findViewById(R.id.step6AdminCommentBtn)
        step7AdminCommentInput = findViewById(R.id.step7AdminCommentInput)
        step7AdminCommentBtn = findViewById(R.id.step7AdminCommentBtn)
        step8AdminCommentInput = findViewById(R.id.step8AdminCommentInput)
        step8AdminCommentBtn = findViewById(R.id.step8AdminCommentBtn)
        step9AdminCommentInput = findViewById(R.id.step9AdminCommentInput)
        step9AdminCommentBtn = findViewById(R.id.step9AdminCommentBtn)
        step10AdminCommentInput = findViewById(R.id.step10AdminCommentInput)
        step10AdminCommentBtn = findViewById(R.id.step10AdminCommentBtn)

        step1MarkCompleteBtn = findViewById(R.id.step1MarkCompleteBtn)
        varstep2MarkCompleteBtn = findViewById(R.id.step2MarkCompleteBtn)
        step3MarkCompleteBtn = findViewById(R.id.step3MarkCompleteBtn)
        step4MarkCompleteBtn = findViewById(R.id.step4MarkCompleteBtn)
        step5MarkCompleteBtn = findViewById(R.id.step5MarkCompleteBtn)
        step6MarkCompleteBtn = findViewById(R.id.step6MarkCompleteBtn)
        step7MarkCompleteBtn = findViewById(R.id.step7MarkCompleteBtn)
        step8MarkCompleteBtn = findViewById(R.id.step8MarkCompleteBtn)
        step9MarkCompleteBtn = findViewById(R.id.step9MarkCompleteBtn)
        step10MarkCompleteBtn = findViewById(R.id.step10MarkCompleteBtn)

        step9InProgressBtn = findViewById(R.id.step9InProgressBtn)
        step10InProgressBtn = findViewById(R.id.step10InProgressBtn)

        step1Content = findViewById(R.id.step1Content)
        step2Content = findViewById(R.id.step2Content)
        step3Content = findViewById(R.id.step3Content)
        step4Content = findViewById(R.id.step4Content)
        step5Content = findViewById(R.id.step5Content)
        step6Content = findViewById(R.id.step6Content)
        step7Content = findViewById(R.id.step7Content)
        step8Content = findViewById(R.id.step8Content)
        step9Content = findViewById(R.id.step9Content)
        step10Content = findViewById(R.id.step10Content)

        step2DocumentsContainer = findViewById(R.id.step2DocumentsContainer)
        step3DocumentsContainer = findViewById(R.id.step3DocumentsContainer)
        step4DocumentsContainer = findViewById(R.id.step4DocumentsContainer)
        step5DocumentsContainer = findViewById(R.id.step5DocumentsContainer)
        step6DocumentsContainer = findViewById(R.id.step6DocumentsContainer)
        step7DocumentsContainer = findViewById(R.id.step7DocumentsContainer)
        step8DocumentsContainer = findViewById(R.id.step8DocumentsContainer)
        step9DocumentsContainer = findViewById(R.id.step9DocumentsContainer)
        step10DocumentsContainer = findViewById(R.id.step10DocumentsContainer)

        setupHeaderClickListeners()
        setupActionButtons()
        fetchUserProgress()

    }

    private fun setupHeaderClickListeners() {
        step1Header.setOnClickListener { toggleVisibility(step1Content) }
        step2Header.setOnClickListener { toggleVisibility(step2Content) }
        step3Header.setOnClickListener { toggleVisibility(step3Content) }
        step4Header.setOnClickListener { toggleVisibility(step4Content) }
        step5Header.setOnClickListener { toggleVisibility(step5Content) }
        step6Header.setOnClickListener { toggleVisibility(step6Content) }
        step7Header.setOnClickListener { toggleVisibility(step7Content) }
        step8Header.setOnClickListener { toggleVisibility(step8Content) }
        step9Header.setOnClickListener { toggleVisibility(step9Content) }
        step10Header.setOnClickListener { toggleVisibility(step10Content) }
    }

    private fun toggleVisibility(layout: LinearLayout) {
        if (layout.visibility == View.GONE) {
            layout.visibility = View.VISIBLE
        } else {
            layout.visibility = View.GONE
        }
    }

    private fun fetchUserProgress() {
        if (userId == null) {
            Toast.makeText(this, "User ID not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("adoption_progress").document(userId!!)
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val document = task.result
                    if (document != null && document.exists()) {
                        Log.d(TAG, "Fetched adoption_progress for userId: $userId. Data: ${document.data}")

                        // Check for versioned structure first
                        val adoptionsMap = document.get("adoptions") as? Map<String, Any>
                        val adoptProgressMap: Map<String, String>? = if (adoptionsMap != null) {
                            // Versioned structure - get current adoption
                            val currentAdoptionNumber = document.getLong("currentAdoption")
                            val currentAdoptionId = currentAdoptionNumber?.toString()
                            if (currentAdoptionId != null) {
                                val currentAdoption = adoptionsMap[currentAdoptionId] as? Map<String, Any>
                                currentAdoption?.get("adopt_progress") as? Map<String, String>
                            } else {
                                null
                            }
                        } else {
                            // Old structure - direct adopt_progress
                            document.get("adopt_progress") as? Map<String, String>
                        }

                        if (adoptProgressMap != null) {
                            updateStepUI(adoptProgressMap["step1"], step1Status, step1Comment, "step1")
                            updateStepUI(adoptProgressMap["step2"], step2Status, step2Comment, "step2")
                            updateStepUI(adoptProgressMap["step3"], step3Status, step3Comment, "step3")
                            updateStepUI(adoptProgressMap["step4"], step4Status, step4Comment, "step4")
                            updateStepUI(adoptProgressMap["step5"], step5Status, step5Comment, "step5")
                            updateStepUI(adoptProgressMap["step6"], step6Status, step6Comment, "step6")
                            updateStepUI(adoptProgressMap["step7"], step7Status, step7Comment, "step7")
                            updateStepUI(adoptProgressMap["step8"], step8Status, step8Comment, "step8")
                            updateStepUI(adoptProgressMap["step9"], step9Status, step9Comment, "step9")
                            updateStepUI(adoptProgressMap["step10"], step10Status, step10Comment, "step10")
                        } else {
                            Log.w(TAG, "adopt_progress map not found or not a map for userId: $userId")
                            resetAllStatusesToLocked()
                        }

                        // Fetch and display dynamic documents for relevant steps
                        fetchStepDocuments(2, step2DocumentsContainer)
                        fetchStepDocuments(3, step3DocumentsContainer)
                        fetchStepDocuments(4, step4DocumentsContainer)
                        fetchStepDocuments(5, step5DocumentsContainer)
                        fetchStepDocuments(6, step6DocumentsContainer)
                        fetchStepDocuments(7, step7DocumentsContainer)
                        fetchStepDocuments(8, step8DocumentsContainer)
                        fetchStepDocuments(9, step9DocumentsContainer)
                        fetchStepDocuments(10, step10DocumentsContainer)

                    } else {
                        Log.d(TAG, "Document does not exist for userId: $userId")
                        resetAllStatusesToLocked()
                    }
                } else {
                    Log.e(TAG, "Error getting document: ", task.exception)
                    Toast.makeText(this, "Error loading user progress.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateStepUI(status: String?, statusTextView: TextView, commentTextView: TextView, stepName: String) {
        val markCompleteButton: Button? = getMarkCompleteButtonForStep(stepName)
        val markInProgressButton: Button? = getMarkInProgressButtonForStep(stepName)

        statusTextView.setTextColor(getColorForStatus(status))

        when (status) {
            "complete" -> {
                statusTextView.text = "Marked Complete"
                markCompleteButton?.visibility = View.GONE
                markInProgressButton?.visibility = View.GONE
                Log.d(TAG, "$stepName UI: Status 'complete'. Buttons hidden.")
            }
            "in_progress" -> {
                statusTextView.text = "In Progress"
                markCompleteButton?.visibility = View.VISIBLE
                markInProgressButton?.visibility = View.GONE
                Log.d(TAG, "$stepName UI: Status 'in_progress'. Mark Complete button shown, In Progress button hidden.")
            }
            else -> {
                statusTextView.text = "Locked"
                if (stepName == "step9" || stepName == "step10") {
                    markInProgressButton?.visibility = View.VISIBLE
                    markCompleteButton?.visibility = View.GONE
                    Log.d(TAG, "$stepName UI: Status 'Locked' (manual). Mark In Progress button shown, Mark Complete button hidden.")
                } else {
                    markCompleteButton?.visibility = View.VISIBLE
                    markInProgressButton?.visibility = View.GONE
                    Log.d(TAG, "$stepName UI: Status 'Locked' (auto). Mark Complete button shown, In Progress button hidden.")
                }
            }
        }

        userId?.let { uid ->
            db.collection("adoption_progress").document(uid)
                .collection("comments")
                .document(stepName)
                .get()
                .addOnSuccessListener { doc ->
                    val adminComment = doc.getString("comment")
                    if (!adminComment.isNullOrBlank()) {
                        commentTextView.text = "Admin Comment: $adminComment"
                        commentTextView.visibility = View.VISIBLE

                    } else {
                        commentTextView.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching admin comment for $stepName: ${e.message}")
                    commentTextView.visibility = View.GONE
                }
        }
    }

    private fun getColorForStatus(status: String?): Int {
        return when (status) {
            "approved" -> resources.getColor(R.color.green_500, null)
            "complete" -> resources.getColor(R.color.green_500, null)
            "in_progress" -> resources.getColor(R.color.blue_500, null)
            else -> resources.getColor(R.color.red_500, null)
        }
    }

    private fun resetAllStatusesToLocked() {
        val lockedColor = resources.getColor(R.color.red_500, null)
        val defaultText = "Locked"

        step1Status.text = defaultText
        step1Status.setTextColor(lockedColor)
        step2Status.text = defaultText
        step2Status.setTextColor(lockedColor)
        step3Status.text = defaultText
        step3Status.setTextColor(lockedColor)
        step4Status.text = defaultText
        step4Status.setTextColor(lockedColor)
        step5Status.text = defaultText
        step5Status.setTextColor(lockedColor)
        step6Status.text = defaultText
        step6Status.setTextColor(lockedColor)
        step7Status.text = defaultText
        step7Status.setTextColor(lockedColor)
        step8Status.text = defaultText
        step8Status.setTextColor(lockedColor)
        step9Status.text = defaultText
        step9Status.setTextColor(lockedColor)
        step10Status.text = defaultText
        step10Status.setTextColor(lockedColor)

        step1MarkCompleteBtn.visibility = View.VISIBLE
        varstep2MarkCompleteBtn.visibility = View.VISIBLE
        step3MarkCompleteBtn.visibility = View.VISIBLE
        step4MarkCompleteBtn.visibility = View.VISIBLE
        step5MarkCompleteBtn.visibility = View.VISIBLE
        step6MarkCompleteBtn.visibility = View.VISIBLE
        step7MarkCompleteBtn.visibility = View.VISIBLE
        step8MarkCompleteBtn.visibility = View.VISIBLE
        step9MarkCompleteBtn.visibility = View.GONE
        step9InProgressBtn.visibility = View.VISIBLE
        step10MarkCompleteBtn.visibility = View.GONE
        step10InProgressBtn.visibility = View.VISIBLE
    }

    private fun setupActionButtons() {
        step1MarkCompleteBtn.setOnClickListener { markStepComplete("step1") }
        varstep2MarkCompleteBtn.setOnClickListener { markStepComplete("step2") }
        step3MarkCompleteBtn.setOnClickListener { markStepComplete("step3") }
        step4MarkCompleteBtn.setOnClickListener { markStepComplete("step4") }
        step5MarkCompleteBtn.setOnClickListener { markStepComplete("step5") }
        step6MarkCompleteBtn.setOnClickListener { markStepComplete("step6") }
        step7MarkCompleteBtn.setOnClickListener { markStepComplete("step7") }
        step8MarkCompleteBtn.setOnClickListener { markStepComplete("step8") }
        step9MarkCompleteBtn.setOnClickListener { markStepComplete("step9") }
        step10MarkCompleteBtn.setOnClickListener { markStepComplete("step10") }

        step9InProgressBtn.setOnClickListener { markStepInProgress("step9") }
        step10InProgressBtn.setOnClickListener { markStepInProgress("step10") }

        step1AdminCommentBtn.setOnClickListener { saveAdminComment("step1", step1AdminCommentInput.text.toString()) }
        step2AdminCommentBtn.setOnClickListener { saveAdminComment("step2", step2AdminCommentInput.text.toString()) }
        step3AdminCommentBtn.setOnClickListener { saveAdminComment("step3", step3AdminCommentInput.text.toString()) }
        step4AdminCommentBtn.setOnClickListener { saveAdminComment("step4", step4AdminCommentInput.text.toString()) }
        step5AdminCommentBtn.setOnClickListener { saveAdminComment("step5", step5AdminCommentInput.text.toString()) }
        step6AdminCommentBtn.setOnClickListener { saveAdminComment("step6", step6AdminCommentInput.text.toString()) }
        step7AdminCommentBtn.setOnClickListener { saveAdminComment("step7", step7AdminCommentInput.text.toString()) }
        step8AdminCommentBtn.setOnClickListener { saveAdminComment("step8", step8AdminCommentInput.text.toString()) }
        step9AdminCommentBtn.setOnClickListener { saveAdminComment("step9", step9AdminCommentInput.text.toString()) }
        step10AdminCommentBtn.setOnClickListener { saveAdminComment("step10", step10AdminCommentInput.text.toString()) }
    }

    private fun markStepComplete(stepName: String) {
        if (userId == null) {
            Toast.makeText(this, "User ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val userProgressRef = db.collection("adoption_progress").document(userId!!)

        userProgressRef.get().addOnSuccessListener { documentSnapshot ->
            // Check for versioned structure first
            val adoptionsMap = documentSnapshot.get("adoptions") as? MutableMap<String, Any>

            if (adoptionsMap != null) {
                // Versioned structure - update current adoption
                val currentAdoptionNumber = documentSnapshot.getLong("currentAdoption")
                val currentAdoptionId = currentAdoptionNumber?.toString()
                if (currentAdoptionId != null) {
                    val currentAdoption = adoptionsMap[currentAdoptionId] as? MutableMap<String, Any>
                    if (currentAdoption != null) {
                        val adoptProgressMap = currentAdoption["adopt_progress"] as? MutableMap<String, String> ?: mutableMapOf()

                        adoptProgressMap[stepName] = "complete"

                        val nextStep = getNextStep(stepName)
                        if (nextStep != null && (adoptProgressMap[nextStep] == null || adoptProgressMap[nextStep] == "locked")) {
                            adoptProgressMap[nextStep] = "in_progress"
                            Log.d(TAG, "Next step ($nextStep) set to 'in_progress' for userId: $userId")
                        }

                        currentAdoption["adopt_progress"] = adoptProgressMap
                        adoptionsMap[currentAdoptionId] = currentAdoption

                        // Check if all 10 steps are now complete for automatic completion
                        var completedSteps = 0
                        for (i in 1..10) {
                            if (adoptProgressMap["step$i"] == "complete") {
                                completedSteps++
                            }
                        }

                        if (completedSteps == 10) {
                            Log.d(TAG, "🎉 All 10 steps completed by admin action! Processing automatic completion and reset for user: $userId")

                            // Trigger automatic completion and reset
                            handleAutomaticAdoptionCompletion(userProgressRef, adoptionsMap, currentAdoptionNumber, currentAdoption, stepName)
                        } else {
                            // Regular step completion update
                            userProgressRef.update("adoptions", adoptionsMap)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "$stepName marked as complete.", Toast.LENGTH_SHORT).show()
                                    Log.d(TAG, "$stepName marked as complete for userId: $userId (versioned)")

                                    // Send step completion message to user
                                    val stepNumber = stepName.removePrefix("step").toIntOrNull()
                                    if (stepNumber != null) {
                                        userProgressRef.get().addOnSuccessListener { doc ->
                                            val username = doc.getString("username") ?: "Unknown User"
                                            SystemMessageHelper.sendStepCompletedMessage(userId!!, username, stepNumber)
                                        }
                                    }

                                    fetchUserProgress()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error marking $stepName complete: ${e.message}", Toast.LENGTH_SHORT).show()
                                    Log.e(TAG, "Error marking $stepName complete (versioned): ${e.message}", e)
                                }
                        }
                    }
                }
            } else {
                // Old structure - direct adopt_progress
                val adoptProgressMap = documentSnapshot.get("adopt_progress") as? MutableMap<String, String> ?: mutableMapOf()

                adoptProgressMap[stepName] = "complete"

                val nextStep = getNextStep(stepName)
                if (nextStep != null && (adoptProgressMap[nextStep] == null || adoptProgressMap[nextStep] == "locked")) {
                    adoptProgressMap[nextStep] = "in_progress"
                    Log.d(TAG, "Next step ($nextStep) set to 'in_progress' for userId: $userId")
                }

                // Check if all 10 steps are now complete for automatic completion
                var completedSteps = 0
                for (i in 1..10) {
                    if (adoptProgressMap["step$i"] == "complete") {
                        completedSteps++
                    }
                }

                if (completedSteps == 10) {
                    Log.d(TAG, "🎉 All 10 steps completed by admin action! Converting to versioned structure and processing automatic completion for user: $userId")

                    // Convert to versioned structure and trigger automatic completion
                    handleOldStructureCompletion(userProgressRef, adoptProgressMap, documentSnapshot, stepName)
                } else {
                    // Regular step completion update
                    userProgressRef.update("adopt_progress", adoptProgressMap)
                        .addOnSuccessListener {
                            Toast.makeText(this, "$stepName marked as complete.", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "$stepName marked as complete for userId: $userId (old structure)")

                            // Send step completion message to user
                            val stepNumber = stepName.removePrefix("step").toIntOrNull()
                            if (stepNumber != null) {
                                userProgressRef.get().addOnSuccessListener { doc ->
                                    val username = doc.getString("username") ?: "Unknown User"
                                    SystemMessageHelper.sendStepCompletedMessage(userId!!, username, stepNumber)
                                }
                            }

                            fetchUserProgress()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error marking $stepName complete: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Error marking $stepName complete (old structure): ${e.message}", e)
                        }
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error fetching user progress to mark $stepName complete: ${e.message}", e)
            Toast.makeText(this, "Error fetching user progress to mark $stepName complete.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAutomaticAdoptionCompletion(
        userProgressRef: com.google.firebase.firestore.DocumentReference,
        adoptionsMap: MutableMap<String, Any>,
        currentAdoptionNumber: Long,
        currentAdoption: MutableMap<String, Any>,
        completedStepName: String
    ) {
        // Mark current adoption as completed for STATUS-BASED HISTORY
        currentAdoption["status"] = "completed"
        currentAdoption["completedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

        // Store completed adoption in adoptions history within the document
        val currentAdoptionKey = currentAdoptionNumber.toString()
        adoptionsMap[currentAdoptionKey] = currentAdoption

        // Create new adoption for re-adoption with CLEAN RESET
        val newAdoptionNumber = currentAdoptionNumber + 1
        val newAdoptionKey = newAdoptionNumber.toString()

        Log.d(TAG, "🔄 Creating new adoption instance #$newAdoptionNumber for re-adoption")

        val newAdoption = mutableMapOf<String, Any>(
            "adopt_progress" to mutableMapOf(
                "step1" to "complete",      // Step 1 starts complete for re-adoption
                "step2" to "in_progress",   // Step 2 is next
                "step3" to "locked",
                "step4" to "locked",
                "step5" to "locked",
                "step6" to "locked",
                "step7" to "locked",
                "step8" to "locked",
                "step9" to "locked",
                "step10" to "locked"
            ),
            "status" to "in_progress",
            "startedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "adoptionNumber" to newAdoptionNumber
        )

        // Add new adoption to adoptions map
        adoptionsMap[newAdoptionKey] = newAdoption

        // Update document with STATUS-BASED HISTORY structure
        val updateData = mutableMapOf<String, Any>(
            "adoptions" to adoptionsMap,
            "currentAdoption" to newAdoptionNumber,
            "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        userProgressRef.update(updateData)
            .addOnSuccessListener {
                Toast.makeText(this, "🎉 Adoption completed! $completedStepName marked complete and new adoption started automatically.", Toast.LENGTH_LONG).show()
                Log.d(TAG, "✅ Automatic adoption completion and reset successful for user: $userId")
                Log.d(TAG, "📊 Completed adoption #$currentAdoptionNumber stored in STATUS-BASED HISTORY")
                Log.d(TAG, "🆕 New adoption #$newAdoptionNumber created and ready for re-adoption")

                // Send messaging notifications to user and admin
                // Get username from the document data we already have
                userProgressRef.get().addOnSuccessListener { doc ->
                    val username = doc.getString("username") ?: "Unknown User"
                    SystemMessageHelper.sendNewAdoptionStartedMessage(userId!!, username, currentAdoptionNumber, newAdoptionNumber)

                    // Show system notification
                    EnhancedNotificationManager(this@UserProgressDetailActivity)
                        .showAdoptionNotification(
                            "Adoption Completed & New One Started!",
                            "Adoption #$currentAdoptionNumber completed! Adoption #$newAdoptionNumber is ready to begin.",
                            userId
                        )
                }

                fetchUserProgress() // Refresh UI to show new adoption
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error completing adoption automatically: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "❌ Error in automatic adoption completion: ${e.message}", e)
            }
    }

    private fun handleOldStructureCompletion(
        userProgressRef: com.google.firebase.firestore.DocumentReference,
        adoptProgressMap: MutableMap<String, String>,
        documentSnapshot: com.google.firebase.firestore.DocumentSnapshot,
        completedStepName: String
    ) {
        // Convert to versioned structure with completed first adoption for STATUS-BASED HISTORY
        val firstAdoption = mutableMapOf<String, Any>(
            "adopt_progress" to adoptProgressMap,
            "status" to "completed", // STATUS-BASED HISTORY marker
            "startedAt" to (documentSnapshot.get("timestamp") ?: com.google.firebase.firestore.FieldValue.serverTimestamp()),
            "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "adoptionNumber" to 1L
        )

        // Create new adoption for re-adoption with CLEAN RESET
        Log.d(TAG, "🔄 Creating second adoption instance for re-adoption")

        val secondAdoption = mutableMapOf<String, Any>(
            "adopt_progress" to mutableMapOf(
                "step1" to "complete",      // Step 1 starts complete for re-adoption
                "step2" to "in_progress",   // Step 2 is next
                "step3" to "locked",
                "step4" to "locked",
                "step5" to "locked",
                "step6" to "locked",
                "step7" to "locked",
                "step8" to "locked",
                "step9" to "locked",
                "step10" to "locked"
            ),
            "status" to "in_progress",
            "startedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "adoptionNumber" to 2L
        )

        // Create versioned structure with STATUS-BASED HISTORY
        val versionedData = mutableMapOf<String, Any>(
            "adoptions" to mutableMapOf(
                "1" to firstAdoption,
                "2" to secondAdoption
            ),
            "currentAdoption" to 2L,
            "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "username" to (documentSnapshot.getString("username") ?: "Unknown User"),
            "timestamp" to (documentSnapshot.get("timestamp") ?: com.google.firebase.firestore.FieldValue.serverTimestamp())
        )

        userProgressRef.set(versionedData)
            .addOnSuccessListener {
                Toast.makeText(this, "🎉 Adoption completed! $completedStepName marked complete and new adoption started automatically.", Toast.LENGTH_LONG).show()
                Log.d(TAG, "✅ Automatic adoption completion and conversion to versioned structure successful for user: $userId")
                Log.d(TAG, "📊 First adoption completed and stored in STATUS-BASED HISTORY")
                Log.d(TAG, "🆕 Second adoption created and ready for re-adoption")

                // Send messaging notifications to user and admin
                // Get username from the document data we already have
                userProgressRef.get().addOnSuccessListener { doc ->
                    val username = doc.getString("username") ?: "Unknown User"
                    SystemMessageHelper.sendNewAdoptionStartedMessage(userId!!, username, 1L, 2L)

                    // Show system notification
                    EnhancedNotificationManager(this@UserProgressDetailActivity)
                        .showAdoptionNotification(
                            "Adoption Completed & New One Started!",
                            "Adoption #1 completed! Adoption #2 is ready to begin.",
                            userId
                        )
                }

                fetchUserProgress() // Refresh UI to show new adoption
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error completing adoption automatically: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "❌ Error in automatic adoption completion and conversion: ${e.message}", e)
            }
    }

    private fun markStepInProgress(stepName: String) {
        if (userId == null) {
            Toast.makeText(this, "User ID not found.", Toast.LENGTH_SHORT).show()
            return
        }
        if (stepName !in listOf("step9", "step10")) {
            Toast.makeText(this, "This 'In Progress' action is only for steps 9 and 10.", Toast.LENGTH_SHORT).show()
            return
        }

        val userProgressRef = db.collection("adoption_progress").document(userId!!)

        userProgressRef.get().addOnSuccessListener { documentSnapshot ->
            // Check for versioned structure first
            val adoptionsMap = documentSnapshot.get("adoptions") as? MutableMap<String, Any>

            if (adoptionsMap != null) {
                // Versioned structure - update current adoption
                val currentAdoptionNumber = documentSnapshot.getLong("currentAdoption")
                val currentAdoptionId = currentAdoptionNumber?.toString()
                if (currentAdoptionId != null) {
                    val currentAdoption = adoptionsMap[currentAdoptionId] as? MutableMap<String, Any>
                    if (currentAdoption != null) {
                        val adoptProgressMap = currentAdoption["adopt_progress"] as? MutableMap<String, String> ?: mutableMapOf()

                        if (adoptProgressMap[stepName] != "complete") {
                            adoptProgressMap[stepName] = "in_progress"
                            Log.d(TAG, "$stepName set to 'in_progress' by admin for userId: $userId (versioned)")

                            currentAdoption["adopt_progress"] = adoptProgressMap
                            adoptionsMap[currentAdoptionId] = currentAdoption

                            userProgressRef.update("adoptions", adoptionsMap)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "$stepName marked as in progress.", Toast.LENGTH_SHORT).show()
                                    fetchUserProgress() // Refresh UI
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error marking $stepName in progress: ${e.message}", Toast.LENGTH_SHORT).show()
                                    Log.e(TAG, "Error marking $stepName in progress (versioned): ${e.message}", e)
                                }
                        } else {
                            Toast.makeText(this, "$stepName is already complete.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                // Old structure - direct adopt_progress
                val adoptProgressMap = documentSnapshot.get("adopt_progress") as? MutableMap<String, String> ?: mutableMapOf()

                if (adoptProgressMap[stepName] != "complete") {
                    adoptProgressMap[stepName] = "in_progress"
                    Log.d(TAG, "$stepName set to 'in_progress' by admin for userId: $userId (old structure)")

                    userProgressRef.update("adopt_progress", adoptProgressMap)
                        .addOnSuccessListener {
                            Toast.makeText(this, "$stepName marked as in progress.", Toast.LENGTH_SHORT).show()
                            fetchUserProgress() // Refresh UI
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error marking $stepName in progress: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Error marking $stepName in progress (old structure): ${e.message}", e)
                        }
                } else {
                    Toast.makeText(this, "$stepName is already complete.", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error fetching user progress to mark $stepName in progress: ${e.message}", e)
            Toast.makeText(this, "Error fetching user progress to mark $stepName in progress.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAdminComment(stepName: String, comment: String) {
        if (userId == null) {
            Toast.makeText(this, "User ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        if (comment.isBlank()) {
            Toast.makeText(this, "Comment cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val commentRef = db.collection("adoption_progress")
            .document(userId!!)
            .collection("comments")
            .document(stepName)

        commentRef.set(mapOf("comment" to comment))
            .addOnSuccessListener {
                Toast.makeText(this, "Comment saved for $stepName.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Admin comment saved for $stepName: $comment")

                getAdminCommentInputForStep(stepName)?.text?.clear()

                fetchUserProgress()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving comment: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error saving admin comment for $stepName: ${e.message}", e)
            }
    }

    private fun getAdminCommentInputForStep(stepName: String): EditText? {
        return when (stepName) {
            "step1" -> step1AdminCommentInput
            "step2" -> step2AdminCommentInput
            "step3" -> step3AdminCommentInput
            "step4" -> step4AdminCommentInput
            "step5" -> step5AdminCommentInput
            "step6" -> step6AdminCommentInput
            "step7" -> step7AdminCommentInput
            "step8" -> step8AdminCommentInput
            "step9" -> step9AdminCommentInput
            "step10" -> step10AdminCommentInput
            else -> null
        }
    }

    private fun fetchStepDocuments(stepNumber: Int, container: LinearLayout) {
        if (userId == null) return

        // Get current adoption number (only needed for steps 9-10)
        db.collection("adoption_progress").document(userId!!)
            .get()
            .addOnSuccessListener { adoptionDoc ->
                val currentAdoptionNumber = if (adoptionDoc.exists() && adoptionDoc.contains("adoptions")) {
                    // Versioned structure - get current adoption number
                    adoptionDoc.getLong("currentAdoption") ?: 1L
                } else {
                    // Old structure - assume adoption 1
                    1L
                }

                val documentsRef = db.collection("adoption_progress")
                    .document(userId!!)
                    .collection("step${stepNumber}_uploads")
                    .orderBy("timestamp", Query.Direction.DESCENDING)

                documentsRef.get()
                    .addOnSuccessListener { querySnapshot ->
                        container.removeAllViews() // Clear existing views before adding new ones

                        // For steps 2-8: Show all documents (they don't use adoptionNumber)
                        // For steps 9-10: Filter by adoption number (they use versioning)
                        val documentsToShow = if (stepNumber <= 8) {
                            // Steps 2-8: Show all documents regardless of adoption number
                            querySnapshot.documents
                        } else {
                            // Steps 9-10: Filter by current adoption number
                            querySnapshot.documents.filter { doc ->
                                val docAdoptionNumber = doc.getLong("adoptionNumber") ?: 1L
                                docAdoptionNumber == currentAdoptionNumber
                            }
                        }

                        if (documentsToShow.isEmpty()) {
                            val noDocsTextView = TextView(this).apply {
                                text = "No documents uploaded for this step."
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setPadding(0, dpToPx(4), 0, dpToPx(4))
                            }
                            container.addView(noDocsTextView)
                            return@addOnSuccessListener
                        }

                        documentsToShow.forEach { doc ->
                            val fileName = doc.getString("fileName")
                            val fileUrl = doc.getString("fileUrl")
                            val timestamp = doc.getLong("timestamp")
                            val docAdoptionNumber = doc.getLong("adoptionNumber")

                            val fileEntry = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(0, dpToPx(4), 0, dpToPx(4))
                            }

                            val fileNameTextView = TextView(this).apply {
                                text = fileName
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                setPadding(0, 0, 0, dpToPx(2))
                            }
                            fileEntry.addView(fileNameTextView)

                            val fileUrlTextView = TextView(this).apply {
                                text = "View Document"
                                setTextColor(resources.getColor(R.color.blue_500, null)) // Use a color that indicates a link
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setOnClickListener {
                                    fileUrl?.let { url ->
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open document. No app available.", Toast.LENGTH_SHORT).show()
                                            Log.e(TAG, "Error opening document URL: $url", e)
                                        }
                                    } ?: Toast.makeText(context, "Document URL is missing.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            fileEntry.addView(fileUrlTextView)

                            val timestampTextView = TextView(this).apply {
                                text = timestamp?.let { "Uploaded: ${dateFormatter.format(Date(it))}" } ?: "Uploaded: N/A"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                            }
                            fileEntry.addView(timestampTextView)

                            // Add adoption number info for debugging (can be removed later)
                            if (docAdoptionNumber != null) {
                                val adoptionInfoTextView = TextView(this).apply {
                                    text = "Adoption #$docAdoptionNumber"
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                                    setTextColor(resources.getColor(android.R.color.darker_gray, null))
                                }
                                fileEntry.addView(adoptionInfoTextView)
                            }

                            container.addView(fileEntry)
                        }

                        Log.d(TAG, "Displayed ${documentsToShow.size} documents for step $stepNumber")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error fetching documents for step $stepNumber: ${e.message}", e)
                        val errorTextView = TextView(this).apply {
                            text = "Error loading documents."
                            setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setPadding(0, dpToPx(4), 0, dpToPx(4))
                        }
                        container.removeAllViews()
                        container.addView(errorTextView)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching adoption progress for document filtering: ${e.message}", e)
                val errorTextView = TextView(this).apply {
                    text = "Error loading adoption progress."
                    setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(0, dpToPx(4), 0, dpToPx(4))
                }
                container.removeAllViews()
                container.addView(errorTextView)
            }
    }

    private fun getMarkCompleteButtonForStep(stepName: String): Button? {
        return when (stepName) {
            "step1" -> step1MarkCompleteBtn
            "step2" -> varstep2MarkCompleteBtn
            "step3" -> step3MarkCompleteBtn
            "step4" -> step4MarkCompleteBtn
            "step5" -> step5MarkCompleteBtn
            "step6" -> step6MarkCompleteBtn
            "step7" -> step7MarkCompleteBtn
            "step8" -> step8MarkCompleteBtn
            "step9" -> step9MarkCompleteBtn
            "step10" -> step10MarkCompleteBtn
            else -> null
        }
    }

    private fun getMarkInProgressButtonForStep(stepName: String): Button? {
        return when (stepName) {
            "step9" -> step9InProgressBtn
            "step10" -> step10InProgressBtn
            else -> null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}