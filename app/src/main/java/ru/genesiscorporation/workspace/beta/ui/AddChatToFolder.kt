package ru.genesiscorporation.workspace.beta.ui

import android.R.attr.onClick
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun AddChatToFolder(
    folders: List<FolderResponseData>,
    chat: Stream,
    onAddButtonTap: (FolderResponseData, Stream) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(
                LocalWorkspaceColorsPalette.current.background,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Выберите папку",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 16.sp,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 12.dp)
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(items = folders) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(65.dp)
                        .clickable(
                            onClick = {
                                onAddButtonTap(item, chat)
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}