package com.zaidashiq.chat.data.network

import com.zaidashiq.chat.BuildConfig

object BackendConfig {
    private const val PLACEHOLDER_RAILWAY = "https://your-railway-backend.up.railway.app"
    private const val PLACEHOLDER_SUPABASE = "https://your-project.supabase.co"
    private const val PLACEHOLDER_GIPHY = "your_giphy_api_key"

    val rawRailwayUrl: String
        get() = BuildConfig.RAILWAY_BACKEND_URL.trim().trimEnd('/')

    val isRailwayConfigured: Boolean
        get() = rawRailwayUrl.isNotBlank() &&
            !rawRailwayUrl.equals(PLACEHOLDER_RAILWAY, ignoreCase = true) &&
            (rawRailwayUrl.startsWith("http://") || rawRailwayUrl.startsWith("https://"))

    val normalizedRailwayBaseUrl: String
        get() = if (isRailwayConfigured) {
            "$rawRailwayUrl/"
        } else {
            "$PLACEHOLDER_RAILWAY/"
        }

    val isSupabaseConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            !BuildConfig.SUPABASE_URL.equals(PLACEHOLDER_SUPABASE, ignoreCase = true) &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY != "your_supabase_publishable_key"

    val isGiphyConfigured: Boolean
        get() = BuildConfig.GIPHY_API_KEY.isNotBlank() &&
            BuildConfig.GIPHY_API_KEY != PLACEHOLDER_GIPHY

    val giphyApiKey: String
        get() = if (isGiphyConfigured) BuildConfig.GIPHY_API_KEY.trim() else ""
}
