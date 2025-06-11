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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration

class Step8Activity : AppCompatActivity() {

    private lateinit var uploadButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var attemptsTextView: TextView
    private lateinit var checkImageView: ImageView

    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var db: FirebaseFirestore

    private val MAX_SUBMISSION_ATTEMPTS = 3

    private lateinit var adminCommentSection: LinearLayout
    private lateinit var adminCommentUserTextView: TextView

    private var adminCommentListener: ListenerRegistration? = null

    private var currentStepNumber: Int = 8
    private val documentId: String = "month_1_post_adoption_video"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step8)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        uploadButton = findViewById(R.id.uploadDocumentBtn14)
        statusTextView = findViewById(R.id.submissionStatusTextView14)
        attemptsTextView = findViewById(R.id.attemptsRemainingTextView14)
        checkImageView = findViewById(R.id.submittedCheckImageView14)

        adminCommentSection = findViewById(R.id.adminCommentSection)
        adminCommentUserTextView = findViewById(R.id.adminCommentUserTextView)

        setupAdminCommentListener()

        uploadButton.setOnClickListener {
            Log.d("Step8Activity", "Upload Document button clicked. Launching file picker.")
            checkAndInitiateUpload()
        }

        val backButton: ImageView = findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            Log.d("Step8Activity", "Back button clicked. Finishing activity.")
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        updateUIBasedOnSubmissionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        adminCommentListener?.remove()
        Log.d("Step8Activity", "Admin comment listener removed in onDestroy.")
    }

    private fun setupAdminCommentListener() {
        val user = auth.currentUser
        if (user == null) {
            Log.e("Step8Activity", "User not logged in, cannot fetch admin comments.")
            adminCommentSection.visibility = View.GONE
            return
        }

        val userId = user.uid
        val stepKey = "step${currentStepNumber}"

        adminCommentListener = db.collection("adoption_progress").document(userId)
            .collection("comments").document(stepKey)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Step8Activity", "Listen failed for admin comments: ${e.message}", e)
                    adminCommentSection.visibility = View.GONE
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val comment = snapshot.getString("comment")
                    if (!comment.isNullOrBlank()) {
                        adminCommentUserTextView.text = comment
                        adminCommentSection.visibility = View.VISIBLE
                        Log.d("Step8Activity", "Admin comment for $stepKey: $comment")
                    } else {
                        adminCommentUserTextView.text = "No comments for this step yet."
                        adminCommentSection.visibility = View.GONE
                        Log.d("Step8Activity", "No admin comment found for $stepKey.")
                    }
                } else {
                    adminCommentUserTextView.text = "No comments for this step yet."
                    adminCommentSection.visibility = View.GONE
                    Log.d("Step8Activity", "Admin comments document not found for user: $userId or step: $stepKey")
                }
            }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { fileUri ->
            Log.d("Step8Activity", "File selected: $fileUri")
            uploadDocument(fileUri)
        } ?: run {
            Log.d("Step8Activity", "No file selected by user.")
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
            adminCommentSection.visibility = View.GONE
            Log.d("Step8Activity", "User not logged in. UI disabled.")
            return
        }

        val userId = user.uid
        db.collection("user_submissions_status")
            .document(userId)
            .collection("step${currentStepNumber}_documents")
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
                        Log.d("Step8Activity", "Document submitted, no attempts remaining.")
                    } else {
                        attemptsTextView.text = "You can submit ${remainingAttempts} more time(s)."
                        uploadButton.isEnabled = true
                        Log.d("Step8Activity", "Document submitted, $remainingAttempts attempts left.")
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
                        Log.d("Step8Activity", "Document not submitted, no attempts remaining.")
                    } else {
                        attemptsTextView.visibility = View.GONE
                        Log.d("Step8Activity", "Document not submitted, attempts left.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step8Activity", "Error fetching submission status for $documentId: ${e.message}", e)
                Toast.makeText(this, "Error loading status. Defaulting to upload.", Toast.LENGTH_SHORT).show()
                uploadButton.isEnabled = true
                statusTextView.text = "Upload document (status error)"
                checkImageView.visibility = View.GONE
                attemptsTextView.visibility = View.GONE
                adminCommentSection.visibility = View.GONE
            }
    }

    private fun checkAndInitiateUpload() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            Log.d("Step8Activity", "Upload initiation failed: User not logged in.")
            return
        }

        val userId = user.uid
        db.collection("user_submissions_status")
            .document(userId)
            .collection("step${currentStepNumber}_documents")
            .document(documentId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val attempts: Int = if (documentSnapshot.exists()) {
                    documentSnapshot.getLong("attempts")?.toInt() ?: 0
                } else {
                    Log.d("Step8Activity", "Document status for $documentId does not exist yet. Initializing attempts to 0.")
                    0
                }

                if (attempts < MAX_SUBMISSION_ATTEMPTS) {
                    getContent.launch("*/*")
                    Log.d("Step8Activity", "Initiating file picker. Attempts: $attempts")
                } else {
                    Toast.makeText(this, "You have reached the maximum submission limit for this document.", Toast.LENGTH_LONG).show()
                    updateUIBasedOnSubmissionStatus()
                    Log.d("Step8Activity", "Max submission attempts reached.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step8Activity", "Failed to check submission attempts: ${e.message}", e)
                Toast.makeText(this, "Failed to check submission attempts. Please try again.", Toast.LENGTH_SHORT).show()
                uploadButton.isEnabled = false
            }
    }

    private fun uploadDocument(uri: Uri) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in. Please authenticate.", Toast.LENGTH_SHORT).show()
            Log.e("Step8Activity", "Upload failed: User not authenticated.")
            return
        }

        val userId = user.uid
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                val username = userDoc.getString("username") ?: userId
                val fileName = "$username-${documentId}-${System.currentTimeMillis()}"

                val storageRef: StorageReference = storage.reference.child("step${currentStepNumber}_uploads/$userId/$fileName")
                Log.d("Step8Activity", "Starting upload for file: $fileName to path: ${storageRef.path}")

                val uploadTask: UploadTask = storageRef.putFile(uri)

                uploadTask.addOnSuccessListener { taskSnapshot ->
                    Log.d("Step8Activity", "Document uploaded to Storage successfully. Getting download URL...")
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val fileUrl = downloadUri.toString()
                        Log.d("Step8Activity", "Download URL obtained: $fileUrl")

                        val documentData = hashMapOf(
                            "fileUrl" to fileUrl,
                            "timestamp" to System.currentTimeMillis(),
                            "fileName" to fileName,
                            "status" to "pending_review"
                        )

                        db.collection("adoption_progress")
                            .document(userId)
                            .collection("step${currentStepNumber}_uploads")
                            .document(documentId)
                            .set(documentData, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d("Step8Activity", "Document details saved to Firestore for user: $userId, document: $documentId")

                                val submissionStatusRef = db.collection("user_submissions_status")
                                    .document(userId)
                                    .collection("step${currentStepNumber}_documents")
                                    .document(documentId)

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
                                        "status" to "pending_review"
                                    ))
                                    Unit
                                }.addOnSuccessListener {
                                    Toast.makeText(this, "Document submitted! Admin will review.", Toast.LENGTH_SHORT).show()
                                    Log.d("Step8Activity", "Submission status updated successfully. Attempts: $finalNewAttempts")
                                    updateUIBasedOnSubmissionStatus()

                                    val resultIntent = Intent().apply {
                                        putExtra("submittedDocumentId", documentId)
                                        putExtra("submissionStatus", "pending_review")
                                        putExtra("stepNumber", currentStepNumber)
                                    }
                                    setResult(Activity.RESULT_OK, resultIntent)

                                }.addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to update submission status: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e("Step8Activity", "Failed to update submission status: ${e.message}", e)
                                }

                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save document details to Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e("Step8Activity", "Failed to save document details to Firestore: ${e.message}", e)
                            }
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("Step8Activity", "Failed to get download URL: ${e.message}", e)
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Document upload to Storage failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Step8Activity", "Document upload to Storage failed: ${e.message}", e)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step8Activity", "Failed to fetch username: ${e.message}", e)
                Toast.makeText(this, "Failed to fetch username: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}