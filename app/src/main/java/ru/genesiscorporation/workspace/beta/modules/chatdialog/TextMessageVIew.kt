package ru.genesiscorporation.workspace.beta.modules.chatdialog

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
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
                            null,
                            item.user?.displayableName() ?: "",
                            30,
                            true
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(30.dp)
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
                        null,
                        item.user?.displayableName() ?: "",
                        30,
                        true
                    )
                }
            }
        }
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
                        fontWeight = FontWeight.Medium
                    )
                    EnhancedMarkdown(
                        markdown = item.payload.content,
                        style = TextStyle(
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp
                        ),
                        navController = navController,
                        viewModel = viewModel
                    )
                }
                Spacer(modifier = Modifier.widthIn(min = 20.dp))
                val instant = Instant.parse(item.createdAt)
                Text(
                    text = instant.atZone(zone).format(hhmmFormatter),
                    color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                    fontSize = 14.sp,
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
                if (item.isOwn) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = {
                            viewModel.onEditMessageClicked(item)
                            menuExpanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Цитировать") },
                    onClick = {
                        viewModel.onQuoteMessageClicked(item)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}