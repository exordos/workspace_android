package ru.genesiscorporation.workspace.beta.ui

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.parser.Parser

/**
 * Small structural model shared by the message renderer and accessibility.
 *
 * Visual Markdown rendering still belongs to the established renderer, while
 * this model preserves block meaning that Android's single TextView projection
 * otherwise drops from TalkBack output (for example ordered-list numbers).
 */
internal data class WorkspaceMarkdownStructure(
    val blockKinds: List<WorkspaceMarkdownBlockKind>,
    val codeLanguages: List<String>,
    val sourceWasTruncated: Boolean,
) {
    fun accessibilityDescription(
        labels: WorkspaceMarkdownAccessibilityLabels,
    ): String? {
        val descriptions = blockKinds.map { kind ->
            when (kind) {
                WorkspaceMarkdownBlockKind.Quote -> labels.quote
                WorkspaceMarkdownBlockKind.OrderedList -> labels.orderedList
                WorkspaceMarkdownBlockKind.BulletList -> labels.bulletList
                WorkspaceMarkdownBlockKind.CodeBlock -> {
                    codeLanguages.firstOrNull()?.let {
                        "${labels.codeBlock}: $it"
                    } ?: labels.codeBlock
                }
            }
        }.toMutableList()
        if (sourceWasTruncated) {
            descriptions += labels.longMessage
        }
        return descriptions
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(separator = ". ")
    }
}

internal data class WorkspaceMarkdownAccessibilityLabels(
    val quote: String,
    val orderedList: String,
    val bulletList: String,
    val codeBlock: String,
    val longMessage: String,
)

internal enum class WorkspaceMarkdownBlockKind {
    Quote,
    OrderedList,
    BulletList,
    CodeBlock,
}

internal fun analyzeWorkspaceMarkdownStructure(
    markdown: String,
): WorkspaceMarkdownStructure {
    val sourceWasTruncated = markdown.length > MAX_MARKDOWN_STRUCTURE_CHARS
    val normalized = markdown
        .take(MAX_MARKDOWN_STRUCTURE_CHARS)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    if (!STRUCTURAL_MARKDOWN_LINE.containsMatchIn(normalized)) {
        return WorkspaceMarkdownStructure(
            blockKinds = emptyList(),
            codeLanguages = emptyList(),
            sourceWasTruncated = sourceWasTruncated,
        )
    }
    val root = runCatching {
        WORKSPACE_MARKDOWN_PARSER.parse(normalized)
    }.getOrNull()
    if (root == null) {
        return WorkspaceMarkdownStructure(
            blockKinds = emptyList(),
            codeLanguages = emptyList(),
            sourceWasTruncated = sourceWasTruncated,
        )
    }

    val kinds = linkedSetOf<WorkspaceMarkdownBlockKind>()
    val languages = linkedSetOf<String>()
    root.visitStructure(kinds, languages)
    return WorkspaceMarkdownStructure(
        blockKinds = kinds.toList(),
        codeLanguages = languages.take(MAX_ACCESSIBLE_CODE_LANGUAGES),
        sourceWasTruncated = sourceWasTruncated,
    )
}

private fun Node.visitStructure(
    kinds: MutableSet<WorkspaceMarkdownBlockKind>,
    languages: MutableSet<String>,
) {
    when (this) {
        is BlockQuote -> kinds += WorkspaceMarkdownBlockKind.Quote
        is OrderedList -> kinds += WorkspaceMarkdownBlockKind.OrderedList
        is BulletList -> kinds += WorkspaceMarkdownBlockKind.BulletList
        is FencedCodeBlock -> {
            kinds += WorkspaceMarkdownBlockKind.CodeBlock
            info
                ?.trim()
                ?.takeWhile { !it.isWhitespace() }
                ?.filter(::isAccessibleCodeLanguageCharacter)
                ?.takeIf(String::isNotBlank)
                ?.take(MAX_ACCESSIBLE_CODE_LANGUAGE_CHARS)
                ?.let(languages::add)
        }

        is IndentedCodeBlock -> kinds += WorkspaceMarkdownBlockKind.CodeBlock
    }
    var child = firstChild
    while (child != null) {
        child.visitStructure(kinds, languages)
        child = child.next
    }
}

private fun isAccessibleCodeLanguageCharacter(value: Char): Boolean =
    value.isLetterOrDigit() || value in CODE_LANGUAGE_PUNCTUATION

private val WORKSPACE_MARKDOWN_PARSER = Parser.builder().build()
private val STRUCTURAL_MARKDOWN_LINE = Regex(
    """(?m)^(?: {0,3}(?:>|[-+*]\s|\d+[.)]\s|`{3,}|~{3,})|(?: {4,}|\t)\S)""",
)
private val CODE_LANGUAGE_PUNCTUATION = setOf('-', '_', '+', '#', '.')

private const val MAX_MARKDOWN_STRUCTURE_CHARS = 40_000
private const val MAX_ACCESSIBLE_CODE_LANGUAGES = 4
private const val MAX_ACCESSIBLE_CODE_LANGUAGE_CHARS = 32
