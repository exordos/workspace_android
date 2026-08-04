package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.navigation.WorkspaceDeepLink
import ru.genesiscorporation.workspace.beta.data.navigation.WorkspaceDeepLinkTarget

class DeepLinkChatTypeTest {
    @Test
    fun cachedCatalogResolvesExactTopicWithoutNetworkState() {
        val stream = stream(
            isPrivate = false,
            providerExternalId = "channel:123",
        )
        val topic = topic(stream.uuid)
        val destination = resolveCachedTopicDeepLink(
            deepLink = WorkspaceDeepLink(
                baseUrl = null,
                organizationId = "workspace.example.com",
                projectId = PROJECT_UUID,
                target = WorkspaceDeepLinkTarget.Topic(
                    streamUuid = stream.uuid,
                    topicUuid = topic.uuid,
                ),
            ),
            streams = listOf(stream),
            topicsByStream = mapOf(stream.uuid to listOf(topic)),
        )

        requireNotNull(destination)
        assertEquals(stream.uuid, destination.route.chatId)
        assertEquals(topic.uuid, destination.route.topicUuid)
        assertEquals(topic.name, destination.route.topicName)
        assertFalse(destination.route.isDirectMessages)
    }

    @Test
    fun cachedCatalogFailsClosedForDuplicateTopicIdentity() {
        val stream = stream(
            isPrivate = false,
            providerExternalId = "channel:123",
        )
        val topic = topic(stream.uuid)

        assertEquals(
            null,
            resolveCachedTopicDeepLink(
                deepLink = WorkspaceDeepLink(
                    baseUrl = null,
                    organizationId = "workspace.example.com",
                    projectId = PROJECT_UUID,
                    target = WorkspaceDeepLinkTarget.Topic(
                        streamUuid = stream.uuid,
                        topicUuid = topic.uuid,
                    ),
                ),
                streams = listOf(stream),
                topicsByStream = mapOf(
                    stream.uuid to listOf(topic, topic.copy(name = "Duplicate")),
                ),
            ),
        )
    }

    @Test
    fun privateChannelIsNotMistakenForDirectChat() {
        val privateChannel = stream(
            isPrivate = true,
            providerExternalId = "channel:123",
        )

        assertFalse(privateChannel.isDirectProviderChat())
        assertEquals("stream", privateChannel.folderItemChatType())
    }

    @Test
    fun directAndGroupDirectProviderKeysOpenAsDirectChats() {
        val direct = stream(
            isPrivate = true,
            providerExternalId = "direct:123",
        )
        val groupDirect = stream(
            isPrivate = true,
            providerExternalId = "group_direct:123,456",
        )

        assertTrue(direct.isDirectProviderChat())
        assertTrue(groupDirect.isDirectProviderChat())
        assertEquals("private", direct.folderItemChatType())
        assertEquals("private", groupDirect.folderItemChatType())
    }

    @Test
    fun nativeDirectUsesExplicitPartnerInsteadOfPrivateFlagAlone() {
        val direct = stream(
            isPrivate = true,
            providerExternalId = null,
            directUserUuid = "33333333-3333-4333-8333-333333333333",
        )

        assertTrue(direct.isDirectProviderChat())
        assertEquals("private", direct.folderItemChatType())
    }

    @Test
    fun malformedProviderDirectKeyRemainsAChannel() {
        val malformed = stream(
            isPrivate = true,
            providerExternalId = "direct",
        )

        assertFalse(malformed.isDirectProviderChat())
        assertEquals("stream", malformed.folderItemChatType())
    }

    @Test
    fun userManagedFolderClassificationMatchesBackendContract() {
        assertTrue(folder(systemType = null).isUserManaged())
        assertTrue(folder(systemType = "created").isUserManaged())
        assertFalse(folder(systemType = "all").isUserManaged())
        assertFalse(folder(systemType = "personal").isUserManaged())
    }

    @Test
    fun folderSubmenuOnlyListsUserFoldersWithoutTheStream() {
        val targetStreamUuid = "11111111-1111-4111-8111-111111111111"
        val available = folder(systemType = null, uuid = "available")
        val alreadyContainsStream = folder(
            systemType = "created",
            uuid = "contains-stream",
            items = listOf(
                FolderItem(
                    uuid = "item",
                    folderUuid = "contains-stream",
                    streamUuid = targetStreamUuid,
                    chatType = "stream",
                    unreadCount = 0,
                ),
            ),
        )
        val system = folder(systemType = "personal", uuid = "system")

        assertEquals(
            listOf(available),
            availableFoldersForStream(
                folders = listOf(system, alreadyContainsStream, available),
                streamUuid = targetStreamUuid,
            ),
        )
    }

    private fun stream(
        isPrivate: Boolean,
        providerExternalId: String?,
        directUserUuid: String? = null,
    ) = Stream(
        uuid = "11111111-1111-4111-8111-111111111111",
        unreadCount = 0,
        updatedAt = "2026-01-01T00:00:00Z",
        name = "Chat",
        isPrivate = isPrivate,
        color = 0,
        directUserUuid = directUserUuid,
        provider = providerExternalId?.let {
            ProviderReference(
                kind = "zulip",
                externalId = it,
            )
        },
    )

    private fun folder(
        systemType: String?,
        uuid: String = "44444444-4444-4444-8444-444444444444",
        items: List<FolderItem> = emptyList(),
    ) = FolderResponseData(
        uuid = uuid,
        title = "Folder",
        unreadCount = 0,
        systemType = systemType,
        creationDate = "2026-01-01T00:00:00Z",
        items = items,
    )

    private fun topic(streamUuid: String) = TopicsResponseData(
        uuid = "22222222-2222-4222-8222-222222222222",
        name = "Cached topic",
        streamUuid = streamUuid,
        updatedAt = "2026-01-01T00:00:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = true,
    )

    private companion object {
        const val PROJECT_UUID =
            "33333333-3333-4333-8333-333333333333"
    }
}
