// Example of your MatchingPreference data class
// Make sure this file exists and is correctly defined in your project
package com.example.meritxell

import com.google.firebase.Timestamp

data class MatchingPreference(
    var id: String = "", // Document ID
    val senderId: String = "",
    val senderUsername: String = "", // Added this field
    val receiverId: String = "",
    val matchedChildDetails: Map<String, Any>? = null, // Store as a map
    val genderPreference: String = "",
    val skinColorPreference: String = "",
    val characteristicsPreference: String = "",
    val preferredSize: String = "",
    val preferredAge: String = "",
    val otherPreferences: String = "",
    val requestTimestamp: Long = 0,
    val status: String = ""
) {
    // Helper property to get the child's name from matchedChildDetails
    val childName: String
        get() = matchedChildDetails?.get("name")?.toString() ?: "N/A"
}