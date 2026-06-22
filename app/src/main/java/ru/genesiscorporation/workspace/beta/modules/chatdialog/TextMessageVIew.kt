package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun TextMessageView(
    item: Message,
    viewModel: ChatDialogViewModel,
    navController: NavHostController
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
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isFromCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        val nextMessage = viewModel.nextMessageById(item.id)
        if (!viewModel.isDirectMessages && !item.isFromCurrentUser) {
            if (nextMessage != null) {
                if (nextMessage.senderId != item.senderId) {
                    Box(
                        Modifier
                            .clickable(
                                onClick = {
                                    navController.navigate(
                                        ChatFlow.ChatUserInfo(
                                            item.senderFullName,
                                            "${item.senderId}",
                                            item.avatarUrl,
                                            ""
                                        )
                                    )
                                }
                            )
                    ) {
                        Avatar(
                            item.avatarUrl,
                            viewModel.userViewModel.baseUrl.value ?: "",
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
                                        item.senderFullName,
                                        "${item.senderId}",
                                        item.avatarUrl,
                                        ""
                                    )
                                )
                            }
                        )
                ) {
                    Avatar(
                        item.avatarUrl,
                        viewModel.userViewModel.baseUrl.value ?: "",
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
                        if (item.isFromCurrentUser)
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
                    Text(
                        text = item.senderFullName,
                        color = if (item.isFromCurrentUser) LocalWorkspaceColorsPalette.current.indicatorBlue else LocalWorkspaceColorsPalette.current.indicatorPurple,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    EnhancedMarkdown(
                        markdown = item.content,
                        style = TextStyle(
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.widthIn(min = 20.dp))
                Text(
                    text = item.timestamp.formatHHmm(),
                    color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                    fontSize = 14.sp,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (item.isFromCurrentUser) {
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