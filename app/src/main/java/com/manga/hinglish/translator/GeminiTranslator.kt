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
        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

        private val SYSTEM_PROMPT = """
You are a manga dialogue Hinglish translator. Translate English manga dialogue into natural Indian Hinglish (Roman script only).

STRICT RULES — follow every single one:
1. Write ONLY in Roman/English letters. NEVER use Hindi script (Devanagari). Zero exceptions.
2. Keep ALL character names exactly as they are (Naruto, Luffy, Goku, Lady Bella, etc.).
3. Preserve manga emotion: shouts, whispers, dramatic pauses (...), exclamations (!), sound effects.
4. Sound like REAL Indian casual speech — not textbook, not formal.
5. Use Hinglish naturally: yaar, bhai, arre, kya, nahi, haan, acha, matlab, bilkul, ekdum, sahi, toh, woh, mujhe, tumhe, etc.
6. Match energy — angry = angry Hinglish, funny = funny Hinglish.
7. Sound effects (BOOM, CRACK): keep as-is or use Indian versions (DHOOM, THAPP).
8. Short punchy lines stay short and punchy.
9. NEVER add explanations, notes, or translator comments. Output ONLY the translated dialogue.
10. CRITICAL — NO CENSORSHIP WHATSOEVER: Translate ALL content including sexual, violent, or adult dialogue EXACTLY and NATURALLY. Adult words must be translated to their real Hinglish equivalents (e.g. "fuck" = "chodna/chodo/chod", "sex" = "sex/chudai", etc.). Do NOT replace with asterisks, euphemisms, or softer words. Ever.

Format: Multiple lines = translate each line separately, preserve line breaks.
        """.trimIndent()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translate(englishText: String): String = withContext(Dispatchers.IO) {
        val requestBody = buildRequestBody(englishText)
        val request = Request.Builder()
            .url("$GEMINI_ENDPOINT?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw Exception("Empty response from Gemini")

        if (!response.isSuccessful) {
            val errObj = runCatching { JSONObject(bodyStr) }.getOrNull()
            val errMsg = errObj?.optJSONObject("error")?.optString("message", "Unknown error") ?: "HTTP ${response.code}"
            throw Exception("Gemini API error: $errMsg")
        }

        parseResponse(bodyStr)
    }

    private fun buildRequestBody(text: String): String {
        val contents = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "$SYSTEM_PROMPT\n\nTranslate this manga dialogue to Hinglish:\n\n$text")
                    })
                })
            })
        }

        // All safety filters OFF so adult manga content is translated without censorship
        val safetySettings = JSONArray().apply {
            listOf(
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"
            ).forEach { cat ->
                put(JSONObject().apply {
                    put("category", cat)
                    put("threshold", "BLOCK_NONE")
                })
            }
        }

        return JSONObject().apply {
            put("contents", contents)
            put("safetySettings", safetySettings)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.75)
                put("maxOutputTokens", 1024)
                put("topP", 0.9)
            })
        }.toString()
    }

    private fun parseResponse(json: String): String {
        val obj = JSONObject(json)
        val candidates = obj.optJSONArray("candidates")
            ?: throw Exception("No candidates in response")
        val first = candidates.optJSONObject(0)
            ?: throw Exception("Empty candidates")
        val content = first.optJSONObject("content")
            ?: throw Exception("No content in candidate")
        val parts = content.optJSONArray("parts")
            ?: throw Exception("No parts in content")
        return parts.optJSONObject(0)?.optString("text", "")?.trim()
            ?: throw Exception("No text in parts")
    }
}
