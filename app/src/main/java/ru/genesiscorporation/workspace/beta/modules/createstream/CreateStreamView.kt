package ru.genesiscorporation.workspace.beta.modules.createstream

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.createdirectstream.CreateDirectStreamViewModel
import ru.genesiscorporation.workspace.beta.modules.createdirectstream.UserCell
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStreamView(
    viewModel: CreateStreamViewModel,
    navController: NavHostController,
) {
    val streamName by viewModel.streamName.collectAsState()
    val users by viewModel.users.collectAsState()
    val selectedUserUuids by viewModel.selectedUserUuids.collectAsState()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val state by viewModel.createQueryState.collectAsStateWithLifecycle()

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
                        false,
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
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Создать стрим",
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
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Text(
                "Название стрима",
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp)
                    .background(
                        LocalWorkspaceColorsPalette.current.searchBackground,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                BasicTextField(
                    value = streamName,
                    onValueChange = viewModel::onStreamNameChange,
                    textStyle = TextStyle(
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                    ),
                    cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textHeaders),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }
            Text(
                "Добавить участников",
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
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
                        UserWithCheckboxCell(
                            item,
                            { viewModel.didTapOnUser(item) },
                            selectedUserUuids.contains(item.uuid),
                            viewModel.client.userViewModel.baseUrl.collectAsState().value,
                            viewModel
                        )
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.createStream()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
                    .padding(20.dp, 6.dp, 20.dp, 6.dp)
            ) {
                Text(
                    "Войти",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun UserWithCheckboxCell(
    item: UserResponseData,
    onUserSelected: (UserResponseData) -> Unit,
    isSelected: Boolean,
    baseUrl: String?,
    viewModel: CreateStreamViewModel
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
                IconButton(onClick = {
                    onUserSelected(item)
                }) {
                    if (isSelected) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_checkbox_filled),
                                contentDescription = null,
                                tint = LocalWorkspaceColorsPalette.current.primary
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_checkbox_tick),
                                contentDescription = null
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_checkbox_empty),
                            contentDescription = null
                        )
                    }
                }
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