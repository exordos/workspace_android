package ru.genesiscorporation.workspace.beta.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount

class PushNavigationRequestTest {
    @Test
    fun `stream payload maps to canonical provider chat key`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "stream_chat_message",
                "realm_url" to "https://WORKSPACE.example/",
                "workspace_message_id" to "901",
                "stream_id" to "42",
                "topic" to "Release",
            ),
        )

        assertEquals("channel:42", request?.providerChatKey)
        assertEquals("https://workspace.example", request?.realmUrl)
        assertEquals("Release", request?.topicName)
        assertEquals(901, request?.workspaceMessageId)
    }

    @Test
    fun `legacy misspelled stream id remains compatible`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "stream_chat_message",
                "realm_url" to "https://workspace.example",
                "workspace_message_id" to "2",
                "steram_id" to "9",
                "topic" to "General",
            ),
        )

        assertEquals("channel:9", request?.providerChatKey)
    }

    @Test
    fun `private participant ids are normalized and sorted`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "private_chat_message",
                "realm_url" to "https://workspace.example",
                "workspace_message_id" to "5",
                "sender_id" to "20",
                "user_id" to "10",
            ),
        )

        assertEquals("direct:10,20", request?.providerChatKey)
        assertNull(request?.topicName)
    }

    @Test
    fun `group participant ids are de-duplicated`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "group_chat_message",
                "realm_url" to "https://workspace.example",
                "workspace_message_id" to "6",
                "sender_id" to "3",
                "pm_users" to "1,2,3",
            ),
        )

        assertEquals("group_direct:1,2,3", request?.providerChatKey)
    }

    @Test
    fun `malformed and incomplete payloads are rejected`() {
        assertNull(
            PushNavigationRequest.fromMessageData(
                mapOf(
                    "kind" to "stream_chat_message",
                    "realm_url" to "https://workspace.example",
                    "workspace_message_id" to "1",
                    "stream_id" to "42",
                ),
            ),
        )
        assertNull(
            PushNavigationRequest.fromMessageData(
                mapOf(
                    "kind" to "private_chat_message",
                    "realm_url" to "https://workspace.example",
                    "workspace_message_id" to "1",
                    "sender_id" to "10",
                    "user_id" to "10",
                ),
            ),
        )
        assertNull(
            PushNavigationRequest.fromIntentFields(
                realmUrl = "https://workspace.example",
                providerChatKey = "channel:42/../../other",
                topicName = "General",
                workspaceMessageId = 1,
            ),
        )
    }

    @Test
    fun `missing or unsafe realm is rejected before navigation`() {
        val otherwiseValid = mapOf(
            "kind" to "private_chat_message",
            "workspace_message_id" to "5",
            "sender_id" to "20",
            "user_id" to "10",
        )

        assertNull(PushNavigationRequest.fromMessageData(otherwiseValid))
        assertNull(
            PushNavigationRequest.fromMessageData(
                otherwiseValid + ("realm_url" to "http://workspace.example"),
            ),
        )
        assertNull(
            PushNavigationRequest.fromMessageData(
                otherwiseValid +
                    ("realm_url" to "https://user@workspace.example/path"),
            ),
        )
        assertNull(
            PushNavigationRequest.fromMessageData(
                otherwiseValid +
                    ("realm_url" to "https://workspace.example/not-a-realm"),
            ),
        )
        assertNull(
            PushNavigationRequest.fromMessageData(
                otherwiseValid +
                    ("realm_url" to "https://workspace.example/?next=other"),
            ),
        )
    }

    @Test
    fun `notification identity is realm scoped and conversation grouped`() {
        val first = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "stream_chat_message",
                "realm_url" to "https://one.example",
                "workspace_message_id" to "901",
                "stream_id" to "42",
                "topic" to "Release",
            ),
        ) ?: error("Request rejected")
        val secondRealm = first.copy(realmUrl = "https://two.example")
        val secondMessage = first.copy(workspaceMessageId = 902)
        val secondTopic = first.copy(topicName = "General")

        assertTrue(first.notificationId() != secondRealm.notificationId())
        assertTrue(first.notificationId() != secondMessage.notificationId())
        assertEquals(
            first.notificationId(),
            PushNavigationRequest.notificationId(
                first.realmUrl,
                first.workspaceMessageId,
            ),
        )
        assertTrue(
            first.notificationGroupKey() != secondTopic.notificationGroupKey(),
        )
    }

    @Test
    fun `account routing never guesses between same realm accounts`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "private_chat_message",
                "realm_url" to "https://workspace.example",
                "workspace_message_id" to "5",
                "sender_id" to "20",
                "user_id" to "10",
            ),
        ) ?: error("Request rejected")
        val first = account("first", "https://workspace.example/")
        val second = account("second", "https://WORKSPACE.example")
        val unrelated = account("other", "https://other.example")

        assertEquals(
            first,
            resolvePushAccountTarget(request, listOf(first, unrelated), null),
        )
        assertNull(
            resolvePushAccountTarget(
                request,
                listOf(first, second, unrelated),
                null,
            ),
        )
        assertEquals(
            second,
            resolvePushAccountTarget(
                request,
                listOf(first, second, unrelated),
                second.accountId,
            ),
        )
        assertNull(
            resolvePushAccountTarget(
                request,
                listOf(first, second),
                unrelated.accountId,
            ),
        )
    }

    private fun account(
        accountId: String,
        baseUrl: String,
    ) = WorkspaceAccount(
        accountId = accountId,
        baseUrl = baseUrl,
        projectId = "$accountId-project",
        projectName = accountId,
        userId = "$accountId-user",
        login = accountId,
    )
}
