package com.example.meritxell

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View // Import View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import androidx.activity.result.contract.ActivityResultContracts

class UserProfileActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage

    private lateinit var firstNameTextView: TextView
    private lateinit var middleNameTextView: TextView
    private lateinit var lastNameTextView: TextView
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var birthdateTextView: TextView
    private lateinit var roleTextView: TextView

    private lateinit var headerUsernameTextView: TextView
    private lateinit var headerLastNameTextView: TextView
    private lateinit var headerRoleTextView: TextView
    private lateinit var profileImageView: ImageView
    private lateinit var backButton: ImageView
    private lateinit var uploadProfileImageBtn: ImageView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uploadImageToFirebaseStorage(it)
        } ?: run {
            Toast.makeText(this, "No image selected.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        // Header info
        headerUsernameTextView = findViewById(R.id.headerUsernameTextView)
        headerLastNameTextView = findViewById(R.id.headerLastNameTextView)
        headerRoleTextView = findViewById(R.id.headerRoleTextView)
        profileImageView = findViewById(R.id.profileImageView)
        backButton = findViewById(R.id.btnBack)
        uploadProfileImageBtn = findViewById(R.id.uploadProfileImageBtn)

        // Full info
        firstNameTextView = findViewById(R.id.firstNameTextView)
        middleNameTextView = findViewById(R.id.middleNameTextView)
        lastNameTextView = findViewById(R.id.lastNameTextView)
        usernameTextView = findViewById(R.id.usernameTextView)
        emailTextView = findViewById(R.id.emailTextView)
        birthdateTextView = findViewById(R.id.birthdateTextView)
        roleTextView = findViewById(R.id.roleTextView)

        backButton.setOnClickListener {
            finish()
        }

        loadUserData()
    }


    private fun loadUserData() {
        val intentUserId = intent.getStringExtra("userId")
        val currentUserUid = auth.currentUser?.uid // Get the ID of the currently logged-in user
        val targetUserId = intentUserId ?: currentUserUid // The profile ID we are trying to view

        if (targetUserId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            // If no user is logged in, hide the upload button and disable it
            uploadProfileImageBtn.visibility = View.GONE
            uploadProfileImageBtn.setOnClickListener(null) // Remove any existing listener
            return
        }

        // --- Conditional visibility for upload button ---
        if (currentUserUid == targetUserId) {
            // It's the current user's own profile, show and enable the upload button
            uploadProfileImageBtn.visibility = View.VISIBLE
            uploadProfileImageBtn.setOnClickListener {
                pickImage.launch("image/*") // Launch image picker
            }
            Log.d("UserProfileActivity", "Viewing own profile. Upload button enabled.")
        } else {
            // Viewing another user's profile (e.g., admin viewing user's profile)
            // Hide and disable the upload button
            uploadProfileImageBtn.visibility = View.GONE
            uploadProfileImageBtn.setOnClickListener(null) // Remove any existing listener
            Log.d("UserProfileActivity", "Viewing another user's profile. Upload button hidden.")
        }
        // --- End of conditional visibility ---


        db.collection("users").document(targetUserId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val firstName = document.getString("firstName") ?: "-"
                    val middleName = document.getString("middleName") ?: "-"
                    val lastName = document.getString("lastName") ?: "-"
                    val username = document.getString("username") ?: "-"
                    val email = document.getString("email") ?: "-"
                    val birthdate = document.getString("birthdate") ?: "-"
                    val role = document.getString("role") ?: "-"
                    val profileImageUrl = document.getString("profileImageUrl") // Get profile image URL

                    // Set header
                    headerUsernameTextView.text = username
                    headerLastNameTextView.text = lastName
                    headerRoleTextView.text = role

                    // Load profile image using Glide if URL exists
                    if (profileImageUrl != null && profileImageUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile) // Placeholder while loading
                            .error(R.drawable.ic_profile)       // Error image if loading fails
                            .into(profileImageView)
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_profile) // Default image
                    }

                    // Set detail fields
                    firstNameTextView.text = firstName
                    middleNameTextView.text = middleName
                    lastNameTextView.text = lastName
                    usernameTextView.text = username
                    emailTextView.text = email
                    birthdateTextView.text = birthdate
                    roleTextView.text = role
                } else {
                    Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserProfileActivity", "Error loading user data: ${e.message}", e)
                Toast.makeText(this, "Error loading user data.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImageToFirebaseStorage(imageUri: Uri) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val profileImageRef: StorageReference = storage.reference
            .child("profile_images/$userId/profile_${System.currentTimeMillis()}.jpg")

        Toast.makeText(this, "Uploading profile image...", Toast.LENGTH_SHORT).show()

        profileImageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                profileImageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    saveProfileImageUrlToFirestore(downloadUri.toString())
                }.addOnFailureListener { e ->
                    Log.e("UserProfileActivity", "Failed to get download URL: ${e.message}", e)
                    Toast.makeText(this, "Failed to get image URL.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserProfileActivity", "Profile image upload failed: ${e.message}", e)
                Toast.makeText(this, "Profile image upload failed.", Toast.LENGTH_SHORT).show()
            }
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                Log.d("UserProfileActivity", "Upload is $progress% done")
            }
    }

    private fun saveProfileImageUrlToFirestore(imageUrl: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) return

        db.collection("users").document(userId)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile image updated successfully!", Toast.LENGTH_SHORT).show()
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(profileImageView)
                Log.d("UserProfileActivity", "Profile image URL saved to Firestore: $imageUrl")
            }
            .addOnFailureListener { e ->
                Log.e("UserProfileActivity", "Failed to update profile image URL in Firestore: ${e.message}", e)
                Toast.makeText(this, "Failed to save image URL.", Toast.LENGTH_SHORT).show()
            }
    }
}