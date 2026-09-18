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
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.SecureTokenStore
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.ServerRepository
import ru.genesiscorporation.workspace.beta.data.TokenPair
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

    @Before
    fun setUp() {
        files = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "cassi-auth-refresh-${UUID.randomUUID()}",
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
        api = WorkspaceAPIClient(http)
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
            refreshStatus = 200,
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
            refreshStatus = 200,
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
    fun transientRefreshFailurePreservesTheSession() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "fresh-access",
            refreshStatus = 500,
        ).use { server ->
            saveSession(server, "stale-access", "stale-refresh")

            val result = api.performRequest(AuthenticationProbeRequest)

            assertEquals("500", (result as ApiResult.Error).error.code)
            assertEquals("stale-access", repository.tokensFor(serverId)?.accessToken)
            assertEquals("stale-refresh", repository.tokensFor(serverId)?.refreshToken)
            assertEquals(server.baseUrl, repository.getServer(serverId)?.baseUrl)
        }
    }

    @Test
    fun genericUnauthorizedRefreshResponsePreservesTheSession() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "fresh-access",
            refreshStatus = 401,
        ).use { server ->
            saveSession(server, "stale-access", "stale-refresh")

            val result = api.performRequest(AuthenticationProbeRequest)

            assertEquals("401", (result as ApiResult.Error).error.code)
            assertEquals("stale-access", repository.tokensFor(serverId)?.accessToken)
            assertEquals("stale-refresh", repository.tokensFor(serverId)?.refreshToken)
            assertEquals(server.baseUrl, repository.getServer(serverId)?.baseUrl)
        }
    }

    @Test
    fun explicitInvalidGrantClearsTheSession() = runBlocking {
        AuthenticationTestServer(
            acceptedAccessToken = "fresh-access",
            refreshStatus = 400,
        ).use { server ->
            saveSession(server, "stale-access", "stale-refresh")

            val result = api.performRequest(AuthenticationProbeRequest)

            assertEquals("400", (result as ApiResult.Error).error.code)
            assertNull(repository.tokensFor(serverId))
            assertEquals(server.baseUrl, repository.getServer(serverId)?.baseUrl)
        }
    }

    private suspend fun saveSession(
        server: AuthenticationTestServer,
        accessToken: String,
        refreshToken: String,
    ) {
        serverId = "cassi-auth-refresh-${UUID.randomUUID()}"
        repository.addServer(
            ServerConfig(
                id = serverId,
                baseUrl = server.baseUrl,
                imageUrl = "",
                name = "Authentication test",
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
    private val refreshStatus: Int,
    simultaneousRejectedRequests: Int = 0,
) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    private val workers = Executors.newCachedThreadPool()
    private val rejectedRequests = CountDownLatch(simultaneousRejectedRequests)
    val protectedRequestCount = AtomicInteger()
    val refreshRequestCount = AtomicInteger()
    val baseUrl = "http://127.0.0.1:${server.localPort}"

    init {
        workers.execute {
            while (running.get()) {
                try {
                    val socket = server.accept()
                    workers.execute { handle(socket) }
                } catch (_: Exception) {
                    if (running.get()) throw AssertionError("Authentication test server failed")
                }
            }
        }
    }

    private fun handle(socket: Socket) = socket.use {
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
                when (refreshStatus) {
                    200 -> respond(
                        it,
                        200,
                        "OK",
                        """{"access_token":"fresh-access","refresh_token":"fresh-refresh"}""",
                    )
                    401 -> respond(
                        it,
                        401,
                        "Unauthorized",
                        """{"msg":"Unauthorized","code":"401"}""",
                    )
                    400 -> respond(
                        it,
                        400,
                        "Bad Request",
                        """{"error":"invalid_grant","error_description":"Refresh token expired"}""",
                    )
                    else -> respond(
                        it,
                        refreshStatus,
                        "Server Error",
                        """{"msg":"Temporary failure","code":"$refreshStatus"}""",
                    )
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
