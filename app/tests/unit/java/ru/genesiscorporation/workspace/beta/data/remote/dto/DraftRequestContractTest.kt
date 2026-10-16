package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class DraftRequestContractTest {
    private val json = Json

    @Test
    fun creationBindsCallerIdentityAndBothConversationIds() {
        val request = CreateDraftRequest("draft", "stream", "topic", "Строка\n![a](urn:image:fixture)")

        assertEquals(json.parseToJsonElement("""{
            "uuid":"draft","stream_uuid":"stream","topic_uuid":"topic",
            "payload":{"kind":"markdown","content":"Строка\n![a](urn:image:fixture)"}
        }"""), json.parseToJsonElement(json.encodeToString(request.data)))
        assertEquals(HTTPMethod.POST, request.method)
        assertTrue(request.requiresApiKey)
    }

    @Test
    fun updateKeepsEmptyContentAndDoesNotMoveTheDraft() {
        val request = UpdateDraftRequest("draft", "", 7)

        assertEquals(json.parseToJsonElement("""{"payload":{"kind":"markdown","content":""}}"""),
            json.parseToJsonElement(json.encodeToString(request.data)))
        assertEquals("/api/workspace/v1/messenger/drafts/draft", request.url)
        assertEquals(HTTPMethod.POST, request.method)
        assertEquals(mapOf("If-Match" to "\"7\""), request.additionalHeaders)
    }

    @Test
    fun updateAndDeletePreserveQuotedOptimisticConcurrencyRevisions() {
        for (revision in listOf(0, 1, 42, Int.MAX_VALUE)) {
            val update = UpdateDraftRequest("draft", "text", revision)
            val delete = DeleteDraftRequest("draft", revision)

            assertEquals("\"$revision\"", update.additionalHeaders["If-Match"])
            assertEquals(update.additionalHeaders, delete.additionalHeaders)
            assertEquals(update.url, delete.url)
            assertEquals(HTTPMethod.DELETE, delete.method)
            assertTrue(delete.requiresApiKey)
        }
    }

    @Test
    fun serverDraftRetainsRevisionAndConversationForTheNextMutation() {
        val draft = json.decodeFromString<Draft>("""{
            "uuid":"draft","stream_uuid":"stream","topic_uuid":"topic",
            "updated_at":"2026-01-01T00:00:00Z","revision":23,
            "payload":{"kind":"markdown","content":"Восстановленный текст"}
        }""")

        assertEquals("stream", draft.streamUuid)
        assertEquals("topic", draft.topicUuid)
        assertEquals("Восстановленный текст", draft.payload.content)
        assertEquals("\"23\"", DeleteDraftRequest(draft.uuid, draft.revision).additionalHeaders["If-Match"])
    }
}
