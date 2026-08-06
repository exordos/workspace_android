package ru.genesiscorporation.workspace.beta.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.jeziellago.compose.markdowntext.MarkdownText
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun EnhancedMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    navController: NavHostController?,
    viewModel: ChatDialogViewModel?
) {
    val context = LocalContext.current
    if (MarkdownParser.hasQuotes(markdown)) {
        val nodes = remember(markdown) { MarkdownParser.parse(markdown) }
        MessageNodes(
            nodes = nodes,
            modifier = modifier,
            style = style,
            depth = 0,
            navController,
            viewModel
        )
    } else {
        MarkdownText(
            markdown = markdown,
            style = style,
            onLinkClicked = { url ->
                when {
                    url.startsWith("urn:user:") -> {
                        val userId = url.removePrefix("urn:user:")
                        val user = viewModel?.getUser(userId)
                        if (user != null) {
                            navController?.navigate(
                                ChatFlow.ChatUserInfo(
                                    user.displayableName(),
                                    user.uuid,
                                    user.avatar,
                                    user.email ?: ""
                                )
                            )
                        }
                    }
                    url.startsWith("urn:url:") -> {
                        val url = url.removePrefix("urn:url:")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        runCatching { context.startActivity(intent) }
                    }
                    else -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        runCatching { context.startActivity(intent) }
                    }
                }
            }
        )
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
    viewModel: ChatDialogViewModel?
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        nodes.forEach { node ->
            when (node) {
                is QouteNode.Text -> MarkdownText(
                    markdown = node.content,
                    style = style,
                    onLinkClicked = { url ->
                        when {
                            url.startsWith("urn:user:") -> {
                                val userId = url.removePrefix("urn:user:")
                                val user = viewModel?.getUser(userId)
                                if (user != null) {
                                    navController?.navigate(
                                        ChatFlow.ChatUserInfo(
                                            user.displayableName(),
                                            user.uuid,
                                            user.avatar,
                                            user.email ?: ""
                                        )
                                    )
                                }
                            }
                            else -> {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                runCatching { context.startActivity(intent) }
                            }
                        }
                    }
                )
                is QouteNode.Quote -> ZulipQuoteBlock(
                    quote = node,
                    style = style,
                    depth = depth,
                    navController = navController,
                    viewModel
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
    viewModel: ChatDialogViewModel?
) {
    val context = LocalContext.current
    val colors = LocalWorkspaceColorsPalette.current
    val borderColor = colors.textAdditional30.copy(alpha = 0.7f)
    val headerStyle = style.copy(
        fontSize = (style.fontSize.value * 0.85f).sp,
        fontFamily = InterFontFamily,
        color = colors.textAdditional30,
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
            MarkdownText(
                markdown = header.toMarkdownLine(),
                style = headerStyle,
                onLinkClicked = { url ->
                    when {
                        url.startsWith("urn:user:") -> {
                            val userId = url.removePrefix("urn:user:")
                            val user = viewModel?.getUser(userId)
                            if (user != null) {
                                navController?.navigate(
                                    ChatFlow.ChatUserInfo(
                                        user.displayableName(),
                                        user.uuid,
                                        user.avatar,
                                        user.email ?: ""
                                    )
                                )
                            }
                        }
                        else -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            runCatching { context.startActivity(intent) }
                        }
                    }
                }
            )
        }
        if (quote.children.isNotEmpty()) {
            MessageNodes(
                nodes = quote.children,
                style = style,
                depth = depth + 1,
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}