package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.content.ContextCompat

class CreateAccountActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        val lastNameEditText = findViewById<EditText>(R.id.lastName)
        val firstNameEditText = findViewById<EditText>(R.id.firstName)
        val middleNameEditText = findViewById<EditText>(R.id.middleName)
        val usernameEditText = findViewById<EditText>(R.id.username)
        val birthdateEditText = findViewById<EditText>(R.id.birthdate)
        val emailEditText = findViewById<EditText>(R.id.email)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPassword)
        val createAccountButton = findViewById<Button>(R.id.loginButton)
        val passwordStrengthText = findViewById<TextView>(R.id.passwordStrength)
        val passwordRequirementsText = findViewById<TextView>(R.id.passwordRequirements) // Updated TextView

        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.GONE

        // Show password rules when password EditText is focused
        passwordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                passwordRequirementsText.visibility = View.VISIBLE // Show password requirements
            } else {
                passwordRequirementsText.visibility = View.GONE // Hide password requirements
            }
        }

        // Password strength indicator (unchanged)
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                val strength = getPasswordStrength(password)
                passwordStrengthText.text = strength
                passwordStrengthText.setTextColor(
                    when (strength) {
                        "Weak" -> getColor(R.color.red)
                        "Medium" -> getColor(R.color.orange)
                        "Strong" -> getColor(R.color.green)
                        else -> getColor(R.color.black)
                    }
                )
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        createAccountButton.setOnClickListener {
            val lastName = lastNameEditText.text.toString().trim()
            val firstName = firstNameEditText.text.toString().trim()
            val middleName = middleNameEditText.text.toString().trim()
            val username = usernameEditText.text.toString().trim()

            val birthdate = birthdateEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (lastName.isEmpty() || firstName.isEmpty() || middleName.isEmpty() ||
                username.isEmpty() || email.isEmpty() ||
                birthdate.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val passwordPattern = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#\$%^&*(),.?\":{}|<>]).{8,}\$")
            if (!password.matches(passwordPattern)) {
                Toast.makeText(this, "Password does not meet requirements", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            // Check if username already exists
            db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show()
                    } else {
                        // Proceed with Firebase Auth
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    user?.sendEmailVerification()
                                        ?.addOnCompleteListener { verificationTask ->
                                            if (verificationTask.isSuccessful) {
                                                val userData = hashMapOf(
                                                    "lastName" to lastName,
                                                    "firstName" to firstName,
                                                    "middleName" to middleName,
                                                    "username" to username,
                                                    "email" to email,
                                                    "birthdate" to birthdate,
                                                    "isVerified" to false,
                                                    "role" to "user"
                                                )
                                                user?.uid?.let { uid ->
                                                    db.collection("users").document(uid)
                                                        .set(userData)
                                                        .addOnSuccessListener {
                                                            Toast.makeText(
                                                                this,
                                                                "Verification email sent. Please verify before logging in.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            auth.signOut()
                                                            startActivity(Intent(this, LoginActivity::class.java))
                                                            finish()
                                                        }
                                                        .addOnFailureListener {
                                                            Toast.makeText(this, "Error saving user data", Toast.LENGTH_SHORT).show()
                                                            progressBar.visibility = View.GONE
                                                        }
                                                }
                                            } else {
                                                Toast.makeText(this, "Error sending verification email", Toast.LENGTH_SHORT).show()
                                                progressBar.visibility = View.GONE
                                            }
                                        }
                                } else {
                                    Toast.makeText(this, "Error creating account: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    progressBar.visibility = View.GONE
                                }
                            }
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to check username: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun getPasswordStrength(password: String): String {
        var score = 0
        if (password.length >= 8) score++
        if (password.matches(Regex(".*[A-Z].*"))) score++
        if (password.matches(Regex(".*[a-z].*"))) score++
        if (password.matches(Regex(".*\\d.*"))) score++
        if (password.matches(Regex(".*[!@#\$%^&*(),.?\":{}|<>].*"))) score++

        return when (score) {
            0, 1, 2 -> "Weak"
            3, 4 -> "Medium"
            5 -> "Strong"
            else -> ""
        }
    }
}
