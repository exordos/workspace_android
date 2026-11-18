package com.exordos.workspace.data

import com.exordos.workspace.data.remote.dto.Draft
import com.exordos.workspace.data.remote.dto.FolderResponseData
import com.exordos.workspace.data.remote.dto.MessageReaction
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.ProjectResponseData
import com.exordos.workspace.data.remote.dto.ServerSettingsResponseData
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.data.uio.toDto
import com.exordos.workspace.data.uio.toUio
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceUioMappingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val messageJson = """{"uuid":"message","updated_at":"updated","created_at":"created",
        "stream_uuid":"stream","topic_uuid":"topic","user_uuid":"user","author_uuid":"author",
        "payload":{"kind":"e2ee","content":"ciphertext","key_uuid":"key","device_id":"device",
        "device_name":"Device","public_key":"public","request_message_uuid":"request",
        "sender_device_id":"sender","recipient_device_id":"recipient"},"is_own":true,
        "reactions":{"smile":2},"read":false,"project_id":"project","mentioned":true,
        "unknown_future_field":true}"""

    @Test fun messageMappingPreservesWireFieldsAndDetachesMutableState() {
        val dto = json.decodeFromString<MessageResponse>(messageJson)
        val uio = dto.toUio()
        assertEquals(listOf("message", "updated", "created", "stream", "topic", "user", "author"),
            listOf(uio.uuid, uio.updatedAt, uio.createdAt, uio.streamUuid, uio.topicUuid, uio.userUuid, uio.authorUuid))
        assertEquals(dto.payload, uio.payload.toDto())
        assertEquals(dto.reactions, uio.reactions)
        assertNotSame(dto.reactions, uio.reactions)
        assertTrue(uio.mentioned && uio.isOwn && !uio.read)
        assertEquals("project", uio.projectId)
        assertNull(uio.user)
        assertNull(uio.originalEncryptedPayload)
        uio.payload.content = "presentation plaintext"
        uio.uuid = "local"
        assertEquals("ciphertext", dto.payload.content)
        assertEquals("message", dto.uuid)
    }

    @Test fun streamMappingPreservesEncryptionMembershipAndPresentationDefaults() {
        val dto = json.decodeFromString<Stream>("""{"uuid":"stream","unread_count":3,
            "active_unread_count":2,"passive_unread_count":1,"updated_at":"updated","name":"Channel",
            "private":true,"color":17,"last_message_uuid":"message","default_topic_uuid":"topic",
            "direct_user_uuid":"person","notification_mode":"all_messages","is_archived":true,
            "encryption":true,"current_encryption_key_uuid":"key","owner_uuid":"owner",
            "project_id":"project","user_uuid":"person","role":"owner","description":"About",
            "invite_only":true,"announce":true}""")
        val uio = dto.toUio()
        assertEquals(dto.uuid, uio.uuid)
        assertEquals(listOf(3, 2, 1, 17), listOf(uio.unreadCount, uio.activeUnreadCount, uio.passiveUnreadCount, uio.color))
        assertEquals(listOf("updated", "Channel", "message", "topic", "person", "all_messages", "key",
            "owner", "project", "person", "owner", "About"), listOf(uio.updatedAt, uio.name,
            uio.lastMessageUuid, uio.defaultTopicUuid, uio.directUserUuid, uio.notificationMode,
            uio.currentEncryptionKeyUuid, uio.ownerUuid, uio.projectId, uio.userUuid, uio.role, uio.description))
        assertTrue(uio.isPrivate && uio.isArchived && uio.encryption && uio.inviteOnly && uio.announce)
        assertNull(uio.directUser)
        assertNull(uio.lastMessage)
    }

    @Test fun usersTopicsFoldersBindingsReactionsDraftsProjectsAndSettingsMapFromWire() {
        val user = json.decodeFromString<UserResponseData>("""{"email":"fixture@example.invalid",
            "first_name":"First","last_name":"Last","username":"fallback","uuid":"person",
            "status_emoji":"smile","status_text":"Available","status":"active","avatar":"avatar"}""").toUio()
        assertEquals("First Last", user.displayableName())
        assertEquals(listOf("fixture@example.invalid", "fallback", "person", "smile", "Available", "active", "avatar"),
            listOf(user.email, user.username, user.uuid, user.statusEmoji, user.statusText, user.status, user.avatar))
        assertEquals("В сети", user.statusDescription())
        val topic = json.decodeFromString<TopicsResponseData>("""{"uuid":"topic","name":"Topic","color":3,
            "stream_uuid":"stream","updated_at":"updated","unread_count":4,"active_unread_count":2,
            "is_done":true,"is_default":false,"last_message_uuid":"message","notification_mode":"follow",
            "summary":"Summary"}""").toUio()
        assertEquals(listOf("topic", "Topic", "stream", "updated", "message", "follow", "Summary"),
            listOf(topic.uuid, topic.name, topic.streamUuid, topic.updatedAt, topic.lastMessageUuid, topic.notificationMode, topic.summary))
        assertEquals(listOf(3, 4, 2), listOf(topic.color, topic.unreadCount, topic.activeUnreadCount))
        assertTrue(topic.isDone && !topic.isDefault)
        assertNull(topic.lastMessage)
        val folderDto = json.decodeFromString<FolderResponseData>("""{"uuid":"folder","title":"Folder",
            "unread_count":4,"system_type":"custom","created_at":"created","folder_items":[
            {"uuid":"item","stream_uuid":"stream","chat_type":"stream","unread_count":4}]}""")
        val folder = folderDto.toUio()
        assertEquals(listOf("folder", "Folder", "custom", "created"), listOf(folder.uuid, folder.title, folder.systemType, folder.creationDate))
        assertEquals(4, folder.unreadCount)
        assertEquals(listOf("item", "stream", "stream"), folder.items.single().let { listOf(it.uuid, it.streamUuid, it.chatType) })
        folder.items.single().unreadCount = 0
        assertEquals(4, folderDto.items.single().unreadCount)
        val binding = json.decodeFromString<StreamBindingResponseData>("""{"uuid":"binding","role":"owner",
            "stream_uuid":"stream","user_uuid":"person"}""").toUio()
        assertEquals(listOf("binding", "owner", "stream", "person"), listOf(binding.uuid, binding.role, binding.streamUuid, binding.userUuid))
        val reaction = json.decodeFromString<MessageReaction>("""{"uuid":"reaction","user_uuid":"person",
            "emoji_name":"smile","message_uuid":"message"}""").toUio()
        assertEquals(listOf("reaction", "person", "smile", "message"), listOf(reaction.uuid, reaction.userUuid, reaction.emojiName, reaction.messageUuid))
        val draftDto = json.decodeFromString<Draft>("""{"uuid":"draft","stream_uuid":"stream",
            "topic_uuid":"topic","updated_at":"updated","payload":{"kind":"markdown","content":"Draft"},"revision":7}""")
        val draft = draftDto.toUio()
        assertEquals(listOf("draft", "stream", "topic", "updated"), listOf(draft.uuid, draft.streamUuid, draft.topicUuid, draft.updatedAt))
        assertEquals(7, draft.revision)
        draft.payload.content = "Edited"
        assertEquals("Draft", draftDto.payload.content)
        val project = json.decodeFromString<ProjectResponseData>("""{"uuid":"project","name":"Project","description":"About"}""").toUio()
        assertEquals(listOf("project", "Project", "About"), listOf(project.uuid, project.name, project.description))
        val settings = json.decodeFromString<ServerSettingsResponseData>("""{"organization_uuid":"organization",
            "push_notifications_enabled":true,"email_auth_enabled":false,"realm_name":"Realm",
            "realm_url":"https://example.invalid","realm_icon":"icon","meet_url":"https://meet.example.invalid"}""").toUio()
        assertEquals(listOf("organization", "Realm", "https://example.invalid", "icon", "https://meet.example.invalid"),
            listOf(settings.organizationUuid, settings.realmName, settings.realmUrl, settings.realmIcon, settings.meetUrl))
        assertTrue(settings.pushNotificationsEnabled && !settings.emailAuthEnabled)
    }
}
