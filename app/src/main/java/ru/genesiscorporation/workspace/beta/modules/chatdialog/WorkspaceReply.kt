package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import ru.genesiscorporation.workspace.beta.data.PersistedWorkspaceReplySession
import ru.genesiscorporation.workspace.beta.data.PersistedWorkspaceReplyTab
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid

internal data class WorkspaceReplyTab(
    val id: String,
    val messageUuid: String,
    val senderUuid: String,
    val senderName: String,
    val quotedContent: String,
    val selectedText: String? = null,
    val createdAt: String,
    val answer: String = "",
)

internal data class WorkspaceReplySession(
    val tabs: List<WorkspaceReplyTab> = emptyList(),
    val activeTabId: String? = null,
) {
    val activeTab: WorkspaceReplyTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    val hasAnswer: Boolean
        get() = tabs.any { it.answer.isNotBlank() }
}

internal data class RestoredWorkspaceReplySession(
    val session: WorkspaceReplySession,
    val activeAnswer: String,
)

internal fun createWorkspaceReplyTab(
    message: MessageResponse,
    id: String,
    createdAt: String,
    selectedText: String? = null,
): WorkspaceReplyTab? {
    val canonicalMessageUuid =
        parseCanonicalMessageUuid(message.uuid) ?: return null
    val canonicalSenderUuid =
        parseCanonicalMessageUuid(message.authorUuid) ?: return null
    val senderName = message.user
        ?.displayableName()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: canonicalSenderUuid
    val canonicalId = id.trim().takeIf(::isValidReplyTabId) ?: return null
    val canonicalCreatedAt =
        createdAt.trim().takeIf(::isValidReplyTimestamp) ?: return null
    val normalizedSelectedText = selectedText
        ?.replace("\r\n", "\n")
        ?.replace('\r', '\n')
        ?.take(MAX_REPLY_TEXT_CHARS)
        ?.takeIf { it.isNotBlank() }
    return WorkspaceReplyTab(
        id = canonicalId,
        messageUuid = canonicalMessageUuid,
        senderUuid = canonicalSenderUuid,
        senderName = senderName.take(MAX_REPLY_SENDER_NAME_CHARS),
        quotedContent = message.payload.content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .take(MAX_REPLY_QUOTED_PREVIEW_CHARS),
        selectedText = normalizedSelectedText,
        createdAt = canonicalCreatedAt,
    )
}

internal fun replyToWorkspaceMessage(
    session: WorkspaceReplySession,
    tab: WorkspaceReplyTab,
): WorkspaceReplySession {
    val normalized = normalizeWorkspaceReplySession(session)
    val replacement = normalizeWorkspaceReplyTab(tab) ?: return normalized
    val activeIndex = normalized.tabs.indexOfFirst {
        it.id == normalized.activeTabId
    }
    if (activeIndex < 0) {
        return WorkspaceReplySession(
            tabs = listOf(replacement),
            activeTabId = replacement.id,
        )
    }
    val active = normalized.tabs[activeIndex]
    val replaced = replacement.copy(
        id = active.id,
        createdAt = active.createdAt,
        answer = active.answer,
    )
    val prospectiveInputChars = normalized.tabs
        .filterIndexed { index, _ -> index != activeIndex }
        .sumOf(WorkspaceReplyTab::replyInputChars) +
        replaced.replyInputChars()
    if (prospectiveInputChars > MAX_WORKSPACE_REPLY_INPUT_CHARS) {
        return normalized
    }
    return normalized.copy(
        tabs = normalized.tabs.mapIndexed { index, existing ->
            if (index == activeIndex) replaced else existing
        },
        activeTabId = replaced.id,
    )
}

internal fun addWorkspaceReplyTab(
    session: WorkspaceReplySession,
    tab: WorkspaceReplyTab,
): WorkspaceReplySession {
    val normalized = normalizeWorkspaceReplySession(session)
    val addition = normalizeWorkspaceReplyTab(tab) ?: return normalized
    if (
        normalized.tabs.size >= MAX_WORKSPACE_REPLY_TABS ||
        normalized.tabs.any { it.id == addition.id } ||
        workspaceReplyInputChars(normalized) + addition.replyInputChars() >
        MAX_WORKSPACE_REPLY_INPUT_CHARS
    ) {
        return normalized
    }
    return WorkspaceReplySession(
        tabs = normalized.tabs + addition,
        activeTabId = addition.id,
    )
}

internal fun setWorkspaceReplyAnswer(
    session: WorkspaceReplySession,
    answer: String,
): WorkspaceReplySession {
    val normalized = normalizeWorkspaceReplySession(session)
    val activeId = normalized.activeTabId ?: return normalized
    val active = normalized.activeTab ?: return normalized
    val otherInputChars = normalized.tabs
        .asSequence()
        .filterNot { it.id == activeId }
        .sumOf(WorkspaceReplyTab::replyInputChars)
    val maximumAnswerChars = (
        MAX_WORKSPACE_REPLY_INPUT_CHARS -
            otherInputChars -
            active.selectedText.orEmpty().length
        ).coerceAtLeast(0)
    return normalized.copy(
        tabs = normalized.tabs.map { tab ->
            if (tab.id == activeId) {
                tab.copy(
                    answer = answer.take(
                        minOf(MAX_REPLY_TEXT_CHARS, maximumAnswerChars),
                    ),
                )
            } else {
                tab
            }
        },
    )
}

internal fun selectWorkspaceReplyTab(
    session: WorkspaceReplySession,
    tabId: String,
): WorkspaceReplySession {
    val normalized = normalizeWorkspaceReplySession(session)
    return if (normalized.tabs.any { it.id == tabId }) {
        normalized.copy(activeTabId = tabId)
    } else {
        normalized
    }
}

internal fun removeWorkspaceReplyTab(
    session: WorkspaceReplySession,
    tabId: String,
): WorkspaceReplySession {
    val normalized = normalizeWorkspaceReplySession(session)
    val removedIndex = normalized.tabs.indexOfFirst { it.id == tabId }
    if (removedIndex < 0) return normalized
    val remaining = normalized.tabs.filterNot { it.id == tabId }
    if (remaining.isEmpty()) return WorkspaceReplySession()
    if (normalized.activeTabId != tabId) {
        return normalized.copy(tabs = remaining)
    }
    val nextActive = remaining.getOrNull(removedIndex)
        ?: remaining.getOrNull(removedIndex - 1)
    return WorkspaceReplySession(
        tabs = remaining,
        activeTabId = nextActive?.id,
    )
}

internal fun moveWorkspaceReplyTab(
    session: WorkspaceReplySession,
    tabId: String,
    offset: Int,
): WorkspaceReplySession {
    val normalized = normalizeWorkspaceReplySession(session)
    val sourceIndex = normalized.tabs.indexOfFirst { it.id == tabId }
    if (sourceIndex < 0 || offset == 0) return normalized
    val destinationIndex = (sourceIndex + offset)
        .coerceIn(0, normalized.tabs.lastIndex)
    if (destinationIndex == sourceIndex) return normalized
    val mutableTabs = normalized.tabs.toMutableList()
    val moved = mutableTabs.removeAt(sourceIndex)
    mutableTabs.add(destinationIndex, moved)
    return normalized.copy(tabs = mutableTabs)
}

internal fun buildWorkspaceReplyMarkdown(
    session: WorkspaceReplySession,
): String? {
    val normalized = normalizeWorkspaceReplySession(session)
    if (normalized.tabs.isEmpty()) return null
    val sections = mutableListOf<String>()
    normalized.tabs.forEach { tab ->
        val quoteUrn = buildWorkspaceQuoteUrn(
            messageUuid = tab.messageUuid,
            selectedText = tab.selectedText,
        ) ?: return null
        val quote = "[${escapeWorkspaceMarkdownInline(tab.senderName)}]" +
            "($quoteUrn)"
        val answer = tab.answer.trim()
        sections += if (answer.isEmpty()) quote else "$quote\n\n$answer"
    }
    return sections.joinToString("\n\n").trimEnd()
}

internal fun workspaceReplyMessageUuidsFromMarkdown(
    markdown: String,
): List<String>? =
    parseWorkspaceReplySections(markdown)
        ?.map { (reference, _) -> reference.messageUuid }
        ?.distinct()

internal fun restoreWorkspaceReplySessionFromMarkdown(
    markdown: String,
    resolveMessage: (String) -> MessageResponse?,
    createIdentity: (Int) -> Pair<String, String>,
): RestoredWorkspaceReplySession? {
    val parsed = parseWorkspaceReplySections(markdown) ?: return null
    val tabs = parsed.mapIndexed { index, (reference, answer) ->
        val source = resolveMessage(reference.messageUuid) ?: return null
        val (id, createdAt) = createIdentity(index)
        createWorkspaceReplyTab(
            message = source,
            id = id,
            createdAt = createdAt,
            selectedText = reference.selectedText,
        )?.copy(answer = answer.take(MAX_REPLY_TEXT_CHARS))
            ?: return null
    }
    val session = normalizeWorkspaceReplySession(
        WorkspaceReplySession(
            tabs = tabs,
            activeTabId = tabs.firstOrNull()?.id,
        ),
    )
    if (session.tabs.size != tabs.size) return null
    val answer = session.activeTab?.answer ?: return null
    return RestoredWorkspaceReplySession(
        session = session,
        activeAnswer = answer,
    )
}

private fun parseWorkspaceReplySections(
    markdown: String,
): List<Pair<WorkspaceQuoteReference, String>>? {
    val normalized = markdown
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (normalized.isEmpty()) return null
    val lines = normalized.split('\n')
    var currentStart = 0
    var currentReference =
        parseStandaloneWorkspaceQuote(lines.first()) ?: return null
    val parsed = mutableListOf<Pair<WorkspaceQuoteReference, String>>()
    while (true) {
        var nextStart = lines.size
        var nextReference: WorkspaceQuoteReference? = null
        for (index in currentStart + 1 until lines.size) {
            if (lines.getOrNull(index - 1)?.isNotBlank() == true) continue
            val candidate = parseStandaloneWorkspaceQuote(lines[index])
                ?: continue
            nextStart = index
            nextReference = candidate
            break
        }
        val answer = lines
            .subList(currentStart + 1, nextStart)
            .joinToString("\n")
            .trim()
        parsed += currentReference to answer
        if (nextReference == null) break
        currentStart = nextStart
        currentReference = nextReference
    }
    return parsed.takeIf {
        it.isNotEmpty() && it.size <= MAX_WORKSPACE_REPLY_TABS
    }
}

internal fun normalizeWorkspaceReplySession(
    session: WorkspaceReplySession,
): WorkspaceReplySession {
    val ids = mutableSetOf<String>()
    var remainingInputChars = MAX_WORKSPACE_REPLY_INPUT_CHARS
    val tabs = mutableListOf<WorkspaceReplyTab>()
    for (source in session.tabs) {
        if (tabs.size >= MAX_WORKSPACE_REPLY_TABS) break
        val normalized = normalizeWorkspaceReplyTab(source)
            ?: continue
        if (!ids.add(normalized.id)) continue
        val selectedChars = normalized.selectedText.orEmpty().length
        if (selectedChars > remainingInputChars) continue
        val maximumAnswerChars = minOf(
            MAX_REPLY_TEXT_CHARS,
            remainingInputChars - selectedChars,
        )
        val bounded = normalized.copy(
            answer = normalized.answer.take(maximumAnswerChars),
        )
        if (
            normalized.replyInputChars() > 0 &&
            bounded.replyInputChars() == 0
        ) {
            break
        }
        remainingInputChars -= bounded.replyInputChars()
        tabs += bounded
        if (bounded.answer.length < normalized.answer.length) break
    }
    if (tabs.isEmpty()) return WorkspaceReplySession()
    val activeId = session.activeTabId
        ?.takeIf { candidate -> tabs.any { it.id == candidate } }
        ?: tabs.first().id
    return WorkspaceReplySession(tabs = tabs, activeTabId = activeId)
}

internal fun WorkspaceReplySession.toPersisted():
    PersistedWorkspaceReplySession {
    val normalized = normalizeWorkspaceReplySession(this)
    return PersistedWorkspaceReplySession(
        tabs = normalized.tabs.map { tab ->
            PersistedWorkspaceReplyTab(
                id = tab.id,
                messageUuid = tab.messageUuid,
                senderUuid = tab.senderUuid,
                senderName = tab.senderName,
                quotedContent = tab.quotedContent,
                selectedText = tab.selectedText,
                createdAt = tab.createdAt,
                answer = tab.answer,
            )
        },
        activeTabId = normalized.activeTabId,
    )
}

internal fun PersistedWorkspaceReplySession.toWorkspaceReplySession():
    WorkspaceReplySession =
    normalizeWorkspaceReplySession(
        WorkspaceReplySession(
            tabs = tabs.map { tab ->
                WorkspaceReplyTab(
                    id = tab.id,
                    messageUuid = tab.messageUuid,
                    senderUuid = tab.senderUuid,
                    senderName = tab.senderName,
                    quotedContent = tab.quotedContent,
                    selectedText = tab.selectedText,
                    createdAt = tab.createdAt,
                    answer = tab.answer,
                )
            },
            activeTabId = activeTabId,
        ),
    )

private fun normalizeWorkspaceReplyTab(
    tab: WorkspaceReplyTab,
): WorkspaceReplyTab? {
    val id = tab.id.trim().takeIf(::isValidReplyTabId) ?: return null
    val messageUuid =
        parseCanonicalMessageUuid(tab.messageUuid) ?: return null
    val senderUuid =
        parseCanonicalMessageUuid(tab.senderUuid) ?: return null
    val senderName = tab.senderName
        .trim()
        .take(MAX_REPLY_SENDER_NAME_CHARS)
        .takeIf(String::isNotEmpty)
        ?: return null
    val createdAt = tab.createdAt
        .trim()
        .takeIf(::isValidReplyTimestamp)
        ?: return null
    return WorkspaceReplyTab(
        id = id,
        messageUuid = messageUuid,
        senderUuid = senderUuid,
        senderName = senderName,
        quotedContent = tab.quotedContent
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .take(MAX_REPLY_QUOTED_PREVIEW_CHARS),
        selectedText = tab.selectedText
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.take(MAX_REPLY_TEXT_CHARS)
            ?.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        answer = tab.answer.take(MAX_REPLY_TEXT_CHARS),
    )
}

internal fun workspaceReplyInputChars(
    session: WorkspaceReplySession,
): Int = session.tabs.sumOf(WorkspaceReplyTab::replyInputChars)

internal fun workspaceMarkdownPlainText(markdown: String): String {
    val boundedMarkdown = markdown.take(MAX_REPLY_TEXT_CHARS)
    if (boundedMarkdown.isEmpty()) return ""
    return runCatching {
        buildString {
            appendWorkspacePlainTextNode(
                Parser.builder()
                    .build()
                    .parse(boundedMarkdown),
            )
        }.trimEnd('\r', '\n')
    }.getOrDefault(boundedMarkdown)
}

private fun StringBuilder.appendWorkspacePlainTextNode(node: Node) {
    when (node) {
        is Text -> append(node.literal)
        is Code -> append(node.literal)
        is SoftLineBreak, is HardLineBreak -> append('\n')
        is FencedCodeBlock -> {
            append(node.literal)
            appendBlockBreak()
        }

        is IndentedCodeBlock -> {
            append(node.literal)
            appendBlockBreak()
        }

        is ListItem -> {
            append("• ")
            appendWorkspacePlainTextChildren(node)
            appendBlockBreak()
        }

        is Paragraph, is Heading -> {
            appendWorkspacePlainTextChildren(node)
            appendBlockBreak()
        }

        is BulletList, is OrderedList -> {
            appendWorkspacePlainTextChildren(node)
            appendBlockBreak()
        }

        else -> appendWorkspacePlainTextChildren(node)
    }
}

private fun StringBuilder.appendWorkspacePlainTextChildren(node: Node) {
    var child = node.firstChild
    while (child != null) {
        appendWorkspacePlainTextNode(child)
        child = child.next
    }
}

private fun StringBuilder.appendBlockBreak() {
    while (length >= 2 && this[length - 1] == '\n' && this[length - 2] == '\n') {
        deleteCharAt(lastIndex)
    }
    if (isNotEmpty() && last() != '\n') append('\n')
    append('\n')
}

private fun WorkspaceReplyTab.replyInputChars(): Int =
    selectedText.orEmpty().length + answer.length

private fun isValidReplyTabId(value: String): Boolean =
    value.isNotEmpty() &&
        value.length <= MAX_REPLY_TAB_ID_CHARS &&
        value.none { it.isISOControl() }

private fun isValidReplyTimestamp(value: String): Boolean =
    value.isNotEmpty() &&
        value.length <= MAX_REPLY_TIMESTAMP_CHARS &&
        value.none { it.isISOControl() }

internal const val MAX_WORKSPACE_REPLY_TABS = 32
internal const val MAX_WORKSPACE_REPLY_INPUT_CHARS = 40_000
private const val MAX_REPLY_TAB_ID_CHARS = 128
private const val MAX_REPLY_TIMESTAMP_CHARS = 64
private const val MAX_REPLY_SENDER_NAME_CHARS = 512
private const val MAX_REPLY_TEXT_CHARS = 40_000
private const val MAX_REPLY_QUOTED_PREVIEW_CHARS = 4_000
