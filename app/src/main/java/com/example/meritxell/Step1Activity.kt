package com.example.meritxell

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Step1Activity : AppCompatActivity() {

    private lateinit var btnBack: View
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step1)

        supportActionBar?.title = null
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Find the Back button by its ID
        btnBack = findViewById(R.id.btnBack)

        // Set up back button functionality
        btnBack.setOnClickListener {
            onBackPressed()  // Navigate back to the previous screen
        }
        
        // Create automatic admin connection when user starts adoption process
        createAdoptionConnection()
    }
    
    private fun createAdoptionConnection() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            
            // Get username from Firestore
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username") ?: "Unknown User"
                        Log.d("Step1Activity", "Creating adoption connection for user: $username")
                        
                        // Create automatic messaging connection
                        MessagingConnectionHelper.createAdoptionConnection(userId, username)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Step1Activity", "Error getting username: ${e.message}")
                }
        }
    }
}
