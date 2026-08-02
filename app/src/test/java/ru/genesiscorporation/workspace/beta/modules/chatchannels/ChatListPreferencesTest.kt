package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferences
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream

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
    fun `folder pin remains stronger than preferences`() {
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
            listOf("pinned", "personal"),
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

    private fun stream(
        uuid: String,
        updatedAt: String,
        unreadCount: Int = 0,
        directUserUuid: String? = null,
        providerExternalId: String? = null,
        notificationMode: String = "all_messages",
    ) = Stream(
        uuid = uuid,
        unreadCount = unreadCount,
        updatedAt = updatedAt,
        name = uuid,
        isPrivate = directUserUuid != null || providerExternalId != null,
        notificationMode = notificationMode,
        directUserUuid = directUserUuid,
        provider = providerExternalId?.let {
            ProviderReference(
                kind = "zulip",
                externalId = it,
            )
        },
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
