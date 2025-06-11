package com.example.meritxell

import com.google.firebase.Timestamp

data class Appointment(
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val appointmentType: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "",
    val scheduledTimestamp: Timestamp? = null
)
