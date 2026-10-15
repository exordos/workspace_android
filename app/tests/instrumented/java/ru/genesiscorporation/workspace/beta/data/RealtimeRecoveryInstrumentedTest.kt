package ru.genesiscorporation.workspace.beta.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.AttachmentStorage
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RealtimeRecoveryInstrumentedTest {
    @Test fun close4410ReloadsTheOpenConversationAndResumesReplay() = runBlocking {
        checkRecovery(CursorExpirySignal.CLOSE)
    }

    @Test fun typedErrorReloadsEvenWithoutClose4410() = runBlocking {
        checkRecovery(CursorExpirySignal.FRAME)
    }

    @Test fun failedHistoryReloadRetriesBeforeReconnecting() = runBlocking {
        checkRecovery(CursorExpirySignal.CLOSE, failFirstHistory = true)
    }

    @Test fun HTTP410ProbeAlsoReloadsTheOpenConversation() = runBlocking {
        checkRecovery(CursorExpirySignal.PROBE)
    }

    @Test fun inactive503DoesNotBlockRecoveryAndOpeningTheStaleChatRetries() = runBlocking {
        checkRecovery(CursorExpirySignal.CLOSE, inactiveFailure = true)
    }

    @Test fun noOpenChatReconnectsWithoutReloadingCachedConversations() = runBlocking {
        checkRecovery(CursorExpirySignal.CLOSE, noOpenChat = true)
    }

    @Test fun closingTheOpenChatCancelsItsBlockedHistoryAndReconnects() = runBlocking {
        checkRecovery(CursorExpirySignal.CLOSE, closeDuringHistory = true)
    }

    @Test fun preExpiryHistoryFinishingAfterNoChatRecoveryIsReloadedOnOpen() = runBlocking {
        checkRecovery(CursorExpirySignal.CLOSE, noOpenChat = true, preExpiryHistory = true)
    }

    @Test fun overlappingHistoryCannotResurrectAMessageDeletedBeforeRecovery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(context.cacheDir, "cassi-overlap-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val storeJob = SupervisorJob()
        val storeScope = CoroutineScope(storeJob + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = storeScope) { File(files, "session.preferences_pb") }
        val tokens = SecureTokenStore(context)
        val http = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
            install(WebSockets)
        }
        val api = WorkspaceAPIClient(http)
        val eventsStore = EventsRepositoryStore(tokens, api)
        val sessions = ServerRepository(store, tokens, eventsStore, storeScope)
        val user = UserViewModel(sessions)
        api.attachUserViewModel(user)
        val serverId = "cassi-overlap-${UUID.randomUUID()}"
        try {
            CursorRecoveryServer(CursorExpirySignal.CLOSE, false, overlappingHistory = true).use { server ->
                val config = ServerConfig(serverId, server.baseUrl, "", "Overlapping history test")
                sessions.addServer(config, TokenPair("synthetic-access", "synthetic-refresh"))
                withTimeout(5_000) { user.selectedServer.first { it?.id == serverId } }
                val events = eventsStore.getOrCreate(config)
                val connection = storeScope.launch { events.start() }
                try {
                    withTimeout(5_000) { while (server.socketCursors.isEmpty()) delay(20) }
                    val chat = withContext(Dispatchers.Main) {
                        ChatDialogViewModel(api, user, "Chat", "stream", "Topic", "topic",
                            false, eventsStore, null, AttachmentStorage(context, api)).also { it.onChatOpened() }
                    }
                    val visible = storeScope.launch { chat.streamTopicMessages.collect {} }
                    try {
                        withTimeout(5_000) {
                            while ("live:11" !in server.calls || events.isConversationStale("stream", "topic")) delay(20)
                        }
                        assertEquals(listOf(10, 11), server.socketCursors.toList())
                        assertEquals(2, server.historyRequests)
                        assertTrue(chat.isLoading.value) // Ordinary request A is still held.
                        assertTrue(events.streamTopicMessages.value.getValue("stream.topic").isEmpty())
                        assertTrue(events.messagesPool.value.isEmpty())
                        // B has committed an empty, fresh history. Now finish the obsolete A with [M].
                        server.releaseHistory.countDown()
                        withTimeout(5_000) { chat.isLoading.first { !it } }
                        assertTrue("Obsolete history resurrected M", events.streamTopicMessages.value.getValue("stream.topic").isEmpty())
                        assertTrue(events.messagesPool.value.isEmpty())
                        assertFalse(chat.streamTopicMessages.value["stream.topic"].orEmpty().any { it.uuid == "deleted-before-recovery" })
                        assertFalse(events.isConversationStale("stream", "topic"))
                        server.sendLive("live-after-overlap")
                        withTimeout(5_000) { chat.streamTopicMessages.first { cache ->
                            cache["stream.topic"].orEmpty().any { it.uuid == "live-after-overlap" }
                        } }
                        assertEquals(listOf("live-after-overlap"), events.streamTopicMessages.value.getValue("stream.topic").map { it.uuid })
                        assertFalse(events.messagesPool.value.any { it.uuid == "deleted-before-recovery" })
                        withContext(Dispatchers.Main) { chat.onChatClosed(); chat.onChatOpened() }
                        assertFalse(chat.isLoading.value)
                        assertEquals(2, server.historyRequests)
                    } finally {
                        visible.cancelAndJoin()
                        withContext(Dispatchers.Main) { chat.onChatClosed(); chat.viewModelScope.cancel() }
                    }
                } finally {
                    connection.cancelAndJoin()
                }
            }
        } finally {
            sessions.removeServer(serverId)
            eventsStore.clear()
            user.viewModelScope.cancel()
            storeJob.cancelAndJoin()
            http.close()
            files.deleteRecursively()
        }
    }

    private suspend fun checkRecovery(
        signal: CursorExpirySignal,
        failFirstHistory: Boolean = false,
        inactiveFailure: Boolean = false,
        noOpenChat: Boolean = false,
        closeDuringHistory: Boolean = false,
        preExpiryHistory: Boolean = false,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(context.cacheDir, "cassi-realtime-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val storeJob = SupervisorJob()
        val storeScope = CoroutineScope(storeJob + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = storeScope) { File(files, "session.preferences_pb") }
        val tokens = SecureTokenStore(context)
        val http = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
            install(WebSockets)
        }
        val api = WorkspaceAPIClient(http)
        val eventsStore = EventsRepositoryStore(tokens, api)
        val sessions = ServerRepository(store, tokens, eventsStore, storeScope)
        val user = UserViewModel(sessions)
        api.attachUserViewModel(user)
        val serverId = "cassi-realtime-${UUID.randomUUID()}"
        try {
            CursorRecoveryServer(signal, failFirstHistory, closeDuringHistory, preExpiryHistory).use { server ->
                val config = ServerConfig(serverId, server.baseUrl, "", "Realtime recovery test")
                sessions.addServer(config, TokenPair("synthetic-access", "synthetic-refresh"))
                withTimeout(5_000) { user.selectedServer.first { it?.id == serverId } }
                val events = eventsStore.getOrCreate(config)
                events.addStreamTopicMessages("stream", "topic", listOf(recoveryMessage("baseline")))
                events.addPendingMessage(recoveryMessage("pending-send").copy(isOwn = true))
                if (!preExpiryHistory) {
                    events.addStreamTopicMessages("stream", "inactive", listOf(recoveryMessage("cached-inactive").copy(topicUuid = "inactive")))
                }
                val olderLoad = if (preExpiryHistory) storeScope.async {
                    events.refreshConversation("stream", "inactive")
                } else null
                if (preExpiryHistory) {
                    assertTrue(withContext(Dispatchers.IO) { server.olderHistoryStarted.await(5, TimeUnit.SECONDS) })
                    assertTrue(events.streamTopicMessages.value["stream.inactive"].isNullOrEmpty())
                }
                val owner = Any()
                if (!noOpenChat) {
                    val oldOwner = Any()
                    events.openConversation(oldOwner, "stream", "inactive")
                    events.openConversation(owner, "stream", "topic")
                    // Disposal of an old screen must not clear the new screen's registration.
                    events.closeConversation(oldOwner)
                }
                val connection = storeScope.launch { events.start() }
                try {
                    if (closeDuringHistory) {
                        assertTrue(withContext(Dispatchers.IO) { server.historyStarted.await(5, TimeUnit.SECONDS) })
                        events.closeConversation(owner)
                    }
                    val messages = withTimeout(45_000) {
                        events.streamTopicMessages.first { cache ->
                            cache["stream.topic"].orEmpty().map { it.uuid }.containsAll(listOf("missed", "during-reload"))
                        }.getValue("stream.topic")
                    }
                    assertEquals(setOf("baseline", "missed", "pending-send", "during-reload"), messages.map { it.uuid }.toSet())
                    assertEquals(4, messages.size)
                    assertEquals(listOf(10, 11), server.socketCursors.take(2))
                    val calls = server.calls.toList()
                    if (!noOpenChat && !closeDuringHistory) {
                        assertTrue(calls.indexOf("epoch:11") < calls.indexOf("history:200"))
                        assertTrue(calls.indexOf("history:200") < calls.indexOf("socket:11"))
                        assertFalse(events.isConversationStale("stream", "topic"))
                    } else {
                        assertFalse(calls.contains("history:200"))
                        assertTrue(events.isConversationStale("stream", "topic"))
                    }
                    if (!preExpiryHistory) assertTrue(events.isConversationStale("stream", "inactive"))
                    assertEquals(if (preExpiryHistory) 1 else 0, server.inactiveHistories.get())
                    if (failFirstHistory) assertTrue(calls.indexOf("history:503") < calls.indexOf("history:200"))
                    assertEquals("synthetic-access", sessions.tokensFor(serverId)?.accessToken)
                    if (preExpiryHistory) {
                        // Finish a snapshot captured before expiry only after recovery has reconnected.
                        server.releaseOlderHistory.countDown()
                        val obsolete = withTimeout(5_000) { olderLoad!!.await() }
                        assertTrue(obsolete is ApiResult.Error)
                        assertEquals("HISTORY_RESPONSE_OBSOLETE", (obsolete as ApiResult.Error).error.code)
                        assertTrue(events.streamTopicMessages.value.getValue("stream.inactive").isEmpty())
                        assertTrue(events.isConversationStale("stream", "inactive"))
                        server.failInactive.set(false)
                        val chat = withContext(Dispatchers.Main) {
                            ChatDialogViewModel(api, user, "Inactive", "stream", "Inactive", "inactive",
                                false, eventsStore, null, AttachmentStorage(context, api)).also { it.onChatOpened() }
                        }
                        try {
                            withTimeout(5_000) {
                                while (chat.isLoading.value || events.isConversationStale("stream", "inactive")) delay(20)
                            }
                            assertEquals(listOf("fresh-inactive"), events.streamTopicMessages.value.getValue("stream.inactive").map { it.uuid })
                            assertEquals(2, server.inactiveHistories.get())
                        } finally {
                            withContext(Dispatchers.Main) { chat.onChatClosed(); chat.viewModelScope.cancel() }
                        }
                    }
                    if (inactiveFailure) {
                        val chat = withContext(Dispatchers.Main) {
                            ChatDialogViewModel(api, user, "Inactive", "stream", "Inactive", "inactive",
                                false, eventsStore, null, AttachmentStorage(context, api)).also { it.onChatOpened() }
                        }
                        suspend fun awaitInactiveAttempt(attempt: Int) {
                            withTimeout(5_000) {
                                while (server.inactiveHistories.get() < attempt || chat.isLoading.value) delay(20)
                            }
                        }
                        try {
                            awaitInactiveAttempt(1)
                            assertTrue(events.isConversationStale("stream", "inactive"))
                            server.sendLive("after-inactive-failure")
                            withTimeout(5_000) { events.streamTopicMessages.first { cache ->
                                cache["stream.topic"].orEmpty().any { it.uuid == "after-inactive-failure" }
                            } }
                            withContext(Dispatchers.Main) { chat.onChatClosed(); chat.onChatOpened() }
                            awaitInactiveAttempt(2)
                            assertTrue(events.isConversationStale("stream", "inactive"))
                            server.failInactive.set(false)
                            withContext(Dispatchers.Main) { chat.onChatClosed(); chat.onChatOpened() }
                            withTimeout(5_000) { events.streamTopicMessages.first { cache ->
                                cache["stream.inactive"].orEmpty().any { it.uuid == "fresh-inactive" }
                            } }
                            awaitInactiveAttempt(3)
                            assertFalse(events.isConversationStale("stream", "inactive"))
                            assertEquals(3, server.inactiveHistories.get())
                        } finally {
                            withContext(Dispatchers.Main) { chat.onChatClosed(); chat.viewModelScope.cancel() }
                        }
                    }
                } finally {
                    connection.cancelAndJoin()
                }
            }
        } finally {
            sessions.removeServer(serverId)
            eventsStore.clear()
            user.viewModelScope.cancel()
            storeJob.cancelAndJoin()
            http.close()
            files.deleteRecursively()
        }
    }
}

private enum class CursorExpirySignal { CLOSE, FRAME, PROBE }

private fun recoveryMessage(id: String) = MessageResponse(
    uuid = id, updatedAt = "2026-10-15T12:00:00Z", createdAt = "2026-10-15T12:00:00Z",
    streamUuid = "stream", topicUuid = "topic", userUuid = "reader", authorUuid = "peer",
    payload = MessageResponsePayload("markdown", id), isOwn = false, reactions = emptyMap(), read = false,
)

/** A real loopback HTTP/WebSocket peer; no app hooks, production account or external service. */
private class CursorRecoveryServer(
    private val signal: CursorExpirySignal,
    private val failFirstHistory: Boolean,
    private val blockHistory: Boolean = false,
    private val delayFirstInactiveHistory: Boolean = false,
    private val overlappingHistory: Boolean = false,
) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    private val workers = Executors.newCachedThreadPool()
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val currentEpoch = AtomicInteger(10)
    private val histories = AtomicInteger()
    val historyRequests: Int get() = histories.get()
    val inactiveHistories = AtomicInteger()
    val failInactive = AtomicBoolean(true)
    val historyStarted = CountDownLatch(1)
    val releaseHistory = CountDownLatch(1)
    val olderHistoryStarted = CountDownLatch(1)
    val releaseOlderHistory = CountDownLatch(1)
    private val liveSocket = AtomicReference<Socket?>()
    val socketCursors = CopyOnWriteArrayList<Int>()
    val calls = CopyOnWriteArrayList<String>()
    val baseUrl = "http://127.0.0.1:${server.localPort}"

    init {
        workers.execute {
            while (running.get()) {
                val socket = try { server.accept() } catch (_: java.net.SocketException) { break }
                sockets += socket
                workers.execute {
                    try { socket.use { handle(it) } } catch (_: java.io.IOException) {
                        // Cancellation and server shutdown close fixture connections.
                    } catch (_: InterruptedException) {
                        // Shutdown interrupts workers waiting on the deliberately blocked history.
                        Thread.currentThread().interrupt()
                    } finally { liveSocket.compareAndSet(socket, null); sockets -= socket }
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        val input = DataInputStream(socket.getInputStream())
        fun line(): String = buildString {
            while (true) {
                val byte = input.read()
                if (byte < 0 || byte == 10) break
                if (byte != 13) append(byte.toChar())
            }
        }
        val path = line().split(' ').getOrNull(1) ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = line()
            if (line.isEmpty()) break
            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
        }
        repeat(headers["content-length"]?.toIntOrNull() ?: 0) { input.read() }
        when {
            headers["upgrade"] == "websocket" -> websocket(socket, input, path, headers)
            path.startsWith("/api/workspace/v1/epoch/") -> {
                val epoch = currentEpoch.get()
                calls += "epoch:$epoch"
                respond(socket, 200, """{"epoch_version":$epoch,"epoch_generation":"generation"}""")
            }
            path.startsWith("/api/workspace/v1/messenger/messages/") -> {
                if (overlappingHistory) {
                    val attempt = histories.incrementAndGet()
                    val snapshot = if (attempt == 1) listOf(recoveryMessage("deleted-before-recovery")) else emptyList()
                    if (attempt == 1) {
                        historyStarted.countDown()
                        releaseHistory.await()
                    }
                    respond(socket, 200, Json.encodeToString(snapshot))
                    return
                }
                if (path.contains("topic_uuid=inactive")) {
                    val attempt = inactiveHistories.incrementAndGet()
                    if (delayFirstInactiveHistory && attempt == 1) {
                        val snapshot = Json.encodeToString(listOf(recoveryMessage("before-expiry").copy(topicUuid = "inactive")))
                        olderHistoryStarted.countDown()
                        releaseOlderHistory.await()
                        respond(socket, 200, snapshot)
                        return
                    }
                    if (failInactive.get()) respond(socket, 503, """{"msg":"Inactive chat unavailable","code":"503"}""")
                    else respond(socket, 200, Json.encodeToString(listOf(recoveryMessage("fresh-inactive").copy(topicUuid = "inactive"))))
                    return
                }
                if (blockHistory) {
                    historyStarted.countDown()
                    releaseHistory.await()
                }
                if (histories.getAndIncrement() == 0 && failFirstHistory) {
                    calls += "history:503"
                    respond(socket, 503, """{"msg":"Temporary failure","code":"503"}""")
                } else {
                    val alreadyReplayed = currentEpoch.get() >= 12
                    // Created after the captured cursor, but absent from this history snapshot.
                    currentEpoch.set(12)
                    calls += "history:200"
                    val snapshot = listOf(recoveryMessage("baseline"), recoveryMessage("missed")) +
                        if (alreadyReplayed) listOf(recoveryMessage("during-reload")) else emptyList()
                    respond(socket, 200, Json.encodeToString(snapshot))
                }
            }
            path.startsWith("/api/workspace/v1/events/") -> {
                currentEpoch.set(11)
                respond(socket, 410, """{"code":410,"error":"epoch_pruned","message":"Expired cursor"}""")
            }
            else -> respond(socket, 404, """{"msg":"Not found","code":"404"}""")
        }
    }

    private fun websocket(socket: Socket, input: DataInputStream, path: String, headers: Map<String, String>) {
        val query = URI(path).rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        val cursor = query.getValue("last_epoch_version").toInt()
        val first = socketCursors.isEmpty()
        socketCursors += cursor
        calls += "socket:$cursor"
        val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
            .digest((headers.getValue("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
        socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
            "Connection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n" +
            "Sec-WebSocket-Protocol: workspace.events.v1\r\n\r\n").toByteArray())
        text(socket, """{"type":"ready","epoch_version":$cursor,"epoch_generation":"generation"}""")
        if (first && signal != CursorExpirySignal.PROBE) {
            if (overlappingHistory) historyStarted.await()
            currentEpoch.set(11)
            if (signal == CursorExpirySignal.FRAME) text(socket,
                """{"type":"EventsCursorExpiredError","code":410,"error":"epoch_pruned"}""")
            frame(socket, 8, ByteBuffer.allocate(2).putShort(if (signal == CursorExpirySignal.CLOSE) 4410 else 1000).array())
            return
        }
        if (!first) {
            liveSocket.set(socket)
            calls += "live:$cursor"
            if (!overlappingHistory) {
                currentEpoch.set(13)
                text(socket, """{"object_type":"message","action":"created","epoch_version":12,"payload":${Json.encodeToString(recoveryMessage("during-reload"))}}""")
                // Replaying an event already present in history must not duplicate it.
                text(socket, """{"object_type":"message","action":"created","epoch_version":13,"payload":${Json.encodeToString(recoveryMessage("missed"))}}""")
            }
        }
        while (running.get()) {
            val firstByte = input.read()
            if (firstByte < 0) return
            val secondByte = input.readUnsignedByte()
            val length = when (val size = secondByte and 127) {
                126 -> input.readUnsignedShort()
                127 -> input.readLong().toInt()
                else -> size
            }
            val mask = if (secondByte and 128 != 0) ByteArray(4).also { input.readFully(it) } else null
            val data = ByteArray(length).also { input.readFully(it) }
            if (mask != null) data.indices.forEach { data[it] = (data[it].toInt() xor mask[it % 4].toInt()).toByte() }
            when (firstByte and 15) {
                8 -> { frame(socket, 8, data); return }
                9 -> frame(socket, 10, data)
            }
        }
    }

    private fun text(socket: Socket, text: String) = frame(socket, 1, text.toByteArray())

    fun sendLive(id: String) {
        val socket = checkNotNull(liveSocket.get())
        val epoch = currentEpoch.incrementAndGet()
        text(socket, """{"object_type":"message","action":"created","epoch_version":$epoch,"payload":${Json.encodeToString(recoveryMessage(id))}}""")
    }

    private fun frame(socket: Socket, opcode: Int, bytes: ByteArray) = synchronized(socket) {
        val output = socket.getOutputStream()
        output.write(128 or opcode)
        if (bytes.size < 126) output.write(bytes.size) else {
            output.write(126)
            output.write(ByteBuffer.allocate(2).putShort(bytes.size.toShort()).array())
        }
        output.write(bytes)
        output.flush()
    }

    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray()
        socket.getOutputStream().write(("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes)
    }

    override fun close() {
        running.set(false)
        releaseHistory.countDown()
        releaseOlderHistory.countDown()
        server.close()
        sockets.forEach { it.close() }
        workers.shutdownNow()
    }
}
