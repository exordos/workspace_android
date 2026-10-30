package com.exordos.workspace.data.crypto

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class ChatKeyOwner(val serverUrl: String, val projectUuid: String, val userUuid: String) {
    init {
        val uri = URI(serverUrl)
        require(uri.scheme in setOf("https", "http") && uri.host != null && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && projectUuid.isNotBlank() && userUuid.isNotBlank())
    }
}

data class ChatDeviceIdentity(val deviceId: String, val deviceName: String)
data class ChatKeyScope(val owner: ChatKeyOwner, val deviceId: String)
data class ChatKeyAddress(val scope: ChatKeyScope, val streamUuid: String, val keyUuid: String)
data class StoredChatKeyMetadata(val keyUuid: String, val receivedAt: Long)

class PendingChatKeyRequest(
    val localRequestId: String,
    val requestMessageUuid: String?,
    val keyPair: ChatRequestKeyPair,
    val createdAt: Long,
    val recoveryEnvelope: ByteArray? = null,
    val previousGroupKey: ByteArray? = null,
)

data class ChatKeyRequestCompletion(
    val localRequestId: String,
    val requestMessageUuid: String,
    val responseMessageUuid: String,
    val outcome: String,
)

/** The issuer's immutable decision, persisted before its first network attempt. */
@Serializable
data class IssuedChatKeyResponse(
    val keyUuid: String,
    val requestMessageUuid: String,
    val requestAuthorUuid: String,
    val recipientDeviceId: String,
    val requestPublicKey: String,
    val canonicalUuid: String,
    val kind: String,
    val content: String = "",
    val responseMessageUuid: String? = null,
    val deletionRevision: Long? = null,
)

data class ChatKeyRotation(val streamUuid: String, val previousKeyUuid: String, val topicUuid: String)
class StagedChatCreation(
    val creationId: String,
    val keyUuid: String,
    val groupKey: ByteArray,
    val rotation: ChatKeyRotation? = null,
)

class ChatKeyStorageException(val reason: String) : IllegalStateException(reason)

/** Blocking durable transactions. Call from IO; instances share an in-process lock. */
interface ChatKeyStorage {
    /** Explicit logout only: destroys every secret in this owner namespace. */
    fun purgeOwner(owner: ChatKeyOwner): Boolean
    fun ensureDeviceIdentity(owner: ChatKeyOwner, deviceName: String = "Workspace Android"): ChatDeviceIdentity
    fun readDeviceIdentity(owner: ChatKeyOwner): ChatDeviceIdentity?
    fun readChatKey(address: ChatKeyAddress): ByteArray?
    fun readChatKeyDeletionRevision(address: ChatKeyAddress): Long
    fun listChatKeys(scope: ChatKeyScope, streamUuid: String): List<StoredChatKeyMetadata>
    fun readLastAvailableChatKey(scope: ChatKeyScope, streamUuid: String): Pair<String, ByteArray>?
    fun writeChatKey(address: ChatKeyAddress, groupKey: ByteArray, forSending: Boolean = false,
                     expectedDeletionRevision: Long? = null, onlyIfPresent: Boolean = false): Boolean
    fun removeChatKey(address: ChatKeyAddress): Boolean
    fun listPendingRequestKeyUuids(scope: ChatKeyScope, streamUuid: String): List<String>
    fun listPendingRequests(address: ChatKeyAddress): List<PendingChatKeyRequest>
    fun writePendingRequest(address: ChatKeyAddress, request: PendingChatKeyRequest): Boolean
    fun bindPendingRequest(address: ChatKeyAddress, localRequestId: String, requestMessageUuid: String): Boolean
    fun readRequestCompletion(address: ChatKeyAddress, localRequestId: String): ChatKeyRequestCompletion?
    fun readRequestCompletionByMessageUuid(address: ChatKeyAddress, requestMessageUuid: String): ChatKeyRequestCompletion?
    fun completeGrant(address: ChatKeyAddress, localRequestId: String, requestMessageUuid: String,
                      responseMessageUuid: String, groupKey: ByteArray,
                      expectedDeletionRevision: Long? = null, replaceExistingKey: Boolean = false): Boolean
    fun completeReject(address: ChatKeyAddress, localRequestId: String,
                       requestMessageUuid: String, responseMessageUuid: String): Boolean
    fun readIssuedResponse(address: ChatKeyAddress, requestMessageUuid: String): IssuedChatKeyResponse?
    /** First writer wins, including across different store/session instances. */
    fun stageIssuedResponse(address: ChatKeyAddress, response: IssuedChatKeyResponse): IssuedChatKeyResponse?
    fun confirmIssuedResponse(address: ChatKeyAddress, requestMessageUuid: String,
                              canonicalUuid: String, responseMessageUuid: String): Boolean
    fun listStagedCreations(scope: ChatKeyScope): List<StagedChatCreation>
    fun writeStagedCreation(scope: ChatKeyScope, creation: StagedChatCreation): Boolean
    fun finalizeCreation(scope: ChatKeyScope, creationId: String, streamUuid: String): Boolean
    fun discardStagedCreation(scope: ChatKeyScope, creationId: String): Boolean
}
