package com.example.meritxell

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ScheduleAppointmentActivity : AppCompatActivity() {

    private lateinit var btnDatePicker: Button
    private lateinit var tvDate: TextView
    private lateinit var spinnerTime: Spinner
    private lateinit var spinnerAppointmentType: Spinner // Changed from EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: ImageView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_appointment)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        supportActionBar?.title = null
        supportActionBar?.hide()

        btnDatePicker = findViewById(R.id.btnDatePicker)
        tvDate = findViewById(R.id.tvDate)
        spinnerTime = findViewById(R.id.spinnerTime)
        spinnerAppointmentType = findViewById(R.id.spinnerAppointmentType) // Initialize the new Spinner
        btnSubmit = findViewById(R.id.btnSubmit)
        btnBack = findViewById(R.id.btnBack)

        btnDatePicker.setOnClickListener {
            // Use Philippine Standard Time for date picker
            val philippineTimeZone = TimeZone.getTimeZone("Asia/Manila")
            val calendar = Calendar.getInstance(philippineTimeZone)
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    dateFormat.timeZone = philippineTimeZone
                    val date = Calendar.getInstance(philippineTimeZone)
                    date.set(year, month, dayOfMonth)
                    val formattedDate = dateFormat.format(date.time)
                    tvDate.text = formattedDate
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnSubmit.setOnClickListener {
            val appointmentType = spinnerAppointmentType.selectedItem?.toString() // Get selected item from Spinner
            val date = tvDate.text.toString()
            val time = spinnerTime.selectedItem?.toString()?.trim() ?: ""

            // Check for placeholder values and empty fields
            if (appointmentType.isNullOrEmpty() || appointmentType == "Select Appointment") {
                Toast.makeText(this, "Please select an appointment type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (date.isEmpty() || date == "Date and Time *") {
                Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (time.isEmpty() || time == "Select Time") {
                Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showConfirmationDialog(appointmentType, date, time)
        }
    }

    private fun showConfirmationDialog(appointmentType: String, date: String, time: String) {
        val message = """
            Appointment Confirmation
            Your appointment request has been successfully submitted. However, please note that all appointments are subject to review and approval by the administrators of the foundation. A final confirmation will be sent via email and SMS once your appointment has been verified. Until confirmed, your appointment remains pending and may be subject to rescheduling based on availability.
            Once your appointment is approved, you will receive a unique confirmation code via email. This code will be required to access your appointment schedule and any related details. Please ensure you keep this code secure, as it will be needed for verification on the day of your appointment.

        """.trimIndent()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Appointment Confirmation")
            .setMessage(message)
            .setPositiveButton("Submit") { _, _ ->
                btnSubmit.isEnabled = false
                checkExistingAppointmentAndSubmit(appointmentType, date, time)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun checkExistingAppointmentAndSubmit(appointmentType: String, date: String, time: String) {
        val userId = auth.currentUser?.uid

        db.collection("appointments")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    saveAppointmentToFirestore(appointmentType, date, time)
                    clearInputFields()
                } else {
                    Toast.makeText(this, "You already have a pending appointment", Toast.LENGTH_SHORT).show()
                    btnSubmit.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking appointments: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSubmit.isEnabled = true
            }
    }

    private fun saveAppointmentToFirestore(appointmentType: String, date: String, time: String) {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            btnSubmit.isEnabled = true
            return
        }

        // Use a consistent date format for parsing the selected date
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        // Format to match spinner time options like "8:00 AM", "8:05 PM", etc. (5-minute intervals for testing)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        try {
            // Use Philippine Standard Time for appointment scheduling
            val philippineTimeZone = TimeZone.getTimeZone("Asia/Manila")
            dateFormat.timeZone = philippineTimeZone
            timeFormat.timeZone = philippineTimeZone
            
            val parsedDate = dateFormat.parse(date)
            // Normalize time format: handle both old "8:00 A.M." and new "8:00 AM" formats
            val normalizedTime = time.replace("A.M.", "AM").replace("P.M.", "PM").trim()
            val parsedTime = timeFormat.parse(normalizedTime)

            if (parsedDate != null && parsedTime != null) {
                val calendarDate = Calendar.getInstance(philippineTimeZone)
                calendarDate.time = parsedDate

                val calendarTime = Calendar.getInstance(philippineTimeZone)
                calendarTime.time = parsedTime

                calendarDate.set(Calendar.HOUR_OF_DAY, calendarTime.get(Calendar.HOUR_OF_DAY))
                calendarDate.set(Calendar.MINUTE, calendarTime.get(Calendar.MINUTE))
                calendarDate.set(Calendar.SECOND, 0)
                calendarDate.set(Calendar.MILLISECOND, 0)

                val appointmentTimestamp = Timestamp(calendarDate.time)

                db.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val username = document.getString("username")
                            if (username.isNullOrEmpty()) {
                                Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show()
                                btnSubmit.isEnabled = true
                                return@addOnSuccessListener
                            }

                            val appointment = hashMapOf(
                                "userId" to userId,
                                "username" to username,
                                "appointmentType" to appointmentType, // This now comes from the Spinner
                                "date" to date,
                                "time" to time,
                                "status" to "pending",
                                "scheduledTimestamp" to appointmentTimestamp
                            )

                            db.collection("appointments")
                                .add(appointment)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this,
                                        "Appointment successfully submitted and pending approval.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    btnSubmit.isEnabled = true
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error scheduling appointment: ${e.message}", Toast.LENGTH_SHORT).show()
                                    btnSubmit.isEnabled = true
                                }

                        } else {
                            Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                            btnSubmit.isEnabled = true
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error fetching username: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnSubmit.isEnabled = true
                    }

            } else {
                Toast.makeText(this, "Invalid date or time format", Toast.LENGTH_SHORT).show()
                btnSubmit.isEnabled = true
            }
        } catch (e: Exception) {
            Log.e("AppointmentParseError", "Failed parsing date/time", e)
            Toast.makeText(this, "Error parsing date/time: ${e.message}", Toast.LENGTH_SHORT).show()
            btnSubmit.isEnabled = true
        }
    }

    private fun clearInputFields() {
        spinnerAppointmentType.setSelection(0) // Reset Spinner to first item
        tvDate.text = ""
        spinnerTime.setSelection(0)
    }
}