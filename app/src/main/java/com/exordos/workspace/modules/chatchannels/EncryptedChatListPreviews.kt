package com.exordos.workspace.modules.chatchannels

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.exordos.workspace.data.WorkspaceDateTime
import com.exordos.workspace.data.ConfirmedAccountContext
import com.exordos.workspace.data.crypto.*
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import com.exordos.workspace.modules.encryptedchat.latestEncryptedChatWireMessages
import com.exordos.workspace.modules.encryptedchat.EncryptedChatScope
import com.exordos.workspace.modules.encryptedchat.encryptedChatStorageScopeMatches

internal data class EncryptedPreviewSource(
    val streamUuid: String, val messageUuid: String, val keyUuid: String, val ciphertext: String,
    val authorized: Boolean, val catalogMessageUuid: String?, val authorName: String?, val createdAt: String,
)

private val encryptedServiceKinds = setOf("key_request", "key_grant", "key_reject", "key_announced")

internal fun latestLoadedEncryptedPreviewMessage(stream: Stream, pool: List<MessageResponse>, conversations: List<MessageResponse>): MessageResponse? {
    val known = latestEncryptedChatWireMessages(listOfNotNull(stream.lastMessage) + pool, conversations, stream.uuid)
    val last = known.firstOrNull { it.uuid == stream.lastMessageUuid } ?: return null
    if (last.payload.kind == "e2ee") return last
    if (last.payload.kind !in encryptedServiceKinds) return null
    val time = runCatching { WorkspaceDateTime.parse(last.createdAt) }.getOrNull() ?: return null
    return known.filter { it.payload.kind == "e2ee" && runCatching { WorkspaceDateTime.parse(it.createdAt) <= time }.getOrDefault(false) }
        .maxWithOrNull(compareBy<MessageResponse> { WorkspaceDateTime.parse(it.createdAt) }.thenBy { it.uuid })
}

/** Captures only public wire fields; repository objects and ciphertext are never replaced with plaintext. */
internal fun encryptedPreviewSource(stream: Stream, message: MessageResponse?, ownUserUuid: String,
                                    projectUuid: String, bindings: List<StreamBindingResponseData>?, catalogLast: MessageResponse? = stream.lastMessage): EncryptedPreviewSource? {
    if (message == null || !stream.encryption || message.payload.kind != "e2ee" || message.payload.keyUuid.isNullOrBlank()) return null
    val memberRoles = setOf("owner", "administrator", "moderator", "member")
    val authorized = stream.userUuid == ownUserUuid && (stream.projectId == null || stream.projectId == projectUuid) &&
        stream.role in memberRoles && (bindings == null || bindings.any {
            it.streamUuid == stream.uuid && it.userUuid == ownUserUuid && it.role in memberRoles
        }) && (message.uuid == stream.lastMessageUuid || (catalogLast != null && catalogLast.uuid == stream.lastMessageUuid && catalogLast.payload.kind in encryptedServiceKinds &&
            runCatching { WorkspaceDateTime.parse(message.createdAt) <= WorkspaceDateTime.parse(catalogLast.createdAt) }.getOrDefault(false))) && message.streamUuid == stream.uuid &&
        message.userUuid == ownUserUuid && (message.projectId == null || message.projectId == projectUuid)
    return EncryptedPreviewSource(stream.uuid, message.uuid, requireNotNull(message.payload.keyUuid), message.payload.content, authorized,
        stream.lastMessageUuid, message.user?.displayableName(), message.createdAt)
}

internal data class EncryptedPreviewSnapshot(val account: ConfirmedAccountContext, val rows: List<EncryptedPreviewSource>) {
    fun matches(other: EncryptedPreviewSnapshot?): Boolean = other != null && account.matches(other.account) && rows == other.rows
}

data class EncryptedChatListPreview(val messageUuid: String, val keyUuid: String, val text: String,
                                    val catalogMessageUuid: String?, val authorName: String?, val createdAt: String)

/** Local read-only projection for the composed list rows. No session, network, identity creation, or disk writes. */
internal class EncryptedChatListPreviewProjector(
    private val currentSnapshot: () -> EncryptedPreviewSnapshot?,
    private val storage: () -> ChatKeyStorage,
    private val decrypt: suspend (ByteArray, ByteArray, ChatMessageContext) -> ByteArray = { key, envelope, context ->
        WorkspaceChatCrypto().decryptMessage(key, envelope, context)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private var revision = 0L
    private var published: EncryptedPreviewSnapshot? = null
    private var account: ConfirmedAccountContext? = null
    private var storageScope: EncryptedChatScope? = null
    private val mutablePreviews = MutableStateFlow<Map<String, EncryptedChatListPreview>>(emptyMap())
    val previews = mutablePreviews.asStateFlow()

    fun accountChanged(): Boolean = synchronized(lock) {
        val current = currentSnapshot()?.account
        if (current == null) account != null else !current.matches(account)
    }

    fun affectedBy(change: EncryptedChatScope): Boolean = synchronized(lock) {
        val current = currentSnapshot() ?: return@synchronized false
        val scope = storageScope ?: return@synchronized false
        account?.matches(current.account) == true && current.rows.any {
            encryptedChatStorageScopeMatches(change, scope.copy(streamId = it.streamUuid))
        }
    }

    fun invalidate() = synchronized(lock) {
        revision++
        published = null
        mutablePreviews.value = emptyMap()
    }

    fun preview(streamUuid: String): EncryptedChatListPreview? = synchronized(lock) {
        if (published?.matches(currentSnapshot()) != true) null else mutablePreviews.value[streamUuid]
    }

    suspend fun refresh() = withContext(dispatcher) {
        val captured = currentSnapshot() ?: return@withContext
        val epoch = synchronized(lock) {
            if (!captured.account.matches(account)) storageScope = null
            account = captured.account
            revision
        }
        try {
            val owner = captured.account.owner
            val keys = storage()
            val device = keys.readDeviceIdentity(owner) ?: return@withContext
            val scope = ChatKeyScope(owner, device.deviceId)
            synchronized(lock) {
                if (revision != epoch || !captured.matches(currentSnapshot())) return@withContext
                storageScope = EncryptedChatScope(owner.serverUrl, owner.projectUuid, owner.userUuid, 0,
                    device.deviceId, device.deviceName, "")
            }
            val projected = linkedMapOf<String, EncryptedChatListPreview>()
            val keyRevisions = linkedMapOf<ChatKeyAddress, Long>()
            for (row in captured.rows.filter { it.authorized }) {
                if (synchronized(lock) { revision != epoch } || !captured.matches(currentSnapshot())) return@withContext
                val address = ChatKeyAddress(scope, row.streamUuid, row.keyUuid)
                val deletion = keys.readChatKeyDeletionRevision(address)
                val key = keys.readChatKey(address) ?: continue
                try {
                    val plaintext = try { decrypt(key, WorkspaceChatCrypto.fromBase64(row.ciphertext), ChatMessageContext(row.streamUuid, row.keyUuid)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { continue }
                    try {
                        if (deletion == keys.readChatKeyDeletionRevision(address)) {
                            projected[row.streamUuid] = EncryptedChatListPreview(row.messageUuid, row.keyUuid, plaintext.toString(Charsets.UTF_8),
                                row.catalogMessageUuid, row.authorName, row.createdAt)
                            keyRevisions[address] = deletion
                        }
                    } finally { plaintext.fill(0) }
                } finally { key.fill(0) }
            }
            if (keyRevisions.any { (address, deletion) -> keys.readChatKeyDeletionRevision(address) != deletion }) return@withContext
            synchronized(lock) {
                if (revision == epoch && captured.matches(currentSnapshot())) {
                    published = captured
                    mutablePreviews.value = projected
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Missing/corrupt local credentials remain an opaque placeholder. */ }
    }
}
