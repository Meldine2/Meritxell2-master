package com.example.meritxell

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.Date

/**
 * AdminDonationManager handles admin operations for donation management
 * including approval, rejection, and system message sending
 */
class AdminDonationManager {
    
    companion object {
        private const val TAG = "AdminDonationManager"
        
        /**
         * Approve a donation and send system messages to user
         * This triggers automatic history movement after 24 hours
         */
        fun approveDonation(
            donationId: String,
            donationType: String,
            collection: String,
            adminId: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            
            // First get the donation details
            firestore.collection(collection).document(donationId).get()
                .addOnSuccessListener { donationDoc ->
                    if (!donationDoc.exists()) {
                        onFailure("Donation not found")
                        return@addOnSuccessListener
                    }
                    
                    val userId = donationDoc.getString("userId") ?: ""
                    val donorName = donationDoc.getString("fullName") ?: "Unknown Donor"
                    
                    if (userId.isEmpty()) {
                        onFailure("User ID not found in donation")
                        return@addOnSuccessListener
                    }
                    
                    // Update donation status to approved
                    val updates = mapOf(
                        "status" to "approved",
                        "approvedBy" to adminId,
                        "approvedAt" to FieldValue.serverTimestamp(),
                        "approvalDate" to Date().time
                    )
                    
                    firestore.collection(collection).document(donationId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "Donation $donationId approved successfully")
                            
                            // Send system messages
                            sendApprovalMessages(userId, donorName, adminId, donationType, donationId)
                            
                                        // Donation approval is now handled by status-based filtering
            // No need to move to separate history collection
                            
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to approve donation: ${e.message}")
                            onFailure("Failed to approve donation: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get donation details: ${e.message}")
                    onFailure("Failed to get donation details: ${e.message}")
                }
        }
        
        /**
         * Reject a donation with reason
         */
        fun rejectDonation(
            donationId: String,
            donationType: String,
            collection: String,
            adminId: String,
            reason: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            
            // Get donation details first
            firestore.collection(collection).document(donationId).get()
                .addOnSuccessListener { donationDoc ->
                    if (!donationDoc.exists()) {
                        onFailure("Donation not found")
                        return@addOnSuccessListener
                    }
                    
                    val userId = donationDoc.getString("userId") ?: ""
                    val donorName = donationDoc.getString("fullName") ?: "Unknown Donor"
                    
                    // Update donation status to rejected
                    val updates = mapOf(
                        "status" to "rejected",
                        "rejectedBy" to adminId,
                        "rejectedAt" to FieldValue.serverTimestamp(),
                        "rejectionReason" to reason
                    )
                    
                    firestore.collection(collection).document(donationId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "Donation $donationId rejected successfully")
                            
                            // Send rejection message to user
                            sendRejectionMessage(userId, donorName, adminId, donationType, reason)
                            
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to reject donation: ${e.message}")
                            onFailure("Failed to reject donation: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get donation details: ${e.message}")
                    onFailure("Failed to get donation details: ${e.message}")
                }
        }
        
        /**
         * Schedule an appointment for a user (admin feature)
         */
        fun scheduleAppointment(
            userId: String,
            username: String,
            appointmentDate: String,
            appointmentTime: String,
            appointmentType: String,
            notes: String = "",
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            val appointmentId = "apt_${userId}_${System.currentTimeMillis()}"
            
            val appointmentData = hashMapOf(
                "appointmentId" to appointmentId,
                "userId" to userId,
                "username" to username,
                "date" to appointmentDate,
                "time" to appointmentTime,
                "type" to appointmentType,
                "notes" to notes,
                "status" to "scheduled",
                "createdAt" to FieldValue.serverTimestamp(),
                "createdBy" to FirebaseAuth.getInstance().currentUser?.uid
            )
            
            firestore.collection("appointments").document(appointmentId)
                .set(appointmentData)
                .addOnSuccessListener {
                    Log.d(TAG, "Appointment scheduled successfully")
                    
                    // Send system message about appointment
                    SystemMessageHelper.sendAppointmentScheduledMessage(
                        userId, username, appointmentDate, appointmentTime
                    )
                    
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to schedule appointment: ${e.message}")
                    onFailure("Failed to schedule appointment: ${e.message}")
                }
        }
        
        /**
         * Cancel an appointment (admin feature)
         */
        fun cancelAppointment(
            appointmentId: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            val adminId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            
            firestore.collection("appointments").document(appointmentId).get()
                .addOnSuccessListener { appointmentDoc ->
                    if (!appointmentDoc.exists()) {
                        onFailure("Appointment not found")
                        return@addOnSuccessListener
                    }
                    
                    val userId = appointmentDoc.getString("userId") ?: ""
                    val username = appointmentDoc.getString("username") ?: "User"
                    val appointmentDate = appointmentDoc.getString("date") ?: ""
                    
                    // Update appointment status to cancelled
                    val updates = mapOf(
                        "status" to "cancelled",
                        "cancelledBy" to adminId,
                        "cancelledAt" to FieldValue.serverTimestamp()
                    )
                    
                    firestore.collection("appointments").document(appointmentId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "Appointment cancelled successfully")
                            
                            // Send cancellation message
                            SystemMessageHelper.sendAppointmentCancelledMessage(
                                userId, username, adminId, appointmentDate
                            )
                            
                            // Schedule for history cleanup
                            // The cancelled appointment will be moved to history automatically by the scheduler
                            
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to cancel appointment: ${e.message}")
                            onFailure("Failed to cancel appointment: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get appointment: ${e.message}")
                    onFailure("Failed to get appointment: ${e.message}")
                }
        }
        
        /**
         * Complete a matching process (admin feature)
         */
        fun completeMatching(
            userId: String,
            username: String,
            childName: String,
            matchingData: Map<String, Any>,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            val matchingId = "match_${userId}_${System.currentTimeMillis()}"
            
            val matchingRecord = hashMapOf(
                "matchingId" to matchingId,
                "userId" to userId,
                "username" to username,
                "childName" to childName,
                "status" to "pending_acceptance",
                "acceptanceDeadline" to (System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000)), // 3 days
                "completedAt" to FieldValue.serverTimestamp(),
                "completedBy" to FirebaseAuth.getInstance().currentUser?.uid
            )
            
            // Add any additional matching data
            matchingRecord.putAll(matchingData)
            
            firestore.collection("matching_preferences").document(matchingId)
                .set(matchingRecord)
                .addOnSuccessListener {
                    Log.d(TAG, "Matching completed successfully")
                    
                    // Send system message about matching
                    SystemMessageHelper.sendMatchingCompletedMessage(userId, username, childName)
                    
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to complete matching: ${e.message}")
                    onFailure("Failed to complete matching: ${e.message}")
                }
        }
        
        /**
         * Accept a matching (user action, but can be triggered by admin)
         */
        fun acceptMatching(
            matchingId: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            // The accepted matching will be moved to history automatically by the scheduler after 3 days
            onSuccess()
        }
        
        /**
         * Complete an appointment
         */
        fun completeAppointment(
            appointmentId: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            // The completed appointment will be moved to history automatically by the scheduler after 3 days
            onSuccess()
        }
        
        private fun sendApprovalMessages(
            userId: String,
            username: String,
            adminId: String,
            donationType: String,
            donationId: String
        ) {
            // Get username from Firestore
            FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val actualUsername = userDoc.getString("username") ?: username
                    
                    SystemMessageHelper.sendDonationApprovedMessage(
                        userId, actualUsername, adminId, donationType, donationId
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get username, using fallback: ${e.message}")
                    SystemMessageHelper.sendDonationApprovedMessage(
                        userId, username, adminId, donationType, donationId
                    )
                }
        }
        
        private fun sendRejectionMessage(
            userId: String,
            username: String,
            adminId: String,
            donationType: String,
            reason: String
        ) {
            // Get username from Firestore and send rejection message
            FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val actualUsername = userDoc.getString("username") ?: username
                    
                    SystemMessageHelper.sendDonationRejectedMessage(
                        userId, actualUsername, adminId, donationType, "rejection_${System.currentTimeMillis()}", reason
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get username, using fallback: ${e.message}")
                    SystemMessageHelper.sendDonationRejectedMessage(
                        userId, username, adminId, donationType, "rejection_${System.currentTimeMillis()}", reason
                    )
                }
        }
        
        private fun scheduleHistoryMovement(donationId: String, collection: String, donationType: String) {
            // In a real app, you would use a job scheduler or cloud function
            // For demonstration, we'll log the requirement
            Log.d(TAG, "Scheduling history movement for $donationType donation $donationId after 24 hours")
            
            // This would typically be handled by a background service or cloud function
            // that runs periodically to move approved donations to history after 24 hours
        }
        
        private fun moveAppointmentToHistory(appointmentId: String, appointmentData: Map<String, Any>) {
            val firestore = FirebaseFirestore.getInstance()
            
            // Move to history collection
            val historyData = appointmentData.toMutableMap()
            historyData["movedToHistoryAt"] = FieldValue.serverTimestamp()
            historyData["originalCollection"] = "appointments"
            
            firestore.collection("appointmentHistory").document(appointmentId)
                .set(historyData)
                .addOnSuccessListener {
                    // Delete from original collection
                    firestore.collection("appointments").document(appointmentId).delete()
                        .addOnSuccessListener {
                            Log.d(TAG, "Appointment moved to history successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to delete original appointment: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to move appointment to history: ${e.message}")
                }
        }
    }
} 