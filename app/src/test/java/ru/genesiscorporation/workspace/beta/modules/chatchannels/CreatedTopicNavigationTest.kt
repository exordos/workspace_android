package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CreatedTopicNavigationTest {
    @Test
    fun `created topic opens its channel chat`() {
        val route = CreatedTopicResult(
            uuid = TOPIC_UUID,
            name = "Новая тема",
            streamName = "Разработка Workspace",
            streamUuid = STREAM_UUID,
        ).toChatDialog()

        requireNotNull(route)
        assertEquals("Разработка Workspace", route.title)
        assertEquals(STREAM_UUID, route.chatId)
        assertEquals("Новая тема", route.topicName)
        assertEquals(TOPIC_UUID, route.topicUuid)
        assertFalse(route.isDirectMessages)
        assertNull(route.userId)
    }

    @Test
    fun `created topic route rejects incomplete server identity`() {
        assertNull(
            createdTopic(uuid = "").toChatDialog(),
        )
        assertNull(
            createdTopic(name = " ").toChatDialog(),
        )
        assertNull(
            createdTopic(streamName = "").toChatDialog(),
        )
        assertNull(
            createdTopic(streamUuid = "").toChatDialog(),
        )
    }

    private fun createdTopic(
        uuid: String = TOPIC_UUID,
        name: String = "Новая тема",
        streamUuid: String = STREAM_UUID,
        streamName: String = "Разработка Workspace",
    ) = CreatedTopicResult(
        uuid = uuid,
        name = name,
        streamUuid = streamUuid,
        streamName = streamName,
    )

    private companion object {
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val TOPIC_UUID = "22222222-2222-4222-8222-222222222222"
    }
}
