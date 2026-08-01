package ru.genesiscorporation.workspace.beta.modules.chatchannels

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class MessengerUiFormattingTest {

    @Test
    fun `parseTime accepts API offset timestamps`() {
        assertEquals(
            Instant.parse("2026-07-26T10:15:30Z"),
            parseTime("2026-07-26T13:15:30+03:00"),
        )
    }

    @Test
    fun `parseTime safely handles malformed timestamps`() {
        assertEquals(Instant.EPOCH, parseTime("not-a-timestamp"))
        assertEquals(Instant.EPOCH, parseTime(null))
    }

    @Test
    fun `messagePreview removes markdown decoration and keeps first content line`() {
        assertEquals(
            "Привет, команда",
            messagePreview("**Привет, команда**\nвторая строка"),
        )
    }

    @Test
    fun `messagePreview replaces image attachment markup`() {
        val preview = messagePreview("[photo.png](urn:image:1234)")
        assertTrue(preview == "Вложение" || preview == "photo.png")
    }

    @Test
    fun `stream title includes the latest topic only for channel rows`() {
        assertEquals(
            "Команда  # Общий чат",
            streamTitle("Команда", " Общий чат ", isDirect = false),
        )
        assertEquals(
            "Анна",
            streamTitle("Анна", "Личные сообщения", isDirect = true),
        )
        assertEquals(
            "Команда",
            streamTitle("Команда", null, isDirect = false),
        )
    }

    @Test
    fun `topic panel settles at the nearest side`() {
        assertTrue(
            topicPanelShouldStayOpen(
                offsetPx = 150f,
                openOffsetPx = 74f,
                closedOffsetPx = 390f,
            ),
        )
        assertFalse(
            topicPanelShouldStayOpen(
                offsetPx = 300f,
                openOffsetPx = 74f,
                closedOffsetPx = 390f,
            ),
        )
    }

    @Test
    fun `channel header counts distinct members and online presence`() {
        val bindings = listOf(
            binding("binding-1", "active-user"),
            binding("binding-2", "idle-user"),
            binding("binding-duplicate", "active-user"),
            binding("binding-other-stream", "other-user", streamUuid = "other"),
        )
        val users = listOf(
            user("active-user", "active"),
            user("idle-user", "idle"),
            user("other-user", "online"),
        )

        assertEquals(
            "2 участника, 1 в сети",
            channelMembersSubtitle("stream", bindings, users),
        )
        assertEquals(
            "0 участников, 0 в сети",
            channelMembersSubtitle("missing", bindings, users),
        )
    }

    @Test
    fun `muted topic exposes its notification state`() {
        assertEquals(
            "Уведомления темы отключены",
            topicNotificationDescription("mute"),
        )
        assertEquals(
            "Настройки уведомлений темы",
            topicNotificationDescription("default"),
        )
    }

    private fun binding(
        uuid: String,
        userUuid: String,
        streamUuid: String = "stream",
    ) = StreamBindingResponseData(
        uuid = uuid,
        streamUuid = streamUuid,
        userUuid = userUuid,
        whoUuid = "owner",
    )

    private fun user(
        uuid: String,
        status: String,
    ) = UserResponseData(
        username = uuid,
        uuid = uuid,
        status = status,
        avatar = "",
    )
}
