package com.manga.hinglish.translator

import android.content.Context

object GeminiApiKeyStore {
    private const val PREF_NAME = "manga_translator_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    fun set(context: Context, key: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, key)
            .apply()
    }

    fun get(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_KEY)
            .apply()
    }
}
