package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest

@Serializable
data class PersistedRealtimeCursor(
    val epochVersion: Int,
    val epochGeneration: String,
) {
    init {
        require(epochVersion >= 0) {
            "Realtime epoch version must not be negative"
        }
        require(epochGeneration.isNotBlank()) {
            "Realtime epoch generation must not be blank"
        }
        require(epochGeneration.length <= MAX_EPOCH_GENERATION_CHARS) {
            "Realtime epoch generation is too long"
        }
    }
}

interface RealtimeCursorStore {
    suspend fun read(ownerKey: String): PersistedRealtimeCursor?
    suspend fun write(ownerKey: String, cursor: PersistedRealtimeCursor)
    suspend fun clearAccount(ownerKey: String)
}

class InMemoryRealtimeCursorStore : RealtimeCursorStore {
    private val mutationMutex = Mutex()
    private val cursors = mutableMapOf<String, PersistedRealtimeCursor>()

    override suspend fun read(
        ownerKey: String,
    ): PersistedRealtimeCursor? = mutationMutex.withLock {
        cursors[ownerKey]
    }

    override suspend fun write(
        ownerKey: String,
        cursor: PersistedRealtimeCursor,
    ) {
        mutationMutex.withLock {
            cursors[ownerKey] = cursor
        }
    }

    override suspend fun clearAccount(ownerKey: String) {
        mutationMutex.withLock {
            cursors.remove(ownerKey)
        }
    }
}

class TinkRealtimeCursorStore(
    context: Context,
) : RealtimeCursorStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE,
    )
    private val mutationMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    private val aead: Aead by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AeadConfig.register()
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, PREFERENCES_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
            .build()
        if (!manager.isUsingKeystore) {
            throw GeneralSecurityException(
                "Android Keystore is required for Workspace realtime state",
            )
        }
        manager.keysetHandle.getPrimitive(
            RegistryConfiguration.get(),
            Aead::class.java,
        )
    }

    override suspend fun read(
        ownerKey: String,
    ): PersistedRealtimeCursor? = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            requireOwnerKey(ownerKey)
            val encoded = preferences.getString(storageKey(ownerKey), null)
                ?: return@withLock null
            require(encoded.length <= MAX_ENCODED_CURSOR_CHARS) {
                "Encrypted realtime cursor exceeds its storage limit"
            }
            val plaintext = aead.decrypt(
                Base64.decode(encoded, Base64.NO_WRAP),
                associatedData(ownerKey),
            )
            require(plaintext.size <= MAX_PLAINTEXT_CURSOR_BYTES) {
                "Decrypted realtime cursor exceeds its storage limit"
            }
            json.decodeFromString<PersistedRealtimeCursor>(
                plaintext.toString(StandardCharsets.UTF_8),
            )
        }
    }

    override suspend fun write(
        ownerKey: String,
        cursor: PersistedRealtimeCursor,
    ) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            requireOwnerKey(ownerKey)
            val plaintext = json.encodeToString(cursor)
                .toByteArray(StandardCharsets.UTF_8)
            require(plaintext.size <= MAX_PLAINTEXT_CURSOR_BYTES) {
                "Realtime cursor exceeds its encrypted storage limit"
            }
            val ciphertext = aead.encrypt(
                plaintext,
                associatedData(ownerKey),
            )
            check(
                preferences.edit()
                    .putString(
                        storageKey(ownerKey),
                        Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                    )
                    .commit(),
            )
        }
    }

    override suspend fun clearAccount(ownerKey: String) =
        withContext(Dispatchers.IO) {
            mutationMutex.withLock {
                requireOwnerKey(ownerKey)
                check(
                    preferences.edit()
                        .remove(storageKey(ownerKey))
                        .commit(),
                )
            }
        }

    private fun storageKey(ownerKey: String): String =
        "${storageHash(ownerKey)}_cursor"

    private fun associatedData(ownerKey: String): ByteArray =
        "$ASSOCIATED_DATA_PREFIX:$ownerKey"
            .toByteArray(StandardCharsets.UTF_8)

    private fun storageHash(value: String): String =
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                value.toByteArray(StandardCharsets.UTF_8),
            ),
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
        )

    private fun requireOwnerKey(ownerKey: String) {
        require(ownerKey.isNotBlank()) {
            "Realtime cursor owner must not be blank"
        }
        require(ownerKey.length <= MAX_OWNER_KEY_CHARS) {
            "Realtime cursor owner is too long"
        }
    }

    companion object {
        const val PREFERENCES_FILE = "workspace_realtime_state"
        private const val KEYSET_NAME = "realtime_cursor_keyset"
        private const val MASTER_KEY_ALIAS = "workspace_realtime_cursor_master_key"
        private const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
        private const val ASSOCIATED_DATA_PREFIX = "workspace-realtime-cursor-v1"
        private const val MAX_OWNER_KEY_CHARS = 4_096
        private const val MAX_PLAINTEXT_CURSOR_BYTES = 1_024
        private const val MAX_ENCODED_CURSOR_CHARS = 4_096
    }
}

private const val MAX_EPOCH_GENERATION_CHARS = 256
