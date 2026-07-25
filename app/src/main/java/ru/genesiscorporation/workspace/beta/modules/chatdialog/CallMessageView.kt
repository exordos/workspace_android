package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL

@Composable
fun CallMessageView(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val context = LocalContext.current
    val itemUrl = runCatching { URL(item.payload.content) }.getOrNull()

    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .background(
                    colors.messageActiveCallBackground,
                    messageBubbleShape(item.isOwn),
                )
                .clickable(enabled = itemUrl != null) {
                    val serverUrl = viewModel.repo.jitsiServerUrl
                    if (serverUrl.isNotBlank() && itemUrl != null) {
                        runCatching {
                            val options = JitsiMeetConferenceOptions.Builder()
                                .setServerURL(URL(serverUrl))
                                .setRoom(itemUrl.path.drop(1))
                                .build()
                            JitsiMeetActivity.launch(context, options)
                        }
                    }
                }
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Звонок",
                    color = colors.indicatorGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = itemUrl?.path?.drop(1).orEmpty().ifBlank { "Workspace" },
                    color = colors.textHeaders,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.call),
                    contentDescription = "Присоединиться к звонку",
                    tint = colors.indicatorGreen,
                )
            }
            Spacer(Modifier.padding(top = 2.dp))
            MessageHeader(item, viewModel)
            MessageFooter(item)
        }
    }
}
