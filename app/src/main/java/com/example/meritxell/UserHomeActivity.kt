package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import com.bumptech.glide.Glide

class UserHomeActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var usernameTextView: TextView
    private lateinit var lastNameTextView: TextView
    private lateinit var roleTextView: TextView
    private lateinit var profileImageView: ImageView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_home)

        supportActionBar?.hide()

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Initialize notification permission helper
        notificationPermissionHelper = NotificationPermissionHelper(this)

        // Initialize DrawerLayout and NavigationView
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Set up ActionBarDrawerToggle for the drawer button
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.open, R.string.close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize UI components for username, lastname, role, and profile image in the header
        val headerView = navView.getHeaderView(0)
        usernameTextView = headerView.findViewById(R.id.usernameTextView)
        lastNameTextView = headerView.findViewById(R.id.lastNameTextView)
        roleTextView = headerView.findViewById(R.id.roleTextView)
        profileImageView = headerView.findViewById(R.id.profileImageView)

        // Set click listeners for the username and profile image to navigate to UserProfileActivity
        usernameTextView.setOnClickListener { navigateToUserProfile() }
        profileImageView.setOnClickListener { navigateToUserProfile() }

        // Retrieve the user info from Firestore (including profile image)
        getUserInfo()

        // Handle navigation item clicks
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.ic_home -> {
                    loadHomeFragment()
                }
                R.id.ic_appointments -> {
                    val userRole = roleTextView.text.toString()
                    if (userRole == "admin") {
                        startActivity(Intent(this, AdminAppointmentsActivity::class.java))
                    } else {
                        loadAppointmentFragment()
                    }
                }
                R.id.ic_history -> {
                    loadHistoryFragment()
                }
                R.id.ic_matching -> { // <-- NEW Matching option
                    val userRole = roleTextView.text.toString()
                    if (userRole == "admin") {
                        // Admin will go to AdminMatchingActivity
                        startActivity(Intent(this, AdminMatchingActivity::class.java))
                    } else {
                        // Regular user will go to UserMatchingFragment
                        loadUserMatchingActivity()
                    }
                }
                R.id.ic_child_status -> {
                    startActivity(Intent(this, ChildStatusActivity::class.java))
                }
                R.id.ic_admin_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                }
                R.id.ic_logout -> {
                    logoutUser()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Load the HomeFragment initially
        loadHomeFragment()
        
        // Setup notifications after a short delay to allow UI to settle
        setupNotifications()
    }

    override fun onResume() {
        super.onResume()
        getUserInfo()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notificationPermissionHelper.handlePermissionResult(
            requestCode, 
            permissions, 
            grantResults,
            onPermissionGranted = {
                Toast.makeText(this, "✅ Notifications enabled! You'll receive important updates.", Toast.LENGTH_LONG).show()
            },
            onPermissionDenied = {
                Toast.makeText(this, "⚠️ You won't receive notifications about messages and updates.", Toast.LENGTH_LONG).show()
            }
        )
    }
    
    private fun setupNotifications() {
        // Check if notifications are enabled, and if not, show setup guide
        if (!notificationPermissionHelper.hasNotificationPermission()) {
            // Show notification setup guide for first-time users
            notificationPermissionHelper.showNotificationSetupGuide {
                // This callback runs after the user interacts with the permission dialog
            }
        }
    }

    private fun loadHomeFragment() {
        val homeFragment = HomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, homeFragment)
            .commit()
    }

    private fun loadAppointmentFragment() {
        val appointmentFragment = AppointmentFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, appointmentFragment)
            .commit()
    }

    private fun loadHistoryFragment() {
        val historyFragment = HistoryFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, historyFragment)
            .commit()
    }

    // NEW: Function to load the user's matching fragment
    private fun loadUserMatchingActivity() {
        val intent = Intent(this, UserMatchingActivity::class.java)
        startActivity(intent)
        // Optional: if you want to close the current activity when navigating to UserMatchingActivity, uncomment the line below:
        // finish()
    }

    private fun logoutUser() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToUserProfile() {
        val intent = Intent(this, UserProfileActivity::class.java)
        startActivity(intent)
    }

    private fun getUserInfo() {
        val user = auth.currentUser
        user?.let { firebaseUser ->
            db.collection("users").document(firebaseUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username") ?: "No username"
                        val lastName = document.getString("lastName") ?: "No lastname"
                        val role = document.getString("role") ?: "No role"
                        val profileImageUrl = document.getString("profileImageUrl")

                        usernameTextView.text = username
                        lastNameTextView.text = lastName
                        roleTextView.text = role

                        if (profileImageUrl != null && profileImageUrl.isNotEmpty()) {
                            Glide.with(this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .into(profileImageView)
                        } else {
                            profileImageView.setImageResource(R.drawable.ic_profile)
                        }

                        // Show/hide admin-only menu items
                        val childStatusMenuItem = navView.menu.findItem(R.id.ic_child_status)
                        val adminHistoryMenuItem = navView.menu.findItem(R.id.ic_admin_history)
                        
                        val isAdmin = (role == "admin")
                        childStatusMenuItem?.isVisible = isAdmin
                        adminHistoryMenuItem?.isVisible = isAdmin
                    } else {
                        Toast.makeText(this, "User data not found in Firestore.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to load user info: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show()
            logoutUser()
        }
    }
}