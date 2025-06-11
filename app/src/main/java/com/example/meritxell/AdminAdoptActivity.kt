package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.min

class AdminAdoptActivity : AppCompatActivity() {

    private lateinit var usersContainer: LinearLayout
    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var btnBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_adopt)

        supportActionBar?.hide()

        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        usersContainer = findViewById(R.id.usersContainer)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            onBackPressed()
        }

        fetchUsers()
    }

    private fun fetchUsers() {
        usersContainer.removeAllViews()

        db.collection("adoption_progress")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.let { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            Toast.makeText(this, "No active adoption processes found.", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }
                        
                        var activeUsersCount = 0
                        for (document in querySnapshot) {
                            val uid = document.id
                            val usernameFromFirestore = document.getString("username")

                            // REQUIREMENT: "Admins can no longer see completed adoption processes in the main view"
                            // Check if adoption process is completed (step 10 finished)
                            checkAdoptionStatus(uid) { isCompleted ->
                                if (!isCompleted) {
                            val userActualUsername = if (!usernameFromFirestore.isNullOrEmpty()) {
                                usernameFromFirestore
                            } else {
                                "User: " + uid.substring(0, min(uid.length, 6)) + "..."
                            }

                            val user = User(uid, userActualUsername)
                            addUserCard(user)
                                    activeUsersCount++
                                }
                                
                                // Show message if no active processes after checking all
                                if (activeUsersCount == 0 && document == querySnapshot.documents.last()) {
                                    runOnUiThread {
                                        Toast.makeText(this, "No active adoption processes found. Completed processes are in history.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    } ?: run {
                        Toast.makeText(this, "No user data available.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Error fetching users: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    /**
     * Check if adoption process is completed (step 10 finished)
     * REQUIREMENT: Hide completed adoption processes from admin main view
     */
    private fun checkAdoptionStatus(userId: String, callback: (Boolean) -> Unit) {
        db.collection("adoption_progress").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val step10Status = document.getString("step10_status")
                    val isCompleted = step10Status == "completed"
                    callback(isCompleted)
                } else {
                    callback(false) // No document means not completed
                }
            }
            .addOnFailureListener {
                callback(false) // On error, assume not completed to be safe
            }
    }

    private fun addUserCard(user: User) {
        val inflater = LayoutInflater.from(this)
        val userCard = inflater.inflate(R.layout.item_user, usersContainer, false) as LinearLayout

        val userDisplayName = userCard.findViewById<TextView>(R.id.userDisplayName)
        val seeProfileLink = userCard.findViewById<TextView>(R.id.seeProfileLink)
        val seeProgressLink = userCard.findViewById<TextView>(R.id.seeProgressLink)

        userDisplayName.text = user.username ?: "N/A"

        seeProfileLink.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", user.uid)
            startActivity(intent)
        }
        seeProgressLink.setOnClickListener {
            val intent = Intent(this, UserProgressDetailActivity::class.java)
            intent.putExtra("userId", user.uid)
            intent.putExtra("username", user.username)
            startActivity(intent)
        }

        usersContainer.addView(userCard)
    }

    data class User(
        val uid: String,
        val username: String?
    )

    companion object {
        private const val TAG = "AdminAdoptActivity"
    }
}