package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun FullScreenError(
    onRestart: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_error),
                contentDescription = null
            )
            Text(
                "Что-то пошло не так",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 16.sp,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(vertical = 20.dp)
            )
            Text(
                "Не удалось загрузить данные. Проверьте подключение к интернету и попробуйте ещё раз",
                color = LocalWorkspaceColorsPalette.current.textAdditional50,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    onRestart()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 40.dp)
            ) {
                Row (
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painterResource(R.drawable.ic_refresh),
                        null
                    )
                    Text(
                        "Повторить запрос",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}