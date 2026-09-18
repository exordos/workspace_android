package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class ServerConfig(
    val id: String,
    val baseUrl: String,
    val imageUrl: String,
    val name: String,
)
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

class ServerRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val tokenStore: SecureTokenStore,
    private val eventsRepositoryStore: EventsRepositoryStore,
    private val appScope: CoroutineScope,
) {
    constructor(context: Context, tokenStore: SecureTokenStore, eventsRepositoryStore: EventsRepositoryStore, appScope: CoroutineScope) : this(context.dataStore, tokenStore, eventsRepositoryStore, appScope)

    private val tokensVersion = MutableStateFlow(0)
    private val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")

    val selectedServerIdFlow: Flow<String?> =
        dataStore.data.map { it[SELECTED_SERVER_ID] }

    private val json = Json { ignoreUnknownKeys = true }
    private val SERVERS = stringPreferencesKey("server_configs")
    private val IS_UNREAD_FIRST = booleanPreferencesKey("is_unread_first")

    val isUnreadFirst: Flow<Boolean> =
        dataStore.data.map { it[IS_UNREAD_FIRST] ?: false }

    val serversFlow: Flow<List<ServerConfig>> = dataStore.data.map { prefs ->
        prefs[SERVERS]
            ?.let { json.decodeFromString<List<ServerConfig>>(it) }
            .orEmpty()
    }


    init {
        appScope.launch {
            serversFlow.collect { servers ->
                eventsRepositoryStore.syncWith(servers)
            }
        }
    }

    suspend fun saveServers(servers: List<ServerConfig>) {
        dataStore.edit { prefs ->
            prefs[SERVERS] = json.encodeToString(servers)
        }
    }

    suspend fun addServer(config: ServerConfig, tokens: TokenPair? = null) {
        dataStore.edit { prefs ->
            val current = prefs[SERVERS]
                ?.let { json.decodeFromString<List<ServerConfig>>(it) }
                .orEmpty()
            prefs[SERVERS] = json.encodeToString(current + config)
        }
        setSelectedServerId(config.id)
        eventsRepositoryStore.getOrCreate(config)
        tokens?.let { tokenStore.save(config.id, it) }
    }
    suspend fun removeServer(serverId: String) {
        dataStore.edit { prefs ->
            val current = prefs[SERVERS]
                ?.let { json.decodeFromString<List<ServerConfig>>(it) }
                .orEmpty()
            prefs[SERVERS] = json.encodeToString(current.filterNot { it.id == serverId })
        }
        tokenStore.clear(serverId) // always clear secrets when removing
    }

    suspend fun updateServer(
        config: ServerConfig,
        tokens: TokenPair? = null,
    ) {
        dataStore.edit { prefs ->
            val current = prefs[SERVERS]
                ?.let { json.decodeFromString<List<ServerConfig>>(it) }
                .orEmpty()
            val updated = current.map { if (it.id == config.id) config else it }
            prefs[SERVERS] = json.encodeToString(updated)
        }
        tokens?.let { tokenStore.save(config.id, it) }
    }

    suspend fun getServer(serverId: String): ServerConfig? {
        return serversFlow.first().find { it.id == serverId }
    }

    fun tokensFor(serverId: String): TokenPair? = tokenStore.get(serverId)

    fun saveTokens(serverId: String, tokens: TokenPair) {
        tokenStore.save(serverId, tokens)
        tokensVersion.value = tokensVersion.value + 1
    }

    fun saveRefreshedTokensIfCurrent(
        serverId: String,
        expectedRefreshToken: String,
        tokens: TokenPair,
    ): Boolean {
        val currentTokens = tokenStore.get(serverId) ?: return false
        if (currentTokens.refreshToken != expectedRefreshToken) return false
        saveTokens(serverId, tokens)
        return true
    }

    fun clearTokensIfRefreshTokenMatches(
        serverId: String,
        expectedRefreshToken: String,
    ): Boolean {
        val currentTokens = tokenStore.get(serverId) ?: return false
        if (currentTokens.refreshToken != expectedRefreshToken) return false
        tokenStore.clear(serverId)
        tokensVersion.value = tokensVersion.value + 1
        return true
    }

    suspend fun setSelectedServerId(serverId: String) {
        dataStore.edit { it[SELECTED_SERVER_ID] = serverId }
    }

    suspend fun setStreamsOrderIsUnreadFirst(isUnreadFirst: Boolean) {
        dataStore.edit { it[IS_UNREAD_FIRST] = isUnreadFirst }
    }

    val selectedAccessTokenFlow: Flow<String?> = combine(
        selectedServerIdFlow,
        tokensVersion,
    ) { id, _ ->
        id?.let { tokenStore.get(it)?.accessToken }
    }
    val selectedRefreshTokenFlow: Flow<String?> = combine(
        selectedServerIdFlow,
        tokensVersion,
    ) { id, _ ->
        id?.let { tokenStore.get(it)?.refreshToken }
    }

//    companion object {
//        private val BASE_URL = stringPreferencesKey("base_url")
//        private val BASE_URLS = stringPreferencesKey("base_urls")
//        private val json = Json { ignoreUnknownKeys = true }
//    }
//
//    val baseUrlsFlow: Flow<List<String>> = dataStore.data
//        .map { prefs ->
//            prefs[BASE_URLS]?.let { json.decodeFromString<List<String>>(it) }
//                ?: emptyList()
//        }
//
//    val baseUrlFlow: Flow<String?> = dataStore.data
//        .map { prefs -> prefs[BASE_URL] }
//
//    val accessTokenFlow: Flow<String?> = dataStore.data
//        .map { prefs ->
//            val baseUrl = prefs[BASE_URL]
//            if (baseUrl != null) {
//                val key = stringPreferencesKey("${baseUrl}_access_token")
//                prefs[key]
//            } else {
//                null
//            }
//        }
//
//    val accessToken: StateFlow<String?> = accessTokenFlow
//        .stateIn(
//            scope = scope,
//            started = SharingStarted.Eagerly,
//            initialValue = null,
//        )
//    fun getAccessToken(): String? = accessToken.value
//
//    val refreshTokenFlow: Flow<String?> = dataStore.data
//        .map { prefs ->
//            val baseUrl = prefs[BASE_URL]
//            if (baseUrl != null) {
//                val key = stringPreferencesKey("${baseUrl}_refresh_token")
//                prefs[key]
//            } else {
//                null
//            }
//        }
//
//    val emailFlow: Flow<String?> = dataStore.data
//        .map { prefs ->
//            val baseUrl = prefs[BASE_URL]
//            if (baseUrl != null) {
//                val key = stringPreferencesKey("${baseUrl}_email")
//                prefs[key]
//            } else {
//                null
//            }
//        }
//
//    val userIdFlow: Flow<String?> = dataStore.data
//        .map { prefs ->
//            val baseUrl = prefs[BASE_URL]
//            if (baseUrl != null) {
//                val key = stringPreferencesKey("${baseUrl}_user_id")
//                prefs[key]
//            } else {
//                null
//            }
//        }
//
//    suspend fun addBaseUrl(url: String) {
//        dataStore.edit { prefs ->
//            val current = prefs[BASE_URLS]
//                ?.let { json.decodeFromString<List<String>>(it) }
//                ?: emptyList()
//            if (url !in current) {
//                prefs[BASE_URLS] = json.encodeToString(current + url)
//            }
//        }
//        saveBaseUrl(url)
//    }
//
//    suspend fun removeBaseUrl(url: String) {
//        dataStore.edit { prefs ->
//            val current = prefs[BASE_URLS]
//                ?.let { json.decodeFromString<List<String>>(it) }
//                ?: return@edit
//            val updated = current.filterNot { it == url }
//            if (updated.isEmpty()) {
//                prefs.remove(BASE_URLS)
//            } else {
//                prefs[BASE_URLS] = json.encodeToString(updated)
//            }
//        }
//    }
//
//    suspend fun saveAccessToken(accessToken: String) {
//        dataStore.edit { prefs ->
//            val baseUrl = prefs[BASE_URL] ?: return@edit
//            val key = stringPreferencesKey("${baseUrl}_access_token")
//            prefs[key] = accessToken
//        }
//    }
//
//    suspend fun saveRefreshToken(accessToken: String) {
//        dataStore.edit { prefs ->
//            val baseUrl = prefs[BASE_URL] ?: return@edit
//            val key = stringPreferencesKey("${baseUrl}_refresh_token")
//            prefs[key] = accessToken
//        }
//    }
//
//    suspend fun saveEmail(email: String) {
//        dataStore.edit { prefs ->
//            val baseUrl = prefs[BASE_URL] ?: return@edit
//            val key = stringPreferencesKey("${baseUrl}_email")
//            prefs[key] = email
//        }
//    }
//
//    suspend fun saveUserId(userId: String) {
//        dataStore.edit { prefs ->
//            val baseUrl = prefs[BASE_URL] ?: return@edit
//            val key = stringPreferencesKey("${baseUrl}_user_id")
//            prefs[key] = userId
//        }
//    }
//
//    private suspend fun saveBaseUrl(baseUrl: String) {
//        dataStore.edit { prefs ->
//            prefs[BASE_URL] = baseUrl
//        }
//    }
//
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
            tokensVersion.value = tokensVersion.value + 1
        }
    }
}
