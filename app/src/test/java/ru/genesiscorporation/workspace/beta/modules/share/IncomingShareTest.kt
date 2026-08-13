package ru.genesiscorporation.workspace.beta.modules.share

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.PersistedAttachment
import ru.genesiscorporation.workspace.beta.data.PersistedComposerDraft
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class IncomingShareTest {
    @Test
    fun `subject and body are normalized without duplicating an existing title`() {
        assertEquals(
            "Release notes\n\nLine one\nLine two",
            combineIncomingShareText(
                subject = " Release notes ",
                text = "Line one\r\nLine two\u0000",
            ),
        )
        assertEquals(
            "Release notes\nBody",
            combineIncomingShareText(
                subject = "Release notes",
                text = "Release notes\rBody",
            ),
        )
        assertEquals(
            "Only subject",
            combineIncomingShareText("Only subject", null),
        )
    }

    @Test
    fun `destination catalog keeps existing direct and channel chats but removes archives`() {
        val direct = stream(
            uuid = DIRECT_STREAM_UUID,
            name = "Zulu direct",
            isPrivate = true,
            directUserUuid = USER_UUID,
        )
        val channel = stream(
            uuid = STREAM_UUID,
            name = "Alpha channel",
        )
        val archived = stream(
            uuid = ARCHIVED_STREAM_UUID,
            name = "Archived",
            isArchived = true,
        )

        assertEquals(
            listOf(direct, channel),
            incomingShareStreams(
                listOf(channel, direct, archived, channel.copy(name = "Duplicate")),
            ),
        )
    }

    @Test
    fun `folder projection keeps exact members in canonical catalog order`() {
        val direct = stream(
            uuid = DIRECT_STREAM_UUID,
            name = "Zulu direct",
            isPrivate = true,
            directUserUuid = USER_UUID,
        )
        val channel = stream(
            uuid = STREAM_UUID,
            name = "Alpha channel",
        )
        val catalog = incomingShareStreams(listOf(channel, direct))
        val selectedFolder = folder(
            uuid = "aaaaaaaa-aaaa-4aaa-8aaa-bbbbbbbbbbbb",
            title = "Project",
            streamUuids = listOf(STREAM_UUID, "missing", STREAM_UUID),
        )

        assertEquals(
            listOf(channel),
            incomingShareStreamsInFolder(catalog, selectedFolder),
        )
        assertEquals(
            catalog,
            incomingShareStreamsInFolder(
                catalog,
                folder(
                    uuid = ALL_CHATS_FOLDER_UUID,
                    title = "All",
                    streamUuids = emptyList(),
                ),
            ),
        )
        assertEquals(catalog, incomingShareStreamsInFolder(catalog, null))
    }

    @Test
    fun `direct destination avatar belongs to the peer not the last sender`() {
        val peer = user(USER_UUID, "urn:image:peer")
        val lastSender = user(OTHER_USER_UUID, "urn:image:last-sender")
        val direct = stream(
            uuid = DIRECT_STREAM_UUID,
            name = "Peer",
            isPrivate = true,
            directUserUuid = USER_UUID,
        ).copy(
            avatar = "urn:image:stream-fallback",
            lastMessage = message(lastSender),
        )

        assertEquals(
            "urn:image:peer",
            incomingShareAvatarUrn(direct, mapOf(peer.uuid to peer)),
        )
        assertEquals(
            "urn:image:stream-fallback",
            incomingShareAvatarUrn(direct, emptyMap()),
        )
        assertNull(
            incomingShareAvatarUrn(
                direct.copy(avatar = null),
                emptyMap(),
            ),
        )
    }

    @Test
    fun `topic catalog is stream scoped deduplicated and default first`() {
        val regular = topic(TOPIC_UUID, "Alpha")
        val default = topic(DEFAULT_TOPIC_UUID, "General", isDefault = true)
        val foreign = topic(FOREIGN_TOPIC_UUID, "Foreign")
            .copy(streamUuid = DIRECT_STREAM_UUID)

        assertEquals(
            listOf(default, regular),
            incomingShareTopics(
                stream(STREAM_UUID, "Channel"),
                listOf(regular, foreign, default, regular.copy(name = "Copy")),
            ),
        )
    }

    @Test
    fun `direct target uses default topic and channel requires matching topic`() {
        val direct = stream(
            uuid = DIRECT_STREAM_UUID,
            name = "Alice",
            isPrivate = true,
            directUserUuid = USER_UUID,
            defaultTopicUuid = DEFAULT_TOPIC_UUID,
        )
        val channel = stream(STREAM_UUID, "Engineering")
        val topic = topic(TOPIC_UUID, "Android")

        assertEquals(
            IncomingShareDraftTarget(
                streamUuid = DIRECT_STREAM_UUID,
                topicUuid = DEFAULT_TOPIC_UUID,
                chatTitle = "Alice",
                topicName = null,
                isDirectMessages = true,
            ),
            resolveIncomingShareTarget(direct, null),
        )
        assertEquals(
            IncomingShareDraftTarget(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                chatTitle = "Engineering",
                topicName = "Android",
                isDirectMessages = false,
            ),
            resolveIncomingShareTarget(channel, topic),
        )
        assertNull(resolveIncomingShareTarget(channel, null))
        assertNull(
            resolveIncomingShareTarget(
                channel,
                topic.copy(streamUuid = DIRECT_STREAM_UUID),
            ),
        )
        assertEquals(
            DEFAULT_TOPIC_UUID,
            directIncomingTopicUuid(
                direct.copy(defaultTopicUuid = null),
                listOf(
                    topic(TOPIC_UUID, "Regular"),
                    topic(
                        DEFAULT_TOPIC_UUID,
                        "Default",
                        isDefault = true,
                    ),
                ).map { it.copy(streamUuid = DIRECT_STREAM_UUID) },
            ),
        )
    }

    @Test
    fun `share appends to ordinary draft and preserves outbox metadata`() {
        val existingAttachment = attachment("existing")
        val incomingAttachment = attachment("incoming")
        val existing = PersistedConversationState(
            route = PersistedConversationRoute(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                chatTitle = "Old title",
                topicName = "Old topic",
                isDirectMessages = false,
            ),
            draftText = "Existing draft",
            attachments = listOf(existingAttachment),
            draftUpdatedAt = "2026-07-01T00:00:00Z",
        )

        val result = mergeIncomingShareDraft(
            existingState = existing,
            target = channelTarget(),
            incomingText = "Shared text",
            incomingAttachments = listOf(incomingAttachment),
            updatedAt = UPDATED_AT,
            incomingRequestId = INCOMING_REQUEST_ID,
        ) as IncomingShareCommitResult.Accepted

        assertEquals("Existing draft\n\nShared text", result.state.draftText)
        assertEquals(
            listOf(existingAttachment, incomingAttachment),
            result.state.attachments,
        )
        assertEquals("Engineering", result.state.route?.chatTitle)
        assertEquals("Android", result.state.route?.topicName)
        assertEquals(UPDATED_AT, result.state.draftUpdatedAt)
        assertEquals(
            INCOMING_REQUEST_ID,
            result.state.lastIncomingShareRequestId,
        )
    }

    @Test
    fun `share received during an edit is retained in the suspended ordinary draft`() {
        val existingSuspendedAttachment = attachment("suspended")
        val incomingAttachment = attachment("incoming")
        val existing = PersistedConversationState(
            route = route(),
            draftText = "Editing replacement",
            editingMessageUuid = MESSAGE_UUID,
            suspendedDraft = PersistedComposerDraft(
                text = "Original draft",
                quotedMessageUuid = QUOTED_MESSAGE_UUID,
                attachments = listOf(existingSuspendedAttachment),
            ),
        )

        val result = mergeIncomingShareDraft(
            existingState = existing,
            target = channelTarget(),
            incomingText = "Shared text",
            incomingAttachments = listOf(incomingAttachment),
            updatedAt = UPDATED_AT,
        ) as IncomingShareCommitResult.Accepted

        assertEquals("Editing replacement", result.state.draftText)
        assertEquals(MESSAGE_UUID, result.state.editingMessageUuid)
        assertEquals(
            "Original draft\n\nShared text",
            result.state.suspendedDraft?.text,
        )
        assertEquals(
            QUOTED_MESSAGE_UUID,
            result.state.suspendedDraft?.quotedMessageUuid,
        )
        assertEquals(
            listOf(existingSuspendedAttachment, incomingAttachment),
            result.state.suspendedDraft?.attachments,
        )
    }

    @Test
    fun `merge is all or nothing at text and attachment limits`() {
        val fullTextResult = mergeIncomingShareDraft(
            existingState = PersistedConversationState(
                route = route(),
                draftText = "x".repeat(MAX_INCOMING_TEXT_CHARS),
            ),
            target = channelTarget(),
            incomingText = "extra",
            incomingAttachments = emptyList(),
            updatedAt = UPDATED_AT,
        )
        val fullAttachmentsResult = mergeIncomingShareDraft(
            existingState = PersistedConversationState(
                route = route(),
                attachments = (0 until MAX_INCOMING_ATTACHMENTS)
                    .map { attachment("existing-$it") },
            ),
            target = channelTarget(),
            incomingText = "",
            incomingAttachments = listOf(attachment("extra")),
            updatedAt = UPDATED_AT,
        )

        assertTrue(fullTextResult is IncomingShareCommitResult.Rejected)
        assertTrue(fullAttachmentsResult is IncomingShareCommitResult.Rejected)
    }

    @Test
    fun `unsafe or empty incoming payload is rejected`() {
        val unsafe = mergeIncomingShareDraft(
            existingState = null,
            target = channelTarget(),
            incomingText = "",
            incomingAttachments = listOf(
                attachment("unsafe").copy(uri = "file:///private/file"),
            ),
            updatedAt = UPDATED_AT,
        )
        val empty = mergeIncomingShareDraft(
            existingState = null,
            target = channelTarget(),
            incomingText = "",
            incomingAttachments = emptyList(),
            updatedAt = UPDATED_AT,
        )

        assertTrue(unsafe is IncomingShareCommitResult.Rejected)
        assertTrue(empty is IncomingShareCommitResult.Rejected)
        assertFalse(unsafe is IncomingShareCommitResult.Accepted)
    }

    @Test
    fun `manifest exposes both Android share actions and ingress cannot auto send`() {
        val projectRoot = locateProjectRoot()
        val manifest = projectRoot.resolve("src/main/AndroidManifest.xml")
            .readText()
        val ingressSources = projectRoot
            .resolve(
                "src/main/java/ru/genesiscorporation/workspace/beta/modules/share",
            )
        val sourceText = Files.walk(ingressSources).use { paths ->
            paths
                .filter {
                    Files.isRegularFile(it) &&
                        it.fileName.toString().endsWith(".kt")
                }
                .map(Path::readText)
                .toList()
                .joinToString("\n")
        }

        assertTrue(manifest.contains("android.intent.action.SEND\""))
        assertTrue(manifest.contains("android.intent.action.SEND_MULTIPLE\""))
        assertFalse(sourceText.contains("SendMessageRequest"))
        assertTrue(sourceText.contains("Добавить в черновик"))
        assertTrue(sourceText.contains("IncomingShareFolderTabs"))
    }

    private fun locateProjectRoot(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        return sequenceOf(
            workingDirectory,
            workingDirectory.resolve("app"),
        ).firstOrNull {
            Files.isRegularFile(it.resolve("src/main/AndroidManifest.xml"))
        } ?: error("Could not locate Android app root from $workingDirectory")
    }

    private fun channelTarget() = IncomingShareDraftTarget(
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        chatTitle = "Engineering",
        topicName = "Android",
        isDirectMessages = false,
    )

    private fun route() = PersistedConversationRoute(
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        chatTitle = "Engineering",
        topicName = "Android",
        isDirectMessages = false,
    )

    private fun attachment(id: String) = PersistedAttachment(
        uri = "content://com.exordos.workspace.fileprovider/attachments/$id",
        fileName = "$id.txt",
        contentType = "text/plain",
        sizeBytes = 5,
    )

    private fun user(uuid: String, avatar: String) = UserResponseData(
        username = "user-$uuid",
        uuid = uuid,
        status = "active",
        avatar = avatar,
    )

    private fun message(author: UserResponseData) = MessageResponse(
        uuid = MESSAGE_UUID,
        updatedAt = UPDATED_AT,
        createdAt = UPDATED_AT,
        streamUuid = DIRECT_STREAM_UUID,
        topicUuid = DEFAULT_TOPIC_UUID,
        userUuid = author.uuid,
        authorUuid = author.uuid,
        payload = MessageResponsePayload(kind = "markdown", content = "Latest"),
        isOwn = false,
        reactions = emptyMap(),
        user = author,
    )

    private fun stream(
        uuid: String,
        name: String,
        isPrivate: Boolean = false,
        directUserUuid: String? = null,
        defaultTopicUuid: String? = null,
        isArchived: Boolean = false,
    ) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = UPDATED_AT,
        name = name,
        isPrivate = isPrivate,
        directUserUuid = directUserUuid,
        defaultTopicUuid = defaultTopicUuid,
        isArchived = isArchived,
    )

    private fun topic(
        uuid: String,
        name: String,
        isDefault: Boolean = false,
    ) = TopicsResponseData(
        uuid = uuid,
        name = name,
        streamUuid = STREAM_UUID,
        updatedAt = UPDATED_AT,
        unreadCount = 0,
        isDone = false,
        isDefault = isDefault,
    )

    private fun folder(
        uuid: String,
        title: String,
        streamUuids: List<String>,
    ) = FolderResponseData(
        uuid = uuid,
        title = title,
        unreadCount = 0,
        creationDate = UPDATED_AT,
        items = streamUuids.mapIndexed { index, streamUuid ->
            FolderItem(
                uuid = "bbbbbbbb-bbbb-4bbb-8bbb-${index.toString().padStart(12, '0')}",
                folderUuid = uuid,
                streamUuid = streamUuid,
                chatType = "stream",
                unreadCount = 0,
            )
        },
    )

    private companion object {
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val DIRECT_STREAM_UUID =
            "22222222-2222-4222-8222-222222222222"
        const val ARCHIVED_STREAM_UUID =
            "33333333-3333-4333-8333-333333333333"
        const val TOPIC_UUID = "44444444-4444-4444-8444-444444444444"
        const val DEFAULT_TOPIC_UUID =
            "55555555-5555-4555-8555-555555555555"
        const val FOREIGN_TOPIC_UUID =
            "66666666-6666-4666-8666-666666666666"
        const val USER_UUID = "77777777-7777-4777-8777-777777777777"
        const val OTHER_USER_UUID =
            "77777777-7777-4777-8777-888888888888"
        const val MESSAGE_UUID = "88888888-8888-4888-8888-888888888888"
        const val QUOTED_MESSAGE_UUID =
            "99999999-9999-4999-8999-999999999999"
        const val UPDATED_AT = "2026-07-30T12:00:00Z"
        const val INCOMING_REQUEST_ID =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val ALL_CHATS_FOLDER_UUID =
            "00000000-0000-0000-0000-000000000000"
    }
}
