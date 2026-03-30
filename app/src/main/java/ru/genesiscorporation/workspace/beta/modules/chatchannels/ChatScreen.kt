package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController
) {
    val subscriptions by chatViewModel.subscriptions.collectAsState()

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
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(LocalWorkspaceColorsPalette.current.surface)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Мессенджер",
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (subscriptions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading"
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    items(items = subscriptions) { item ->
                        ChatChannel(item, navController)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatChannel(
    item: ChatHeader,
    navController: NavHostController
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp)
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(LocalWorkspaceColorsPalette.current.cardBackgroundBase)
            .clickable(
                onClick = {
                    if (item.isDirectMessages) {
                        navController.navigate(
                            ChatFlow.ChatDialog(
                                item.title,
                                item.streamId,
                                null,
                                true
                            )
                        )
                    } else {
                        navController.navigate(
                            ChatFlow.ChatTopic(
                                item.title,
                                item.streamId
                            )
                        )
                    }
                }
            )
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
        ) {
            Row {
                Text(
                    text = item.title,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.lastMessage != null) {
                Row {
                    Text(
                        text = item.lastMessage.content,
                        color = LocalWorkspaceColorsPalette.current.textAdditional50,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.fillMaxWidth())
//                Text(
//                    text = "3"
//                )
                }
            }
        }
    }
}