package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest

interface CredentialStore {
    fun read(accountKey: String, credential: Credential): String?
    fun write(accountKey: String, credential: Credential, value: String)
    fun remove(accountKey: String, credential: Credential)
    fun clear()
}

enum class Credential(val storageSuffix: String) {
    ACCESS_TOKEN("access_token"),
    REFRESH_TOKEN("refresh_token"),
}

class TinkCredentialStore(context: Context) : CredentialStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE,
    )
    private val aead: Aead by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AeadConfig.register()
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, PREFERENCES_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
            .build()
        if (!manager.isUsingKeystore) {
            throw GeneralSecurityException(
                "Android Keystore is required for Workspace credentials",
            )
        }
        manager.keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun read(accountKey: String, credential: Credential): String? {
        val encoded = preferences.getString(storageKey(accountKey, credential), null)
            ?: return null
        return runCatching {
            val plaintext = aead.decrypt(
                Base64.decode(encoded, Base64.NO_WRAP),
                associatedData(accountKey, credential),
            )
            plaintext.toString(StandardCharsets.UTF_8)
        }.getOrNull()
    }

    override fun write(accountKey: String, credential: Credential, value: String) {
        val ciphertext = aead.encrypt(
            value.toByteArray(StandardCharsets.UTF_8),
            associatedData(accountKey, credential),
        )
        check(
            preferences.edit()
                .putString(
                    storageKey(accountKey, credential),
                    Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                )
                .commit(),
        )
    }

    override fun remove(accountKey: String, credential: Credential) {
        check(preferences.edit().remove(storageKey(accountKey, credential)).commit())
    }

    override fun clear() {
        val editor = preferences.edit()
        preferences.all.keys
            .filterNot { it == KEYSET_NAME }
            .forEach(editor::remove)
        check(editor.commit())
    }

    private fun associatedData(accountKey: String, credential: Credential): ByteArray =
        "$ASSOCIATED_DATA_PREFIX:$accountKey:${credential.storageSuffix}"
            .toByteArray(StandardCharsets.UTF_8)

    private fun storageKey(accountKey: String, credential: Credential): String =
        "${
            Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(
                    accountKey.toByteArray(StandardCharsets.UTF_8),
                ),
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
            )
        }_${credential.storageSuffix}"

    companion object {
        const val PREFERENCES_FILE = "workspace_credentials"
        private const val KEYSET_NAME = "credential_keyset"
        private const val MASTER_KEY_ALIAS = "workspace_credentials_master_key"
        private const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
        private const val ASSOCIATED_DATA_PREFIX = "workspace-credential-v1"
    }
}
