package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class MessageReactionProjectionTest {
    @Test
    fun `add and remove reconcile only an unchanged baseline`() {
        val baseline = mapOf("thumbs_up" to 2, "heart" to 1)
        val added = reconcileMessageReactionCounts(
            current = baseline,
            emojiName = "thumbs_up",
            baselineCount = 2,
            delta = 1,
        )
        val removed = reconcileMessageReactionCounts(
            current = added,
            emojiName = "heart",
            baselineCount = 1,
            delta = -1,
        )

        assertEquals(
            mapOf("thumbs_up" to 3),
            removed,
        )
    }

    @Test
    fun `concurrent authoritative count wins over stale completion`() {
        val authoritative = mapOf("thumbs_up" to 4)

        assertSame(
            authoritative,
            reconcileMessageReactionCounts(
                current = authoritative,
                emojiName = "thumbs_up",
                baselineCount = 2,
                delta = 1,
            ),
        )
        assertSame(
            authoritative,
            reconcileMessageReactionCounts(
                current = authoritative,
                emojiName = "thumbs_up",
                baselineCount = 2,
                delta = -1,
            ),
        )
    }

    @Test
    fun `invalid input and overflow leave projection untouched`() {
        val current = mapOf("max" to Int.MAX_VALUE)

        assertSame(
            current,
            reconcileMessageReactionCounts(
                current,
                "max",
                Int.MAX_VALUE,
                1,
            ),
        )
        assertSame(
            current,
            reconcileMessageReactionCounts(current, "", 0, 1),
        )
        assertSame(
            current,
            reconcileMessageReactionCounts(current, "max", -1, -1),
        )
    }

    @Test
    fun `confirmed reaction counts accept only bounded positive entries`() {
        assertEquals(
            mapOf("thumbs_up" to 2),
            validatedMessageReactionCounts(
                mapOf("thumbs_up" to 2),
            ),
        )
        assertEquals(
            emptyMap<String, Int>(),
            validatedMessageReactionCounts(emptyMap()),
        )
        assertNull(
            validatedMessageReactionCounts(mapOf("thumbs_up" to 0)),
        )
        assertNull(
            validatedMessageReactionCounts(mapOf("bad\nname" to 1)),
        )
        assertNull(
            validatedMessageReactionCounts(
                (0..256).associate { index -> "reaction_$index" to 1 },
            ),
        )
    }

    @Test
    fun `complete reaction users preserve server order`() {
        val first = user(FIRST_USER_UUID, "cassi")
        val second = user(SECOND_USER_UUID, "eugene")

        assertEquals(
            listOf(first, second),
            completeMessageReactionUsers(
                reactionUsers = mapOf(
                    "heart" to listOf(FIRST_USER_UUID, SECOND_USER_UUID),
                ),
                emojiName = "heart",
                expectedCount = 2,
                usersByUuid = mapOf(
                    first.uuid to first,
                    second.uuid to second,
                ),
            ),
        )
    }

    @Test
    fun `reaction users fall back to count unless the list is complete`() {
        val first = user(FIRST_USER_UUID, "cassi")
        val users = mapOf(first.uuid to first)

        assertNull(
            completeMessageReactionUsers(
                reactionUsers = emptyMap(),
                emojiName = "heart",
                expectedCount = 1,
                usersByUuid = users,
            ),
        )
        assertNull(
            completeMessageReactionUsers(
                reactionUsers = mapOf("heart" to listOf(FIRST_USER_UUID)),
                emojiName = "heart",
                expectedCount = 2,
                usersByUuid = users,
            ),
        )
        assertNull(
            completeMessageReactionUsers(
                reactionUsers = mapOf(
                    "heart" to listOf(FIRST_USER_UUID, FIRST_USER_UUID),
                ),
                emojiName = "heart",
                expectedCount = 2,
                usersByUuid = users,
            ),
        )
        assertNull(
            completeMessageReactionUsers(
                reactionUsers = mapOf("heart" to listOf("not-a-uuid")),
                emojiName = "heart",
                expectedCount = 1,
                usersByUuid = users,
            ),
        )
        assertNull(
            completeMessageReactionUsers(
                reactionUsers = mapOf("heart" to listOf(SECOND_USER_UUID)),
                emojiName = "heart",
                expectedCount = 1,
                usersByUuid = users,
            ),
        )
    }

    private fun user(uuid: String, username: String) = UserResponseData(
        username = username,
        uuid = uuid,
        status = "active",
        avatar = "",
    )

    private companion object {
        const val FIRST_USER_UUID = "11111111-1111-4111-8111-111111111111"
        const val SECOND_USER_UUID = "22222222-2222-4222-8222-222222222222"
    }
}
