package com.example.meritxell

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText // Corrected: No extra 'var' here
    private lateinit var passwordToggle: ImageButton
    private lateinit var loginButton: Button
    private lateinit var countdownText: TextView

    private var loginAttempts = 0
    private var isLockedOut = false
    private var isPasswordVisible = false
    private var timer: CountDownTimer? = null

    private val LOCKOUT_DURATION_MS = 3 * 60 * 1000L  // 3 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Configure Firebase for emulator if needed
        if (isEmulator()) {
            Log.d("LoginActivity", "Running in emulator - applying emulator-specific settings")
            // Uncomment the next line to use Firebase Auth emulator
            // auth.useEmulator("10.0.2.2", 9099)
            
            // Test network connectivity
            testNetworkConnectivity()
        }

        supportActionBar?.hide()

        // Initialize all views
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        passwordToggle = findViewById(R.id.passwordToggle) // This finds the eye icon button
        loginButton = findViewById(R.id.loginButton)
        countdownText = findViewById(R.id.countdownText)
        val signupLink = findViewById<TextView>(R.id.signupLink)
        val forgotPasswordLink = findViewById<TextView>(R.id.forgotPasswordLink)

        // This makes the "Login" button initially hidden
        loginButton.visibility = View.GONE

        signupLink.setOnClickListener {
            startActivity(Intent(this, CreateAccountActivity::class.java))
        }

        forgotPasswordLink.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Toast.makeText(this, "No account found with this email", Toast.LENGTH_SHORT).show()
                    } else {
                        val document = documents.documents[0]
                        val isVerified = document.getBoolean("isVerified") ?: false

                        if (!isVerified) {
                            Toast.makeText(this, "Email not verified. Please verify before resetting password.", Toast.LENGTH_LONG).show()
                        } else {
                            auth.sendPasswordResetEmail(email)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to check email: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        val prefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        val lockoutEndTime = prefs.getLong("lockoutEndTime", 0)
        if (System.currentTimeMillis() < lockoutEndTime) {
            startLockout(lockoutEndTime - System.currentTimeMillis())
        }

        // This sets up the click listener for the eye icon button,
        // it doesn't control its initial visibility.
        passwordToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        // Add TextWatchers to both EditTexts to control the *LOGIN BUTTON* visibility
        emailEditText.addTextChangedListener(loginTextWatcher)
        passwordEditText.addTextChangedListener(loginTextWatcher)

        loginButton.setOnClickListener {
            if (isLockedOut) return@setOnClickListener

            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("LoginActivity", "Manual login attempt for: $email")
            
            // Show loading indicator
            loginButton.isEnabled = false
            loginButton.text = "Logging in..."
            
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    // Re-enable button
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                    
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        user?.let {
                            if (!user.isEmailVerified) {
                                Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                                auth.signOut()
                                return@addOnCompleteListener
                            } else {
                                // Update verification status in Firestore
                                db.collection("users").document(user.uid)
                                    .update("isVerified", true)
                                    .addOnSuccessListener {
                                        Log.d("LoginActivity", "User verification status updated.")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w("LoginActivity", "Failed to update verification status", e)
                                    }
                            }

                            // Reset login attempts on successful login
                            loginAttempts = 0
                            getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                                .edit().remove("lockoutEndTime").apply()

                            db.collection("users").document(user.uid).get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        val role = document.getString("role")
                                        val intent = when (role) {
                                            "admin" -> Intent(this, UserHomeActivity::class.java)
                                            else -> Intent(this, UserHomeActivity::class.java)
                                        }
                                        Log.d("LoginActivity", "Login successful for user: ${user.email}")
                                        
                                        // Initialize notifications after successful login
                                        initializeNotificationsAfterLogin()
                                        
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        Toast.makeText(this, "No role found for this user", Toast.LENGTH_SHORT).show()
                                        Log.w("LoginActivity", "No user document found for UID: ${user.uid}")
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error retrieving user data", Toast.LENGTH_SHORT).show()
                                    Log.e("LoginActivity", "Error retrieving user data", e)
                                }
                        }
                    } else {
                        loginAttempts++
                        val errorMessage = task.exception?.message ?: "Unknown authentication error"
                        Log.w("LoginActivity", "Authentication failed: $errorMessage", task.exception)
                        
                        // Provide more specific error messages
                        val userFriendlyMessage = when {
                            errorMessage.contains("INVALID_LOGIN_CREDENTIALS") -> "Invalid email or password"
                            errorMessage.contains("USER_NOT_FOUND") -> "No account found with this email"
                            errorMessage.contains("WRONG_PASSWORD") -> "Incorrect password"
                            errorMessage.contains("TOO_MANY_REQUESTS") -> "Too many failed attempts. Please try again later"
                            errorMessage.contains("NETWORK_ERROR") -> "Network error. Please check your connection"
                            else -> "Authentication failed: $errorMessage"
                        }
                        
                        Toast.makeText(this, userFriendlyMessage, Toast.LENGTH_SHORT).show()
                        
                        if (loginAttempts >= 5) {
                            val lockoutEnd = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                            getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                                .edit().putLong("lockoutEndTime", lockoutEnd).apply()
                            startLockout(LOCKOUT_DURATION_MS)
                        }
                    }
                }
        }
    }

    private val loginTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            val emailHasText = emailEditText.text.toString().trim().isNotEmpty()
            val passwordHasText = passwordEditText.text.toString().trim().isNotEmpty()

            // This controls the *Login Button* visibility, NOT the eye icon
            loginButton.visibility = if (emailHasText && passwordHasText) View.VISIBLE else View.GONE
        }
    }

    private fun togglePasswordVisibility() {
        val selection = passwordEditText.selectionEnd

        if (isPasswordVisible) {
            // Hide password
            passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordToggle.setImageResource(R.drawable.ic_visibility_off)
        } else {
            // Show password
            passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordToggle.setImageResource(R.drawable.ic_visibility)
        }
        isPasswordVisible = !isPasswordVisible

        passwordEditText.setSelection(selection)
    }

    private fun startLockout(duration: Long) {
        isLockedOut = true
        loginButton.isEnabled = false
        countdownText.visibility = View.VISIBLE

        timer?.cancel()
        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                countdownText.text = String.format("Too many failed attempts. Try again in %d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                isLockedOut = false
                loginAttempts = 0
                loginButton.isEnabled = true
                countdownText.visibility = View.GONE

                getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                    .edit().remove("lockoutEndTime").apply()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
    
    /**
     * Check if running in emulator
     */
    private fun isEmulator(): Boolean {
        return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator")
    }
    
    private fun testNetworkConnectivity() {
        Thread {
            try {
                val connection = java.net.URL("https://www.google.com").openConnection()
                connection.connectTimeout = 5000
                connection.connect()
                runOnUiThread {
                    Log.d("LoginActivity", "Network connectivity: OK")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("LoginActivity", "Network connectivity: FAILED - ${e.message}")
                    Toast.makeText(this, "Network connectivity issue detected", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    
    /**
     * Initialize notifications after successful login
     */
    private fun initializeNotificationsAfterLogin() {
        try {
            val notificationManager = NotificationManagerHelper.getInstance(this)
            notificationManager.initializeNotifications()
            Log.d("LoginActivity", "Notifications initialized after login")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Failed to initialize notifications after login: ${e.message}")
        }
    }
}