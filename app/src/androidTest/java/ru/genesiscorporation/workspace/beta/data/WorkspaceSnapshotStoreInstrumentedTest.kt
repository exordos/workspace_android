package ru.genesiscorporation.workspace.beta.data

import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class WorkspaceSnapshotStoreInstrumentedTest {
    private lateinit var context: IsolatedAndroidTestContext
    private lateinit var database: WorkspaceSnapshotDatabase
    private lateinit var dao: WorkspaceSnapshotDao
    private lateinit var store: RoomWorkspaceSnapshotStore

    @Before
    fun setUp() {
        context = IsolatedAndroidTestContext(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "workspace-snapshot",
        )
        database = Room.inMemoryDatabaseBuilder(
            context,
            WorkspaceSnapshotDatabase::class.java,
        ).build()
        dao = database.snapshotDao()
        store = RoomWorkspaceSnapshotStore(
            dao = dao,
            cipher = TinkWorkspaceSnapshotCipher(context),
            clockMillis = { 1_785_456_000_000L },
        )
    }

    @After
    fun cleanUp() {
        database.close()
        context.cleanUp()
    }

    @Test
    fun snapshotIsEncryptedBoundedAndAccountScoped() = runBlocking {
        val snapshot = snapshot(
            messageContent = MESSAGE_SENTINEL,
            includeLocalOutbox = true,
        )

        store.write(ACCOUNT_A, snapshot)

        assertEquals(snapshot.copy(
            messagesByConversation = mapOf(
                CONVERSATION_KEY to listOf(snapshotMessage(MESSAGE_SENTINEL)),
            ),
        ), store.read(ACCOUNT_A))
        assertEquals(WorkspaceSnapshot(), store.read(ACCOUNT_B))

        val rows = dao.readAccount(
            ownerKeyHash = ownerHash(ACCOUNT_A),
            streamLimit = MAX_STREAMS,
            topicLimit = MAX_TOPICS,
            messageLimit = MAX_MESSAGES_PER_ACCOUNT,
            folderLimit = MAX_FOLDERS,
            userLimit = MAX_USERS,
            streamBindingLimit = MAX_STREAM_BINDINGS,
            maxEncryptedBytes = MAX_ENCRYPTED_PAYLOAD_BYTES,
        )
        assertEquals(1, rows.streams.size)
        assertEquals(1, rows.topics.size)
        assertEquals(1, rows.messages.size)
        assertEquals(1, rows.folders.size)
        assertEquals(1, rows.users.size)
        assertEquals(1, rows.streamBindings.size)
        val persistedBytes = (
            rows.streams.map(CachedStreamEntity::encryptedPayload) +
                rows.topics.map(CachedTopicEntity::encryptedPayload) +
                rows.messages.map(CachedMessageEntity::encryptedPayload) +
                rows.folders.map(CachedFolderEntity::encryptedPayload) +
                rows.users.map(CachedUserEntity::encryptedPayload) +
                rows.streamBindings.map(
                    CachedStreamBindingEntity::encryptedPayload,
                )
        ).fold(ByteArray(0)) { all, next -> all + next }
        val persistedText = persistedBytes.toString(StandardCharsets.UTF_8)
        assertFalse(persistedText.contains(MESSAGE_SENTINEL))
        assertFalse(rows.streams.single().ownerKeyHash.contains(ACCOUNT_A))

        store.write(ACCOUNT_B, snapshot("owner-b"))
        store.clearAccount(ACCOUNT_A)
        assertEquals(WorkspaceSnapshot(), store.read(ACCOUNT_A))
        assertEquals(
            "owner-b",
            store.read(ACCOUNT_B)
                .messagesByConversation
                .getValue(CONVERSATION_KEY)
                .single()
                .payload
                .content,
        )
    }

    @Test
    fun ciphertextCannotBeReplayedForAnotherAccount() = runBlocking {
        store.write(ACCOUNT_A, snapshot("owner-a"))
        store.write(ACCOUNT_B, snapshot("owner-b"))

        val ownerARow = dao.readMessages(
            ownerKeyHash = ownerHash(ACCOUNT_A),
            limit = 1,
            maxEncryptedBytes = MAX_ENCRYPTED_PAYLOAD_BYTES,
        ).single()
        val ownerBRow = dao.readMessages(
            ownerKeyHash = ownerHash(ACCOUNT_B),
            limit = 1,
            maxEncryptedBytes = MAX_ENCRYPTED_PAYLOAD_BYTES,
        ).single()
        dao.insertMessages(
            listOf(
                ownerBRow.copy(
                    encryptedPayload = ownerARow.encryptedPayload,
                ),
            ),
        )

        assertTrue(store.read(ACCOUNT_B).messagesByConversation.isEmpty())
        assertEquals(
            "owner-a",
            store.read(ACCOUNT_A)
                .messagesByConversation
                .getValue(CONVERSATION_KEY)
                .single()
                .payload
                .content,
        )
    }

    @Test
    fun ciphertextCannotBeMovedWithinAnAccount() = runBlocking {
        store.write(ACCOUNT_A, snapshot("original-position"))

        val row = dao.readMessages(
            ownerKeyHash = ownerHash(ACCOUNT_A),
            limit = 1,
            maxEncryptedBytes = MAX_ENCRYPTED_PAYLOAD_BYTES,
        ).single()
        dao.insertMessages(
            listOf(
                row.copy(position = row.position + 1),
            ),
        )

        assertTrue(store.read(ACCOUNT_A).messagesByConversation.isEmpty())
    }

    @Test
    fun invalidRowsAndLocalOutboxNeverEnterTheCache() = runBlocking {
        val invalidStream = snapshot("valid").streams.single().copy(
            uuid = "not-a-uuid",
        )
        store.write(
            ACCOUNT_A,
            snapshot("valid").copy(
                streams = snapshot("valid").streams + invalidStream,
                messagesByConversation = mapOf(
                    CONVERSATION_KEY to listOf(
                        snapshotMessage("valid"),
                        snapshotMessage("local").copy(uuid = "local-pending"),
                    ),
                ),
            ),
        )

        val restored = store.read(ACCOUNT_A)
        assertEquals(listOf(STREAM_UUID), restored.streams.map(Stream::uuid))
        assertEquals(
            listOf(MESSAGE_UUID),
            restored.messagesByConversation
                .getValue(CONVERSATION_KEY)
                .map(MessageResponse::uuid),
        )
    }

    @Test
    fun invalidCatalogRelationshipsAndEnumsAreDiscarded() = runBlocking {
        val valid = snapshot("valid")
        val folder = valid.folders.single()
        val binding = valid.streamBindings.single()
        store.write(
            ACCOUNT_A,
            valid.copy(
                folders = listOf(
                    folder.copy(
                        items = folder.items + folder.items.single().copy(
                            uuid = messageUuid(701),
                            folderUuid = ACCOUNT_B,
                        ),
                    ),
                ),
                users = valid.users + valid.users.single().copy(
                    uuid = "not-a-uuid",
                ),
                streamBindings = valid.streamBindings + binding.copy(
                    uuid = messageUuid(801),
                    role = "superuser",
                ),
            ),
        )

        val restored = store.read(ACCOUNT_A)
        assertEquals(
            listOf(FOLDER_ITEM_UUID),
            restored.folders.single().items.map(FolderItem::uuid),
        )
        assertEquals(listOf(USER_UUID), restored.users.map(UserResponseData::uuid))
        assertEquals(
            listOf(BINDING_UUID),
            restored.streamBindings.map(StreamBindingResponseData::uuid),
        )
    }

    @Test
    fun newestMessagesAreBoundedAndOversizedRowsAreSkipped() = runBlocking {
        val messages = (0 until MAX_MESSAGES_PER_CONVERSATION + 6).map { index ->
            val timestamp = OffsetDateTime.parse(TIMESTAMP)
                .plusSeconds(index.toLong())
                .toString()
            snapshotMessage("message-$index").copy(
                uuid = messageUuid(index),
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        }
        store.write(
            ACCOUNT_A,
            snapshot("unused").copy(
                messagesByConversation = mapOf(
                    CONVERSATION_KEY to messages,
                ),
            ),
        )

        val restoredMessages = store.read(ACCOUNT_A)
            .messagesByConversation
            .getValue(CONVERSATION_KEY)
        assertEquals(MAX_MESSAGES_PER_CONVERSATION, restoredMessages.size)
        assertEquals("message-6", restoredMessages.first().payload.content)
        assertEquals("message-105", restoredMessages.last().payload.content)

        val oversized = snapshotMessage(
            "x".repeat(MAX_MESSAGE_PAYLOAD_BYTES + 1),
        ).copy(uuid = messageUuid(999))
        store.write(
            ACCOUNT_A,
            snapshot("valid").copy(
                messagesByConversation = mapOf(
                    CONVERSATION_KEY to listOf(
                        snapshotMessage("valid"),
                        oversized,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("valid"),
            store.read(ACCOUNT_A)
                .messagesByConversation
                .getValue(CONVERSATION_KEY)
                .map { it.payload.content },
        )
    }

    private fun snapshot(
        messageContent: String,
        includeLocalOutbox: Boolean = false,
    ): WorkspaceSnapshot {
        val messages = buildList {
            add(snapshotMessage(messageContent))
            if (includeLocalOutbox) {
                add(
                    snapshotMessage("local content")
                        .copy(uuid = "local-pending"),
                )
            }
        }
        return WorkspaceSnapshot(
            streams = listOf(
                Stream(
                    uuid = STREAM_UUID,
                    unreadCount = 1,
                    updatedAt = TIMESTAMP,
                    name = "Cached stream",
                    isPrivate = false,
                    defaultTopicUuid = TOPIC_UUID,
                ),
            ),
            topicsByStream = mapOf(
                STREAM_UUID to listOf(
                    TopicsResponseData(
                        uuid = TOPIC_UUID,
                        name = "Cached topic",
                        streamUuid = STREAM_UUID,
                        updatedAt = TIMESTAMP,
                        unreadCount = 1,
                        isDone = false,
                        isDefault = true,
                        lastMessageUuid = MESSAGE_UUID,
                    ),
                ),
            ),
            messagesByConversation = mapOf(
                CONVERSATION_KEY to messages,
            ),
            folders = listOf(
                FolderResponseData(
                    uuid = FOLDER_UUID,
                    title = "Cached folder",
                    unreadCount = 1,
                    systemType = "created",
                    creationDate = TIMESTAMP,
                    backgroundColorValue = 0xFF12_3456,
                    items = listOf(
                        FolderItem(
                            uuid = FOLDER_ITEM_UUID,
                            folderUuid = FOLDER_UUID,
                            streamUuid = STREAM_UUID,
                            chatType = "stream",
                            unreadCount = 1,
                        ),
                    ),
                ),
            ),
            users = listOf(
                UserResponseData(
                    username = "cached-user",
                    uuid = USER_UUID,
                    status = "active",
                    avatar = "",
                ),
            ),
            streamBindings = listOf(
                StreamBindingResponseData(
                    uuid = BINDING_UUID,
                    projectId = PROJECT_UUID,
                    streamUuid = STREAM_UUID,
                    userUuid = USER_UUID,
                    whoUuid = USER_UUID,
                    role = "owner",
                    notificationMode = "mentions_only",
                ),
            ),
        )
    }

    private fun snapshotMessage(content: String) = MessageResponse(
        uuid = MESSAGE_UUID,
        updatedAt = TIMESTAMP,
        createdAt = TIMESTAMP,
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = USER_UUID,
        authorUuid = USER_UUID,
        payload = MessageResponsePayload(
            kind = "markdown",
            content = content,
        ),
        isOwn = false,
        reactions = emptyMap(),
    )

    private fun messageUuid(index: Int): String =
        String.format(
            Locale.ROOT,
            "40000000-0000-4000-8000-%012d",
            index,
        )

    private fun ownerHash(ownerKey: String): String =
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                ownerKey.toByteArray(StandardCharsets.UTF_8),
            ),
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
        )

    companion object {
        private const val ACCOUNT_A =
            "10000000-0000-4000-8000-000000000001"
        private const val ACCOUNT_B =
            "10000000-0000-4000-8000-000000000002"
        private const val STREAM_UUID =
            "20000000-0000-4000-8000-000000000001"
        private const val TOPIC_UUID =
            "30000000-0000-4000-8000-000000000001"
        private const val MESSAGE_UUID =
            "40000000-0000-4000-8000-000000000001"
        private const val USER_UUID =
            "50000000-0000-4000-8000-000000000001"
        private const val FOLDER_UUID =
            "60000000-0000-4000-8000-000000000001"
        private const val FOLDER_ITEM_UUID =
            "70000000-0000-4000-8000-000000000001"
        private const val BINDING_UUID =
            "80000000-0000-4000-8000-000000000001"
        private const val PROJECT_UUID =
            "90000000-0000-4000-8000-000000000001"
        private const val CONVERSATION_KEY = "$STREAM_UUID.$TOPIC_UUID"
        private const val TIMESTAMP = "2026-07-30T10:00:00Z"
        private const val MESSAGE_SENTINEL = "private-offline-message"
    }
}
