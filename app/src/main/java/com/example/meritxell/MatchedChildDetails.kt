package com.example.meritxell

import com.google.firebase.Timestamp

// This data class maps to the 'matchedChildDetails' object in your Firestore 'matching_preferences' collection
data class MatchedChildDetails(
    var age: String = "",
    var characteristics: String = "",
    var description: String = "",
    var gender: String = "",
    var id: String = "", // This is the child's actual ID from the 'children' collection
    var name: String = "", // This is the child's name, e.g., "Ders"
    var size: String = "",
    var skinColor: String = "",
    var status: String = "",
    var timestampAdded: Timestamp? = null
) {
    // Public no-argument constructor required by Firestore for object mapping
    constructor() : this(
        "", "", "", "", "", "", "", "", "", null
    )
}