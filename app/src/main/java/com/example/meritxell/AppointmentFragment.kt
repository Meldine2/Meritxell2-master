package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppointmentFragment : Fragment(R.layout.fragment_appointments) {

    private lateinit var btnGetStarted: Button
    private lateinit var btnViewAppointment: Button
    private lateinit var termsCheckBox: CheckBox
    private lateinit var btnBack: ImageView  // Back button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()


        // Initialize views
        btnGetStarted = view.findViewById(R.id.btnGetStarted)
        btnViewAppointment = view.findViewById(R.id.btnViewAppointment)
        termsCheckBox = view.findViewById(R.id.termsCheckbox)
        btnBack = view.findViewById(R.id.btnBack)  // Back button

        // Set up the "Get Started" button to be enabled only if the CheckBox is checked
        btnGetStarted.isEnabled = false  // Initially disable the button

        // Enable the "Get Started" button when the CheckBox is checked
        termsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            btnGetStarted.isEnabled = isChecked
        }

        // Set click listener for "Get Started" button
        btnGetStarted.setOnClickListener {
            if (termsCheckBox.isChecked) {
                // Navigate to Schedule Appointment activity
                navigateToScheduleAppointment()
            } else {
                Toast.makeText(requireContext(), "Please agree to the terms and conditions", Toast.LENGTH_SHORT).show()
            }
        }

        // Set click listener for "View Appointment" button
        btnViewAppointment.setOnClickListener {
            // Navigate to View Appointment activity
            navigateToViewAppointment()
        }

        // Set click listener for the back button to navigate back to HomeFragment
        btnBack.setOnClickListener {
            // Navigate back to the HomeFragment
            loadHomeFragment()
        }

        // Check the role of the current user
        checkUserRole()
    }

    // Method to navigate to the "HomeFragment"
    private fun loadHomeFragment() {
        val homeFragment = HomeFragment()  // Initialize HomeFragment
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, homeFragment)  // Replace current fragment with HomeFragment
            .commit()  // Commit the transaction
    }

    // Method to check the role of the current user and restrict access
    private fun checkUserRole() {
        val user = auth.currentUser
        user?.let {
            // Retrieve the user role from Firestore
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val role = document.getString("role") ?: "No role"

                        if (role == "admin") {
                            // Show a message for admins and disable the buttons
                            btnGetStarted.isEnabled = false
                            btnViewAppointment.isEnabled = false
                            Toast.makeText(requireContext(), "Admins cannot access this section", Toast.LENGTH_SHORT).show()
                        } else {
                            // Allow users to access the appointment features
                            btnGetStarted.isEnabled = true
                            btnViewAppointment.isEnabled = true
                        }
                    }
                }
                .addOnFailureListener {
                    // Handle error (e.g., failed to retrieve user data)
                    Toast.makeText(requireContext(), "Error loading user data", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Method to navigate to the "Schedule Appointment" activity
    private fun navigateToScheduleAppointment() {
        val intent = Intent(requireContext(), ScheduleAppointmentActivity::class.java)
        startActivity(intent)
    }

    // Method to navigate to the "View Appointment" activity
    private fun navigateToViewAppointment() {
        val intent = Intent(requireContext(), ViewAppointmentActivity::class.java)
        startActivity(intent)
    }
}
