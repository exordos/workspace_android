package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun SendMessageView(viewModel: ChatDialogViewModel) {
    val messageText by viewModel.messageText.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()
    val editingMessageBackupText by viewModel.editingMessageBackupText.collectAsState()
    val quotedMessage by viewModel.quotedMessage.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = LocalWorkspaceColorsPalette.current
    val canSend = messageText.isNotBlank() || imageUri != null || viewModel.editingMessage != null
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        viewModel.onImageUriChange(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chatHeaderBackground, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .navigationBarsPadding()
            .padding(top = 5.dp),
    ) {
        imageUri?.let { uri ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 5.dp)
                    .size(96.dp)
                    .clip(RoundedCornerShape(9.dp)),
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Выбранное изображение",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription = "Удалить изображение",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .clickable { viewModel.onImageUriChange(null) }
                        .padding(5.dp),
                )
            }
        }
        when {
            editingMessageBackupText != null -> ComposerContext(
                title = "Редактирование",
                text = editingMessageBackupText.orEmpty(),
                onClose = viewModel::clearEditingMessage,
            )

            quotedMessage != null -> ComposerContext(
                title = "Ответ ${quotedMessage?.user?.displayableName().orEmpty()}",
                text = quotedMessage?.payload?.content.orEmpty(),
                onClose = viewModel::clearEditingMessage,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 112.dp)
                    .background(colors.background, RoundedCornerShape(14.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = colors.iconBase,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.attach_file),
                        contentDescription = "Прикрепить изображение",
                    )
                }
                BasicTextField(
                    value = messageText,
                    onValueChange = viewModel::onMessageChange,
                    textStyle = TextStyle(
                        color = colors.textHeaders,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (messageText.isEmpty()) {
                                Text(
                                    text = "Сообщение…",
                                    color = colors.textAdditional30,
                                    fontSize = 14.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Button(
                    onClick = {
                        scope.launch { viewModel.onSendClicked(context) }
                    },
                    enabled = canSend,
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSend) colors.primary else Color.Transparent,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = colors.iconBase,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            if (viewModel.editingMessage == null) R.drawable.send else R.drawable.ic_check,
                        ),
                        contentDescription = if (viewModel.editingMessage == null) {
                            "Отправить"
                        } else {
                            "Сохранить"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerContext(
    title: String,
    text: String,
    onClose: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.primary,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                text = text,
                color = colors.textAdditional50,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_close_small),
            contentDescription = "Закрыть",
            tint = colors.iconBase,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onClose),
        )
    }
}
