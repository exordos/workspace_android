package com.exordos.workspace.modules.adduserstostream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.exordos.workspace.ChatFlow
import com.exordos.workspace.R
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.modules.users.matchesUserSearch
import com.exordos.workspace.modules.users.UserSearchField
import com.exordos.workspace.modules.createstream.CreateStreamViewModel
import com.exordos.workspace.modules.createstream.UserWithCheckboxCell
import com.exordos.workspace.ui.theme.InterFontFamily
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUsersToStreamView(
    viewModel: AddUsersToStreamViewModel,
    navController: NavHostController,
) {
    val users by viewModel.users.collectAsState()
    val selectedUserUuids by viewModel.selectedUserUuids.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val state by viewModel.createQueryState.collectAsStateWithLifecycle()

    val filteredUsers = remember(searchQuery, users) {
        users.filter { it.matchesUserSearch(searchQuery) }
    }

    LaunchedEffect(state) {
        if (state is QueryState.Success) {
            navController.popBackStack()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Добавить пользователей",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                UserSearchField(searchQuery, { searchQuery = it })
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .weight(1f)
                ) {
                    items(items = filteredUsers) { item ->
                        UserWithCheckboxCell(
                            item,
                            { viewModel.didTapOnUser(item) },
                            selectedUserUuids.contains(item.uuid),
                            viewModel.client.userViewModel?.baseUrl?.collectAsState()?.value,
                            viewModel.client.authHeaders(),
                            enabled = state !is QueryState.Loading && state !is QueryState.Success
                        )
                    }
                }
                if (state is QueryState.Error) {
                    Text(
                        text = (state as QueryState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                Button(
                    onClick = {
                        viewModel.submitInvitation()
                    },
                    enabled = selectedUserUuids.isNotEmpty() && state !is QueryState.Loading && state !is QueryState.Success,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalWorkspaceColorsPalette.current.primary,
                        contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(20.dp, 6.dp, 20.dp, 6.dp)
                ) {
                    Text(
                        "Добавить",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
