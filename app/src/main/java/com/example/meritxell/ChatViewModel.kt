package com.example.meritxell

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
    private val chatService = ChatService()
    
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val messagesList = mutableListOf<ChatMessage>()
    
    fun addMessage(message: ChatMessage) {
        messagesList.add(message)
        _messages.value = messagesList.toList()
    }
    
    suspend fun sendMessage(content: String) {
        // Add user message
        val userMessage = ChatMessage(content = content, isUser = true)
        addMessage(userMessage)
        
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                val response = chatService.sendMessage(content)
                val botMessage = ChatMessage(content = response, isUser = false)
                addMessage(botMessage)
            } catch (e: Exception) {
                _errorMessage.value = "Sorry, I encountered an error. Please try again."
                val errorMessage = ChatMessage(
                    content = "Sorry, I didn't get that. Could you please rephrase your question about adoption in the Philippines?",
                    isUser = false
                )
                addMessage(errorMessage)
            } finally {
                _isLoading.value = false
            }
        }
    }
} 