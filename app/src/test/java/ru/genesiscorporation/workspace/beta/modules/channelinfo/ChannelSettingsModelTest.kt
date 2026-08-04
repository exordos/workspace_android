package ru.genesiscorporation.workspace.beta.modules.channelinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSettingsModelTest {
    @Test
    fun `channel visibility maps to backend flags`() {
        assertEquals(
            ChannelVisibilityFlags(inviteOnly = false, isPrivate = false),
            ChannelVisibility.OPEN.flags(),
        )
        assertEquals(
            ChannelVisibilityFlags(inviteOnly = true, isPrivate = false),
            ChannelVisibility.CLOSED_OPEN_HISTORY.flags(),
        )
        assertEquals(
            ChannelVisibilityFlags(inviteOnly = true, isPrivate = true),
            ChannelVisibility.CLOSED_PROTECTED_HISTORY.flags(),
        )
        assertEquals(
            ChannelVisibility.CLOSED_PROTECTED_HISTORY,
            channelVisibility(inviteOnly = true, isPrivate = true),
        )
    }

    @Test
    fun `owner and administrator have management capabilities`() {
        assertTrue(canManageChannel("owner"))
        assertTrue(canManageChannel("administrator"))
        assertFalse(canManageChannel("moderator"))
        assertFalse(canManageChannel("member"))
        assertFalse(canManageChannel("guest"))
        assertFalse(canManageChannel(null))
    }

    @Test
    fun `only the channel owner can permanently delete it`() {
        assertTrue(
            canDeleteChannel(
                currentUserUuid = "owner",
                ownerUuid = "owner",
            ),
        )
        assertFalse(
            canDeleteChannel(
                currentUserUuid = "administrator",
                ownerUuid = "owner",
            ),
        )
        assertFalse(
            canDeleteChannel(
                currentUserUuid = null,
                ownerUuid = "owner",
            ),
        )
    }

    @Test
    fun `owner and current user remain immutable in member management`() {
        assertFalse(
            canManageChannelMember(
                currentUserRole = "owner",
                currentUserUuid = "current",
                memberUserUuid = "owner",
                memberRole = "owner",
            ),
        )
        assertFalse(
            canManageChannelMember(
                currentUserRole = "administrator",
                currentUserUuid = "current",
                memberUserUuid = "current",
                memberRole = "administrator",
            ),
        )
        assertTrue(
            canManageChannelMember(
                currentUserRole = "administrator",
                currentUserUuid = "current",
                memberUserUuid = "member",
                memberRole = "moderator",
            ),
        )
    }

    @Test
    fun `current member can leave while managers can remove non-owner members`() {
        assertTrue(
            canRemoveChannelMember(
                currentUserRole = "member",
                currentUserUuid = "current",
                memberUserUuid = "current",
                memberRole = "member",
            ),
        )
        assertTrue(
            canRemoveChannelMember(
                currentUserRole = "administrator",
                currentUserUuid = "admin",
                memberUserUuid = "member",
                memberRole = "moderator",
            ),
        )
        assertFalse(
            canRemoveChannelMember(
                currentUserRole = "administrator",
                currentUserUuid = "admin",
                memberUserUuid = "owner",
                memberRole = "owner",
            ),
        )
        assertFalse(
            canRemoveChannelMember(
                currentUserRole = "member",
                currentUserUuid = "member",
                memberUserUuid = "other",
                memberRole = "guest",
            ),
        )
    }

    @Test
    fun `only roles shown by the member editor are assignable`() {
        assertEquals(
            listOf("administrator", "moderator", "member"),
            EDITABLE_CHANNEL_MEMBER_ROLES,
        )
        assertFalse(isEditableChannelMemberRole("guest"))
        assertFalse(isEditableChannelMemberRole("owner"))
        assertFalse(isEditableChannelMemberRole("unsupported"))
    }
}
