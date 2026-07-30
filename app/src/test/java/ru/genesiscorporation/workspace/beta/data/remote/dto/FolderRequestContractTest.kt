package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderRequestContractTest {
    @Test
    fun `folder create uses the canonical messenger endpoint`() {
        val request = AddFolderRequest("Important")

        assertEquals("/api/workspace/v1/messenger/folders/", request.url)
        assertEquals("Important", request.data.title)
    }

    @Test
    fun `folder item create uses UUID fields from the backend contract`() {
        val request = AddChatToFolderRequest(
            folderUuid = "folder-uuid",
            streamUuid = "stream-uuid",
            chatType = "stream",
        )

        assertEquals("/api/workspace/v1/messenger/folder_items/", request.url)
        assertEquals("folder-uuid", request.data.folderUuid)
        assertEquals("stream-uuid", request.data.streamUuid)
        assertEquals("stream", request.data.chatType)
        assertEquals(null, request.data.orderIndex)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `folder item list filters by folder UUID`() {
        val request = FolderChatsRequest("folder-uuid")

        assertEquals("/api/workspace/v1/messenger/folder_items/", request.url)
        assertEquals(
            mapOf("folder_uuid" to "folder-uuid"),
            Properties.encodeToStringMap(request.data),
        )
    }

    @Test
    fun `folder item delete addresses the item directly`() {
        val request = DeleteChatFromFolderRequest("folder-item-uuid")

        assertEquals(
            "/api/workspace/v1/messenger/folder_items/folder-item-uuid",
            request.url,
        )
    }
}
