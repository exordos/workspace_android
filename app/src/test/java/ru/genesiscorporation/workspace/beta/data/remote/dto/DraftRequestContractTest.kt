package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DraftRequestContractTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `draft list uses bounded descending updated-at keyset pagination`() {
        val request = DraftsRequest(
            pageMarker = DRAFT_UUID.uppercase(),
        )
        val params = Properties.encodeToStringMap(request.data)

        assertEquals(DEFAULT_DRAFT_PAGE_SIZE.toString(), params["page_limit"])
        assertEquals(DRAFT_UUID, params["page_marker"])
        assertEquals("updated_at", params["sort_key"])
        assertEquals("desc", params["sort_dir"])
        assertNull(params["stream_uuid"])
        assertNull(params["topic_uuid"])
    }

    @Test
    fun `create keeps client uuid and normalized first payload`() {
        val request = CreateDraftRequest(
            draftUuid = DRAFT_UUID,
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            content = "  retained after timeout  ",
        )

        assertEquals(DRAFT_UUID, request.data.uuid)
        assertEquals(STREAM_UUID, request.data.streamUuid)
        assertEquals(TOPIC_UUID, request.data.topicUuid)
        assertEquals("markdown", request.data.payload.kind)
        assertEquals("retained after timeout", request.data.payload.content)
    }

    @Test
    fun `draft wire payload always includes the required markdown kind`() {
        val create = CreateDraftRequest(
            draftUuid = DRAFT_UUID,
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            content = "wire payload",
        )
        val update = UpdateDraftRequest(
            draftUuid = DRAFT_UUID,
            content = "updated wire payload",
            entityTag = "\"1\"",
        )
        val json = Json {
            encodeDefaults = false
            explicitNulls = false
        }

        val createPayload = json
            .encodeToJsonElement(CreateDraftRequestData.serializer(), create.data)
            .jsonObject
            .getValue("payload")
            .jsonObject
        val updatePayload = json
            .encodeToJsonElement(UpdateDraftRequestData.serializer(), update.data)
            .jsonObject
            .getValue("payload")
            .jsonObject

        assertEquals("markdown", createPayload.getValue("kind").jsonPrimitive.content)
        assertEquals("wire payload", createPayload.getValue("content").jsonPrimitive.content)
        assertEquals("markdown", updatePayload.getValue("kind").jsonPrimitive.content)
        assertEquals(
            "updated wire payload",
            updatePayload.getValue("content").jsonPrimitive.content,
        )
    }

    @Test
    fun `update and delete require a strong revision entity tag`() {
        val update = UpdateDraftRequest(DRAFT_UUID, "next", "\"2\"")
        val delete = DeleteDraftRequest(DRAFT_UUID, "\"2\"")

        assertEquals("\"2\"", update.additionalHeaders["If-Match"])
        assertEquals("\"2\"", delete.additionalHeaders["If-Match"])
        for (invalid in listOf("2", "W/\"2\"", "\"0\"", "\"02\"")) {
            assertThrows(IllegalArgumentException::class.java) {
                UpdateDraftRequest(DRAFT_UUID, "next", invalid)
            }
            assertThrows(IllegalArgumentException::class.java) {
                DeleteDraftRequest(DRAFT_UUID, invalid)
            }
        }
    }

    @Test
    fun `draft requests reject malformed scope and content`() {
        assertThrows(IllegalArgumentException::class.java) {
            DraftsRequest(topicUuid = TOPIC_UUID)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DraftsRequest(pageLimit = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CreateDraftRequest(
                "$DRAFT_UUID/path",
                STREAM_UUID,
                TOPIC_UUID,
                "text",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CreateDraftRequest(DRAFT_UUID, STREAM_UUID, TOPIC_UUID, "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CreateDraftRequest(
                DRAFT_UUID,
                STREAM_UUID,
                TOPIC_UUID,
                "x".repeat(MAX_DRAFT_CONTENT_CHARS + 1),
            )
        }
    }

    @Test
    fun `draft response validates ownership timestamps and revision etag`() {
        val response = response()
        val validated = validateDraftResponse(
            response = response,
            expectedProjectId = PROJECT_UUID,
            expectedUserUuid = USER_UUID,
            expectedStreamUuid = STREAM_UUID,
            expectedTopicUuid = TOPIC_UUID,
            responseEntityTag = "\"3\"",
        )

        assertEquals("\"3\"", validated.entityTag)
        assertEquals("saved", validated.response.payload.content)
        assertThrows(IllegalArgumentException::class.java) {
            validateDraftResponse(
                response.copy(userUuid = OTHER_USER_UUID),
                expectedUserUuid = USER_UUID,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateDraftResponse(
                response.copy(revision = 2),
                responseEntityTag = "\"3\"",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateDraftResponse(
                response.copy(updatedAt = "not-a-time"),
            )
        }
    }

    @Test
    fun `conflict parser accepts only an owned valid current snapshot`() {
        val body = """
            {
              "current": {
                "uuid": "$DRAFT_UUID",
                "project_id": "$PROJECT_UUID",
                "user_uuid": "$USER_UUID",
                "stream_uuid": "$STREAM_UUID",
                "topic_uuid": "$TOPIC_UUID",
                "payload": {"kind": "markdown", "content": "server"},
                "revision": 4,
                "created_at": "2026-07-30T10:00:00Z",
                "updated_at": "2026-07-30T10:01:00Z"
              }
            }
        """.trimIndent()

        assertNotNull(
            parseDraftConflictBody(
                body,
                "\"4\"",
                DRAFT_UUID,
                PROJECT_UUID,
                USER_UUID,
                STREAM_UUID,
                TOPIC_UUID,
            ),
        )
        assertNull(
            parseDraftConflictBody(
                body,
                "\"4\"",
                DRAFT_UUID,
                PROJECT_UUID,
                OTHER_USER_UUID,
                STREAM_UUID,
                TOPIC_UUID,
            ),
        )
        assertNull(
            parseDraftConflictBody(
                body,
                "\"3\"",
                DRAFT_UUID,
                PROJECT_UUID,
                USER_UUID,
                STREAM_UUID,
                TOPIC_UUID,
            ),
        )
    }

    private fun response() = DraftResponse(
        uuid = DRAFT_UUID,
        projectId = PROJECT_UUID,
        userUuid = USER_UUID,
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        payload = DraftPayload(kind = "markdown", content = "saved"),
        revision = 3,
        createdAt = "2026-07-30T10:00:00Z",
        updatedAt = "2026-07-30T10:01:00Z",
    )

    private companion object {
        const val DRAFT_UUID = "11111111-1111-4111-8111-111111111111"
        const val STREAM_UUID = "22222222-2222-4222-8222-222222222222"
        const val TOPIC_UUID = "33333333-3333-4333-8333-333333333333"
        const val PROJECT_UUID = "44444444-4444-4444-8444-444444444444"
        const val USER_UUID = "55555555-5555-4555-8555-555555555555"
        const val OTHER_USER_UUID = "66666666-6666-4666-8666-666666666666"
    }
}
