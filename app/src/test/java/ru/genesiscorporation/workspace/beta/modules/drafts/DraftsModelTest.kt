package ru.genesiscorporation.workspace.beta.modules.drafts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedReadBoundary
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.remote.dto.DraftPayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.DraftResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedDraft

class DraftsModelTest {
    @Test
    fun `page requires descending updated-at uuid order and exact marker`() {
        val page = validateDraftPage(
            responses = listOf(
                response(DRAFT_B, "2026-07-30T10:00:00Z"),
                response(DRAFT_A, "2026-07-30T10:00:00Z"),
            ),
            nextPageMarkerHeader = DRAFT_A,
            expectedProjectId = PROJECT_UUID,
            expectedUserUuid = USER_UUID,
        )

        assertNull(page.error)
        assertEquals(DRAFT_A, page.nextPageMarker)
        assertEquals(listOf(DRAFT_B, DRAFT_A), page.drafts.map {
            it.response.uuid
        })

        assertNotNull(
            validateDraftPage(
                responses = listOf(
                    response(DRAFT_A, "2026-07-30T10:00:00Z"),
                    response(DRAFT_B, "2026-07-30T10:00:00Z"),
                ),
                nextPageMarkerHeader = DRAFT_B,
                expectedProjectId = PROJECT_UUID,
                expectedUserUuid = USER_UUID,
            ).error,
        )
        assertNotNull(
            validateDraftPage(
                responses = listOf(response(DRAFT_A)),
                nextPageMarkerHeader = DRAFT_B,
                expectedProjectId = PROJECT_UUID,
                expectedUserUuid = USER_UUID,
            ).error,
        )
    }

    @Test
    fun `local dirty content overrides server while server-only drafts remain`() {
        val serverA = validated(response(DRAFT_A, content = "server A"))
        val serverB = validated(response(DRAFT_B, content = "server B"))
        val local = localState(
            content = "local A",
            serverDraft = PersistedServerDraftState(
                draftUuid = DRAFT_A,
                entityTag = "\"1\"",
                serverRevision = 1,
                syncedContent = "server A",
                serverUpdatedAt = "2026-07-30T10:00:00Z",
                status = PersistedDraftSyncStatus.LOCAL,
            ),
        )

        val merged = mergeDraftItems(listOf(serverA, serverB), listOf(local))

        assertEquals(2, merged.size)
        assertEquals("local A", merged.single { it.draftUuid == DRAFT_A }.content)
        assertEquals(
            PersistedDraftSyncStatus.LOCAL,
            merged.single { it.draftUuid == DRAFT_A }.status,
        )
        assertNull(merged.single { it.draftUuid == DRAFT_A }.storageSlot)
        assertEquals("server B", merged.single { it.draftUuid == DRAFT_B }.content)
        assertEquals(
            DRAFT_B,
            merged.single { it.draftUuid == DRAFT_B }.storageSlot,
        )
    }

    @Test
    fun `same conversation keeps two selected server drafts in exact slots`() {
        val localA = localState(
            content = "local A",
            storageSlot = DRAFT_A,
            serverDraft = PersistedServerDraftState(
                draftUuid = DRAFT_A,
                entityTag = "\"1\"",
                serverRevision = 1,
                syncedContent = "server A",
                serverUpdatedAt = "2026-07-30T10:00:00Z",
                status = PersistedDraftSyncStatus.LOCAL,
            ),
        )
        val localB = localState(
            content = "local B",
            storageSlot = DRAFT_B,
            serverDraft = PersistedServerDraftState(
                draftUuid = DRAFT_B,
                entityTag = "\"1\"",
                serverRevision = 1,
                syncedContent = "server B",
                serverUpdatedAt = "2026-07-30T10:01:00Z",
                status = PersistedDraftSyncStatus.LOCAL,
            ),
        )

        val merged = mergeDraftItems(
            serverDrafts = listOf(
                validated(response(DRAFT_B, content = "server B")),
                validated(response(DRAFT_A, content = "server A")),
            ),
            localStates = listOf(localA, localB),
        )

        assertEquals(2, merged.size)
        assertEquals(
            setOf(DRAFT_A, DRAFT_B),
            merged.mapNotNull(DraftListItem::storageSlot).toSet(),
        )
        assertEquals("local A", merged.single { it.storageSlot == DRAFT_A }.content)
        assertEquals("local B", merged.single { it.storageSlot == DRAFT_B }.content)
    }

    @Test
    fun `externally deleted unchanged saved draft is not resurrected locally`() {
        val local = localState(
            content = "saved",
            serverDraft = PersistedServerDraftState(
                draftUuid = DRAFT_A,
                entityTag = "\"1\"",
                serverRevision = 1,
                syncedContent = "saved",
                serverUpdatedAt = "2026-07-30T10:00:00Z",
                status = PersistedDraftSyncStatus.SAVED,
            ),
        )

        assertTrue(mergeDraftItems(emptyList(), listOf(local)).isEmpty())
        assertEquals(
            1,
            mergeDraftItems(
                emptyList(),
                listOf(
                    local.copy(
                        draftText = "new local edit",
                        serverDraft = local.serverDraft?.copy(
                            status = PersistedDraftSyncStatus.LOCAL,
                        ),
                    ),
                ),
            ).size,
        )
    }

    @Test
    fun `read retry survives draft cleanup without becoming a draft row`() {
        val readOnlyState = localState(content = "").copy(
            draftUpdatedAt = null,
            pendingReadBoundary = PersistedReadBoundary(
                messageUuid = "77777777-7777-4777-8777-777777777777",
                createdAt = "2026-07-30T10:00:00Z",
            ),
        )

        assertTrue(readOnlyState.hasRetainedConversationWork())
        assertTrue(mergeDraftItems(emptyList(), listOf(readOnlyState)).isEmpty())
    }

    @Test
    fun `draft context labels direct chats without channel or topic decoration`() {
        assertEquals(
            "Eugene Frolov",
            draftContextLabel(
                streamName = " Eugene Frolov ",
                topicName = "Personal messages",
                isDirectMessages = true,
            ),
        )
        assertEquals(
            "Личный чат",
            draftContextLabel(
                streamName = " ",
                topicName = "Internal direct topic",
                isDirectMessages = true,
            ),
        )
        assertEquals(
            "#песочница · cassi bridge e2e",
            draftContextLabel(
                streamName = "песочница",
                topicName = "cassi bridge e2e",
                isDirectMessages = false,
            ),
        )
    }

    @Test
    fun `refresh recovers a create that committed before a server error`() {
        val local = localState(
            content = "first payload",
            serverDraft = PersistedServerDraftState(
                draftUuid = DRAFT_A,
                pendingCreateContent = "first payload",
                status = PersistedDraftSyncStatus.FAILED,
                errorMessage = "temporary failure",
            ),
        )

        val recovered = reconcilePendingDraftCreate(
            local,
            validated(response(DRAFT_A, content = "first payload")),
        )

        assertEquals(
            PersistedDraftSyncStatus.SAVED,
            recovered.serverDraft?.status,
        )
        assertEquals("\"1\"", recovered.serverDraft?.entityTag)
        assertNull(recovered.serverDraft?.pendingCreateContent)
        assertNull(recovered.serverDraft?.errorMessage)
    }

    @Test
    fun `refresh exposes a committed create with different server text as conflict`() {
        val local = localState(
            content = "my text",
            serverDraft = PersistedServerDraftState(
                draftUuid = DRAFT_A,
                pendingCreateContent = "first payload",
                status = PersistedDraftSyncStatus.FAILED,
            ),
        )

        val recovered = reconcilePendingDraftCreate(
            local,
            validated(response(DRAFT_A, content = "server text")),
        )

        assertEquals(
            PersistedDraftSyncStatus.CONFLICT,
            recovered.serverDraft?.status,
        )
        assertEquals(
            "server text",
            recovered.serverDraft?.conflict?.serverContent,
        )
    }

    private fun localState(
        content: String,
        storageSlot: String? = null,
        serverDraft: PersistedServerDraftState? = null,
    ) = PersistedConversationState(
        route = PersistedConversationRoute(
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            chatTitle = "Sandbox",
            topicName = "Drafts",
            isDirectMessages = false,
        ),
        draftStorageSlot = storageSlot,
        draftText = content,
        draftUpdatedAt = "2026-07-30T10:02:00Z",
        serverDraft = serverDraft,
    )

    private fun validated(response: DraftResponse) = ValidatedDraft(
        response = response,
        entityTag = "\"${response.revision}\"",
    )

    private fun response(
        uuid: String,
        updatedAt: String = "2026-07-30T10:00:00Z",
        content: String = uuid,
    ) = DraftResponse(
        uuid = uuid,
        projectId = PROJECT_UUID,
        userUuid = USER_UUID,
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        payload = DraftPayload(kind = "markdown", content = content),
        revision = 1,
        createdAt = "2026-07-30T09:00:00Z",
        updatedAt = updatedAt,
    )

    private companion object {
        const val DRAFT_A = "11111111-1111-4111-8111-111111111111"
        const val DRAFT_B = "22222222-2222-4222-8222-222222222222"
        const val PROJECT_UUID = "33333333-3333-4333-8333-333333333333"
        const val USER_UUID = "44444444-4444-4444-8444-444444444444"
        const val STREAM_UUID = "55555555-5555-4555-8555-555555555555"
        const val TOPIC_UUID = "66666666-6666-4666-8666-666666666666"
    }
}
