package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream

class DeepLinkChatTypeTest {
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
    fun folderMenuOnlyExposesBackendSupportedMutations() {
        assertEquals(
            FolderChatMenuAction.ADD,
            folderChatMenuAction(folder(systemType = "all")),
        )
        assertEquals(
            FolderChatMenuAction.REMOVE,
            folderChatMenuAction(folder(systemType = "created")),
        )
        assertEquals(
            FolderChatMenuAction.REMOVE,
            folderChatMenuAction(folder(systemType = null)),
        )
        assertTrue(folder(systemType = null).isUserManaged())
        assertFalse(folder(systemType = "all").isUserManaged())
        assertEquals(null, folderChatMenuAction(folder(systemType = "personal")))
        assertEquals(null, folderChatMenuAction(null))
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

    private fun folder(systemType: String?) = FolderResponseData(
        uuid = "44444444-4444-4444-8444-444444444444",
        title = "Folder",
        unreadCount = 0,
        systemType = systemType,
        creationDate = "2026-01-01T00:00:00Z",
    )
}
