package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
import ru.genesiscorporation.workspace.beta.ui.ReferenceMessageBase
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

/** Resolves forwarded sources independently of the destination conversation. */
@Composable
fun QuotedMessagePartView(
    quotedMessageUuid: String,
    isOwn: Boolean,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    displayName: String? = null,
    visitedQuoteUuids: Set<String> = emptySet(),
    quoteNodeBudget: Int = MAX_RENDERED_QUOTE_NODES,
    selectedText: String? = null,
) {
    val fallbackAuthor = displayName ?: stringResource(R.string.quote_default_author)
    if (selectedText != null) {
        SnapshotQuoteView(MessageElement.SnapshotQuote(fallbackAuthor, selectedText), isOwn,
            viewModel, navController, quoteNodeBudget, visitedQuoteUuids)
        return
    }
    val uuid = quotedMessageUuid.lowercase()
    val states by viewModel.quoteStates.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val recursive = uuid in visitedQuoteUuids || visitedQuoteUuids.size >= 8 || quoteNodeBudget <= 0
    LaunchedEffect(uuid, recursive, states[uuid] == null) {
        if (!recursive) viewModel.loadQuotedMessages(listOf(uuid))
    }
    val state = if (recursive) MessageQuoteState.Unavailable else states[uuid] ?: MessageQuoteState.Loading
    val ready = (state as? MessageQuoteState.Ready)?.message
    val author = ready?.let { source -> users.firstOrNull { it.uuid == source.authorUuid } }
    MessageQuoteCard(
        state = state,
        authorLabel = author?.displayableName()
            ?: ready?.user?.takeIf { it.uuid == ready.authorUuid }?.displayableName()
            ?: ready?.authorUuid ?: fallbackAuthor,
        isOwn = isOwn,
        onRetry = { viewModel.retryQuotedMessage(uuid) },
    ) { message ->
        MessageQuoteContent(message.payload.content, isOwn, viewModel, navController, visitedQuoteUuids + uuid, quoteNodeBudget)
    }
}

@Composable
internal fun SnapshotQuoteView(
    quote: MessageElement.SnapshotQuote,
    isOwn: Boolean,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    quoteNodeBudget: Int,
    visitedQuoteUuids: Set<String> = emptySet(),
) {
    SnapshotQuoteCard(quote.displayName, isOwn) {
        MessageQuoteContent(quote.text, isOwn, viewModel, navController, visitedQuoteUuids, quoteNodeBudget)
    }
}

/** A sender-supplied copy is visibly different from a source resolved from the server. */
@Composable
internal fun SnapshotQuoteCard(authorLabel: String, isOwn: Boolean, content: @Composable () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))
        .background(if (isOwn) colors.messageOwnSelectedBg else colors.messageOwnBackground)
        .border(1.dp, colors.textAdditional50, RoundedCornerShape(8.dp))
        .padding(8.dp).testTag("snapshot-quote-card")) {
        Text(stringResource(R.string.quote_snapshot_copy), color = colors.textAdditional50,
            fontSize = 12.sp, fontFamily = InterFontFamily)
        Text(authorLabel, color = colors.textHeaders, fontSize = 12.sp,
            fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun MessageQuoteContent(
    content: String,
    isOwn: Boolean,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    visitedQuoteUuids: Set<String>,
    quoteNodeBudget: Int,
) {
    val elements = remember(content, quoteNodeBudget) {
        budgetMessageQuotes(MarkdownPayloadParser.parse(content), (quoteNodeBudget - 1).coerceAtLeast(0))
    }
    LaunchedEffect(elements) {
        if (visitedQuoteUuids.size < 8) {
            viewModel.loadQuotedMessages(elements.map { it.element }.filterIsInstance<MessageElement.Quote>().filter { it.text.isEmpty() }
                .map { it.uuid }.filterNot { it.lowercase() in visitedQuoteUuids })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        elements.forEach { budgeted ->
            when (val element = budgeted.element) {
                is MessageElement.Image -> QuotedMessageImage(element, viewModel)
                is MessageElement.File -> FileAttachmentRow(element, viewModel, onOpenFile = {})
                is MessageElement.Quote -> QuotedMessagePartView(
                    element.uuid, isOwn, viewModel, navController,
                    element.displayName, visitedQuoteUuids, budgeted.quoteBudget, element.text.takeIf { it.isNotEmpty() },
                )
                is MessageElement.SnapshotQuote -> SnapshotQuoteView(
                    element, isOwn, viewModel, navController, budgeted.quoteBudget, visitedQuoteUuids,
                )
                is MessageElement.PlainText -> EnhancedMarkdown(
                    markdown = element.text,
                    style = TextStyle(color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp, fontFamily = InterFontFamily),
                    navController = navController, viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
internal fun MessageQuoteCard(
    state: MessageQuoteState,
    authorLabel: String,
    isOwn: Boolean,
    onRetry: () -> Unit,
    content: @Composable (MessageResponse) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    ReferenceMessageBase(Modifier.padding(vertical = 4.dp), shouldClose = false, onCloseTap = {}) {
        Column(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (isOwn) colors.messageOwnSelectedBg else colors.messageOwnBackground)
                .padding(8.dp),
        ) {
            Text(
                authorLabel, color = colors.indicatorOrange, fontSize = 12.sp,
                fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            when (state) {
                MessageQuoteState.Loading -> Text(stringResource(R.string.quote_loading), color = colors.textAdditional50, fontSize = 12.sp)
                MessageQuoteState.Unavailable -> Text(stringResource(R.string.quote_unavailable), color = colors.textAdditional50, fontSize = 12.sp)
                MessageQuoteState.Error -> {
                    Text(stringResource(R.string.quote_load_failed), color = colors.textAdditional50, fontSize = 12.sp)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.quote_retry)) }
                }
                is MessageQuoteState.Ready -> content(state.message)
            }
        }
    }
}

@Composable
private fun QuotedMessageImage(element: MessageElement.Image, viewModel: ChatDialogViewModel) {
    val baseUrl by viewModel.userViewModel.repo.baseUrlFlow.collectAsStateWithLifecycle(initialValue = "")
    val headers = NetworkHeaders.Builder().apply {
        viewModel.client.authHeaders().forEach { set(it.title, it.value) }
    }.build()
    val request = ImageRequest.Builder(LocalContext.current)
        .data("$baseUrl/api/workspace/v1/messenger/files/${element.uuid}/actions/download")
        .httpHeaders(headers).build()
    var fullscreen by remember(element.uuid) { mutableStateOf(false) }
    AsyncImage(
        model = request, contentDescription = element.fileName,
        modifier = Modifier.heightIn(max = 240.dp).clickable { fullscreen = true },
    )
    if (fullscreen) {
        FullscreenZoomableImage(request, element.fileName, onDismiss = { fullscreen = false })
    }
}
