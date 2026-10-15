package ru.genesiscorporation.workspace.beta.data.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Separate from login tokens and message caches; keys never enter ordinary preferences. */
class EncryptedChatKeyStore internal constructor(
    context: Context,
    preferencesName: String,
    masterKeyAlias: String,
) : ChatKeyStorage by ChatKeyStorageImpl(
    EncryptedChatSecretRecords(context.applicationContext, preferencesName, masterKeyAlias),
) {
    constructor(context: Context) : this(context, "workspace_chat_secrets_v1", "workspace_chat_secrets_master_v1")
}

private class EncryptedChatSecretRecords(context: Context, preferencesName: String, masterKeyAlias: String) : ChatSecretRecords {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        preferencesName,
        MasterKey.Builder(context, masterKeyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(key: String): String? = preferences.getString(key, null)
    override fun write(key: String, value: String): Boolean = preferences.edit().putString(key, value).commit()
}
