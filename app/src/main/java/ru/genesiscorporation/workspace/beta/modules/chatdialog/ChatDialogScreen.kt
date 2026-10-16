@file:OptIn(ExperimentalMaterial3Api::class)

package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.R.attr.end
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults.contentPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.Duration
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import kotlinx.coroutines.flow.distinctUntilChanged
import net.fellbaum.jemoji.EmojiManager
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.LocalBottomBarVisible
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatLockedMessage
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatPresentation
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatRequestStatus
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatServiceMessage
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatIntroduction
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatBackground
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatLinkColor
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatDialogScreen(
    viewModel: ChatDialogViewModel,
    navController: NavHostController
) {
    val directUser by viewModel.directUser.collectAsStateWithLifecycle()
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val encryptedChatState by viewModel.encryptedChatState.collectAsStateWithLifecycle()
    val protectedCallState by viewModel.protectedCallState.collectAsStateWithLifecycle()
    val streamTopicMessages by viewModel.streamTopicMessages.collectAsStateWithLifecycle()
    val messagePool by viewModel.repo.messagesPool.collectAsStateWithLifecycle()
    val rawTopicMessages by viewModel.repo.streamTopicMessages.collectAsStateWithLifecycle()
    val viewer by viewModel.repo.currentUser.collectAsStateWithLifecycle()
    val history = remember(viewModel, streamTopicMessages, messagePool, rawTopicMessages, viewer?.uuid, stream?.encryption, encryptedChatState) {
        deriveChatHistoryPresentation(streamTopicMessages["${viewModel.chatId}.${viewModel.topicUuid}"], messagePool,
            rawTopicMessages, viewModel.chatId, stream?.encryption == true, viewer?.uuid, encryptedChatState)
    }
    val messages = history.orderedMessages
    val streamBindings by viewModel.streamBindings.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val quotedMessages by viewModel.quotedMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedMessageUuids by viewModel.selectedMessageUuids.collectAsStateWithLifecycle()
    val selectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val canDeleteSelected by viewModel.canDeleteSelectedMessages.collectAsStateWithLifecycle()
    val deletingSelected by viewModel.deletingSelectedMessages.collectAsStateWithLifecycle()
    var forwardingMessages by remember { mutableStateOf<List<MessageResponse>?>(null) }
    var forwardingSelection by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    BackHandler(enabled = selectionMode) { viewModel.clearMessageSelection() }
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageActionErrorEffect(actionError, snackbarHostState, viewModel::clearActionError)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val protectedCallFailureMessage = stringResource(R.string.protected_stream_call_failed)
    LaunchedEffect(protectedCallState.error, protectedCallFailureMessage) {
        if (protectedCallState.error != null) {
            snackbarHostState.showSnackbar(protectedCallFailureMessage)
            viewModel.clearProtectedCallState()
        }
    }
    val notifiedKeys = remember { mutableSetOf<String>() }

    val messageFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    var hasDoneInitialScroll by remember { mutableStateOf(false) }
    var imeHeight = remember { mutableStateOf(0) }
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    val setBottomBarVisible = LocalBottomBarVisible.current

    DisposableEffect(viewModel) {
        viewModel.onChatOpened()
        onDispose { viewModel.onChatClosed() }
    }

    DisposableEffect(Unit) {
        setBottomBarVisible(false)
        onDispose { setBottomBarVisible(true) }
    }

    LaunchedEffect(key1 = Unit) {
        val keyboardFlow = snapshotFlow {
            ime.getBottom(localDensity)
        }

        keyboardFlow.collect { keyboardHeight ->
            if (keyboardHeight > 0) {
                if (imeHeight.value < keyboardHeight) {
                    listState.scrollBy((keyboardHeight - imeHeight.value).toFloat())
                }
                imeHeight.value = keyboardHeight
            } else if (keyboardHeight == 0) {
                imeHeight.value = 0
            }
        }
    }
    val openingUnread = viewModel.firstUnreadMessageUuid != null
    LaunchedEffect(messages?.size, openingUnread && isLoading) {
        if (openingUnread && isLoading) return@LaunchedEffect
        if (!messages.isNullOrEmpty() && (!hasDoneInitialScroll || viewModel.shouldScrollToBottom)) {
            val index = if (!hasDoneInitialScroll) viewModel.initialMessageIndex(messages) else messages.lastIndex
            listState.scrollToItem(index)
            if (openingUnread && !hasDoneInitialScroll) {
                if (index == messages.lastIndex) viewModel.enableAutoScroll()
                else viewModel.disableAutoScroll()
            }
            hasDoneInitialScroll = true
        }
    }

    LaunchedEffect(messages?.lastOrNull()) {
        if ((!openingUnread || hasDoneInitialScroll) && !messages.isNullOrEmpty() && viewModel.shouldScrollToBottom) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(listState, openingUnread && hasDoneInitialScroll) {
        if (openingUnread && !hasDoneInitialScroll) return@LaunchedEffect
        snapshotFlow {
            listState.isScrollInProgress to !listState.canScrollForward
        }
            .distinctUntilChanged()
            .collect { (isScrolling, isAtBottom) ->
                when {
                    isAtBottom -> viewModel.enableAutoScroll()
                    isScrolling -> viewModel.disableAutoScroll()
                }
            }
    }

    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.lastOrNull()
                ?.let { last ->
                    MeasuredChatBottom(
                        last.index,
                        layout.totalItemsCount,
                        last.offset,
                        last.size,
                        layout.viewportEndOffset - layout.afterContentPadding,
                        listState.isScrollInProgress,
                    )
                }
        }.distinctUntilChanged().collect { bottom ->
            // Selection, Coil and decrypted Markdown can move the final item
            // entirely outside the viewport. Preserve following through that
            // remeasurement, while leaving manual history scrolling alone.
            if (bottom == null || bottom.isScrolling || !viewModel.shouldScrollToBottom ||
                (bottom.itemIndex == bottom.itemCount - 1 &&
                    bottom.itemOffset + bottom.itemSize <= bottom.viewportEnd)
            ) return@collect

            // Layout snapshots can arrive within a measure pass. A synchronous
            // scroll would force another measure; correct only after that frame.
            withFrameNanos { }
            yield()
            if (!viewModel.shouldScrollToBottom || listState.isScrollInProgress) return@collect
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull() ?: return@collect
            if (last.index < layout.totalItemsCount - 1) {
                listState.scrollToItem(layout.totalItemsCount - 1)
                return@collect
            }
            val overflow = last.offset + last.size -
                (layout.viewportEndOffset - layout.afterContentPadding)
            if (overflow > 0) listState.scrollBy(overflow.toFloat())
        }
    }

    LaunchedEffect(listState, openingUnread && hasDoneInitialScroll) {
        if (openingUnread && !hasDoneInitialScroll) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItemsInfo ->
                val visibleKeysNow: Set<String> =
                    visibleItemsInfo.mapNotNull { info ->
                        info.key as? String
                    }.toSet()
                val newlyVisible = visibleKeysNow.filter { it !in notifiedKeys }
                if (newlyVisible.isNotEmpty()) {
                    notifiedKeys.addAll(newlyVisible)
                    scope.launch {
                        viewModel.onMessagesVisible(newlyVisible)
                    }
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (forwardingMessages != null) Modifier.clearAndSetSemantics {} else Modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (selectionMode) {
                    MessageSelectionBar(
                        selectedMessageUuids = selectedMessageUuids,
                        canDelete = canDeleteSelected,
                        isDeleting = deletingSelected,
                        onDelete = viewModel::deleteSelectedMessages,
                        onCancel = viewModel::clearMessageSelection,
                        onForward = {
                            forwardingSelection = true
                            forwardingMessages = viewModel.selectedMessages().sortedBy { Instant.parse(it.createdAt) }
                        },
                        isForwarding = forwardingMessages != null,
                        canForward = selectedMessageUuids.size <= MAX_FORWARD_SOURCE_MESSAGES &&
                            viewModel.selectedMessages().all(::canForwardMessage),
                    )
                }
            },
            topBar = {
                TopAppBar(
                    modifier = if (stream?.encryption == true) Modifier.clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)) else Modifier,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (stream?.encryption == true) Color(0xFF333333) else LocalWorkspaceColorsPalette.current.background,
                        titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                    ),
                    expandedHeight = if (stream?.encryption == true) 66.dp else 60.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                            .clickable(
                                onClick = {
                                    val user = viewModel.user
                                    if (user != null) {
    //                                    navController.navigate(
    //                                        ChatFlow.ChatUserInfo(
    //                                            user.fullName,
    //                                            "${user.userId}",
    //                                            user.avatarUrl ?: "",
    //                                            user.email
    //                                        )
    //                                    )
                                    }
                                }
                            )
                        ) {
                            val title = if (viewModel.isDirectMessages) {
                                stream?.displayableName(directUser) ?: viewModel.chatTitle
                            } else {
                                viewModel.topicName ?: viewModel.chatTitle
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (stream?.encryption == true) Box(Modifier.size(width = 3.dp, height = 16.dp)
                                .background(Color(0xFFFFCC00), RoundedCornerShape(2.dp)))
                            Text(
                                title,
                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                fontSize = if (stream?.encryption == true) 14.sp else 16.sp,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                modifier = if (stream?.encryption == true) Modifier.weight(1f, fill = false) else Modifier,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (stream?.encryption == true) {
                                Text(stringResource(R.string.encrypted_chat_title), color = EncryptedChatLinkColor,
                                    fontSize = 12.sp, lineHeight = 16.sp, fontFamily = InterFontFamily, maxLines = 1)
                            }
                            }
                            val currentStreamBindings = streamBindings[viewModel.chatId]
                            val user = directUser
                            if (currentStreamBindings != null && currentStreamBindings.count() > 0) {
                                val currentBindedOnlineUsers = currentStreamBindings.mapNotNull { binding -> users.firstOrNull { binding.userUuid == it.uuid && it.status == "active" } }
                                var baseText = if (viewModel.isDirectMessages && user != null) {
                                    user.statusDescription()
                                } else {
                                    context.resources.getQuantityString(
                                        R.plurals.participants_count, currentStreamBindings.count(), currentStreamBindings.count()
                                    )
                                }
                                if (!viewModel.isDirectMessages && currentBindedOnlineUsers.count() > 0) {
                                    baseText += ", ${currentBindedOnlineUsers.count()} онлайн"
                                }
                                Text(
                                    baseText,
                                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                    fontSize = if (stream?.encryption == true) 12.sp else 14.sp,
                                    fontFamily = InterFontFamily,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectionMode) viewModel.clearMessageSelection() else navController.popBackStack()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_back),
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = !selectionMode && (stream?.encryption != true ||
                                (encryptedChatState.sendingKeyUuid != null && !protectedCallState.busy)),
                            onClick = {
                                if (stream?.encryption == true) {
                                    scope.launch {
                                        val roomName = viewModel.createProtectedCall()
                                        if (roomName != null) {
                                            val options = JitsiMeetConferenceOptions.Builder()
                                                .setServerURL(URL(viewModel.repo.jitsiServerUrl)).setRoom(roomName).build()
                                            JitsiMeetActivity.launch(context, options)
                                        }
                                    }
                                } else {
                                val roomName = JitsiStyleRoomNameGenerator.generate()
                                val messageText = "${viewModel.repo.jitsiServerUrl}/${roomName}"
                                scope.launch {
                                    viewModel.sendTextMessage(messageText)
                                }
                                val options = JitsiMeetConferenceOptions.Builder()
                                    .setServerURL(URL(viewModel.repo.jitsiServerUrl))
                                    .setRoom(roomName)
                                    .build()

                                JitsiMeetActivity.launch(context, options)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = LocalWorkspaceColorsPalette.current.iconBase
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.call),
                                contentDescription = "Call"
                            )
                        }
                        IconButton(
                            enabled = !selectionMode,
                            onClick = {
                                navController.navigate(ChatFlow.StreamInfo(viewModel.chatId, viewModel.topicUuid))
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = LocalWorkspaceColorsPalette.current.iconBase
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more_vertical),
                                contentDescription = "More"
                            )
                        }
                    }
                )
            },
        ) { innerPadding ->
            val density = LocalDensity.current
            val navBarHeight = 70.dp
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(if (stream?.encryption == true) EncryptedChatBackground else LocalWorkspaceColorsPalette.current.background)
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        WindowInsets.ime
                            .exclude(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Bottom)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (stream?.encryption == true) EncryptedChatBackground else LocalWorkspaceColorsPalette.current.background)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedGif(Modifier.size(80.dp))
                        }
                    } else {
                        if (messages?.isEmpty() ?: true) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = if (stream?.encryption == true) Alignment.TopCenter else Alignment.Center
                            ) {
                                if (stream?.encryption == true) EncryptedChatIntroduction(
                                    encryptedChatState.canRotate, encryptedChatState,
                                    history.showInitialKeyRequest,
                                    viewModel::requestEncryptionKey,
                                ) else Text(
                                    text = "Сообщений нет",
                                    color = LocalWorkspaceColorsPalette.current.textAdditional50,
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = if (selectionMode) 0.dp else 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(
                                    space = 8.dp,
                                    alignment = if (stream?.encryption == true) Alignment.Top else Alignment.Bottom
                                )
                            ) {
                                items(items = messages, key = { it.uuid }) { item ->
                                    if (stream?.encryption == true && item.uuid == history.firstMessageUuid) {
                                        EncryptedChatIntroduction(
                                            encryptedChatState.canRotate, encryptedChatState,
                                            history.showInitialKeyRequest,
                                            viewModel::requestEncryptionKey,
                                        )
                                    }
                                    ChatMessage(
                                        item,
                                        viewModel,
                                        navController,
                                        history,
                                        {
                                            if (viewModel.shouldScrollToBottom) {
                                                scope.launch {
                                                    listState.scrollToItem(messages.lastIndex)
                                                }
                                            }
                                        },
                                        onForwardMessage = { message ->
                                            forwardingSelection = false
                                            forwardingMessages = listOf(message.copy(payload = message.payload.copy()))
                                        },
                                    )
                                }
                            }
                        }
                        if (!selectionMode) {
                            if (stream != null) {
                                SendMessageView(viewModel, canSend = stream?.encryption != true || encryptedChatState.sendingKeyUuid != null)
                            }
                        }
                    }
                }
            }
        }
        forwardingMessages?.let { sources ->
            ForwardMessagesAction(
                sources = sources,
                sessionStore = viewModel.forwardSession,
                client = viewModel.client,
                repo = viewModel.repo,
                userViewModel = viewModel.userViewModel,
                onDismiss = { forwardingMessages = null },
                onForwarded = {
                    forwardingMessages = null
                    if (forwardingSelection) viewModel.clearMessageSelection()
                },
            )
        }

    }
}

private data class MeasuredChatBottom(
    val itemIndex: Int,
    val itemCount: Int,
    val itemOffset: Int,
    val itemSize: Int,
    val viewportEnd: Int,
    val isScrolling: Boolean,
)

data class UserUploadMarkdownParts(
    val caption: String,
    val fileName: String,
    val relativePath: String,
)
private val captionThenLink = Regex(
    """^(?:(.*?)\r?\n)?!?\[([^\]]+)\]\((urn:image:[^?)]+(?:\?[^)]*)?)\)""",
)

fun String.parseUserUploadMarkdownOrNull(): UserUploadMarkdownParts? {
    val m = captionThenLink.find(this) ?: return null
    val caption = m.groupValues[1]
    val fileName = m.groupValues[2]
    val path = m.groupValues[3]
    return UserUploadMarkdownParts(caption, fileName, path)
}

@Composable
internal fun ChatMessage(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    history: ChatHistoryPresentation,
    onImageLoad: () -> Unit,
    onForwardMessage: ((MessageResponse) -> Unit)? = null,
) {
    val selectedMessageUuids by viewModel.selectedMessageUuids.collectAsStateWithLifecycle()
    val selectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val deletingSelected by viewModel.deletingSelectedMessages.collectAsStateWithLifecycle()
    val encryptedChatState by viewModel.encryptedChatState.collectAsStateWithLifecycle()
    val encryptedPresentations by viewModel.encryptedPresentations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val messageFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag("chat-message-${item.uuid}"),
    ) {
        val previousMessage = viewModel.previousMessageByUuid(item.uuid)
        if (previousMessage != null) {
            val currentMessageDate = LocalDateTime.parse(item.createdAt, messageFormatter).toLocalDate()
            val previousMessageDate = LocalDateTime.parse(previousMessage.createdAt, messageFormatter).toLocalDate()
            if (currentMessageDate != previousMessageDate) {
                val zone = ZoneId.systemDefault()
                val locale = LocalLocale.current.platformLocale
                val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
                Text(
                    text = currentMessageDate.format(formatter),
                    color = LocalWorkspaceColorsPalette.current.textAdditional50,
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .border(
                            width = 1.dp,
                            color = LocalWorkspaceColorsPalette.current.divider,
                            shape = RoundedCornerShape(100.dp)
                        )
                        .background(
                            LocalWorkspaceColorsPalette.current.cardBackgroundBase,
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                )
            }
//            if (previousMessage.flags.contains("read") && !item.flags.contains("read")) {
//                Text(
//                    text = "Новые сообщения",
//                    color = LocalWorkspaceColorsPalette.current.textHeaders,
//                    fontSize = 14.sp,
//                    modifier = Modifier
//                        .padding(vertical = 16.dp)
//                        .background(
//                            LocalWorkspaceColorsPalette.current.surface,
//                            shape = RoundedCornerShape(100.dp)
//                        )
//                        .padding(vertical = 4.dp, horizontal = 12.dp)
//                )
//                }
        }
        SelectableMessageRow(
            messageUuid = item.uuid,
            description = item.description(stringResource(R.string.quote_forwarded_message)),
            selectionMode = selectionMode,
            selected = item.uuid in selectedMessageUuids,
            enabled = viewModel.canSelectMessage(item) && !deletingSelected,
            onToggle = { viewModel.toggleMessageSelection(item) },
        ) {
            val encryptedPresentation = encryptedPresentations[item.uuid]
            if (encryptedPresentation is EncryptedChatPresentation.Locked) {
                val keyPending = messageKeyRequestPending(encryptedPresentation.keyUuid, encryptedChatState)
                LockedEncryptedMessageActions(item.uuid, keyPending, onRequestKey = { viewModel.requestEncryptionKeyForMessage(item) }) {
                    EncryptedChatLockedMessage(
                        keyUuid = encryptedPresentation.keyUuid,
                        status = encryptedChatState.requestStatus[encryptedPresentation.keyUuid] ?: EncryptedChatRequestStatus.IDLE,
                        invalidCiphertext = encryptedPresentation.invalidCiphertext,
                        showRequest = false,
                        pending = keyPending,
                        onRequestKey = { viewModel.requestEncryptionKeyForMessage(item) },
                    )
                }
            } else if (encryptedPresentation is EncryptedChatPresentation.Service) {
                val ownRequestKey = history.requestKey(item.streamUuid, encryptedPresentation.payload.keyUuid)
                EncryptedChatServiceMessage(
                    payload = encryptedPresentation.payload,
                    authorName = item.user?.displayableName() ?: stringResource(R.string.encrypted_chat_member),
                    requestOutcome = encryptedChatState.requests.firstOrNull { it.message.uuid == item.uuid }?.outcome,
                    canManage = encryptedChatState.canManageKeys,
                    pending = encryptedChatState.pending,
                    onGrant = { viewModel.grantEncryptionRequest(item.uuid) },
                    onReject = { viewModel.rejectEncryptionRequest(item.uuid) },
                    canGrant = encryptedChatState.localKeys.any { it.available && it.keyUuid == encryptedPresentation.payload.keyUuid },
                    isOwnDeviceRequest = item.authorUuid == history.viewerUuid && encryptedPresentation.payload.deviceId == history.deviceId,
                    isRecipientDevice = encryptedChatState.deviceId != null && encryptedPresentation.payload.recipientDeviceId == encryptedChatState.deviceId,
                    hasGenerationKey = encryptedChatState.localKeys.any { it.available && it.keyUuid == encryptedPresentation.payload.keyUuid },
                    isOwnerAnnouncement = encryptedChatState.canRotate && item.authorUuid == history.viewerUuid,
                    hasOwnGenerationRequest = ownRequestKey != null && ownRequestKey in history.ownGenerationRequests,
                    requestStatus = encryptedChatState.requestStatus[encryptedPresentation.payload.keyUuid],
                    onRequestKey = viewModel::requestEncryptionKey,
                    isLatestOwnGenerationRequest = ownRequestKey != null && history.latestOwnRequestUuids[ownRequestKey] == item.uuid,
                )
            } else if (item.payload.kind != "markdown" && item.payload.kind in setOf("e2ee", "key_request", "key_grant", "key_reject", "key_announced")) {
                // Never flash raw ciphertext while the scoped presentation is being prepared.
                val keyPending = messageKeyRequestPending(item.payload.keyUuid, encryptedChatState)
                if (item.payload.kind == "e2ee") {
                    LockedEncryptedMessageActions(item.uuid, keyPending, onRequestKey = { viewModel.requestEncryptionKeyForMessage(item) }) {
                        EncryptedChatLockedMessage(item.payload.keyUuid.orEmpty(), EncryptedChatRequestStatus.IDLE, false,
                            keyPending, showRequest = false, onRequestKey = { viewModel.requestEncryptionKeyForMessage(item) })
                    }
                } // Opaque service envelopes wait for their scoped presentation; never render them as messages.
            } else if (Patterns.WEB_URL.matcher(item.payload.content)
                    .matches() && item.payload.content.contains(viewModel.repo.jitsiServerUrl)
            ) {
                CallMessageView(item, viewModel, navController, onForwardMessage)
            } else {
                TextMessageView(item, viewModel, navController, onImageLoad, onForwardMessage)
            }
        }
    }
}

fun toUnicodeEmoji(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return value
    if (EmojiManager.isEmoji(value)) return value
    val alias = value.trim(':')
    val emojis = EmojiManager.getByAlias(alias).orElse(emptyList())
    val match = emojis.firstOrNull { emoji ->
        emoji.githubAliases.any { it.trim(':') == alias }
    } ?: emojis.firstOrNull()
    return match?.getEmoji() ?: value
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Long.formatHHmm(zoneId: ZoneId = ZoneId.systemDefault()): String =
    HHMM.format(Instant.ofEpochSecond(this).atZone(zoneId))

object JitsiStyleRoomNameGenerator {
    private val adjectives = listOf(
        "amber", "brisk", "calm", "clever", "daring", "eager", "fancy", "gentle",
        "jolly", "kind", "lucky", "merry", "nimble", "proud", "quick", "sunny",
        "tidy", "vivid", "witty", "zesty"
    )
    private val colorsOrQualifiers = listOf(
        "blue", "crimson", "golden", "green", "indigo", "ivory", "jade", "lavender",
        "orange", "pearl", "ruby", "silver", "teal", "violet"
    )
    private val nouns = listOf(
        "anchor", "badger", "beacon", "comet", "dolphin", "falcon", "forest", "harbor",
        "lantern", "meadow", "otter", "panda", "river", "rocket", "sparrow", "summit",
        "tiger", "valley", "willow", "zephyr"
    )

    fun generate(): String {
        val w1 = adjectives.random()
        val w2 = colorsOrQualifiers.random()
        val w3 = nouns.random()
        val base = buildCamelCase(w1, w2, w3)
        return base
    }
    private fun buildCamelCase(vararg parts: String): String {
        if (parts.isEmpty()) return ""
        val head = parts[0].lowercase()
        val tail = parts.drop(1).joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.titlecase() }
        }
        return head + tail
    }
}

private fun ruPlural(n: Long, one: String, few: String, many: String): String {
    val nAbs = kotlin.math.abs(n) % 100
    val n10 = nAbs % 10
    return when {
        nAbs in 11L..14L -> many
        n10 == 1L -> one
        n10 in 2L..4L -> few
        else -> many
    }
}
fun pastEpochSecondsToRelativeRu(
    pastEpochSeconds: Long,
    now: Instant = Instant.now()
): String {
    val past = Instant.ofEpochSecond(pastEpochSeconds)
    val seconds = Duration.between(past, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "только что"
        seconds < 3600 -> {
            val m: Long = seconds / 60
            val word = ruPlural(m, "минуту", "минуты", "минут")
            "$m $word назад"
        }
        seconds < 86400 -> {
            val h: Long = seconds / 3600
            val word = ruPlural(h, "час", "часа", "часов")
            "$h $word назад"
        }
        seconds < 604800 -> {
            val d: Long = seconds / 86400
            val word = ruPlural(d, "день", "дня", "дней")
            "$d $word назад"
        }
        else -> {
            val z = java.time.ZoneId.systemDefault()
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("ru", "RU"))
                .withZone(z)
                .format(past)
        }
    }
}
