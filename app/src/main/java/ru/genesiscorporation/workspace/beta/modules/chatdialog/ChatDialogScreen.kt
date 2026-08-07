@file:OptIn(ExperimentalMaterial3Api::class)

package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.R.attr.end
import android.net.Uri
import android.util.Patterns
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
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
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
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
    val streamTopicMessages by viewModel.streamTopicMessages.collectAsStateWithLifecycle()
    val streamBindings by viewModel.streamBindings.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val messageFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    var hasDoneInitialScroll by remember { mutableStateOf(false) }
    var imeHeight = remember { mutableStateOf(0) }
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
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
    LaunchedEffect(streamTopicMessages["${viewModel.chatId}.${viewModel.topicUuid ?: ""}"]?.size) {
        val messages = streamTopicMessages["${viewModel.chatId}.${viewModel.topicUuid ?: ""}"]
        if (messages != null) {
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.lastIndex)
                hasDoneInitialScroll = true
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) {
                    viewModel.onScroll()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                expandedHeight = 60.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column(modifier = Modifier
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
                        )) {
                        val title = if (viewModel.isDirectMessages) viewModel.chatTitle else viewModel.topicName ?: viewModel.chatTitle
                        Text(
                            title,
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 16.sp,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val currentStreamBindings = streamBindings[viewModel.chatId]
                        if (currentStreamBindings != null && currentStreamBindings.count() > 0) {
                            val currentBindedOnlineUsers = currentStreamBindings.mapNotNull { binding -> users.firstOrNull { binding.userUuid == it.uuid && it.status == "active" } }
                            var baseText = context.resources.getQuantityString(
                                R.plurals.participants_count, currentStreamBindings.count(), currentStreamBindings.count()
                            )
                            if (currentBindedOnlineUsers.count() > 0) {
                                baseText += ", ${currentBindedOnlineUsers.count()} онлайн"
                            }
                            Text(
                                baseText,
                                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                fontSize = 14.sp,
                                fontFamily = InterFontFamily,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
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
        val imeVisible = WindowInsets.isImeVisible
        val navBarHeight = 70.dp
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(
                    top = if (imeVisible) 0.dp else innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                )
                .windowInsetsPadding(
                    WindowInsets.ime
                        .exclude(WindowInsets.navigationBars)
                        .only(WindowInsetsSides.Bottom)
                )
                .offset {
                    val extra = if (imeVisible) {
                        with(density) { navBarHeight.roundToPx() }
                    } else 0
                    IntOffset(0, extra)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalWorkspaceColorsPalette.current.background)
            ) {
                val messages = streamTopicMessages["${viewModel.chatId}.${viewModel.topicUuid ?: ""}"]
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
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Сообщений нет"
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(
                                space = 8.dp,
                                alignment = Alignment.Bottom
                            )
                        ) {
                            items(items = messages.sortedBy { LocalDateTime.parse(it.createdAt, messageFormatter) }, key = { "${it.uuid}${it.payload.content}" }) { item ->
                                ChatMessage(
                                    item,
                                    viewModel,
                                    navController,
                                    {
                                        if (viewModel.shouldScrollToBottom) {
                                            scope.launch {
                                                listState.scrollToItem(messages.lastIndex)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    SendMessageView(viewModel)
                }
            }
        }
    }
}

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
fun ChatMessage(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    onImageLoad: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val messageFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
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
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            if (Patterns.WEB_URL.matcher(item.payload.content)
                    .matches() && item.payload.content.contains(viewModel.repo.jitsiServerUrl)
            ) {
                CallMessageView(item, viewModel, navController)
            } else if (item.payload.content.parseUserUploadMarkdownOrNull() != null) {
                val text = item.payload.content.parseUserUploadMarkdownOrNull()!!.caption
                val imageName = item.payload.content.parseUserUploadMarkdownOrNull()!!.fileName
                val imageUrl = item.payload.content.parseUserUploadMarkdownOrNull()!!.relativePath
                ImageMessageView(text, "$imageUrl", viewModel, item, navController, onImageLoad)
            } else {
                TextMessageView(item, viewModel, navController)
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