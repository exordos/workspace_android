package ru.genesiscorporation.workspace.beta.modules.creationbase

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.StreamCreationFlow
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationBaseView(
    viewModel: CreationBaseViewModel,
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Назад",
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Button(
                onClick = {
                    navController.navigate(ChatFlow.CreateDirectStream)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.textHeaders
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_person_add),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                            .padding(end = 8.dp)
                    )
                    Text("Начать чат")
                }
            }
            Button(
                onClick = {
                    navController.navigate(ChatFlow.CreateStream)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.textHeaders
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_group),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                            .padding(end = 8.dp)
                    )
                    Text("Создать стрим")
                }
            }
        }
    }
}