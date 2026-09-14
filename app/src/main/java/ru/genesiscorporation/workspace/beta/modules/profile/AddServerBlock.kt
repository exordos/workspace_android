package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun AddServer(
    viewModel: ProfileViewModel,
    onAddButtonTap: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val serverText by viewModel.serverText.collectAsState()
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(
                LocalWorkspaceColorsPalette.current.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "Добавить организацию",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Укажите ссылку на организацию, \nчтобы добавить её в список",
                color = LocalWorkspaceColorsPalette.current.textAdditional50,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 12.dp )
            )
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "Адрес организации",
                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
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
                        value = serverText,
                        onValueChange = viewModel::onServerChange,
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
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.getServerSettings()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text("Добавить")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider,
                )
                Text(
                    text = "или",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider,
                )
            }
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "Вы можете подключиться к нашему публичному серверу:",
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = {
                        viewModel.onServerChange("https://workspace.exordos.com")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalWorkspaceColorsPalette.current.searchBackground,
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
                            painter = painterResource(id = R.drawable.icon),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                                .padding(end = 8.dp)
                        )
                        Text("Exordos public")
                    }
                }
            }
        }
    }
}