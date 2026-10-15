package ru.genesiscorporation.workspace.beta.data
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyOwner
import ru.genesiscorporation.workspace.beta.data.crypto.EncryptedChatKeyStore

class SecureTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_tokens",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    fun get(serverId: String): TokenPair? = synchronized(tokenLock) {
        val access = prefs.getString(accessKey(serverId), null)
            ?: return@synchronized null
        val refresh = prefs.getString(refreshKey(serverId), null)
            ?: return@synchronized null
        TokenPair(access, refresh)
    }
    fun save(serverId: String, tokens: TokenPair) = synchronized(tokenLock) {
        prefs.edit()
            .putString(accessKey(serverId), tokens.accessToken)
            .putString(refreshKey(serverId), tokens.refreshToken)
            .apply()
    }

    fun saveIfRefreshTokenMatches(
        serverId: String,
        expectedRefreshToken: String,
        tokens: TokenPair,
    ): Boolean = synchronized(tokenLock) {
        if (prefs.getString(refreshKey(serverId), null) != expectedRefreshToken) {
            return@synchronized false
        }
        prefs.edit()
            .putString(accessKey(serverId), tokens.accessToken)
            .putString(refreshKey(serverId), tokens.refreshToken)
            .apply()
        true
    }

    /** Written only by a validated /me; survives expiry/restart without trusting replacement credentials. */
    internal fun rememberConfirmedOwner(server: ServerConfig, userUuid: String) = synchronized(tokenLock) {
        val project = server.projectUuid ?: return@synchronized
        val owner = ChatKeyOwner(server.baseUrl, project, userUuid)
        check(prefs.edit().putString(ownerKey(server.id), Json.encodeToString(owner)).commit())
    }

    fun clear(serverId: String) {
        val record = synchronized(tokenLock) { prefs.getString(ownerKey(serverId), null) }
        record?.let { check(EncryptedChatKeyStore(appContext).purgeOwner(Json.decodeFromString<ChatKeyOwner>(it))) }
        synchronized(tokenLock) {
            check(prefs.getString(ownerKey(serverId), null) == record)
            check(prefs.edit()
                .remove(accessKey(serverId))
                .remove(refreshKey(serverId))
                .remove(ownerKey(serverId))
                .commit())
        }
    }

    fun clearAll() = synchronized(tokenLock) {
        prefs.edit().clear().apply()
    }

    private fun accessKey(serverId: String) = "access_$serverId"
    private fun refreshKey(serverId: String) = "refresh_$serverId"
    private fun ownerKey(serverId: String) = "confirmed_chat_owner_$serverId"

    private companion object {
        private val tokenLock = Any()
    }
}
