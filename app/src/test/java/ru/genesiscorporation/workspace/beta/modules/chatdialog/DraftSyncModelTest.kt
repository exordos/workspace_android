package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.dto.DraftPayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.DraftResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedDraft

class DraftSyncModelTest {
    @Test
    fun `first create payload stays stable across newer local edits`() {
        val first = beginDraftSync(
            existing = null,
            localContent = " first ",
            newUuid = { DRAFT_UUID },
        )
        val edited = beginDraftSync(first, "second")

        assertEquals("first", first?.pendingCreateContent)
        assertEquals("first", draftCreatePayload(edited!!, "second"))
        val saved = applyDraftSaveSuccess(
            edited,
            server(revision = 1, content = "first"),
            sentContent = "first",
            currentLocalContent = "second",
        )
        assertEquals(PersistedDraftSyncStatus.LOCAL, saved.status)
        assertEquals("\"1\"", saved.entityTag)
        assertNull(saved.pendingCreateContent)
    }

    @Test
    fun `matching save becomes saved and blank composer becomes tombstone`() {
        val local = beginDraftSync(
            null,
            "draft",
            newUuid = { DRAFT_UUID },
        )!!
        val saved = applyDraftSaveSuccess(
            local,
            server(1, "draft"),
            sentContent = "draft",
            currentLocalContent = " draft ",
        )
        val deleting = beginDraftSync(saved, "")

        assertEquals(PersistedDraftSyncStatus.SAVED, saved.status)
        assertEquals(PersistedDraftSyncStatus.DELETING, deleting?.status)
        assertTrue(deleting?.deleteRequested == true)
    }

    @Test
    fun `matching conflict is reconciled but divergent conflict is explicit`() {
        val saved = persisted()
        val matching = applyDraftConflict(
            saved,
            server(2, " local "),
            currentLocalContent = "local",
        )
        val divergent = applyDraftConflict(
            saved,
            server(2, "server"),
            currentLocalContent = "local",
        )

        assertEquals(PersistedDraftSyncStatus.SAVED, matching.status)
        assertEquals("\"2\"", matching.entityTag)
        assertEquals(PersistedDraftSyncStatus.CONFLICT, divergent.status)
        assertEquals("server", divergent.conflict?.serverContent)
    }

    @Test
    fun `all three conflict resolutions retain the newest server etag`() {
        val conflict = applyDraftConflict(
            persisted(),
            server(2, "server"),
            currentLocalContent = "local",
        )

        val accepted = acceptServerDraftVersion(conflict)
        assertEquals("server", accepted?.first)
        assertEquals(PersistedDraftSyncStatus.SAVED, accepted?.second?.status)
        assertEquals("\"2\"", accepted?.second?.entityTag)

        val kept = keepLocalDraftVersion(conflict)
        assertEquals(PersistedDraftSyncStatus.LOCAL, kept?.status)
        assertEquals("\"2\"", kept?.entityTag)

        val deleting = deleteConflictedServerDraft(conflict)
        assertEquals(PersistedDraftSyncStatus.DELETING, deleting?.status)
        assertEquals("\"2\"", deleting?.entityTag)
        assertTrue(deleting?.deleteRequested == true)
    }

    @Test
    fun `retry policy separates transient failures from final rejections`() {
        assertTrue(
            isRetryableDraftError(
                ApiError("offline", "NETWORK", ApiErrorKind.NETWORK),
            ),
        )
        assertTrue(
            isRetryableDraftError(
                ApiError(
                    "rate",
                    "429",
                    ApiErrorKind.RATE_LIMITED,
                    httpStatus = 429,
                ),
            ),
        )
        assertFalse(
            isRetryableDraftError(
                ApiError(
                    "invalid",
                    "400",
                    ApiErrorKind.VALIDATION,
                    httpStatus = 400,
                ),
            ),
        )
        assertFalse(
            isRetryableDraftError(
                ApiError(
                    "forbidden",
                    "403",
                    ApiErrorKind.FORBIDDEN,
                    httpStatus = 403,
                ),
            ),
        )
    }

    @Test
    fun `delete conflict retries only when server retained the synced content`() {
        val deleting = persisted().copy(
            status = PersistedDraftSyncStatus.DELETING,
            deleteRequested = true,
        )

        assertTrue(
            canRetryMatchingDraftDeleteConflict(
                deleting,
                server(2, "saved"),
            ),
        )
        assertFalse(
            canRetryMatchingDraftDeleteConflict(
                deleting,
                server(2, "changed elsewhere"),
            ),
        )
    }

    private fun persisted() = PersistedServerDraftState(
        draftUuid = DRAFT_UUID,
        entityTag = "\"1\"",
        serverRevision = 1,
        syncedContent = "saved",
        serverUpdatedAt = "2026-07-30T10:00:00Z",
        status = PersistedDraftSyncStatus.SAVED,
    )

    private fun server(
        revision: Int,
        content: String,
    ) = ValidatedDraft(
        response = DraftResponse(
            uuid = DRAFT_UUID,
            projectId = PROJECT_UUID,
            userUuid = USER_UUID,
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            payload = DraftPayload(
                kind = "markdown",
                content = content.trim(),
            ),
            revision = revision,
            createdAt = "2026-07-30T10:00:00Z",
            updatedAt = "2026-07-30T10:01:00Z",
        ),
        entityTag = "\"$revision\"",
    )

    private companion object {
        const val DRAFT_UUID = "11111111-1111-4111-8111-111111111111"
        const val PROJECT_UUID = "22222222-2222-4222-8222-222222222222"
        const val USER_UUID = "33333333-3333-4333-8333-333333333333"
        const val STREAM_UUID = "44444444-4444-4444-8444-444444444444"
        const val TOPIC_UUID = "55555555-5555-4555-8555-555555555555"
    }
}
