package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.QuotedMessagePart
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
import ru.genesiscorporation.workspace.beta.ui.ReferenceMessageBase
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TextMessageView(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController
) {
    val quotedMessages by viewModel.quotedMessages.collectAsState()
    val zone = ZoneId.systemDefault()
    val hhmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val scope = rememberCoroutineScope()
    val bubbleShape = if (item.isOwn) {
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
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        val nextMessage = viewModel.nextMessageByUuid(item.uuid)
        if (!viewModel.isDirectMessages && !item.isOwn) {
            val bottomPadding = if (item.reactions.isEmpty()) 0.dp else 8.dp
            if (nextMessage != null) {
                if (nextMessage.authorUuid != item.authorUuid) {
                    Box(
                        Modifier
                            .clickable(
                                onClick = {
                                    navController.navigate(
                                        ChatFlow.ChatUserInfo(
                                            item.user?.displayableName() ?: "",
                                            item.authorUuid,
                                            item.user?.avatar ?: "",
                                            ""
                                        )
                                    )
                                }
                            )
                    ) {
                        Avatar(
                            item.user?.avatar,
                            viewModel.userViewModel.baseUrl.value ?: "",
                            viewModel.client.authHeaders(),
                            null,
                            item.user?.displayableName() ?: "",
                            Modifier.padding(end = 4.dp, bottom = bottomPadding)
                                .size(30.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(color = Color.Transparent, shape = CircleShape)
                    )
                }
            } else {
                Box(
                    Modifier
                        .clickable(
                            onClick = {
                                navController.navigate(
                                    ChatFlow.ChatUserInfo(
                                        item.user?.displayableName() ?: "",
                                        item.authorUuid,
                                        item.user?.avatar ?: "",
                                        item.user?.email ?: ""
                                    )
                                )
                            }
                        )
                ) {
                    Avatar(
                        item.user?.avatar,
                        viewModel.userViewModel.baseUrl.value ?: "",
                        viewModel.client.authHeaders(),
                        null,
                        item.user?.displayableName() ?: "",
                        Modifier.padding(end = 4.dp, bottom = bottomPadding)
                            .size(30.dp)
                    )
                }
            }
        }
        Column {
            Box {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .background(
                            if (item.isOwn)
                                LocalWorkspaceColorsPalette.current.messageOwnBackground
                            else LocalWorkspaceColorsPalette.current.messageBackground,
                            shape = bubbleShape
                        )
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                menuExpanded = true
                            }
                        )
                        .padding(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            .weight(2f, fill = false)
                    ) {
                        val defaultName = if (item.isOwn) "Я" else "Собеседник"
                        Text(
                            text = item.user?.displayableName() ?: defaultName,
                            color = if (item.isOwn) LocalWorkspaceColorsPalette.current.indicatorBlue else LocalWorkspaceColorsPalette.current.indicatorPurple,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        item.asQuotedMessages().forEach {
                            QuotedMessagePartView(
                                it,
                                item.isOwn,
                                viewModel,
                                navController
                            )
                        }
                    }
                    Spacer(modifier = Modifier.widthIn(min = 20.dp))
                    val instant = Instant.parse(item.createdAt)
                    Text(
                        text = instant.atZone(zone).format(hhmmFormatter),
                        color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("👍", "❤️", "😂", "😮", "😢").forEach { emoji ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.onReactionTap(item.uuid, emoji)
                                    }
                                    menuExpanded = false
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_quote),
                                    contentDescription = ""
                                )
                                Text(
                                    "Ответить",
                                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                                    fontSize = 14.sp,
                                    fontFamily = InterFontFamily
                                )
                            }
                        },
                        onClick = {
                            viewModel.onQuoteMessageClicked(item)
                            menuExpanded = false
                        }
                    )
                    if (quotedMessages.count() > 0) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_add_quote),
                                        contentDescription = ""
                                    )
                                    Text(
                                        "Добавить цитату",
                                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                                        fontSize = 14.sp,
                                        fontFamily = InterFontFamily
                                    )
                                }
                            },
                            onClick = {
                                viewModel.onAddQuoteMessageClicked(item)
                                menuExpanded = false
                            }
                        )
                    }
                    if (item.isOwn) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_edit),
                                        contentDescription = ""
                                    )
                                    Text(
                                        "Изменить",
                                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                                        fontSize = 14.sp,
                                        fontFamily = InterFontFamily
                                    )
                                }
                            },
                            onClick = {
                                viewModel.onEditMessageClicked(item)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
            if (!item.reactions.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (reaction in item.reactions) {
                        val hasMyReaction = viewModel.hasMyReaction(reaction.key, item.uuid)
                        val unicodeEmoji = remember(reaction.key) { toUnicodeEmoji(reaction.key) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .padding(top = 4.dp, bottom = 8.dp)
                                .background(
                                    if (hasMyReaction) LocalWorkspaceColorsPalette.current.primary else LocalWorkspaceColorsPalette.current.cardBackgroundActive,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable(
                                    onClick = {
                                        scope.launch {
                                            viewModel.onMessageReactionTap(item.uuid, reaction.key)
                                        }
                                    }
                                ),
                        ) {
                            Text(unicodeEmoji)
                            Text(text ="${reaction.value}",
                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable fun QuotedMessagePartView(
    quotedMessagePart: QuotedMessagePart,
    isOwn: Boolean,
    viewModel: ChatDialogViewModel,
    navController: NavHostController
) {
    val streamTopicMessages by viewModel.streamTopicMessages.collectAsStateWithLifecycle()
    Column() {
        val quotedMessageUuid = quotedMessagePart.uuid
        if (quotedMessageUuid != null) {
            val messages = streamTopicMessages["${viewModel.chatId}.${viewModel.topicUuid ?: ""}"]
            val message = messages?.firstOrNull { it.uuid == quotedMessageUuid }
            ReferenceMessageBase(
                Modifier
                    .padding(vertical = 4.dp),
                shouldClose = false, onCloseTap = {}
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background( if (isOwn) LocalWorkspaceColorsPalette.current.messageOwnSelectedBg else LocalWorkspaceColorsPalette.current.messageOwnBackground )
                        .padding(8.dp)
                ) {
                    Text(
                        message?.user?.displayableName() ?: "Цитируемое сообщение",
                        color = LocalWorkspaceColorsPalette.current.indicatorOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val messageQuotedParts = message?.asQuotedMessages()
                    if (messageQuotedParts != null) {
                        messageQuotedParts.forEach {
                            QuotedMessagePartView(
                                it,
                                isOwn,
                                viewModel,
                                navController
                            )
                        }
                    } else {
                        Text(
                            "Не удалось загрузить сообщение",
                            color = LocalWorkspaceColorsPalette.current.textAdditional30,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        EnhancedMarkdown(
            markdown = quotedMessagePart.text,
            style = TextStyle(
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
            ),
            navController = navController,
            viewModel = viewModel
        )
    }
}