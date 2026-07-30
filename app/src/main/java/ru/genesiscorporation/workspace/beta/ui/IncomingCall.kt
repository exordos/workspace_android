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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.WorkspaceViewModel
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL

@Composable
fun IncomingCall(
    callMessage: MessageResponse,
    viewModel: WorkspaceViewModel,
    context: Context
) {
    val itemUrl = runCatching { URL(callMessage.payload.content) }.getOrNull()
    val serverUrl = viewModel.repo.jitsiServerUrl
        .takeIf(String::isNotBlank)
        ?.let { runCatching { URL(it) }.getOrNull() }
    val roomName = itemUrl?.path?.trim('/')
    val isTrustedCall = itemUrl != null &&
        serverUrl != null &&
        itemUrl.protocol == "https" &&
        itemUrl.host == serverUrl.host &&
        !roomName.isNullOrBlank()
    if (!isTrustedCall) {
        LaunchedEffect(callMessage.uuid) {
            viewModel.setCurrentCallMessage(null)
        }
        return
    }
    val trustedServerUrl = requireNotNull(serverUrl)
    val trustedRoomName = requireNotNull(roomName)
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
            Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Workspace",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 12.dp)
            )
        }
        Row {
            Column {
                Text(
                    trustedRoomName,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Входящий звонок",
                    color = LocalWorkspaceColorsPalette.current.textAdditional50,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(id = R.drawable.ic_active_call),
                contentDescription = null
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    viewModel.setCurrentCallMessage(null)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.indicatorRed
                ),
                modifier = Modifier
                    .padding(6.dp)
            ) {
                Text("Отклонить")
            }
            Button(
                onClick = {
                    val options = JitsiMeetConferenceOptions.Builder()
                        .setServerURL(trustedServerUrl)
                        .setRoom(trustedRoomName)
                        .build()
                    viewModel.setCurrentCallMessage(null)
                    JitsiMeetActivity.launch(context, options)

                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.indicatorGreen
                ),
                modifier = Modifier
                    .padding(6.dp)
            ) {
                Text("Принять")
            }
        }
    }
}
