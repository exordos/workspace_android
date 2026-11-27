package com.exordos.workspace.data

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.ProtectedRequestAuthentication
import com.exordos.workspace.data.remote.dto.FoldersRequest
import com.exordos.workspace.data.remote.dto.MessageReactionsRequest
import com.exordos.workspace.data.remote.dto.MessagesRequest
import com.exordos.workspace.data.remote.dto.ProjectsRequest
import com.exordos.workspace.data.remote.dto.ServerSettingsRequest
import com.exordos.workspace.data.remote.dto.StreamBindingsRequest
import com.exordos.workspace.data.remote.dto.TopicsRequest
import com.exordos.workspace.data.uio.MessageReactionUIO
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.uio.ProjectUIO
import com.exordos.workspace.data.uio.ServerSettingsUIO
import com.exordos.workspace.data.uio.StreamBindingUIO
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.modules.chatdialog.AttachmentStorage
import com.exordos.workspace.modules.chatdialog.ChatDialogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class StartupUioInstrumentedTest {
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private val requests = CopyOnWriteArrayList<LoopbackWorkspaceServer.Request>()
    @Volatile private var bindingStatus = 200
    private val person = """{"uuid":"person","username":"CASSI fixture","status":"active","avatar":""}"""
    private val message = """{"uuid":"message","updated_at":"2026-01-01T00:00:00Z","created_at":"2026-01-01T00:00:00Z",
        "stream_uuid":"stream","topic_uuid":"topic","user_uuid":"person","author_uuid":"person",
        "payload":{"kind":"markdown","content":"Hello"},"is_own":true,"reactions":{},"read":false}"""
    private val stream = """{"uuid":"stream","unread_count":1,"active_unread_count":1,"passive_unread_count":0,
        "updated_at":"2026-01-01T00:00:00Z","name":"Channel","private":false,"color":1,
        "last_message_uuid":"message","notification_mode":"all_messages","direct_user_uuid":"person"}"""
    private val topic = """{"uuid":"topic","stream_uuid":"stream","name":"Topic","color":1,
        "updated_at":"2026-01-01T00:00:00Z","unread_count":1,"active_unread_count":1,
        "is_done":false,"is_default":true,"notification_mode":"follow"}"""
    private val folder = """{"uuid":"folder","title":"Folder","unread_count":1,"system_type":"created",
        "created_at":"2026-01-01T00:00:00Z","folder_items":[{"uuid":"item","folder_uuid":"folder","stream_uuid":"stream","chat_type":"stream","unread_count":1}]}"""
    private val binding = """{"uuid":"binding","stream_uuid":"stream","user_uuid":"person","role":"owner"}"""
    private val reaction = """{"uuid":"reaction","user_uuid":"person","message_uuid":"message","emoji_name":"smile"}"""

    @Before fun setup(): Unit = runBlocking {
        server = LoopbackWorkspaceServer { request ->
            requests += request
            val body = when {
                request.path.endsWith("/server_settings/") -> """{"email_auth_enabled":false,"realm_name":"Realm","realm_url":"","realm_icon":"","meet_url":"https://meet.example.invalid"}"""
                request.path.endsWith("/me/") -> person
                request.path.endsWith("/users/") -> "[$person]"
                request.path.endsWith("/streams/") -> "[$stream]"
                request.path.endsWith("/stream_topics/") -> "[$topic]"
                request.path.endsWith("/messages/") -> "[$message]"
                request.path.endsWith("/folders/") -> "[$folder]"
                request.path.endsWith("/stream_bindings/") -> "[$binding]"
                request.path.endsWith("/message_reactions/") -> "[$reaction]"
                request.path.endsWith("/drafts/") -> """[{"uuid":"draft","stream_uuid":"stream","topic_uuid":"topic",
                    "updated_at":"2026-01-01T00:00:00Z","payload":{"kind":"markdown","content":"Draft"},"revision":1}]"""
                request.path.endsWith("/projects/") -> """[{"uuid":"project","name":"Project","description":"About"}]"""
                else -> "{}"
            }
            if (request.path.endsWith("/stream_bindings/") && bindingStatus != 200)
                LoopbackWorkspaceServer.Response("""{"msg":"Denied","code":"FORBIDDEN"}""", bindingStatus)
            else LoopbackWorkspaceServer.Response(body)
        }
        account = WorkspaceTestAccount.create(server.url)
    }

    @After fun cleanup(): Unit = runBlocking {
        if (::account.isInitialized) account.close()
        if (::server.isInitialized) server.close()
    }

    @Test fun startupPublishesOnlyUiosIncludingResolvedUsersLastMessagesAndDrafts(): Unit = runBlocking {
        val repo = account.repo
        repo.loadServerSettings()
        withTimeout(10_000) { repo.streamsQueryState.first { it == QueryState.Success } }
        assertEquals("CASSI fixture", repo.currentUser.value!!.displayableName())
        assertEquals("person", repo.users.first().single().uuid)
        val loaded = repo.streams.first().single()
        assertEquals("CASSI fixture", repo.users.first().single { it.uuid == loaded.directUserUuid }.displayableName())
        val lastMessage = repo.lastMessageDao.observeLastMessages().first().single { it.uuid == loaded.lastMessageUuid }
        assertEquals("Hello", lastMessage.description())
        assertEquals("person", lastMessage.authorUuid)
        assertEquals("stream", repo.folderItemDao.observeFolderItems("folder").first().single().streamUuid)
        assertEquals("folder", repo.currentlySelectedFolder.value!!.uuid)
        assertEquals("reaction", repo.userReactions.first().single().uuid)
        assertEquals("Draft", repo.drafts.first().single().payload.content)
        assertEquals("https://meet.example.invalid", repo.jitsiServerUrl)
    }

    @Test fun openingAChatRequestsItsLatestPageAndPublishesStoredHistory(): Unit = runBlocking {
        val repo = account.repo
        assertTrue(repo.messageDao.observeMessages("stream", "topic").first().isEmpty())
        val chat = withContext(Dispatchers.Main) {
            ChatDialogViewModel(account.api, account.user, "Channel", "stream", "Topic", "topic",
                false, account.events, null, AttachmentStorage(repo.context, account.api)).also { it.onChatOpened() }
        }
        try {
            val visible = withTimeout(10_000) {
                chat.streamTopicMessages.first { it["stream.topic"].orEmpty().isNotEmpty() }
            }.getValue("stream.topic")
            assertEquals(listOf("Hello"), visible.map { it.payload.content })
            assertEquals(visible, repo.messageDao.observeMessages("stream", "topic").first())
            val request = requests.single { it.path == "/api/workspace/v1/messenger/messages/" }
            assertEquals("GET", request.method)
            assertTrue("stream_uuid=stream" in request.query.split('&'))
            assertTrue("topic_uuid=topic" in request.query.split('&'))
            assertTrue("page_limit=100" in request.query.split('&'))
        } finally {
            withContext(Dispatchers.Main) {
                chat.onChatClosed()
                chat.viewModelScope.cancel()
            }
        }
    }

    @Test fun typedHttpAndPinnedRequestsReturnUios(): Unit = runBlocking {
        val api = account.api
        val topics: ApiResult<List<TopicUIO>, ApiError> = api.performRequest(TopicsRequest(listOf("stream")))
        val messages: ApiResult<List<MessageUIO>, ApiError> = api.performRequest(MessagesRequest("stream", "topic", 25, null))
        val reactions: ApiResult<List<MessageReactionUIO>, ApiError> = api.performRequest(MessageReactionsRequest("person"))
        val projects: ApiResult<List<ProjectUIO>, ApiError> = api.performRequest(ProjectsRequest())
        val settings: ApiResult<ServerSettingsUIO, ApiError> = api.performRequest(ServerSettingsRequest(server.url))
        assertEquals("topic", (topics as ApiResult.Success).value.single().uuid)
        assertEquals("Hello", (messages as ApiResult.Success).value.single().description())
        assertEquals("smile", (reactions as ApiResult.Success).value.single().emojiName)
        assertEquals("Project", (projects as ApiResult.Success).value.single().name)
        assertEquals("Realm", (settings as ApiResult.Success).value.realmName)
        val pinned = ProtectedRequestAuthentication("cassi-pinned-fixture").pin(StreamBindingsRequest("stream"))
        val bindings: ApiResult<List<StreamBindingUIO>, ApiError> = api.performRequest(pinned)
        assertEquals("owner", (bindings as ApiResult.Success).value.single().role)
        assertEquals("Bearer cassi-pinned-fixture", requests.last { it.path.endsWith("/stream_bindings/") }.headers["authorization"])
        bindingStatus = 401
        val count = requests.count { it.path.endsWith("/stream_bindings/") }
        val denied = api.performRequest(pinned) as ApiResult.Error
        assertEquals("FORBIDDEN", denied.error.code)
        assertEquals(401, denied.error.httpStatusCode)
        assertEquals(count + 1, requests.count { it.path.endsWith("/stream_bindings/") })
    }

    @Test fun foldersRequestReadsNestedItemsWithFolderUuid(): Unit = runBlocking {
        val response = account.api.performRequest(FoldersRequest())
        assertTrue(response is ApiResult.Success)
        val snapshot = (response as ApiResult.Success).value.single()
        assertEquals("folder", snapshot.folder.uuid)
        assertEquals("folder", snapshot.items.single().folder)
    }

    @Test fun realtimeMapsEveryEntityBeforeUpdatingPresentationState() = runBlocking {
        val repo = account.repo
        repo.didReceiveUserEvent(person, "created")
        repo.updateCurrentUser(withTimeout(5_000) { repo.users.first { it.isNotEmpty() } }.single())
        repo.didReceiveStreamEvent(stream, "created")
        repo.didReceiveTopicEvent(topic, "created")
        repo.didReceiveMessageEvent(message, "created")
        repo.didReceiveFolderEvent(folder, "created")
        repo.didReceiveStreamBindingEvent("""{"uuid":"stream","items":[$binding]}""", "created")
        repo.didReceiveStreamBindingEvent(binding.replace("owner", "administrator"), "updated")
        repo.didReceiveReactionEvent(reaction, "created")
        assertEquals("CASSI fixture", repo.users.first().single().displayableName())
        assertEquals("Hello", withTimeout(5_000) { repo.lastMessageDao.observeLastMessages().first { it.isNotEmpty() } }.single().description())
        assertEquals("Topic", withTimeout(5_000) { repo.topics.first { it.isNotEmpty() } }.single().name)
        assertEquals("Folder", repo.folders.first().single().title)
        assertEquals("administrator", repo.streamBindingDao.observeStreamBindings("stream").first().single().role)
        assertEquals("smile", repo.userReactions.first().single().emojiName)
    }
}
