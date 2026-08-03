package ru.genesiscorporation.workspace.beta.modules.createdirectstream

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.Chat
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.users.UserCell
import ru.genesiscorporation.workspace.beta.modules.users.UsersViewModel
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun CreateDirectStreamView(
    viewModel: CreateDirectStreamViewModel,
    navController: NavHostController
) {
    val scope = rememberCoroutineScope()
    val users by viewModel.users.collectAsState()
    val state by viewModel.createQueryState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    val filteredUsers = remember(searchQuery, users) {
        if (searchQuery.isBlank()) {
            users
        } else {
            users.filter {
                it.lastName?.contains(searchQuery, ignoreCase = true) ?: false || it.firstName?.contains(searchQuery, ignoreCase = true) ?: false || it.email?.contains(searchQuery, ignoreCase = true) ?: false
            }
        }
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

    Box(Modifier.fillMaxSize()) {
        TextButton(
            onClick = { navController.navigateUp() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Text("Close")
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp, horizontal = 16.dp)
                    .background(
                        LocalWorkspaceColorsPalette.current.searchBackground,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(
                        color = LocalWorkspaceColorsPalette.current.textAdditional30,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                    ),
                    cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textAdditional30),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Поиск...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                items(items = filteredUsers) { item ->
                    UserCell(
                        item,
                        {
                            scope.launch {
                                viewModel.createPrivateStream(item)
                            }
                        },
                        viewModel.client.userViewModel.baseUrl.collectAsState().value,
                        viewModel
                    )
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
                    .padding(10.dp)
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