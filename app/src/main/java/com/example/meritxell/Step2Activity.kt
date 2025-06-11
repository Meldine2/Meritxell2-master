package com.example.meritxell

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.ListenerRegistration

class Step2Activity : AppCompatActivity() {

    private lateinit var uploadDocumentBtn: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var db: FirebaseFirestore
    private lateinit var qrImageView: ImageView
    private lateinit var linkTextView: TextView

    private lateinit var submissionStatusTextView: TextView
    private lateinit var attemptsRemainingTextView: TextView
    private lateinit var submittedCheckImageView: ImageView

    // Admin comment views
    private lateinit var adminCommentSection: LinearLayout
    private lateinit var adminCommentUserTextView: TextView

    private val MAX_SUBMISSION_ATTEMPTS = 3

    private var currentStepNumber: Int = 2 // This activity is for Step 2

    // Listener for admin comments
    private var adminCommentListener: ListenerRegistration? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Log.d(TAG, "File selected: $uri") // Changed to TAG for consistency
            uploadDocument(it)
        } ?: run {
            Log.d(TAG, "No file selected by user.") // Changed to TAG
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "Step2Activity" // Define TAG for consistency
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step2)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        currentStepNumber = intent.getIntExtra("stepNumber", 2)

        uploadDocumentBtn = findViewById(R.id.uploadDocumentBtn)
        qrImageView = findViewById(R.id.qrCodeImageView)
        linkTextView = findViewById(R.id.formLinkTextView)

        submittedCheckImageView = findViewById(R.id.submittedCheckImageView)
        submissionStatusTextView = findViewById(R.id.submissionStatusTextView)
        attemptsRemainingTextView = findViewById(R.id.attemptsRemainingTextView)

        // Initialize admin comment views
        adminCommentSection = findViewById(R.id.adminCommentSection)
        adminCommentUserTextView = findViewById(R.id.adminCommentUserTextView)

        updateUIBasedOnSubmissionStatus()

        uploadDocumentBtn.setOnClickListener {
            Log.d(TAG, "Upload Document button clicked. Launching file picker.") // Changed to TAG
            checkAndInitiateUpload()
        }

        val backButton: ImageView = findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked. Finishing activity.") // Changed to TAG
            setResult(Activity.RESULT_CANCELED) // Indicate that the step wasn't successfully completed
            finish()
        }

        // Set up listener for admin comments
        setupAdminCommentListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the listener when the activity is destroyed
        adminCommentListener?.remove()
    }

    // Function to set up the admin comment listener
    private fun setupAdminCommentListener() {
        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "User not logged in, cannot fetch admin comments.") // Changed to TAG
            adminCommentSection.visibility = View.GONE
            return
        }

        val userId = user.uid
        val stepKey = "step${currentStepNumber}" // e.g., "step2"

        // *** CRITICAL CHANGE HERE ***
        // Now listening to the subcollection "comments" under the user's adoption_progress document
        adminCommentListener = db.collection("adoption_progress").document(userId)
            .collection("comments").document(stepKey)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed for admin comments: ${e.message}", e) // Changed to TAG
                    adminCommentSection.visibility = View.GONE // Hide section on error
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val comment = snapshot.getString("comment") // Get the "comment" field from the document
                    if (!comment.isNullOrBlank()) {
                        adminCommentUserTextView.text = comment
                        adminCommentSection.visibility = View.VISIBLE
                        Log.d(TAG, "Admin comment for $stepKey: $comment") // Changed to TAG
                    } else {
                        adminCommentUserTextView.text = "No comments for this step yet."
                        adminCommentSection.visibility = View.GONE // Hide if no comment
                        Log.d(TAG, "No admin comment found for $stepKey.") // Changed to TAG
                    }
                } else {
                    adminCommentUserTextView.text = "No comments for this step yet."
                    adminCommentSection.visibility = View.GONE // Hide if document doesn't exist
                    Log.d(TAG, "Admin comments document not found for user: $userId or step: $stepKey") // Changed to TAG
                }
            }
    }


    private fun updateUIBasedOnSubmissionStatus() {
        val user = auth.currentUser
        if (user == null) {
            uploadDocumentBtn.isEnabled = false
            uploadDocumentBtn.text = "Log In to Upload"
            submissionStatusTextView.text = "Please log in to submit."
            submittedCheckImageView.visibility = View.GONE
            attemptsRemainingTextView.visibility = View.GONE
            adminCommentSection.visibility = View.GONE // Hide if user not logged in
            return
        }

        val userId = user.uid
        db.collection("user_submissions_status")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val submitted = document.getBoolean("submitted") ?: false
                val attempts = document.getLong("attempts")?.toInt() ?: 0

                val remainingAttempts = MAX_SUBMISSION_ATTEMPTS - attempts

                if (submitted) {
                    submittedCheckImageView.visibility = View.VISIBLE
                    submissionStatusTextView.text = "Submitted"
                    uploadDocumentBtn.text = "Re-upload Document"

                    if (remainingAttempts <= 0) {
                        attemptsRemainingTextView.text = "No more submissions allowed."
                        uploadDocumentBtn.isEnabled = false
                    } else {
                        attemptsRemainingTextView.text = "You can submit ${remainingAttempts} more time(s)."
                        uploadDocumentBtn.isEnabled = true
                    }
                    attemptsRemainingTextView.visibility = View.VISIBLE

                } else {
                    submittedCheckImageView.visibility = View.GONE
                    submissionStatusTextView.text = "Upload your document"
                    uploadDocumentBtn.text = "Upload Document"
                    uploadDocumentBtn.isEnabled = true

                    attemptsRemainingTextView.visibility = View.GONE

                    if (attempts >= MAX_SUBMISSION_ATTEMPTS) {
                        uploadDocumentBtn.isEnabled = false
                        attemptsRemainingTextView.text = "No more submissions allowed."
                        attemptsRemainingTextView.visibility = View.VISIBLE
                    }
                }
                // Ensure comments are loaded after user is confirmed.
                // Re-calling setupAdminCommentListener() here is redundant given its placement in onCreate
                // and the listener's `addSnapshotListener` behavior. It won't hurt, but isn't strictly necessary.
                // The existing listener from onCreate will pick up on the userId.
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching submission status: ${e.message}", e) // Changed to TAG
                Toast.makeText(this, "Error loading submission status. Defaulting to upload.", Toast.LENGTH_SHORT).show()
                uploadDocumentBtn.isEnabled = true
                submissionStatusTextView.text = "Upload document (status error)"
                submittedCheckImageView.visibility = View.GONE
                attemptsRemainingTextView.visibility = View.GONE
                adminCommentSection.visibility = View.GONE // Hide on error
            }
    }

    private fun checkAndInitiateUpload() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = user.uid
        db.collection("user_submissions_status")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val attempts = document.getLong("attempts")?.toInt() ?: 0
                if (attempts < MAX_SUBMISSION_ATTEMPTS) {
                    getContent.launch("*/*")
                } else {
                    Toast.makeText(this, "You have reached the maximum submission limit.", Toast.LENGTH_LONG).show()
                    updateUIBasedOnSubmissionStatus()
                    Log.d(TAG, "Max submission attempts reached.") // Changed to TAG
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check submission attempts: ${e.message}", e) // Changed to TAG
                Toast.makeText(this, "Failed to check submission attempts. Please try again.", Toast.LENGTH_SHORT).show()
                uploadDocumentBtn.isEnabled = false
            }
    }

    private fun uploadDocument(uri: Uri) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in. Please authenticate.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Upload failed: User not authenticated.")
            return
        }

        val userId = user.uid
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: userId
                val fileName = "$username-${System.currentTimeMillis()}"

                val storageRef: StorageReference = storage.reference.child("step2_uploads/$userId/$fileName")
                Log.d(TAG, "Starting upload for file: $fileName to path: ${storageRef.path}")

                val uploadTask: UploadTask = storageRef.putFile(uri)

                uploadTask.addOnSuccessListener { taskSnapshot ->
                    Log.d(TAG, "Document uploaded to Storage successfully. Getting download URL...")
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val fileUrl = downloadUri.toString()
                        Log.d(TAG, "Download URL obtained: $fileUrl")

                        val documentData = hashMapOf(
                            "fileUrl" to fileUrl,
                            "timestamp" to System.currentTimeMillis(),
                            "fileName" to fileName,
                            "status" to "pending_review" // Added status for consistency
                        )

                        // *** MODIFIED FIREBASE WRITE PATH HERE ***
                        db.collection("adoption_progress") // Start from the parent collection
                            .document(userId)             // Navigate to the specific user's document
                            .collection("step${currentStepNumber}_uploads") // Then to the subcollection 'step2_uploads'
                            .document("document_step2_upload") // A fixed document ID for this specific step's single upload
                            .set(documentData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Document details saved to Firestore for user: $userId")

                                val submissionStatusRef = db.collection("user_submissions_status").document(userId)
                                var finalNewAttempts = 0

                                db.runTransaction { transaction ->
                                    val snapshot = transaction.get(submissionStatusRef)
                                    val currentAttempts = snapshot.getLong("attempts")?.toInt() ?: 0
                                    finalNewAttempts = currentAttempts + 1

                                    transaction.set(submissionStatusRef, hashMapOf(
                                        "submitted" to true,
                                        "attempts" to finalNewAttempts,
                                        "lastSubmissionTimestamp" to System.currentTimeMillis(),
                                        "lastFileUrl" to fileUrl,
                                        "status" to "pending_review" // Added status for consistency
                                    ))
                                    // Transactions should typically return a value or Unit
                                    Unit
                                }.addOnSuccessListener {
                                    Toast.makeText(this, "Document submitted! Admin will review.", Toast.LENGTH_SHORT).show()
                                    Log.d(TAG, "Submission status updated successfully. Attempts: $finalNewAttempts")
                                    updateUIBasedOnSubmissionStatus()

                                    val resultIntent = Intent().apply {
                                        putExtra("submittedStepKey", "step${currentStepNumber}")
                                        putExtra("submissionStatus", "pending_review")
                                    }
                                    setResult(Activity.RESULT_OK, resultIntent)
                                    // You might want to consider not calling finish() immediately here
                                    // if you want the UI to visually update before closing the activity.
                                    finish()

                                }.addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to update submission status: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e(TAG, "Failed to update submission status: ${e.message}", e)
                                    setResult(Activity.RESULT_CANCELED)
                                    finish()
                                }

                                submittedCheckImageView.visibility = View.VISIBLE
                                submissionStatusTextView.text = "Submitted (Pending Admin Review)"

                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save document details to Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Failed to save document details to Firestore: ${e.message}", e)
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Failed to get download URL: ${e.message}", e)
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Document upload to Storage failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Document upload to Storage failed: ${e.message}", e)
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch username: ${e.message}", e)
                Toast.makeText(this, "Failed to fetch username: ${e.message}", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
    }
}