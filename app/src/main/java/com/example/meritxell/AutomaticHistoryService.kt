package com.example.meritxell

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * DISABLED: This service is not used in status-based history system
 * 
 * The app now uses status-based history where records stay in original collections
 * and history is determined by status fields (completed, approved, rejected, cancelled)
 * 
 * REQUIREMENT: "Automatic 24-hour Timers: For adoption and donation history movement"
 * Service to automatically move records to history after 24 hours
 */
class AutomaticHistoryService : Service() {

    private lateinit var firestore: FirebaseFirestore
    private val timer = Timer()
    
    companion object {
        private const val TAG = "AutomaticHistoryService"
        private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L // Check every hour
        private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L
    }

    override fun onCreate() {
        super.onCreate()
        firestore = FirebaseFirestore.getInstance()
        Log.d(TAG, "AutomaticHistoryService created - DISABLED for status-based history")
        
        // DISABLED: No longer needed for status-based history
        // All history is now managed through status fields in original collections
        // timer.scheduleAtFixedRate(object : TimerTask() {
        //     override fun run() {
        //         checkAndMoveExpiredRecords()
        //     }
        // }, 0, CHECK_INTERVAL_MS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
        Log.d(TAG, "AutomaticHistoryService destroyed")
    }

    // DISABLED: All methods below are disabled for status-based history approach
    // History is now determined by status fields in original collections
    
    /*
    private fun checkAndMoveExpiredRecords() {
        Log.d(TAG, "Checking for expired records to move to history...")
        
        // Check adoption processes
        checkExpiredAdoptionProcesses()
        
        // Check donations
        checkExpiredDonations()
        
        // Check matching preferences
        checkExpiredMatching()
    }

    private fun checkExpiredAdoptionProcesses() {
        val twentyFourHoursAgo = Timestamp(Date(System.currentTimeMillis() - TWENTY_FOUR_HOURS_MS))
        
        firestore.collection("adoption_progress")
            .whereEqualTo("step10_status", "completed")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val completedAt = document.getTimestamp("step10_completedAt")
                    if (completedAt != null && completedAt.toDate().before(twentyFourHoursAgo.toDate())) {
                        moveAdoptionToHistory(document.id, document.data)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking expired adoption processes: ${e.message}")
            }
    }

    private fun checkExpiredDonations() {
        val twentyFourHoursAgo = Timestamp(Date(System.currentTimeMillis() - TWENTY_FOUR_HOURS_MS))
        
        // Check main donations collection
        checkDonationCollection("donations", twentyFourHoursAgo)
        
        // Check in-kind donation collections
        val inKindCollections = listOf("toysdonation", "clothesdonation", "fooddonation", "educationdonation")
        for (collection in inKindCollections) {
            checkDonationCollection(collection, twentyFourHoursAgo)
        }
    }

    private fun checkDonationCollection(collectionName: String, twentyFourHoursAgo: Timestamp) {
        firestore.collection(collectionName)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val approvedAt = document.getTimestamp("approvedAt")
                    if (approvedAt != null && approvedAt.toDate().before(twentyFourHoursAgo.toDate())) {
                        moveDonationToHistory(document.id, document.data, collectionName)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking expired donations in $collectionName: ${e.message}")
            }
    }

    private fun checkExpiredMatching() {
        val threeDaysAgo = Timestamp(Date(System.currentTimeMillis() - (3 * TWENTY_FOUR_HOURS_MS)))
        
        firestore.collection("matching_preferences")
            .whereEqualTo("status", "accepted")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val acceptedAt = document.getTimestamp("acceptedAt")
                    if (acceptedAt != null && acceptedAt.toDate().before(threeDaysAgo.toDate())) {
                        moveMatchingToHistory(document.id, document.data)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking expired matching preferences: ${e.message}")
            }
    }

    private fun moveAdoptionToHistory(documentId: String, data: Map<String, Any>) {
        // REMOVED: No longer using adoption_history collection
        // Status-based history uses status field in adoption_progress collection
    }

    private fun moveDonationToHistory(documentId: String, data: Map<String, Any>, collectionName: String) {
        // REMOVED: No longer moving to separate history collections
        // Status-based history uses status field in original collections
    }

    private fun moveMatchingToHistory(documentId: String, data: Map<String, Any>) {
        // REMOVED: No longer moving to separate history collections  
        // Status-based history uses status field in original collections
    }
    */
} 