package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL

@Composable
fun CallMessageView(
    item: Message,
    viewModel: ChatDialogViewModel,
    navController: NavHostController
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