package ru.genesiscorporation.workspace.beta.modules.encryptedchat

/** Immutable identity captured when opening a chat. A session must never be rebound to another login. */
data class EncryptedChatScope(
    val server: String,
    val projectId: String,
    val userId: String,
    val runtimeGeneration: Long,
    val deviceId: String,
    val deviceName: String,
    val streamId: String,
)

/** Derived only from the current authenticated stream and participant catalog. */
data class EncryptedChatAccess(
    val scope: EncryptedChatScope,
    val currentKeyUuid: String,
    val ownerUuid: String,
    val rolesByUser: Map<String, String>,
    val isMember: Boolean = true,
    val encrypted: Boolean = true,
) {
    fun canIssue(userUuid: String): Boolean =
        userUuid == ownerUuid || rolesByUser[userUuid] in setOf("owner", "administrator")
}

/** Wire-only data. Content is always ciphertext for e2ee/key_grant. */
data class EncryptedChatPayload(
    val kind: String,
    val keyUuid: String,
    val content: String = "",
    val deviceId: String? = null,
    val deviceName: String? = null,
    val publicKey: String? = null,
    val requestMessageUuid: String? = null,
    val senderDeviceId: String? = null,
    val recipientDeviceId: String? = null,
)

data class EncryptedChatWireMessage(
    val uuid: String,
    val projectId: String,
    val streamId: String,
    val authorId: String,
    val payload: EncryptedChatPayload,
)

/** The transport must retain guard until immediately before its HTTP write and local commit. */
fun interface EncryptedChatServiceTransport {
    suspend fun send(
        payload: EncryptedChatPayload,
        canonicalUuid: String,
        rotateKey: Boolean,
        guard: () -> Boolean,
    ): EncryptedChatWireMessage
}

enum class EncryptedChatRequestStatus { IDLE, REQUESTED, RECEIVED, REJECTED }
enum class EncryptedChatRequestOutcome { PENDING, GRANTED, REJECTED }

data class EncryptedChatKeyRequest(
    val message: EncryptedChatWireMessage,
    val outcome: EncryptedChatRequestOutcome,
)

data class EncryptedChatLocalKey(val keyUuid: String, val createdAt: Long, val available: Boolean)

data class EncryptedChatSessionState(
    val currentKeyUuid: String? = null,
    val sendingKeyUuid: String? = null,
    val hasCurrentKey: Boolean = false,
    val pending: Boolean = false,
    val canManageKeys: Boolean = false,
    val canRotate: Boolean = false,
    val requestStatus: Map<String, EncryptedChatRequestStatus> = emptyMap(),
    val requests: List<EncryptedChatKeyRequest> = emptyList(),
    val localKeys: List<EncryptedChatLocalKey> = emptyList(),
    /** Consumers drop every decrypted presentation when this changes. */
    val keyRevision: Long = 0,
    /** Public local device identity, used only to present the correct recipient state. */
    val deviceId: String? = null,
    val error: EncryptedChatFailure? = null,
)

enum class EncryptedChatFailure {
    STALE, FORBIDDEN, MISSING_KEY, INVALID_MESSAGE, INVALID_CIPHERTEXT, STORAGE_FAILED,
    SEND_FAILED, TOO_LONG, UNAVAILABLE,
}

class EncryptedChatException(val failure: EncryptedChatFailure) : Exception(failure.name)

sealed interface EncryptedChatPresentation {
    /** Plaintext belongs only to the mounted presentation and must never replace a repository DTO. */
    data class Text(val text: String, val keyUuid: String) : EncryptedChatPresentation
    data class Locked(val keyUuid: String, val invalidCiphertext: Boolean = false) : EncryptedChatPresentation
    data class Service(val payload: EncryptedChatPayload) : EncryptedChatPresentation
}

data class EncryptedChatGrantOutcome(val requestMessageUuid: String, val failure: EncryptedChatFailure? = null)
