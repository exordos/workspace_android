package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChatDialogScreen(
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
) {
    val streamTopicMessages by viewModel.streamTopicMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uiMode = LocalConfiguration.current.uiMode
    val key = "${viewModel.chatId}.${viewModel.topicUuid}"
    val messages = remember(streamTopicMessages[key]) {
        streamTopicMessages[key].orEmpty().sortedBy {
            runCatching { LocalDateTime.parse(it.createdAt, viewModel.messageFormatter) }
                .getOrNull()
        }
    }

    LaunchedEffect(messages.size, uiMode) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = LocalWorkspaceColorsPalette.current.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ConversationHeader(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(LocalWorkspaceColorsPalette.current.background)
                .imePadding(),
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedGif(Modifier.size(72.dp))
                    }
                }

                messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Сообщений пока нет",
                            color = LocalWorkspaceColorsPalette.current.textAdditional50,
                            fontSize = 15.sp,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            space = 5.dp,
                            alignment = Alignment.Bottom,
                        ),
                    ) {
                        items(
                            items = messages,
                            key = { "${it.uuid}:${it.payload.content}" },
                        ) { message ->
                            ChatMessage(
                                item = message,
                                viewModel = viewModel,
                                navController = navController,
                                onImageLoad = {
                                    if (viewModel.shouldScrollToBottom && messages.isNotEmpty()) {
                                        val lastIndex = messages.lastIndex
                                        scope.launch {
                                            listState.scrollToItem(lastIndex)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            SendMessageView(viewModel)
        }
    }
}

@Composable
private fun ConversationHeader(
    viewModel: ChatDialogViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(
                colors.chatHeaderBackground,
                RoundedCornerShape(bottomStart = 13.dp, bottomEnd = 13.dp),
            )
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = "Назад",
                tint = colors.iconBase,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = viewModel.chatTitle,
                color = colors.textHeaders,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            viewModel.topicName?.takeIf { it.isNotBlank() }?.let { topic ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(width = 3.dp, height = 18.dp)
                            .background(colors.indicatorYellow, RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = "# $topic",
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Button(
            onClick = {
                val roomName = JitsiStyleRoomNameGenerator.generate()
                val messageText = "${viewModel.repo.jitsiServerUrl}/$roomName"
                scope.launch { viewModel.sendTextMessage(messageText) }
                runCatching {
                    val options = JitsiMeetConferenceOptions.Builder()
                        .setServerURL(URL(viewModel.repo.jitsiServerUrl))
                        .setRoom(roomName)
                        .build()
                    JitsiMeetActivity.launch(context, options)
                }
            },
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = colors.indicatorGreen,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.call),
                contentDescription = "Начать звонок",
            )
        }
    }
}

data class UserUploadMarkdownParts(
    val caption: String,
    val attachments: List<UserUploadAttachment>,
) {
    val fileName: String
        get() = attachments.first().fileName

    val relativePath: String
        get() = attachments.first().relativePath
}

data class UserUploadAttachment(
    val fileName: String,
    val relativePath: String,
)

private val markdownUpload = Regex("""!?\[([^\]]*)]\((urn:image:[^)]+)\)""")
private val legacyUpload = Regex("""\(([^)]+)\)\s*\[(urn:image:[^\]]+)]""")

fun String.parseUserUploadMarkdownOrNull(): UserUploadMarkdownParts? {
    val markdownMatches = markdownUpload.findAll(this).toList()
    val matches = markdownMatches.ifEmpty { legacyUpload.findAll(this).toList() }
    if (matches.isEmpty()) return null

    val attachmentPattern = if (markdownMatches.isNotEmpty()) markdownUpload else legacyUpload
    return UserUploadMarkdownParts(
        caption = replace(attachmentPattern, "")
            .lineSequence()
            .joinToString("\n") { it.trimEnd() }
            .trim(),
        attachments = matches.map { match ->
            UserUploadAttachment(
                fileName = match.groupValues[1],
                relativePath = match.groupValues[2],
            )
        },
    )
}

@Composable
fun ChatMessage(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    onImageLoad: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val previous = viewModel.previousMessageByUuid(item.uuid)
    val currentDate = runCatching {
        LocalDateTime.parse(item.createdAt, viewModel.messageFormatter).toLocalDate()
    }.getOrNull()
    val previousDate = previous?.let {
        runCatching {
            LocalDateTime.parse(it.createdAt, viewModel.messageFormatter).toLocalDate()
        }.getOrNull()
    }
    val locale = LocalLocale.current.platformLocale
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start,
    ) {
        if (currentDate != null && currentDate != previousDate) {
            Text(
                text = currentDate.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                color = colors.textAdditional50,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .background(colors.surface, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val upload = item.payload.content.parseUserUploadMarkdownOrNull()
        val jitsiBaseUrl = viewModel.repo.jitsiServerUrl
        when {
            jitsiBaseUrl.isNotBlank() &&
                Patterns.WEB_URL.matcher(item.payload.content).matches() &&
                item.payload.content.startsWith(jitsiBaseUrl) ->
                CallMessageView(item, viewModel, navController)

            upload != null ->
                ImageMessageView(
                    text = upload.caption,
                    imageUrls = upload.attachments.map { it.relativePath },
                    viewModel = viewModel,
                    item = item,
                    navController = navController,
                    onImageLoad = onImageLoad,
                )

            else ->
                TextMessageView(item, viewModel, navController)
        }

        if (item.reactions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(
                    start = if (!item.isOwn && !viewModel.isDirectMessages) 44.dp else 0.dp,
                    top = 3.dp,
                ),
            ) {
                item.reactions.forEach { (emoji, count) ->
                    val selected = viewModel.hasMyReaction(emoji, item.uuid)
                    Row(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.primary else colors.iconDisable,
                                shape = CircleShape,
                            )
                            .background(
                                if (selected) colors.primary.copy(alpha = 0.16f) else colors.surface,
                                CircleShape,
                            )
                            .clickable {
                                scope.launch {
                                    viewModel.onMessageReactionTap(item.uuid, emoji)
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = emoji, fontSize = 16.sp)
                        if (count > 1) {
                            Text(
                                text = count.toString(),
                                color = colors.textAdditional50,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Long.formatHHmm(zoneId: ZoneId = ZoneId.systemDefault()): String =
    HHMM.format(Instant.ofEpochSecond(this).atZone(zoneId))

object JitsiStyleRoomNameGenerator {
    private val adjectives = listOf(
        "amber", "brisk", "calm", "clever", "daring", "eager", "fancy", "gentle",
        "jolly", "kind", "lucky", "merry", "nimble", "proud", "quick", "sunny",
        "tidy", "vivid", "witty", "zesty",
    )
    private val qualifiers = listOf(
        "blue", "crimson", "golden", "green", "indigo", "ivory", "jade", "lavender",
        "orange", "pearl", "ruby", "silver", "teal", "violet",
    )
    private val nouns = listOf(
        "anchor", "badger", "beacon", "comet", "dolphin", "falcon", "forest", "harbor",
        "lantern", "meadow", "otter", "panda", "river", "rocket", "sparrow", "summit",
        "tiger", "valley", "willow", "zephyr",
    )

    fun generate(): String {
        val parts = listOf(adjectives.random(), qualifiers.random(), nouns.random())
        return parts.first().lowercase() +
            parts.drop(1).joinToString("") { it.lowercase().replaceFirstChar(Char::titlecase) }
    }
}

private fun ruPlural(n: Long, one: String, few: String, many: String): String {
    val absolute = kotlin.math.abs(n) % 100
    val last = absolute % 10
    return when {
        absolute in 11L..14L -> many
        last == 1L -> one
        last in 2L..4L -> few
        else -> many
    }
}

fun pastEpochSecondsToRelativeRu(
    pastEpochSeconds: Long,
    now: Instant = Instant.now(),
): String {
    val past = Instant.ofEpochSecond(pastEpochSeconds)
    val seconds = Duration.between(past, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "только что"
        seconds < 3600 -> {
            val minutes = seconds / 60
            "$minutes ${ruPlural(minutes, "минуту", "минуты", "минут")} назад"
        }
        seconds < 86400 -> {
            val hours = seconds / 3600
            "$hours ${ruPlural(hours, "час", "часа", "часов")} назад"
        }
        seconds < 604800 -> {
            val days = seconds / 86400
            "$days ${ruPlural(days, "день", "дня", "дней")} назад"
        }
        else -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru-RU"))
            .withZone(ZoneId.systemDefault())
            .format(past)
    }
}
