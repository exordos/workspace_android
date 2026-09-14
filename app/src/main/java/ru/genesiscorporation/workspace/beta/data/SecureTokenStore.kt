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
    fun get(serverId: String): TokenPair? {
        val access = prefs.getString(accessKey(serverId), null) ?: return null
        val refresh = prefs.getString(refreshKey(serverId), null) ?: return null
        return TokenPair(access, refresh)
    }
    fun save(serverId: String, tokens: TokenPair) {
        prefs.edit()
            .putString(accessKey(serverId), tokens.accessToken)
            .putString(refreshKey(serverId), tokens.refreshToken)
            .apply()
    }
    fun clear(serverId: String) {
        prefs.edit()
            .remove(accessKey(serverId))
            .remove(refreshKey(serverId))
            .apply()
    }
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    private fun accessKey(serverId: String) = "access_$serverId"
    private fun refreshKey(serverId: String) = "refresh_$serverId"
}