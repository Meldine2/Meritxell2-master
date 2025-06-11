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

class Step9Activity : AppCompatActivity() {

    private val TAG = "Step9Activity"

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

    private var currentStepNumber: Int = 9
    private val documentId: String = "step9_main_document"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step9)

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
            Log.d(TAG, "Upload Document button clicked. Launching file picker.")
            checkAndInitiateUpload()
        }

        val backButton: ImageView = findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked. Finishing activity.")
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        updateUIBasedOnSubmissionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        adminCommentListener?.remove()
        Log.d(TAG, "Admin comment listener removed in onDestroy.")
    }

    private fun setupAdminCommentListener() {
        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "User not logged in, cannot fetch admin comments.")
            adminCommentSection.visibility = View.GONE
            return
        }

        val userId = user.uid
        val stepKey = "step${currentStepNumber}"

        adminCommentListener = db.collection("adoption_progress").document(userId)
            .collection("comments").document(stepKey)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed for admin comments: ${e.message}", e)
                    adminCommentSection.visibility = View.GONE
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val comment = snapshot.getString("comment")
                    if (!comment.isNullOrBlank()) {
                        adminCommentUserTextView.text = comment
                        adminCommentSection.visibility = View.VISIBLE
                        Log.d(TAG, "Admin comment for $stepKey: $comment")
                    } else {
                        adminCommentUserTextView.text = "No comments for this step yet."
                        adminCommentSection.visibility = View.GONE
                        Log.d(TAG, "No admin comment found for $stepKey.")
                    }
                } else {
                    adminCommentUserTextView.text = "No comments for this step yet."
                    adminCommentSection.visibility = View.GONE
                    Log.d(TAG, "Admin comments document not found for user: $userId or step: $stepKey")
                }
            }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { fileUri ->
            Log.d(TAG, "File selected: $fileUri")
            uploadDocument(fileUri)
        } ?: run {
            Log.d(TAG, "No file selected by user.")
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUIBasedOnSubmissionStatus() {
        val user = auth.currentUser

        if (user == null) {
            uploadButton.isEnabled = false
            uploadButton.text = "Log In to Upload"
            statusTextView.text = "Please log in to submit."
            statusTextView.setTextColor(resources.getColor(android.R.color.black, theme)) // Default color
            checkImageView.visibility = View.GONE
            attemptsTextView.visibility = View.GONE
            adminCommentSection.visibility = View.GONE
            Log.d(TAG, "User not logged in. UI disabled.")
            return
        }

        val userId = user.uid
        
        // First get the current adoption number to check submission status for the current adoption instance
        db.collection("adoption_progress").document(userId)
            .get()
            .addOnSuccessListener { adoptionDoc ->
                if (!adoptionDoc.exists()) {
                    Log.d(TAG, "No adoption progress found. Defaulting to upload state.")
                    setDefaultUploadState()
                    return@addOnSuccessListener
                }
                
                val currentAdoptionNumber = if (adoptionDoc.contains("adoptions")) {
                    // Versioned structure - get current adoption number
                    adoptionDoc.getLong("currentAdoption") ?: 1L
                } else {
                    // Old structure - assume adoption 1
                    1L
                }
                
                Log.d(TAG, "Checking submission status for adoption #$currentAdoptionNumber")
                
                // Check if documents exist for current adoption instance in step9_uploads
                db.collection("adoption_progress")
                    .document(userId)
                    .collection("step${currentStepNumber}_uploads")
                    .get()
                    .addOnSuccessListener { uploadsSnapshot ->
                        val hasDocuments = !uploadsSnapshot.isEmpty
                        
                        if (hasDocuments) {
                            // Check submission status for current adoption
                            db.collection("user_submissions_status")
                                .document(userId)
                                .collection("step${currentStepNumber}_documents")
                                .document("${documentId}_adoption_${currentAdoptionNumber}")
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

                                    updateUIWithSubmissionStatus(submitted, attempts)
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error fetching adoption-specific submission status: ${e.message}", e)
                                    setDefaultUploadState()
                                }
                        } else {
                            // No documents uploaded for this step in current adoption
                            Log.d(TAG, "No documents found for step $currentStepNumber in current adoption. Setting to upload state.")
                            setDefaultUploadState()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error checking step uploads: ${e.message}", e)
                        setDefaultUploadState()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching adoption progress: ${e.message}", e)
                setDefaultUploadState()
            }
    }
    
    private fun updateUIWithSubmissionStatus(submitted: Boolean, attempts: Int) {
        val remainingAttempts = MAX_SUBMISSION_ATTEMPTS - attempts

        if (submitted) {
            checkImageView.visibility = View.VISIBLE // Checkmark visible if submitted
            statusTextView.text = "Submitted"
            uploadButton.text = "Re-upload Document"

            if (remainingAttempts <= 0) {
                attemptsTextView.text = "No more submissions allowed."
                uploadButton.isEnabled = false
                Log.d(TAG, "Document submitted, no attempts remaining.")
            } else {
                attemptsTextView.text = "You can submit ${remainingAttempts} more time(s)."
                uploadButton.isEnabled = true
                Log.d(TAG, "Document submitted, $remainingAttempts attempts left.")
            }
            attemptsTextView.visibility = View.VISIBLE

        } else {
            setDefaultUploadState()
        }
    }
    
    private fun setDefaultUploadState() {
        checkImageView.visibility = View.GONE // Checkmark hidden if not submitted
        statusTextView.text = "Upload your document"
        statusTextView.setTextColor(resources.getColor(android.R.color.black, theme)) // Default color
        uploadButton.text = "Upload Document"
        uploadButton.isEnabled = true
        attemptsTextView.visibility = View.GONE
        Log.d(TAG, "UI set to default upload state.")
    }

    private fun checkAndInitiateUpload() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Upload initiation failed: User not logged in.")
            return
        }

        val userId = user.uid
        
        // Get current adoption number first
        db.collection("adoption_progress").document(userId)
            .get()
            .addOnSuccessListener { adoptionDoc ->
                val currentAdoptionNumber = if (adoptionDoc.exists() && adoptionDoc.contains("adoptions")) {
                    adoptionDoc.getLong("currentAdoption") ?: 1L
                } else {
                    1L
                }
                
                // Check attempts for current adoption instance
                db.collection("user_submissions_status")
                    .document(userId)
                    .collection("step${currentStepNumber}_documents")
                    .document("${documentId}_adoption_${currentAdoptionNumber}")
                    .get()
                    .addOnSuccessListener { documentSnapshot ->
                        val attempts: Int = if (documentSnapshot.exists()) {
                            documentSnapshot.getLong("attempts")?.toInt() ?: 0
                        } else {
                            Log.d(TAG, "Document status for $documentId adoption $currentAdoptionNumber does not exist yet. Initializing attempts to 0.")
                            0
                        }

                        if (attempts < MAX_SUBMISSION_ATTEMPTS) {
                            getContent.launch("*/*")
                            Log.d(TAG, "Initiating file picker for adoption $currentAdoptionNumber. Attempts: $attempts")
                        } else {
                            Toast.makeText(this, "You have reached the maximum submission limit for this document.", Toast.LENGTH_LONG).show()
                            updateUIBasedOnSubmissionStatus()
                            Log.d(TAG, "Max submission attempts reached for adoption $currentAdoptionNumber.")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to check submission attempts: ${e.message}", e)
                        Toast.makeText(this, "Failed to check submission attempts. Please try again.", Toast.LENGTH_SHORT).show()
                        uploadButton.isEnabled = false
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get adoption progress: ${e.message}", e)
                Toast.makeText(this, "Failed to check adoption status. Please try again.", Toast.LENGTH_SHORT).show()
                uploadButton.isEnabled = false
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
        
        // Get current adoption number first
        db.collection("adoption_progress").document(userId)
            .get()
            .addOnSuccessListener { adoptionDoc ->
                val currentAdoptionNumber = if (adoptionDoc.exists() && adoptionDoc.contains("adoptions")) {
                    adoptionDoc.getLong("currentAdoption") ?: 1L
                } else {
                    1L
                }
                
                db.collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener { userDoc ->
                        val username = userDoc.getString("username") ?: userId
                        val fileName = "$username-${documentId}-adoption${currentAdoptionNumber}-${System.currentTimeMillis()}"

                        val storageRef: StorageReference = storage.reference.child("step${currentStepNumber}_uploads/$userId/$fileName")
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
                                    "adoptionNumber" to currentAdoptionNumber,
                                    "status" to "pending_review"
                                )

                                db.collection("adoption_progress")
                                    .document(userId)
                                    .collection("step${currentStepNumber}_uploads")
                                    .document(documentId)
                                    .set(documentData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Document details saved to Firestore for user: $userId, document: $documentId, adoption: $currentAdoptionNumber")

                                        val submissionStatusRef = db.collection("user_submissions_status")
                                            .document(userId)
                                            .collection("step${currentStepNumber}_documents")
                                            .document("${documentId}_adoption_${currentAdoptionNumber}")

                                        db.runTransaction { transaction ->
                                            val snapshot = transaction.get(submissionStatusRef)
                                            val currentAttempts = snapshot.getLong("attempts")?.toInt() ?: 0
                                            val newAttempts = currentAttempts + 1

                                            transaction.set(submissionStatusRef, hashMapOf(
                                                "submitted" to true,
                                                "attempts" to newAttempts,
                                                "adoptionNumber" to currentAdoptionNumber,
                                                "lastSubmissionTimestamp" to System.currentTimeMillis(),
                                                "lastFileUrl" to fileUrl,
                                                "status" to "pending_review"
                                            ), SetOptions.merge())
                                            Unit
                                        }.addOnSuccessListener {
                                            Toast.makeText(this, "Document submitted! Admin will review.", Toast.LENGTH_SHORT).show()
                                            Log.d(TAG, "Submission status updated successfully for adoption $currentAdoptionNumber.")
                                            updateUIBasedOnSubmissionStatus()

                                            val resultIntent = Intent().apply {
                                                putExtra("submittedDocumentId", documentId)
                                                putExtra("submissionStatus", "pending_review")
                                                putExtra("stepNumber", currentStepNumber)
                                                putExtra("adoptionNumber", currentAdoptionNumber.toInt())
                                            }
                                            setResult(Activity.RESULT_OK, resultIntent)

                                        }.addOnFailureListener { e ->
                                            Toast.makeText(this, "Failed to update submission status: ${e.message}", Toast.LENGTH_LONG).show()
                                            Log.e(TAG, "Failed to update submission status: ${e.message}", e)
                                        }

                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Failed to save document details to Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                                        Log.e(TAG, "Failed to save document details to Firestore: ${e.message}", e)
                                    }
                            }.addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Failed to get download URL: ${e.message}", e)
                            }
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Document upload to Storage failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Document upload to Storage failed: ${e.message}", e)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to fetch username for file naming: ${e.message}", e)
                        Toast.makeText(this, "Failed to fetch username: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get adoption progress: ${e.message}", e)
                Toast.makeText(this, "Failed to check adoption status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}