package com.example.meritxell

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.meritxell.data.Donation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class AdminDonationDetailActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseStorage: FirebaseStorage

    private lateinit var donationId: String
    private lateinit var collectionName: String
    private var donation: Donation? = null

    private lateinit var tvDetailType: TextView
    private lateinit var tvDetailAmount: TextView
    private lateinit var tvDetailDonor: TextView
    private lateinit var tvDetailStatus: TextView
    private lateinit var tvDetailReceiptUrl: TextView
    private lateinit var ivReceiptImage: ImageView

    // Proof of Donation UI elements
    private lateinit var tvProofOfDonationLabel: TextView
    private lateinit var etProofText: EditText
    private lateinit var ivProofImage: ImageView
    private lateinit var btnUploadProofImage: Button
    private lateinit var btnSaveProof: Button

    private lateinit var btnApproveDonation: Button
    private lateinit var btnRejectDonation: Button
    private lateinit var tvDetailTimestamp: TextView
    private lateinit var btnBack: ImageView

    private var selectedProofImageUri: Uri? = null

    companion object {
        private const val TAG = "AdminDonationDetail"
        private const val DONATIONS_COLLECTION = "donations"
        private const val DATE_FORMAT = "MMM dd,yyyy HH:mm"
    }

    // New launcher for picking content
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            Log.d(TAG, "Content selected: $uri")
            selectedProofImageUri = uri
            ivProofImage.visibility = View.VISIBLE
            ivProofImage.setImageURI(selectedProofImageUri) // Display selected image for preview
        } else {
            Log.d(TAG, "No content selected or user cancelled.")
            Toast.makeText(this, "No image selected.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_donation_detail)

        supportActionBar?.hide()

        firestore = FirebaseFirestore.getInstance()
        firebaseStorage = FirebaseStorage.getInstance()

        donationId = intent.getStringExtra("donationId") ?: run {
            Log.e(TAG, "FATAL: Donation ID not found in intent extras. Finishing activity.")
            Toast.makeText(this, "Error: Donation ID missing. Please try again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        collectionName = intent.getStringExtra("collectionName") ?: "donations"

        // Get viewer role to determine permissions
        val viewerRole = intent.getStringExtra("viewerRole") ?: "user"
        val isAdmin = viewerRole == "admin"

        initViews()
        fetchDonationDetails()
        setupListeners(isAdmin)
    }

    private fun initViews() {
        try {
            tvDetailType = findViewById(R.id.tvDetailType)
            tvDetailAmount = findViewById(R.id.tvDetailAmount)
            tvDetailDonor = findViewById(R.id.tvDetailDonor)
            tvDetailStatus = findViewById(R.id.tvDetailStatus)
            tvDetailReceiptUrl = findViewById(R.id.tvDetailReceiptUrl)
            ivReceiptImage = findViewById(R.id.ivReceiptImage)

            // Initialize Proof of Donation UI elements
            tvProofOfDonationLabel = findViewById(R.id.tvProofOfDonationLabel)
            etProofText = findViewById(R.id.etProofText)
            ivProofImage = findViewById(R.id.ivProofImage)
            btnUploadProofImage = findViewById(R.id.btnUploadProofImage)
            btnSaveProof = findViewById(R.id.btnSaveProof)

            btnApproveDonation = findViewById(R.id.btnApproveDonation)
            btnRejectDonation = findViewById(R.id.btnRejectDonation)
            tvDetailTimestamp = findViewById(R.id.tvDetailTimestamp)
            btnBack = findViewById(R.id.btnBack)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views: ${e.message}", e)
            Toast.makeText(this, "App configuration error. Please contact support.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun fetchDonationDetails() {
        firestore.collection(collectionName).document(donationId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val donation = Donation(
                        id = document.id,
                        userId = document.getString("userId"),
                        username = document.getString("username"),
                        donationType = document.getString("donationType"),
                        amount = document.getString("amount"),
                        receiptUrl = document.getString("receiptUrl"),
                        status = document.getString("status"),
                        proofOfDonationText = document.getString("proofOfDonationText"),
                        proofOfDonationImageUrl = document.getString("proofOfDonationImageUrl")
                    )

                    val timestampObject = document.get("timestamp")

                    if (timestampObject is com.google.firebase.Timestamp) {
                        donation.displayDate = timestampObject.toDate().let { date ->
                            SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(date)
                        }
                    } else if (timestampObject is String) {
                        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        donation.displayDate = try {
                            val date = formatter.parse(timestampObject)
                            date?.let { SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing string timestamp from Firestore for ID ${document.id}: $timestampObject - ${e.message}", e)
                            "Invalid Date Format"
                        }
                    } else {
                        donation.displayDate = "N/A"
                        Log.w(TAG, "Timestamp field for ID ${document.id} is neither Firebase Timestamp nor String. Type: ${timestampObject?.javaClass?.name ?: "null"}")
                    }

                    Log.d(TAG, "Full Donation Object Fetched: $donation")

                    this.donation = donation // Store for later use
                    displayDonationDetails(donation)
                } else {
                    Log.w(TAG, "Donation with ID '$donationId' not found in Firestore.")
                    Toast.makeText(this, "Donation details not found.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error fetching donation details for ID: $donationId", exception)
                Toast.makeText(this, "Failed to load donation: ${exception.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun displayDonationDetails(donation: Donation) {
        tvDetailType.text = "Donation Type: ${donation.donationType ?: "N/A"}"
        tvDetailAmount.text = "Amount: ${donation.amount ?: "N/A"}"
        tvDetailDonor.text = "Donor: ${donation.username ?: "N/A"}"
        tvDetailTimestamp.text = "Date: ${donation.displayDate ?: "N/A"}"

        val currentStatus = donation.status ?: "N/A"
        tvDetailStatus.text = "Status: $currentStatus"
        tvDetailStatus.visibility = View.VISIBLE

        // Get viewer role to determine permissions
        val viewerRole = intent.getStringExtra("viewerRole") ?: "user"
        val isAdmin = viewerRole == "admin"

        // Control visibility of Approve/Reject buttons based on status and user role
        if (isAdmin && currentStatus.equals("pending", ignoreCase = true)) {
            btnApproveDonation.visibility = View.VISIBLE
            btnRejectDonation.visibility = View.VISIBLE
        } else {
            btnApproveDonation.visibility = View.GONE
            btnRejectDonation.visibility = View.GONE
        }

        val receiptUrl = donation.receiptUrl
        if (!receiptUrl.isNullOrBlank()) {
            tvDetailReceiptUrl.text = "Receipt URL: $receiptUrl"
            tvDetailReceiptUrl.visibility = View.VISIBLE
            ivReceiptImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(receiptUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(ivReceiptImage)
        } else {
            tvDetailReceiptUrl.visibility = View.GONE
            ivReceiptImage.visibility = View.GONE
            Glide.with(this).clear(ivReceiptImage)
        }

        // --- START: MODIFIED LOGIC FOR PERMANENT HIDING OF PROOF OF DONATION INPUT SECTION ---
        val proofImageUrl = donation.proofOfDonationImageUrl
        val proofText = donation.proofOfDonationText

        // Handle proof of donation section based on user role and submission status
        if (!proofText.isNullOrBlank() || !proofImageUrl.isNullOrBlank()) {
            // Proof has been submitted - show it for viewing
            tvProofOfDonationLabel.visibility = View.VISIBLE
            tvProofOfDonationLabel.text = "Proof of Donation (Submitted)"
            
            if (!proofText.isNullOrBlank()) {
                etProofText.visibility = View.VISIBLE
                etProofText.setText(proofText)
                etProofText.isEnabled = false // Make it read-only
            } else {
                etProofText.visibility = View.GONE
            }
            
            if (!proofImageUrl.isNullOrBlank()) {
                ivProofImage.visibility = View.VISIBLE
                Glide.with(this)
                    .load(proofImageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(ivProofImage)
            } else {
                ivProofImage.visibility = View.GONE
            }
            
            // Hide input controls since proof is already submitted
            btnUploadProofImage.visibility = View.GONE
            btnSaveProof.visibility = View.GONE
            selectedProofImageUri = null
        } else if (!isAdmin) {
            // Regular user viewing their own donation - hide proof section if not submitted
            tvProofOfDonationLabel.visibility = View.GONE
            etProofText.visibility = View.GONE
            ivProofImage.visibility = View.GONE
            btnUploadProofImage.visibility = View.GONE
            btnSaveProof.visibility = View.GONE
        } else {
            // Admin viewing donation with no proof submitted yet - show input section
            tvProofOfDonationLabel.visibility = View.VISIBLE
            tvProofOfDonationLabel.text = "Proof of Donation"
            etProofText.visibility = View.VISIBLE
            etProofText.isEnabled = true
            btnUploadProofImage.visibility = View.VISIBLE
            btnSaveProof.visibility = View.VISIBLE

            // Ensure the text field is empty and image view is hidden for fresh input
            etProofText.setText("")
            ivProofImage.visibility = View.GONE
            Glide.with(this).clear(ivProofImage)
            selectedProofImageUri = null
        }
        // --- END: MODIFIED LOGIC ---
    }


    private fun setupListeners(isAdmin: Boolean) {
        btnBack.setOnClickListener {
            onBackPressed()
        }

        // Only set up admin-specific listeners if user is admin
        if (isAdmin) {
            btnUploadProofImage.setOnClickListener {
                Log.d(TAG, "Upload Proof Image button clicked. Launching image picker.")
                getContent.launch("image/*")
            }

            btnSaveProof.setOnClickListener {
                saveProofOfDonation()
            }

            btnApproveDonation.setOnClickListener {
                // Check if proof of donation (picture + comment) has been provided
                val currentDonation = donation
                val hasProofImage = !currentDonation?.proofOfDonationImageUrl.isNullOrBlank()
                val hasProofText = !currentDonation?.proofOfDonationText.isNullOrBlank()
                
                if (!hasProofImage || !hasProofText) {
                    AlertDialog.Builder(this)
                        .setTitle("Proof Required")
                        .setMessage("Before approving this donation, you must provide both a picture and comment as proof of donation. Please upload an image and add a comment first.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }
                
                showConfirmationDialog(
                    "Approve Donation",
                    "Are you sure you want to approve this donation? This will move the donation to history.",
                    { updateDonationStatusAndGoToHistory("approved") }
                )
            }

            btnRejectDonation.setOnClickListener {
                showConfirmationDialog(
                    "Reject Donation",
                    "Are you sure you want to reject this donation? This will move the donation to history.",
                    { updateDonationStatusAndGoToHistory("rejected") }
                )
            }
        }
    }

    private fun saveProofOfDonation() {
        val proofText = etProofText.text.toString().trim()

        if (selectedProofImageUri == null && proofText.isEmpty()) {
            Toast.makeText(this, "Please select an image or enter text for proof before saving.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedProofImageUri != null) {
            uploadProofImage(proofText)
        } else {
            updateProofInFirestore(proofText, null)
        }
    }

    private fun uploadProofImage(proofText: String) {
        val uriToUpload = selectedProofImageUri
        if (uriToUpload == null) {
            Toast.makeText(this, "No image selected for upload.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "uploadProofImage called with null selectedProofImageUri.")
            return
        }

        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
        val storageRef = firebaseStorage.reference.child("proof_of_donations/${UUID.randomUUID()}.jpg")

        storageRef.putFile(uriToUpload)
            .addOnSuccessListener { taskSnapshot ->
                val metadataReference = taskSnapshot.metadata?.reference
                if (metadataReference != null) {
                    metadataReference.downloadUrl.addOnSuccessListener { downloadUri ->
                        Log.d(TAG, "Image uploaded successfully. Download URL: $downloadUri")
                        updateProofInFirestore(proofText, downloadUri.toString())
                        // selectedProofImageUri will be cleared in updateProofInFirestore's success block
                    }.addOnFailureListener {
                        Log.e(TAG, "Failed to get download URL after image upload: ${it.message}", it)
                        Toast.makeText(this, "Failed to get image link: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e(TAG, "Storage metadata reference is null after successful upload.")
                    Toast.makeText(this, "Error: Could not get image reference after upload.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to upload image to Firebase Storage: ${it.message}", it)
                Toast.makeText(this, "Failed to upload image: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateProofInFirestore(proofText: String, imageUrl: String?) {
        val updates = hashMapOf<String, Any>(
            "proofOfDonationText" to proofText,
            "proofOfDonationImageUrl" to (imageUrl ?: "")
        )

        firestore.collection(collectionName).document(donationId)
            .update(updates)
            .addOnSuccessListener {
                // Permanently hide the "Proof of Donation" input section after successful submission
                tvProofOfDonationLabel.visibility = View.GONE
                etProofText.visibility = View.GONE
                ivProofImage.visibility = View.GONE
                btnUploadProofImage.visibility = View.GONE
                btnSaveProof.visibility = View.GONE

                // Clear any lingering data from the input fields
                etProofText.setText("")
                ivProofImage.setImageDrawable(null)
                selectedProofImageUri = null // Ensure internal URI is also cleared

                Toast.makeText(this, "Proof of donation saved successfully!", Toast.LENGTH_SHORT).show()
                // Do NOT call fetchDonationDetails() here, as the UI state has been specifically set.
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving proof to Firestore for ID: $donationId: ${e.message}", e)
                Toast.makeText(this, "Error saving proof: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Update donation status to approved/rejected and navigate to donation history
     * Uses your existing setup - keeps donations in same collections with updated status
     */
    private fun updateDonationStatusAndGoToHistory(newStatus: String) {
        // Add approval/rejection timestamp
        val actionTimestamp = com.google.firebase.Timestamp.now()
        val updates = hashMapOf<String, Any>(
            "status" to newStatus,
            "${newStatus}At" to actionTimestamp
        )
        
        firestore.collection(collectionName).document(donationId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Donation $newStatus successfully! Redirecting to donation history...", Toast.LENGTH_SHORT).show()
                
                // Navigate to donation history page
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("history_type", "donation")
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating donation status to '$newStatus' for ID: $donationId: ${e.message}", e)
                Toast.makeText(this, "Error updating status: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}