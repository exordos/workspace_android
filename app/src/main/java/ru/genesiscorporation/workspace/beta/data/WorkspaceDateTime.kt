package ru.genesiscorporation.workspace.beta.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared wall-clock contract for API, cached and locally created dates.
 * Read ISO offset dates (Z or a numeric offset), compare Instants, write UTC Z.
 * Convert to the device zone only for display and calendar-day boundaries.
 * Invalid or offset-free input throws; callers retain their existing error policy.
 * Elapsed-time deadlines and scheduling durations do not belong to this clock.
 */
internal object WorkspaceDateTime {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    // Instant.parse rejects numeric offsets on older supported Android runtimes.
    fun parse(value: String): Instant = OffsetDateTime.parse(value).toInstant()

    fun apiTimestamp(instant: Instant = Instant.now()): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    fun nowEpochMillis(): Long = System.currentTimeMillis()

    fun localDate(value: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        parse(value).atZone(zone).toLocalDate()

    fun formatTime(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.withZone(zone).format(parse(value))

    fun formatEpochSecondsTime(value: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.withZone(zone).format(Instant.ofEpochSecond(value))

    fun formatDay(value: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
        format(parse(value), "d MMM", locale, zone)

    fun formatEpochMillis(value: Long, pattern: String, zone: ZoneId = ZoneId.systemDefault()): String =
        format(Instant.ofEpochMilli(value), pattern, Locale.getDefault(), zone)

    fun relativeTimeRu(
        pastEpochSeconds: Long,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val past = Instant.ofEpochSecond(pastEpochSeconds)
        val seconds = Duration.between(past, now).seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "только что"
            seconds < 3600 -> {
                val minutes = seconds / 60
                "$minutes ${ruPlural(minutes, "минуту", "минуты", "минут")} назад"
            }
            seconds < 86400 -> {
                val hours = seconds / 3600
                "$hours ${ruPlural(hours, "час", "часа", "часов")} назад"
            }
            seconds < 604800 -> {
                val days = seconds / 86400
                "$days ${ruPlural(days, "день", "дня", "дней")} назад"
            }
            else -> format(past, "d MMM yyyy", Locale("ru", "RU"), zone)
        }
    }

    private fun format(instant: Instant, pattern: String, locale: Locale, zone: ZoneId): String =
        DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(instant)

    private fun ruPlural(n: Long, one: String, few: String, many: String): String {
        val nAbs = kotlin.math.abs(n) % 100
        val n10 = nAbs % 10
        return when {
            nAbs in 11L..14L -> many
            n10 == 1L -> one
            n10 in 2L..4L -> few
            else -> many
        }
    }
}
