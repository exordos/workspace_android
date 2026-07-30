package ru.genesiscorporation.workspace.beta.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL

@Composable
fun CreateFolder(
    onCreateButtonTap: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    title: String = "Создать папку",
    submitLabel: String = "Создать",
) {
    var folderName by rememberSaveable { mutableStateOf(initialName) }
    Column(
        modifier = Modifier
            .padding(16.dp)
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
                title,
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 12.dp)
            )
        }
        Row {
            BasicTextField(
                value = folderName,
                onValueChange =  { folderName = it },
                textStyle = TextStyle(
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textHeaders),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.indicatorRed
                ),
                modifier = Modifier
                    .padding(6.dp)
            ) {
                Text("Отменить")
            }
            Button(
                onClick = {
                    onCreateButtonTap(folderName.trim())
                },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.indicatorGreen
                ),
                modifier = Modifier
                    .padding(6.dp)
            ) {
                Text(submitLabel)
            }
        }
    }
}
