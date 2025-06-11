package com.example.meritxell

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestions: List<String>? = null
)