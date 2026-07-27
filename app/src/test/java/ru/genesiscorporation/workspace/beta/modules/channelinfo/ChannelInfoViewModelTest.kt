package ru.genesiscorporation.workspace.beta.modules.channelinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(result.none(ChannelMember::isMockMembership))
    }

    @Test
    fun `users become explicitly mock memberships when bindings are unavailable`() {
        val result = resolveChannelMembers(
            streamUuid = "stream",
            bindings = emptyList(),
            users = listOf(user("first", "Анна"), user("second", "Борис")),
        )

        assertEquals(2, result.size)
        assertEquals("owner", result.first().role)
        assertTrue(result.all(ChannelMember::isMockMembership))
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
