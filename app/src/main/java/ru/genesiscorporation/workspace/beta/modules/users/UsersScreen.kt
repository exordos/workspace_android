package ru.genesiscorporation.workspace.beta.modules.users

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.Avatar

@Composable
fun UsersScreen(
    viewModel: UsersViewModel,
    onUserSelected: (UserResponseData) -> Unit,
    onDismiss: () -> Unit
) {
    val users by viewModel.users.collectAsState()

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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LocalWorkspaceColorsPalette.current.surface
        ) {
            Box(Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onDismiss,
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
                    if (users.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading"
                            )
                        }
                    } else {
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
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textAdditional30),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
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
                                UserCell(item, onUserSelected, viewModel)
                            }
                        }
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
    viewModel: UsersViewModel
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
                viewModel.client.userViewModel.baseUrl.value ?: "",
                null,
                item.displayableName(),
                40,
                false
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