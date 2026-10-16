package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class MessageTimeFormattingTest {
    private val now = Instant.parse("2026-01-10T12:00:00Z")
    private fun relative(seconds: Long) = pastEpochSecondsToRelativeRu(now.epochSecond - seconds, now)

    @Test
    fun futureAndSubMinuteTimestampsDoNotProduceNegativeAges() {
        assertEquals("только что", relative(-60))
        assertEquals("только что", relative(0))
        assertEquals("только что", relative(59))
    }

    @Test
    fun minuteDeclensionHandlesOneFewManyAndTeens() {
        for ((minutes, text) in mapOf(1 to "1 минуту назад", 2 to "2 минуты назад",
            5 to "5 минут назад", 11 to "11 минут назад", 14 to "14 минут назад",
            21 to "21 минуту назад", 22 to "22 минуты назад", 59 to "59 минут назад")) {
            assertEquals(text, relative(minutes * 60L))
        }
    }

    @Test
    fun hourAndDayBoundariesUseTheirOwnUnits() {
        for ((seconds, text) in mapOf(3_600L to "1 час назад", 7_200L to "2 часа назад",
            18_000L to "5 часов назад", 39_600L to "11 часов назад", 86_399L to "23 часа назад",
            86_400L to "1 день назад", 172_800L to "2 дня назад", 518_400L to "6 дней назад")) {
            assertEquals(text, relative(seconds))
        }
    }

    @Test
    fun weekBoundarySwitchesToCalendarDateInTheDeviceZone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("3 янв. 2026", relative(604_800))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun messageTimeUsesEpochSecondsAndExplicitZoneAcrossMidnight() {
        val timestamp = Instant.parse("2026-01-01T23:30:00Z").epochSecond

        assertEquals("23:30", timestamp.formatHHmm(ZoneId.of("UTC")))
        assertEquals("02:30", timestamp.formatHHmm(ZoneId.of("Europe/Moscow")))
        assertEquals("18:30", timestamp.formatHHmm(ZoneId.of("America/New_York")))
    }

    @Test
    fun messageTimeFollowsDaylightSavingRulesOfTheSelectedZone() {
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals("01:59", Instant.parse("2026-03-29T00:59:00Z").epochSecond.formatHHmm(zone))
        assertEquals("03:00", Instant.parse("2026-03-29T01:00:00Z").epochSecond.formatHHmm(zone))
    }
}
