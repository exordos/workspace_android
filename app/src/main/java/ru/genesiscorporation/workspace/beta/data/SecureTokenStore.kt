package ru.genesiscorporation.workspace.beta.data
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenStore(context: Context) {
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

    fun clear(serverId: String) = synchronized(tokenLock) {
        prefs.edit()
            .remove(accessKey(serverId))
            .remove(refreshKey(serverId))
            .apply()
    }

    fun clearAll() = synchronized(tokenLock) {
        prefs.edit().clear().apply()
    }

    private fun accessKey(serverId: String) = "access_$serverId"
    private fun refreshKey(serverId: String) = "refresh_$serverId"

    private companion object {
        private val tokenLock = Any()
    }
}
