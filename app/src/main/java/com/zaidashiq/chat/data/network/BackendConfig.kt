package com.zaidashiq.chat.data.network

import com.zaidashiq.chat.BuildConfig

object BackendConfig {
    private const val DEFAULT_PRODUCTION_RAILWAY_URL = "https://zaid-app-production.up.railway.app"
    private const val PLACEHOLDER_RAILWAY = "https://your-railway-backend.up.railway.app"
    private const val PLACEHOLDER_SUPABASE = "https://your-project.supabase.co"
    private const val PLACEHOLDER_GIPHY = "your_giphy_api_key"

    private fun sanitizeValue(raw: String): String =
        raw.trim().trim('"', '\'').trim()

    val rawRailwayUrl: String
        get() {
            val cleaned = sanitizeValue(BuildConfig.RAILWAY_BACKEND_URL).trimEnd('/')
            if (cleaned.isBlank() ||
                cleaned.equals("UNSET", ignoreCase = true) ||
                cleaned.equals(PLACEHOLDER_RAILWAY, ignoreCase = true) ||
                cleaned.contains("your-railway-backend", ignoreCase = true)
            ) {
                return DEFAULT_PRODUCTION_RAILWAY_URL
            }
            return if (cleaned.startsWith("http://", ignoreCase = true) ||
                cleaned.startsWith("https://", ignoreCase = true)
            ) {
                cleaned
            } else {
                "https://$cleaned"
            }
        }

    val isRailwayConfigured: Boolean
        get() = rawRailwayUrl.isNotBlank()

    val normalizedRailwayBaseUrl: String
        get() = if (isRailwayConfigured) {
            "$rawRailwayUrl/"
        } else {
            "$PLACEHOLDER_RAILWAY/"
        }

    val supabaseUrl: String
        get() {
            val cleaned = sanitizeValue(BuildConfig.SUPABASE_URL).trimEnd('/')
            if (cleaned.isBlank() ||
                cleaned.equals("UNSET", ignoreCase = true) ||
                cleaned.equals(PLACEHOLDER_SUPABASE, ignoreCase = true) ||
                cleaned.contains("your-project.supabase.co", ignoreCase = true)
            ) {
                return ""
            }
            return if (cleaned.startsWith("http://", ignoreCase = true) ||
                cleaned.startsWith("https://", ignoreCase = true)
            ) {
                cleaned
            } else {
                "https://$cleaned"
            }
        }

    val supabasePublishableKey: String
        get() {
            val cleaned = sanitizeValue(BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            return if (cleaned.isBlank() ||
                cleaned.equals("UNSET", ignoreCase = true) ||
                cleaned == "your_supabase_publishable_key"
            ) {
                ""
            } else {
                cleaned
            }
        }

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()

    val giphyApiKey: String
        get() {
            val cleaned = sanitizeValue(BuildConfig.GIPHY_API_KEY)
            return if (cleaned.isBlank() ||
                cleaned.equals("UNSET", ignoreCase = true) ||
                cleaned == PLACEHOLDER_GIPHY
            ) {
                ""
            } else {
                cleaned
            }
        }

    val isGiphyConfigured: Boolean
        get() = giphyApiKey.isNotBlank()

    val defaultOwnerNumber: String
        get() = sanitizeValue(BuildConfig.OWNER_NUMBER)
            .takeUnless { it.equals("UNSET", ignoreCase = true) }
            ?.filter { it.isDigit() }
            .orEmpty()

    val timezone: String
        get() = sanitizeValue(BuildConfig.TIMEZONE).ifBlank { "Asia/Karachi" }
}
