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
import java.util.UUID

@Serializable
data class PersistedAttachment(
    val uri: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long? = null,
)

@Serializable
data class PersistedComposerDraft(
    val text: String = "",
    val quotedMessageUuid: String? = null,
    val attachments: List<PersistedAttachment> = emptyList(),
)

@Serializable
data class PersistedConversationRoute(
    val streamUuid: String,
    val topicUuid: String,
    val chatTitle: String,
    val topicName: String? = null,
    val isDirectMessages: Boolean,
)

@Serializable
data class PersistedOutboxEntry(
    val localMessageUuid: String,
    val streamUuid: String,
    val topicUuid: String,
    val content: String,
    val createdAt: String,
    val lastAttemptAt: String = createdAt,
    val knownMatchingMessageUuids: List<String> = emptyList(),
    val status: PersistedOutboxStatus,
    val errorMessage: String? = null,
)

@Serializable
enum class PersistedOutboxStatus {
    SENDING,
    FAILED,
    UNCERTAIN,
}

@Serializable
enum class PersistedDraftSyncStatus {
    LOCAL,
    SAVING,
    SAVED,
    FAILED,
    CONFLICT,
    DELETING,
}

@Serializable
data class PersistedDraftConflict(
    val serverContent: String,
    val serverEntityTag: String,
    val serverRevision: Int,
    val serverUpdatedAt: String,
)

@Serializable
data class PersistedServerDraftState(
    val draftUuid: String,
    val entityTag: String? = null,
    val serverRevision: Int? = null,
    val syncedContent: String? = null,
    val pendingCreateContent: String? = null,
    val serverUpdatedAt: String? = null,
    val status: PersistedDraftSyncStatus = PersistedDraftSyncStatus.LOCAL,
    val conflict: PersistedDraftConflict? = null,
    val deleteRequested: Boolean = false,
    val errorMessage: String? = null,
)

@Serializable
data class PersistedConversationState(
    val route: PersistedConversationRoute? = null,
    val draftStorageSlot: String? = null,
    val draftText: String = "",
    val editingMessageUuid: String? = null,
    val quotedMessageUuid: String? = null,
    val attachments: List<PersistedAttachment> = emptyList(),
    val suspendedDraft: PersistedComposerDraft? = null,
    val outbox: List<PersistedOutboxEntry> = emptyList(),
    val draftUpdatedAt: String? = null,
    val serverDraft: PersistedServerDraftState? = null,
    val lastIncomingShareRequestId: String? = null,
)

@Serializable
private data class PersistedConversationIndex(
    val entries: List<PersistedConversationIndexEntry> = emptyList(),
)

@Serializable
private data class PersistedConversationIndexEntry(
    val streamUuid: String,
    val topicUuid: String,
    val draftStorageSlot: String? = null,
)

interface ConversationStateStore {
    suspend fun read(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String? = null,
    ): PersistedConversationState?

    suspend fun write(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        state: PersistedConversationState,
        draftStorageSlot: String? = null,
    )

    suspend fun list(ownerKey: String): List<PersistedConversationState>

    suspend fun remove(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String? = null,
    )

    suspend fun clearAccount(ownerKey: String)
}

class TinkConversationStateStore(
    context: Context,
) : ConversationStateStore {
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
                "Android Keystore is required for Workspace message state",
            )
        }
        manager.keysetHandle.getPrimitive(
            RegistryConfiguration.get(),
            Aead::class.java,
        )
    }

    override suspend fun read(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String?,
    ): PersistedConversationState? = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            readLocked(
                ownerKey,
                streamUuid,
                topicUuid,
                canonicalStorageSlot(draftStorageSlot),
            )
        }
    }

    override suspend fun write(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        state: PersistedConversationState,
        draftStorageSlot: String?,
    ) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val canonicalSlot = canonicalStorageSlot(draftStorageSlot)
            require(state.draftStorageSlot == canonicalSlot) {
                "Conversation state storage slot does not match its key"
            }
            val plaintext = json.encodeToString(state)
                .toByteArray(StandardCharsets.UTF_8)
            require(plaintext.size <= MAX_PLAINTEXT_STATE_BYTES) {
                "Conversation state exceeds the encrypted storage limit"
            }
            val ciphertext = aead.encrypt(
                plaintext,
                associatedData(ownerKey, streamUuid, topicUuid, canonicalSlot),
            )
            val index = readIndexLocked(ownerKey)
            val entry = PersistedConversationIndexEntry(
                streamUuid = streamUuid,
                topicUuid = topicUuid,
                draftStorageSlot = canonicalSlot,
            )
            val nextEntries = (
                index.entries.filterNot {
                    it.streamUuid == streamUuid &&
                        it.topicUuid == topicUuid &&
                        it.draftStorageSlot == canonicalSlot
                } + entry
            )
            require(nextEntries.size <= MAX_INDEX_ENTRIES) {
                "Conversation index exceeds its storage limit"
            }
            val encryptedIndex = encryptIndex(ownerKey, nextEntries)
            check(
                preferences.edit()
                    .putString(
                        storageKey(
                            ownerKey,
                            streamUuid,
                            topicUuid,
                            canonicalSlot,
                        ),
                        Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                    )
                    .putString(indexStorageKey(ownerKey), encryptedIndex)
                    .commit(),
            )
        }
    }

    override suspend fun list(
        ownerKey: String,
    ): List<PersistedConversationState> = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val index = readIndexLocked(ownerKey)
            val liveEntries = mutableListOf<PersistedConversationIndexEntry>()
            val states = index.entries.mapNotNull { entry ->
                readLocked(
                    ownerKey,
                    entry.streamUuid,
                    entry.topicUuid,
                    entry.draftStorageSlot,
                )
                    ?.also { liveEntries += entry }
            }
            if (liveEntries.size != index.entries.size) {
                check(
                    preferences.edit()
                        .putString(
                            indexStorageKey(ownerKey),
                            encryptIndex(ownerKey, liveEntries),
                        )
                        .commit(),
                )
            }
            states
        }
    }

    override suspend fun remove(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String?,
    ) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            removeLocked(
                ownerKey,
                streamUuid,
                topicUuid,
                canonicalStorageSlot(draftStorageSlot),
            )
        }
    }

    override suspend fun clearAccount(ownerKey: String) =
        withContext(Dispatchers.IO) {
            mutationMutex.withLock {
                val ownerPrefix = "${storageHash(ownerKey)}_"
                val editor = preferences.edit()
                preferences.all.keys
                    .filter { it.startsWith(ownerPrefix) }
                    .forEach(editor::remove)
                check(editor.commit())
            }
        }

    private fun removeLocked(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String?,
    ) {
        val nextEntries = readIndexLocked(ownerKey).entries.filterNot {
            it.streamUuid == streamUuid &&
                it.topicUuid == topicUuid &&
                it.draftStorageSlot == draftStorageSlot
        }
        check(
            preferences.edit()
                .remove(
                    storageKey(
                        ownerKey,
                        streamUuid,
                        topicUuid,
                        draftStorageSlot,
                    ),
                )
                .putString(
                    indexStorageKey(ownerKey),
                    encryptIndex(ownerKey, nextEntries),
                )
                .commit(),
        )
    }

    private fun readLocked(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String?,
    ): PersistedConversationState? {
        val encoded = preferences.getString(
            storageKey(
                ownerKey,
                streamUuid,
                topicUuid,
                draftStorageSlot,
            ),
            null,
        ) ?: return null
        require(encoded.length <= MAX_ENCODED_STATE_CHARS) {
            "Encrypted conversation state exceeds its storage limit"
        }
        val plaintext = aead.decrypt(
            Base64.decode(encoded, Base64.NO_WRAP),
            associatedData(
                ownerKey,
                streamUuid,
                topicUuid,
                draftStorageSlot,
            ),
        )
        require(plaintext.size <= MAX_PLAINTEXT_STATE_BYTES) {
            "Decrypted conversation state exceeds its storage limit"
        }
        return json.decodeFromString<PersistedConversationState>(
            plaintext.toString(StandardCharsets.UTF_8),
        ).also { state ->
            require(state.draftStorageSlot == draftStorageSlot) {
                "Conversation state storage slot does not match its key"
            }
        }
    }

    private fun readIndexLocked(ownerKey: String): PersistedConversationIndex {
        val encoded = preferences.getString(indexStorageKey(ownerKey), null)
            ?: return PersistedConversationIndex()
        require(encoded.length <= MAX_ENCODED_INDEX_CHARS) {
            "Encrypted conversation index exceeds its storage limit"
        }
        val plaintext = aead.decrypt(
            Base64.decode(encoded, Base64.NO_WRAP),
            indexAssociatedData(ownerKey),
        )
        require(plaintext.size <= MAX_PLAINTEXT_INDEX_BYTES) {
            "Decrypted conversation index exceeds its storage limit"
        }
        val decoded = json.decodeFromString<PersistedConversationIndex>(
            plaintext.toString(StandardCharsets.UTF_8),
        )
        require(decoded.entries.size <= MAX_INDEX_ENTRIES) {
            "Conversation index exceeds its entry limit"
        }
        require(decoded.entries.distinct().size == decoded.entries.size) {
            "Conversation index contains duplicate entries"
        }
        require(decoded.entries.all(::reasonableIndexEntry)) {
            "Conversation index contains malformed identifiers"
        }
        return decoded
    }

    private fun encryptIndex(
        ownerKey: String,
        entries: List<PersistedConversationIndexEntry>,
    ): String {
        val plaintext = json.encodeToString(PersistedConversationIndex(entries))
            .toByteArray(StandardCharsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_INDEX_BYTES) {
            "Conversation index exceeds its encrypted storage limit"
        }
        return Base64.encodeToString(
            aead.encrypt(plaintext, indexAssociatedData(ownerKey)),
            Base64.NO_WRAP,
        )
    }

    private fun reasonableIndexEntry(
        entry: PersistedConversationIndexEntry,
    ): Boolean =
        entry.streamUuid.isNotBlank() &&
            entry.streamUuid.length <= MAX_INDEX_IDENTIFIER_CHARS &&
            entry.topicUuid.isNotBlank() &&
            entry.topicUuid.length <= MAX_INDEX_IDENTIFIER_CHARS &&
            runCatching {
                canonicalStorageSlot(entry.draftStorageSlot) ==
                    entry.draftStorageSlot
            }.getOrDefault(false)

    private fun storageKey(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String?,
    ): String {
        val routeKey = if (draftStorageSlot == null) {
            "$streamUuid\u0000$topicUuid"
        } else {
            "$streamUuid\u0000$topicUuid\u0000$draftStorageSlot"
        }
        return "${storageHash(ownerKey)}_${storageHash(routeKey)}"
    }

    private fun indexStorageKey(ownerKey: String): String =
        "${storageHash(ownerKey)}_index"

    private fun associatedData(
        ownerKey: String,
        streamUuid: String,
        topicUuid: String,
        draftStorageSlot: String?,
    ): ByteArray {
        val suffix = draftStorageSlot?.let { ":$it" }.orEmpty()
        return (
            "$ASSOCIATED_DATA_PREFIX:$ownerKey:$streamUuid:$topicUuid$suffix"
        ).toByteArray(StandardCharsets.UTF_8)
    }

    private fun indexAssociatedData(ownerKey: String): ByteArray =
        "$ASSOCIATED_DATA_PREFIX:$ownerKey:index"
            .toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val PREFERENCES_FILE = "workspace_message_state"
        private const val KEYSET_NAME = "message_state_keyset"
        private const val MASTER_KEY_ALIAS = "workspace_message_state_master_key"
        private const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
        private const val ASSOCIATED_DATA_PREFIX = "workspace-message-state-v1"
        private const val MAX_PLAINTEXT_STATE_BYTES = 512 * 1024
        private const val MAX_ENCODED_STATE_CHARS = 1024 * 1024
        private const val MAX_INDEX_ENTRIES = 2_000
        private const val MAX_INDEX_IDENTIFIER_CHARS = 128
        private const val MAX_PLAINTEXT_INDEX_BYTES = 512 * 1024
        private const val MAX_ENCODED_INDEX_CHARS = 1024 * 1024
    }
}

private fun canonicalStorageSlot(value: String?): String? {
    value ?: return null
    val trimmed = value.trim()
    val canonical = runCatching { UUID.fromString(trimmed).toString() }
        .getOrNull()
        ?: throw IllegalArgumentException(
            "Draft storage slots must be canonical UUIDs",
        )
    require(canonical.equals(trimmed, ignoreCase = true)) {
        "Draft storage slots must be canonical UUIDs"
    }
    return canonical
}

private fun storageHash(value: String): String =
    Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(
            value.toByteArray(StandardCharsets.UTF_8),
        ),
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
    )
