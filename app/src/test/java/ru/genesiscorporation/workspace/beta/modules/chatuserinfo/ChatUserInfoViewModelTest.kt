package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData

class ChatUserInfoViewModelTest {
    @Test
    fun `shared channels follow selected user bindings`() {
        val streams = listOf(
            stream("shared", private = false),
            stream("other", private = false),
        )
        val bindings = listOf(
            binding("shared", "selected"),
            binding("other", "someone-else"),
        )

        val result = resolveSharedChannels("selected", streams, bindings)

        assertEquals(listOf("shared"), result.map(Stream::uuid))
    }

    @Test
    fun `channels are not fabricated when membership data is absent`() {
        val streams = listOf(
            stream("public", private = false),
            stream("direct", private = true),
        )

        val result = resolveSharedChannels("selected", streams, emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `channels stay empty while memberships load`() {
        assertTrue(
            resolveSharedChannels(
                userUuid = "selected",
                streams = listOf(stream("public", private = false)),
                bindings = null,
            ).isEmpty(),
        )
    }

    private fun stream(uuid: String, private: Boolean) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = "2026-07-26T00:00:00Z",
        name = uuid,
        isPrivate = private,
        color = 0xFF8138,
    )

    private fun binding(streamUuid: String, userUuid: String) =
        StreamBindingResponseData(
            uuid = "$streamUuid-$userUuid",
            streamUuid = streamUuid,
            userUuid = userUuid,
            whoUuid = "actor",
        )
}
