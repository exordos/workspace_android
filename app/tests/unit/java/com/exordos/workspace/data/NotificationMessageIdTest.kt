package com.exordos.workspace.data

import org.junit.Assert.*
import org.junit.Test

class NotificationMessageIdTest {
    @Test fun rejectsMissingMalformedAndNegativeIds() {
        listOf(null, "", " ", "not-a-number", "-1", "+1", "1.5", "1e3", "١٢", "12 34",
            "00000000-0000-4000-8000-000000000001").forEach {
            assertNull("Unexpected accepted ID: $it", NotificationMessageId.parse(it))
        }
    }

    @Test fun preservesLegacyNotificationKeysIncludingZero() {
        listOf("0", "1", "2147483647").forEach { raw ->
            val id = requireNotNull(NotificationMessageId.parse(raw))
            assertNull(id.tag)
            assertEquals(raw.toInt(), id.androidId)
        }
    }

    @Test fun canonicalizesProducerIntegersWithoutTruncationOrHashCollisions() {
        val first = requireNotNull(NotificationMessageId.parse(" 0002147483648 "))
        val second = requireNotNull(NotificationMessageId.parse("9223372036854775808"))
        assertEquals("2147483648", first.value)
        assertEquals("workspace-message:2147483648", first.tag)
        assertEquals(0, first.androidId)
        assertNotEquals(first.tag, second.tag)
        assertNotEquals(first.identity, second.identity)
        assertEquals(first.identity, NotificationMessageId.parse("2147483648")?.identity)
        assertEquals("0", NotificationMessageId.parse("000")?.value)
    }
}
