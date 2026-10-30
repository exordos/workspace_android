package com.exordos.workspace.modules.homementions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import com.exordos.workspace.data.WorkspaceDateTime
import com.exordos.workspace.ChatFlow
import com.exordos.workspace.HomeFlow
import com.exordos.workspace.R
import com.exordos.workspace.modules.chatdialog.QuotedMessagePartView
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.modules.creationbase.CreationBaseViewModel
import com.exordos.workspace.ui.AnimatedGif
import com.exordos.workspace.ui.Avatar
import com.exordos.workspace.ui.EnhancedMarkdown
import com.exordos.workspace.ui.FullScreenError
import com.exordos.workspace.ui.theme.InterFontFamily
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMentionsScreen(
    viewModel: HomeMentionsViewModel,
    navController: NavHostController
) {

    val messagesQueryState by viewModel.messagesQueryState.collectAsStateWithLifecycle()
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Упоминания",
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                    navigationIconContentColor = LocalWorkspaceColorsPalette.current.textHeaders
                )
            )
        },
        containerColor = LocalWorkspaceColorsPalette.current.background
    ) { innerPadding ->
        when (messagesQueryState) {
            QueryState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    AnimatedGif(Modifier.size(80.dp))
                }
            }

            is QueryState.Error -> {
                FullScreenError {
                    scope.launch {
                        viewModel.loadMentionedMessages()
                    }
                }
            }

            QueryState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        items(
                            items = messages.sortedByDescending  { WorkspaceDateTime.parse(it.createdAt) }
                        ) { item ->

                            val stream = streams.firstOrNull { it.uuid == item.streamUuid }
                            if (stream != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable(
                                            onClick = {
                                                navController.navigate(
                                                    HomeFlow.ChatDialog(
                                                        stream.displayableName(),
                                                        stream.uuid,
                                                        "",
                                                        stream.defaultTopicUuid ?: "",
                                                        stream.isPrivate,
                                                        null
                                                    )
                                                )
                                            }
                                        ),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Avatar(
                                        item.user?.avatar,
                                        viewModel.client.userViewModel?.baseUrl?.value ?: "",
                                        viewModel.client.authHeaders(),
                                        null,
                                        item.user?.displayableName() ?: "",
                                        Modifier.padding(16.dp)
                                            .size(30.dp)
                                    )
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.Start,
                                            modifier = Modifier
                                                .weight(1f)
                                        ) {
                                            val defaultName = if (item.isOwn) "Я" else "Собеседник"
                                            Text(
                                                text = item.user?.displayableName() ?: defaultName,
                                                color = if (item.isOwn) LocalWorkspaceColorsPalette.current.indicatorBlue else LocalWorkspaceColorsPalette.current.indicatorPurple,
                                                fontSize = 14.sp,
                                                fontFamily = InterFontFamily,
                                                fontWeight = FontWeight.Medium
                                            )
                                            EnhancedMarkdown(
                                                markdown = item.description(stringResource(R.string.quote_forwarded_message)),
                                                style = TextStyle(
                                                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                                                    fontSize = 14.sp,
                                                    fontFamily = InterFontFamily,
                                                ),
                                                navController = navController,
                                                viewModel = null
                                            )
                                        }
                                        Spacer(modifier = Modifier.widthIn(min = 20.dp))
                                        val messageTime = WorkspaceDateTime.formatTime(item.createdAt)
                                        Text(
                                            text = messageTime,
                                            color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                                            fontSize = 14.sp,
                                            fontFamily = InterFontFamily,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
