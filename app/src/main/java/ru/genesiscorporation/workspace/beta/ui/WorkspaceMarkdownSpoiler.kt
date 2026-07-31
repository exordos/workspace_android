package ru.genesiscorporation.workspace.beta.ui

internal sealed interface WorkspaceBlockSpoilerSegment {
    data class Text(val markdown: String) : WorkspaceBlockSpoilerSegment

    data class Spoiler(
        val id: Int,
        val header: String?,
        val bodyMarkdown: String,
    ) : WorkspaceBlockSpoilerSegment
}

internal data class WorkspaceInlineSpoilerDocument(
    val segments: List<WorkspaceInlineSpoilerSegment>,
) {
    val spoilerIds: Set<Int> = segments
        .filterIsInstance<WorkspaceInlineSpoilerSegment.Spoiler>()
        .mapTo(linkedSetOf(), WorkspaceInlineSpoilerSegment.Spoiler::id)

    fun render(
        revealedIds: Set<Int>,
        showLabel: String,
        hideLabel: String,
    ): String = buildString {
        segments.forEach { segment ->
            when (segment) {
                is WorkspaceInlineSpoilerSegment.Text -> {
                    append(segment.markdown)
                }

                is WorkspaceInlineSpoilerSegment.Spoiler -> {
                    if (segment.id in revealedIds) {
                        append('[')
                        append(escapeMarkdownLinkLabel(hideLabel))
                        append("](")
                        append(INLINE_SPOILER_URN_PREFIX)
                        append(segment.id)
                        append(") ")
                        append(segment.markdown)
                    } else {
                        append('[')
                        append(escapeMarkdownLinkLabel(showLabel))
                        append("](")
                        append(INLINE_SPOILER_URN_PREFIX)
                        append(segment.id)
                        append(')')
                    }
                }
            }
        }
    }
}

internal sealed interface WorkspaceInlineSpoilerSegment {
    data class Text(val markdown: String) : WorkspaceInlineSpoilerSegment

    data class Spoiler(
        val id: Int,
        val markdown: String,
    ) : WorkspaceInlineSpoilerSegment
}

internal fun parseWorkspaceBlockSpoilers(
    markdown: String,
): List<WorkspaceBlockSpoilerSegment> {
    val normalized = markdown
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    val lines = normalized.split('\n')
    val result = mutableListOf<WorkspaceBlockSpoilerSegment>()
    val text = StringBuilder()
    var spoilerId = 0
    var lineIndex = 0
    var ordinaryFence: String? = null

    fun appendTextLine(line: String, includeLineBreak: Boolean) {
        text.append(line)
        if (includeLineBreak) text.append('\n')
    }

    fun flushText() {
        if (text.isNotEmpty()) {
            result += WorkspaceBlockSpoilerSegment.Text(text.toString())
            text.clear()
        }
    }

    while (lineIndex < lines.size) {
        val line = lines[lineIndex]
        val activeOrdinaryFence = ordinaryFence
        if (activeOrdinaryFence != null) {
            appendTextLine(line, lineIndex < lines.lastIndex)
            if (isClosingFence(line, activeOrdinaryFence)) {
                ordinaryFence = null
            }
            lineIndex++
            continue
        }

        val opening = SPOILER_FENCE_OPEN.matchEntire(line)
        if (opening == null || spoilerId >= MAX_SPOILERS_PER_MESSAGE) {
            ordinaryFence = line.markdownOpeningFence()
            appendTextLine(line, lineIndex < lines.lastIndex)
            lineIndex++
            continue
        }

        val fence = opening.groupValues[1]
        val closingIndex = (lineIndex + 1..lines.lastIndex).firstOrNull {
            isClosingFence(lines[it], fence)
        }
        if (closingIndex == null) {
            ordinaryFence = fence
            appendTextLine(line, lineIndex < lines.lastIndex)
            lineIndex++
            continue
        }

        flushText()
        val header = opening.groupValues[2]
            .trim()
            .takeIf(String::isNotEmpty)
            ?.boundedSpoilerHeader()
        result += WorkspaceBlockSpoilerSegment.Spoiler(
            id = spoilerId++,
            header = header,
            bodyMarkdown = lines
                .subList(lineIndex + 1, closingIndex)
                .joinToString(separator = "\n"),
        )
        lineIndex = closingIndex + 1
        if (lineIndex <= lines.lastIndex) {
            text.append('\n')
        }
    }
    flushText()
    return result.ifEmpty {
        listOf(WorkspaceBlockSpoilerSegment.Text(normalized))
    }
}

internal fun parseWorkspaceInlineSpoilers(
    markdown: String,
): WorkspaceInlineSpoilerDocument {
    val segments = mutableListOf<WorkspaceInlineSpoilerSegment>()
    val text = StringBuilder()
    var spoilerId = 0
    var cursor = 0
    var fence: String? = null
    var atLineStart = true
    var inlineCodeFenceLength = 0
    var linkDestinationDepth = 0
    var inAngleDestination = false

    fun flushText() {
        if (text.isNotEmpty()) {
            segments += WorkspaceInlineSpoilerSegment.Text(text.toString())
            text.clear()
        }
    }

    while (cursor < markdown.length) {
        if (atLineStart) {
            val lineEnd = markdown.indexOf('\n', cursor)
                .takeIf { it >= 0 }
                ?: markdown.length
            val line = markdown.substring(cursor, lineEnd)
            val currentFence = fence
            if (currentFence != null) {
                text.append(line)
                if (lineEnd < markdown.length) text.append('\n')
                if (isClosingFence(line, currentFence)) fence = null
                cursor = (lineEnd + 1).coerceAtMost(markdown.length)
                atLineStart = true
                inlineCodeFenceLength = 0
                linkDestinationDepth = 0
                inAngleDestination = false
                continue
            }
            val openingFence = MARKDOWN_FENCE_OPEN.find(line)
                ?.takeIf { it.range.first == 0 }
                ?.groupValues
                ?.get(1)
            if (openingFence != null) {
                fence = openingFence
                text.append(line)
                if (lineEnd < markdown.length) text.append('\n')
                cursor = (lineEnd + 1).coerceAtMost(markdown.length)
                atLineStart = true
                inlineCodeFenceLength = 0
                linkDestinationDepth = 0
                inAngleDestination = false
                continue
            }
        }

        val character = markdown[cursor]
        if (character == '\n') {
            text.append(character)
            cursor++
            atLineStart = true
            inlineCodeFenceLength = 0
            linkDestinationDepth = 0
            inAngleDestination = false
            continue
        }
        if (character == '\\') {
            text.append(character)
            cursor++
            if (cursor < markdown.length) {
                text.append(markdown[cursor])
                cursor++
            }
            atLineStart = false
            continue
        }
        if (character == '`') {
            val runLength = markdown.countRun(cursor, '`')
            text.append(markdown, cursor, cursor + runLength)
            inlineCodeFenceLength = when {
                inlineCodeFenceLength == 0 -> runLength
                inlineCodeFenceLength == runLength -> 0
                else -> inlineCodeFenceLength
            }
            cursor += runLength
            atLineStart = false
            continue
        }
        if (inlineCodeFenceLength > 0) {
            text.append(character)
            cursor++
            atLineStart = false
            continue
        }
        if (inAngleDestination) {
            text.append(character)
            if (character == '>') inAngleDestination = false
            cursor++
            atLineStart = false
            continue
        }
        if (linkDestinationDepth > 0) {
            text.append(character)
            when (character) {
                '(' -> linkDestinationDepth++
                ')' -> linkDestinationDepth--
            }
            cursor++
            atLineStart = false
            continue
        }
        if (character == '<') {
            text.append(character)
            inAngleDestination = true
            cursor++
            atLineStart = false
            continue
        }
        if (
            character == ']' &&
            cursor + 1 < markdown.length &&
            markdown[cursor + 1] == '('
        ) {
            text.append("](")
            linkDestinationDepth = 1
            cursor += 2
            atLineStart = false
            continue
        }
        if (
            spoilerId < MAX_SPOILERS_PER_MESSAGE &&
            markdown.isExactDoublePipeAt(cursor) &&
            (cursor == 0 || markdown[cursor - 1] != '\\')
        ) {
            val close = findInlineSpoilerClose(markdown, cursor + 2)
            if (close != null && close > cursor + 2) {
                flushText()
                segments += WorkspaceInlineSpoilerSegment.Spoiler(
                    id = spoilerId++,
                    markdown = markdown.substring(cursor + 2, close),
                )
                cursor = close + 2
                atLineStart = false
                continue
            }
        }

        text.append(character)
        cursor++
        atLineStart = false
    }
    flushText()
    return WorkspaceInlineSpoilerDocument(
        segments = segments.ifEmpty {
            listOf(WorkspaceInlineSpoilerSegment.Text(markdown))
        },
    )
}

internal fun parseWorkspaceInlineSpoilerUrn(value: String): Int? {
    if (!value.startsWith(INLINE_SPOILER_URN_PREFIX)) return null
    val rawId = value.substring(INLINE_SPOILER_URN_PREFIX.length)
    if (rawId.isEmpty() || rawId.any { !it.isDigit() }) return null
    return rawId.toIntOrNull()?.takeIf { it in 0 until MAX_SPOILERS_PER_MESSAGE }
}

private fun findInlineSpoilerClose(
    markdown: String,
    startIndex: Int,
): Int? {
    var cursor = startIndex
    var inlineCodeFenceLength = 0
    var linkDestinationDepth = 0
    var inAngleDestination = false
    while (cursor < markdown.length) {
        val character = markdown[cursor]
        if (character == '\n') return null
        if (character == '\\') {
            cursor = (cursor + 2).coerceAtMost(markdown.length)
            continue
        }
        if (character == '`') {
            val runLength = markdown.countRun(cursor, '`')
            inlineCodeFenceLength = when {
                inlineCodeFenceLength == 0 -> runLength
                inlineCodeFenceLength == runLength -> 0
                else -> inlineCodeFenceLength
            }
            cursor += runLength
            continue
        }
        if (inlineCodeFenceLength > 0) {
            cursor++
            continue
        }
        if (inAngleDestination) {
            if (character == '>') inAngleDestination = false
            cursor++
            continue
        }
        if (linkDestinationDepth > 0) {
            when (character) {
                '(' -> linkDestinationDepth++
                ')' -> linkDestinationDepth--
            }
            cursor++
            continue
        }
        if (character == '<') {
            inAngleDestination = true
            cursor++
            continue
        }
        if (
            character == ']' &&
            cursor + 1 < markdown.length &&
            markdown[cursor + 1] == '('
        ) {
            linkDestinationDepth = 1
            cursor += 2
            continue
        }
        if (markdown.isExactDoublePipeAt(cursor)) return cursor
        cursor++
    }
    return null
}

private fun isClosingFence(
    line: String,
    openingFence: String,
): Boolean {
    val leadingSpaces = line.takeWhile { it == ' ' }.length
    if (leadingSpaces > 3) return false
    val candidate = line
        .drop(leadingSpaces)
        .trimEnd(' ', '\t')
    return candidate.length >= openingFence.length &&
        candidate.all { it == openingFence.first() }
}

private fun String.boundedSpoilerHeader(): String =
    if (length <= MAX_SPOILER_HEADER_CHARS) {
        this
    } else {
        take(MAX_SPOILER_HEADER_CHARS - 1) + "…"
    }

private fun String.countRun(startIndex: Int, character: Char): Int {
    var cursor = startIndex
    while (cursor < length && this[cursor] == character) cursor++
    return cursor - startIndex
}

private fun String.isExactDoublePipeAt(index: Int): Boolean =
    startsWith("||", index) &&
        (index == 0 || this[index - 1] != '|') &&
        (index + 2 >= length || this[index + 2] != '|')

private fun String.markdownOpeningFence(): String? =
    MARKDOWN_FENCE_OPEN
        .find(this)
        ?.takeIf { it.range.first == 0 }
        ?.groupValues
        ?.get(1)

private fun escapeMarkdownLinkLabel(value: String): String = buildString {
    value.forEach { character ->
        if (character == '\\' || character == '[' || character == ']') {
            append('\\')
        }
        append(character)
    }
}

private val SPOILER_FENCE_OPEN = Regex(
    """^ {0,3}(`{3,}|~{3,})spoiler(?:[ \t]+(.*?))?[ \t]*$""",
    RegexOption.IGNORE_CASE,
)
private val MARKDOWN_FENCE_OPEN = Regex("""^ {0,3}(`{3,}|~{3,})""")
private const val INLINE_SPOILER_URN_PREFIX =
    "urn:workspace-mobile-inline-spoiler:"
private const val MAX_SPOILERS_PER_MESSAGE = 64
private const val MAX_SPOILER_HEADER_CHARS = 200
