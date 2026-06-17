package com.manga.hinglish.translator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiTranslator(private val apiKey: String) {

    companion object {
        private const val ENDPOINT =
            "https://api.groq.com/openai/v1/chat/completions"

        private const val SYSTEM_PROMPT = """You are a manga dialogue Hinglish translator. Translate English manga dialogue into natural Indian Hinglish (Roman script only, no Devanagari).

Rules:
1. Write ONLY in Roman/English letters — NEVER Devanagari.
2. Keep character names exactly as they are.
3. Preserve emotion — shouts, whispers, dramatic pauses (...), exclamations (!).
4. Sound like REAL Indian casual conversation: yaar, bhai, arre, kya, nahi, haan, acha, matlab, sahi, toh, woh, mujhe, etc.
5. Keep sound effects as-is or use Indian ones (DHOOM, THAPP).
6. Short punchy lines stay short. No explanations. ONLY output the translated dialogue.
7. Multiple lines = translate each line separately with same line breaks."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translate(englishText: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "llama-3.1-8b-instant")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Translate to Hinglish:\n\n$englishText")
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 1024)
        }.toString()

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val errObj = runCatching { JSONObject(bodyStr) }.getOrNull()
            val errMsg = errObj?.optJSONObject("error")?.optString("message", "Unknown")
                ?: "HTTP ${response.code}"
            throw Exception("Translation error: $errMsg")
        }

        val json = JSONObject(bodyStr)
        json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "")
            ?.trim()
            ?: throw Exception("No translation in response")
    }
}
