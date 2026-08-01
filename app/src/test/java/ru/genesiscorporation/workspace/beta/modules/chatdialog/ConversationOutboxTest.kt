package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.PersistedAttachment
import ru.genesiscorporation.workspace.beta.data.PersistedAttachmentUpload
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxEntry
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus
import ru.genesiscorporation.workspace.beta.data.PersistedReadBoundary
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.PersistedWorkspaceReplySession
import ru.genesiscorporation.workspace.beta.data.PersistedWorkspaceReplyTab
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class ConversationOutboxTest {
    @Test
    fun `timeouts and malformed success responses remain uncertain`() {
        assertEquals(
            PersistedOutboxStatus.UNCERTAIN,
            classifyOutboxFailure(
                ApiError("timeout", "TIMEOUT", ApiErrorKind.TIMEOUT),
            ),
        )
        assertEquals(
            PersistedOutboxStatus.UNCERTAIN,
            classifyOutboxFailure(
                ApiError(
                    "bad response",
                    "MALFORMED_RESPONSE",
                    ApiErrorKind.MALFORMED_RESPONSE,
                ),
            ),
        )
    }

    @Test
    fun `rejected requests remain safe to retry`() {
        assertEquals(
            PersistedOutboxStatus.FAILED,
            classifyOutboxFailure(
                ApiError("invalid", "400", ApiErrorKind.VALIDATION),
            ),
        )
        assertEquals(
            PersistedOutboxStatus.FAILED,
            classifyOutboxFailure(
                ApiError("conflict", "409", ApiErrorKind.CONFLICT),
            ),
        )
    }

    @Test
    fun `account switch after mutation remains uncertain`() {
        assertEquals(
            PersistedOutboxStatus.UNCERTAIN,
            classifyOutboxFailure(
                ApiError(
                    "account changed",
                    "ACCOUNT_CHANGED",
                    ApiErrorKind.CONFLICT,
                ),
            ),
        )
    }

    @Test
    fun `interrupted sending becomes uncertain after process restore`() {
        val restored = interruptedOutboxEntry(
            outbox(status = PersistedOutboxStatus.SENDING),
        )

        assertEquals(PersistedOutboxStatus.UNCERTAIN, restored.status)
        assertTrue(restored.errorMessage.orEmpty().contains("перезапущено"))
    }

    @Test
    fun `unique matching server message confirms uncertain send`() {
        val matches = reconcileUncertainOutbox(
            outbox = listOf(outbox()),
            serverMessages = listOf(serverMessage()),
        )

        assertEquals(1, matches.size)
        assertEquals("local-message", matches.single().localMessageUuid)
        assertEquals("server-message", matches.single().serverMessage.uuid)
    }

    @Test
    fun `send confirmation must belong to the same conversation`() {
        val entry = outbox()

        assertTrue(isExpectedSendConfirmation(entry, serverMessage()))
        assertTrue(
            !isExpectedSendConfirmation(
                entry,
                serverMessage().copy(topicUuid = "other-topic"),
            ),
        )
        assertTrue(
            !isExpectedSendConfirmation(
                entry,
                serverMessage().copy(isOwn = false),
            ),
        )
        assertTrue(
            !isExpectedSendConfirmation(
                entry,
                serverMessage().copy(
                    payload = MessageResponsePayload("markdown", "Other"),
                ),
            ),
        )
    }

    @Test
    fun `ambiguous server matches never clear the local outbox`() {
        val matches = reconcileUncertainOutbox(
            outbox = listOf(outbox()),
            serverMessages = listOf(
                serverMessage(uuid = "server-one"),
                serverMessage(uuid = "server-two"),
            ),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `message known before the attempt cannot confirm the attempt`() {
        val matches = reconcileUncertainOutbox(
            outbox = listOf(
                outbox().copy(
                    knownMatchingMessageUuids = listOf("server-message"),
                ),
            ),
            serverMessages = listOf(serverMessage()),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `one server message never confirms two identical outbox entries`() {
        val matches = reconcileUncertainOutbox(
            outbox = listOf(
                outbox(localUuid = "local-one"),
                outbox(localUuid = "local-two"),
            ),
            serverMessages = listOf(serverMessage()),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `failed outbox entries are not heuristically reconciled`() {
        val matches = reconcileUncertainOutbox(
            outbox = listOf(outbox(status = PersistedOutboxStatus.FAILED)),
            serverMessages = listOf(serverMessage()),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `unavailable edit preserves both the prior draft and edited text`() {
        assertEquals(
            "Prior draft\n\n---\n" +
                "Восстановленный текст редактирования:\nEdited text",
            mergeRecoveredDraftTexts(
                originalDraft = "Prior draft",
                recoveredEdit = "Edited text",
            ),
        )
        assertEquals(
            "Edited text",
            mergeRecoveredDraftTexts(
                originalDraft = "",
                recoveredEdit = "Edited text",
            ),
        )
    }

    @Test
    fun `restored state is bounded before reaching the composer`() {
        val sanitized = sanitizePersistedConversationState(
            PersistedConversationState(
                route = PersistedConversationRoute(
                    streamUuid = "stream",
                    topicUuid = "topic",
                    chatTitle = "  " + "C".repeat(600) + "  ",
                    topicName = "  Topic  ",
                    isDirectMessages = false,
                ),
                draftText = "x".repeat(40_100),
                attachments = List(12) { index ->
                    PersistedAttachment(
                        uri = "content://file/$index",
                        fileName = "$index.txt",
                        contentType = "text/plain",
                        sizeBytes = 10,
                    )
                },
                outbox = listOf(
                    outbox(),
                    outbox(localUuid = "not-a-local-entry"),
                ),
                pendingReadBoundary = readBoundary(
                    uuid = READ_BOUNDARY_UUID.uppercase(),
                    createdAt = "2026-07-30T13:00:00+03:00",
                ),
            ),
        )

        assertEquals(40_000, sanitized.draftText.length)
        assertEquals(512, sanitized.route?.chatTitle?.length)
        assertEquals("Topic", sanitized.route?.topicName)
        assertEquals(10, sanitized.attachments.size)
        assertEquals(
            listOf("local-message"),
            sanitized.outbox.map(PersistedOutboxEntry::localMessageUuid),
        )
        assertEquals(
            readBoundary(),
            sanitized.pendingReadBoundary,
        )
    }

    @Test
    fun `persisted attachment upload checkpoints are validated independently`() {
        val sanitized = sanitizePersistedConversationState(
            PersistedConversationState(
                attachments = listOf(
                    PersistedAttachment(
                        uri = "content://file/valid",
                        fileName = "valid.png",
                        contentType = "image/png",
                        sizeBytes = 42,
                        uploaded = PersistedAttachmentUpload(
                            uuid = "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA",
                            name = "server.png",
                            contentType = "IMAGE/PNG; charset=binary",
                            sizeBytes = 42,
                        ),
                    ),
                    PersistedAttachment(
                        uri = "content://file/invalid",
                        fileName = "invalid.png",
                        contentType = "image/png",
                        sizeBytes = 42,
                        uploaded = PersistedAttachmentUpload(
                            uuid = "not-a-uuid",
                            name = "server.png",
                            contentType = "image/png",
                            sizeBytes = 42,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            PersistedAttachmentUpload(
                uuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                name = "server.png",
                contentType = "image/png",
                sizeBytes = 42,
            ),
            sanitized.attachments.first().uploaded,
        )
        assertNull(sanitized.attachments.last().uploaded)
    }

    @Test
    fun `invalid persisted read boundary is discarded`() {
        val sanitized = sanitizePersistedConversationState(
            PersistedConversationState(
                pendingReadBoundary = readBoundary(
                    uuid = "not-a-canonical-message-uuid",
                ),
            ),
        )

        assertEquals(null, sanitized.pendingReadBoundary)
        assertFalse(sanitized.hasConversationWork())
    }

    @Test
    fun `persisted reply session is bounded canonical and retained as work`() {
        val valid = PersistedWorkspaceReplyTab(
            id = "reply",
            messageUuid = READ_BOUNDARY_UUID,
            senderUuid = "00000000-0000-4000-8000-000000000099",
            senderName = "  Alice  ",
            quotedContent = "quote",
            createdAt = "2026-07-31T12:00:00Z",
            answer = "answer",
        )
        val sanitized = sanitizePersistedConversationState(
            PersistedConversationState(
                replySession = PersistedWorkspaceReplySession(
                    tabs = listOf(
                        valid,
                        valid.copy(messageUuid =
                            "00000000-0000-4000-8000-000000000098"),
                        valid.copy(id = "broken", messageUuid = "bad"),
                    ),
                    activeTabId = "missing",
                ),
            ),
        )

        assertEquals(1, sanitized.replySession.tabs.size)
        assertEquals("Alice", sanitized.replySession.tabs.single().senderName)
        assertEquals("reply", sanitized.replySession.activeTabId)
        assertTrue(sanitized.hasConversationWork())
    }

    @Test
    fun `persisted read boundary requires a valid timestamp`() {
        val sanitized = sanitizePersistedConversationState(
            PersistedConversationState(
                pendingReadBoundary = readBoundary(createdAt = "not-a-time"),
            ),
        )

        assertEquals(null, sanitized.pendingReadBoundary)
        assertFalse(sanitized.hasConversationWork())
    }

    @Test
    fun `read retry without a draft remains durable conversation work`() {
        val state = PersistedConversationState(
            pendingReadBoundary = readBoundary(),
        )

        assertTrue(state.hasConversationWork())
    }

    @Test
    fun `restored outbox and route must belong to the opened conversation`() {
        val sanitized = sanitizePersistedConversationState(
            state = PersistedConversationState(
                route = PersistedConversationRoute(
                    streamUuid = "other-stream",
                    topicUuid = "other-topic",
                    chatTitle = "Other",
                    topicName = "Other",
                    isDirectMessages = false,
                ),
                outbox = listOf(
                    outbox(),
                    outbox(localUuid = "local-other").copy(
                        streamUuid = "other-stream",
                    ),
                ),
            ),
            expectedStreamUuid = "stream",
            expectedTopicUuid = "topic",
        )

        assertEquals(null, sanitized.route)
        assertEquals(
            listOf("local-message"),
            sanitized.outbox.map(PersistedOutboxEntry::localMessageUuid),
        )
    }

    @Test
    fun `interrupted draft mutations become retryable instead of staying busy`() {
        val saving = sanitizePersistedConversationState(
            PersistedConversationState(
                serverDraft = PersistedServerDraftState(
                    draftUuid = "11111111-1111-4111-8111-111111111111",
                    pendingCreateContent = "draft",
                    status = PersistedDraftSyncStatus.SAVING,
                ),
            ),
        ).serverDraft
        val deleting = sanitizePersistedConversationState(
            PersistedConversationState(
                serverDraft = PersistedServerDraftState(
                    draftUuid = "11111111-1111-4111-8111-111111111111",
                    entityTag = "\"2\"",
                    serverRevision = 2,
                    syncedContent = "draft",
                    serverUpdatedAt = "2026-07-30T10:00:00Z",
                    status = PersistedDraftSyncStatus.DELETING,
                    deleteRequested = true,
                ),
            ),
        ).serverDraft

        assertEquals(PersistedDraftSyncStatus.FAILED, saving?.status)
        assertTrue(saving?.errorMessage.orEmpty().contains("перезапущено"))
        assertEquals(PersistedDraftSyncStatus.FAILED, deleting?.status)
        assertTrue(deleting?.deleteRequested == true)
    }

    @Test
    fun `secondary draft keeps its composer isolated and shares the base outbox`() {
        val slot = "11111111-1111-4111-8111-111111111111"
        val base = PersistedConversationState(
            route = route(),
            draftText = "base draft",
            outbox = listOf(outbox(localUuid = "local-old")),
        )
        val secondary = PersistedConversationState(
            route = route(),
            draftStorageSlot = slot,
            draftText = "secondary draft",
            outbox = listOf(outbox(localUuid = "local-new")),
            pendingReadBoundary = readBoundary(),
        )

        val plan = planConversationStateStorage(secondary, base)

        assertEquals(slot, plan.selectedState.draftStorageSlot)
        assertEquals("secondary draft", plan.selectedState.draftText)
        assertTrue(plan.selectedState.outbox.isEmpty())
        assertEquals(null, plan.selectedState.pendingReadBoundary)
        assertEquals(null, plan.baseState?.draftStorageSlot)
        assertEquals("base draft", plan.baseState?.draftText)
        assertEquals(
            listOf("local-new"),
            plan.baseState?.outbox?.map(PersistedOutboxEntry::localMessageUuid),
        )
        assertEquals(
            readBoundary(),
            plan.baseState?.pendingReadBoundary,
        )
    }

    @Test
    fun `base draft storage plan remains backward compatible`() {
        val state = PersistedConversationState(
            route = route(),
            draftText = "base draft",
            outbox = listOf(outbox()),
        )

        val plan = planConversationStateStorage(state, null)

        assertEquals(state, plan.selectedState)
        assertEquals(null, plan.baseState)
    }

    private fun outbox(
        localUuid: String = "local-message",
        status: PersistedOutboxStatus = PersistedOutboxStatus.UNCERTAIN,
    ) = PersistedOutboxEntry(
        localMessageUuid = localUuid,
        streamUuid = "stream",
        topicUuid = "topic",
        content = "Hello",
        createdAt = "2026-07-30T10:00:00Z",
        status = status,
    )

    private fun serverMessage(
        uuid: String = "server-message",
    ) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-07-30T10:00:03Z",
        createdAt = "2026-07-30T10:00:03Z",
        streamUuid = "stream",
        topicUuid = "topic",
        userUuid = "me",
        authorUuid = "me",
        payload = MessageResponsePayload("markdown", "Hello"),
        isOwn = true,
        reactions = emptyMap(),
    )

    private fun route() = PersistedConversationRoute(
        streamUuid = "stream",
        topicUuid = "topic",
        chatTitle = "Chat",
        topicName = "Topic",
        isDirectMessages = false,
    )

    private fun readBoundary(
        uuid: String = READ_BOUNDARY_UUID,
        createdAt: String = "2026-07-30T10:00:00Z",
    ) = PersistedReadBoundary(
        messageUuid = uuid,
        createdAt = createdAt,
    )

    private companion object {
        const val READ_BOUNDARY_UUID =
            "33333333-3333-4333-8333-333333333333"
    }
}
