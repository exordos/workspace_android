package com.exordos.workspace.modules.streaminfo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.R
import com.exordos.workspace.ChatFlow
import com.exordos.workspace.LocalBottomBarVisible
import com.exordos.workspace.LocalProtectedBottomBarStyle
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.SecureTokenStore
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.ServerRepository
import com.exordos.workspace.data.TokenPair
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.ui.theme.WokspaceTheme
import java.io.Closeable
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real protected routes and metadata HTTP; all account and server data is synthetic. */
@RunWith(AndroidJUnit4::class)
class StreamInfoAvailabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val bottomBarVisible = AtomicBoolean(true)
    private val protectedBottomBarStyle = AtomicReference<Boolean?>(null)

    @Test fun missingRevokedAndRestoredSnapshotsKeepBackAvailableAndCloseKeyPanel() = runBlocking {
        withFixture(initialStream = false) { fixture ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val repo = fixture.repo
            val viewModel = fixture.info
            repo.addStreamTopics(STREAM, listOf(topic(OTHER_TOPIC)))
            show(viewModel)
            compose.onNodeWithText(context.getString(R.string.stream_info_unavailable)).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.stream_info_back)).assertIsDisplayed().assertHasClickAction()
            compose.runOnIdle { assertNull(protectedBottomBarStyle.get()) }

            repo.setInitialStreams(listOf(fixture.peer.stream))
            repo.addStreamTopics(STREAM, listOf(topic(TOPIC)))
            compose.waitUntil(5_000) { viewModel.stream.value != null && viewModel.topic.value != null &&
                viewModel.protectedStreamDetails.value != null && !viewModel.protectedStreamUpdateState.value.busy }
            compose.onNodeWithText("CASSI protected chat").assertIsDisplayed()
            compose.runOnIdle { assertEquals(false, protectedBottomBarStyle.get()) }
            compose.onNodeWithTag("protected-stream-settings").performClick()
            compose.onNodeWithTag("protected-stream-keys-row").performScrollTo().performClick()
            compose.onNodeWithTag("encrypted-chat-keys-screen").assertIsDisplayed()
            compose.runOnIdle { assertEquals(true, protectedBottomBarStyle.get()) }
            compose.onNodeWithText(context.getString(R.string.encrypted_chat_key_screen_title)).assertIsDisplayed()

            repo.setInitialStreams(emptyList())
            repo.addStreamTopics(STREAM, listOf(topic(OTHER_TOPIC)))
            compose.waitUntil(5_000) { viewModel.stream.value == null && viewModel.topic.value == null }
            compose.onNodeWithTag("encrypted-chat-keys-screen").assertDoesNotExist()
            compose.runOnIdle { assertNull(protectedBottomBarStyle.get()) }
            compose.onNodeWithText(context.getString(R.string.stream_info_unavailable)).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.stream_info_back)).assertIsDisplayed().assertHasClickAction()
            repo.setInitialStreams(listOf(fixture.peer.stream))
            compose.waitUntil(5_000) { viewModel.stream.value != null }
            compose.onNodeWithText("CASSI protected chat").assertIsDisplayed()
            compose.onNodeWithTag("protected-stream-info").assertIsDisplayed()
            compose.runOnIdle { assertEquals(false, protectedBottomBarStyle.get()) }
            compose.onNodeWithTag("encrypted-chat-keys-screen").assertDoesNotExist()
        }
    }

    @Test fun ownerNavigatesInfoEditTypeKeysAndSavesOnlyMetadata() = runBlocking {
        withFixture { fixture ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            show(fixture.info)
            ready(fixture.info)
            compose.runOnIdle {
                // A late ordinary-chat dispose cannot release the protected override.
                bottomBarVisible.set(true)
                assertEquals(false, protectedBottomBarStyle.get())
            }
            compose.onNodeWithText(context.getString(R.string.protected_stream_owner)).assertIsDisplayed()
            compose.onNodeWithTag("protected-stream-add-members").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("protected-stream-settings").assertIsEnabled().performClick()
            ready(fixture.info)
            compose.runOnIdle { assertEquals(true, protectedBottomBarStyle.get()) }
            compose.onNodeWithTag("protected-stream-edit").assertIsDisplayed()
            compose.onNodeWithTag("protected-stream-name").assertIsEnabled().assertTextEquals("CASSI protected chat")
            compose.onNodeWithTag("protected-stream-description").assertIsEnabled().assertTextEquals("Synthetic original description")
            compose.onNodeWithTag("protected-stream-delete-row").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("protected-stream-type-row").performClick()
            assertReadOnlyType()
            assertTrue(fixture.peer.mutations.isEmpty())
            compose.onNodeWithTag("protected-stream-advanced").performScrollTo().performClick()
            compose.onNodeWithTag("protected-stream-local-keys").performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.encrypted_chat_no_local_keys)).assertIsDisplayed()
            compose.runOnIdle { assertEquals(true, protectedBottomBarStyle.get()) }
            compose.onNodeWithTag("encrypted-keys-back").performClick()
            compose.onNodeWithTag("protected-stream-type").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.protected_stream_info_back)).performClick()
            compose.onNodeWithTag("protected-stream-edit").assertIsDisplayed()
            compose.onNodeWithTag("protected-stream-keys-row").performClick()
            compose.onNodeWithText(context.getString(R.string.encrypted_chat_key_screen_title)).assertIsDisplayed()
            compose.runOnIdle { assertEquals(true, protectedBottomBarStyle.get()) }
            compose.onNodeWithTag("encrypted-keys-close").performClick()
            compose.onNodeWithTag("protected-stream-edit").assertIsDisplayed()

            compose.onNodeWithTag("protected-stream-name").performScrollTo().performTextReplacement("CASSI updated protected chat")
            compose.onNodeWithTag("protected-stream-description").performTextReplacement("Synthetic updated description")
            compose.onNodeWithTag("protected-stream-save").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(5_000) { fixture.peer.mutations.size == 1 && fixture.repo.streams.value.any {
                it.uuid == STREAM && it.name == "CASSI updated protected chat" && it.description == "Synthetic updated description" } }
            compose.onNodeWithTag("protected-stream-info").assertIsDisplayed()
            compose.onNodeWithText("CASSI updated protected chat").assertIsDisplayed()
            compose.runOnIdle { assertEquals(false, protectedBottomBarStyle.get()) }
            val body = Json.parseToJsonElement(fixture.peer.mutations.single()).jsonObject
            assertEquals(setOf("name", "description"), body.keys)
            assertEquals("CASSI updated protected chat", body.getValue("name").jsonPrimitive.content)
            assertEquals("Synthetic updated description", body.getValue("description").jsonPrimitive.content)
            assertTrue(fixture.peer.stream.encryption)
            assertEquals(CURRENT_KEY, fixture.peer.stream.currentEncryptionKeyUuid)
        }
    }

    @Test fun memberCanInspectTypeAndKeysButCannotEditDeleteOrInvite() = runBlocking {
        withFixture(role = "member") { fixture ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            show(fixture.info)
            ready(fixture.info)
            compose.onNodeWithTag("protected-stream-add-members").assertDoesNotExist()
            compose.onNodeWithTag("protected-stream-settings").performClick()
            ready(fixture.info)
            compose.onNodeWithTag("protected-stream-name").assertIsNotEnabled()
            compose.onNodeWithTag("protected-stream-description").assertIsNotEnabled()
            compose.onNodeWithTag("protected-stream-save").assertDoesNotExist()
            compose.onNodeWithTag("protected-stream-delete-row").assertDoesNotExist()
            compose.onNodeWithTag("protected-stream-type-row").performClick()
            assertReadOnlyType()
            compose.onNodeWithContentDescription(context.getString(R.string.protected_stream_info_back)).performClick()
            compose.onNodeWithTag("protected-stream-keys-row").performClick()
            compose.onNodeWithText(context.getString(R.string.encrypted_chat_key_screen_title)).assertIsDisplayed()
            compose.onNodeWithTag("encrypted-keys-close").performClick()
            compose.onNodeWithTag("protected-stream-members-row").performClick()
            compose.onNodeWithText("CASSI Partner").assertIsDisplayed()
            compose.onNodeWithTag("protected-stream-add-members").assertDoesNotExist()
            assertTrue(fixture.peer.mutations.isEmpty())
            assertEquals("CASSI protected chat", fixture.peer.stream.name)
            assertEquals(CURRENT_KEY, fixture.peer.stream.currentEncryptionKeyUuid)
        }
    }

    @Test fun realtimeRemovalKeepsProtectedDeletionObserverMountedUntil404ReturnsToChatList() = runBlocking {
        withFixture { fixture ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            fixture.peer.beforeDeleteResponse = {
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            try {
                compose.setContent { WokspaceTheme(darkTheme = true, dynamicColor = false) {
                    CompositionLocalProvider(
                        LocalBottomBarVisible provides { bottomBarVisible.set(it) },
                        LocalProtectedBottomBarStyle provides { protectedBottomBarStyle.set(it) },
                    ) {
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = ChatFlow.ChatList) {
                            composable<ChatFlow.ChatList> { Text("Chat list", Modifier.testTag("deleted-return-chat-list")) }
                            composable<ChatFlow.StreamInfo> { StreamInfoView(fixture.info, navController) }
                        }
                        LaunchedEffect(navController) { navController.navigate(ChatFlow.StreamInfo(STREAM, TOPIC)) }
                    }
                } }
                ready(fixture.info)
                compose.onNodeWithTag("protected-stream-settings").performClick()
                ready(fixture.info)
                compose.onNodeWithTag("protected-stream-delete-row").performScrollTo().performClick()
                compose.onNodeWithTag("protected-stream-confirm-delete").performClick()
                compose.waitUntil(5_000) { started.count == 0L }
                fixture.repo.setInitialStreams(emptyList())
                compose.waitUntil(5_000) { fixture.info.stream.value == null }
                compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
                    .getString(R.string.stream_info_unavailable)).assertIsDisplayed()
                assertTrue(fixture.info.hasMountedProtectedStream)
                release.countDown()
                compose.waitUntil(5_000) { fixture.info.protectedStreamDeleteState.value.deleted }
                compose.onNodeWithTag("deleted-return-chat-list").assertIsDisplayed()
                assertEquals(1, fixture.peer.deleteRequests.get())
            } finally { release.countDown() }
        }
    }

    private fun show(info: StreamInfoViewModel) {
        compose.setContent { WokspaceTheme(darkTheme = true, dynamicColor = false) {
            CompositionLocalProvider(
                LocalBottomBarVisible provides { bottomBarVisible.set(it) },
                LocalProtectedBottomBarStyle provides { protectedBottomBarStyle.set(it) },
            ) {
                StreamInfoView(info, rememberNavController())
            }
        } }
    }

    private fun ready(info: StreamInfoViewModel) = compose.waitUntil(5_000) {
        info.protectedStreamDetails.value != null && !info.protectedStreamUpdateState.value.busy
    }

    private fun assertReadOnlyType() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithTag("protected-stream-type").assertIsDisplayed()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onAllNodes(radio).assertCountEquals(4)
        listOf(R.string.protected_stream_type_public, R.string.protected_stream_type_closed_open,
            R.string.protected_stream_type_closed_protected, R.string.protected_stream_type_e2ee).forEach { label ->
            val node = compose.onNode(radio and hasAnyDescendant(hasText(context.getString(label))))
            node.assertIsNotEnabled().assertHasNoClickAction()
            if (label == R.string.protected_stream_type_e2ee) node.assertIsSelected()
        }
    }

    private suspend fun withFixture(role: String = "owner", initialStream: Boolean = true, block: suspend (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val peer = ProtectedInfoPeer(role)
        val directory = File(context.cacheDir, "cassi-stream-info-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "fixture.preferences_pb") }
        val http = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 5_000; connectTimeoutMillis = 2_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = WorkspaceAPIClient(http)
        val tokens = SecureTokenStore(context)
        val events = EventsRepositoryStore(tokens, api)
        val preferences = ServerRepository(dataStore, tokens, events, scope)
        val serverId = "cassi-stream-info-${UUID.randomUUID()}"
        var user: UserViewModel? = null
        var info: StreamInfoViewModel? = null
        try {
            val config = ServerConfig(serverId, peer.url, "", "CASSI test", projectUuid = PROJECT)
            val expectedAccess = "synthetic-access"
            preferences.addServer(config, TokenPair(expectedAccess, "synthetic-refresh"))
            // Start Main-bound state collectors only after the complete account is saved.
            val readyUser = withContext(Dispatchers.Main) { UserViewModel(preferences) }
            user = readyUser
            api.attachUserViewModel(readyUser)
            try {
                withTimeout(5_000) {
                    combine(readyUser.selectedServerId, readyUser.selectedServer, readyUser.accessToken) { id, selected, access ->
                        id == serverId && selected == config && access == expectedAccess
                    }.first { it }
                }
            } catch (timeout: TimeoutCancellationException) {
                throw AssertionError("Fixture account is not coherent; selectedIdMatches=${readyUser.selectedServerId.value == serverId}; " +
                    "selectedConfigMatches=${readyUser.selectedServer.value == config}; " +
                    "accessMatches=${readyUser.accessToken.value == expectedAccess}; " +
                    "storedAccessMatches=${preferences.tokensFor(serverId)?.accessToken == expectedAccess}", timeout)
            }
            val repo = events.getOrCreate(config)
            repo.updateCurrentUser(peer.viewer)
            repo.loadUserInfo()
            assertTrue(repo.verifiedOwnerAuthentication.value != null)
            repo.setInitialUsers(listOf(peer.viewer, peer.partner))
            repo.addStreamBindings(STREAM, peer.bindings)
            repo.addStreamTopics(STREAM, listOf(topic(TOPIC)))
            if (initialStream) repo.setInitialStreams(listOf(peer.stream))
            val viewModel = withContext(Dispatchers.Main) { StreamInfoViewModel(STREAM, TOPIC, api, events) }
            info = viewModel
            block(Fixture(viewModel, repo, peer))
        } finally {
            info?.viewModelScope?.cancel()
            preferences.removeServer(serverId)
            events.clear()
            user?.viewModelScope?.cancel()
            scope.cancel()
            http.close()
            peer.close()
            directory.deleteRecursively()
        }
    }

    private data class Fixture(val info: StreamInfoViewModel, val repo: EventsRepository, val peer: ProtectedInfoPeer)
    private fun topic(uuid: String) = TopicsResponseData(uuid, "Topic", 0, STREAM, TIME, 0, 0,
        false, uuid == TOPIC, notificationMode = "all")

    private class ProtectedInfoPeer(role: String) : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(true)
        private val workers = Executors.newCachedThreadPool()
        val url = "http://127.0.0.1:${socket.localPort}"
        val viewer = UserResponseData(firstName = "CASSI", lastName = "User", username = "cassi", uuid = VIEWER, status = "active", avatar = "")
        val partner = UserResponseData(firstName = "CASSI", lastName = "Partner", username = "cassi-partner", uuid = PARTNER, status = "offline", avatar = "")
        @Volatile var stream = Stream(STREAM, 0, 0, 0, TIME, "CASSI protected chat", true, 0,
            defaultTopicUuid = TOPIC, notificationMode = "all", encryption = true,
            currentEncryptionKeyUuid = CURRENT_KEY, ownerUuid = if (role == "owner") VIEWER else PARTNER,
            projectId = PROJECT, userUuid = VIEWER, role = role, description = "Synthetic original description")
        val bindings = listOf(StreamBindingResponseData(UUID.randomUUID().toString(), role, STREAM, VIEWER),
            StreamBindingResponseData(UUID.randomUUID().toString(), if (role == "owner") "member" else "owner", STREAM, PARTNER))
        val mutations = CopyOnWriteArrayList<String>()
        val deleteRequests = AtomicInteger(0)
        @Volatile var deleted = false
        @Volatile var beforeDeleteResponse: (() -> Unit)? = null
        init {
            workers.execute {
                while (running.get()) {
                    val connection = try { socket.accept() } catch (_: java.net.SocketException) { break }
                    workers.execute { runCatching { connection.use(::handle) } }
                }
            }
        }
        private fun handle(connection: Socket) {
            connection.soTimeout = 5_000
            val input = connection.getInputStream()
            fun line(): String = buildString {
                while (true) { val byte = input.read(); if (byte < 0 || byte == 10) break; if (byte != 13) append(byte.toChar()) }
            }
            val request = line().split(' ')
            val method = request.firstOrNull().orEmpty()
            val path = request.getOrNull(1)?.substringBefore('?').orEmpty()
            var length = 0
            var chunked = false
            var authenticated = false
            while (true) {
                val header = line(); if (header.isEmpty()) break
                if (header.substringBefore(':').equals("Content-Length", true)) length = header.substringAfter(':').trim().toInt()
                if (header.substringBefore(':').equals("Transfer-Encoding", true)) chunked = header.substringAfter(':').trim().equals("chunked", true)
                if (header.substringBefore(':').equals("Authorization", true)) authenticated = header.substringAfter(':').trim() == "Bearer synthetic-access"
            }
            fun readBytes(count: Int): ByteArray {
                val bytes = ByteArray(count)
                var offset = 0
                while (offset < count) { val read = input.read(bytes, offset, count - offset); if (read < 0) break; offset += read }
                return bytes
            }
            val bytes = if (chunked) ByteArrayOutputStream().use { output ->
                while (true) {
                    val count = line().substringBefore(';').toInt(16)
                    if (count == 0) { line(); break }
                    output.write(readBytes(count)); line()
                }
                output.toByteArray()
            } else readBytes(length)
            if (!authenticated) {
                connection.getOutputStream().write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            var status = 200
            val body = when {
                path == "/api/workspace/v1/messenger/me/" -> Json.encodeToString(viewer)
                path == "/api/workspace/v1/messenger/streams/$STREAM" && method == "DELETE" -> {
                    deleteRequests.incrementAndGet()
                    deleted = true
                    beforeDeleteResponse?.invoke()
                    ""
                }
                path == "/api/workspace/v1/messenger/streams/$STREAM" && method == "PUT" -> {
                    val raw = bytes.toString(Charsets.UTF_8)
                    mutations += raw
                    val fields = Json.parseToJsonElement(raw).jsonObject
                    stream = stream.copy(name = fields.getValue("name").jsonPrimitive.content, description = fields.getValue("description").jsonPrimitive.content)
                    Json.encodeToString(stream)
                }
                path == "/api/workspace/v1/messenger/streams/$STREAM" && deleted -> {
                    status = 404
                    "{}"
                }
                path == "/api/workspace/v1/messenger/streams/$STREAM" -> Json.encodeToString(stream)
                else -> "{}"
            }.toByteArray()
            connection.getOutputStream().apply {
                write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(body); flush()
            }
        }
        override fun close() { running.set(false); socket.close(); workers.shutdownNow() }
    }

    companion object {
        private const val STREAM = "939dd3e4-7120-4fba-8a47-9c35968a0795"
        private const val TOPIC = "4e528a0c-ac4d-4579-bae1-51dbbb081c52"
        private const val OTHER_TOPIC = "fda69ee4-d0fd-44cd-a48b-e802339c7af0"
        private const val PROJECT = "a4e8176b-bbd6-41cc-a630-f99f03211f70"
        private const val VIEWER = "831dc945-a43e-409d-82a4-7088bd2fdb8e"
        private const val PARTNER = "78f78061-5022-42ba-8daf-8cd0e2110e0e"
        private const val CURRENT_KEY = "1d49a772-d322-46ab-9733-1dfb96e73bdf"
        private const val TIME = "2026-10-15T10:00:00Z"
    }
}
