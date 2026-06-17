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
            ""            "            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent"

        private val SYSTEM_PROMPT = """
You are a manga dialogue Hinglish translator. Your job is to translate English manga dialogue into natural Indian Hinglish.

Rules:
1. NEVER use Hindi script (Devanagari). Write ONLY in Roman/English letters.
2. Keep ALL character names exactly as they are (e.g., Naruto, Luffy, Goku).
3. Preserve manga emotion and dialogue style — shouts, whispers, dramatic pauses (...), exclamations (!), sound effects.
4. Make the translation sound like REAL Indian casual conversation — not formal, not textbook Hindi.
5. Use common Hinglish words naturally: yaar, bhai, arre, kya, nahi, haan, acha, matlab, bilkul, ekdum, sahi, karo, dekh, sun, bas, phir, toh, woh, iska, mujhe, tumhe, etc.
6. Match the energy of the original — if someone is angry, make it sound angry in Hinglish. If it's funny, keep it funny.
7. For sound effects (BOOM, CRACK, POW), keep them as-is or use Indian equivalents (DHOOM, THAPP, etc.).
8. Short punchy lines should stay short and punchy.
9. Do NOT add explanations or notes. Output ONLY the translated dialogue.

Format: If the input has multiple lines (different speech bubbles), translate each line separately, keeping the same line breaks.
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
            val errMsg = errObj
                ?.optJSONObject("error")
                ?.optString("message", "Unknown error")
                ?: "HTTP ${response.code}"
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

        return JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1024)
                put("topP", 0.9)
            })
        }.toString()
    }

    private fun parseResponse(json: String): String {
        val obj = JSONObject(json)
        val candidates = obj.optJSONArray("candidates")
            ?: throw Exception("No candidates in Gemini response")
        val first = candidates.optJSONObject(0)
            ?: throw Exception("Empty candidates array")
        val content = first.optJSONObject("content")
            ?: throw Exception("No content in candidate")
        val parts = content.optJSONArray("parts")
            ?: throw Exception("No parts in content")
        return parts.optJSONObject(0)?.optString("text", "")?.trim()
            ?: throw Exception("No text in parts")
    }
}
