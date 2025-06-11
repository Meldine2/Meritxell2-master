package com.example.meritxell

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ChatService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Configuration - In production, these should be stored securely
    private val apiKey = "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"
    private val systemPrompt = "You are Ally, an AI Legal Assistant EXCLUSIVELY specialized in Philippine adoption law and procedures. You ONLY answer questions related to adoption in the Philippines. For ANY question not about Philippine adoption (including other legal topics, general questions, or unrelated subjects), politely respond: 'I'm specifically designed to help with Philippine adoption questions only. Please ask me about adoption procedures, requirements, documentation, or legal processes in the Philippines.' Keep adoption-related responses CONCISE (2-3 sentences maximum). You can respond in English or Filipino."

    suspend fun sendMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("ChatService", "=== Starting API Request ===")
            Log.d("ChatService", "User message: $userMessage")

            // Test basic connectivity first
            val testRequest = Request.Builder()
                .url("https://httpbin.org/get")
                .build()

            try {
                val testResponse = client.newCall(testRequest).execute()
                Log.d("ChatService", "Connectivity test: ${testResponse.code}")
                testResponse.close()
            } catch (e: Exception) {
                Log.e("ChatService", "Connectivity test failed", e)
                throw Exception("Network connectivity issue: ${e.message}")
            }

            val requestData = ChatRequest(
                model = "gpt-3.5-turbo",
                messages = listOf(
                    ChatRequestMessage(role = "system", content = systemPrompt),
                    ChatRequestMessage(role = "user", content = userMessage)
                ),
                maxTokens = 200,
                temperature = 0.7
            )

            val json = gson.toJson(requestData)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            Log.d("ChatService", "Request payload: $json")

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d("ChatService", "Making API request to OpenAI...")

            val response = client.newCall(request).execute()

            Log.d("ChatService", "Response received - Code: ${response.code}")
            Log.d("ChatService", "Response headers: ${response.headers}")

            val responseBodyString = response.body?.string()
            Log.d("ChatService", "Response body: $responseBodyString")

            if (!response.isSuccessful) {
                Log.e("ChatService", "API error: ${response.code}")
                Log.e("ChatService", "Error body: $responseBodyString")

                when (response.code) {
                    401 -> throw Exception("API key is invalid or expired")
                    429 -> throw Exception("Rate limit exceeded. Please try again later")
                    500, 502, 503 -> throw Exception("OpenAI server error. Please try again")
                    else -> throw Exception("API error: ${response.code} - $responseBodyString")
                }
            }

            if (responseBodyString.isNullOrEmpty()) {
                throw Exception("Empty response from API")
            }

            val chatResponse = try {
                gson.fromJson(responseBodyString, ChatResponse::class.java)
            } catch (e: Exception) {
                Log.e("ChatService", "JSON parsing error", e)
                throw Exception("Failed to parse API response: ${e.message}")
            }

            val content = chatResponse.choices.firstOrNull()?.message?.content

            if (content.isNullOrEmpty()) {
                Log.e("ChatService", "No content in response")
                throw Exception("No response content received")
            }

            Log.d("ChatService", "=== API Request Successful ===")
            Log.d("ChatService", "Response content: $content")

            return@withContext content

        } catch (e: Exception) {
            Log.e("ChatService", "=== API Request Failed ===", e)
            Log.e("ChatService", "Error message: ${e.message}")
            throw e
        }
    }
}

// Data classes for OpenAI API
data class ChatRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    val temperature: Double
)

data class ChatRequestMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice>
)

data class ChatChoice(
    val message: ChatResponseMessage
)

data class ChatResponseMessage(
    val content: String
)