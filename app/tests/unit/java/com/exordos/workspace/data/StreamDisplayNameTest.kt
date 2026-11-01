package com.exordos.workspace.data

import org.junit.Assert.assertEquals
import org.junit.Test
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.UserResponseData

class StreamDisplayNameTest {
    @Test
    fun directConversationUsesParticipantNameInsteadOfTechnicalName() {
        assertEquals("Riley Chen", stream().displayableName())
    }

    @Test
    fun directConversationUsesUsernameWhenProfileHasNoName() {
        val participant = user().copy(firstName = null, lastName = null)
        assertEquals("riley", stream().displayableName(participant))
    }

    @Test
    fun missingOrUnrelatedParticipantKeepsStreamName() {
        assertEquals("Direct", stream().copy(directUser = null).displayableName())
        assertEquals("Direct", stream().displayableName(user().copy(uuid = "someone-else")))
        assertEquals("Direct", stream().displayableName(user().copy(firstName = " ", lastName = "", username = "")))
    }

    @Test
    fun groupKeepsItsNameEvenWhenAParticipantIsPresent() {
        assertEquals("Project chat", stream().copy(name = "Project chat", directUserUuid = null).displayableName())
    }

    @Test
    fun currentProfileOverridesCachedParticipantForAnOpenConversation() {
        assertEquals("Riley Updated", stream().displayableName(user().copy(lastName = "Updated")))
    }

    private fun stream() = Stream(
        uuid = "conversation", unreadCount = 0, activeUnreadCount = 0,
        passiveUnreadCount = 0, updatedAt = "2026-01-01T00:00:00Z", name = "Direct",
        isPrivate = true, color = 0, directUserUuid = "participant",
        notificationMode = "all_messages", directUser = user(),
    )

    private fun user() = UserResponseData(
        firstName = "Riley", lastName = "Chen", username = "riley", uuid = "participant",
        status = "idle", avatar = "",
    )
}
