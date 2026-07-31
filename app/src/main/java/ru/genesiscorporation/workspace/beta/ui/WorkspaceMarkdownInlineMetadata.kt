package ru.genesiscorporation.workspace.beta.ui

import android.content.Context
import ru.genesiscorporation.workspace.beta.R
import java.io.BufferedReader
import java.util.Locale

internal data class WorkspaceMentionCandidate(
    val userUuid: String,
    val displayText: String,
    val username: String,
)

internal data class WorkspaceMentionTarget(
    val userUuid: String,
    val displayText: String,
)

internal class WorkspaceMentionCatalog private constructor(
    private val targetsByUuid: Map<String, WorkspaceMentionTarget>,
    private val targetsByLookup: Map<String, WorkspaceMentionTarget>,
) {
    fun resolve(value: String): WorkspaceMentionTarget? {
        val lookup = normalizeMentionLookup(value)
        if (lookup.isEmpty()) return null
        return targetsByLookup[lookup]
    }

    fun resolveUuid(value: String): WorkspaceMentionTarget? {
        val uuid = canonicalWorkspaceMentionUuid(value) ?: return null
        return targetsByUuid[uuid]
    }

    companion object {
        val Empty = WorkspaceMentionCatalog(
            targetsByUuid = emptyMap(),
            targetsByLookup = emptyMap(),
        )

        fun from(
            candidates: Collection<WorkspaceMentionCandidate>,
        ): WorkspaceMentionCatalog {
            if (candidates.isEmpty()) return Empty

            val targetsByUuid = candidates
                .mapNotNull { candidate ->
                    val uuid = canonicalWorkspaceMentionUuid(
                        candidate.userUuid,
                    ) ?: return@mapNotNull null
                    val displayText =
                        boundedMentionDisplayText(candidate.displayText)
                            ?: boundedMentionDisplayText(candidate.username)
                    WorkspaceMentionTarget(
                        userUuid = uuid,
                        displayText = displayText ?: uuid,
                    )
                }
                .groupBy(WorkspaceMentionTarget::userUuid)
                .mapNotNull { (uuid, targets) ->
                    targets
                        .singleOrNull()
                        ?.let { uuid to it }
                }
                .toMap()

            val candidatesByLookup =
                mutableMapOf<String, MutableMap<String, WorkspaceMentionTarget>>()
            candidates.forEach { candidate ->
                val uuid = canonicalWorkspaceMentionUuid(
                    candidate.userUuid,
                ) ?: return@forEach
                val target = targetsByUuid[uuid] ?: return@forEach
                listOf(
                    uuid,
                    candidate.displayText,
                    candidate.username,
                ).forEach { source ->
                    val lookup = normalizeMentionLookup(source)
                    if (lookup.isNotEmpty()) {
                        candidatesByLookup
                            .getOrPut(lookup, ::linkedMapOf)
                            .putIfAbsent(uuid, target)
                    }
                }
            }
            val uniqueTargetsByLookup = candidatesByLookup
                .mapNotNull { (lookup, targets) ->
                    targets.values.singleOrNull()?.let { lookup to it }
                }
                .toMap()

            return WorkspaceMentionCatalog(
                targetsByUuid = targetsByUuid,
                targetsByLookup = uniqueTargetsByLookup,
            )
        }
    }
}

internal fun renderWorkspaceMarkdownInlineMetadata(
    markdown: String,
    mentionCatalog: WorkspaceMentionCatalog = WorkspaceMentionCatalog.Empty,
    resolveEmoji: (String) -> String? = { null },
): String {
    if (
        markdown.isEmpty() ||
        (
            '@' !in markdown &&
                ':' !in markdown &&
                "urn:user:" !in markdown.lowercase(Locale.ROOT)
            )
    ) {
        return markdown
    }

    val output = StringBuilder(markdown.length)
    var cursor = 0
    var atLineStart = true
    var blockFence: String? = null
    var inlineCodeFenceLength = 0
    var linkDestinationDepth = 0
    var inAngleDestination = false
    var replacements = 0

    while (cursor < markdown.length) {
        if (atLineStart) {
            val lineEnd = markdown.indexOf('\n', cursor)
                .takeIf { it >= 0 }
                ?: markdown.length
            val line = markdown.substring(cursor, lineEnd)
            val activeFence = blockFence
            if (activeFence != null) {
                output.append(line)
                if (lineEnd < markdown.length) output.append('\n')
                if (line.isMarkdownFenceClose(activeFence)) {
                    blockFence = null
                }
                cursor = (lineEnd + 1).coerceAtMost(markdown.length)
                atLineStart = true
                inlineCodeFenceLength = 0
                linkDestinationDepth = 0
                inAngleDestination = false
                continue
            }
            val openingFence = line.markdownMetadataFenceOpen()
            if (openingFence != null || line.isIndentedMarkdownCode()) {
                if (openingFence != null) blockFence = openingFence
                output.append(line)
                if (lineEnd < markdown.length) output.append('\n')
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
            output.append(character)
            cursor++
            atLineStart = true
            linkDestinationDepth = 0
            inAngleDestination = false
            continue
        }
        if (character == '\\') {
            output.append(character)
            cursor++
            if (cursor < markdown.length) {
                output.append(markdown[cursor])
                cursor++
            }
            atLineStart = false
            continue
        }
        if (character == '`') {
            val runLength = markdown.countMetadataRun(cursor, '`')
            output.append(markdown, cursor, cursor + runLength)
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
            output.append(character)
            cursor++
            atLineStart = false
            continue
        }
        if (inAngleDestination) {
            output.append(character)
            if (character == '>') inAngleDestination = false
            cursor++
            atLineStart = false
            continue
        }
        if (linkDestinationDepth > 0) {
            output.append(character)
            when (character) {
                '(' -> linkDestinationDepth++
                ')' -> linkDestinationDepth--
            }
            cursor++
            atLineStart = false
            continue
        }
        if (replacements >= MAX_INLINE_METADATA_REPLACEMENTS) {
            output.append(markdown, cursor, markdown.length)
            break
        }

        if (character == '!' && markdown.startsWith("![", cursor)) {
            val imageEnd = markdown.markdownImageEnd(cursor)
            if (imageEnd != null) {
                output.append(markdown, cursor, imageEnd)
                cursor = imageEnd
                atLineStart = false
                continue
            }
        }
        if (character == '[') {
            val userLink = markdown.parseWorkspaceUserLinkAt(cursor)
            if (userLink != null) {
                output.append(
                    renderWorkspaceMentionLink(
                        target = mentionCatalog.resolveUuid(userLink.userUuid),
                        fallbackDisplayText = userLink.label,
                        userUuid = userLink.userUuid,
                    ),
                )
                replacements++
                cursor = userLink.endIndex
                atLineStart = false
                continue
            }
        }
        if (character == '<') {
            val canonicalMention =
                CANONICAL_WORKSPACE_MENTION.find(markdown, cursor)
                    ?.takeIf { it.range.first == cursor }
            if (canonicalMention != null) {
                val uuid = canonicalWorkspaceMentionUuid(
                    canonicalMention.groupValues[1],
                )
                if (uuid != null) {
                    output.append(
                        renderWorkspaceMentionLink(
                            target = mentionCatalog.resolveUuid(uuid),
                            fallbackDisplayText = uuid,
                            userUuid = uuid,
                        ),
                    )
                    replacements++
                    cursor = canonicalMention.range.last + 1
                    atLineStart = false
                    continue
                }
            }
            val angleEnd = markdown.indexOf('>', cursor + 1)
                .takeIf { it >= 0 }
            val lineEnd = markdown.indexOf('\n', cursor + 1)
                .takeIf { it >= 0 }
                ?: markdown.length
            if (angleEnd != null && angleEnd < lineEnd) {
                output.append(character)
                inAngleDestination = true
                cursor++
                atLineStart = false
                continue
            }
        }
        if (
            character == ']' &&
            cursor + 1 < markdown.length &&
            markdown[cursor + 1] == '('
        ) {
            output.append("](")
            linkDestinationDepth = 1
            cursor += 2
            atLineStart = false
            continue
        }
        if (character == '@' && markdown.canStartWorkspaceMention(cursor)) {
            val strongMention = markdown.parseStrongMentionAt(cursor)
            if (strongMention != null) {
                val displayText = normalizeMentionDisplayText(
                    strongMention.displayText,
                )
                val target = mentionCatalog.resolve(displayText)
                output.append(
                    if (target == null) {
                        escapeMarkdownPlainText("@$displayText")
                    } else {
                        renderWorkspaceMentionLink(
                            target = target,
                            fallbackDisplayText = displayText,
                            userUuid = target.userUuid,
                        )
                    },
                )
                replacements++
                cursor = strongMention.endIndex
                atLineStart = false
                continue
            }
            val plainMention = PLAIN_WORKSPACE_MENTION.find(markdown, cursor)
                ?.takeIf { it.range.first == cursor }
            if (plainMention != null) {
                val sourceText = plainMention.groupValues[1]
                val target = mentionCatalog.resolve(sourceText)
                if (target != null) {
                    output.append(
                        renderWorkspaceMentionLink(
                            target = target,
                            fallbackDisplayText = sourceText,
                            userUuid = target.userUuid,
                        ),
                    )
                    replacements++
                } else {
                    output.append(plainMention.value)
                }
                cursor = plainMention.range.last + 1
                atLineStart = false
                continue
            }
        }
        if (character == ':') {
            val shortcode = EMOJI_SHORTCODE.find(markdown, cursor)
                ?.takeIf { it.range.first == cursor }
            if (shortcode != null) {
                val unicode = resolveEmoji(shortcode.groupValues[1])
                if (unicode != null) {
                    output.append(unicode)
                    replacements++
                } else {
                    output.append(shortcode.value)
                }
                cursor = shortcode.range.last + 1
                atLineStart = false
                continue
            }
        }

        output.append(character)
        cursor++
        atLineStart = false
    }

    return output.toString()
}

internal fun parseWorkspaceEmojiCatalog(
    reader: BufferedReader,
): Map<String, String> = buildMap {
    reader.useLines { lines ->
        lines.forEach { line ->
            if (line.isBlank() || line.startsWith('#')) return@forEach
            val separator = line.indexOf('\t')
            if (separator <= 0 || separator == line.lastIndex) return@forEach
            val shortcode = normalizeEmojiShortcode(line.substring(0, separator))
            val unicode = line.substring(separator + 1)
            if (
                shortcode.isNotEmpty() &&
                unicode.isNotEmpty() &&
                '\t' !in unicode
            ) {
                put(shortcode, unicode)
            }
        }
    }
}

internal object WorkspaceEmojiShortcodeCatalog {
    @Volatile
    private var cachedEntries: Map<String, String>? = null

    fun resolver(context: Context): (String) -> String? {
        val entries = entries(context.applicationContext)
        return { rawShortcode ->
            entries[normalizeEmojiShortcode(rawShortcode)]
        }
    }

    private fun entries(context: Context): Map<String, String> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries ?: runCatching {
                context.resources
                    .openRawResource(R.raw.workspace_emoji_shortcodes_v17)
                    .bufferedReader(Charsets.UTF_8)
                    .let(::parseWorkspaceEmojiCatalog)
            }.getOrDefault(emptyMap()).also { cachedEntries = it }
        }
    }
}

private data class ParsedWorkspaceUserLink(
    val label: String,
    val userUuid: String,
    val endIndex: Int,
)

private data class ParsedStrongMention(
    val displayText: String,
    val endIndex: Int,
)

private fun String.parseWorkspaceUserLinkAt(
    startIndex: Int,
): ParsedWorkspaceUserLink? {
    if (getOrNull(startIndex) != '[') return null
    var cursor = startIndex + 1
    var labelEnd = -1
    while (cursor < length && cursor - startIndex <= MAX_MENTION_SOURCE_CHARS) {
        when (this[cursor]) {
            '\n' -> return null
            '\\' -> cursor += 2
            ']' -> {
                labelEnd = cursor
                break
            }
            else -> cursor++
        }
    }
    if (
        labelEnd < 0 ||
        getOrNull(labelEnd + 1) != '('
    ) {
        return null
    }
    val destinationEnd = indexOf(')', labelEnd + 2)
    if (destinationEnd < 0) return null
    val destination = substring(labelEnd + 2, destinationEnd)
    val match = WORKSPACE_USER_URN.matchEntire(destination) ?: return null
    val uuid = canonicalWorkspaceMentionUuid(match.groupValues[1]) ?: return null
    return ParsedWorkspaceUserLink(
        label = substring(startIndex + 1, labelEnd)
            .unescapeMarkdownLabel(),
        userUuid = uuid,
        endIndex = destinationEnd + 1,
    )
}

private fun String.markdownImageEnd(startIndex: Int): Int? {
    if (!startsWith("![", startIndex)) return null
    var cursor = startIndex + 2
    while (cursor < length) {
        when (this[cursor]) {
            '\n' -> return null
            '\\' -> cursor += 2
            ']' -> {
                if (getOrNull(cursor + 1) != '(') return null
                var destinationCursor = cursor + 2
                var depth = 1
                while (destinationCursor < length) {
                    when (this[destinationCursor]) {
                        '\\' -> destinationCursor += 2
                        '(' -> {
                            depth++
                            destinationCursor++
                        }
                        ')' -> {
                            depth--
                            destinationCursor++
                            if (depth == 0) return destinationCursor
                        }
                        '\n' -> return null
                        else -> destinationCursor++
                    }
                }
                return null
            }
            else -> cursor++
        }
    }
    return null
}

private fun String.parseStrongMentionAt(
    startIndex: Int,
): ParsedStrongMention? {
    if (!startsWith("@**", startIndex)) return null
    val contentStart = startIndex + 3
    val end = indexOf("**", contentStart)
    if (
        end <= contentStart ||
        end - contentStart > MAX_MENTION_SOURCE_CHARS ||
        '\n' in substring(contentStart, end)
    ) {
        return null
    }
    val displayText = substring(contentStart, end)
        .unescapeMarkdownLabel()
    return ParsedStrongMention(
        displayText = displayText,
        endIndex = end + 2,
    )
}

private fun String.canStartWorkspaceMention(index: Int): Boolean {
    if (index == 0) return true
    return this[index - 1].isWhitespace() ||
        this[index - 1] in "([{\"'.,!?;:"
}

private fun String.markdownMetadataFenceOpen(): String? =
    MARKDOWN_METADATA_FENCE_OPEN
        .find(this)
        ?.groupValues
        ?.get(1)

private fun String.isMarkdownFenceClose(openingFence: String): Boolean {
    val candidate = replace(MARKDOWN_QUOTE_PREFIX, "")
        .dropWhile { it == ' ' }
        .trimEnd(' ', '\t')
    return candidate.length >= openingFence.length &&
        candidate.all { it == openingFence.first() }
}

private fun String.isIndentedMarkdownCode(): Boolean {
    val candidate = replace(MARKDOWN_QUOTE_PREFIX, "")
    return candidate.startsWith("    ") || candidate.startsWith('\t')
}

private fun String.countMetadataRun(
    startIndex: Int,
    character: Char,
): Int {
    var cursor = startIndex
    while (cursor < length && this[cursor] == character) cursor++
    return cursor - startIndex
}

private fun String.unescapeMarkdownLabel(): String = buildString {
    var cursor = 0
    while (cursor < this@unescapeMarkdownLabel.length) {
        if (
            this@unescapeMarkdownLabel[cursor] == '\\' &&
            cursor + 1 < this@unescapeMarkdownLabel.length
        ) {
            cursor++
        }
        append(this@unescapeMarkdownLabel[cursor])
        cursor++
    }
}

private fun renderWorkspaceMentionLink(
    target: WorkspaceMentionTarget?,
    fallbackDisplayText: String,
    userUuid: String,
): String {
    val displayText = target
        ?.displayText
        ?.takeIf(String::isNotBlank)
        ?: boundedMentionDisplayText(fallbackDisplayText)
        ?: userUuid
    return buildString {
        append('[')
        append(escapeMarkdownLinkText("@$displayText"))
        append("](urn:user:")
        append(userUuid)
        append(')')
    }
}

private fun normalizeMentionDisplayText(value: String): String =
    value
        .replace(MENTION_WHITESPACE, " ")
        .trim()
        .trimStart('@')

private fun boundedMentionDisplayText(value: String): String? =
    normalizeMentionDisplayText(value)
        .takeIf { it.isNotEmpty() && it.length <= MAX_MENTION_DISPLAY_CHARS }

private fun normalizeMentionLookup(value: String): String =
    boundedMentionDisplayText(value)
        ?.lowercase(Locale.ROOT)
        .orEmpty()

private fun normalizeEmojiShortcode(value: String): String =
    value
        .trim()
        .lowercase(Locale.ROOT)
        .trim(':')
        .replace(EMOJI_SHORTCODE_SEPARATOR, "_")
        .replace(EMOJI_SHORTCODE_UNSAFE, "")
        .replace(EMOJI_SHORTCODE_DUPLICATE_UNDERSCORE, "_")
        .trim('_')

private fun canonicalWorkspaceMentionUuid(value: String): String? =
    value
        .lowercase(Locale.ROOT)
        .takeIf { CANONICAL_UUID.matches(it) }

private fun escapeMarkdownPlainText(value: String): String = buildString {
    value.forEach { character ->
        if (character in "\\`*{}[]<>()#+-.!_|") append('\\')
        append(character)
    }
}

private fun escapeMarkdownLinkText(value: String): String = buildString {
    value.forEach { character ->
        if (character in "\\`*_[]<>") append('\\')
        append(character)
    }
}

private val CANONICAL_UUID = Regex(
    """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""",
)
private val CANONICAL_WORKSPACE_MENTION = Regex(
    """<@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-""" +
        """[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>""",
)
private val WORKSPACE_USER_URN = Regex(
    """urn:user:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-""" +
        """[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""",
    RegexOption.IGNORE_CASE,
)
private val PLAIN_WORKSPACE_MENTION = Regex(
    """@([A-Za-z0-9._-]{1,128})""",
)
private val EMOJI_SHORTCODE = Regex(
    """:([A-Za-z0-9_+-]{1,128}):""",
    RegexOption.IGNORE_CASE,
)
private val MARKDOWN_METADATA_FENCE_OPEN = Regex(
    """^ {0,3}(?:> ?)* {0,3}(`{3,}|~{3,})""",
)
private val MARKDOWN_QUOTE_PREFIX = Regex("""^ {0,3}(?:> ?)+""")
private val MENTION_WHITESPACE = Regex("""\s+""")
private val EMOJI_SHORTCODE_SEPARATOR = Regex("""[\s-]+""")
private val EMOJI_SHORTCODE_UNSAFE = Regex("""[^a-z0-9_+]+""")
private val EMOJI_SHORTCODE_DUPLICATE_UNDERSCORE = Regex("""_+""")
private const val MAX_MENTION_SOURCE_CHARS = 512
private const val MAX_MENTION_DISPLAY_CHARS = 200
private const val MAX_INLINE_METADATA_REPLACEMENTS = 512
