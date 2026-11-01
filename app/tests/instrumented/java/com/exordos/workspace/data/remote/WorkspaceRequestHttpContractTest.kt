package com.exordos.workspace.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.BuildConfig
import com.exordos.workspace.data.TokenPair
import com.exordos.workspace.data.remote.dto.*
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise the production Ktor transport; fixtures never use a live Workspace account. */
@RunWith(AndroidJUnit4::class)
class WorkspaceRequestHttpContractTest {
    @Test
    fun streamCreationUsesSupportedFieldsWithTheProductionJsonConfiguration() = runBlocking {
        val streamUuid = "00000000-0000-4000-8000-000000000001"
        val keyUuid = "00000000-0000-4000-8000-000000000002"
        val peerUuid = "00000000-0000-4000-8000-000000000003"
        val response = """{
            "uuid":"$streamUuid","name":"CASSI contract","updated_at":"2026-01-01T00:00:00Z",
            "unread_count":0,"active_unread_count":0,"passive_unread_count":0,
            "private":false,"color":0,"notification_mode":"all_messages"
        }"""
        // The deployed stream schema accepts source_name, not the legacy nested source.
        val supportedFields = setOf("uuid", "name", "description", "source_name", "invite_only",
            "announce", "direct_user_uuid", "private", "color", "encryption", "current_encryption_key_uuid")
        val cases = listOf(
            AddStreamRequest("CASSI contract", "", null) to
                """{"name":"CASSI contract","description":"","direct_user_uuid":null,"source_name":"native","encryption":false}""",
            AddStreamRequest("CASSI contract", "", null, true, keyUuid, streamUuid) to
                """{"name":"CASSI contract","description":"","direct_user_uuid":null,"source_name":"native","encryption":true,"current_encryption_key_uuid":"$keyUuid","uuid":"$streamUuid"}""",
            AddStreamRequest("CASSI contract", "", peerUuid) to
                """{"name":"CASSI contract","description":"","direct_user_uuid":"$peerUuid","source_name":"native","encryption":false}""",
        )
        withPeer(response, respond = { request ->
            val unknownFields = Json.parseToJsonElement(request.body).jsonObject.keys - supportedFields
            if (unknownFields.isEmpty()) LoopbackWorkspaceServer.Response(response)
            else LoopbackWorkspaceServer.Response("""{"msg":"Unsupported stream properties","code":"VALIDATION_ERROR"}""", 400)
        }) { account, requests ->
            for ((creation, expected) in cases) {
                val result = account.api.performRequest(creation)
                assertTrue("Stream creation failed: $result", result is ApiResult.Success)
                val request = requests.receive()
                assertProtected(request)
                assertEquals("POST", request.method)
                assertEquals("/api/workspace/v1/messenger/streams/", request.path)
                assertJson(request, expected)
            }
        }
    }

    @Test
    fun loginSendsOtpOnlyAsAHeaderAndNeverUsesTheSavedBearer() = runBlocking {
        withPeer("""{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","future_field":true}""") { account, requests ->
            val result = account.api.performRequest(LoginRequest("cassi+fixture@example.invalid", "synthetic \"пароль\"", "123456"))

            assertEquals(ApiResult.Success(LoginResponse("synthetic-access", "synthetic-refresh")), result)
            val request = requests.receive()
            assertEquals("POST", request.method)
            assertEquals("/api/core/v1/iam/clients/default/actions/get_token/invoke", request.path)
            assertNull(request.headers["authorization"])
            assertEquals("123456", request.headers["x-otp"])
            assertJson(request, """{
                "login":"cassi+fixture@example.invalid","password":"synthetic \"пароль\"",
                "grant_type":"login+password","scope":"openid email profile","ttl":"3600","refresh_ttl":"31536000"
            }""")
            assertFalse(Json.parseToJsonElement(request.body).jsonObject.containsKey("otp"))
        }
    }

    @Test
    fun refreshHasNoBearerAndRetainsItsRequestedProjectScope() = runBlocking {
        withPeer("""{"access_token":"synthetic-new","refresh_token":"synthetic-rotated"}""") { account, requests ->
            val result = account.api.performRequest(TokenRefreshRequest("synthetic-old", "openid project:fixture"))

            assertEquals(ApiResult.Success(LoginResponse("synthetic-new", "synthetic-rotated")), result)
            val request = requests.receive()
            assertNull(request.headers["authorization"])
            assertNull(request.headers["x-otp"])
            assertJson(request, """{"grant_type":"refresh_token","refresh_token":"synthetic-old","scope":"openid project:fixture"}""")
        }
    }

    @Test
    fun rejectedLoginDoesNotRefreshOrInvalidateTheAlreadySavedSession() = runBlocking {
        withPeer("""{"msg":"Invalid login","code":"INVALID_CREDENTIALS"}""", 401) { account, requests ->
            val before = account.preferences.tokensFor(account.config.id)
            val result = account.api.performRequest(LoginRequest("other-fixture", "synthetic-rejected"))

            val request = requests.receive()
            assertNull(request.headers["authorization"])
            assertNull(request.headers["x-otp"])
            assertTrue(requests.tryReceive().isFailure)
            assertEquals(before, account.preferences.tokensFor(account.config.id))
            assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
            assertEquals(ApiResult.Error(ApiError("Invalid login", "INVALID_CREDENTIALS", 401)), result)
        }
    }

    @Test
    fun rejectedRefreshDoesNotRecurseOrInvalidateTheAlreadySavedSession() = runBlocking {
        withPeer("""{"msg":"Refresh rejected","code":"INVALID_GRANT"}""", 401) { account, requests ->
            val before = account.preferences.tokensFor(account.config.id)
            val result = account.api.performRequest(TokenRefreshRequest("synthetic-rejected", "openid"))

            val request = requests.receive()
            assertNull(request.headers["authorization"])
            assertJson(request, """{"grant_type":"refresh_token","refresh_token":"synthetic-rejected","scope":"openid"}""")
            assertTrue(requests.tryReceive().isFailure)
            assertEquals(before, account.preferences.tokensFor(account.config.id))
            assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
            assertEquals(ApiResult.Error(ApiError("Refresh rejected", "INVALID_GRANT", 401)), result)
        }
    }

    @Test
    fun structuredErrorAfterRefreshPreservesBackendFieldsAndTheRotatedSession() = runBlocking {
        val conflict = """{"msg":"Draft changed","code":"REVISION_CONFLICT","revision":10}"""
        withPeer(conflict, respond = { request ->
            when (request.headers["authorization"]) {
                null -> LoopbackWorkspaceServer.Response("""{"access_token":"synthetic-new","refresh_token":"synthetic-rotated"}""")
                "Bearer synthetic-new" -> LoopbackWorkspaceServer.Response(conflict, 412)
                else -> LoopbackWorkspaceServer.Response("""{"msg":"Expired","code":"SESSION_EXPIRED"}""", 401)
            }
        }) { account, requests ->
            val result = account.api.performRequest(UpdateDraftRequest("draft", "Updated", 8))

            assertEquals(ApiResult.Error(ApiError("Draft changed", "REVISION_CONFLICT", 412)), result)
            assertProtected(requests.receive())
            val refresh = requests.receive()
            assertNull(refresh.headers["authorization"])
            assertJson(refresh, """{"grant_type":"refresh_token","refresh_token":"cassi-fixture-refresh","scope":null}""")
            val retry = requests.receive()
            assertEquals("Bearer synthetic-new", retry.headers["authorization"])
            assertEquals("\"8\"", retry.headers["if-match"])
            assertTrue(requests.tryReceive().isFailure)
            assertEquals(TokenPair("synthetic-new", "synthetic-rotated"), account.preferences.tokensFor(account.config.id))
            assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
        }
    }

    @Test
    fun rejectedRefreshCannotInvalidateAReplacementSession() = runBlocking {
        val finishRefresh = CountDownLatch(1)
        withPeer("{}", respond = { request ->
            if (request.headers["authorization"] == null) {
                check(finishRefresh.await(10, TimeUnit.SECONDS))
                LoopbackWorkspaceServer.Response("""{"msg":"Refresh rejected","code":"INVALID_GRANT"}""", 400)
            } else LoopbackWorkspaceServer.Response("""{"msg":"Expired","code":"SESSION_EXPIRED"}""", 401)
        }) { account, requests ->
            val pending = async(Dispatchers.IO) { account.api.performRequest(MessagesRequest("stream", "topic")) }
            val replacement = TokenPair("synthetic-replacement", "synthetic-replacement-refresh")
            try {
                assertProtected(requests.receive())
                assertNull(requests.receive().headers["authorization"])
                account.preferences.saveTokensAndClearRelogin(account.config.id, replacement)
            } finally { finishRefresh.countDown() }

            assertEquals(ApiResult.Error(ApiError("Refresh rejected", "INVALID_GRANT", 400)), pending.await())
            assertTrue(requests.tryReceive().isFailure)
            assertEquals(replacement, account.preferences.tokensFor(account.config.id))
            assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
        }
    }

    @Test
    fun temporaryRefreshFailurePreservesTheSavedSession() = runBlocking {
        withPeer("{}", respond = { request ->
            if (request.headers["authorization"] == null)
                LoopbackWorkspaceServer.Response("""{"msg":"Try later","code":"SERVICE_UNAVAILABLE"}""", 503)
            else LoopbackWorkspaceServer.Response("""{"msg":"Expired","code":"SESSION_EXPIRED"}""", 401)
        }) { account, requests ->
            val before = account.preferences.tokensFor(account.config.id)
            val result = account.api.performRequest(MessagesRequest("stream", "topic"))

            assertEquals(ApiResult.Error(ApiError("Try later", "SERVICE_UNAVAILABLE", 503)), result)
            assertProtected(requests.receive())
            assertNull(requests.receive().headers["authorization"])
            assertTrue(requests.tryReceive().isFailure)
            assertEquals(before, account.preferences.tokensFor(account.config.id))
            assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
        }
    }

    @Test
    fun draftCreationRoundTripsUnicodeContentAndTheServerRevision() = runBlocking {
        withPeer(draftResponse) { account, requests ->
            val result = account.api.performRequest(CreateDraftRequest("draft", "stream", "topic", "Текст\nвторая строка"))

            assertTrue(result is ApiResult.Success)
            assertEquals(9, (result as ApiResult.Success).value.revision)
            val request = requests.receive()
            assertProtected(request)
            assertEquals("POST", request.method)
            assertEquals("/api/workspace/v1/messenger/drafts/", request.path)
            assertJson(request, """{"uuid":"draft","stream_uuid":"stream","topic_uuid":"topic","payload":{"kind":"markdown","content":"Текст\nвторая строка"}}""")
        }
    }

    @Test
    fun draftUpdateAndDeleteTransmitQuotedIfMatchWithoutLosingTheSessionHeader() = runBlocking {
        withPeer(draftResponse) { account, requests ->
            assertTrue(account.api.performRequest(UpdateDraftRequest("draft", "Updated", 8)) is ApiResult.Success)
            val update = requests.receive()
            assertProtected(update)
            assertEquals("POST", update.method)
            assertEquals("\"8\"", update.headers["if-match"])
            assertJson(update, """{"payload":{"kind":"markdown","content":"Updated"}}""")

            // Empty response DTOs accept object responses with additional fields.
            assertTrue(account.api.performRequest(DeleteDraftRequest("draft", 9)) is ApiResult.Success)
            val delete = requests.receive()
            assertProtected(delete)
            assertEquals("DELETE", delete.method)
            assertEquals(update.path, delete.path)
            assertEquals("\"9\"", delete.headers["if-match"])
            assertEquals("", delete.body)
        }
    }

    @Test
    fun staleDraftRevisionReturnsConflictWithoutRetryingOrChangingCredentials() = runBlocking {
        withPeer("""{"msg":"Draft changed","code":"REVISION_CONFLICT","revision":10}""", 412) { account, requests ->
            val before = account.preferences.tokensFor(account.config.id)
            val result = account.api.performRequest(UpdateDraftRequest("draft", "Updated", 8))

            assertEquals("\"8\"", requests.receive().headers["if-match"])
            assertTrue(requests.tryReceive().isFailure)
            assertEquals(before, account.preferences.tokensFor(account.config.id))
            assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
            assertEquals(ApiResult.Error(ApiError("Draft changed", "REVISION_CONFLICT", 412)), result)
        }
    }

    @Test
    fun readActionsKeepSingleMessageReadUpToTopicAndStreamBoundariesSeparate() = runBlocking {
        withPeer("{}") { account, requests ->
            assertTrue(account.api.performRequest(MarkMessagesReadRequest("message")) is ApiResult.Success)
            assertRead(requests.receive(), "messages/message/actions/read/invoke")
            assertTrue(account.api.performRequest(MarkMessagesReadUpToRequest("message")) is ApiResult.Success)
            assertRead(requests.receive(), "messages/message/actions/read_up_to/invoke")
            assertTrue(account.api.performRequest(MarkStreamMessagesReadRequest("stream")) is ApiResult.Success)
            assertRead(requests.receive(), "streams/stream/actions/read/invoke")
            assertTrue(account.api.performRequest(MarkTopicMessagesReadRequest("topic")) is ApiResult.Success)
            assertRead(requests.receive(), "stream_topics/topic/actions/read/invoke")
        }
    }

    @Test
    fun notificationOverridesAndDoneUseTheirOwnActionsAndWireFields() = runBlocking {
        withPeer("{}") { account, requests ->
            assertTrue(account.api.performRequest(UpdateStreamNotificationModeRequest("stream", "mentions_only")) is ApiResult.Success)
            val stream = requests.receive()
            assertRead(stream, "streams/stream/actions/notifications/invoke", emptyBody = false)
            assertJson(stream, """{"notification_mode":"mentions_only"}""")
            assertTrue(account.api.performRequest(UpdateTopicNotificationModeRequest("topic", "follow")) is ApiResult.Success)
            val topic = requests.receive()
            assertRead(topic, "stream_topics/topic/actions/notifications/invoke", emptyBody = false)
            assertJson(topic, """{"notification_mode":"follow"}""")
            assertTrue(account.api.performRequest(ToggleTopicDoneRequest("topic")) is ApiResult.Success)
            assertRead(requests.receive(), "stream_topics/topic/actions/toggle_done/invoke")
        }
    }

    @Test
    fun mismatchedServerIdAndConfigurationNeverReachTheTransport() = runBlocking {
        withPeer("[]") { account, requests ->
            val result = account.api.performRequest(MessagesRequest("stream", "topic"),
                serverId = "cassi-another-account", serverConfig = account.config)

            assertTrue(result is ApiResult.Error)
            assertEquals("SERVER_CHANGED", (result as ApiResult.Error).error.code)
            assertTrue(requests.tryReceive().isFailure)
        }
    }

    @Test
    fun plainServerErrorsPreserveStatusAndDoNotTriggerRefresh() = runBlocking {
        withPeer("temporarily unavailable", 503) { account, requests ->
            val result = account.api.performRequest(MessagesRequest("stream", "topic"))

            assertEquals(ApiResult.Error(ApiError("temporarily unavailable", "503", 503)), result)
            assertEquals("GET", requests.receive().method)
            assertTrue(requests.tryReceive().isFailure)
            assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
        }
    }

    @Test
    fun malformedAndIncompleteHttpErrorsKeepTheRawBodyAndSavedSession() = runBlocking {
        for (body in listOf("", "{", "{}", "null", "[]", """{"msg":"Missing code"}""")) {
            withPeer(body, 502) { account, requests ->
                val before = account.preferences.tokensFor(account.config.id)
                val result = account.api.performRequest(MessagesRequest("stream", "topic"))

                assertEquals(ApiResult.Error(ApiError(body, "502", 502)), result)
                assertProtected(requests.receive())
                assertTrue(requests.tryReceive().isFailure)
                assertEquals(before, account.preferences.tokensFor(account.config.id))
                assertFalse(requireNotNull(account.preferences.getServer(account.config.id)).needsToRelogin)
            }
        }
    }

    private suspend fun withPeer(
        body: String,
        status: Int = 200,
        respond: (LoopbackWorkspaceServer.Request) -> LoopbackWorkspaceServer.Response = {
            LoopbackWorkspaceServer.Response(body, status)
        },
        block: suspend (WorkspaceTestAccount, Channel<LoopbackWorkspaceServer.Request>) -> Unit,
    ) = withTimeout(15_000) {
        val requests = Channel<LoopbackWorkspaceServer.Request>(Channel.UNLIMITED)
        LoopbackWorkspaceServer { request ->
            check(requests.trySend(request).isSuccess)
            respond(request)
        }.use { server ->
            val account = WorkspaceTestAccount.create(server.url)
            try { block(account, requests) } finally { account.close(); requests.close() }
        }
    }

    private fun assertProtected(request: LoopbackWorkspaceServer.Request) {
        assertEquals("Bearer cassi-fixture-0", request.headers["authorization"])
        assertEquals("Workspace/android/${BuildConfig.VERSION_NAME}", request.headers["user-agent"])
    }

    private fun assertRead(request: LoopbackWorkspaceServer.Request, path: String, emptyBody: Boolean = true) {
        assertProtected(request)
        assertEquals("POST", request.method)
        assertEquals("/api/workspace/v1/messenger/$path", request.path)
        if (emptyBody) assertEquals("", request.body)
    }

    private fun assertJson(request: LoopbackWorkspaceServer.Request, body: String) {
        assertTrue(request.headers["content-type"].orEmpty().startsWith("application/json"))
        assertEquals(Json.parseToJsonElement(body), Json.parseToJsonElement(request.body))
    }

    private val draftResponse = """{
        "uuid":"draft","stream_uuid":"stream","topic_uuid":"topic",
        "updated_at":"2026-01-01T00:00:00Z","revision":9,
        "payload":{"kind":"markdown","content":"Текст\nвторая строка"}
    }"""
}
