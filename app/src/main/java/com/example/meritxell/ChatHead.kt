package com.example.meritxell

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager

// Import the necessary view types
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import android.view.KeyEvent
import android.widget.TextView
import android.widget.ImageButton // Don't forget this import for ImageButton
import com.google.android.material.chip.ChipGroup // Keep this import as ChipGroup is used in ChatAdapter

import kotlinx.coroutines.launch

class ChatHead : AppCompatActivity() {

    // Declare views to be found by ID
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var buttonSend: Button
    private lateinit var editTextMessage: EditText
    private lateinit var buttonClose: ImageButton

    private lateinit var chatAdapter: ChatAdapter
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Make sure this matches your XML file name

        supportActionBar?.hide()

        // Initialize views using findViewById
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        buttonSend = findViewById(R.id.buttonSend)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonClose = findViewById(R.id.buttonClose)

        setupUI()
        setupObservers()
        addWelcomeMessage() // Add welcome message with suggestions
    }

    private fun setupUI() {
        // Setup RecyclerView
        // Pass the lambda to handle suggestion clicks. This lambda calls sendMessage().
        chatAdapter = ChatAdapter { suggestion ->
            // This lambda is called when a suggestion chip is clicked
            editTextMessage.setText(suggestion) // Populate the EditText
            sendMessage() // Automatically call sendMessage()
        }
        recyclerViewChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@ChatHead)
            // Ensure the chat scrolls to the bottom when new messages are added
            setHasFixedSize(true)
        }

        // Setup send button
        // This is the direct listener for the send button. It calls sendMessage().
        buttonSend.setOnClickListener {
            sendMessage()
        }

        // Make the close button go back to the previous page
        buttonClose.setOnClickListener {
            onBackPressed() // This calls the default back press behavior, finishing the current activity
        }

        // Setup enter key to send message
        // This listener also calls sendMessage() when the "Send" action is triggered on the keyboard.
        editTextMessage.setOnEditorActionListener {
                v: TextView, actionId: Int, event: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun setupObservers() {
        // Observe chat messages
        viewModel.messages.observe(this) { messages: List<ChatMessage> ->
            chatAdapter.updateMessages(messages)
            if (messages.isNotEmpty()) {
                recyclerViewChat.scrollToPosition(messages.size - 1)
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            buttonSend.isEnabled = !isLoading
            editTextMessage.isEnabled = !isLoading

            if (isLoading) {
                buttonSend.text = "..."
            } else {
                buttonSend.text = "Send"
            }
        }

        // Observe errors
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addWelcomeMessage() {
        // Define your adoption-related suggestions for the Philippines
        val adoptionSuggestions = listOf(
            "How to adopt a child in the Philippines?",
            "What are the requirements for adoption?",
            "What is inter-country adoption?",
            "Local adoption agencies in the Philippines",
            "Legal aspects of adoption in the Philippines",
            "Cost of adoption in the Philippines"
        )

        val welcomeMessage = ChatMessage(
            content = getString(R.string.welcome_message), // Ensure this string resource exists
            isUser = false,
            timestamp = System.currentTimeMillis(),
            suggestions = adoptionSuggestions // Add the suggestions here
        )
        viewModel.addMessage(welcomeMessage)
    }

    /**
     * Handles sending a message. This function is called by:
     * 1. The "Send" button click listener.
     * 2. The EditText's "Send" action listener (when pressing Enter on keyboard).
     * 3. The suggestion chip click listener (automatically sends the suggestion).
     */
    private fun sendMessage() {
        val message = editTextMessage.text.toString().trim()
        if (message.isEmpty()) return

        editTextMessage.setText("") // Clear the input field after sending

        lifecycleScope.launch {
            viewModel.sendMessage(message) // Send the message via the ViewModel
        }
    }

    // onBackPressed now behaves as a standard back button, finishing the activity
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
