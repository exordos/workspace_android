package ru.genesiscorporation.workspace.beta.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.time.format.DateTimeParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceDateTimeTest {
    @Test fun `API and pending timestamps retain their absolute time and precision`() {
        val cases = mapOf(
            "2026-10-30T11:39:50.168+03:00" to "2026-10-30T08:39:50.168Z",
            "2026-10-30T08:39:50.168Z" to "2026-10-30T08:39:50.168Z",
            "2026-10-30T03:39:50.168-05:00" to "2026-10-30T08:39:50.168Z",
            "2026-10-30T00:15:00+05:45" to "2026-10-29T18:30:00Z",
            "2026-10-30T11:39:50.123456789+03:00" to "2026-10-30T08:39:50.123456789Z",
            "2026-10-30T08:39:50+00:00" to "2026-10-30T08:39:50Z",
        )
        cases.forEach { (input, expected) ->
            assertEquals(input, Instant.parse(expected), WorkspaceDateTime.parse(input))
        }
    }

    @Test fun `API output is UTC regardless of device zone and retains nanoseconds`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
            val instant = WorkspaceDateTime.parse("2026-10-30T11:39:50.123456789+03:00")
            assertEquals("2026-10-30T08:39:50.123456789Z", WorkspaceDateTime.apiTimestamp(instant))
            assertEquals(instant, WorkspaceDateTime.parse(WorkspaceDateTime.apiTimestamp(instant)))
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun `calendar days and displayed times use the selected zone not the encoded date`() {
        val timestamp = "2026-10-30T00:15:00+05:45"
        val utc = ZoneId.of("UTC")
        val kathmandu = ZoneId.of("Asia/Kathmandu")
        assertEquals("2026-10-29", WorkspaceDateTime.localDate(timestamp, utc).toString())
        assertEquals("2026-10-30", WorkspaceDateTime.localDate(timestamp, kathmandu).toString())
        assertEquals("18:30", WorkspaceDateTime.formatTime(timestamp, utc))
        assertEquals("00:15", WorkspaceDateTime.formatTime(timestamp, kathmandu))
        assertEquals("29 Oct", WorkspaceDateTime.formatDay(timestamp, Locale.ENGLISH, utc))
    }

    @Test fun `a zone change is reflected without restarting the process`() {
        val previous = TimeZone.getDefault()
        val timestamp = "2026-10-29T21:30:00Z"
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("21:30", WorkspaceDateTime.formatTime(timestamp))
            assertEquals("2026-10-29", WorkspaceDateTime.localDate(timestamp).toString())
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
            assertEquals("00:30", WorkspaceDateTime.formatTime(timestamp))
            assertEquals("2026-10-30", WorkspaceDateTime.localDate(timestamp).toString())
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun `key receipts use epoch milliseconds rather than seconds`() {
        val instant = WorkspaceDateTime.parse("2026-10-29T21:30:00Z")
        assertEquals("30.10.2026 00:30", WorkspaceDateTime.formatEpochMillis(
            instant.toEpochMilli(), "dd.MM.yyyy HH:mm", ZoneId.of("Europe/Moscow")))
    }

    @Test fun `invalid and offset-free dates retain the parsing error contract`() {
        listOf("", "invalid", "2026-10-30T11:39:50", "2026-02-30T11:39:50Z").forEach { input ->
            assertThrows(input, DateTimeParseException::class.java) { WorkspaceDateTime.parse(input) }
        }
    }
}
