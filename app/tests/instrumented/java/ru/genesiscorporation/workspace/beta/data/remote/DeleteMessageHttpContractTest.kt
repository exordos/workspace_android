package ru.genesiscorporation.workspace.beta.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.SessionCookieStore
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.modules.chatdialog.MessageDeletion
import ru.genesiscorporation.workspace.beta.modules.chatdialog.MessageSelection
import ru.genesiscorporation.workspace.beta.modules.chatdialog.canSelectMessage
import ru.genesiscorporation.workspace.beta.modules.chatdialog.canDeleteMessage
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeleteMessageHttpContractTest {
    private lateinit var files: File
    private lateinit var storeJob: Job
    private lateinit var preferences: ApiKeyRepository
    private lateinit var user: UserViewModel
    private lateinit var http: HttpClient
    private lateinit var api: WorkspaceAPIClient
    private lateinit var messages: EventsRepository

    @Before
    fun setUp() {
        files = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "cassi-delete-http-${UUID.randomUUID()}",
        ).apply { check(mkdirs()) }
        storeJob = SupervisorJob()
        val storeScope = CoroutineScope(storeJob + Dispatchers.IO)
        // Context.dataStore is a singleton. Inject a separate store so this test
        // never reads or changes the signed-in user's server or credentials.
        val store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(files, "delete-http.preferences_pb")
        }
        preferences = ApiKeyRepository(store, storeScope)
        user = UserViewModel(preferences)
        http = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
            }
            install(ContentNegotiation) { json() }
        }
        api = WorkspaceAPIClient(http, user, SessionCookieStore()).apply {
            baseAccessToken = "cassi-delete-contract-fixture"
        }
        messages = EventsRepository()
    }

    @After
    fun tearDown() = runBlocking {
        http.close()
        user.viewModelScope.cancel()
        messages.close()
        storeJob.cancelAndJoin()
        files.deleteRecursively()
        Unit
    }

    @Test
    fun empty204ResponseSucceedsAndUsesExactDeleteContract() = runBlocking {
        LoopbackDeleteServer(204, "No Content").use { server ->
            preferences.addBaseUrl(server.baseUrl)

            val response = api.performRequest(DeleteMessageRequest(MESSAGE_UUID))

            assertEquals(ApiResult.Success(""), response)
            assertDeleteContract(server.awaitRequest())
        }
    }

    @Test
    fun successfulHttpDeletionRemovesOnlyTheSelectedMessage() = runBlocking {
        val selected = message(MESSAGE_UUID)
        val kept = message(KEPT_MESSAGE_UUID)
        messages.addStreamTopicMessages(STREAM_UUID, TOPIC_UUID, listOf(selected, kept))
        messages.setInitialMessagesPool(listOf(selected, kept))
        LoopbackDeleteServer(204, "No Content").use { server ->
            preferences.addBaseUrl(server.baseUrl)
            val deletion = deletion()

            deletion.delete(selected)

            assertEquals(listOf(kept), messages.messagesPool.value)
            assertEquals(listOf(kept), messages.streamTopicMessages.value["$STREAM_UUID.$TOPIC_UUID"])
            assertTrue(deletion.deletingMessageUuids.value.isEmpty())
            assertNull(deletion.actionError.value)
            assertDeleteContract(server.awaitRequest())
        }
    }

    @Test
    fun forbiddenHttpDeletionRetainsTheMessageAndReportsFailure() = runBlocking {
        assertFailedDeletionKeepsMessage(403, "Forbidden")
    }

    @Test
    fun serverErrorDeletionRetainsTheMessageAndReportsFailure() = runBlocking {
        assertFailedDeletionKeepsMessage(500, "Internal Server Error")
    }

    @Test
    fun partialHttpBatchKeepsFailuresSelectedAndRetriesOnlyFailures() = runBlocking {
        val first = message(MESSAGE_UUID)
        val second = message(KEPT_MESSAGE_UUID)
        messages.addStreamTopicMessages(STREAM_UUID, TOPIC_UUID, listOf(first, second))
        messages.setInitialMessagesPool(listOf(first, second))
        LoopbackDeleteServer(
            listOf(
                LoopbackResponse(500, "Internal Server Error", """{"error":"Request failed"}"""),
                LoopbackResponse(204, "No Content"),
                LoopbackResponse(204, "No Content"),
            ),
        ).use { server ->
            preferences.addBaseUrl(server.baseUrl)
            val deletion = deletion()
            val selection = MessageSelection(
                canSelect = { canSelectMessage(it, STREAM_UUID, TOPIC_UUID) },
                canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
                deletion = deletion,
            )
            selection.startMessageSelection(first)
            selection.toggleMessageSelection(second)

            selection.deleteSelectedMessages()

            assertEquals(listOf(first), messages.messagesPool.value)
            assertEquals(setOf(MESSAGE_UUID), selection.selectedMessageUuids.value)
            assertEquals(
                R.string.messages_delete_partial_failure,
                deletion.actionError.value?.resourceId,
            )
            assertEquals(listOf(1, 2, 1), deletion.actionError.value?.formatArgs)
            assertDeleteContract(server.awaitRequest(), MESSAGE_UUID)
            assertDeleteContract(server.awaitRequest(), KEPT_MESSAGE_UUID)

            selection.deleteSelectedMessages()

            assertTrue(messages.messagesPool.value.isEmpty())
            assertTrue(selection.selectedMessageUuids.value.isEmpty())
            assertNull(deletion.actionError.value)
            assertDeleteContract(server.awaitRequest(), MESSAGE_UUID)
        }
    }

    private suspend fun assertFailedDeletionKeepsMessage(status: Int, reason: String) {
        val selected = message(MESSAGE_UUID)
        messages.addStreamTopicMessages(STREAM_UUID, TOPIC_UUID, listOf(selected))
        messages.setInitialMessagesPool(listOf(selected))
        LoopbackDeleteServer(status, reason, """{"error":"Cannot delete message"}""").use { server ->
            preferences.addBaseUrl(server.baseUrl)
            val deletion = deletion()

            deletion.delete(selected)

            assertEquals(listOf(selected), messages.messagesPool.value)
            assertEquals(listOf(selected), messages.streamTopicMessages.value["$STREAM_UUID.$TOPIC_UUID"])
            assertTrue(deletion.deletingMessageUuids.value.isEmpty())
            assertNotNull(deletion.actionError.value)
            assertDeleteContract(server.awaitRequest())
        }
    }

    private fun deletion() = MessageDeletion(
        canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
        request = { api.performRequest(DeleteMessageRequest(it)) },
        onDeleted = messages::removeMessage,
    )

    private fun assertDeleteContract(request: CapturedRequest, messageUuid: String = MESSAGE_UUID) {
        assertEquals("DELETE /api/workspace/v1/messenger/messages/$messageUuid HTTP/1.1", request.requestLine)
        assertEquals("Bearer cassi-delete-contract-fixture", request.headers["authorization"])
        assertTrue(request.body.isEmpty())
        assertNull(request.headers["transfer-encoding"])
    }

    private fun message(uuid: String) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-09-07T12:00:00Z",
        createdAt = "2026-09-07T12:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = "current-user",
        authorUuid = "current-user",
        payload = MessageResponsePayload("markdown", "Delete contract fixture"),
        isOwn = true,
        reactions = emptyMap(),
        read = true,
    )

    private companion object {
        const val MESSAGE_UUID = "ac0819d1-a91f-4b87-a1bc-e4b0d353f162"
        const val KEPT_MESSAGE_UUID = "2ff4fdd2-cb41-43a9-8532-262b8b36b858"
        const val STREAM_UUID = "stream"
        const val TOPIC_UUID = "topic"
    }
}

private data class CapturedRequest(
    val requestLine: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

private data class LoopbackResponse(val status: Int, val reason: String, val body: String = "")

private class LoopbackDeleteServer(responses: List<LoopbackResponse>) : AutoCloseable {
    constructor(status: Int, reason: String, body: String = "") :
        this(listOf(LoopbackResponse(status, reason, body)))
    private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val received = Channel<CapturedRequest>(Channel.UNLIMITED)
    val baseUrl = "http://127.0.0.1:${socket.localPort}"

    init {
        scope.launch {
            try {
                for (response in responses) {
                    socket.accept().use { connection ->
                        connection.soTimeout = 5_000
                        val input = connection.getInputStream().buffered()
                        fun readLine(): String = buildString {
                            while (true) {
                                val next = input.read()
                                check(next >= 0) { "Unexpected end of request headers" }
                                if (next == '\n'.code) break
                                if (next != '\r'.code) append(next.toChar())
                                check(length <= 8_192) { "Request header exceeded test limit" }
                            }
                        }
                        val requestLine = readLine()
                        val headers = buildMap {
                            while (true) {
                                val line = readLine()
                                if (line.isEmpty()) break
                                val separator = line.indexOf(':')
                                check(separator > 0)
                                put(line.substring(0, separator).lowercase(), line.substring(separator + 1).trim())
                            }
                        }
                        val length = headers["content-length"]?.toInt() ?: 0
                        check(length in 0..8_192)
                        val requestBody = ByteArray(length)
                        var offset = 0
                        while (offset < length) {
                            val count = input.read(requestBody, offset, length - offset)
                            check(count > 0)
                            offset += count
                        }
                        received.send(CapturedRequest(requestLine, headers, requestBody))
                        val responseBody = response.body.toByteArray(Charsets.UTF_8)
                        val bodyHeaders = if (response.status == 204) "" else {
                            "Content-Length: ${responseBody.size}\r\n" +
                                "Content-Type: application/json\r\n"
                        }
                        val responseHeaders = "HTTP/1.1 ${response.status} ${response.reason}\r\n" +
                            bodyHeaders + "Connection: close\r\n\r\n"
                        connection.getOutputStream().apply {
                            write(responseHeaders.toByteArray(Charsets.US_ASCII))
                            write(responseBody)
                            flush()
                        }
                    }
                }
                received.close()
            } catch (failure: Throwable) {
                received.close(failure)
            }
        }
    }

    suspend fun awaitRequest(): CapturedRequest = withTimeout(5_000) { received.receive() }

    override fun close() {
        socket.close()
        scope.cancel()
    }
}
