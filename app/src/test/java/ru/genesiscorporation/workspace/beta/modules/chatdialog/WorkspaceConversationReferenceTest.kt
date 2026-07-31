package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class WorkspaceConversationReferenceTest {
    @Test
    fun `canonical user and message references match the desktop contract`() {
        assertEquals(
            WorkspaceEntityReference.UserReference(USER_UUID),
            parseWorkspaceEntityReferenceUrn("urn:user:$USER_UUID"),
        )
        assertEquals(
            WorkspaceEntityReference.MessageReference(MESSAGE_UUID),
            parseWorkspaceEntityReferenceUrn(
                "  URN:MESSAGE:${MESSAGE_UUID.uppercase()}  ",
            ),
        )
    }

    @Test
    fun `malformed user and message references fail closed`() {
        listOf(
            "",
            "urn:user:",
            "urn:user:not-a-uuid",
            "urn:user:1-1-1-1-1",
            "urn:user:$USER_UUID:extra",
            "urn:message:",
            "urn:message:$MESSAGE_UUID?query=1",
            "urn:message:urn:message:$MESSAGE_UUID",
            "urn:stream:$STREAM_UUID",
        ).forEach { value ->
            assertNull(value, parseWorkspaceEntityReferenceUrn(value))
        }
    }

    @Test
    fun `canonical stream and topic references match the desktop contract`() {
        assertEquals(
            WorkspaceConversationReference.StreamReference(STREAM_UUID),
            parseWorkspaceConversationReferenceUrn(
                "urn:stream:$STREAM_UUID",
            ),
        )
        assertEquals(
            WorkspaceConversationReference.TopicReference(TOPIC_UUID),
            parseWorkspaceConversationReferenceUrn(
                "  URN:TOPIC:${TOPIC_UUID.uppercase()}  ",
            ),
        )
        assertEquals(
            WorkspaceConversationReference.TopicReference(
                topicUuid = TOPIC_UUID,
                streamUuid = STREAM_UUID,
            ),
            parseWorkspaceConversationReferenceUrn(
                "urn:topic:$STREAM_UUID:$TOPIC_UUID",
            ),
        )
    }

    @Test
    fun `malformed ambiguous and nested references fail closed`() {
        listOf(
            "",
            "urn:stream:",
            "urn:stream:not-a-uuid",
            "urn:stream:1-1-1-1-1",
            "urn:stream:$STREAM_UUID:extra",
            "urn:stream:$STREAM_UUID?query=1",
            "urn:topic:",
            "urn:topic:$TOPIC_UUID:extra",
            "urn:topic:$STREAM_UUID:$TOPIC_UUID:extra",
            "urn:topic:urn:topic:$TOPIC_UUID",
            "urn:url:https://example.com",
        ).forEach { value ->
            assertNull(value, parseWorkspaceConversationReferenceUrn(value))
        }
    }

    @Test
    fun `stream selection requires one active exact match`() {
        val match = stream(STREAM_UUID, "Sandbox")

        assertEquals(
            match,
            selectWorkspaceReferenceStream(
                STREAM_UUID,
                listOf(
                    stream(OTHER_STREAM_UUID, "Other"),
                    match,
                ),
            ),
        )
        assertNull(
            selectWorkspaceReferenceStream(
                STREAM_UUID,
                listOf(match, match.copy(name = "Duplicate")),
            ),
        )
        assertNull(
            selectWorkspaceReferenceStream(
                STREAM_UUID,
                listOf(match.copy(isArchived = true)),
            ),
        )
    }

    @Test
    fun `topic selection is unique and honors optional stream scope`() {
        val match = topic(TOPIC_UUID, STREAM_UUID, "E2E")
        val sameUuidInOtherStream =
            topic(TOPIC_UUID, OTHER_STREAM_UUID, "Other")

        assertEquals(
            match,
            selectWorkspaceReferenceTopic(
                WorkspaceConversationReference.TopicReference(
                    topicUuid = TOPIC_UUID,
                    streamUuid = STREAM_UUID,
                ),
                listOf(match, sameUuidInOtherStream),
            ),
        )
        assertNull(
            selectWorkspaceReferenceTopic(
                WorkspaceConversationReference.TopicReference(TOPIC_UUID),
                listOf(match, sameUuidInOtherStream),
            ),
        )
        assertNull(
            selectWorkspaceReferenceTopic(
                WorkspaceConversationReference.TopicReference(
                    topicUuid = TOPIC_UUID,
                    streamUuid = OTHER_STREAM_UUID,
                ),
                listOf(match),
            ),
        )
    }

    @Test
    fun `user selection is unique and builds an exact profile route`() {
        val match = user(USER_UUID)

        assertEquals(
            match,
            selectWorkspaceReferenceUser(
                USER_UUID,
                listOf(match, user(OTHER_USER_UUID)),
            ),
        )
        assertNull(
            selectWorkspaceReferenceUser(
                USER_UUID,
                listOf(match, match.copy(username = "duplicate")),
            ),
        )
        assertEquals(
            ChatFlow.ChatUserInfo(
                userName = "Cassandra Volkova",
                userId = USER_UUID,
                avatarUrl = "urn:avatar:test",
                email = "cassi@example.test",
            ),
            buildOpenWorkspaceUserRoute(match),
        )
    }

    @Test
    fun `regular stream opens topic list and exact topic opens dialog`() {
        val stream = stream(STREAM_UUID, "Sandbox")
        val topic = topic(TOPIC_UUID, STREAM_UUID, "E2E")

        val streamEvent = buildOpenWorkspaceConversationEvent(
            stream = stream,
            topic = null,
        )
        val topicEvent = buildOpenWorkspaceConversationEvent(
            stream = stream,
            topic = topic,
        )

        assertEquals(
            OpenWorkspaceConversationEvent.TopicList(
                ChatFlow.ChatTopic(
                    channelName = "Sandbox",
                    channelId = STREAM_UUID,
                ),
            ),
            streamEvent,
        )
        assertEquals(
            OpenWorkspaceConversationEvent.Dialog(
                ChatFlow.ChatDialog(
                    title = "Sandbox",
                    chatId = STREAM_UUID,
                    topicName = "E2E",
                    topicUuid = TOPIC_UUID,
                    isDirectMessages = false,
                    userId = null,
                ),
            ),
            topicEvent,
        )
    }

    @Test
    fun `direct default topic prefers metadata and recovers from stale metadata`() {
        val preferred = topic(TOPIC_UUID, STREAM_UUID, "Preferred")
        val fallback = topic(
            uuid = OTHER_TOPIC_UUID,
            streamUuid = STREAM_UUID,
            name = "Fallback",
            isDefault = true,
        )
        val stream = stream(
            uuid = STREAM_UUID,
            name = "Direct",
            directUserUuid = USER_UUID,
            defaultTopicUuid = TOPIC_UUID,
        )

        assertEquals(
            preferred,
            selectWorkspaceReferenceDefaultTopic(
                stream,
                listOf(fallback, preferred),
            ),
        )
        assertEquals(
            fallback,
            selectWorkspaceReferenceDefaultTopic(
                stream.copy(defaultTopicUuid = MISSING_TOPIC_UUID),
                listOf(fallback),
            ),
        )
        assertNull(
            selectWorkspaceReferenceDefaultTopic(
                stream.copy(defaultTopicUuid = null),
                listOf(
                    fallback,
                    fallback.copy(uuid = MISSING_TOPIC_UUID),
                ),
            ),
        )
    }

    @Test
    fun `direct references require a real topic and hide its technical name`() {
        val direct = stream(
            uuid = STREAM_UUID,
            name = "Direct",
            directUserUuid = USER_UUID,
        )
        val topic = topic(TOPIC_UUID, STREAM_UUID, "Default")

        assertNull(
            buildOpenWorkspaceConversationEvent(
                stream = direct,
                topic = null,
            ),
        )
        val event = buildOpenWorkspaceConversationEvent(
            stream = direct,
            topic = topic,
        )
        assertTrue(event is OpenWorkspaceConversationEvent.Dialog)
        val route = (event as OpenWorkspaceConversationEvent.Dialog).route
        assertNull(route.topicName)
        assertTrue(route.isDirectMessages)
        assertNull(
            buildOpenWorkspaceConversationEvent(
                stream = direct,
                topic = topic.copy(streamUuid = OTHER_STREAM_UUID),
            ),
        )
    }

    @Test
    fun `message response must match and creates an exact focused route`() {
        val reference =
            WorkspaceEntityReference.MessageReference(MESSAGE_UUID)
        val message = message()

        assertEquals(
            message,
            validateWorkspaceReferenceMessage(reference, message),
        )
        assertNull(
            validateWorkspaceReferenceMessage(
                reference,
                message.copy(uuid = MISSING_TOPIC_UUID),
            ),
        )
        assertNull(
            validateWorkspaceReferenceMessage(
                reference,
                message.copy(streamUuid = "not-a-uuid"),
            ),
        )
        val event = buildOpenWorkspaceConversationEvent(
            stream = stream(STREAM_UUID, "Sandbox"),
            topic = topic(TOPIC_UUID, STREAM_UUID, "E2E"),
            focusMessageUuid = MESSAGE_UUID,
        ) as OpenWorkspaceConversationEvent.Dialog
        assertEquals(MESSAGE_UUID, event.route.focusMessageUuid)
        assertEquals(STREAM_UUID, event.route.chatId)
        assertEquals(TOPIC_UUID, event.route.topicUuid)
    }

    private fun stream(
        uuid: String,
        name: String,
        isArchived: Boolean = false,
        directUserUuid: String? = null,
        defaultTopicUuid: String? = null,
    ) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = UPDATED_AT,
        name = name,
        isPrivate = directUserUuid != null,
        isArchived = isArchived,
        directUserUuid = directUserUuid,
        defaultTopicUuid = defaultTopicUuid,
    )

    private fun topic(
        uuid: String,
        streamUuid: String,
        name: String,
        isDefault: Boolean = false,
    ) = TopicsResponseData(
        uuid = uuid,
        name = name,
        streamUuid = streamUuid,
        updatedAt = UPDATED_AT,
        unreadCount = 0,
        isDone = false,
        isDefault = isDefault,
    )

    private fun user(uuid: String) = UserResponseData(
        email = "cassi@example.test",
        firstName = "Cassandra",
        lastName = "Volkova",
        username = "cassi",
        uuid = uuid,
        status = "active",
        avatar = "urn:avatar:test",
    )

    private fun message() = MessageResponse(
        uuid = MESSAGE_UUID,
        updatedAt = UPDATED_AT,
        createdAt = UPDATED_AT,
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = USER_UUID,
        authorUuid = USER_UUID,
        payload = MessageResponsePayload(
            kind = "markdown",
            content = "Reference target",
        ),
        isOwn = true,
        reactions = emptyMap(),
    )

    private companion object {
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val TOPIC_UUID = "22222222-2222-4222-8222-222222222222"
        const val OTHER_STREAM_UUID =
            "33333333-3333-4333-8333-333333333333"
        const val USER_UUID = "44444444-4444-4444-8444-444444444444"
        const val OTHER_TOPIC_UUID =
            "55555555-5555-4555-8555-555555555555"
        const val MISSING_TOPIC_UUID =
            "66666666-6666-4666-8666-666666666666"
        const val MESSAGE_UUID =
            "77777777-7777-4777-8777-777777777777"
        const val OTHER_USER_UUID =
            "88888888-8888-4888-8888-888888888888"
        const val UPDATED_AT = "2026-07-31T00:00:00Z"
    }
}
