package ru.genesiscorporation.workspace.beta.modules.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class InboxModelTest {
    @Test
    fun `groups direct conversations before presentation without losing catalog order`() {
        val direct = stream(
            uuid = "stream-direct",
            name = "fallback",
            unreadCount = 2,
            isPrivate = true,
            directUserUuid = "user-2",
        )
        val channel = stream(
            uuid = "stream-channel",
            name = "Platform",
            unreadCount = 3,
        )

        val groups = buildInboxGroups(
            streams = listOf(channel, direct),
            topicsByStream = mapOf(
                channel.uuid to listOf(topic("topic-channel", channel.uuid, "Deploys", 3)),
                direct.uuid to listOf(topic("topic-direct", direct.uuid, "General", 2)),
            ),
            users = listOf(user("user-2", "Ada", "Lovelace")),
        )

        assertEquals(
            listOf("stream-channel", "stream-direct"),
            groups.map(InboxGroup::streamUuid),
        )
        assertEquals(InboxGroupKind.CHANNEL, groups[0].kind)
        assertEquals("#Platform · Deploys", groups[0].rows.single().title)
        assertEquals(InboxGroupKind.DIRECT, groups[1].kind)
        assertEquals("Ada Lovelace · General", groups[1].rows.single().title)
    }

    @Test
    fun `shows only unread topics and keeps exact duplicate-name topic UUID`() {
        val stream = stream("stream-1", "Engineering", unreadCount = 9)
        val groups = buildInboxGroups(
            streams = listOf(stream),
            topicsByStream = mapOf(
                stream.uuid to listOf(
                    topic("topic-read", stream.uuid, "Release", 0),
                    topic("topic-unread-a", stream.uuid, "Release", 4),
                    topic("topic-unread-b", stream.uuid, "Release", 5),
                ),
            ),
            users = emptyList(),
        )

        assertEquals(
            listOf("topic-unread-a", "topic-unread-b"),
            groups.single().rows.map(InboxRow::id),
        )
        assertEquals(
            InboxDestination.Topic("stream-1", "topic-unread-b"),
            groups.single().rows.last().destination,
        )
    }

    @Test
    fun `uses stream fallback only when stream unread has no unread topic`() {
        val stream = stream("stream-1", "Support", unreadCount = 7)
        val groups = buildInboxGroups(
            streams = listOf(stream),
            topicsByStream = mapOf(
                stream.uuid to listOf(topic("topic-read", stream.uuid, "Resolved", 0)),
            ),
            users = emptyList(),
        )

        val row = groups.single().rows.single()
        assertEquals("#Support", row.title)
        assertEquals(7, row.unreadCount)
        assertEquals(InboxDestination.Stream("stream-1"), row.destination)
    }

    @Test
    fun `hides archived and fully read streams`() {
        val groups = buildInboxGroups(
            streams = listOf(
                stream("read", "Read", unreadCount = 0),
                stream("archived", "Archived", unreadCount = 8, isArchived = true),
                stream("topic-unread", "Topic unread", unreadCount = 0),
            ),
            topicsByStream = mapOf(
                "read" to listOf(topic("read-topic", "read", "Read", 0)),
                "archived" to listOf(topic("archived-topic", "archived", "Unread", 8)),
                "topic-unread" to listOf(topic("unread-topic", "topic-unread", "Unread", 1)),
            ),
            users = emptyList(),
        )

        assertEquals(listOf("topic-unread"), groups.map(InboxGroup::streamUuid))
    }

    @Test
    fun `blank names get functional nonblank labels and negative counts are ignored`() {
        val groups = buildInboxGroups(
            streams = listOf(stream("stream", " ", unreadCount = 0)),
            topicsByStream = mapOf(
                "stream" to listOf(topic("topic", "stream", " ", 1)),
            ),
            users = emptyList(),
        )

        assertEquals("#Канал · Все сообщения", groups.single().rows.single().title)
        assertTrue(inboxUnreadCount(groups) > 0)
        assertEquals(
            0,
            inboxUnreadCount(
                listOf(
                    InboxGroup(
                        streamUuid = "negative",
                        streamTitle = "#Negative",
                        kind = InboxGroupKind.CHANNEL,
                        unreadCount = -10,
                        rows = listOf(
                            InboxRow(
                                id = "negative-row",
                                title = "#Negative",
                                unreadCount = -4,
                                updatedAt = "",
                                destination = InboxDestination.Stream("negative"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `catalog validation rejects ambiguous and foreign identifiers`() {
        val stream = stream("stream", "Channel", unreadCount = 1)
        val duplicateStream = stream.copy(name = "Duplicate")
        assertEquals(
            "stream UUIDs are not unique",
            validateInboxCatalog(listOf(stream, duplicateStream), emptyList()),
        )
        assertEquals(
            "topic UUIDs are not unique",
            validateInboxCatalog(
                listOf(stream),
                listOf(
                    topic("topic", "stream", "One", 1),
                    topic("topic", "stream", "Two", 1),
                ),
            ),
        )
        assertEquals(
            "topic references an unknown stream",
            validateInboxCatalog(
                listOf(stream),
                listOf(topic("topic", "another-stream", "Foreign", 1)),
            ),
        )
        assertEquals(
            null,
            validateInboxCatalog(
                listOf(stream),
                listOf(topic("topic", "stream", "Valid", 1)),
            ),
        )
    }

    @Test
    fun `catalog refresh never overwrites a concurrent realtime update`() {
        assertEquals(
            InboxCatalogApplyDecision.APPLY,
            decideInboxCatalogApply(
                catalogChangedDuringRequest = false,
                attempt = 0,
                maxAttempts = 2,
            ),
        )
        assertEquals(
            InboxCatalogApplyDecision.RETRY,
            decideInboxCatalogApply(
                catalogChangedDuringRequest = true,
                attempt = 0,
                maxAttempts = 2,
            ),
        )
        assertEquals(
            InboxCatalogApplyDecision.FAIL_BUSY,
            decideInboxCatalogApply(
                catalogChangedDuringRequest = true,
                attempt = 1,
                maxAttempts = 2,
            ),
        )
    }

    @Test
    fun `authoritative empty snapshot remains displayable after offline refresh failure`() {
        assertTrue(
            hasDisplayableInboxSnapshot(
                groups = emptyList(),
                state = InboxSyncState(
                    hasLoaded = true,
                    hasUsableSnapshot = true,
                    error = "offline",
                ),
            ),
        )
        assertFalse(
            hasDisplayableInboxSnapshot(
                groups = emptyList(),
                state = InboxSyncState(
                    hasLoaded = true,
                    error = "offline",
                ),
            ),
        )
    }

    private fun stream(
        uuid: String,
        name: String,
        unreadCount: Int,
        isPrivate: Boolean = false,
        directUserUuid: String? = null,
        isArchived: Boolean = false,
    ) = Stream(
        uuid = uuid,
        unreadCount = unreadCount,
        updatedAt = "2026-07-30T09:00:00Z",
        name = name,
        isPrivate = isPrivate,
        directUserUuid = directUserUuid,
        isArchived = isArchived,
        provider = if (isPrivate) {
            ProviderReference(kind = "workspace", externalId = "direct:user")
        } else {
            null
        },
    )

    private fun topic(
        uuid: String,
        streamUuid: String,
        name: String,
        unreadCount: Int,
    ) = TopicsResponseData(
        uuid = uuid,
        name = name,
        streamUuid = streamUuid,
        updatedAt = "2026-07-30T09:00:00Z",
        unreadCount = unreadCount,
        isDone = false,
        isDefault = false,
    )

    private fun user(
        uuid: String,
        firstName: String,
        lastName: String,
    ) = UserResponseData(
        firstName = firstName,
        lastName = lastName,
        username = uuid,
        uuid = uuid,
        status = "active",
        avatar = "",
    )
}
