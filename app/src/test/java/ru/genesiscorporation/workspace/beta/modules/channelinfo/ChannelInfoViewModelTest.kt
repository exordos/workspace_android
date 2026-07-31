package ru.genesiscorporation.workspace.beta.modules.channelinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class ChannelInfoViewModelTest {
    @Test
    fun `members are resolved from bindings with owner first`() {
        val users = listOf(
            user("member", "Борис"),
            user("owner", "Анна"),
            user("outside", "Вне канала"),
        )
        val bindings = listOf(
            binding("stream", "member", "member"),
            binding("stream", "owner", "owner"),
            binding("other-stream", "outside", "owner"),
        )

        val result = resolveChannelMembers("stream", bindings, users)

        assertEquals(listOf("owner", "member"), result.map { it.user.uuid })
        assertEquals("owner", result.first().role)
        assertEquals("stream-owner", result.first().bindingUuid)
    }

    @Test
    fun `users are not fabricated as members when bindings are absent`() {
        val result = resolveChannelMembers(
            streamUuid = "stream",
            bindings = emptyList(),
            users = listOf(user("first", "Анна"), user("second", "Борис")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `members stay empty while bindings load`() {
        val result = resolveChannelMembers(
            streamUuid = "stream",
            bindings = null,
            users = listOf(user("first", "Анна")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `members can leave while only the stream owner can remove others`() {
        assertTrue(
            canRemoveChannelMember(
                memberUserUuid = "current",
                currentUserUuid = "current",
                ownerUuid = "owner",
            ),
        )
        assertTrue(
            canRemoveChannelMember(
                memberUserUuid = "member",
                currentUserUuid = "owner",
                ownerUuid = "owner",
            ),
        )
        assertEquals(
            false,
            canRemoveChannelMember(
                memberUserUuid = "other",
                currentUserUuid = "member",
                ownerUuid = "owner",
            ),
        )
        assertEquals(
            false,
            canRemoveChannelMember(
                memberUserUuid = "current",
                currentUserUuid = "current",
                ownerUuid = "owner",
                bindingsAuthoritative = false,
            ),
        )
    }

    private fun user(uuid: String, name: String) = UserResponseData(
        firstName = name,
        username = uuid,
        uuid = uuid,
        status = "active",
        avatar = "",
    )

    private fun binding(
        streamUuid: String,
        userUuid: String,
        role: String,
    ) = StreamBindingResponseData(
        uuid = "$streamUuid-$userUuid",
        streamUuid = streamUuid,
        userUuid = userUuid,
        whoUuid = "actor",
        role = role,
    )
}
