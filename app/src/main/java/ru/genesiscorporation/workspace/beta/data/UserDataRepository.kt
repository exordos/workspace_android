package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class ApiKeyRepository(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val EMAIL = stringPreferencesKey("email")
        private val BASE_URL = stringPreferencesKey("base_url")
        private val USER_ID = stringPreferencesKey("user_id")
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[API_KEY] }

    val emailFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[EMAIL] }

    val baseUrlFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[BASE_URL] }

    val userIdFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[USER_ID] }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[API_KEY] = apiKey
        }
    }

    suspend fun saveEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[EMAIL] = email
        }
    }

    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[BASE_URL] = baseUrl
        }
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID] = userId
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}