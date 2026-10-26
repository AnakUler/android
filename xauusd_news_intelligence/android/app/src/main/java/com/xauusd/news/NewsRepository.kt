package com.xauusd.news

import android.content.Context
import org.json.JSONObject

class NewsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveFeed(raw: String) { prefs.edit().putString(KEY_CACHED_FEED, raw).apply() }
    fun loadCached(): NewsFeedResponse? = runCatching { prefs.getString(KEY_CACHED_FEED, null)?.let { NewsFeedResponse.fromJson(JSONObject(it)) } }.getOrNull()
    fun saveSetting(key: String, value: String) { prefs.edit().putString(key, value.trim()).apply() }
    fun setting(key: String, default: String = ""): String = prefs.getString(key, default).orEmpty()

    companion object {
        const val PREFS = "xauusd_news_private_settings"
        const val KEY_CACHED_FEED = "cached_feed_json"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_REGISTRATION_CODE = "registration_code"
        const val KEY_FIREBASE_API_KEY = "firebase_api_key"
        const val KEY_FIREBASE_APP_ID = "firebase_app_id"
        const val KEY_FIREBASE_PROJECT_ID = "firebase_project_id"
        const val KEY_FIREBASE_SENDER_ID = "firebase_sender_id"
    }
}
