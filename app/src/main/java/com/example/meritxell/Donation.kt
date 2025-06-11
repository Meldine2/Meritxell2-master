package com.example.meritxell.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp // Keep this import if you use @ServerTimestamp elsewhere or for new data
import java.util.Date // Keep this import for displayDate

data class Donation(
    // CHANGED from 'val' to 'var' to allow reassignment of document ID
    var id: String? = null,
    val userId: String? = null,
    val username: String? = null,
    var donationType: String? = null,
    val amount: String? = null,
    val receiptUrl: String? = null,
    val status: String? = null,
    val proofOfDonationText: String? = null,
    val proofOfDonationImageUrl: String? = null,

    // CHANGED this type to String? to match your existing Firestore 'timestamp' field data type
    val timestamp: String? = null,
    @Exclude @set:Exclude @get:Exclude
    var displayDate: String? = null // For UI display, not stored in Firestore
)