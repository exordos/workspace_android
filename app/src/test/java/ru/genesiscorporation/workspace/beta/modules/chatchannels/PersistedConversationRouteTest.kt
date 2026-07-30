package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxEntry
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus
import ru.genesiscorporation.workspace.beta.data.navigation.WorkspaceDeepLinkTarget

class PersistedConversationRouteTest {
    @Test
    fun `exact topic link restores a channel with retained outbox`() {
        val route = resolvePersistedConversationRoute(
            target = WorkspaceDeepLinkTarget.Topic(STREAM, TOPIC),
            state = state(),
        )

        requireNotNull(route)
        assertEquals("Sandbox", route.title)
        assertEquals(STREAM, route.chatId)
        assertEquals("Recovery", route.topicName)
        assertEquals(TOPIC, route.topicUuid)
        assertEquals(false, route.isDirectMessages)
    }

    @Test
    fun `direct conversation never exposes its default topic as a title`() {
        val route = resolvePersistedConversationRoute(
            target = WorkspaceDeepLinkTarget.Topic(STREAM, TOPIC),
            state = state().copy(
                route = state().route?.copy(
                    topicName = "Default",
                    isDirectMessages = true,
                ),
            ),
        )

        requireNotNull(route)
        assertNull(route.topicName)
        assertEquals(true, route.isDirectMessages)
    }

    @Test
    fun `route is rejected without retained work or for another target`() {
        assertNull(
            resolvePersistedConversationRoute(
                target = WorkspaceDeepLinkTarget.Topic(STREAM, TOPIC),
                state = state().copy(outbox = emptyList()),
            ),
        )
        assertNull(
            resolvePersistedConversationRoute(
                target = WorkspaceDeepLinkTarget.Topic("other", TOPIC),
                state = state(),
            ),
        )
        assertNull(
            resolvePersistedConversationRoute(
                target = WorkspaceDeepLinkTarget.Stream(STREAM),
                state = state(),
            ),
        )
        assertNull(
            resolvePersistedConversationRoute(
                target = WorkspaceDeepLinkTarget.Topic(STREAM, TOPIC),
                state = state().copy(
                    outbox = listOf(
                        state().outbox.single().copy(
                            streamUuid = "other-stream",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `channel route requires a nonblank topic name`() {
        assertNull(
            resolvePersistedConversationRoute(
                target = WorkspaceDeepLinkTarget.Topic(STREAM, TOPIC),
                state = state().copy(
                    route = state().route?.copy(topicName = "  "),
                ),
            ),
        )
    }

    private fun state() = PersistedConversationState(
        route = PersistedConversationRoute(
            streamUuid = STREAM,
            topicUuid = TOPIC,
            chatTitle = " Sandbox ",
            topicName = " Recovery ",
            isDirectMessages = false,
        ),
        outbox = listOf(
            PersistedOutboxEntry(
                localMessageUuid = "local-message",
                streamUuid = STREAM,
                topicUuid = TOPIC,
                content = "pending",
                createdAt = "2026-07-30T10:00:00Z",
                status = PersistedOutboxStatus.UNCERTAIN,
            ),
        ),
    )

    private companion object {
        const val STREAM = "11111111-1111-4111-8111-111111111111"
        const val TOPIC = "22222222-2222-4222-8222-222222222222"
    }
}
