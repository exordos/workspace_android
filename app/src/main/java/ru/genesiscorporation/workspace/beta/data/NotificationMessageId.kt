package ru.genesiscorporation.workspace.beta.data

/** Bouncer sends non-negative decimal integers, including values wider than Android's Int ID. */
internal class NotificationMessageId private constructor(val value: String) {
    val identity = "workspace-message:$value"
    private val legacyId = value.toIntOrNull()
    // Preserve cancellation of notifications posted by older app versions.
    val androidId = legacyId ?: 0
    val tag = if (legacyId == null) identity else null

    companion object {
        fun parse(raw: String?): NotificationMessageId? {
            val digits = raw?.trim()?.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
                ?: return null
            return NotificationMessageId(digits.trimStart('0').ifEmpty { "0" })
        }
    }
}
