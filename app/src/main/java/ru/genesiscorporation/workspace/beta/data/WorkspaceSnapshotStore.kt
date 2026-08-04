package ru.genesiscorporation.workspace.beta.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.resolvedActiveUnreadCount
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID

data class WorkspaceSnapshot(
    val streams: List<Stream> = emptyList(),
    val topicsByStream: Map<String, List<TopicsResponseData>> = emptyMap(),
    val messagesByConversation: Map<String, List<MessageResponse>> = emptyMap(),
    val paginationByConversation:
        Map<String, ConversationPaginationState> = emptyMap(),
    val folders: List<FolderResponseData> = emptyList(),
    val users: List<UserResponseData> = emptyList(),
    val streamBindings: List<StreamBindingResponseData> = emptyList(),
)

@Serializable
enum class ConversationWindowMode {
    LATEST,
    CONTEXT,
    UNKNOWN,
}

data class ConversationPaginationState(
    val streamUuid: String,
    val topicUuid: String,
    val mode: ConversationWindowMode,
    val contextAnchorUuid: String? = null,
    val olderPageMarker: String? = null,
    val newerPageMarker: String? = null,
)

enum class WorkspaceTimelineKind(
    val wireValue: String,
) {
    FEED("feed"),
    STARRED("starred"),
    /**
     * Zero-row owner-bound marker for an Inbox catalog that completed
     * successfully. Inbox rows themselves remain in the encrypted workspace
     * catalog tables; this marker distinguishes authoritative empty from an
     * account that has never synchronized Inbox.
     */
    INBOX("inbox"),
}

data class WorkspaceTimelineSnapshot(
    val messages: List<MessageResponse> = emptyList(),
    val nextPageMarker: String? = null,
)

interface WorkspaceSnapshotStore {
    suspend fun read(ownerKey: String): WorkspaceSnapshot
    suspend fun write(ownerKey: String, snapshot: WorkspaceSnapshot)
    suspend fun readTimeline(
        ownerKey: String,
        kind: WorkspaceTimelineKind,
    ): WorkspaceTimelineSnapshot?
    suspend fun writeTimeline(
        ownerKey: String,
        kind: WorkspaceTimelineKind,
        snapshot: WorkspaceTimelineSnapshot,
    )
    suspend fun clearAccount(ownerKey: String)
}

class InMemoryWorkspaceSnapshotStore : WorkspaceSnapshotStore {
    private val mutationMutex = Mutex()
    private val snapshots = mutableMapOf<String, WorkspaceSnapshot>()
    private val timelines =
        mutableMapOf<Pair<String, WorkspaceTimelineKind>, WorkspaceTimelineSnapshot>()

    override suspend fun read(ownerKey: String): WorkspaceSnapshot =
        mutationMutex.withLock {
            snapshots[ownerKey] ?: WorkspaceSnapshot()
        }

    override suspend fun write(
        ownerKey: String,
        snapshot: WorkspaceSnapshot,
    ) {
        mutationMutex.withLock {
            snapshots[ownerKey] = snapshot
        }
    }

    override suspend fun readTimeline(
        ownerKey: String,
        kind: WorkspaceTimelineKind,
    ): WorkspaceTimelineSnapshot? = mutationMutex.withLock {
        timelines[ownerKey to kind]
    }

    override suspend fun writeTimeline(
        ownerKey: String,
        kind: WorkspaceTimelineKind,
        snapshot: WorkspaceTimelineSnapshot,
    ) {
        mutationMutex.withLock {
            timelines[ownerKey to kind] = snapshot
        }
    }

    override suspend fun clearAccount(ownerKey: String) {
        mutationMutex.withLock {
            snapshots.remove(ownerKey)
            timelines.keys.removeAll { it.first == ownerKey }
        }
    }
}

@SuppressLint("LogNotTimber") // Timber is transitive; this app uses android.util.Log.
class RoomWorkspaceSnapshotStore internal constructor(
    private val dao: WorkspaceSnapshotDao,
    private val cipher: WorkspaceSnapshotCipher,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : WorkspaceSnapshotStore {
    private val mutationMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    override suspend fun read(
        ownerKey: String,
    ): WorkspaceSnapshot = withContext(Dispatchers.IO) {
        require(ownerKey.isNotBlank()) {
            "Snapshot owner must not be blank"
        }
        mutationMutex.withLock {
            decodeRows(
                ownerKey = ownerKey,
                rows = dao.readAccount(
                    ownerKeyHash = ownerKeyHash(ownerKey),
                    streamLimit = MAX_STREAMS,
                    topicLimit = MAX_TOPICS,
                    messageLimit = MAX_MESSAGES_PER_ACCOUNT,
                    conversationPaginationLimit =
                        MAX_CACHED_CONVERSATIONS,
                    folderLimit = MAX_FOLDERS,
                    userLimit = MAX_USERS,
                    streamBindingLimit = MAX_STREAM_BINDINGS,
                    maxEncryptedBytes = MAX_ENCRYPTED_PAYLOAD_BYTES,
                ),
            )
        }
    }

    override suspend fun write(
        ownerKey: String,
        snapshot: WorkspaceSnapshot,
    ) = withContext(Dispatchers.IO) {
        require(ownerKey.isNotBlank()) {
            "Snapshot owner must not be blank"
        }
        mutationMutex.withLock {
            val ownerHash = ownerKeyHash(ownerKey)
            val cachedAtMillis = clockMillis()
            val rows = encodeSnapshot(
                ownerKey = ownerKey,
                ownerKeyHash = ownerHash,
                snapshot = snapshot,
                cachedAtMillis = cachedAtMillis,
            )
            dao.replaceAccount(
                ownerKeyHash = ownerHash,
                streams = rows.streams,
                topics = rows.topics,
                messages = rows.messages,
                conversationPagination = rows.conversationPagination,
                folders = rows.folders,
                users = rows.users,
                streamBindings = rows.streamBindings,
            )
        }
    }

    override suspend fun readTimeline(
        ownerKey: String,
        kind: WorkspaceTimelineKind,
    ): WorkspaceTimelineSnapshot? = withContext(Dispatchers.IO) {
        require(ownerKey.isNotBlank()) {
            "Timeline owner must not be blank"
        }
        mutationMutex.withLock {
            decodeTimelineRows(
                ownerKey = ownerKey,
                kind = kind,
                rows = dao.readTimeline(
                    ownerKeyHash = ownerKeyHash(ownerKey),
                    kind = kind.wireValue,
                    messageLimit = MAX_TIMELINE_MESSAGES,
                    maxEncryptedBytes = MAX_ENCRYPTED_PAYLOAD_BYTES,
                ),
            )
        }
    }

    override suspend fun writeTimeline(
        ownerKey: String,
        kind: WorkspaceTimelineKind,
        snapshot: WorkspaceTimelineSnapshot,
    ) = withContext(Dispatchers.IO) {
        require(ownerKey.isNotBlank()) {
            "Timeline owner must not be blank"
        }
        mutationMutex.withLock {
            val ownerHash = ownerKeyHash(ownerKey)
            val cachedAtMillis = clockMillis()
            val rows = encodeTimeline(
                ownerKey = ownerKey,
                ownerKeyHash = ownerHash,
                kind = kind,
                snapshot = snapshot,
                cachedAtMillis = cachedAtMillis,
            )
            dao.replaceTimeline(
                ownerKeyHash = ownerHash,
                kind = kind.wireValue,
                timeline = rows.timeline,
                messages = rows.messages,
            )
        }
    }

    override suspend fun clearAccount(
        ownerKey: String,
    ) = withContext(Dispatchers.IO) {
        require(ownerKey.isNotBlank()) {
            "Snapshot owner must not be blank"
        }
        mutationMutex.withLock {
            dao.clearAccount(ownerKeyHash(ownerKey))
        }
    }

    private fun encodeSnapshot(
        ownerKey: String,
        ownerKeyHash: String,
        snapshot: WorkspaceSnapshot,
        cachedAtMillis: Long,
    ): CachedWorkspaceRows {
        val streams = snapshot.streams
            .asSequence()
            .filter { isCanonicalUuid(it.uuid) }
            .distinctBy(Stream::uuid)
            .take(MAX_STREAMS)
            .mapIndexedNotNull { index, stream ->
                val plaintext = encodeBoundedOrNull(
                    stream,
                    MAX_CATALOG_PAYLOAD_BYTES,
                ) ?: return@mapIndexedNotNull null
                CachedStreamEntity(
                    ownerKeyHash = ownerKeyHash,
                    uuid = stream.uuid,
                    position = index,
                    encryptedPayload = cipher.encrypt(
                        plaintext,
                        associatedData(
                            ownerKey = ownerKey,
                            kind = SnapshotRowKind.STREAM,
                            uuid = stream.uuid,
                            position = index,
                        ),
                    ),
                    cachedAtMillis = cachedAtMillis,
                )
            }
            .toList()
        val streamUuids = streams.mapTo(mutableSetOf()) { it.uuid }

        val topics = streams
            .asSequence()
            .flatMap { stream ->
                snapshot.topicsByStream[stream.uuid]
                    .orEmpty()
                    .asSequence()
                    .filter {
                        isCanonicalUuid(it.uuid) &&
                            it.streamUuid == stream.uuid
                    }
                    .distinctBy(TopicsResponseData::uuid)
                    .mapIndexedNotNull { index, topic ->
                        val plaintext = encodeBoundedOrNull(
                            topic,
                            MAX_CATALOG_PAYLOAD_BYTES,
                        ) ?: return@mapIndexedNotNull null
                        CachedTopicEntity(
                            ownerKeyHash = ownerKeyHash,
                            uuid = topic.uuid,
                            streamUuid = topic.streamUuid,
                            position = index,
                            encryptedPayload = cipher.encrypt(
                                plaintext,
                                associatedData(
                                    ownerKey = ownerKey,
                                    kind = SnapshotRowKind.TOPIC,
                                    uuid = topic.uuid,
                                    streamUuid = topic.streamUuid,
                                    position = index,
                                ),
                            ),
                            cachedAtMillis = cachedAtMillis,
                        )
                    }
            }
            .take(MAX_TOPICS)
            .toList()
        val topicKeys = topics
            .mapTo(mutableSetOf()) { it.streamUuid to it.uuid }

        val selectedConversations = snapshot.messagesByConversation
            .asSequence()
            .mapNotNull { (key, messages) ->
                val ids = parseConversationKey(key) ?: return@mapNotNull null
                if (
                    ids.first !in streamUuids ||
                    ids !in topicKeys
                ) {
                    return@mapNotNull null
                }
                val canonicalMessages = messages
                    .asSequence()
                    .filter {
                        !it.uuid.startsWith(LOCAL_MESSAGE_UUID_PREFIX) &&
                            isCanonicalUuid(it.uuid) &&
                            it.streamUuid == ids.first &&
                            it.topicUuid == ids.second &&
                            parseTimestampMillis(it.createdAt) != null &&
                            parseTimestampMillis(it.updatedAt) != null
                    }
                    .distinctBy(MessageResponse::uuid)
                    .sortedWith(
                        compareBy<MessageResponse>(
                            { parseTimestampMillis(it.createdAt) },
                            MessageResponse::uuid,
                        ),
                    )
                    .toList()
                if (canonicalMessages.isEmpty()) {
                    null
                } else {
                    val retainedMessages = canonicalMessages
                        .takeLast(MAX_MESSAGES_PER_CONVERSATION)
                    CachedConversation(
                        streamUuid = ids.first,
                        topicUuid = ids.second,
                        messages = retainedMessages,
                        sourceFirstMessageUuid =
                            canonicalMessages.first().uuid,
                        sourceLastMessageUuid =
                            canonicalMessages.last().uuid,
                        newestMessageMillis =
                            parseTimestampMillis(
                                retainedMessages.last().createdAt,
                            ) ?: Long.MIN_VALUE,
                    )
                }
            }
            .sortedByDescending(CachedConversation::newestMessageMillis)
            .take(MAX_CACHED_CONVERSATIONS)
            .toList()

        var remainingMessages = MAX_MESSAGES_PER_ACCOUNT
        val messages = buildList {
            selectedConversations.forEach { conversation ->
                if (remainingMessages == 0) return@forEach
                val selected = conversation.messages
                    .takeLast(remainingMessages)
                selected.forEachIndexed { index, message ->
                    val createdAtMillis =
                        requireNotNull(parseTimestampMillis(message.createdAt))
                    val updatedAtMillis =
                        requireNotNull(parseTimestampMillis(message.updatedAt))
                    val plaintext = encodeBoundedOrNull(
                        message,
                        MAX_MESSAGE_PAYLOAD_BYTES,
                    ) ?: return@forEachIndexed
                    add(
                        CachedMessageEntity(
                            ownerKeyHash = ownerKeyHash,
                            uuid = message.uuid,
                            streamUuid = message.streamUuid,
                            topicUuid = message.topicUuid,
                            position = index,
                            createdAtMillis = createdAtMillis,
                            updatedAtMillis = updatedAtMillis,
                            encryptedPayload = cipher.encrypt(
                                plaintext,
                                associatedData(
                                    ownerKey = ownerKey,
                                    kind = SnapshotRowKind.MESSAGE,
                                    uuid = message.uuid,
                                    streamUuid = message.streamUuid,
                                    topicUuid = message.topicUuid,
                                    position = index,
                                ),
                            ),
                            cachedAtMillis = cachedAtMillis,
                        ),
                    )
                }
                remainingMessages -= selected.size
            }
        }

        val messageUuidsByConversation = messages
            .groupBy {
                conversationKey(it.streamUuid, it.topicUuid)
            }
            .mapValues { (_, rows) ->
                rows.sortedBy(CachedMessageEntity::position)
                    .map(CachedMessageEntity::uuid)
            }
        val selectedConversationsByKey = selectedConversations.associateBy {
            conversationKey(it.streamUuid, it.topicUuid)
        }
        val conversationPagination = snapshot.paginationByConversation
            .asSequence()
            .mapNotNull { (key, state) ->
                val conversation = selectedConversationsByKey[key]
                    ?: return@mapNotNull null
                if (
                    state.streamUuid != conversation.streamUuid ||
                    state.topicUuid != conversation.topicUuid
                ) {
                    return@mapNotNull null
                }
                val retainedMessageUuids =
                    messageUuidsByConversation[key].orEmpty()
                val normalized = normalizeConversationPaginationState(
                    state = state,
                    retainedMessageUuids = retainedMessageUuids,
                    sourceFirstMessageUuid =
                        conversation.sourceFirstMessageUuid,
                    sourceLastMessageUuid =
                        conversation.sourceLastMessageUuid,
                    cacheIsComplete =
                        retainedMessageUuids.size ==
                            conversation.messages.size,
                ) ?: return@mapNotNull null
                val metadata = CachedConversationPaginationMetadata(
                    streamUuid = normalized.streamUuid,
                    topicUuid = normalized.topicUuid,
                    mode = normalized.mode,
                    contextAnchorUuid = normalized.contextAnchorUuid,
                    olderPageMarker = normalized.olderPageMarker,
                    newerPageMarker = normalized.newerPageMarker,
                    messageCount = retainedMessageUuids.size,
                    firstMessageUuid = retainedMessageUuids.first(),
                    lastMessageUuid = retainedMessageUuids.last(),
                )
                val plaintext = encodeBoundedOrNull(
                    metadata,
                    MAX_CONVERSATION_PAGINATION_PAYLOAD_BYTES,
                ) ?: return@mapNotNull null
                CachedConversationPaginationEntity(
                    ownerKeyHash = ownerKeyHash,
                    streamUuid = normalized.streamUuid,
                    topicUuid = normalized.topicUuid,
                    encryptedPayload = cipher.encrypt(
                        plaintext,
                        associatedData(
                            ownerKey = ownerKey,
                            kind =
                                SnapshotRowKind.CONVERSATION_PAGINATION,
                            uuid = "",
                            streamUuid = normalized.streamUuid,
                            topicUuid = normalized.topicUuid,
                            position = -1,
                        ),
                    ),
                    cachedAtMillis = cachedAtMillis,
                )
            }
            .take(MAX_CACHED_CONVERSATIONS)
            .toList()

        val folders = snapshot.folders
            .asSequence()
            .mapNotNull { folder ->
                validatedFolderOrNull(folder, streamUuids)
            }
            .distinctBy(FolderResponseData::uuid)
            .take(MAX_FOLDERS)
            .mapIndexedNotNull { index, folder ->
                val plaintext = encodeBoundedOrNull(
                    folder,
                    MAX_CATALOG_PAYLOAD_BYTES,
                ) ?: return@mapIndexedNotNull null
                CachedFolderEntity(
                    ownerKeyHash = ownerKeyHash,
                    uuid = folder.uuid,
                    position = index,
                    encryptedPayload = cipher.encrypt(
                        plaintext,
                        associatedData(
                            ownerKey = ownerKey,
                            kind = SnapshotRowKind.FOLDER,
                            uuid = folder.uuid,
                            position = index,
                        ),
                    ),
                    cachedAtMillis = cachedAtMillis,
                )
            }
            .toList()

        val users = snapshot.users
            .asSequence()
            .filter(::isValidCachedUser)
            .distinctBy(UserResponseData::uuid)
            .take(MAX_USERS)
            .mapIndexedNotNull { index, user ->
                val plaintext = encodeBoundedOrNull(
                    user,
                    MAX_USER_PAYLOAD_BYTES,
                ) ?: return@mapIndexedNotNull null
                CachedUserEntity(
                    ownerKeyHash = ownerKeyHash,
                    uuid = user.uuid,
                    position = index,
                    encryptedPayload = cipher.encrypt(
                        plaintext,
                        associatedData(
                            ownerKey = ownerKey,
                            kind = SnapshotRowKind.USER,
                            uuid = user.uuid,
                            position = index,
                        ),
                    ),
                    cachedAtMillis = cachedAtMillis,
                )
            }
            .toList()

        val streamBindings = snapshot.streamBindings
            .asSequence()
            .filter { isValidCachedBinding(it, streamUuids) }
            .distinctBy(StreamBindingResponseData::uuid)
            .take(MAX_STREAM_BINDINGS)
            .mapIndexedNotNull { index, binding ->
                val plaintext = encodeBoundedOrNull(
                    binding,
                    MAX_STREAM_BINDING_PAYLOAD_BYTES,
                ) ?: return@mapIndexedNotNull null
                CachedStreamBindingEntity(
                    ownerKeyHash = ownerKeyHash,
                    uuid = binding.uuid,
                    streamUuid = binding.streamUuid,
                    position = index,
                    encryptedPayload = cipher.encrypt(
                        plaintext,
                        associatedData(
                            ownerKey = ownerKey,
                            kind = SnapshotRowKind.STREAM_BINDING,
                            uuid = binding.uuid,
                            streamUuid = binding.streamUuid,
                            position = index,
                        ),
                    ),
                    cachedAtMillis = cachedAtMillis,
                )
            }
            .toList()

        return CachedWorkspaceRows(
            streams = streams,
            topics = topics,
            messages = messages,
            conversationPagination = conversationPagination,
            folders = folders,
            users = users,
            streamBindings = streamBindings,
        )
    }

    private fun decodeRows(
        ownerKey: String,
        rows: CachedWorkspaceRows,
    ): WorkspaceSnapshot {
        val streams = rows.streams.mapNotNull { row ->
            decodeRow<Stream>(
                row.encryptedPayload,
                MAX_CATALOG_PAYLOAD_BYTES,
                associatedData(
                    ownerKey = ownerKey,
                    kind = SnapshotRowKind.STREAM,
                    uuid = row.uuid,
                    position = row.position,
                ),
            )?.takeIf { stream ->
                stream.uuid == row.uuid &&
                    isCanonicalUuid(stream.uuid)
            }
        }
        val streamUuids = streams.mapTo(mutableSetOf(), Stream::uuid)

        val topics = rows.topics.mapNotNull { row ->
            if (row.streamUuid !in streamUuids) return@mapNotNull null
            decodeRow<TopicsResponseData>(
                row.encryptedPayload,
                MAX_CATALOG_PAYLOAD_BYTES,
                associatedData(
                    ownerKey = ownerKey,
                    kind = SnapshotRowKind.TOPIC,
                    uuid = row.uuid,
                    streamUuid = row.streamUuid,
                    position = row.position,
                ),
            )?.takeIf { topic ->
                topic.uuid == row.uuid &&
                    topic.streamUuid == row.streamUuid &&
                    isCanonicalUuid(topic.uuid)
            }
        }
        val topicKeys = topics.mapTo(mutableSetOf()) {
            it.streamUuid to it.uuid
        }

        val messages = rows.messages.mapNotNull { row ->
            if ((row.streamUuid to row.topicUuid) !in topicKeys) {
                return@mapNotNull null
            }
            decodeRow<MessageResponse>(
                row.encryptedPayload,
                MAX_MESSAGE_PAYLOAD_BYTES,
                associatedData(
                    ownerKey = ownerKey,
                    kind = SnapshotRowKind.MESSAGE,
                    uuid = row.uuid,
                    streamUuid = row.streamUuid,
                    topicUuid = row.topicUuid,
                    position = row.position,
                ),
            )?.takeIf { message ->
                message.uuid == row.uuid &&
                    message.streamUuid == row.streamUuid &&
                    message.topicUuid == row.topicUuid &&
                    isCanonicalUuid(message.uuid) &&
                    parseTimestampMillis(message.createdAt) ==
                        row.createdAtMillis &&
                    parseTimestampMillis(message.updatedAt) ==
                        row.updatedAtMillis
            }
        }
        val messagesByConversation = messages.groupBy {
            conversationKey(it.streamUuid, it.topicUuid)
        }

        val conversationPagination = rows.conversationPagination
            .asSequence()
            .distinctBy { row ->
                conversationKey(row.streamUuid, row.topicUuid)
            }
            .mapNotNull { row ->
                val key = conversationKey(
                    row.streamUuid,
                    row.topicUuid,
                )
                val retainedMessageUuids = messagesByConversation[key]
                    .orEmpty()
                    .map(MessageResponse::uuid)
                if (retainedMessageUuids.isEmpty()) {
                    return@mapNotNull null
                }
                val metadata =
                    decodeRow<CachedConversationPaginationMetadata>(
                        ciphertext = row.encryptedPayload,
                        maximumBytes =
                            MAX_CONVERSATION_PAGINATION_PAYLOAD_BYTES,
                        associatedData = associatedData(
                            ownerKey = ownerKey,
                            kind =
                                SnapshotRowKind.CONVERSATION_PAGINATION,
                            uuid = "",
                            streamUuid = row.streamUuid,
                            topicUuid = row.topicUuid,
                            position = -1,
                        ),
                    )?.takeIf {
                        it.streamUuid == row.streamUuid &&
                            it.topicUuid == row.topicUuid &&
                            it.messageCount in
                                1..MAX_MESSAGES_PER_CONVERSATION &&
                            isCanonicalUuid(it.firstMessageUuid) &&
                            isCanonicalUuid(it.lastMessageUuid)
                    } ?: return@mapNotNull null
                val normalized = normalizeConversationPaginationState(
                    state = ConversationPaginationState(
                        streamUuid = metadata.streamUuid,
                        topicUuid = metadata.topicUuid,
                        mode = metadata.mode,
                        contextAnchorUuid =
                            metadata.contextAnchorUuid,
                        olderPageMarker =
                            metadata.olderPageMarker,
                        newerPageMarker =
                            metadata.newerPageMarker,
                    ),
                    retainedMessageUuids = retainedMessageUuids,
                    sourceFirstMessageUuid =
                        metadata.firstMessageUuid,
                    sourceLastMessageUuid =
                        metadata.lastMessageUuid,
                    cacheIsComplete =
                        metadata.messageCount ==
                            retainedMessageUuids.size,
                ) ?: return@mapNotNull null
                key to normalized
            }
            .toMap()

        val folders = rows.folders.mapNotNull { row ->
            decodeRow<FolderResponseData>(
                row.encryptedPayload,
                MAX_CATALOG_PAYLOAD_BYTES,
                associatedData(
                    ownerKey = ownerKey,
                    kind = SnapshotRowKind.FOLDER,
                    uuid = row.uuid,
                    position = row.position,
                ),
            )?.takeIf { it.uuid == row.uuid }
                ?.let { validatedFolderOrNull(it, streamUuids) }
        }

        val users = rows.users.mapNotNull { row ->
            decodeRow<UserResponseData>(
                row.encryptedPayload,
                MAX_USER_PAYLOAD_BYTES,
                associatedData(
                    ownerKey = ownerKey,
                    kind = SnapshotRowKind.USER,
                    uuid = row.uuid,
                    position = row.position,
                ),
            )?.takeIf { user ->
                user.uuid == row.uuid && isValidCachedUser(user)
            }
        }

        val streamBindings = rows.streamBindings.mapNotNull { row ->
            if (row.streamUuid !in streamUuids) return@mapNotNull null
            decodeRow<StreamBindingResponseData>(
                row.encryptedPayload,
                MAX_STREAM_BINDING_PAYLOAD_BYTES,
                associatedData(
                    ownerKey = ownerKey,
                    kind = SnapshotRowKind.STREAM_BINDING,
                    uuid = row.uuid,
                    streamUuid = row.streamUuid,
                    position = row.position,
                ),
            )?.takeIf { binding ->
                binding.uuid == row.uuid &&
                    binding.streamUuid == row.streamUuid &&
                    isValidCachedBinding(binding, streamUuids)
            }
        }

        return WorkspaceSnapshot(
            streams = streams,
            topicsByStream = topics.groupBy(TopicsResponseData::streamUuid),
            messagesByConversation = messagesByConversation,
            paginationByConversation = conversationPagination,
            folders = folders,
            users = users,
            streamBindings = streamBindings,
        )
    }

    private fun encodeTimeline(
        ownerKey: String,
        ownerKeyHash: String,
        kind: WorkspaceTimelineKind,
        snapshot: WorkspaceTimelineSnapshot,
        cachedAtMillis: Long,
    ): EncodedTimelineRows {
        val canonicalMessages = if (kind == WorkspaceTimelineKind.INBOX) {
            emptyList()
        } else {
            snapshot.messages
                .asSequence()
                .filter { message ->
                    !message.uuid.startsWith(LOCAL_MESSAGE_UUID_PREFIX) &&
                        isValidTimelineMessage(message, kind)
                }
                .distinctBy(MessageResponse::uuid)
                .sortedWith(TIMELINE_MESSAGE_COMPARATOR)
                .toList()
        }
        val retainedMessages = canonicalMessages.takeLast(MAX_TIMELINE_MESSAGES)
        val sourceWasTrimmed =
            retainedMessages.size < canonicalMessages.size
        val requestedMarker = snapshot.nextPageMarker
            ?.takeIf(::isCanonicalUuid)
            ?.takeIf { marker ->
                canonicalMessages.firstOrNull()?.uuid == marker
            }
        val markerWasRequested = snapshot.nextPageMarker != null
        val nextMarker = when {
            retainedMessages.isEmpty() -> null
            sourceWasTrimmed -> retainedMessages.first().uuid
            requestedMarker != null -> retainedMessages.first().uuid
            markerWasRequested -> retainedMessages.first().uuid
            else -> null
        }
        val messages = retainedMessages.mapIndexedNotNull { index, message ->
            val plaintext = encodeBoundedOrNull(
                message,
                MAX_MESSAGE_PAYLOAD_BYTES,
            ) ?: return@mapIndexedNotNull null
            val createdAtMillis =
                requireNotNull(parseTimestampMillis(message.createdAt))
            val updatedAtMillis =
                requireNotNull(parseTimestampMillis(message.updatedAt))
            CachedTimelineMessageEntity(
                ownerKeyHash = ownerKeyHash,
                kind = kind.wireValue,
                uuid = message.uuid,
                streamUuid = message.streamUuid,
                topicUuid = message.topicUuid,
                position = index,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
                encryptedPayload = cipher.encrypt(
                    plaintext,
                    timelineAssociatedData(
                        ownerKey = ownerKey,
                        kind = kind,
                        rowKind = TimelineRowKind.MESSAGE,
                        uuid = message.uuid,
                        streamUuid = message.streamUuid,
                        topicUuid = message.topicUuid,
                        position = index,
                    ),
                ),
                cachedAtMillis = cachedAtMillis,
            )
        }
        val persistedMarker = when {
            messages.isEmpty() -> null
            messages.size != retainedMessages.size -> messages.first().uuid
            nextMarker != null -> messages.first().uuid
            else -> null
        }
        val metadata = CachedTimelineMetadata(
            kind = kind.wireValue,
            messageCount = messages.size,
            firstMessageUuid = messages.firstOrNull()?.uuid,
            lastMessageUuid = messages.lastOrNull()?.uuid,
            nextPageMarker = persistedMarker,
        )
        val metadataPlaintext = requireNotNull(
            encodeBoundedOrNull(
                metadata,
                MAX_TIMELINE_METADATA_PAYLOAD_BYTES,
            ),
        )
        return EncodedTimelineRows(
            timeline = CachedTimelineEntity(
                ownerKeyHash = ownerKeyHash,
                kind = kind.wireValue,
                encryptedPayload = cipher.encrypt(
                    metadataPlaintext,
                    timelineAssociatedData(
                        ownerKey = ownerKey,
                        kind = kind,
                        rowKind = TimelineRowKind.METADATA,
                    ),
                ),
                cachedAtMillis = cachedAtMillis,
            ),
            messages = messages,
        )
    }

    private fun decodeTimelineRows(
        ownerKey: String,
        kind: WorkspaceTimelineKind,
        rows: CachedTimelineRows,
    ): WorkspaceTimelineSnapshot? {
        val timeline = rows.timeline ?: return null
        if (timeline.kind != kind.wireValue) return null
        val metadata = decodeRow<CachedTimelineMetadata>(
            ciphertext = timeline.encryptedPayload,
            maximumBytes = MAX_TIMELINE_METADATA_PAYLOAD_BYTES,
            associatedData = timelineAssociatedData(
                ownerKey = ownerKey,
                kind = kind,
                rowKind = TimelineRowKind.METADATA,
            ),
        )?.takeIf { cached ->
            cached.kind == kind.wireValue &&
                (
                    kind != WorkspaceTimelineKind.INBOX ||
                        cached.messageCount == 0
                ) &&
                cached.messageCount in 0..MAX_TIMELINE_MESSAGES &&
                cached.firstMessageUuid?.let(::isCanonicalUuid) != false &&
                cached.lastMessageUuid?.let(::isCanonicalUuid) != false &&
                cached.nextPageMarker?.let(::isCanonicalUuid) != false &&
                (
                    cached.messageCount > 0 ||
                        (
                            cached.firstMessageUuid == null &&
                                cached.lastMessageUuid == null &&
                                cached.nextPageMarker == null
                        )
                )
        } ?: return null

        val messages = rows.messages.mapNotNull { row ->
            if (
                row.kind != kind.wireValue ||
                row.position !in 0 until MAX_TIMELINE_MESSAGES
            ) {
                return@mapNotNull null
            }
            decodeRow<MessageResponse>(
                ciphertext = row.encryptedPayload,
                maximumBytes = MAX_MESSAGE_PAYLOAD_BYTES,
                associatedData = timelineAssociatedData(
                    ownerKey = ownerKey,
                    kind = kind,
                    rowKind = TimelineRowKind.MESSAGE,
                    uuid = row.uuid,
                    streamUuid = row.streamUuid,
                    topicUuid = row.topicUuid,
                    position = row.position,
                ),
            )?.takeIf { message ->
                message.uuid == row.uuid &&
                    message.streamUuid == row.streamUuid &&
                    message.topicUuid == row.topicUuid &&
                    parseTimestampMillis(message.createdAt) ==
                        row.createdAtMillis &&
                    parseTimestampMillis(message.updatedAt) ==
                        row.updatedAtMillis &&
                    isValidTimelineMessage(message, kind)
            }
        }.sortedWith(TIMELINE_MESSAGE_COMPARATOR)

        if (metadata.messageCount == 0) {
            return WorkspaceTimelineSnapshot()
                .takeIf { messages.isEmpty() }
        }
        if (messages.isEmpty()) return null

        val cacheIsComplete =
            messages.size == metadata.messageCount &&
                messages.first().uuid == metadata.firstMessageUuid &&
                messages.last().uuid == metadata.lastMessageUuid
        return WorkspaceTimelineSnapshot(
            messages = messages,
            nextPageMarker = if (cacheIsComplete) {
                metadata.nextPageMarker
            } else {
                messages.first().uuid
            },
        )
    }

    private inline fun <reified T> encodeBoundedOrNull(
        value: T,
        maximumBytes: Int,
    ): ByteArray? {
        val plaintext = json.encodeToString(value)
            .toByteArray(StandardCharsets.UTF_8)
        return plaintext.takeIf { it.size <= maximumBytes }
    }

    private inline fun <reified T> decodeRow(
        ciphertext: ByteArray,
        maximumBytes: Int,
        associatedData: ByteArray,
    ): T? {
        if (ciphertext.size > MAX_ENCRYPTED_PAYLOAD_BYTES) return null
        return try {
            val plaintext = cipher.decrypt(ciphertext, associatedData)
            if (plaintext.size > maximumBytes) return null
            json.decodeFromString<T>(
                plaintext.toString(StandardCharsets.UTF_8),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            when (exception) {
                is SerializationException,
                is IllegalArgumentException,
                is java.security.GeneralSecurityException -> {
                    Log.d(TAG, "Ignored an invalid encrypted snapshot row")
                }
                else -> Log.d(TAG, "Failed to decode a snapshot row")
            }
            null
        }
    }

    companion object {
        fun create(context: Context): RoomWorkspaceSnapshotStore =
            RoomWorkspaceSnapshotStore(
                dao = WorkspaceSnapshotDatabase
                    .getInstance(context)
                    .snapshotDao(),
                cipher = TinkWorkspaceSnapshotCipher(context),
            )
    }
}

internal fun conversationKey(
    streamUuid: String,
    topicUuid: String,
): String = "$streamUuid.$topicUuid"

private fun parseConversationKey(
    key: String,
): Pair<String, String>? {
    val separator = key.indexOf('.')
    if (separator <= 0 || separator == key.lastIndex) return null
    val streamUuid = key.substring(0, separator)
    val topicUuid = key.substring(separator + 1)
    return (streamUuid to topicUuid).takeIf {
        isCanonicalUuid(streamUuid) && isCanonicalUuid(topicUuid)
    }
}

internal fun unknownConversationPaginationState(
    streamUuid: String,
    topicUuid: String,
    retainedMessageUuids: List<String>,
): ConversationPaginationState? {
    val canonicalRows = retainedMessageUuids
        .filter(::isCanonicalUuid)
        .distinctBy { it.lowercase() }
    if (
        !isCanonicalUuid(streamUuid) ||
        !isCanonicalUuid(topicUuid) ||
        canonicalRows.isEmpty()
    ) {
        return null
    }
    return ConversationPaginationState(
        streamUuid = streamUuid,
        topicUuid = topicUuid,
        mode = ConversationWindowMode.UNKNOWN,
        olderPageMarker = canonicalRows.first(),
        newerPageMarker = canonicalRows.last(),
    )
}

internal fun normalizeConversationPaginationState(
    state: ConversationPaginationState,
    retainedMessageUuids: List<String>,
    sourceFirstMessageUuid: String,
    sourceLastMessageUuid: String,
    cacheIsComplete: Boolean = true,
): ConversationPaginationState? {
    if (
        !isCanonicalUuid(state.streamUuid) ||
        !isCanonicalUuid(state.topicUuid)
    ) {
        return null
    }
    val retained = retainedMessageUuids
        .filter(::isCanonicalUuid)
        .distinctBy { it.lowercase() }
    if (retained.isEmpty()) return null
    val unknown = unknownConversationPaginationState(
        streamUuid = state.streamUuid,
        topicUuid = state.topicUuid,
        retainedMessageUuids = retained,
    ) ?: return null
    if (
        !cacheIsComplete ||
        !isCanonicalUuid(sourceFirstMessageUuid) ||
        !isCanonicalUuid(sourceLastMessageUuid)
    ) {
        return unknown
    }
    val actualUuidByCanonical = retained.associateBy(String::lowercase)
    fun retainedUuid(value: String?): String? =
        value
            ?.takeIf(::isCanonicalUuid)
            ?.lowercase()
            ?.let(actualUuidByCanonical::get)

    val sourceFirstRetained =
        retainedUuid(sourceFirstMessageUuid) == retained.first()
    val sourceLastRetained =
        retainedUuid(sourceLastMessageUuid) == retained.last()
    val identifiersAreValid =
        state.contextAnchorUuid?.let(::isCanonicalUuid) != false &&
            state.olderPageMarker?.let(::isCanonicalUuid) != false &&
            state.newerPageMarker?.let(::isCanonicalUuid) != false
    if (!identifiersAreValid) return unknown

    val contextAnchor = retainedUuid(state.contextAnchorUuid)
    if (
        (state.mode == ConversationWindowMode.CONTEXT &&
            contextAnchor == null) ||
        (state.mode != ConversationWindowMode.CONTEXT &&
            state.contextAnchorUuid != null) ||
        (state.mode == ConversationWindowMode.LATEST &&
            state.newerPageMarker != null) ||
        (state.mode == ConversationWindowMode.LATEST &&
            !sourceLastRetained)
    ) {
        return unknown
    }

    val olderPageMarker = when {
        !sourceFirstRetained -> retained.first()
        state.olderPageMarker == null -> null
        else -> retainedUuid(state.olderPageMarker) ?: retained.first()
    }
    val newerPageMarker = when {
        state.mode == ConversationWindowMode.LATEST -> null
        !sourceLastRetained -> retained.last()
        state.newerPageMarker == null -> null
        else -> retainedUuid(state.newerPageMarker) ?: retained.last()
    }
    return ConversationPaginationState(
        streamUuid = state.streamUuid,
        topicUuid = state.topicUuid,
        mode = state.mode,
        contextAnchorUuid = contextAnchor,
        olderPageMarker = olderPageMarker,
        newerPageMarker = newerPageMarker,
    )
}

private fun isCanonicalUuid(value: String): Boolean =
    runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?.equals(value.trim(), ignoreCase = true)
        ?: false

private fun parseTimestampMillis(value: String): Long? =
    runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrNull()

private fun validatedFolderOrNull(
    folder: FolderResponseData,
    streamUuids: Set<String>,
): FolderResponseData? {
    if (
        !isCanonicalUuid(folder.uuid) ||
        folder.title.isBlank() ||
        folder.title.length > MAX_FOLDER_TITLE_LENGTH ||
        folder.unreadCount < 0 ||
        folder.systemType !in VALID_FOLDER_SYSTEM_TYPES ||
        parseTimestampMillis(folder.creationDate) == null ||
        folder.backgroundColorValue?.let {
            it !in MIN_ARGB_COLOR_VALUE..MAX_ARGB_COLOR_VALUE
        } == true
    ) {
        return null
    }
    val items = folder.items
        .asSequence()
        .filter { item ->
            isValidFolderItem(
                item = item,
                parentFolderUuid = folder.uuid,
                streamUuids = streamUuids,
            )
        }
        .distinctBy(FolderItem::uuid)
        .take(MAX_FOLDER_ITEMS_PER_FOLDER)
        .toList()
    return folder.copy(
        unreadCount = items.sumOf { it.resolvedActiveUnreadCount() },
        items = items,
    )
}

private fun isValidFolderItem(
    item: FolderItem,
    parentFolderUuid: String,
    streamUuids: Set<String>,
): Boolean =
    isCanonicalUuid(item.uuid) &&
        item.streamUuid in streamUuids &&
        (
            item.folderUuid == parentFolderUuid ||
                item.folder == parentFolderUuid
        ) &&
        item.folderUuid?.let {
            isCanonicalUuid(it) && it == parentFolderUuid
        } != false &&
        item.folder?.let {
            isCanonicalUuid(it) && it == parentFolderUuid
        } != false &&
        item.chatType in VALID_FOLDER_CHAT_TYPES &&
        item.unreadCount >= 0 &&
        item.activeUnreadCount?.let { it >= 0 } != false &&
        item.passiveUnreadCount?.let { it >= 0 } != false &&
        (
            item.pinnedAt == null ||
                parseTimestampMillis(item.pinnedAt) != null
        )

private fun isValidCachedUser(user: UserResponseData): Boolean =
    isCanonicalUuid(user.uuid) &&
        user.username.isNotBlank()

private fun isValidCachedBinding(
    binding: StreamBindingResponseData,
    streamUuids: Set<String>,
): Boolean =
    isCanonicalUuid(binding.uuid) &&
        binding.projectId?.let(::isCanonicalUuid) != false &&
        binding.streamUuid in streamUuids &&
        isCanonicalUuid(binding.userUuid) &&
        isCanonicalUuid(binding.whoUuid) &&
        binding.role in VALID_STREAM_BINDING_ROLES &&
        binding.notificationMode in VALID_STREAM_NOTIFICATION_MODES

private fun isValidTimelineMessage(
    message: MessageResponse,
    kind: WorkspaceTimelineKind,
): Boolean =
    kind != WorkspaceTimelineKind.INBOX &&
        isCanonicalUuid(message.uuid) &&
        isCanonicalUuid(message.streamUuid) &&
        isCanonicalUuid(message.topicUuid) &&
        isCanonicalUuid(message.userUuid) &&
        isCanonicalUuid(message.authorUuid) &&
        parseTimestampMillis(message.createdAt) != null &&
        parseTimestampMillis(message.updatedAt) != null &&
        (kind != WorkspaceTimelineKind.STARRED || message.starred)

private fun ownerKeyHash(ownerKey: String): String =
    Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(
            ownerKey.toByteArray(StandardCharsets.UTF_8),
        ),
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
    )

private fun associatedData(
    ownerKey: String,
    kind: SnapshotRowKind,
    uuid: String,
    streamUuid: String = "",
    topicUuid: String = "",
    position: Int,
): ByteArray =
    listOf(
        ASSOCIATED_DATA_PREFIX,
        ownerKey,
        kind.wireValue,
        uuid,
        streamUuid,
        topicUuid,
        position.toString(),
    )
        .joinToString("|")
        .toByteArray(StandardCharsets.UTF_8)

private fun timelineAssociatedData(
    ownerKey: String,
    kind: WorkspaceTimelineKind,
    rowKind: TimelineRowKind,
    uuid: String = "",
    streamUuid: String = "",
    topicUuid: String = "",
    position: Int = -1,
): ByteArray =
    listOf(
        TIMELINE_ASSOCIATED_DATA_PREFIX,
        ownerKey,
        kind.wireValue,
        rowKind.wireValue,
        uuid,
        streamUuid,
        topicUuid,
        position.toString(),
    )
        .joinToString("|")
        .toByteArray(StandardCharsets.UTF_8)

private enum class SnapshotRowKind(
    val wireValue: String,
) {
    STREAM("stream"),
    TOPIC("topic"),
    MESSAGE("message"),
    CONVERSATION_PAGINATION("conversation_pagination"),
    FOLDER("folder"),
    USER("user"),
    STREAM_BINDING("stream_binding"),
}

private enum class TimelineRowKind(
    val wireValue: String,
) {
    METADATA("metadata"),
    MESSAGE("message"),
}

@Serializable
private data class CachedTimelineMetadata(
    val kind: String,
    val messageCount: Int,
    val firstMessageUuid: String?,
    val lastMessageUuid: String?,
    val nextPageMarker: String?,
)

private data class EncodedTimelineRows(
    val timeline: CachedTimelineEntity,
    val messages: List<CachedTimelineMessageEntity>,
)

private data class CachedConversation(
    val streamUuid: String,
    val topicUuid: String,
    val messages: List<MessageResponse>,
    val sourceFirstMessageUuid: String,
    val sourceLastMessageUuid: String,
    val newestMessageMillis: Long,
)

@Serializable
private data class CachedConversationPaginationMetadata(
    val streamUuid: String,
    val topicUuid: String,
    val mode: ConversationWindowMode,
    val contextAnchorUuid: String?,
    val olderPageMarker: String?,
    val newerPageMarker: String?,
    val messageCount: Int,
    val firstMessageUuid: String,
    val lastMessageUuid: String,
)

private const val TAG = "WorkspaceSnapshot"
private const val ASSOCIATED_DATA_PREFIX = "workspace-snapshot-v1"
private const val TIMELINE_ASSOCIATED_DATA_PREFIX =
    "workspace-timeline-v1"
private const val LOCAL_MESSAGE_UUID_PREFIX = "local-"
internal const val MAX_STREAMS = 1_000
internal const val MAX_TOPICS = 10_000
internal const val MAX_CACHED_CONVERSATIONS = 100
internal const val MAX_MESSAGES_PER_CONVERSATION = 100
internal const val MAX_MESSAGES_PER_ACCOUNT = 5_000
internal const val MAX_TIMELINE_MESSAGES = 500
internal const val MAX_FOLDERS = 500
internal const val MAX_FOLDER_ITEMS_PER_FOLDER = MAX_STREAMS
internal const val MAX_USERS = 10_000
internal const val MAX_STREAM_BINDINGS = 50_000
internal const val MAX_CONVERSATION_PAGINATION_PAYLOAD_BYTES =
    16 * 1_024
internal const val MAX_CATALOG_PAYLOAD_BYTES = 256 * 1_024
internal const val MAX_USER_PAYLOAD_BYTES = 64 * 1_024
internal const val MAX_STREAM_BINDING_PAYLOAD_BYTES = 16 * 1_024
internal const val MAX_TIMELINE_METADATA_PAYLOAD_BYTES = 16 * 1_024
internal const val MAX_MESSAGE_PAYLOAD_BYTES = 1_024 * 1_024
internal const val MAX_ENCRYPTED_PAYLOAD_BYTES =
    MAX_MESSAGE_PAYLOAD_BYTES + 4 * 1_024
private const val MAX_FOLDER_TITLE_LENGTH = 64
private const val MIN_ARGB_COLOR_VALUE = 0L
private const val MAX_ARGB_COLOR_VALUE = 0xFFFF_FFFFL
private val VALID_FOLDER_SYSTEM_TYPES = setOf(null, "all", "created")
private val VALID_FOLDER_CHAT_TYPES = setOf("stream", "group", "private")
private val VALID_STREAM_BINDING_ROLES = setOf(
    "guest",
    "member",
    "moderator",
    "administrator",
    "owner",
)
private val VALID_STREAM_NOTIFICATION_MODES = setOf(
    "mentions_only",
    "muted",
    "all_messages",
)
private val TIMELINE_MESSAGE_COMPARATOR = compareBy<MessageResponse>(
    { parseTimestampMillis(it.createdAt) },
    MessageResponse::uuid,
)
