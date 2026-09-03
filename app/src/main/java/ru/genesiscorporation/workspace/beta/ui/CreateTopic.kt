package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun CreateTopic(
    onCreateButtonTap: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var topicName by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .background(
                LocalWorkspaceColorsPalette.current.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Название темы",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 12.sp,
                fontFamily = InterFontFamily,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 12.dp)
            )
        }
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
        )  {
            BasicTextField(
                value = topicName,
                onValueChange =  { topicName = it },
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Button(
                onClick = {
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.cardBackgroundActive,
                    contentColor = LocalWorkspaceColorsPalette.current.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Отменить")
            }
            Button(
                onClick = {
                    if (!topicName.isEmpty()) {
                        onCreateButtonTap(topicName)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Создать")
            }
        }
    }
}