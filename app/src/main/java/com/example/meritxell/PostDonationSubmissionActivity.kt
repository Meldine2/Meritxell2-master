package com.example.meritxell

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class PostDonationSubmissionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var tvDonationInfo: TextView
    private lateinit var etComments: EditText
    private lateinit var llPhotoContainer: LinearLayout
    private lateinit var btnAddPhoto: Button
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInstructions: TextView

    private var donationId: String = ""
    private var donationType: String = ""
    private var donationAmount: String = ""
    private var approvalDate: String = ""

    private val selectedPhotos = mutableListOf<Uri>()
    private val photoNames = mutableListOf<String>()

    private var currentPhotoPath: String? = null

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1001
        private const val REQUEST_IMAGE_PICK = 1002
        private const val CAMERA_PERMISSION_REQUEST = 2001
        private const val MAX_PHOTOS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_donation_submission)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        initViews()
        loadDonationInfo()
        setupButtons()
    }

    private fun initViews() {
        tvDonationInfo = findViewById(R.id.tvDonationInfo)
        etComments = findViewById(R.id.etComments)
        llPhotoContainer = findViewById(R.id.llPhotoContainer)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        btnSubmit = findViewById(R.id.btnSubmit)
        progressBar = findViewById(R.id.progressBar)
        tvInstructions = findViewById(R.id.tvInstructions)
    }

    private fun loadDonationInfo() {
        donationId = intent.getStringExtra("donationId") ?: ""
        donationType = intent.getStringExtra("donationType") ?: ""
        donationAmount = intent.getStringExtra("donationAmount") ?: ""
        approvalDate = intent.getStringExtra("approvalDate") ?: ""

        if (donationId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid donation information", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        displayDonationInfo()
    }

    private fun displayDonationInfo() {
        val donationInfo = buildString {
            appendLine("Donation ID: $donationId")
            appendLine("Type: $donationType")
            if (donationAmount.isNotEmpty()) {
                appendLine("Details: $donationAmount")
            }
            appendLine("Approved on: $approvalDate")
        }
        
        tvDonationInfo.text = donationInfo

        // Set specific instructions based on donation type
        val instructions = when (donationType.lowercase()) {
            "food", "meals" -> "Please upload photos showing the food items or meals you donated. Include any preparation process if applicable."
            "clothing" -> "Please upload photos of the clothing items donated. Show the condition and types of garments."
            "toys" -> "Please upload photos of the toys or play items donated. Show their condition and variety."
            "medical supplies", "medicine" -> "Please upload photos of the medical supplies or medicines donated. Ensure all labels are visible."
            "educational materials", "books" -> "Please upload photos of books, stationery, or educational materials donated."
            else -> "Please upload photos showing the items you donated and share any additional comments about your donation experience."
        }
        
        tvInstructions.text = instructions
    }

    private fun setupButtons() {
        btnAddPhoto.setOnClickListener {
            if (selectedPhotos.size >= MAX_PHOTOS) {
                Toast.makeText(this, "Maximum $MAX_PHOTOS photos allowed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPhotoSelectionDialog()
        }

        btnSubmit.setOnClickListener {
            if (validateSubmission()) {
                submitDonationEvidence()
            }
        }
    }

    private fun showPhotoSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Add Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickFromGallery()
                }
            }
            .show()
    }

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile = createImageFile()
            photoFile?.let {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this, 
                    "${packageName}.fileprovider", 
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }

    private fun pickFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir("Pictures")
            File.createTempFile("DONATION_${timeStamp}_", ".jpg", storageDir).apply {
                currentPhotoPath = absolutePath
            }
        } catch (ex: IOException) {
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    currentPhotoPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val uri = Uri.fromFile(file)
                            addPhotoToContainer(uri)
                        }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        addPhotoToContainer(uri)
                    }
                }
            }
        }
    }

    private fun addPhotoToContainer(uri: Uri) {
        if (selectedPhotos.size >= MAX_PHOTOS) {
            Toast.makeText(this, "Maximum $MAX_PHOTOS photos allowed", Toast.LENGTH_SHORT).show()
            return
        }

        selectedPhotos.add(uri)
        
        val photoCard = createPhotoCard(uri, selectedPhotos.size - 1)
        llPhotoContainer.addView(photoCard)
        
        // Update add photo button text
        btnAddPhoto.text = "Add Photo (${selectedPhotos.size}/$MAX_PHOTOS)"
        
        if (selectedPhotos.size >= MAX_PHOTOS) {
            btnAddPhoto.isEnabled = false
            btnAddPhoto.text = "Maximum photos reached"
        }
    }

    private fun createPhotoCard(uri: Uri, index: Int): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            radius = 12f
            cardElevation = 4f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        // Photo thumbnail
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(context, R.drawable.rounded_image_background)
        }
        
        // Load thumbnail
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val thumbnail = getRotatedBitmap(bitmap, uri)
            imageView.setImageBitmap(thumbnail)
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.ic_photo_placeholder)
        }

        // Photo info
        val infoLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 16, 0)
        }

        val tvPhotoName = TextView(this).apply {
            text = "Photo ${index + 1}"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        val tvPhotoSize = TextView(this).apply {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val size = inputStream?.available() ?: 0
                text = "Size: ${formatFileSize(size)}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.grey_500))
            } catch (e: Exception) {
                text = "Size: Unknown"
            }
        }

        infoLayout.addView(tvPhotoName)
        infoLayout.addView(tvPhotoSize)

        // Remove button
        val btnRemove = Button(this).apply {
            text = "Remove"
            textSize = 12f
            background = ContextCompat.getDrawable(context, R.drawable.rounded_button_red)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                removePhoto(index)
            }
        }

        layout.addView(imageView)
        layout.addView(infoLayout)
        layout.addView(btnRemove)
        card.addView(layout)

        return card
    }

    private fun getRotatedBitmap(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun removePhoto(index: Int) {
        if (index < selectedPhotos.size) {
            selectedPhotos.removeAt(index)
            refreshPhotoContainer()
        }
    }

    private fun refreshPhotoContainer() {
        llPhotoContainer.removeAllViews()
        
        for (i in selectedPhotos.indices) {
            val photoCard = createPhotoCard(selectedPhotos[i], i)
            llPhotoContainer.addView(photoCard)
        }
        
        // Update button
        btnAddPhoto.text = "Add Photo (${selectedPhotos.size}/$MAX_PHOTOS)"
        btnAddPhoto.isEnabled = selectedPhotos.size < MAX_PHOTOS
        
        if (selectedPhotos.size < MAX_PHOTOS) {
            btnAddPhoto.text = "Add Photo (${selectedPhotos.size}/$MAX_PHOTOS)"
        }
    }

    private fun validateSubmission(): Boolean {
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(this, "Please add at least one photo", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etComments.text.toString().trim().length < 10) {
            Toast.makeText(this, "Please provide more detailed comments (at least 10 characters)", Toast.LENGTH_SHORT).show()
            etComments.requestFocus()
            return false
        }

        return true
    }

    private fun submitDonationEvidence() {
        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false
        btnAddPhoto.isEnabled = false

        val comments = etComments.text.toString().trim()
        val submissionId = "${donationId}_evidence_${System.currentTimeMillis()}"
        
        uploadPhotos(submissionId) { photoUrls ->
            if (photoUrls.isNotEmpty()) {
                saveSubmissionToFirestore(submissionId, comments, photoUrls)
            } else {
                progressBar.visibility = View.GONE
                btnSubmit.isEnabled = true
                btnAddPhoto.isEnabled = selectedPhotos.size < MAX_PHOTOS
                Toast.makeText(this, "Failed to upload photos. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uploadPhotos(submissionId: String, callback: (List<String>) -> Unit) {
        val photoUrls = mutableListOf<String>()
        val uploadTasks = mutableListOf<Boolean>()
        
        for (i in selectedPhotos.indices) {
            uploadTasks.add(false)
        }

        selectedPhotos.forEachIndexed { index, uri ->
            val fileName = "${submissionId}_photo_${index + 1}.jpg"
            val photoRef = storage.reference.child("donation_evidence/$fileName")
            
            // Compress image before upload
            val compressedImage = compressImage(uri)
            
            photoRef.putBytes(compressedImage)
                .addOnSuccessListener {
                    photoRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        photoUrls.add(downloadUrl.toString())
                        uploadTasks[index] = true
                        
                        if (uploadTasks.all { it }) {
                            callback(photoUrls)
                        }
                    }.addOnFailureListener {
                        uploadTasks[index] = true
                        if (uploadTasks.all { it }) {
                            callback(photoUrls)
                        }
                    }
                }
                .addOnFailureListener {
                    uploadTasks[index] = true
                    if (uploadTasks.all { it }) {
                        callback(photoUrls)
                    }
                }
        }
    }

    private fun compressImage(uri: Uri): ByteArray {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val rotatedBitmap = getRotatedBitmap(bitmap, uri)
            
            val outputStream = ByteArrayOutputStream()
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    private fun saveSubmissionToFirestore(submissionId: String, comments: String, photoUrls: List<String>) {
        val currentUserId = auth.currentUser?.uid ?: ""
        
        val submissionData = hashMapOf(
            "submissionId" to submissionId,
            "donationId" to donationId,
            "userId" to currentUserId,
            "donationType" to donationType,
            "donationAmount" to donationAmount,
            "comments" to comments,
            "photoUrls" to photoUrls,
            "photoCount" to photoUrls.size,
            "submissionTimestamp" to com.google.firebase.Timestamp.now(),
            "status" to "submitted",
            "reviewStatus" to "pending"
        )

        db.collection("donation_evidence")
            .document(submissionId)
            .set(submissionData)
            .addOnSuccessListener {
                // Update original donation record
                updateDonationRecord(submissionId)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSubmit.isEnabled = true
                btnAddPhoto.isEnabled = selectedPhotos.size < MAX_PHOTOS
                Toast.makeText(this, "Failed to save submission: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateDonationRecord(submissionId: String) {
        db.collection("donations")
            .document(donationId)
            .update(
                "evidenceSubmitted", true,
                "evidenceSubmissionId", submissionId,
                "evidenceSubmissionDate", com.google.firebase.Timestamp.now()
            )
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                showSuccessDialog()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Submission saved but failed to update donation record: ${e.message}", Toast.LENGTH_LONG).show()
                showSuccessDialog()
            }
    }

    private fun showSuccessDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Submission Successful!")
            .setMessage("Thank you for submitting evidence of your donation. Your submission is now under review by our team.")
            .setPositiveButton("OK") { _, _ ->
                setResult(Activity.RESULT_OK)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
} 