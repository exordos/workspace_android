package ru.genesiscorporation.workspace.beta.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.jeziellago.compose.markdowntext.MarkdownText
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ForwardMarkdownSegment
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ForwardQuoteResolution
import ru.genesiscorporation.workspace.beta.modules.chatdialog.WorkspaceQuoteReference
import ru.genesiscorporation.workspace.beta.modules.chatdialog.parseForwardMarkdown
import ru.genesiscorporation.workspace.beta.modules.chatdialog.parseWorkspaceConversationReferenceUrn
import ru.genesiscorporation.workspace.beta.modules.chatdialog.parseWorkspaceEntityReferenceUrn
import ru.genesiscorporation.workspace.beta.modules.chatdialog.parseWorkspaceQuoteUrn
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URI

internal val LocalWorkspaceMentionCatalog =
    staticCompositionLocalOf<WorkspaceMentionCatalog?> { null }

@Composable
fun EnhancedMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?
) {
    val context = LocalContext.current
    val providedMentionCatalog = LocalWorkspaceMentionCatalog.current
    val mentionCatalog = providedMentionCatalog ?: remember(viewModel) {
        WorkspaceMentionCatalog.from(
            viewModel
                ?.repo
                ?.users
                ?.value
                .orEmpty()
                .map { user ->
                    WorkspaceMentionCandidate(
                        userUuid = user.uuid,
                        displayText = user.displayableName(),
                        username = user.username,
                    )
                },
        )
    }
    val emojiResolver = remember(context.applicationContext) {
        WorkspaceEmojiShortcodeCatalog.resolver(context.applicationContext)
    }
    EnhancedMarkdownContent(
        markdown = markdown,
        modifier = modifier,
        style = style,
        navController = navController,
        viewModel = viewModel,
        mentionCatalog = mentionCatalog,
        emojiResolver = emojiResolver,
    )
}

@Composable
private fun EnhancedMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    val forwardSegments = remember(markdown) { parseForwardMarkdown(markdown) }
    if (forwardSegments.any { it is ForwardMarkdownSegment.Quote }) {
        Column(modifier = modifier.fillMaxWidth()) {
            forwardSegments.forEach { segment ->
                when (segment) {
                    is ForwardMarkdownSegment.Text -> LegacyEnhancedMarkdown(
                        markdown = segment.markdown,
                        style = style,
                        navController = navController,
                        viewModel = viewModel,
                        mentionCatalog = mentionCatalog,
                        emojiResolver = emojiResolver,
                    )

                    is ForwardMarkdownSegment.Quote -> WorkspaceForwardQuoteBlock(
                        reference = segment.reference,
                        style = style,
                        viewModel = viewModel,
                        mentionCatalog = mentionCatalog,
                        emojiResolver = emojiResolver,
                    )
                }
            }
        }
    } else {
        LegacyEnhancedMarkdown(
            markdown = markdown,
            modifier = modifier,
            style = style,
            navController = navController,
            viewModel = viewModel,
            mentionCatalog = mentionCatalog,
            emojiResolver = emojiResolver,
        )
    }
}

@Composable
private fun LegacyEnhancedMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    val blockSegments = remember(markdown) {
        parseWorkspaceBlockSpoilers(markdown)
    }
    if (blockSegments.any { it is WorkspaceBlockSpoilerSegment.Spoiler }) {
        Column(modifier = modifier.fillMaxWidth()) {
            blockSegments.forEach { segment ->
                when (segment) {
                    is WorkspaceBlockSpoilerSegment.Text -> {
                        if (segment.markdown.isNotEmpty()) {
                            LegacyEnhancedMarkdownCore(
                                markdown = segment.markdown,
                                style = style,
                                navController = navController,
                                viewModel = viewModel,
                                mentionCatalog = mentionCatalog,
                                emojiResolver = emojiResolver,
                            )
                        }
                    }

                    is WorkspaceBlockSpoilerSegment.Spoiler -> {
                        key(segment.id, segment.header, segment.bodyMarkdown) {
                            WorkspaceSpoilerBlock(
                                segment = segment,
                                style = style,
                                navController = navController,
                                viewModel = viewModel,
                                mentionCatalog = mentionCatalog,
                                emojiResolver = emojiResolver,
                            )
                        }
                    }
                }
            }
        }
        return
    }
    LegacyEnhancedMarkdownCore(
        markdown = markdown,
        modifier = modifier,
        style = style,
        navController = navController,
        viewModel = viewModel,
        mentionCatalog = mentionCatalog,
        emojiResolver = emojiResolver,
    )
}

@Composable
private fun LegacyEnhancedMarkdownCore(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    val context = LocalContext.current
    if (MarkdownParser.hasQuotes(markdown)) {
        val nodes = remember(markdown) { MarkdownParser.parse(markdown) }
        MessageNodes(
            nodes = nodes,
            modifier = modifier,
            style = style,
            depth = 0,
            navController = navController,
            viewModel = viewModel,
            mentionCatalog = mentionCatalog,
            emojiResolver = emojiResolver,
        )
    } else {
        WorkspaceMarkdownText(
            markdown = markdown,
            style = style,
            onLinkClicked = { url ->
                handleWorkspaceLink(url, context, viewModel)
            },
            mentionCatalog = mentionCatalog,
            emojiResolver = emojiResolver,
        )
    }
}

@Composable
private fun WorkspaceSpoilerBlock(
    segment: WorkspaceBlockSpoilerSegment.Spoiler,
    style: TextStyle,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val defaultHeader = stringResource(R.string.markdown_spoiler_default_header)
    val showLabel = stringResource(R.string.markdown_spoiler_show)
    val hideLabel = stringResource(R.string.markdown_spoiler_hide)
    val hiddenState = stringResource(R.string.markdown_spoiler_hidden_state)
    val shownState = stringResource(R.string.markdown_spoiler_shown_state)
    var revealed by rememberSaveable(
        segment.id,
        segment.header,
        segment.bodyMarkdown,
    ) {
        mutableStateOf(false)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = colors.infoCardBackground,
                shape = RoundedCornerShape(7.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { revealed = !revealed }
                .semantics {
                    role = Role.Button
                    stateDescription = if (revealed) shownState else hiddenState
                }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = segment.header ?: defaultHeader,
                color = style.color,
                fontSize = style.fontSize,
                lineHeight = style.lineHeight,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (revealed) hideLabel else showLabel,
                color = colors.primary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (revealed && segment.bodyMarkdown.isNotEmpty()) {
            EnhancedMarkdownContent(
                markdown = segment.bodyMarkdown,
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 10.dp,
                ),
                style = style,
                navController = navController,
                viewModel = viewModel,
                mentionCatalog = mentionCatalog,
                emojiResolver = emojiResolver,
            )
        }
    }
}

@Composable
private fun WorkspaceForwardQuoteBlock(
    reference: WorkspaceQuoteReference,
    style: TextStyle,
    viewModel: ChatDialogViewModel?,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val context = LocalContext.current
    val resolutions by viewModel
        ?.forwardQuoteResolutions
        ?.collectAsStateWithLifecycle()
        ?: remember {
            kotlinx.coroutines.flow.MutableStateFlow<
                Map<String, ForwardQuoteResolution>
                >(emptyMap())
        }.collectAsStateWithLifecycle()
    val resolution = resolutions[reference.messageUuid]
        ?: ForwardQuoteResolution.Loading
    LaunchedEffect(viewModel, reference.messageUuid) {
        viewModel?.requestForwardQuote(reference.messageUuid)
    }
    val ready = resolution as? ForwardQuoteResolution.Ready
    val authorLabel = ready
        ?.message
        ?.let { message ->
            message.user?.displayableName()
                ?: viewModel?.getUser(message.authorUuid)?.displayableName()
        }
        ?.takeIf(String::isNotBlank)
        ?: reference.authorLabel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 4.dp)
            .background(
                colors.infoCardBackground,
                RoundedCornerShape(7.dp),
            )
            .then(
                if (ready != null && viewModel != null) {
                    Modifier
                        .clickable {
                            viewModel.openForwardQuoteSource(reference.messageUuid)
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Открыть исходное сообщение"
                        }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val width = 3.dp.toPx()
                drawLine(
                    color = colors.messageOwnAccent,
                    start = Offset(width / 2f, 0f),
                    end = Offset(width / 2f, size.height),
                    strokeWidth = width,
                )
            }
            .padding(start = 11.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
    ) {
        Text(
            text = when (resolution) {
                ForwardQuoteResolution.Loading -> reference.authorLabel
                ForwardQuoteResolution.Unavailable -> "Исходное сообщение недоступно"
                is ForwardQuoteResolution.Ready -> authorLabel
            },
            color = if (resolution is ForwardQuoteResolution.Unavailable) {
                colors.textAdditional50
            } else {
                colors.messageOwnAccent
            },
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (resolution) {
            ForwardQuoteResolution.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(20.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
            }

            ForwardQuoteResolution.Unavailable -> {
                if (viewModel != null) {
                    TextButton(
                        onClick = {
                            viewModel.retryForwardQuote(reference.messageUuid)
                        },
                    ) {
                        Text("Повторить")
                    }
                }
            }

            is ForwardQuoteResolution.Ready -> {
                val selectedText = reference.selectedText
                if (selectedText != null) {
                    Text(
                        text = selectedText,
                        color = style.color,
                        fontSize = style.fontSize,
                        lineHeight = style.lineHeight,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                } else {
                    WorkspaceMarkdownText(
                        markdown = resolution.message.payload.content,
                        style = style,
                        onLinkClicked = { url ->
                            handleWorkspaceLink(
                                url,
                                context,
                                viewModel,
                            )
                        },
                        mentionCatalog = mentionCatalog,
                        emojiResolver = emojiResolver,
                    )
                }
            }
        }
    }
}

object MarkdownParser {
    private val QUOTE_FENCE_OPEN = Regex("""^(`{3,}|~{3,})quote\s*$""")
    private val QUOTE_HEADER = Regex(
        """^@_\*\*(.+?)(?:\|(\d+))?\*\*\s*\[(.*?)]\((.*?)\):\s*$""",
    )
    private val HAS_QUOTE = Regex("""(`{3,}|~{3,})quote""")
    fun hasQuotes(content: String): Boolean = HAS_QUOTE.containsMatchIn(content)
    fun parse(content: String): List<QouteNode> {
        if (content.isEmpty()) return emptyList()
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        return parseBlock(normalized.split('\n'), startIndex = 0, closeFence = null).first
    }
    private fun parseBlock(
        lines: List<String>,
        startIndex: Int,
        closeFence: String?,
    ): Pair<List<QouteNode>, Int> {
        val nodes = mutableListOf<QouteNode>()
        val textBuffer = StringBuilder()
        var index = startIndex
        fun flushText() {
            val text = textBuffer.toString().trimEnd('\n')
            if (text.isNotEmpty()) {
                nodes.add(QouteNode.Text(text))
            }
            textBuffer.clear()
        }
        fun takeHeaderFromBuffer(): QuoteHeader? {
            if (textBuffer.isEmpty()) return null
            val text = textBuffer.toString()
            val lastNewline = text.lastIndexOf('\n')
            val lastLine = if (lastNewline >= 0) text.substring(lastNewline + 1) else text
            val match = QUOTE_HEADER.matchEntire(lastLine.trim()) ?: return null
            if (lastNewline >= 0) {
                textBuffer.setLength(lastNewline)
            } else {
                textBuffer.clear()
            }
            return match.toQuoteHeader()
        }
        while (index < lines.size) {
            val line = lines[index]
            val trimmedEnd = line.trimEnd()
            if (closeFence != null && trimmedEnd == closeFence) {
                flushText()
                return nodes to index + 1
            }
            val openMatch = QUOTE_FENCE_OPEN.matchEntire(trimmedEnd)
            if (openMatch != null) {
                val header = takeHeaderFromBuffer()
                flushText()
                val fence = openMatch.groupValues[1]
                val closingFence = fence.first().toString().repeat(fence.length)
                index++
                val (children, nextIndex) = parseBlock(lines, index, closingFence)
                nodes.add(QouteNode.Quote(header = header, children = children))
                index = nextIndex
                continue
            }
            if (textBuffer.isNotEmpty()) {
                textBuffer.append('\n')
            }
            textBuffer.append(line)
            index++
        }
        flushText()
        return nodes to index
    }
    private fun MatchResult.toQuoteHeader(): QuoteHeader = QuoteHeader(
        displayName = groupValues[1],
        userId = groupValues[2].toIntOrNull(),
        linkText = groupValues[3],
        linkUrl = groupValues[4],
    )
}


sealed interface QouteNode {
    data class Text(val content: String) : QouteNode
    data class Quote(
        val header: QuoteHeader?,
        val children: List<QouteNode>,
    ) : QouteNode
}

data class QuoteHeader(
    val displayName: String,
    val userId: Int?,
    val linkText: String,
    val linkUrl: String,
) {
    fun toMarkdownLine(): String = buildString {
        append("@_**")
        append(displayName)
        if (userId != null) {
            append('|')
            append(userId)
        }
        append("** [")
        append(linkText)
        append("](")
        append(linkUrl)
        append("):")
    }
}

@Composable
private fun MessageNodes(
    nodes: List<QouteNode>,
    modifier: Modifier = Modifier,
    style: TextStyle,
    depth: Int,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        nodes.forEach { node ->
            when (node) {
                is QouteNode.Text -> EnhancedMarkdownContent(
                    markdown = node.content,
                    style = style,
                    navController = navController,
                    viewModel = viewModel,
                    mentionCatalog = mentionCatalog,
                    emojiResolver = emojiResolver,
                )
                is QouteNode.Quote -> ZulipQuoteBlock(
                    quote = node,
                    style = style,
                    depth = depth,
                    navController = navController,
                    viewModel = viewModel,
                    mentionCatalog = mentionCatalog,
                    emojiResolver = emojiResolver,
                )
            }
        }
    }
}

private val QuoteIndentStep: Dp = 12.dp
private val QuoteBorderWidth: Dp = 3.dp
@Composable
private fun ZulipQuoteBlock(
    quote: QouteNode.Quote,
    style: TextStyle,
    depth: Int,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    val context = LocalContext.current
    val colors = LocalWorkspaceColorsPalette.current
    val borderColor = colors.textAdditional30.copy(alpha = 0.7f)
    val headerStyle = style.copy(
        fontSize = (style.fontSize.value * 0.85f).sp,
        color = colors.messageSecondaryText,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = QuoteIndentStep * depth, top = 4.dp, bottom = 4.dp)
            .drawBehind {
                val strokeWidth = QuoteBorderWidth.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(strokeWidth / 2f, 0f),
                    end = Offset(strokeWidth / 2f, size.height),
                    strokeWidth = strokeWidth,
                )
            }
            .padding(start = 8.dp),
    ) {
        quote.header?.let { header ->
            WorkspaceMarkdownText(
                markdown = header.toMarkdownLine(),
                style = headerStyle,
                onLinkClicked = { url ->
                    handleWorkspaceLink(url, context, viewModel)
                },
                mentionCatalog = mentionCatalog,
                emojiResolver = emojiResolver,
            )
        }
        if (quote.children.isNotEmpty()) {
            MessageNodes(
                nodes = quote.children,
                style = style,
                depth = depth + 1,
                navController = navController,
                viewModel = viewModel,
                mentionCatalog = mentionCatalog,
                emojiResolver = emojiResolver,
            )
        }
    }
}

@Composable
private fun WorkspaceMarkdownText(
    markdown: String,
    style: TextStyle,
    onLinkClicked: (String) -> Unit,
    mentionCatalog: WorkspaceMentionCatalog,
    emojiResolver: (String) -> String?,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val showSpoilerLabel = stringResource(R.string.markdown_spoiler_show)
    val hideSpoilerLabel = stringResource(R.string.markdown_spoiler_hide)
    val inlineSpoilers = remember(markdown) {
        parseWorkspaceInlineSpoilers(markdown)
    }
    var revealedSpoilerIds by rememberSaveable(markdown) {
        mutableStateOf("")
    }
    val revealedIds = remember(revealedSpoilerIds) {
        revealedSpoilerIds
            .split(',')
            .mapNotNull(String::toIntOrNull)
            .toSet()
    }
    val currentInlineSpoilers by rememberUpdatedState(inlineSpoilers)
    val currentRevealedIds by rememberUpdatedState(revealedIds)
    val currentOnLinkClicked by rememberUpdatedState(onLinkClicked)
    val spoilerMarkdown = remember(
        inlineSpoilers,
        revealedIds,
        showSpoilerLabel,
        hideSpoilerLabel,
    ) {
        inlineSpoilers.render(
            revealedIds = revealedIds,
            showLabel = showSpoilerLabel,
            hideLabel = hideSpoilerLabel,
        )
    }
    val renderedMarkdown = remember(
        spoilerMarkdown,
        mentionCatalog,
        emojiResolver,
    ) {
        renderWorkspaceMarkdownInlineMetadata(
            markdown = spoilerMarkdown,
            mentionCatalog = mentionCatalog,
            resolveEmoji = emojiResolver,
        )
    }
    val structure = remember(renderedMarkdown) {
        analyzeWorkspaceMarkdownStructure(renderedMarkdown)
    }
    val accessibilityLabels = WorkspaceMarkdownAccessibilityLabels(
        quote = stringResource(R.string.markdown_structure_quote),
        orderedList = stringResource(R.string.markdown_structure_ordered_list),
        bulletList = stringResource(R.string.markdown_structure_bullet_list),
        codeBlock = stringResource(R.string.markdown_structure_code_block),
        longMessage = stringResource(R.string.markdown_structure_long_message),
    )
    val structureDescription = remember(structure, accessibilityLabels) {
        structure.accessibilityDescription(accessibilityLabels)
    }
    key(
        colors.markdownCodeBackground,
        colors.markdownCodeText,
        style.color,
        style.fontSize,
        style.lineHeight,
    ) {
        MarkdownText(
            markdown = renderedMarkdown,
            modifier = Modifier.then(
                if (structureDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics {
                        stateDescription = structureDescription
                    }
                },
            ),
            style = style,
            syntaxHighlightColor = colors.markdownCodeBackground,
            syntaxHighlightTextColor = colors.markdownCodeText,
            onLinkClicked = { url ->
                val spoilerId = parseWorkspaceInlineSpoilerUrn(url)
                if (
                    spoilerId != null &&
                    spoilerId in currentInlineSpoilers.spoilerIds
                ) {
                    val updatedIds = if (spoilerId in currentRevealedIds) {
                        currentRevealedIds - spoilerId
                    } else {
                        currentRevealedIds + spoilerId
                    }
                    revealedSpoilerIds = updatedIds
                        .sorted()
                        .joinToString(separator = ",")
                } else {
                    currentOnLinkClicked(url)
                }
            },
        )
    }
}

private fun handleWorkspaceLink(
    url: String,
    context: Context,
    viewModel: ChatDialogViewModel?,
) {
    val normalizedUrl = url.trim()
    val entityReference =
        parseWorkspaceEntityReferenceUrn(normalizedUrl)
    val conversationReference =
        parseWorkspaceConversationReferenceUrn(normalizedUrl)
    when {
        entityReference != null -> {
            viewModel?.openWorkspaceEntityReference(entityReference)
        }

        conversationReference != null -> {
            viewModel?.openWorkspaceConversationReference(
                conversationReference,
            )
        }

        normalizedUrl.startsWith("urn:stream:", ignoreCase = true) ||
            normalizedUrl.startsWith("urn:topic:", ignoreCase = true) -> {
            viewModel?.reportActionError(
                "Повреждённая ссылка на чат или топик",
            )
        }

        normalizedUrl.startsWith("urn:user:", ignoreCase = true) ||
            normalizedUrl.startsWith("urn:message:", ignoreCase = true) -> {
            viewModel?.reportActionError(
                "Повреждённая ссылка на пользователя или сообщение",
            )
        }

        normalizedUrl.startsWith("urn:quote:", ignoreCase = true) -> {
            val quote = parseWorkspaceQuoteUrn(normalizedUrl)
            if (quote == null) {
                viewModel?.reportActionError("Повреждённая ссылка на сообщение")
            } else {
                viewModel?.openForwardQuoteSource(quote.first)
            }
        }

        else -> {
            val uri = safeExternalUri(url)
            if (uri == null) {
                viewModel?.reportActionError("Небезопасная ссылка заблокирована")
                return
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
            runCatching { context.startActivity(intent) }
                .onFailure {
                    viewModel?.reportActionError("Не удалось открыть ссылку")
                }
        }
    }
}

internal fun safeExternalUri(value: String): Uri? {
    val target = normalizeSafeExternalLink(value) ?: return null
    return runCatching { target.toUri() }.getOrNull()
}

internal fun normalizeSafeExternalLink(value: String): String? {
    val normalized = value.trim()
    val target = if (
        normalized.startsWith(WORKSPACE_URL_URN_PREFIX, ignoreCase = true)
    ) {
        parseWorkspaceUrlUrn(normalized) ?: return null
    } else {
        normalized
    }
    return target.takeIf(::isSafeExternalLink)
}

internal fun parseWorkspaceUrlUrn(value: String): String? {
    val normalized = value.trim()
    if (!normalized.startsWith(WORKSPACE_URL_URN_PREFIX, ignoreCase = true)) {
        return null
    }
    val target = normalized.substring(WORKSPACE_URL_URN_PREFIX.length)
    if (target.isEmpty() || target.any(Char::isWhitespace)) return null
    val uri = runCatching { URI(target) }.getOrNull() ?: return null
    if (
        uri.scheme?.lowercase() !in setOf("http", "https") ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null
    ) {
        return null
    }
    return target
}

internal fun isSafeExternalLink(value: String): Boolean {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    return when (uri.scheme?.lowercase()) {
        "http", "https" -> !uri.host.isNullOrBlank() && uri.userInfo == null
        "mailto" -> !uri.rawSchemeSpecificPart.isNullOrBlank()
        else -> false
    }
}

private const val WORKSPACE_URL_URN_PREFIX = "urn:url:"
