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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.SessionCookieStore
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.remote.dto.ForwardMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ForwardDeliveryStatus
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ForwardDestination
import ru.genesiscorporation.workspace.beta.modules.chatdialog.MessageForwarding
import ru.genesiscorporation.workspace.beta.modules.chatdialog.buildWorkspaceForwardMarkdown
import ru.genesiscorporation.workspace.beta.modules.chatdialog.createForwardPreparation
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ForwardFileRecord
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ForwardSourceFile
import java.security.MessageDigest
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ForwardMessageHttpContractTest {
    private lateinit var files: File
    private lateinit var storeJob: Job
    private lateinit var preferences: ApiKeyRepository
    private lateinit var user: UserViewModel
    private lateinit var http: HttpClient
    private lateinit var api: WorkspaceAPIClient

    @Before fun setUp() {
        files = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cassi-forward-http-${UUID.randomUUID()}").apply { check(mkdirs()) }
        storeJob = SupervisorJob()
        val scope = CoroutineScope(storeJob + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(files, "forward.preferences_pb") }
        preferences = ApiKeyRepository(store, scope)
        user = UserViewModel(preferences)
        http = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 5_000; connectTimeoutMillis = 5_000 }
            install(ContentNegotiation) { json() }
        }
        api = WorkspaceAPIClient(http, user, SessionCookieStore()).apply { baseAccessToken = "cassi-forward-contract-fixture" }
    }

    @After fun tearDown() = runBlocking {
        http.close()
        user.viewModelScope.cancel()
        storeJob.cancelAndJoin()
        files.deleteRecursively()
        Unit
    }

    @Test fun postAndReadbackConfirmTheReturnedMessageWithoutOptimisticSuccess() = runBlocking {
        val content = requireNotNull(buildWorkspaceForwardMarkdown(listOf(message(SOURCE))))
        val persisted = message(RETURNED).copy(payload = MessageResponsePayload("markdown", content))
        ForwardLoopbackServer(listOf(201 to """{"uuid":"$RETURNED","topic_uuid":"$TOPIC"}""", 200 to Json.encodeToString(listOf(persisted)))).use { server ->
            preferences.addBaseUrl(server.baseUrl)
            val confirmed = mutableListOf<MessageResponse>()
            val forwarding = sender { confirmed += it }
            forwarding.send(ForwardDestination(STREAM, TOPIC))
            assertEquals(ForwardDeliveryStatus.COMPLETED, forwarding.state.value.status)
            assertEquals(listOf(persisted), confirmed)
            val requests = server.awaitRequests()
            assertEquals("POST /api/workspace/v1/messenger/messages/ HTTP/1.1", requests[0].line)
            assertEquals("Bearer cassi-forward-contract-fixture", requests[0].authorization)
            val body = Json.parseToJsonElement(requests[0].body).jsonObject
            assertEquals(REQUESTED, body["uuid"]?.jsonPrimitive?.content)
            assertEquals(STREAM, body["stream_uuid"]?.jsonPrimitive?.content)
            assertEquals(TOPIC, body["topic_uuid"]?.jsonPrimitive?.content)
            assertEquals(content, body["payload"]?.jsonObject?.get("content")?.jsonPrimitive?.content)
            assertEquals("GET /api/workspace/v1/messenger/messages/?uuid=$RETURNED HTTP/1.1", requests[1].line)
        }
    }

    @Test fun ambiguousServerFailureReadsButDoesNotRepeatThePost() = runBlocking {
        ForwardLoopbackServer(listOf(500 to "{}", 200 to "[]", 200 to "[]")).use { server ->
            preferences.addBaseUrl(server.baseUrl)
            var confirmed = false
            val forwarding = sender { confirmed = true }
            forwarding.send(ForwardDestination(STREAM, TOPIC))
            forwarding.send(ForwardDestination(STREAM, TOPIC))
            forwarding.verify()
            assertFalse(confirmed)
            assertEquals(ForwardDeliveryStatus.UNCERTAIN, forwarding.state.value.status)
            val requests = server.awaitRequests()
            assertEquals(1, requests.count { it.line.startsWith("POST ") })
            assertEquals(2, requests.count { it.line == "GET /api/workspace/v1/messenger/messages/?uuid=$REQUESTED HTTP/1.1" })
        }
    }

    @Test fun copiesSourceAttachmentsToTheDestinationBeforePostingTheMaterializedSnapshot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalFile = "00000000-0000-0000-0000-000000000007"
        val destinationFile = "00000000-0000-0000-0000-000000000008"
        val fileContent = "CASSI restricted attachment fixture"
        val fileHash = MessageDigest.getInstance("SHA-256").digest(fileContent.toByteArray()).joinToString("") { "%02x".format(it) }
        val source = message(SOURCE).copy(payload = MessageResponsePayload("markdown", "Private source text\n\n[specification](urn:file:$originalFile)"))
        val preparedSource = source.copy(payload = source.payload.copy(content = source.payload.content.replace(originalFile, destinationFile)))
        val content = requireNotNull(buildWorkspaceForwardMarkdown(listOf(preparedSource)))
        val persisted = message(RETURNED).copy(payload = MessageResponsePayload("markdown", content))
        val fileRecord = ForwardFileRecord(destinationFile, "report.pdf", fileHash, STREAM, AUTHOR, "application/pdf")
        ForwardLoopbackServer(listOf(
            200 to Json.encodeToString(ForwardSourceFile(originalFile, "report.pdf", fileHash, "application/pdf")),
            200 to fileContent,
            200 to "[]",
            201 to """{"uuid":"$destinationFile","name":"report.pdf"}""",
            200 to Json.encodeToString(listOf(fileRecord)),
            201 to """{"uuid":"$RETURNED","topic_uuid":"$TOPIC"}""",
            200 to Json.encodeToString(listOf(persisted)),
        )).use { server ->
            preferences.addBaseUrl(server.baseUrl)
            val preparation = createForwardPreparation(context, listOf(source), api, AUTHOR)
            var confirmed = false
            val forwarding = MessageForwarding(listOf(source), AUTHOR,
                post = { uuid, target, text -> api.performRequest(ForwardMessageRequest(uuid, target.streamUuid, target.topicUuid, text)) },
                read = { api.performRequest(MessagesByIdsRequest(listOf(it))) }, onConfirmed = { confirmed = true },
                newUuid = { REQUESTED }, prepare = preparation::prepare)
            forwarding.send(ForwardDestination(STREAM, TOPIC))
            assertTrue(confirmed)
            val requests = server.awaitRequests()
            assertEquals("GET /api/workspace/v1/messenger/files/$originalFile HTTP/1.1", requests[0].line)
            assertEquals("GET /api/workspace/v1/messenger/files/$originalFile/actions/download HTTP/1.1", requests[1].line)
            assertTrue(requests[2].line.contains("stream_uuid=$STREAM"))
            assertTrue(requests[2].line.contains("hash=$fileHash"))
            assertEquals("POST /api/workspace/v1/messenger/files/ HTTP/1.1", requests[3].line)
            assertTrue(requests[3].body.contains("name=stream_uuid") || requests[3].body.contains("name=\"stream_uuid\""))
            assertTrue(requests[3].body.contains(STREAM))
            assertTrue(requests[3].body.contains(fileContent))
            assertTrue(requests[3].body.contains("application/pdf"))
            assertTrue(requests[3].body.contains("report.pdf"))
            val posted = Json.parseToJsonElement(requests[5].body).jsonObject["payload"]?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()
            assertTrue(posted.contains("Private source text"))
            assertTrue(posted.contains("urn:file:$destinationFile"))
            assertFalse(posted.contains(originalFile))
            assertFalse(posted.contains("urn:quote:"))
        }
    }

    private fun sender(onConfirmed: (MessageResponse) -> Unit) = MessageForwarding(listOf(message(SOURCE)), AUTHOR,
        post = { uuid, target, text -> api.performRequest(ForwardMessageRequest(uuid, target.streamUuid, target.topicUuid, text)) },
        read = { api.performRequest(MessagesByIdsRequest(listOf(it))) }, onConfirmed = onConfirmed, newUuid = { REQUESTED })

    companion object {
        private const val SOURCE = "00000000-0000-0000-0000-000000000001"
        private const val REQUESTED = "00000000-0000-0000-0000-000000000002"
        private const val RETURNED = "00000000-0000-0000-0000-000000000003"
        private const val STREAM = "00000000-0000-0000-0000-000000000004"
        private const val TOPIC = "00000000-0000-0000-0000-000000000005"
        private const val AUTHOR = "00000000-0000-0000-0000-000000000006"
        private fun message(uuid: String) = MessageResponse(uuid, "2026-09-07T12:00:00Z", "2026-09-07T12:00:00Z",
            STREAM, TOPIC, AUTHOR, AUTHOR, MessageResponsePayload("markdown", "CASSI forward fixture"), true, emptyMap(), true)
    }
}

private data class ForwardCapturedRequest(val line: String, val authorization: String?, val body: String)
private class ForwardLoopbackServer(responses: List<Pair<Int, String>>) : AutoCloseable {
    private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = CompletableDeferred<List<ForwardCapturedRequest>>()
    val baseUrl = "http://127.0.0.1:${socket.localPort}"
    init {
        scope.launch {
            try {
                val captured = mutableListOf<ForwardCapturedRequest>()
                responses.forEach { (status, response) ->
                    socket.accept().use { connection ->
                        connection.soTimeout = 5_000
                        val input = connection.getInputStream().buffered()
                        fun line() = buildString {
                            while (true) {
                                val c = input.read()
                                check(c >= 0)
                                if (c == '\n'.code) break
                                if (c != '\r'.code) append(c.toChar())
                                check(length < 8_192)
                            }
                        }
                        val requestLine = line()
                        val headers = buildMap {
                            while (true) {
                                val value = line()
                                if (value.isEmpty()) break
                                put(value.substringBefore(':').lowercase(), value.substringAfter(':').trim())
                            }
                        }
                        val length = headers["content-length"]?.toInt() ?: 0
                        check(length in 0..16_384)
                        val bytes = ByteArray(length)
                        var offset = 0
                        while (offset < length) { val read = input.read(bytes, offset, length - offset); check(read > 0); offset += read }
                        captured += ForwardCapturedRequest(requestLine, headers["authorization"], bytes.toString(Charsets.UTF_8))
                        val body = response.toByteArray(Charsets.UTF_8)
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body); flush()
                        }
                    }
                }
                requests.complete(captured)
            } catch (failure: Throwable) { requests.completeExceptionally(failure) }
        }
    }
    suspend fun awaitRequests() = withTimeout(10_000) { requests.await() }
    override fun close() { socket.close(); scope.cancel() }
}
