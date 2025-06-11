package com.example.meritxell

import com.google.firebase.Timestamp // Import Timestamp

data class Child(
    var id: String = "",
    var name: String = "",
    var age: String = "", // Ensure this is String
    var characteristics: String = "",
    var gender: String = "",
    var size: String = "", // Ensure this field matches your database for 'size'
    var skinColor: String = "",
    var description: String = "", // Assuming 'description' is the field name in Firestore
    var status: String = "Available",
    val timestampAdded: Timestamp? = null // Corrected type
)