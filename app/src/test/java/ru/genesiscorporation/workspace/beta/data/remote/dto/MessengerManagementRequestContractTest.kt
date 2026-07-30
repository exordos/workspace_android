package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class MessengerManagementRequestContractTest {
    @Test
    fun `folder mutations use canonical resource and action endpoints`() {
        val update = UpdateFolderRequest("folder-id", "Renamed", 42)
        val delete = DeleteFolderRequest("folder-id")
        val pin = PinFolderItemRequest("item-id")
        val unpin = UnpinFolderItemRequest("item-id")

        assertEquals(HTTPMethod.PUT, update.method)
        assertEquals("/api/workspace/v1/messenger/folders/folder-id", update.url)
        assertEquals("Renamed", update.data.title)
        assertEquals(42L, update.data.backgroundColorValue)
        assertEquals(HTTPMethod.DELETE, delete.method)
        assertEquals("/api/workspace/v1/messenger/folders/folder-id", delete.url)
        assertEquals(
            "/api/workspace/v1/messenger/folder_items/item-id/actions/pin/invoke",
            pin.url,
        )
        assertEquals(
            "/api/workspace/v1/messenger/folder_items/item-id/actions/unpin/invoke",
            unpin.url,
        )
    }

    @Test
    fun `topic mutations match the desktop messenger contract`() {
        val create = CreateTopicRequest("Launch", "stream-id")
        val rename = RenameTopicRequest("topic-id", "Renamed")
        val toggleDone = ToggleTopicDoneRequest("topic-id")
        val notifications = TopicNotificationsRequest("topic-id", "follow")
        val markRead = MarkTopicReadRequest("topic-id")
        val delete = DeleteTopicRequest("topic-id")

        assertEquals("/api/workspace/v1/messenger/stream_topics/", create.url)
        assertEquals("stream-id", create.data.streamUuid)
        assertEquals("/api/workspace/v1/messenger/stream_topics/topic-id", rename.url)
        assertEquals("Renamed", rename.data.name)
        assertEquals(
            "/api/workspace/v1/messenger/stream_topics/topic-id/actions/toggle_done/invoke",
            toggleDone.url,
        )
        assertEquals(
            "/api/workspace/v1/messenger/stream_topics/topic-id/actions/notifications/invoke",
            notifications.url,
        )
        assertEquals("follow", notifications.data.notificationMode)
        assertEquals(
            "/api/workspace/v1/messenger/stream_topics/topic-id/actions/read/invoke",
            markRead.url,
        )
        assertEquals(HTTPMethod.DELETE, delete.method)
    }

    @Test
    fun `stream membership mutations use member role grouping`() {
        val add = AddStreamMembersRequest(
            streamUuid = "stream-id",
            memberUserUuids = listOf("user-a", "user-b"),
        )
        val remove = DeleteStreamBindingRequest("binding-id")
        val markRead = MarkStreamReadRequest("stream-id")

        assertEquals(
            "/api/workspace/v1/messenger/streams/stream-id/actions/add_users/invoke",
            add.url,
        )
        assertEquals(listOf("user-a", "user-b"), add.data.member)
        assertEquals(
            "/api/workspace/v1/messenger/stream_bindings/binding-id",
            remove.url,
        )
        assertEquals(
            "/api/workspace/v1/messenger/streams/stream-id/actions/read/invoke",
            markRead.url,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `stream bindings can be scoped without emitting a null query parameter`() {
        assertEquals(
            mapOf("stream_uuid" to "stream-id"),
            Properties.encodeToStringMap(StreamBindingsRequest("stream-id").data),
        )
        assertEquals(
            emptyMap<String, String>(),
            Properties.encodeToStringMap(StreamBindingsRequest().data),
        )
    }
}
