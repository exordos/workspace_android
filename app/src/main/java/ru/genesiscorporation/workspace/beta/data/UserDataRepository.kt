package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

class ApiKeyRepository(private val context: Context) {

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")
        private val BASE_URLS = stringPreferencesKey("base_urls")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val baseUrlsFlow: Flow<List<String>> = context.dataStore.data
        .map { prefs ->
            prefs[BASE_URLS]?.let { json.decodeFromString<List<String>>(it) }
                ?: emptyList()
        }

    val baseUrlFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[BASE_URL] }

    val accessTokenFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            val baseUrl = prefs[BASE_URL]
            if (baseUrl != null) {
                val key = stringPreferencesKey("${baseUrl}_access_token")
                prefs[key]
            } else {
                null
            }
        }

    val refreshTokenFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            val baseUrl = prefs[BASE_URL]
            if (baseUrl != null) {
                val key = stringPreferencesKey("${baseUrl}_refresh_token")
                prefs[key]
            } else {
                null
            }
        }

    val emailFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            val baseUrl = prefs[BASE_URL]
            if (baseUrl != null) {
                val key = stringPreferencesKey("${baseUrl}_email")
                prefs[key]
            } else {
                null
            }
        }

    val userIdFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            val baseUrl = prefs[BASE_URL]
            if (baseUrl != null) {
                val key = stringPreferencesKey("${baseUrl}_user_id")
                prefs[key]
            } else {
                null
            }
        }

    suspend fun addBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[BASE_URLS]
                ?.let { json.decodeFromString<List<String>>(it) }
                ?: emptyList()
            if (url !in current) {
                prefs[BASE_URLS] = json.encodeToString(current + url)
            }
        }
        saveBaseUrl(url)
    }

    suspend fun removeBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[BASE_URLS]
                ?.let { json.decodeFromString<List<String>>(it) }
                ?: return@edit
            val updated = current.filterNot { it == url }
            if (updated.isEmpty()) {
                prefs.remove(BASE_URLS)
            } else {
                prefs[BASE_URLS] = json.encodeToString(updated)
            }
        }
    }

    suspend fun saveAccessToken(accessToken: String) {
        context.dataStore.edit { prefs ->
            val baseUrl = prefs[BASE_URL] ?: return@edit
            val key = stringPreferencesKey("${baseUrl}_access_token")
            prefs[key] = accessToken
        }
    }

    suspend fun saveRefreshToken(accessToken: String) {
        context.dataStore.edit { prefs ->
            val baseUrl = prefs[BASE_URL] ?: return@edit
            val key = stringPreferencesKey("${baseUrl}_refresh_token")
            prefs[key] = accessToken
        }
    }

    suspend fun saveEmail(email: String) {
        context.dataStore.edit { prefs ->
            val baseUrl = prefs[BASE_URL] ?: return@edit
            val key = stringPreferencesKey("${baseUrl}_email")
            prefs[key] = email
        }
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { prefs ->
            val baseUrl = prefs[BASE_URL] ?: return@edit
            val key = stringPreferencesKey("${baseUrl}_user_id")
            prefs[key] = userId
        }
    }

    private suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[BASE_URL] = baseUrl
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}