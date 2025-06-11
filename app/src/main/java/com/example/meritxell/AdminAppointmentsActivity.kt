package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminAppointmentsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var appointmentsLayout: LinearLayout
    private lateinit var backButton: ImageView

    private val appointmentDocIds = mutableMapOf<Appointment, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_appointments)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        supportActionBar?.hide()

        backButton = findViewById(R.id.btnBack)
        appointmentsLayout = findViewById(R.id.appointmentsLayout)

        backButton.setOnClickListener { finish() }

        loadAppointments()
    }

    private fun loadAppointments() {
        // First check and update expired appointments
        AppointmentStatusHelper.checkAndUpdateExpiredAppointments {
            // Then load only pending and accepted appointments for admin active view
        db.collection("appointments")
                .whereIn("status", listOf("pending", "accepted"))
            .get()
            .addOnSuccessListener { documents ->
                appointmentDocIds.clear()
                appointmentsLayout.removeAllViews()

                if (documents.isEmpty) {
                        Toast.makeText(this, "No active appointments available", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    val appointment = document.toObject(Appointment::class.java)
                    appointmentDocIds[appointment] = document.id
                    displayAppointment(appointment)
                        Log.d("AdminAppointments", "Loaded active appointment: $appointment")
                }
            }
            .addOnFailureListener { e ->
                Log.e("AdminAppointments", "Error loading appointments: ${e.message}")
                Toast.makeText(this, "Failed to load appointments", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun displayAppointment(appointment: Appointment) {
        val appointmentView = layoutInflater.inflate(R.layout.appointment_item, null)

        val appointmentDetails: TextView = appointmentView.findViewById(R.id.appointmentDetails)
        val appointmentStatus: TextView = appointmentView.findViewById(R.id.appointmentStatus)
        val checkboxAccept: CheckBox = appointmentView.findViewById(R.id.checkboxAccept)
        val btnCancel: TextView = appointmentView.findViewById(R.id.btnCancel)
        val usernameTextView: TextView = appointmentView.findViewById(R.id.usernameTextView)

        // Enhanced appointment details with time remaining
        val timeRemaining = AppointmentStatusHelper.getTimeRemaining(appointment.date ?: "", appointment.time ?: "")
        appointmentDetails.text = "${appointment.appointmentType}\n📅 ${appointment.date} at ${appointment.time}\n⏰ $timeRemaining"
        
        // Enhanced status with color coding
        appointmentStatus.text = "Status: ${appointment.status}"
        when (appointment.status) {
            "pending" -> appointmentStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            "accepted" -> appointmentStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            else -> appointmentStatus.setTextColor(getColor(android.R.color.black))
        }
        
        checkboxAccept.isChecked = appointment.status == "accepted"

        // Use username from appointment document if available
        val username = appointment.username ?: "Unknown User"
        usernameTextView.text = username

        usernameTextView.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", appointment.userId)
            startActivity(intent)
        }

        checkboxAccept.setOnCheckedChangeListener { _, isChecked ->
            val newStatus = if (isChecked) "accepted" else "pending"
            updateAppointmentStatus(appointment, newStatus, appointmentView)
        }

        btnCancel.setOnClickListener {
            updateAppointmentStatus(appointment, "cancelled", appointmentView)
        }

        // Check if appointment is expired and disable interactions
        if (AppointmentStatusHelper.isAppointmentExpired(appointment.date ?: "", appointment.time ?: "")) {
            checkboxAccept.isEnabled = false
            btnCancel.isEnabled = false
            appointmentView.alpha = 0.6f
        }

        appointmentsLayout.addView(appointmentView)
    }

    private fun updateAppointmentStatus(appointment: Appointment, status: String, appointmentView: View? = null) {
        val docId = appointmentDocIds[appointment]
        if (docId == null) {
            Toast.makeText(this, "Appointment not found", Toast.LENGTH_SHORT).show()
            Log.e("AdminAppointments", "Missing docId for appointment")
            return
        }

        val appointmentRef = db.collection("appointments").document(docId)

        // Always update status instead of deleting - this preserves appointments for history
        val updateData = mapOf(
            "status" to status,
            "cancelledAt" to if (status == "cancelled") com.google.firebase.firestore.FieldValue.serverTimestamp() else null,
            "cancelledBy" to if (status == "cancelled") "admin" else null
        ).filterValues { it != null } // Remove null values
        
        appointmentRef.update(updateData)
                .addOnSuccessListener {
                Toast.makeText(this, "Status updated to $status", Toast.LENGTH_SHORT).show()
                
                // Remove from admin view if cancelled or completed (but keep in database for history)
                if (status == "cancelled" || status == "completed") {
                    appointmentView?.let { appointmentsLayout.removeView(it) }
                    appointmentDocIds.remove(appointment)
                }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error updating status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    
    override fun onResume() {
        super.onResume()
        // Check for expired appointments and refresh the list when activity resumes
        loadAppointments()
    }
}
