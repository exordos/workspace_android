package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

class CreatedStreamRecoveryTest {
    @Test
    fun `response default topic wins without a follow-up lookup`() {
        assertEquals(
            "response-default",
            resolveCreatedStreamDefaultTopicUuid(
                responseDefaultTopicUuid = "response-default",
                topics = listOf(topic("listed-default", isDefault = true)),
                refreshedDefaultTopicUuid = "refreshed-default",
            ),
        )
    }

    @Test
    fun `default topic from loaded topics recovers a partial create response`() {
        assertEquals(
            "listed-default",
            resolveCreatedStreamDefaultTopicUuid(
                responseDefaultTopicUuid = null,
                topics = listOf(
                    topic("regular", isDefault = false),
                    topic("listed-default", isDefault = true),
                ),
                refreshedDefaultTopicUuid = null,
            ),
        )
    }

    @Test
    fun `refreshed stream recovers when topic loading failed`() {
        assertEquals(
            "refreshed-default",
            resolveCreatedStreamDefaultTopicUuid(
                responseDefaultTopicUuid = " ",
                topics = emptyList(),
                refreshedDefaultTopicUuid = "refreshed-default",
            ),
        )
    }

    @Test
    fun `ambiguous default topics do not select an arbitrary destination`() {
        assertNull(
            resolveCreatedStreamDefaultTopicUuid(
                responseDefaultTopicUuid = null,
                topics = listOf(
                    topic("first", isDefault = true),
                    topic("second", isDefault = true),
                ),
                refreshedDefaultTopicUuid = null,
            ),
        )
    }

    private fun topic(
        uuid: String,
        isDefault: Boolean,
    ) = TopicsResponseData(
        uuid = uuid,
        name = uuid,
        streamUuid = "stream",
        updatedAt = "2026-07-30T00:00:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = isDefault,
    )
}
