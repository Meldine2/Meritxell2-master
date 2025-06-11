package com.example.meritxell

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for managing appointment status updates
 * Automatically converts appointments to "completed" when their scheduled time passes
 */
class AppointmentStatusHelper {
    
    companion object {
        private const val TAG = "AppointmentStatusHelper"
        
        /**
         * Check and update expired appointments to "completed" status
         * Call this method periodically or when loading appointments
         */
        fun checkAndUpdateExpiredAppointments(onComplete: () -> Unit = {}) {
            val firestore = FirebaseFirestore.getInstance()
            // Use Philippine Standard Time for current time comparison
            val philippineTimeZone = TimeZone.getTimeZone("Asia/Manila")
            val currentTime = Calendar.getInstance(philippineTimeZone).timeInMillis
            
            Log.d(TAG, "Checking for expired appointments using Philippine time...")
            
            firestore.collection("appointments")
                .whereIn("status", listOf("pending", "accepted"))
                .get()
                .addOnSuccessListener { documents ->
                    val batch = firestore.batch()
                    var updatedCount = 0
                    
                    for (document in documents) {
                        val appointmentDate = document.getString("date") ?: continue
                        val appointmentTime = document.getString("time") ?: continue
                        val status = document.getString("status") ?: continue
                        
                        // Parse appointment date and time using Philippine timezone
                        val appointmentDateTime = parseAppointmentDateTime(appointmentDate, appointmentTime)
                        
                        if (appointmentDateTime != null && appointmentDateTime < currentTime) {
                            // Appointment time has passed, update to completed
                            val docRef = firestore.collection("appointments").document(document.id)
                            batch.update(docRef, mapOf(
                                "status" to "completed",
                                "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "autoCompleted" to true,
                                "completedBy" to "system"
                            ))
                            updatedCount++
                            
                            Log.d(TAG, "Marking appointment ${document.id} as completed (was: $status) - Philippine time")
                        }
                    }
                    
                    if (updatedCount > 0) {
                        batch.commit()
                            .addOnSuccessListener {
                                Log.d(TAG, "Successfully updated $updatedCount expired appointments to completed")
                                onComplete()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to update expired appointments: ${e.message}")
                                onComplete()
                            }
                    } else {
                        Log.d(TAG, "No expired appointments found")
                        onComplete()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking expired appointments: ${e.message}")
                    onComplete()
                }
        }
        
        /**
         * Parse appointment date and time into milliseconds using Philippine Standard Time
         * Expected format: date="12/12/2024", time="8:00 AM" or "8:05 PM" (5-minute intervals for testing)
         */
        private fun parseAppointmentDateTime(date: String, time: String): Long? {
            return try {
                // Clean up the time format - handle both old and new formats
                val cleanTime = time.replace("A.M.", "AM").replace("P.M.", "PM").trim()
                val dateTimeString = "$date $cleanTime"
                
                // Use Philippine Standard Time for parsing
                val philippineTimeZone = TimeZone.getTimeZone("Asia/Manila")
                
                // Try different date formats that might be used
                val formats = listOf(
                    "dd/MM/yyyy h:mm a",    // 12/12/2024 8:00 AM (primary format from date picker)
                    "dd/MM/yyyy h:mm a",    // 12/12/2024 8:15 AM (with minutes)
                    "MMM dd, yyyy h:mm a",  // Dec 12, 2024 8:00 AM
                    "MMM d, yyyy h:mm a",   // Dec 5, 2024 8:00 AM
                    "MMMM dd, yyyy h:mm a", // December 12, 2024 8:00 AM
                    "yyyy-MM-dd h:mm a",    // 2024-12-12 8:00 AM
                    "MM/dd/yyyy h:mm a"     // 12/12/2024 8:00 AM (US format)
                )
                
                for (format in formats) {
                    try {
                        val sdf = SimpleDateFormat(format, Locale.getDefault())
                        sdf.timeZone = philippineTimeZone // Set Philippine timezone
                        val parsedDate = sdf.parse(dateTimeString)
                        if (parsedDate != null) {
                            Log.d(TAG, "Successfully parsed appointment time: $dateTimeString -> ${parsedDate.time} (Philippine time)")
                            return parsedDate.time
                        }
                    } catch (e: Exception) {
                        // Try next format
                        continue
                    }
                }
                
                Log.w(TAG, "Could not parse appointment date/time: '$date' '$time'")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing appointment date/time: ${e.message}")
                null
            }
        }
        
        /**
         * Check if a specific appointment is expired using Philippine Standard Time
         */
        fun isAppointmentExpired(date: String, time: String): Boolean {
            val appointmentDateTime = parseAppointmentDateTime(date, time)
            val philippineTimeZone = TimeZone.getTimeZone("Asia/Manila")
            val currentTime = Calendar.getInstance(philippineTimeZone).timeInMillis
            return appointmentDateTime != null && appointmentDateTime < currentTime
        }
        
        /**
         * Get time remaining for an appointment in a readable format using Philippine Standard Time
         */
        fun getTimeRemaining(date: String, time: String): String {
            val appointmentDateTime = parseAppointmentDateTime(date, time)
            if (appointmentDateTime == null) return "Unknown"
            
            val philippineTimeZone = TimeZone.getTimeZone("Asia/Manila")
            val currentTime = Calendar.getInstance(philippineTimeZone).timeInMillis
            val timeDiff = appointmentDateTime - currentTime
            
            return if (timeDiff <= 0) {
                "Expired"
            } else {
                val days = timeDiff / (24 * 60 * 60 * 1000)
                val hours = (timeDiff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                val minutes = (timeDiff % (60 * 60 * 1000)) / (60 * 1000)
                
                when {
                    days > 0 -> "${days}d ${hours}h remaining"
                    hours > 0 -> "${hours}h ${minutes}m remaining"
                    else -> "${minutes}m remaining"
                }
            }
        }
    }
} 