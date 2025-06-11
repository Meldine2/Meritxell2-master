package com.example.meritxell

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.Timestamp // Import Timestamp

class ViewAppointmentActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var recyclerViewAppointments: RecyclerView
    private lateinit var tvNoAppointments: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var appointmentsAdapter: AppointmentsAdapter
    private val appointmentsList = mutableListOf<Appointment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_appointment)

        // Initialize Firebase Firestore and Auth
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        // Initialize views
        btnBack = findViewById(R.id.btnBack)
        recyclerViewAppointments = findViewById(R.id.recyclerViewAppointments)
        tvNoAppointments = findViewById(R.id.tvNoAppointments)

        // Set up RecyclerView
        // The onCancelClick now defaults to 'user' as the canceller, as 'admin_cancelled' status is being removed.
        appointmentsAdapter = AppointmentsAdapter(appointmentsList) { appointment ->
            cancelAppointment(appointment, "user") // Assume user initiated cancel in this activity
        }
        recyclerViewAppointments.layoutManager = LinearLayoutManager(this)
        recyclerViewAppointments.adapter = appointmentsAdapter

        // Set up the Back button to go back to the previous screen (AppointmentFragment)
        btnBack.setOnClickListener {
            onBackPressed()  // This will return to the previous screen (AppointmentFragment)
        }

        // Load all appointment details for the current user
        loadAllAppointments()
    }

    private fun loadAllAppointments() {
        // Get the current user's UID
        val userId = auth.currentUser?.uid
        if (userId != null) {
            // Fetch ALL appointment details from Firestore
            db.collection("appointments")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { result ->
                    appointmentsList.clear()

                    if (!result.isEmpty) {
                        for (document in result.documents) {
                            try {
                                val appointment = Appointment(
                                    id = document.id,
                                    appointmentType = document.getString("appointmentType") ?: "Unknown",
                                    date = document.getString("date") ?: "Unknown",
                                    time = document.getString("time") ?: "Unknown",
                                    status = document.getString("status") ?: "Unknown",
                                    userId = document.getString("userId") ?: "",
                                    username = document.getString("username") ?: "Unknown",
                                    cancelledBy = document.getString("cancelledBy") ?: ""
                                )
                                appointmentsList.add(appointment)
                            } catch (e: Exception) {
                                Log.e("ViewAppointment", "Error parsing appointment: ${e.message}")
                            }
                        }

                        // No specific sorting needed for 'admin_cancelled' as it's removed
                        appointmentsAdapter.notifyDataSetChanged()
                        recyclerViewAppointments.visibility = View.VISIBLE
                        tvNoAppointments.visibility = View.GONE
                    } else {
                        recyclerViewAppointments.visibility = View.GONE
                        tvNoAppointments.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ViewAppointment", "Error loading appointments: ${e.message}")
                    Toast.makeText(this, "Error loading appointments: ${e.message}", Toast.LENGTH_SHORT).show()
                    recyclerViewAppointments.visibility = View.GONE
                    tvNoAppointments.visibility = View.VISIBLE
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    // Modify cancelAppointment to always set status to 'cancelled'
    private fun cancelAppointment(appointment: Appointment, cancelledBy: String) {
        // Always set the status to "cancelled"
        val updates = mapOf(
            "status" to "cancelled", // Always set to "cancelled"
            "cancelledAt" to Timestamp.now(),
            "cancelledBy" to cancelledBy // Still useful to know who initiated it for history
        )

        db.collection("appointments").document(appointment.id)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Appointment cancelled successfully.", Toast.LENGTH_LONG).show()
                // Reload appointments to reflect the change
                loadAllAppointments()
            }
            .addOnFailureListener { e ->
                Log.e("ViewAppointment", "Error cancelling appointment: ${e.message}")
                Toast.makeText(this, "Error cancelling appointment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Data class for Appointment
    data class Appointment(
        val id: String,
        val appointmentType: String,
        val date: String,
        val time: String,
        val status: String,
        val userId: String,
        val username: String,
        val cancelledBy: String = "" // Keep this field for historical tracking if needed
    )

    // RecyclerView Adapter for Appointments
    class AppointmentsAdapter(
        private val appointments: List<Appointment>,
        private val onCancelClick: (Appointment) -> Unit
    ) : RecyclerView.Adapter<AppointmentsAdapter.AppointmentViewHolder>() {

        class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvAppointmentType: TextView = itemView.findViewById(R.id.tvAppointmentType)
            val tvAppointmentStatus: TextView = itemView.findViewById(R.id.tvAppointmentStatus)
            val tvAppointmentDate: TextView = itemView.findViewById(R.id.tvAppointmentDate)
            val tvAppointmentTime: TextView = itemView.findViewById(R.id.tvAppointmentTime)
            val btnCancelAppointment: Button = itemView.findViewById(R.id.btnCancelAppointment)
            val layoutActionButtons: LinearLayout = itemView.findViewById(R.id.layoutActionButtons)
            val tvAdminMessage: TextView = itemView.findViewById(R.id.tvAdminMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_appointment, parent, false)
            return AppointmentViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
            val appointment = appointments[position]

            holder.tvAppointmentType.text = appointment.appointmentType
            holder.tvAppointmentDate.text = "Date: ${appointment.date}"
            holder.tvAppointmentTime.text = "Time: ${appointment.time}"
            holder.tvAppointmentStatus.text = appointment.status.uppercase()

            // Set status color and visibility of admin message based on appointment status
            when (appointment.status.lowercase()) {
                "pending" -> {
                    holder.tvAppointmentStatus.setBackgroundColor(0xFFFF9800.toInt()) // Orange
                    holder.tvAppointmentStatus.setTextColor(0xFFFFFFFF.toInt()) // White
                    holder.tvAdminMessage.visibility = View.GONE // Hide admin message
                }
                "accepted" -> {
                    holder.tvAppointmentStatus.setBackgroundColor(0xFF4CAF50.toInt()) // Green
                    holder.tvAppointmentStatus.setTextColor(0xFFFFFFFF.toInt()) // White
                    holder.tvAdminMessage.visibility = View.GONE // Hide admin message
                }
                "completed" -> {
                    holder.tvAppointmentStatus.setBackgroundColor(0xFF2196F3.toInt()) // Blue
                    holder.tvAppointmentStatus.setTextColor(0xFFFFFFFF.toInt()) // White
                    holder.tvAdminMessage.visibility = View.GONE // Hide admin message
                }
                "cancelled" -> { // Now handles all cancellations
                    holder.tvAppointmentStatus.setBackgroundColor(0xFFF44336.toInt()) // Red
                    holder.tvAppointmentStatus.setTextColor(0xFFFFFFFF.toInt()) // White
                    // Show "Admin request to reschedule" for any "cancelled" status
                    holder.tvAdminMessage.text = "Admin request to reschedule"
                    holder.tvAdminMessage.visibility = View.VISIBLE
                }
                "expired" -> {
                    holder.tvAppointmentStatus.setBackgroundColor(0xFF9E9E9E.toInt()) // Gray
                    holder.tvAppointmentStatus.setTextColor(0xFFFFFFFF.toInt()) // White
                    holder.tvAdminMessage.visibility = View.GONE // Hide admin message
                }
                else -> {
                    holder.tvAppointmentStatus.setBackgroundColor(0xFF6EC6FF.toInt()) // Default blue
                    holder.tvAppointmentStatus.setTextColor(0xFFFFFFFF.toInt()) // White
                    holder.tvAdminMessage.visibility = View.GONE // Hide admin message
                }
            }

            // Show cancel button only for pending and accepted appointments
            if (appointment.status.lowercase() in listOf("pending", "accepted")) {
                holder.layoutActionButtons.visibility = View.VISIBLE
                holder.btnCancelAppointment.setOnClickListener {
                    onCancelClick(appointment)
                }
            } else {
                holder.layoutActionButtons.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = appointments.size
    }
}