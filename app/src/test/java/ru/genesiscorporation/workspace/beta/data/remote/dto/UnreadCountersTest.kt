package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadCountersTest {
    @Test
    fun `displayed unread prefers active then passive`() {
        assertEquals(
            DisplayedUnreadCount(count = 3, passive = false),
            resolveDisplayedUnreadCount(
                unreadCount = 8,
                activeUnreadCount = 3,
                passiveUnreadCount = 5,
            ),
        )
        assertEquals(
            DisplayedUnreadCount(count = 5, passive = true),
            resolveDisplayedUnreadCount(
                unreadCount = 5,
                activeUnreadCount = 0,
                passiveUnreadCount = 5,
            ),
        )
        assertNull(
            resolveDisplayedUnreadCount(
                unreadCount = 0,
                activeUnreadCount = 0,
                passiveUnreadCount = 0,
            ),
        )
    }

    @Test
    fun `legacy cached stream falls back to total unread`() {
        val stream = Json.decodeFromString<Stream>(
            """{
                "uuid":"stream",
                "unread_count":4,
                "updated_at":"2026-08-04T10:00:00Z",
                "name":"Legacy",
                "private":false
            }""".trimIndent(),
        )

        assertNull(stream.activeUnreadCount)
        assertNull(stream.passiveUnreadCount)
        assertEquals(
            DisplayedUnreadCount(count = 4, passive = false),
            stream.displayedUnreadCount(),
        )
    }

    @Test
    fun `legacy cached topic and folder item fall back to total unread`() {
        val topic = Json.decodeFromString<TopicsResponseData>(
            """{
                "uuid":"topic",
                "name":"Legacy topic",
                "stream_uuid":"stream",
                "updated_at":"2026-08-04T10:00:00Z",
                "unread_count":3,
                "is_done":false,
                "is_default":false
            }""".trimIndent(),
        )
        val folderItem = Json.decodeFromString<FolderItem>(
            """{
                "uuid":"item",
                "stream_uuid":"stream",
                "chat_type":"stream",
                "unread_count":2
            }""".trimIndent(),
        )

        assertEquals(DisplayedUnreadCount(count = 3, passive = false), topic.displayedUnreadCount())
        assertEquals(DisplayedUnreadCount(count = 2, passive = false), folderItem.displayedUnreadCount())
    }

    @Test
    fun `topic modes inherit mute and explicit modes reactivate stream`() {
        val inherited = topic("default")
        val explicitlyMuted = topic("mute")
        val unmuted = topic("unmute")
        val followed = topic("follow")
        val mutedStream = stream(notificationMode = "muted")

        assertTrue(inherited.isEffectivelyMuted("muted"))
        assertTrue(explicitlyMuted.isEffectivelyMuted("all_messages"))
        assertFalse(unmuted.isEffectivelyMuted("muted"))
        assertTrue(mutedStream.isFullyMuted(listOf(inherited, explicitlyMuted)))
        assertFalse(mutedStream.isFullyMuted(listOf(inherited, unmuted)))
        assertFalse(mutedStream.isFullyMuted(listOf(followed)))
    }

    private fun stream(notificationMode: String) = Stream(
        uuid = "stream",
        unreadCount = 0,
        updatedAt = "2026-08-04T10:00:00Z",
        name = "Stream",
        isPrivate = false,
        notificationMode = notificationMode,
    )

    private fun topic(notificationMode: String) = TopicsResponseData(
        uuid = "topic-$notificationMode",
        name = "Topic",
        streamUuid = "stream",
        updatedAt = "2026-08-04T10:00:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = false,
        notificationMode = notificationMode,
    )
}
