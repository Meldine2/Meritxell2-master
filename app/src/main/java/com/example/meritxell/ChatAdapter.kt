package com.example.meritxell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup // Import ChipGroup

class ChatAdapter(private val onSuggestionClick: (String) -> Unit) : // Added a callback for suggestion clicks
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var messages = mutableListOf<ChatMessage>()

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        // Use the existing item_chat_message layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view, onSuggestionClick) // Pass the callback to the ViewHolder
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class ChatViewHolder(itemView: View, private val onSuggestionClick: (String) -> Unit) : // Receive callback
        RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.textViewMessage)
        private val messageCard: MaterialCardView = itemView.findViewById(R.id.cardViewMessage)
        private val chipGroupSuggestions: ChipGroup? = itemView.findViewById(R.id.chipGroupSuggestions) // Find ChipGroup

        fun bind(message: ChatMessage) {
            messageText.text = message.content

            val context = itemView.context
            val layoutParams = messageCard.layoutParams as FrameLayout.LayoutParams

            // Clear existing chips before adding new ones
            chipGroupSuggestions?.removeAllViews()

            if (message.isUser) {
                // User message - align right with blue background
                layoutParams.gravity = android.view.Gravity.END
                layoutParams.leftMargin = context.resources.getDimensionPixelSize(R.dimen.margin_large)
                layoutParams.rightMargin = context.resources.getDimensionPixelSize(R.dimen.margin_small)

                // Set user message colors (assuming these colors are defined in colors.xml)
                messageCard.setCardBackgroundColor(context.getColor(R.color.user_message_bg))
                messageText.setTextColor(context.getColor(R.color.user_message_text))

                // Hide suggestions for user messages
                chipGroupSuggestions?.visibility = View.GONE

            } else {
                // Bot message - align left with light background
                layoutParams.gravity = android.view.Gravity.START
                layoutParams.leftMargin = context.resources.getDimensionPixelSize(R.dimen.margin_small)
                layoutParams.rightMargin = context.resources.getDimensionPixelSize(R.dimen.margin_large)

                // Set bot message colors (assuming these colors are defined in colors.xml)
                messageCard.setCardBackgroundColor(context.getColor(R.color.bot_message_bg))
                messageText.setTextColor(context.getColor(R.color.bot_message_text))

                // Show suggestions if available for bot messages
                message.suggestions?.let { suggestions ->
                    if (suggestions.isNotEmpty()) {
                        chipGroupSuggestions?.visibility = View.VISIBLE
                        for (suggestion in suggestions) {
                            val chip = Chip(context)
                            chip.text = suggestion
                            chip.isClickable = true
                            chip.isCheckable = false // Make it behave like a button, not a toggle
                            chip.setOnClickListener {
                                onSuggestionClick(suggestion) // Trigger the callback
                            }
                            // Optional: Set a specific style for the chips if needed
                            // chip.setChipBackgroundColorResource(R.color.suggestion_chip_bg)
                            // chip.setTextColor(context.getColor(R.color.suggestion_chip_text))
                            chipGroupSuggestions?.addView(chip)
                        }
                    } else {
                        chipGroupSuggestions?.visibility = View.GONE
                    }
                } ?: run {
                    chipGroupSuggestions?.visibility = View.GONE
                }
            }
            messageCard.layoutParams = layoutParams
        }
    }
}
