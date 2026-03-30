package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant

@Composable
fun ChatDialogScreen(
    viewModel: ChatDialogViewModel,
    navController: NavHostController
) {
    val messages by viewModel.messages.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var hasDoneInitialScroll by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            hasDoneInitialScroll = true
        }
    }

    Box(modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(LocalWorkspaceColorsPalette.current.background)
        ) {
            Row(
                modifier = Modifier
                    .background(LocalWorkspaceColorsPalette.current.surface)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .padding(end = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = LocalWorkspaceColorsPalette.current.iconBase
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_back),
                        contentDescription = "Back"
                    )
                }
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        viewModel.chatTitle,
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (viewModel.topic != null) {
                        Text(
                            viewModel.topic,
                            color = LocalWorkspaceColorsPalette.current.textAdditional30,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f)
                )
                Button(
                    onClick = {
                        val roomName = JitsiStyleRoomNameGenerator.generate()
                        val messageText = "https://meet.example.com/${roomName}"
                        scope.launch {
                            viewModel.sendMessage(messageText)
                        }
                        val options = JitsiMeetConferenceOptions.Builder()
                            .setServerURL(URL("https://meet.example.com"))
                            .setRoom(roomName)
                            .build()

                        JitsiMeetActivity.launch(context, options)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = LocalWorkspaceColorsPalette.current.iconBase
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.call),
                        contentDescription = "Call"
                    )
                }
            }
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading"
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        space = 8.dp,
                        alignment = Alignment.Bottom
                    )
                ) {
                    items(items = messages) { item ->
                        ChatMessage(item)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 60.dp)
                        .background(LocalWorkspaceColorsPalette.current.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .weight(1f)
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                LocalWorkspaceColorsPalette.current.background,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicTextField(
                            value = messageText,
                            onValueChange = viewModel::onMessageChange,
                            textStyle = TextStyle(
                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                fontSize = 16.sp
                            ),
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.onSendClicked()
                            }
                        },
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalWorkspaceColorsPalette.current.primary,
                            contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.send),
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessage(
    item: Message
) {
    if (Patterns.WEB_URL.matcher(item.content).matches() && URL(item.content).host == "meet.example.com") {
        CallMessageView(item)
    } else {
        TextMessageView(item)
    }

}

@Composable
fun TextMessageView(
    item: Message
) {
    val bubbleShape = if (item.isFromCurrentUser) {
        RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 8.dp,
            bottomEnd = 0.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 0.dp,
            bottomEnd = 8.dp
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isFromCurrentUser) Arrangement.End else Arrangement.Start,
        ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .background(
                    if (item.isFromCurrentUser)
                    LocalWorkspaceColorsPalette.current.messageOwnBackground
                else LocalWorkspaceColorsPalette.current.messageBackground,
                    shape = bubbleShape
                )
                .padding(10.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .weight(2f, fill = false)
            ) {
                Text(
                    text = item.senderFullName,
                    color = if (item.isFromCurrentUser) LocalWorkspaceColorsPalette.current.indicatorBlue else LocalWorkspaceColorsPalette.current.indicatorPurple,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.content,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.widthIn(min = 20.dp))
            Text(
                text = item.timestamp.formatHHmm(),
                color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun CallMessageView(
    item: Message
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val itemUrl = URL(item.content)
    val bubbleShape = if (item.isFromCurrentUser) {
        RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 8.dp,
            bottomEnd = 0.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 0.dp,
            bottomEnd = 8.dp
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isFromCurrentUser) Arrangement.End else Arrangement.Start,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .background(
                    LocalWorkspaceColorsPalette.current.messageActiveCallBackground,
                    shape = bubbleShape
                )
                .padding(10.dp)
                .clickable {
                    val options = JitsiMeetConferenceOptions.Builder()
                        .setServerURL(URL("https://meet.example.com"))
                        .setRoom(itemUrl.path.drop(1))
                        .build()

                    JitsiMeetActivity.launch(context, options)
                }
        ) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row {
                    Text(
                        text = "Звонок",
                        color = LocalWorkspaceColorsPalette.current.indicatorGreen,
                        fontSize = 14.sp
                    )
                    Text(
                        text = itemUrl.path.drop(1),
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                    )
                    Icon(
                        painter = painterResource(R.drawable.call),
                        "Call",
                        tint = LocalWorkspaceColorsPalette.current.indicatorGreen
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = item.timestamp.formatHHmm(),
                        color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Int.formatHHmm(zoneId: ZoneId = ZoneId.systemDefault()): String =
    HHMM.format(Instant.ofEpochSecond(this.toLong()).atZone(zoneId))

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