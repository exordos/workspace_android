package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.BuildConfig

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp).weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            val userData = viewModel.userViewModel.userData
            if (userData != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                ) {
//                    val imageRequest = ImageRequest.Builder(LocalContext.current)
//                        .data(userData.avatar_url)
//                        .build()
//                    AsyncImage(
//                        model = imageRequest,
//                        contentDescription = null,
//                        contentScale = ContentScale.Crop,
//                        modifier = Modifier
//                            .padding(end = 16.dp)
//                            .size(64.dp)
//                            .clip(CircleShape),
//                    )
                    Text(
                        text = "${userData.firstName} ${userData.lastName}" ,
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mail),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                    )
                    val email = userData.email
                    if (email != null) {
                        Column(
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Email",
                                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                fontSize = 12.sp
                            )
                            Text(
                                text = email,
                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_userid),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Имя пользователя",
                            color = LocalWorkspaceColorsPalette.current.textAdditional30,
                            fontSize = 12.sp
                        )
                        Text(
                            text = userData.username,
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp
                        )
                    }
                }
            }
//            Button(
//                onClick = {
//
//                }
//            ) {
//                Row {
//                    Image(
//                        painter = painterResource(id = R.drawable.ic_add),
//                        contentDescription = null
//                    )
//                    Text(
//                        text = "Добавить организацию"
//                    )
//                }
//            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logout),
                    contentDescription = null
                )
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = LocalWorkspaceColorsPalette.current.indicatorRed
                    )
                ) {
                    Text(
                        text = "Выйти"
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(BuildConfig.VERSION_NAME,
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 10.sp)
        }
    }
}