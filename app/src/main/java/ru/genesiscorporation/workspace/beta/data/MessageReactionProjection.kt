package ru.genesiscorporation.workspace.beta.data

internal fun reconcileMessageReactionCounts(
    current: Map<String, Int>,
    emojiName: String,
    baselineCount: Int,
    delta: Int,
): Map<String, Int> {
    if (
        emojiName.isBlank() ||
        delta !in setOf(-1, 1) ||
        baselineCount < 0
    ) {
        return current
    }
    val currentCount = current[emojiName] ?: 0
    if (currentCount != baselineCount) return current
    if (delta > 0 && currentCount == Int.MAX_VALUE) return current
    val nextCount = currentCount + delta
    return if (nextCount <= 0) {
        if (emojiName !in current) current else current - emojiName
    } else {
        current + (emojiName to nextCount)
    }
}

internal fun validatedMessageReactionCounts(
    counts: Map<String, Int>,
): Map<String, Int>? {
    if (counts.size > MAX_MESSAGE_REACTION_KINDS) return null
    if (
        counts.any { (emojiName, count) ->
            emojiName.length !in 1..MAX_MESSAGE_REACTION_NAME_CHARS ||
                emojiName.any(Char::isISOControl) ||
                count <= 0
        }
    ) {
        return null
    }
    return counts.toMap()
}

private const val MAX_MESSAGE_REACTION_KINDS = 256
private const val MAX_MESSAGE_REACTION_NAME_CHARS = 128
