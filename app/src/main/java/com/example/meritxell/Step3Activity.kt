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

class Step3Activity : AppCompatActivity() {

    // Map to hold references to UI elements for each document
    // Using a map directly will make initialization more compact and reduce boilerplate
    private val documentUIMap = mutableMapOf<Int, DocumentUIElements>()

    private data class DocumentUIElements(
        val uploadButton: Button,
        val statusTextView: TextView,
        val attemptsTextView: TextView,
        val checkImageView: ImageView,
        val documentId: String // Unique ID for Firestore document
    )

    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var db: FirebaseFirestore

    private val MAX_SUBMISSION_ATTEMPTS = 3

    private var currentStepNumber: Int = 3 // This activity is for Step 3

    // Admin comment views
    private lateinit var adminCommentSection: LinearLayout
    private lateinit var adminCommentUserTextView: TextView

    // Listener for admin comments
    private var adminCommentListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step3)

        adminCommentSection = findViewById(R.id.adminCommentSection)
        adminCommentUserTextView = findViewById(R.id.adminCommentUserTextView)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        // Get the step number from the intent, default to 3 for this activity
        currentStepNumber = intent.getIntExtra("stepNumber", 3)

        // Initialize UI elements and populate the map
        initializeDocumentUIElements()

        // Set up listener for admin comments
        setupAdminCommentListener()

        // Set click listeners for each upload button
        documentUIMap.forEach { (docNumber, uiElements) ->
            uiElements.uploadButton.setOnClickListener {
                Log.d("Step${currentStepNumber}Activity", "Upload Document button clicked for document $docNumber. Launching file picker.")
                checkAndInitiateUpload(docNumber, uiElements.documentId)
            }
        }

        val backButton: ImageView = findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            Log.d("Step${currentStepNumber}Activity", "Back button clicked. Finishing activity.")
            setResult(Activity.RESULT_CANCELED) // Indicate that the step wasn't successfully completed
            finish()
        }

        // Update UI for all documents on creation
        documentUIMap.forEach { (docNumber, uiElements) ->
            updateUIBasedOnSubmissionStatus(docNumber, uiElements.documentId)
        }
    }


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
        val stepKey = "step${currentStepNumber}" // This will be "step3" for this activity

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

    private fun initializeDocumentUIElements() {
        try {
            // Document 1
            documentUIMap[1] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn1),
                findViewById(R.id.submissionStatusTextView1),
                findViewById(R.id.attemptsRemainingTextView1),
                findViewById(R.id.submittedCheckImageView1),
                "document_1_application_undertaking"
            )

            // Document 2
            documentUIMap[2] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn2),
                findViewById(R.id.submissionStatusTextView2),
                findViewById(R.id.attemptsRemainingTextView2),
                findViewById(R.id.submittedCheckImageView2),
                "document_2_psa_birth_certificate"
            )

            // Document 3
            documentUIMap[3] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn3),
                findViewById(R.id.submissionStatusTextView3),
                findViewById(R.id.attemptsRemainingTextView3),
                findViewById(R.id.submittedCheckImageView3),
                "document_3_psa_marriage_divorce"
            )

            // Document 4
            documentUIMap[4] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn4),
                findViewById(R.id.submissionStatusTextView4),
                findViewById(R.id.attemptsRemainingTextView4),
                findViewById(R.id.submittedCheckImageView4),
                "document_4_medical_certificate"
            )

            // Document 5
            documentUIMap[5] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn5),
                findViewById(R.id.submissionStatusTextView5),
                findViewById(R.id.attemptsRemainingTextView5),
                findViewById(R.id.submittedCheckImageView5),
                "document_5_income_proof"
            )

            // Document 6
            documentUIMap[6] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn6),
                findViewById(R.id.submissionStatusTextView6),
                findViewById(R.id.attemptsRemainingTextView6),
                findViewById(R.id.submittedCheckImageView6),
                "document_6_nbi_police_clearance"
            )

            // Document 7
            documentUIMap[7] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn7),
                findViewById(R.id.submissionStatusTextView7),
                findViewById(R.id.attemptsRemainingTextView7),
                findViewById(R.id.submittedCheckImageView7),
                "document_7_barangay_certificate"
            )

            // Document 8
            documentUIMap[8] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn8),
                findViewById(R.id.submissionStatusTextView8),
                findViewById(R.id.attemptsRemainingTextView8),
                findViewById(R.id.submittedCheckImageView8),
                "document_8_whole_body_photos"
            )

            // Document 9
            documentUIMap[9] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn9),
                findViewById(R.id.submissionStatusTextView9),
                findViewById(R.id.attemptsRemainingTextView9),
                findViewById(R.id.submittedCheckImageView9),
                "document_9_character_references"
            )

            // Document 10
            documentUIMap[10] = DocumentUIElements(
                findViewById(R.id.uploadDocumentBtn10),
                findViewById(R.id.submissionStatusTextView10),
                findViewById(R.id.attemptsRemainingTextView10),
                findViewById(R.id.submittedCheckImageView10),
                "document_10_psychological_evaluation"
            )
            Log.d("Step${currentStepNumber}Activity", "All UI elements initialized successfully.")
        } catch (e: Exception) {
            Log.e("Step${currentStepNumber}Activity", "Error initializing UI elements: ${e.message}", e)
            Toast.makeText(this, "Error loading screen. Please check logs.", Toast.LENGTH_LONG).show()
            // Depending on severity, you might want to finish the activity here
            // finish()
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { fileUri ->
            currentSelectedDocument?.let { (docNumber, documentId) ->
                Log.d("Step${currentStepNumber}Activity", "File selected for document $docNumber: $fileUri")
                uploadDocument(fileUri, docNumber, documentId)
            } ?: run {
                Log.e("Step${currentStepNumber}Activity", "No current document selected when file picker returned.")
                Toast.makeText(this, "Error: No document context for upload.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.d("Step${currentStepNumber}Activity", "No file selected by user.")
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    // A temporary variable to hold the ID of the document whose upload button was clicked
    private var currentSelectedDocument: Pair<Int, String>? = null

    private fun updateUIBasedOnSubmissionStatus(docNumber: Int, documentId: String) {
        val user = auth.currentUser
        val uiElements = documentUIMap[docNumber] ?: return

        if (user == null) {
            uiElements.uploadButton.isEnabled = false
            uiElements.uploadButton.text = "Log In to Upload"
            uiElements.statusTextView.text = "Please log in to submit."
            uiElements.checkImageView.visibility = View.GONE
            uiElements.attemptsTextView.visibility = View.GONE
            adminCommentSection.visibility = View.GONE // Hide if user not logged in
            Log.d("Step${currentStepNumber}Activity", "User not logged in. UI disabled for document $docNumber.")
            return
        }

        val userId = user.uid
        db.collection("user_submissions_status")
            .document(userId)
            .collection("step${currentStepNumber}_documents") // e.g., "step3_documents"
            .document(documentId)
            .get()
            .addOnSuccessListener { document ->
                val submitted = document.getBoolean("submitted") ?: false
                val attempts = document.getLong("attempts")?.toInt() ?: 0

                val remainingAttempts = MAX_SUBMISSION_ATTEMPTS - attempts

                if (submitted) {
                    uiElements.checkImageView.visibility = View.VISIBLE
                    uiElements.statusTextView.text = "Submitted"
                    uiElements.uploadButton.text = "Re-upload Document"

                    if (remainingAttempts <= 0) {
                        uiElements.attemptsTextView.text = "No more submissions allowed."
                        uiElements.uploadButton.isEnabled = false
                        Log.d("Step${currentStepNumber}Activity", "Document $docNumber: Submitted, no attempts remaining.")
                    } else {
                        uiElements.attemptsTextView.text = "You can submit ${remainingAttempts} more time(s)."
                        uiElements.uploadButton.isEnabled = true
                        Log.d("Step${currentStepNumber}Activity", "Document $docNumber: Submitted, $remainingAttempts attempts left.")
                    }
                    uiElements.attemptsTextView.visibility = View.VISIBLE

                } else {
                    uiElements.checkImageView.visibility = View.GONE
                    uiElements.statusTextView.text = "Upload your document"
                    uiElements.uploadButton.text = "Upload Document"
                    uiElements.uploadButton.isEnabled = true

                    if (attempts >= MAX_SUBMISSION_ATTEMPTS) {
                        uiElements.uploadButton.isEnabled = false
                        uiElements.attemptsTextView.text = "No more submissions allowed."
                        uiElements.attemptsTextView.visibility = View.VISIBLE
                        Log.d("Step${currentStepNumber}Activity", "Document $docNumber: Not submitted, no attempts remaining.")
                    } else {
                        uiElements.attemptsTextView.visibility = View.GONE
                        Log.d("Step${currentStepNumber}Activity", "Document $docNumber: Not submitted, attempts left.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step${currentStepNumber}Activity", "Error fetching submission status for $documentId: ${e.message}", e)
                Toast.makeText(this, "Error loading status for document $docNumber. Defaulting to upload.", Toast.LENGTH_SHORT).show()
                uiElements.uploadButton.isEnabled = true
                uiElements.statusTextView.text = "Upload document (status error)"
                uiElements.checkImageView.visibility = View.GONE
                uiElements.attemptsTextView.visibility = View.GONE
                adminCommentSection.visibility = View.GONE // Hide on error
            }
    }

    private fun checkAndInitiateUpload(docNumber: Int, documentId: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            Log.d("Step${currentStepNumber}Activity", "Upload initiation failed: User not logged in.")
            return
        }

        val userId = user.uid
        // Path adjusted for checking status
        db.collection("user_submissions_status")
            .document(userId)
            .collection("step${currentStepNumber}_documents") // e.g., "step3_documents"
            .document(documentId)
            .get()
            .addOnSuccessListener { document ->
                val attempts = document.getLong("attempts")?.toInt() ?: 0
                if (attempts < MAX_SUBMISSION_ATTEMPTS) {
                    currentSelectedDocument = Pair(docNumber, documentId) // Store the current document context
                    getContent.launch("*/*") // Launch file picker
                    Log.d("Step${currentStepNumber}Activity", "Initiating file picker for document $docNumber. Attempts: $attempts")
                } else {
                    Toast.makeText(this, "You have reached the maximum submission limit for document $docNumber.", Toast.LENGTH_LONG).show()
                    updateUIBasedOnSubmissionStatus(docNumber, documentId) // Ensure UI reflects max attempts
                    Log.d("Step${currentStepNumber}Activity", "Max submission attempts reached.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step${currentStepNumber}Activity", "Failed to check submission attempts for document $docNumber: ${e.message}", e)
                Toast.makeText(this, "Failed to check submission attempts for document $docNumber. Please try again.", Toast.LENGTH_SHORT).show()
                // Optionally disable the button here if checking attempts is critical before upload
                documentUIMap[docNumber]?.uploadButton?.isEnabled = false
            }
    }

    private fun uploadDocument(uri: Uri, docNumber: Int, documentId: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in. Please authenticate.", Toast.LENGTH_SHORT).show()
            Log.e("Step${currentStepNumber}Activity", "Upload failed: User not authenticated.")
            return
        }

        val uiElements = documentUIMap[docNumber] ?: run {
            Log.e("Step${currentStepNumber}Activity", "UI elements not found for document $docNumber. Cannot proceed with upload.")
            Toast.makeText(this, "Error: UI elements not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = user.uid
        // First, fetch the username to use in the file name
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                val username = userDoc.getString("username") ?: userId // Use userId as fallback
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
                            .document(documentId) // This is the specific document within the subcollection
                            .set(documentData, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d("Step${currentStepNumber}Activity", "Document details saved to Firestore for user: $userId, document: $documentId")

                                val submissionStatusRef = db.collection("user_submissions_status")
                                    .document(userId)
                                    .collection("step${currentStepNumber}_documents") // e.g., "step3_documents"
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
                                    Toast.makeText(this, "Document $docNumber submitted! Admin will review.", Toast.LENGTH_SHORT).show()
                                    Log.d("Step${currentStepNumber}Activity", "Submission status updated successfully for document $docNumber. Attempts: $finalNewAttempts")
                                    updateUIBasedOnSubmissionStatus(docNumber, documentId)

                                    // If all documents for this step are submitted, you might want to return RESULT_OK
                                    // For now, let's assume successful upload of one document is enough for this specific result.
                                    val resultIntent = Intent().apply {
                                        putExtra("submittedDocumentId", documentId)
                                        putExtra("submissionStatus", "pending_review")
                                        putExtra("stepNumber", currentStepNumber) // Pass the step number back
                                    }
                                    // Only set RESULT_OK if this upload is a success within the context of the activity's goal
                                    // If Step3Activity's goal is to complete ALL documents, then you'd check all submissions here.
                                    // For now, it indicates one document's submission was successful.
                                    setResult(Activity.RESULT_OK, resultIntent)

                                }.addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to update submission status for document $docNumber: ${e.message}", Toast.LENGTH_LONG).show()
                                    Log.e("Step${currentStepNumber}Activity", "Failed to update submission status for document $docNumber: ${e.message}", e)
                                }

                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save document details to Firestore for document $docNumber: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e("Step${currentStepNumber}Activity", "Failed to save document details to Firestore for document $docNumber: ${e.message}", e)
                            }
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to get download URL for document $docNumber: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("Step${currentStepNumber}Activity", "Failed to get download URL for document $docNumber: ${e.message}", e)
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Document upload to Storage failed for document $docNumber: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Step${currentStepNumber}Activity", "Document upload to Storage failed for document $docNumber: ${e.message}", e)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Step${currentStepNumber}Activity", "Failed to fetch username for document $docNumber: ${e.message}", e)
                Toast.makeText(this, "Failed to fetch username for document $docNumber: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}