package com.example.meritxell

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout // Import for LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration // Import for ListenerRegistration

class Step6Activity : AppCompatActivity() {

    // UI elements for the single document
    private lateinit var uploadButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var attemptsTextView: TextView
    private lateinit var checkImageView: ImageView

    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var db: FirebaseFirestore

    private val MAX_SUBMISSION_ATTEMPTS = 3

    // Admin comment views
    private lateinit var adminCommentSection: LinearLayout
    private lateinit var adminCommentUserTextView: TextView

    // Listener for admin comments
    private var adminCommentListener: ListenerRegistration? = null

    private var currentStepNumber: Int = 6 // This activity is for Step 6
    private val documentId: String = "document_13_acceptance_receipt" // Unique ID for this document in Firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step6)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        // Initialize UI elements for the single document
        uploadButton = findViewById(R.id.uploadDocumentBtn13)
        statusTextView = findViewById(R.id.submissionStatusTextView13)
        attemptsTextView = findViewById(R.id.attemptsRemainingTextView13)
        checkImageView = findViewById(R.id.submittedCheckImageView13)

        // Initialize admin comment views
        adminCommentSection = findViewById(R.id.adminCommentSection)
        adminCommentUserTextView = findViewById(R.id.adminCommentUserTextView)

        // Set up listener for admin comments
        setupAdminCommentListener()

        uploadButton.setOnClickListener {
            Log.d("Step${currentStepNumber}Activity", "Upload Document button clicked. Launching file picker.")
            checkAndInitiateUpload()
        }

        val backButton: ImageView = findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            Log.d("Step${currentStepNumber}Activity", "Back button clicked. Finishing activity.")
            setResult(Activity.RESULT_CANCELED) // Indicate that the step wasn't successfully completed
            finish()
        }

        // Update UI on creation
        updateUIBasedOnSubmissionStatus()
    }

    // --- Admin Comment Listener and Lifecycle Management ---
    override fun onDestroy() {
        super.onDestroy()
        // Remove the listener when the activity is destroyed
        adminCommentListener?.remove()
        Log.d("Step${currentStepNumber}Activity", "Admin comment listener removed in onDestroy.")
    }

    // Function to set up the admin comment listener
    private fun setupAdminCommentListener() {
        val user = auth.currentUser
        if (user == null) {
            Log.e("Step${currentStepNumber}Activity", "User not logged in, cannot fetch admin comments.")
            adminCommentSection.visibility = View.GONE
            return
        }

        val userId = user.uid
        val stepKey = "step${currentStepNumber}" // This will be "step6" for this activity

        // Listen to the 'comments' subcollection under the user's 'adoption_progress' document
        adminCommentListener = db.collection("adoption_progress").document(userId)
            .collection("comments").document(stepKey)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Step${currentStepNumber}Activity", "Listen failed for admin comments: ${e.message}", e)
                    adminCommentSection.visibility = View.GONE // Hide section on error
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val comment = snapshot.getString("comment") // Get the "comment" field
                    if (!comment.isNullOrBlank()) {
                        adminCommentUserTextView.text = comment
                        adminCommentSection.visibility = View.VISIBLE
                        Log.d("Step${currentStepNumber}Activity", "Admin comment for $stepKey: $comment")
                    } else {
                        // If document exists but comment field is empty/null, hide the section
                        adminCommentUserTextView.text = "No comments for this step yet."
                        adminCommentSection.visibility = View.GONE
                        Log.d("Step${currentStepNumber}Activity", "No admin comment found for $stepKey.")
                    }
                } else {
                    // If the document doesn't exist, hide the section
                    adminCommentUserTextView.text = "No comments for this step yet."
                    adminCommentSection.visibility = View.GONE
                    Log.d("Step${currentStepNumber}Activity", "Admin comments document not found for user: $userId or step: $stepKey")
                }
            }
    }
    // --- End Admin Comment Listener and Lifecycle Management ---

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { fileUri ->
            Log.d("Step${currentStepNumber}Activity", "File selected: $fileUri")
            uploadDocument(fileUri)
        } ?: run {
            Log.d("Step${currentStepNumber}Activity", "No file selected by user.")
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUIBasedOnSubmissionStatus() {
        val user = auth.currentUser

        if (user == null) {
            uploadButton.isEnabled = false
            uploadButton.text = "Log In to Upload"
            statusTextView.text = "Please log in to submit."
            checkImageView.visibility = View.GONE
            attemptsTextView.visibility = View.GONE
            adminCommentSection.visibility = View.GONE // Hide if user not logged in
            Log.d("Step${currentStepNumber}Activity", "User not logged in. UI disabled.")
            return
        }

        val userId = user.uid
        db.collection("user_submissions_status") // Correct collection name
            .document(userId)
            .collection("step${currentStepNumber}_documents") // e.g., "step6_documents"
            .document(documentId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val submitted = if (documentSnapshot.exists()) {
                    documentSnapshot.getBoolean("submitted") ?: false
                } else {
                    false
                }
                val attempts = if (documentSnapshot.exists()) {
                    documentSnapshot.getLong("attempts")?.toInt() ?: 0
                } else {
                    0
                }

                val remainingAttempts = MAX_SUBMISSION_ATTEMPTS - attempts

                if (submitted) {
                    checkImageView.visibility = View.VISIBLE
                    statusTextView.text = "Submitted"
                    uploadButton.text = "Re-upload Document"

                    if (remainingAttempts <= 0) {
                        attemptsTextView.text = "No more submissions allowed."
                        uploadButton.isEnabled = false
                        Log.d("Step${currentStepNumber}Activity", "Document submitted, no attempts remaining.")
                    } else {
                        attemptsTextView.text = "You can submit ${remainingAttempts} more time(s)."
                        uploadButton.isEnabled = true
                        Log.d("Step${currentStepNumber}Activity", "Document submitted, $remainingAttempts attempts left.")
                    }
                    attemptsTextView.visibility = View.VISIBLE

                } else {
                    checkImageView.visibility = View.GONE
                    statusTextView.text = "Upload your document"
                    uploadButton.text = "Upload Document"
                    uploadButton.isEnabled = true

                    if (attempts >= MAX_SUBMISSION_ATTEMPTS) {
                        uploadButton.isEnabled = false
                        attemptsTextView.text = "No more submissions allowed."
                        attemptsTextView.visibility = View.VISIBLE
                        Log.d("Step${currentStepNumber}Activity", "Document not submitted, no attempts remaining.")
                    } else {
                        attemptsTextView.visibility = View.GONE
                        Log.d("Step${currentStepNumber}Activity", "Document not submitted, attempts left.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step${currentStepNumber}Activity", "Error fetching submission status for $documentId: ${e.message}", e)
                Toast.makeText(this, "Error loading status. Defaulting to upload.", Toast.LENGTH_SHORT).show()
                uploadButton.isEnabled = true
                statusTextView.text = "Upload document (status error)"
                checkImageView.visibility = View.GONE
                attemptsTextView.visibility = View.GONE
                adminCommentSection.visibility = View.GONE // Hide on error
            }
    }

    private fun checkAndInitiateUpload() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            Log.d("Step${currentStepNumber}Activity", "Upload initiation failed: User not logged in.")
            return
        }

        val userId = user.uid
        db.collection("user_submissions_status") // Correct collection name
            .document(userId)
            .collection("step${currentStepNumber}_documents")
            .document(documentId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val attempts: Int = if (documentSnapshot.exists()) {
                    documentSnapshot.getLong("attempts")?.toInt() ?: 0
                } else {
                    Log.d("Step${currentStepNumber}Activity", "Document status for $documentId does not exist yet. Initializing attempts to 0.")
                    0
                }

                if (attempts < MAX_SUBMISSION_ATTEMPTS) {
                    getContent.launch("*/*") // Launch file picker
                    Log.d("Step${currentStepNumber}Activity", "Initiating file picker. Attempts: $attempts")
                } else {
                    Toast.makeText(this, "You have reached the maximum submission limit for this document.", Toast.LENGTH_LONG).show()
                    updateUIBasedOnSubmissionStatus() // Ensure UI reflects max attempts
                    Log.d("Step${currentStepNumber}Activity", "Max submission attempts reached.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step${currentStepNumber}Activity", "Failed to check submission attempts: ${e.message}", e)
                Toast.makeText(this, "Failed to check submission attempts. Please try again.", Toast.LENGTH_SHORT).show()
                uploadButton.isEnabled = false
            }
    }

    private fun uploadDocument(uri: Uri) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in. Please authenticate.", Toast.LENGTH_SHORT).show()
            Log.e("Step${currentStepNumber}Activity", "Upload failed: User not authenticated.")
            return
        }

        val userId = user.uid
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                val username = userDoc.getString("username") ?: userId
                val fileName = "$username-${documentId}-${System.currentTimeMillis()}"

                // Storage path: stepX_uploads/{userId}/{fileName}
                val storageRef: StorageReference = storage.reference.child("step${currentStepNumber}_uploads/$userId/$fileName")
                Log.d("Step${currentStepNumber}Activity", "Starting upload for file: $fileName to path: ${storageRef.path}")

                val uploadTask: UploadTask = storageRef.putFile(uri)

                uploadTask.addOnSuccessListener { taskSnapshot ->
                    Log.d("Step${currentStepNumber}Activity", "Document uploaded to Storage successfully. Getting download URL...")
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val fileUrl = downloadUri.toString()
                        Log.d("Step${currentStepNumber}Activity", "Download URL obtained: $fileUrl")

                        val documentData = hashMapOf(
                            "fileUrl" to fileUrl,
                            "timestamp" to System.currentTimeMillis(),
                            "fileName" to fileName,
                            "status" to "pending_review" // Initial status
                        )

                        // Save document metadata to Firestore under 'adoption_progress/{userId}/stepX_uploads/{documentId}'
                        // This matches the common security rule pattern
                        db.collection("adoption_progress")
                            .document(userId)
                            .collection("step${currentStepNumber}_uploads") // This makes it a subcollection
                            .document(documentId) // This is the specific document within the subcollection (document_13_acceptance_receipt)
                            .set(documentData, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d("Step${currentStepNumber}Activity", "Document details saved to Firestore for user: $userId, document: $documentId")

                                // Now, update submission status in user_submissions_status collection
                                val submissionStatusRef = db.collection("user_submissions_status") // Correct collection name
                                    .document(userId)
                                    .collection("step${currentStepNumber}_documents") // e.g., "step6_documents"
                                    .document(documentId)

                                var finalNewAttempts = 0

                                // Use a transaction to safely increment attempts
                                db.runTransaction { transaction ->
                                    val snapshot = transaction.get(submissionStatusRef)
                                    val currentAttempts = snapshot.getLong("attempts")?.toInt() ?: 0
                                    finalNewAttempts = currentAttempts + 1

                                    transaction.set(submissionStatusRef, hashMapOf(
                                        "submitted" to true,
                                        "attempts" to finalNewAttempts,
                                        "lastSubmissionTimestamp" to System.currentTimeMillis(),
                                        "lastFileUrl" to fileUrl,
                                        "status" to "pending_review" // Update status here as well
                                    ))
                                    // Transactions should typically return a value or Unit
                                    Unit
                                }.addOnSuccessListener {
                                    Toast.makeText(this, "Document submitted! Admin will review.", Toast.LENGTH_SHORT).show()
                                    Log.d("Step${currentStepNumber}Activity", "Submission status updated successfully. Attempts: $finalNewAttempts")
                                    updateUIBasedOnSubmissionStatus() // Refresh UI

                                    val resultIntent = Intent().apply {
                                        putExtra("submittedDocumentId", documentId)
                                        putExtra("submissionStatus", "pending_review")
                                        putExtra("stepNumber", currentStepNumber)
                                    }
                                    setResult(Activity.RESULT_OK, resultIntent)

                                }.addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to update submission status: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e("Step${currentStepNumber}Activity", "Failed to update submission status: ${e.message}", e)
                                }

                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save document details to Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e("Step${currentStepNumber}Activity", "Failed to save document details to Firestore: ${e.message}", e)
                            }
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("Step${currentStepNumber}Activity", "Failed to get download URL: ${e.message}", e)
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Document upload to Storage failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Step${currentStepNumber}Activity", "Document upload to Storage failed: ${e.message}", e)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step${currentStepNumber}Activity", "Failed to fetch username: ${e.message}", e)
                Toast.makeText(this, "Failed to fetch username: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}