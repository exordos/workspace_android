package ru.genesiscorporation.workspace.beta.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.SecureTokenStore
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.ServerRepository
import ru.genesiscorporation.workspace.beta.data.TokenPair
import ru.genesiscorporation.workspace.beta.modules.chatdialog.AttachmentStorage
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogScreen
import ru.genesiscorporation.workspace.beta.LocalBottomBarVisible
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AuthenticationRefreshInstrumentedTest {
    private lateinit var files: File
    private lateinit var storeJob: Job
    private lateinit var repository: ServerRepository
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var eventsStore: EventsRepositoryStore
    private lateinit var user: UserViewModel
    private lateinit var http: HttpClient
    private lateinit var api: WorkspaceAPIClient
    private lateinit var serverId: String
    private var storageAvailable: (File) -> Long = { it.usableSpace }

    @Before
    fun setUp() {
        files = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "auth-refresh-${UUID.randomUUID()}",
        ).apply { check(mkdirs()) }
        storeJob = SupervisorJob()
        val storeScope = CoroutineScope(storeJob + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(files, "auth-refresh.preferences_pb")
        }
        http = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
            }
            install(ContentNegotiation) { json() }
        }
        api = WorkspaceAPIClient(http) { directory -> storageAvailable(directory) }
        tokenStore = SecureTokenStore(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        eventsStore = EventsRepositoryStore(tokenStore, api)
        repository = ServerRepository(store, tokenStore, eventsStore, storeScope)
        user = UserViewModel(repository)
        api.attachUserViewModel(user)
    }

    @After
    fun tearDown() = runBlocking {
        if (::serverId.isInitialized) repository.removeServer(serverId)
        eventsStore.clear()
        http.close()
        user.viewModelScope.cancel()
        storeJob.cancelAndJoin()
        files.deleteRecursively()
        Unit
    }

    @Test
    fun storedAccessTokenIsUsedDirectlyAfterProcessRestart() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "stored-access",
        ).use { server ->
            saveSession(server, "stored-access", "stored-refresh")

            val result = api.performRequest(AuthenticationProbeRequest)

            assertEquals(ApiResult.Success(AuthenticationProbeResponse("ok")), result)
            assertEquals(1, server.protectedRequestCount.get())
            assertEquals(0, server.refreshRequestCount.get())
        }
    }

    @Test
    fun concurrentUnauthorizedRequestsShareOneRotatingRefreshToken() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "fresh-access",
            simultaneousRejectedRequests = 2,
        ).use { server ->
            saveSession(server, "stale-access", "stale-refresh")

            val results = List(2) {
                async(Dispatchers.IO) {
                    api.performRequest(AuthenticationProbeRequest)
                }
            }.awaitAll()

            assertEquals(
                listOf(
                    ApiResult.Success(AuthenticationProbeResponse("ok")),
                    ApiResult.Success(AuthenticationProbeResponse("ok")),
                ),
                results,
            )
            assertEquals(4, server.protectedRequestCount.get())
            assertEquals(1, server.refreshRequestCount.get())
            assertEquals("fresh-access", repository.tokensFor(serverId)?.accessToken)
            assertEquals("fresh-refresh", repository.tokensFor(serverId)?.refreshToken)
        }
    }

    @Test
    fun staleSocketClosureDoesNotRefreshNewLoginTokens() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "new-login-access",
        ).use { server ->
            saveSession(server, "new-login-access", "new-login-refresh")

            val result = api.refreshSession(serverId, "old-socket-access")

            assertEquals(
                ApiResult.Success(TokenPair("new-login-access", "new-login-refresh")),
                result,
            )
            assertEquals(0, server.refreshRequestCount.get())
            assertFalse(requireNotNull(repository.getServer(serverId)).needsToRelogin)
        }
    }

    @Test
    fun directFileTransfersUseTheStoredAccessToken() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "stored-access",
        ).use { server ->
            saveSession(server, "stored-access", "stored-refresh")

            val upload = api.uploadFile<AuthenticationProbeResponse>(
                path = "/upload",
                parts = emptyList(),
            )
            val destination = File(files, "download.txt").apply { writeText("old") }
            val download = api.downloadFile("/download", destination)

            assertEquals(ApiResult.Success(AuthenticationProbeResponse("ok")), upload)
            assertEquals(ApiResult.Success(destination), download)
            assertEquals("download", destination.readText())
            assertEquals(2, server.transferRequestCount.get())
        }
    }

    @Test
    fun oversizedContentLengthIsRejectedBeforeCreatingAPartialFile() = runBlocking {
        val budget = 64 * 1024
        val checks = AtomicInteger()
        storageAvailable = { checks.incrementAndGet(); DOWNLOAD_STORAGE_RESERVE_BYTES + budget }
        AuthenticationTestServer("stored-access", downloadBytes = budget + 1).use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val destination = File(files, "previous.txt").apply { writeText("previous") }
            assertStorageLimit(api.downloadFile("/sized-download", destination), destination)
            assertEquals(1, checks.get())
        }
    }

    @Test
    fun chunkedDownloadIsStoppedAtTheByteBudgetAndPartialFileIsDeleted() = runBlocking {
        val budget = 64 * 1024
        storageAvailable = { DOWNLOAD_STORAGE_RESERVE_BYTES + budget }
        AuthenticationTestServer("stored-access", downloadBytes = budget + 1).use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val destination = File(files, "previous.txt").apply { writeText("previous") }
            assertStorageLimit(api.downloadFile("/chunked-sized-download", destination), destination)
        }
    }

    @Test
    fun downloadExactlyAtTheAvailableBudgetSucceeds() = runBlocking {
        val budget = 64 * 1024
        storageAvailable = { DOWNLOAD_STORAGE_RESERVE_BYTES + budget }
        AuthenticationTestServer("stored-access", downloadBytes = budget).use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val destination = File(files, "complete.bin").apply { writeText("previous") }
            assertTrue(api.downloadFile("/sized-download", destination) is ApiResult.Success)
            assertEquals(budget.toLong(), destination.length())
            assertTrue(destination.readBytes().all { it == 65.toByte() })
            assertFalse(files.listFiles().orEmpty().any { it.name.endsWith(".part") })
        }
    }

    @Test
    fun downloadDoesNotConsumeTheFreeSpaceReserve() = runBlocking {
        storageAvailable = { DOWNLOAD_STORAGE_RESERVE_BYTES - 1 }
        AuthenticationTestServer("stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val destination = File(files, "previous.txt").apply { writeText("previous") }
            assertStorageLimit(api.downloadFile("/download", destination), destination)
        }
    }

    @Test
    fun storageConsumedAfterTheInitialCheckStopsTheNextWrite() = runBlocking {
        val checks = AtomicInteger()
        storageAvailable = {
            DOWNLOAD_STORAGE_RESERVE_BYTES + if (checks.incrementAndGet() == 1) 64 * 1024 else 3
        }
        AuthenticationTestServer("stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val destination = File(files, "previous.txt").apply { writeText("previous") }
            assertStorageLimit(api.downloadFile("/download", destination), destination)
            assertEquals(2, checks.get())
        }
    }

    private fun assertStorageLimit(result: ApiResult<File, ApiError>, destination: File) {
        assertTrue(result is ApiResult.Error)
        assertEquals("INSUFFICIENT_STORAGE", (result as ApiResult.Error).error.code)
        assertEquals("previous", destination.readText())
        assertFalse(files.listFiles().orEmpty().any { it.name.endsWith(".part") })
    }

    @Test
    fun excessivelyLongDownloadNameReturnsErrorWithoutCrashingOrLeavingPartialFile() = runBlocking {
        AuthenticationTestServer(acceptedAccessToken = "stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val destination = File(files, "a".repeat(300) + ".pdf")
            // This is the same filesystem failure reported by Play for version 29.
            try {
                destination.writeText("legacy download")
                throw AssertionError("Expected the legacy filename to exceed NAME_MAX")
            } catch (failure: java.io.FileNotFoundException) {
                assertTrue(failure.message.orEmpty().contains("ENAMETOOLONG"))
            }
            val result = api.downloadFile("/download", destination)
            assertTrue(result is ApiResult.Error)
            assertEquals("DOWNLOAD_FAILED", (result as ApiResult.Error).error.code)
            assertFalse(files.listFiles().orEmpty().any { it.name.endsWith(".part") })
        }
    }

    @Test
    fun attachmentStorageSavesLongUnicodeNameAndPreservesTheVisibleOriginal() = runBlocking {
        AuthenticationTestServer(acceptedAccessToken = "stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val storage = AttachmentStorage(InstrumentationRegistry.getInstrumentation().targetContext, api)
            val id = UUID.randomUUID().toString()
            val name = "Длинное_имя_вложения_".repeat(30) + ".pdf"
            val destination = storage.localFile(id, name)
            try {
                val result = storage.loadOrDownload(id, name)
                assertTrue(result is ApiResult.Success)
                val attachment = (result as ApiResult.Success).value
                assertEquals(name, attachment.fileName)
                assertTrue(destination.name.toByteArray(Charsets.UTF_8).size <= 255)
                assertTrue(destination.name.endsWith(".pdf"))
                assertEquals("download", destination.readText())
                assertTrue(storage.isCached(id, name))
            } finally {
                destination.delete()
            }
        }
    }

    @Test
    fun failedDownloadPreservesAnExistingDestinationAndCancellationPropagates() = runBlocking {
        AuthenticationTestServer(acceptedAccessToken = "stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val directory = File(files, "existing").apply { check(mkdirs()) }
            val existing = File(directory, "keep.txt").apply { writeText("keep") }
            assertTrue(api.downloadFile("/download", directory) is ApiResult.Error)
            assertEquals("keep", existing.readText())
            assertFalse(files.listFiles().orEmpty().any { it.name.endsWith(".part") })
            val cancelled = async(Dispatchers.IO) {
                api.downloadFile("/slow-download", File(files, "cancelled.txt"))
            }
            assertTrue(server.slowTransferStarted.await(5, TimeUnit.SECONDS))
            cancelled.cancel()
            server.finishSlowTransfer.countDown()
            try {
                cancelled.await()
                throw AssertionError("Cancellation was swallowed")
            } catch (_: kotlinx.coroutines.CancellationException) {
                assertFalse(File(files, "cancelled.txt").exists())
            }
        }
    }

    @Test
    fun interruptedDownloadDoesNotReplaceThePreviousCompleteFile() = runBlocking {
        AuthenticationTestServer(acceptedAccessToken = "stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val destination = File(files, "complete.txt").apply { writeText("previous download") }
            assertTrue(api.downloadFile("/truncated-download", destination) is ApiResult.Error)
            assertEquals("previous download", destination.readText())
            assertFalse(files.listFiles().orEmpty().any { it.name.endsWith(".part") })
        }
    }

    @Test
    fun chatOpenedBeforeStreamsArriveAndRemovedStreamDoNotCrash() = runBlocking {
        AuthenticationTestServer(acceptedAccessToken = "stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val chat = withContext(Dispatchers.Main) {
                ChatDialogViewModel(api, user, "Fixture", "stream", "Topic", "topic",
                    false, eventsStore, null, AttachmentStorage(context, api))
            }
            try {
                assertEquals(null, chat.stream.value)
                val stream = Stream(uuid = "stream", unreadCount = 0, activeUnreadCount = 0,
                    passiveUnreadCount = 0, updatedAt = "2026-10-07T12:00:00Z", name = "Fixture",
                    isPrivate = false, color = 0, lastMessageUuid = null, notificationMode = "all")
                val loaded = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    withTimeout(5_000) { chat.stream.first { it?.uuid == stream.uuid } }
                }
                chat.repo.setInitialStreams(listOf(stream))
                assertEquals(stream, loaded.await())
                val removed = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    withTimeout(5_000) { chat.stream.first { it == null } }
                }
                chat.repo.setInitialStreams(emptyList())
                assertEquals(null, removed.await())
            } finally {
                chat.viewModelScope.cancel()
            }
        }
    }

    @Test
    fun realChatScreenCanMeasureAndScrollARepeatedServerMessageKey() = runBlocking {
        AuthenticationTestServer(acceptedAccessToken = "stored-access").use { server ->
            saveSession(server, "stored-access", "stored-refresh")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val chat = withContext(Dispatchers.Main) {
                ChatDialogViewModel(api, user, "Fixture", "stream", "Topic", "topic",
                    false, eventsStore, null, AttachmentStorage(context, api))
            }
            val message = MessageResponse("00000000-0000-0000-0000-000000000001",
                "2026-10-07T12:00:00Z", "2026-10-07T12:00:00Z", "stream", "topic", "author", "author",
                MessageResponsePayload("markdown", "CASSI vitals regression"), false, emptyMap(), true)
            chat.repo.addStreamTopicMessages("stream", "topic", listOf(message, message.copy()))
            val activity = instrumentation.startActivitySync(requireNotNull(
                context.packageManager.getLaunchIntentForPackage(context.packageName))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
            try {
                instrumentation.runOnMainSync {
                    activity.setContent {
                        WokspaceTheme(dynamicColor = false) {
                            CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                                ChatDialogScreen(chat, rememberNavController())
                            }
                        }
                    }
                }
                withTimeout(10_000) {
                    while (instrumentation.uiAutomation.rootInActiveWindow
                            ?.findAccessibilityNodeInfosByText("CASSI vitals regression").orEmpty().isEmpty()) {
                        kotlinx.coroutines.delay(100)
                    }
                }
                // Exercise a later list update and the production auto-scroll effect.
                chat.repo.addStreamTopicMessages("stream", "topic", listOf(message,
                    message.copy(uuid = "00000000-0000-0000-0000-000000000002",
                        payload = MessageResponsePayload("markdown", "CASSI next message"))))
                withTimeout(10_000) {
                    while (instrumentation.uiAutomation.rootInActiveWindow
                            ?.findAccessibilityNodeInfosByText("CASSI next message").orEmpty().isEmpty()) {
                        kotlinx.coroutines.delay(100)
                    }
                }
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                chat.viewModelScope.cancel()
            }
        }
    }

    @Test
    fun staleRefreshResultCannotReplaceNewLoginTokens() {
        val testServerId = "auth-cas-${UUID.randomUUID()}"
        val newLoginTokens = TokenPair("new-login-access", "new-login-refresh")
        try {
            tokenStore.save(
                testServerId,
                TokenPair("expired-access", "refresh-used-by-request"),
            )
            tokenStore.save(testServerId, newLoginTokens)

            val saved = tokenStore.saveIfRefreshTokenMatches(
                serverId = testServerId,
                expectedRefreshToken = "refresh-used-by-request",
                tokens = TokenPair("stale-refreshed-access", "stale-rotated-refresh"),
            )

            assertFalse(saved)
            assertEquals(newLoginTokens, tokenStore.get(testServerId))
        } finally {
            tokenStore.clear(testServerId)
        }
    }

    @Test
    fun rejectedStaleRefreshCannotMarkNewLoginForRelogin() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "new-login-access",
        ).use { server ->
            saveSession(server, "expired-access", "refresh-used-by-request")
            assertTrue(
                user.setTokensAndWait(
                    accessToken = "new-login-access",
                    refreshToken = "new-login-refresh",
                    serverId = serverId,
                ),
            )

            val marked = repository.setNeedsToReloginIfRefreshTokenCurrent(
                serverId = serverId,
                expectedRefreshToken = "refresh-used-by-request",
            )

            assertFalse(marked)
            assertFalse(requireNotNull(repository.getServer(serverId)).needsToRelogin)
            assertEquals("new-login-refresh", repository.tokensFor(serverId)?.refreshToken)
        }
    }

    @Test
    fun staleRetriedRequestCannotMarkNewLoginForRelogin() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "new-login-access",
        ).use { server ->
            saveSession(server, "expired-access", "refresh-used-by-request")
            val retriedTokens = TokenPair("refreshed-access", "rotated-refresh")
            assertTrue(
                repository.saveRefreshedTokensIfCurrent(
                    serverId = serverId,
                    expectedRefreshToken = "refresh-used-by-request",
                    tokens = retriedTokens,
                ),
            )
            assertTrue(
                user.setTokensAndWait(
                    accessToken = "new-login-access",
                    refreshToken = "new-login-refresh",
                    serverId = serverId,
                ),
            )

            val marked = repository.setNeedsToReloginIfTokensCurrent(
                serverId = serverId,
                expectedTokens = retriedTokens,
            )

            assertFalse(marked)
            assertFalse(requireNotNull(repository.getServer(serverId)).needsToRelogin)
            assertEquals("new-login-refresh", repository.tokensFor(serverId)?.refreshToken)
        }
    }

    private suspend fun saveSession(
        server: AuthenticationTestServer,
        accessToken: String,
        refreshToken: String,
    ) {
        serverId = "auth-refresh-${UUID.randomUUID()}"
        repository.addServer(
            ServerConfig(
                id = serverId,
                baseUrl = server.baseUrl,
                imageUrl = "",
                name = "Authentication test",
                needsToRelogin = false,
            ),
            TokenPair(accessToken, refreshToken),
        )
        withTimeout(2_000) {
            user.selectedServer.first { it?.id == serverId }
        }
    }
}

private object AuthenticationProbeRequest :
    ApiRequest<EmptyRequestData, AuthenticationProbeResponse, ApiError> {
    override val url = "/protected"
    override val method = HTTPMethod.GET
    override val data = EmptyRequestData()
}

@Serializable
private data class AuthenticationProbeResponse(val value: String)

private class AuthenticationTestServer(
    private val acceptedAccessToken: String,
    simultaneousRejectedRequests: Int = 0,
    private val downloadBytes: Int = 8,
) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    private val workers = Executors.newCachedThreadPool()
    private val rejectedRequests = CountDownLatch(simultaneousRejectedRequests)
    val protectedRequestCount = AtomicInteger()
    val refreshRequestCount = AtomicInteger()
    val transferRequestCount = AtomicInteger()
    val slowTransferStarted = CountDownLatch(1)
    val finishSlowTransfer = CountDownLatch(1)
    val baseUrl = "http://127.0.0.1:${server.localPort}"

    init {
        workers.execute {
            while (running.get()) {
                try {
                    val socket = server.accept()
                    workers.execute {
                        try { handle(socket) } catch (_: java.net.SocketException) {
                            // Clients may cancel a request during fixture teardown.
                        } catch (interrupted: InterruptedException) {
                            // shutdownNow interrupts handlers waiting on fixture latches.
                            if (running.get()) throw interrupted
                            Thread.currentThread().interrupt()
                        }
                    }
                } catch (_: Exception) {
                    if (running.get()) throw AssertionError("Authentication test server failed")
                }
            }
        }
    }

    private fun handle(socket: Socket): Unit = socket.use {
        val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }
        repeat(headers["content-length"]?.toIntOrNull() ?: 0) { reader.read() }

        when {
            requestLine.startsWith("GET /protected ") -> {
                protectedRequestCount.incrementAndGet()
                if (headers["authorization"] == "Bearer $acceptedAccessToken") {
                    respond(it, 200, "OK", """{"value":"ok"}""")
                } else {
                    rejectedRequests.countDown()
                    check(rejectedRequests.await(5, TimeUnit.SECONDS))
                    respond(
                        it,
                        401,
                        "Unauthorized",
                        """{"msg":"Unauthorized","code":"401"}""",
                    )
                }
            }
            requestLine.startsWith(
                "POST /api/core/v1/iam/clients/default/actions/get_token/invoke ",
            ) -> {
                refreshRequestCount.incrementAndGet()
                respond(
                    it,
                    200,
                    "OK",
                    """{"access_token":"fresh-access","refresh_token":"fresh-refresh"}""",
                )
            }
            requestLine.startsWith("POST /upload ") -> {
                transferRequestCount.incrementAndGet()
                if (headers["authorization"] == "Bearer $acceptedAccessToken") {
                    respond(it, 200, "OK", """{"value":"ok"}""")
                } else {
                    respond(it, 401, "Unauthorized", "{}")
                }
            }
            requestLine.startsWith("GET /sized-download ") ||
                requestLine.startsWith("GET /chunked-sized-download ") -> {
                val output = it.getOutputStream()
                val chunked = requestLine.startsWith("GET /chunked-sized-download ")
                val framing = if (chunked) "Transfer-Encoding: chunked" else "Content-Length: $downloadBytes"
                output.write("HTTP/1.1 200 OK\r\n$framing\r\nConnection: close\r\n\r\n".toByteArray())
                var sent = 0
                while (sent < downloadBytes) {
                    val count = minOf(4096, downloadBytes - sent)
                    if (chunked) output.write("${count.toString(16)}\r\n".toByteArray())
                    output.write(ByteArray(count) { 65 })
                    if (chunked) output.write("\r\n".toByteArray())
                    output.flush()
                    sent += count
                }
                if (chunked) output.write("0\r\n\r\n".toByteArray())
                output.flush()
            }
            requestLine.startsWith("GET /truncated-download ") -> {
                it.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\ndown".toByteArray())
                    flush()
                }
            }
            requestLine.startsWith("GET /slow-download ") -> {
                val output = it.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Length: 8\r\nConnection: close\r\n\r\ndown".toByteArray())
                output.flush()
                slowTransferStarted.countDown()
                if (finishSlowTransfer.await(5, TimeUnit.SECONDS)) {
                    runCatching { output.write("load".toByteArray()); output.flush() }
                }
            }
            requestLine.startsWith("GET /download ") ||
                requestLine.matches(Regex("GET /api/workspace/v1/messenger/files/[^/]+/actions/download HTTP/.*")) -> {
                transferRequestCount.incrementAndGet()
                if (headers["authorization"] == "Bearer $acceptedAccessToken") {
                    respond(it, 200, "OK", "download")
                } else {
                    respond(it, 401, "Unauthorized", "{}")
                }
            }
            else -> respond(it, 404, "Not Found", "{}")
        }
    }

    private fun respond(
        socket: Socket,
        status: Int,
        reason: String,
        body: String,
    ) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        socket.getOutputStream().apply {
            write(headers)
            write(bodyBytes)
            flush()
        }
    }

    override fun close() {
        running.set(false)
        server.close()
        workers.shutdownNow()
        check(workers.awaitTermination(5, TimeUnit.SECONDS))
    }
}
