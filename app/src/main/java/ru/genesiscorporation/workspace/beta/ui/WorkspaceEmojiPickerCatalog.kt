package ru.genesiscorporation.workspace.beta.ui

import java.util.Locale

internal data class WorkspaceEmojiPickerEntry(
    val glyph: String,
    val primaryShortcode: String,
    val aliases: List<String>,
)

internal fun buildWorkspaceEmojiPickerEntries(
    shortcodeToGlyph: Map<String, String>,
): List<WorkspaceEmojiPickerEntry> =
    shortcodeToGlyph
        .entries
        .filter { (shortcode, glyph) ->
            shortcode.isNotBlank() && glyph.isNotBlank()
        }
        .groupBy(
            keySelector = Map.Entry<String, String>::value,
            valueTransform = Map.Entry<String, String>::key,
        )
        .map { (glyph, aliases) ->
            val sortedAliases = aliases
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .sortedWith(
                    compareBy<String>(
                        { it.contains("_tone") },
                        String::length,
                        { it },
                    ),
                )
                .toList()
            WorkspaceEmojiPickerEntry(
                glyph = glyph,
                primaryShortcode = sortedAliases.first(),
                aliases = sortedAliases,
            )
        }
        .sortedWith(
            compareBy(
                WorkspaceEmojiPickerEntry::primaryShortcode,
                WorkspaceEmojiPickerEntry::glyph,
            ),
        )

internal fun filterWorkspaceEmojiPickerEntries(
    entries: List<WorkspaceEmojiPickerEntry>,
    query: String,
): List<WorkspaceEmojiPickerEntry> {
    val rawQuery = query.trim()
    if (rawQuery.isEmpty()) return entries
    val normalizedQuery = rawQuery
        .lowercase(Locale.ROOT)
        .trim(':')
        .replace(EMOJI_PICKER_QUERY_SEPARATOR, "_")
        .replace(EMOJI_PICKER_QUERY_UNSAFE, "")
        .replace(EMOJI_PICKER_DUPLICATE_UNDERSCORE, "_")
        .trim('_')
    if (normalizedQuery.isEmpty()) {
        return entries.filter { rawQuery in it.glyph }
    }
    return entries.filter { entry ->
        rawQuery in entry.glyph ||
            entry.aliases.any { alias -> normalizedQuery in alias }
    }
}

internal fun workspaceReactionDisplayText(
    emojiName: String,
    resolveShortcode: (String) -> String?,
): String {
    val trimmed = emojiName.trim()
    if (trimmed.isEmpty()) return ""
    resolveShortcode(trimmed)?.let { return it }
    if (trimmed.any { it.code > ASCII_LAST_CODE_POINT }) return trimmed
    return ":${trimmed.trim(':')}:"
}

private const val ASCII_LAST_CODE_POINT = 0x7F
private val EMOJI_PICKER_QUERY_SEPARATOR = Regex("""[\s-]+""")
private val EMOJI_PICKER_QUERY_UNSAFE = Regex("""[^a-z0-9_+]""")
private val EMOJI_PICKER_DUPLICATE_UNDERSCORE = Regex("""_+""")
