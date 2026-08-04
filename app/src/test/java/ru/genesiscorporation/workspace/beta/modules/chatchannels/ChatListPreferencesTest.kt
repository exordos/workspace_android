package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferences
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

class ChatListPreferencesTest {
    @Test
    fun `default order remains newest activity first`() {
        val newest = stream(
            uuid = "newest",
            updatedAt = "2026-07-30T12:00:00Z",
        )
        val older = stream(
            uuid = "older",
            updatedAt = "2026-07-30T11:00:00Z",
        )

        assertEquals(
            listOf("newest", "older"),
            orderChatStreams(
                streams = listOf(older, newest),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `personal unread preference only promotes one to one unread chats`() {
        val channel = stream(
            uuid = "channel",
            updatedAt = "2026-07-30T12:00:00Z",
            unreadCount = 2,
        )
        val groupDirect = stream(
            uuid = "group",
            updatedAt = "2026-07-30T11:30:00Z",
            unreadCount = 2,
            providerExternalId = "group_direct:1,2",
        )
        val personal = stream(
            uuid = "personal",
            updatedAt = "2026-07-30T11:00:00Z",
            unreadCount = 1,
            directUserUuid = "11111111-1111-4111-8111-111111111111",
        )

        assertEquals(
            listOf("personal", "channel", "group"),
            orderChatStreams(
                streams = listOf(channel, groupDirect, personal),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(
                    prioritizePersonalUnread = true,
                ),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `personal preference does not move read direct chat above unread`() {
        val unreadChannel = stream(
            uuid = "channel",
            updatedAt = "2026-07-30T12:00:00Z",
            unreadCount = 1,
        )
        val readPersonal = stream(
            uuid = "personal",
            updatedAt = "2026-07-30T11:00:00Z",
            directUserUuid = "11111111-1111-4111-8111-111111111111",
        )

        assertEquals(
            listOf("channel", "personal"),
            orderChatStreams(
                streams = listOf(readPersonal, unreadChannel),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(
                    prioritizePersonalUnread = true,
                ),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `muted unread channels are lower by default`() {
        val muted = stream(
            uuid = "muted",
            updatedAt = "2026-07-30T12:00:00Z",
            unreadCount = 1,
            notificationMode = "muted",
        )
        val active = stream(
            uuid = "active",
            updatedAt = "2026-07-30T11:00:00Z",
            unreadCount = 1,
            notificationMode = "all_messages",
        )

        assertEquals(
            listOf("active", "muted"),
            orderChatStreams(
                streams = listOf(muted, active),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `mentions only ordinary unread is lower than all messages unread`() {
        val mentionsOnly = stream(
            uuid = "mentions-only",
            updatedAt = "2026-07-30T12:00:00Z",
            unreadCount = 2,
            notificationMode = "mentions_only",
        )
        val allMessages = stream(
            uuid = "all-messages",
            updatedAt = "2026-07-30T11:00:00Z",
            unreadCount = 1,
            notificationMode = "all_messages",
        )

        assertEquals(
            listOf("all-messages", "mentions-only"),
            orderChatStreams(
                streams = listOf(mentionsOnly, allMessages),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `mentions only unread mention keeps attention priority`() {
        val mentionsOnly = stream(
            uuid = "mentions-only",
            updatedAt = "2026-07-30T12:00:00Z",
            unreadCount = 2,
            notificationMode = "mentions_only",
        )
        val allMessages = stream(
            uuid = "all-messages",
            updatedAt = "2026-07-30T11:00:00Z",
            unreadCount = 1,
            notificationMode = "all_messages",
        )

        assertEquals(
            listOf("mentions-only", "all-messages"),
            orderChatStreams(
                streams = listOf(allMessages, mentionsOnly),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(),
                unreadMentionStreamUuids = setOf(mentionsOnly.uuid),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `active group remains stronger than muted folder pin`() {
        val personal = stream(
            uuid = "personal",
            updatedAt = "2026-07-30T12:00:00Z",
            unreadCount = 1,
            directUserUuid = "11111111-1111-4111-8111-111111111111",
        )
        val pinned = stream(
            uuid = "pinned",
            updatedAt = "2026-07-30T10:00:00Z",
            unreadCount = 1,
            notificationMode = "muted",
        )
        val folderItems = mapOf(
            pinned.uuid to folderItem(
                streamUuid = pinned.uuid,
                orderIndex = 10,
                pinnedAt = "2026-07-30T09:00:00Z",
            ),
            personal.uuid to folderItem(
                streamUuid = personal.uuid,
                orderIndex = 0,
            ),
        )

        assertEquals(
            listOf("personal", "pinned"),
            orderChatStreams(
                streams = listOf(personal, pinned),
                folderItemsByStream = folderItems,
                preferences = WorkspaceUiPreferences(
                    prioritizePersonalUnread = true,
                    prioritizeUnmutedUnreadChannels = true,
                ),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `enabled unread priority overrides ordinary folder order`() {
        val channel = stream(
            uuid = "channel",
            updatedAt = "2026-07-30T12:00:00Z",
            unreadCount = 1,
        )
        val personal = stream(
            uuid = "personal",
            updatedAt = "2026-07-30T11:00:00Z",
            unreadCount = 1,
            directUserUuid = "11111111-1111-4111-8111-111111111111",
        )
        val folderItems = mapOf(
            channel.uuid to folderItem(
                streamUuid = channel.uuid,
                orderIndex = 0,
            ),
            personal.uuid to folderItem(
                streamUuid = personal.uuid,
                orderIndex = 10,
            ),
        )

        assertEquals(
            listOf("personal", "channel"),
            orderChatStreams(
                streams = listOf(channel, personal),
                folderItemsByStream = folderItems,
                preferences = WorkspaceUiPreferences(
                    prioritizePersonalUnread = true,
                ),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `stream groups depend on settings even without unread`() {
        val active = stream(
            uuid = "active",
            updatedAt = "2026-08-01T10:00:00Z",
        )
        val muted = stream(
            uuid = "muted",
            updatedAt = "2026-08-04T10:00:00Z",
            notificationMode = "muted",
        )
        val archived = stream(
            uuid = "archived",
            updatedAt = "2026-08-05T10:00:00Z",
            isArchived = true,
        )

        assertEquals(
            listOf("active", "muted", "archived"),
            orderChatStreams(
                streams = listOf(archived, muted, active),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(),
            ).map(Stream::uuid),
        )
    }

    @Test
    fun `explicit active topic returns muted stream to active group`() {
        val ordinary = stream(
            uuid = "ordinary",
            updatedAt = "2026-08-03T10:00:00Z",
        )
        val mutedWithActiveTopic = stream(
            uuid = "muted-active-topic",
            updatedAt = "2026-08-04T10:00:00Z",
            notificationMode = "muted",
        )
        val fullyMuted = stream(
            uuid = "fully-muted",
            updatedAt = "2026-08-05T10:00:00Z",
            notificationMode = "muted",
        )

        assertEquals(
            listOf("muted-active-topic", "ordinary", "fully-muted"),
            orderChatStreams(
                streams = listOf(fullyMuted, ordinary, mutedWithActiveTopic),
                folderItemsByStream = emptyMap(),
                preferences = WorkspaceUiPreferences(),
                topicsByStream = mapOf(
                    mutedWithActiveTopic.uuid to listOf(
                        topic(streamUuid = mutedWithActiveTopic.uuid, notificationMode = "follow"),
                    ),
                    fullyMuted.uuid to listOf(
                        topic(streamUuid = fullyMuted.uuid, notificationMode = "default"),
                    ),
                ),
            ).map(Stream::uuid),
        )
    }

    private fun stream(
        uuid: String,
        updatedAt: String,
        unreadCount: Int = 0,
        directUserUuid: String? = null,
        providerExternalId: String? = null,
        notificationMode: String = "all_messages",
        isArchived: Boolean = false,
    ) = Stream(
        uuid = uuid,
        unreadCount = unreadCount,
        updatedAt = updatedAt,
        name = uuid,
        isPrivate = directUserUuid != null || providerExternalId != null,
        notificationMode = notificationMode,
        isArchived = isArchived,
        directUserUuid = directUserUuid,
        provider = providerExternalId?.let {
            ProviderReference(
                kind = "zulip",
                externalId = it,
            )
        },
    )

    private fun topic(
        streamUuid: String,
        notificationMode: String,
    ) = TopicsResponseData(
        uuid = "topic-$streamUuid-$notificationMode",
        name = "Topic",
        streamUuid = streamUuid,
        updatedAt = "2026-08-04T10:00:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = false,
        notificationMode = notificationMode,
    )

    private fun folderItem(
        streamUuid: String,
        orderIndex: Int,
        pinnedAt: String? = null,
    ) = FolderItem(
        uuid = "item-$streamUuid",
        streamUuid = streamUuid,
        chatType = "stream",
        unreadCount = 0,
        orderIndex = orderIndex,
        pinnedAt = pinnedAt,
    )
}
