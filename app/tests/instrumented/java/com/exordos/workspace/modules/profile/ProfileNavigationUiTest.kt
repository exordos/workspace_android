package com.exordos.workspace.modules.profile

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.*
import com.exordos.workspace.data.*
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.ui.theme.WokspaceTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class ProfileNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: ProfileFixtureServer
    private lateinit var scope: CoroutineScope
    private lateinit var files: File
    private lateinit var repository: ServerRepository
    private lateinit var events: EventsRepositoryStore
    private lateinit var user: UserViewModel
    private lateinit var api: WorkspaceAPIClient
    private lateinit var workspace: WorkspaceViewModel
    private lateinit var http: HttpClient
    private lateinit var id: String

    @Before fun prepare() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        server = ProfileFixtureServer()
        files = File(context.cacheDir, "cassi-profile-${UUID.randomUUID()}").apply { mkdirs() }
        val portrait = File(files, "avatar.png").apply { writeBytes(server.portrait) }
        server.user.avatar = "urn:url:${portrait.toURI()}"
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(files, "profile.preferences_pb") }
        http = HttpClient(CIO) { install(ContentNegotiation) { json() } }
        api = WorkspaceAPIClient(http)
        val tokens = SecureTokenStore(context)
        events = EventsRepositoryStore(tokens, api)
        repository = ServerRepository(store, tokens, events, scope)
        user = UserViewModel(repository)
        api.attachUserViewModel(user)
        workspace = WorkspaceViewModel(api, events)
        id = "cassi-profile-${UUID.randomUUID()}"
        repository.addServer(ServerConfig(id, server.url, "", "Exordos Workspace", projectUuid = "project"), TokenPair("fixture-access", "fixture-refresh"))
        repository.setSelectedServerId(id)
        withTimeout(5000) { user.selectedServer.first { it?.id == id } }
        events.getOrCreateForId(id, user.servers.value)!!.updateCurrentUser(server.user)
    }

    @After fun cleanup() = runBlocking {
        repository.removeServer(id)
        events.clear()
        user.viewModelScope.cancel()
        workspace.viewModelScope.cancel()
        scope.cancel()
        http.close()
        server.close()
        files.deleteRecursively()
        Unit
    }

    @Test fun profileDisplaysSelectedProjectName() {
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(UserState provides user) {
                    WorkspaceApp(workspace, api, events, null, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Мой профиль").assertIsDisplayed()
        compose.onNodeWithText("Example User").assertIsDisplayed()
        compose.waitUntil(5000) { user.servers.value.single().name == "Example project" }
        compose.onNodeWithText("Example project").assertIsDisplayed()
    }

    @Test fun avatarFollowsSelectedSessionAndKeepsProfileNavigation() = runBlocking {
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(UserState provides user) {
                    WorkspaceApp(workspace, api, events, null, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.waitUntil(5000) { navigationColor() == Color.rgb(61, 112, 199) }
        val secondId = "$id-second"
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val portrait = File(files, "second-avatar.png")
        portrait.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val config = ServerConfig(secondId, server.url, "", "Second project", projectUuid = "second-project")
            repository.addServer(config, TokenPair("second-access", "second-refresh"))
            events.getOrCreate(config).updateCurrentUser(server.user.copy(firstName = "Second", lastName = "User", avatar = "urn:url:${portrait.toURI()}"))
            compose.waitUntil(5000) { navigationColor() == Color.RED }
            compose.onNodeWithText("Second User").assertIsDisplayed()
            events.getOrCreate(config).updateCurrentUser(server.user.copy(firstName = "Second", lastName = "User", avatar = ""))
            compose.waitUntil(5000) { compose.onAllNodesWithText("S").fetchSemanticsNodes().size == 2 }
            compose.onNodeWithContentDescription("Profile").performClick()
            compose.onNodeWithText("Мой профиль").assertIsDisplayed()
        } finally { repository.removeServer(secondId) }
        Unit
    }

    private fun navigationColor(): Int {
        val pixels = compose.onNodeWithContentDescription("Profile", useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[pixels.width / 4, pixels.height / 2].toArgb()
    }

    @Test fun projectNamesRefreshExistingSessionsAndRejectStaleResponses() = runBlocking {
        val profile = withContext(Dispatchers.Main) { ProfileViewModel(api, user, events) }
        try {
            withTimeout(5000) { user.servers.first { it.single().name == "Example project" } }
            server.close()
            assertEquals("Example project", repository.serversFlow.first().single().name)
            repository.updateServerProjectUuid(id, "second-project", "Second project")
            assertEquals("Second project", repository.serversFlow.first().single().name)
            repository.updateServerProjectName(id, "project", "Stale response")
            assertEquals("Second project", repository.serversFlow.first().single().name)
            assertEquals("second-project", repository.serversFlow.first().single().projectUuid)
        } finally { profile.viewModelScope.cancel() }
    }
}

private class ProfileFixtureServer : AutoCloseable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    val url = "http://127.0.0.1:${socket.localPort}"
    val user = UserResponseData(firstName = "Example", lastName = "User", username = "cassi", uuid = "cassi-fixture", status = "active", avatar = "")
    val portrait = ByteArrayOutputStream().apply {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(61, 112, 199))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 64f; textAlign = Paint.Align.CENTER }
        canvas.drawText("C", 48f, 71f, paint)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
        bitmap.recycle()
    }.toByteArray()
    init {
        executor.submit {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                executor.submit {
                    client.use {
                        val reader = it.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val image = path.contains("/files/avatar/")
                        val body = if (image) portrait else when {
                            path.startsWith("/api/core/v1/iam/projects/") -> """[{"uuid":"project","name":"Example project","description":""}]"""
                            path.contains("/server_settings/") -> """{"email_auth_enabled":true,"realm_name":"Exordos Workspace","realm_url":"$url","realm_icon":"","meet_url":"https://meet.invalid"}"""
                            path.contains("/me/") -> """{"first_name":"Example","last_name":"User","username":"cassi","uuid":"cassi-fixture","status":"active","avatar":"${user.avatar}"}"""
                            else -> "[]"
                        }.toByteArray()
                        val type = if (image) "image/png" else "application/json"
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body); flush()
                        }
                    }
                }
            }
        }
    }
    override fun close() { socket.close(); executor.shutdownNow() }
}
