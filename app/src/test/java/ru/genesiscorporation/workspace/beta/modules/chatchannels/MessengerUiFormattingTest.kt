package ru.genesiscorporation.workspace.beta.modules.chatchannels

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
