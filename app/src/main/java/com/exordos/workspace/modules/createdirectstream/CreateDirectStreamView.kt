package com.exordos.workspace.modules.createdirectstream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable
import com.exordos.workspace.Chat
import com.exordos.workspace.ChatFlow
import com.exordos.workspace.LoginFlow
import com.exordos.workspace.R
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.modules.chatdialog.MentionTextFieldState
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.modules.users.matchesUserSearch
import com.exordos.workspace.modules.users.UserSearchField
import com.exordos.workspace.modules.users.UserCell
import com.exordos.workspace.modules.users.UsersViewModel
import com.exordos.workspace.ui.Avatar
import com.exordos.workspace.ui.theme.InterFontFamily
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDirectStreamView(
    viewModel: CreateDirectStreamViewModel,
    navController: NavHostController
) {
    val users by viewModel.users.collectAsState()
    val state by viewModel.createQueryState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    val filteredUsers = remember(searchQuery, users) {
        users.filter { it.matchesUserSearch(searchQuery) }
    }

    LaunchedEffect(state) {
        if (state is QueryState.Success) {
            val createdStream = viewModel.createdStream
            if (createdStream != null) {
                viewModel.createdStream = null
                navController.navigate(
                    ChatFlow.ChatDialog(
                        createdStream.name,
                        createdStream.uuid,
                        null,
                        createdStream.defaultTopicUuid ?: "",
                        true,
                        null
                    )
                ) {
                    popUpTo<ChatFlow.ChatList> {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Начать чат",
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
        Box(Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(LocalWorkspaceColorsPalette.current.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                UserSearchField(searchQuery, { searchQuery = it })
                if (state is QueryState.Error) {
                    Text(
                        text = (state as QueryState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    items(items = filteredUsers) { item ->
                        UserCell(
                            item,
                            {
                                viewModel.submitPrivateStream(item)
                            },
                            viewModel.client.userViewModel?.baseUrl?.collectAsState()?.value,
                            viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserCell(
    item: UserResponseData,
    onUserSelected: (UserResponseData) -> Unit,
    baseUrl: String?,
    viewModel: CreateDirectStreamViewModel,
) {
    Box {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .clip(
                    RoundedCornerShape(8.dp)
                )
                .clickable(
                    onClick = {
                        onUserSelected(item)
                    }
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    item.avatar,
                    baseUrl ?: "",
                    viewModel.client.authHeaders(),
                    null,
                    item.displayableName(),
                    Modifier.size(40.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                ) {
                    Row {
                        Text(
                            text = item.displayableName(),
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val email = item.email
                    if (email != null) {
                        Row {
                            Text(
                                text = email,
                                color = LocalWorkspaceColorsPalette.current.textAdditional50,
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    Column {
        Spacer(
            Modifier.weight(1f)
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = LocalWorkspaceColorsPalette.current.divider
        )
    }
}
