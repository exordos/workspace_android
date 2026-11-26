package com.exordos.workspace.data

import org.junit.Assert.assertEquals
import org.junit.Test
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.uio.UserUIO
import com.exordos.workspace.modules.chatchannels.chatDisplayName

class StreamDisplayNameTest {
    @Test
    fun directConversationUsesParticipantNameInsteadOfTechnicalName() {
        assertEquals("Riley Chen", chatDisplayName(stream(), user()))
    }

    @Test
    fun directConversationUsesUsernameWhenProfileHasNoName() {
        val participant = user().copy(firstName = null, lastName = null)
        assertEquals("riley", chatDisplayName(stream(), participant))
    }

    @Test
    fun missingOrUnrelatedParticipantKeepsStreamName() {
        assertEquals("Direct", chatDisplayName(stream(), null))
        assertEquals("Direct", chatDisplayName(stream(), user().copy(uuid = "someone-else")))
        assertEquals("Direct", chatDisplayName(stream(), user().copy(firstName = " ", lastName = "", username = "")))
    }

    @Test
    fun groupKeepsItsNameEvenWhenAParticipantIsPresent() {
        assertEquals("Project chat", chatDisplayName(stream().copy(name = "Project chat", directUserUuid = null), user()))
    }

    @Test
    fun currentProfileOverridesCachedParticipantForAnOpenConversation() {
        assertEquals("Riley Updated", chatDisplayName(stream(), user().copy(lastName = "Updated")))
    }

    private fun stream() = StreamUIO(
        uuid = "conversation", unreadCount = 0, activeUnreadCount = 0,
        passiveUnreadCount = 0, updatedAt = "2026-01-01T00:00:00Z", name = "Direct",
        isPrivate = true, color = 0, directUserUuid = "participant",
        notificationMode = "all_messages",
    )

    private fun user() = UserUIO(
        firstName = "Riley", lastName = "Chen", username = "riley", uuid = "participant",
        status = "idle", avatar = "",
    )
}
