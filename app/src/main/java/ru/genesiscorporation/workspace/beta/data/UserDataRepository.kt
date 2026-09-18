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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class ServerConfig(
    val id: String,
    val baseUrl: String,
    val imageUrl: String,
    val name: String,
    var needsToRelogin: Boolean
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
    private val sessionMutationMutex = Mutex()
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
        tokens?.let { saveTokens(config.id, it) }
    }
    suspend fun removeServer(serverId: String) {
        dataStore.edit { prefs ->
            val current = prefs[SERVERS]
                ?.let { json.decodeFromString<List<ServerConfig>>(it) }
                .orEmpty()
            prefs[SERVERS] = json.encodeToString(current.filterNot { it.id == serverId })
        }
        sessionMutationMutex.withLock {
            tokenStore.clear(serverId) // always clear secrets when removing
            tokensVersion.value = tokensVersion.value + 1
        }
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
        tokens?.let { saveTokens(config.id, it) }
    }

    suspend fun getServer(serverId: String): ServerConfig? {
        return serversFlow.first().find { it.id == serverId }
    }

    fun tokensFor(serverId: String): TokenPair? = tokenStore.get(serverId)

    suspend fun saveTokens(serverId: String, tokens: TokenPair) =
        sessionMutationMutex.withLock {
            saveTokensLocked(serverId, tokens)
        }

    suspend fun saveTokensAndClearRelogin(serverId: String, tokens: TokenPair) =
        sessionMutationMutex.withLock {
            saveTokensLocked(serverId, tokens)
            setNeedsToRelogin(false, serverId)
        }

    suspend fun saveRefreshedTokensIfCurrent(
        serverId: String,
        expectedRefreshToken: String,
        tokens: TokenPair,
    ): Boolean = sessionMutationMutex.withLock {
        val saved = tokenStore.saveIfRefreshTokenMatches(
            serverId = serverId,
            expectedRefreshToken = expectedRefreshToken,
            tokens = tokens,
        )
        if (saved) {
            tokensVersion.value = tokensVersion.value + 1
            setNeedsToRelogin(false, serverId)
        }
        saved
    }

    suspend fun setNeedsToReloginIfRefreshTokenCurrent(
        serverId: String,
        expectedRefreshToken: String?,
    ): Boolean = sessionMutationMutex.withLock {
        if (tokenStore.get(serverId)?.refreshToken != expectedRefreshToken) {
            return@withLock false
        }
        setNeedsToRelogin(true, serverId)
        true
    }

    suspend fun setNeedsToReloginIfTokensCurrent(
        serverId: String,
        expectedTokens: TokenPair,
    ): Boolean = sessionMutationMutex.withLock {
        if (tokenStore.get(serverId) != expectedTokens) {
            return@withLock false
        }
        setNeedsToRelogin(true, serverId)
        true
    }

    suspend fun setSelectedServerId(serverId: String?) {
        dataStore.edit { it[SELECTED_SERVER_ID] = serverId ?: "" }
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

    suspend fun setNeedsToRelogin(newValue: Boolean, serverUuid: String) {
        dataStore.edit { prefs ->
            val current = prefs[SERVERS]
                ?.let { json.decodeFromString<List<ServerConfig>>(it) }
                .orEmpty()
            val updated = current.map { server ->
                if (server.id == serverUuid) {
                    server.copy(needsToRelogin = newValue)
                } else {
                    server
                }
            }
            prefs[SERVERS] = json.encodeToString(updated)
        }
    }

    private fun saveTokensLocked(serverId: String, tokens: TokenPair) {
        tokenStore.save(serverId, tokens)
        tokensVersion.value = tokensVersion.value + 1
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
