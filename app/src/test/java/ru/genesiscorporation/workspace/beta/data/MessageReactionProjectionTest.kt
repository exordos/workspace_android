package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

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
}
