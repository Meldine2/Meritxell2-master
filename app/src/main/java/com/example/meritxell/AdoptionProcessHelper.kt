package com.example.meritxell

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

/**
 * AdoptionProcessHelper handles adoption process integration with system messages
 */
class AdoptionProcessHelper {
    
    companion object {
        private const val TAG = "AdoptionProcessHelper"
        
        /**
         * Start the adoption process - sends automatic system message
         */
        fun startAdoptionProcess(
            userId: String,
            userData: Map<String, Any>,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            val processId = "adoption_${userId}_${System.currentTimeMillis()}"
            
            val adoptionData = hashMapOf(
                "processId" to processId,
                "userId" to userId,
                "status" to "in_progress",
                "currentStep" to 1,
                "totalSteps" to 10,
                "startedAt" to FieldValue.serverTimestamp(),
                "completedSteps" to listOf<Int>()
            )
            
            // Add user data
            adoptionData.putAll(userData)
            
            firestore.collection("adoptionProcesses").document(processId)
                .set(adoptionData)
                .addOnSuccessListener {
                    Log.d(TAG, "Adoption process started successfully: $processId")
                    
                    // Send system messages to connect with admin
                    sendAdoptionStartMessages(userId, userData)
                    
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to start adoption process: ${e.message}")
                    onFailure("Failed to start adoption process: ${e.message}")
                }
        }
        
        /**
         * Complete a step in the adoption process
         */
        fun completeAdoptionStep(
            processId: String,
            stepNumber: Int,
            stepData: Map<String, Any> = emptyMap(),
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            
            firestore.collection("adoptionProcesses").document(processId).get()
                .addOnSuccessListener { processDoc ->
                    if (!processDoc.exists()) {
                        onFailure("Adoption process not found")
                        return@addOnSuccessListener
                    }
                    
                    val userId = processDoc.getString("userId") ?: ""
                    val completedSteps = processDoc.get("completedSteps") as? List<Int> ?: emptyList()
                    val updatedSteps = completedSteps.toMutableList()
                    
                    if (!updatedSteps.contains(stepNumber)) {
                        updatedSteps.add(stepNumber)
                    }
                    
                    val updates = mutableMapOf<String, Any>(
                        "completedSteps" to updatedSteps,
                        "currentStep" to (stepNumber + 1).coerceAtMost(10),
                        "step${stepNumber}CompletedAt" to FieldValue.serverTimestamp()
                    )
                    
                    // Add step-specific data
                    stepData.forEach { (key, value) ->
                        updates["step${stepNumber}_$key"] = value
                    }
                    
                    // If all steps completed, mark as finished
                    if (stepNumber == 10) {
                        updates["status"] = "completed"
                        updates["completedAt"] = FieldValue.serverTimestamp()
                    }
                    
                    firestore.collection("adoptionProcesses").document(processId)
                        .update(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "Step $stepNumber completed for process $processId")
                            
                            // Send system message about step completion
                            sendStepCompletionMessage(userId, stepNumber)
                            
                            // Process completed - status is now "completed" for history filtering
                            if (stepNumber == 10) {
                                Log.d(TAG, "Adoption process completed - available in history via status filter")
                            }
                            
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to complete step: ${e.message}")
                            onFailure("Failed to complete step: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get adoption process: ${e.message}")
                    onFailure("Failed to get adoption process: ${e.message}")
                }
        }
        
        /**
         * Get adoption process status for a user
         */
        fun getAdoptionStatus(
            userId: String,
            onSuccess: (Map<String, Any>?) -> Unit,
            onFailure: (String) -> Unit
        ) {
            val firestore = FirebaseFirestore.getInstance()
            
            firestore.collection("adoptionProcesses")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "in_progress")
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        onSuccess(null)
                    } else {
                        val processDoc = documents.documents[0]
                        onSuccess(processDoc.data)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get adoption status: ${e.message}")
                    onFailure("Failed to get adoption status: ${e.message}")
                }
        }
        
        private fun sendAdoptionStartMessages(userId: String, userData: Map<String, Any>) {
            val firestore = FirebaseFirestore.getInstance()
            
            // Get user's username from their profile
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val username = userDoc.getString("username") ?: 
                                  userData["fullName"] as? String ?: 
                                  "User"
                    
                    // Find an admin to connect with
                    findAdminForAdoption(userId, username)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get user data for adoption messages: ${e.message}")
                    val username = userData["fullName"] as? String ?: "User"
                    findAdminForAdoption(userId, username)
                }
        }
        
        private fun findAdminForAdoption(userId: String, username: String) {
            val firestore = FirebaseFirestore.getInstance()
            
            firestore.collection("users")
                .whereEqualTo("role", "admin")
                .limit(1)
                .get()
                .addOnSuccessListener { adminDocs ->
                    if (!adminDocs.isEmpty) {
                        val adminId = adminDocs.documents[0].id
                        
                        // Send system message to start adoption chat
                        SystemMessageHelper.sendAdoptionStartedMessage(userId, username, adminId)
                    } else {
                        Log.w(TAG, "No admin found for adoption process")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to find admin for adoption: ${e.message}")
                }
        }
        
        private fun sendStepCompletionMessage(userId: String, stepNumber: Int) {
            val firestore = FirebaseFirestore.getInstance()
            
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val username = userDoc.getString("username") ?: "User"
                    
                    SystemMessageHelper.sendStepCompletedMessage(userId, username, stepNumber)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get username for step completion: ${e.message}")
                    SystemMessageHelper.sendStepCompletedMessage(userId, "User", stepNumber)
                }
        }
        
        /**
         * Helper method to demonstrate usage in your activities
         */
        fun exampleUsageInActivity() {
            // Example: Starting adoption process from an adoption form activity
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val userData = mapOf(
                "fullName" to "John Doe",
                "email" to "john@example.com",
                "phone" to "09123456789"
            )
            
            startAdoptionProcess(
                userId = userId,
                userData = userData,
                onSuccess = {
                    Log.d(TAG, "Adoption process started successfully")
                    // Navigate to step 1 or dashboard
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start adoption: $error")
                    // Show error to user
                }
            )
            
            // Example: Completing a step
            val processId = "adoption_123_timestamp"
            val stepData = mapOf(
                "documentSubmitted" to true,
                "documentType" to "Birth Certificate"
            )
            
            completeAdoptionStep(
                processId = processId,
                stepNumber = 1,
                stepData = stepData,
                onSuccess = {
                    Log.d(TAG, "Step completed successfully")
                    // Navigate to next step or update UI
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to complete step: $error")
                    // Show error to user
                }
            )
        }
    }
} 