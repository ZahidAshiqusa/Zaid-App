package com.zaidashiq.chat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "zaid_ashiq_chat_prefs")

data class StoredSession(
    val deviceId: String = "",
    val pairedPhone: String = "",
    val isLinked: Boolean = false
)

class SessionPreferences(private val context: Context) {
    private val keyDeviceId = stringPreferencesKey("device_id")
    private val keyPairedPhone = stringPreferencesKey("paired_phone")
    private val keyIsLinked = booleanPreferencesKey("is_linked")

    val sessionFlow: Flow<StoredSession> = context.sessionDataStore.data.map { prefs ->
        StoredSession(
            deviceId = prefs[keyDeviceId] ?: "",
            pairedPhone = prefs[keyPairedPhone] ?: "",
            isLinked = prefs[keyIsLinked] ?: false
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.sessionDataStore.data.first()[keyDeviceId]
        if (!existing.isNullOrBlank()) return existing
        val generated = "android_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        context.sessionDataStore.edit { it[keyDeviceId] = generated }
        return generated
    }

    suspend fun saveLinkedSession(phone: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[keyPairedPhone] = phone
            prefs[keyIsLinked] = true
        }
    }

    suspend fun clearLinkedSession() {
        context.sessionDataStore.edit { prefs ->
            prefs[keyIsLinked] = false
        }
    }
}
