package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendMessageView(
    viewModel: ChatDialogViewModel
) {

    val messageText by viewModel.messageText.collectAsState()
    val scope = rememberCoroutineScope()
    val imageUri by viewModel.imageUri.collectAsState()
    val editingMessageBackupText by viewModel.editingMessageBackupText.collectAsState()
    val quotedMessage by viewModel.quotedMessage.collectAsState()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract =
            ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onImageUriChange(uri)
    }
    Column(
        modifier = Modifier
            .background(LocalWorkspaceColorsPalette.current.surface)
    ) {
        if (imageUri != null) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 0.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription = "Close",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clickable { viewModel.onImageUriChange(null) },
                )
            }
        }
        val message = editingMessageBackupText
        val currentlyQuotedMessage = quotedMessage
        if (message != null) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 0.dp)
            ) {
                Column {
                    Text(
                        "Сообщение",
                        color = LocalWorkspaceColorsPalette.current.primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        message,
                        color = LocalWorkspaceColorsPalette.current.textAdditional50,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription = "Close",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { viewModel.clearEditingMessage() },
                )
            }
        } else if (currentlyQuotedMessage != null){
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 0.dp)
            ) {
                Column {
                    Text(
                        "Цитируемое сообщение",
                        color = LocalWorkspaceColorsPalette.current.primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        currentlyQuotedMessage.payload.content,
                        color = LocalWorkspaceColorsPalette.current.textAdditional50,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription = "Close",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { viewModel.clearEditingMessage() },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .background(LocalWorkspaceColorsPalette.current.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        LocalWorkspaceColorsPalette.current.background,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            launcher.launch("image/*")
                        },
                        modifier = Modifier.size(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = LocalWorkspaceColorsPalette.current.iconBase
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.attach_file),
                            contentDescription = "Attach file"
                        )
                    }
                    BasicTextField(
                        value = messageText,
                        onValueChange = viewModel::onMessageChange,
                        textStyle = TextStyle(
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textHeaders),
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.onSendClicked(context)
                    }
                },
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                )
            ) {
                if (viewModel.editingMessage == null) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = "Send"
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "Send edit"
                    )
                }
            }
        }
    }
}