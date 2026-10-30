package com.exordos.workspace.data.remote

import io.ktor.http.URLBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.data.remote.dto.MessagesRequest
import com.exordos.workspace.data.remote.dto.MentionedMessagesRequest

@OptIn(ExperimentalSerializationApi::class)
class RequestQuerySerializationTest {
    @Test
    fun messageIdsUseRepeatedKeysAndRetainOrderAndDuplicates() {
        val ids = listOf("first", "second", "first")
        val url = URLBuilder("https://workspace.example/messages/").apply {
            appendGetQueryParameters(Properties.encodeToStringMap(MessagesByIdsRequest(ids).data))
        }.build()

        assertEquals(ids, url.parameters.getAll("uuid"))
        assertNull(url.parameters["uuid.0"])
        assertEquals(setOf("uuid"), url.parameters.names())
    }

    @Test
    fun emptyIdListAddsNoInventedEmptyFilter() {
        val url = URLBuilder("https://workspace.example/messages/").apply {
            appendGetQueryParameters(Properties.encodeToStringMap(MessagesByIdsRequest(emptyList()).data))
        }.build()

        assertEquals(emptySet<String>(), url.parameters.names())
    }

    @Test
    fun nullableTopicIsOmittedWhileStreamFilterRemains() {
        val url = URLBuilder("https://workspace.example/messages/").apply {
            appendGetQueryParameters(Properties.encodeToStringMap(MessagesRequest("stream", null).data))
        }.build()

        assertEquals("stream", url.parameters["stream_uuid"])
        assertNull(url.parameters["topic_uuid"])
    }

    @Test
    fun conversationAndMentionQueriesKeepTheirWireNames() {
        assertEquals(mapOf("stream_uuid" to "stream", "topic_uuid" to "topic"),
            Properties.encodeToStringMap(MessagesRequest("stream", "topic").data))
        assertEquals(mapOf("mentioned" to "true"),
            Properties.encodeToStringMap(MentionedMessagesRequest().data))
    }

    @Test
    fun specialCharactersAreEncodedOnceAndDoNotInjectParameters() {
        val value = "Текст + 100% & other=value /?#"
        val url = URLBuilder("https://workspace.example/messages/?page_limit=10").apply {
            appendGetQueryParameters(mapOf("query" to value, "epoch_version>" to "42"))
        }.build()
        val reparsed = URLBuilder(url.toString()).build()

        assertEquals(value, reparsed.parameters["query"])
        assertEquals("10", reparsed.parameters["page_limit"])
        assertEquals("42", reparsed.parameters["epoch_version>"])
        assertEquals(setOf("page_limit", "query", "epoch_version>"), reparsed.parameters.names())
        assertEquals("", reparsed.fragment)
    }

    @Test
    fun listIndexRemovalDoesNotStripOrdinaryDottedFieldNames() {
        val url = URLBuilder("https://workspace.example/messages/").apply {
            appendGetQueryParameters(linkedMapOf("user.name" to "Alice", "uuid.0" to "one", "uuid.1" to "two"))
        }.build()

        assertEquals("Alice", url.parameters["user.name"])
        assertEquals(listOf("one", "two"), url.parameters.getAll("uuid"))
    }
}
