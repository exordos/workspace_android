package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

/** Native Sharesheet first, then the Workspace picker only when its target is chosen. */
@Composable
internal fun ForwardMessagesAction(
    sources: List<MessageResponse>,
    client: WorkspaceAPIClient,
    repo: EventsRepository,
    userViewModel: UserViewModel,
    sessionStore: ForwardSessionStore,
    onDismiss: () -> Unit,
    onForwarded: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalWorkspaceColorsPalette.current
    val chooserTitle = stringResource(R.string.messages_forward)
    val scope = rememberCoroutineScope()
    val baseUrl by userViewModel.baseUrl.collectAsStateWithLifecycle()
    val currentUser by repo.currentUser.collectAsStateWithLifecycle()
    val owner = "$baseUrl|${currentUser?.uuid ?: userViewModel.userData?.uuid.orEmpty()}"
    val resumed = remember(owner, sessionStore) { sessionStore.unresolved(owner) != null }
    val session = remember { ForwardShareSessions.create().also { if (resumed) it.second.value = true } }
    val internalRequested by session.second.collectAsState()
    var preparing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Int?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The explicit target marks the session before finishing its Activity.
        if (!session.second.value) onDismiss()
    }
    DisposableEffect(Unit) { onDispose { ForwardShareSessions.remove(session.first) } }
    suspend fun share() {
        preparing = true
        error = null
        try {
            val external = prepareExternalShareIntent(context, sources, client)
            val internal = Intent(context, ForwardToWorkspaceActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = external.type
                putExtra(ForwardShareSessions.EXTRA_SESSION, session.first)
            }
            val chooser = Intent.createChooser(external, chooserTitle).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(internal))
            }
            preparing = false
            launcher.launch(chooser)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            preparing = false
            error = R.string.forward_external_preparation_failed
        }
    }
    LaunchedEffect(Unit) { if (!resumed) share() }
    if (internalRequested) {
        ForwardMessagesDialog(sources, client, repo, userViewModel, sessionStore, onDismiss, onForwarded)
    } else if (preparing || error != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.messageBackground,
            title = { Text(stringResource(R.string.messages_forward), color = colors.textHeaders) },
            text = {
                if (preparing) CircularProgressIndicator()
                else Text(stringResource(requireNotNull(error)), color = colors.textHeaders)
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.message_delete_cancel), color = colors.textHeaders) } },
            confirmButton = {
                if (error != null) {
                    TextButton(onClick = { ForwardShareSessions.chooseInternal(session.first) }) { Text(stringResource(R.string.forward_in_workspace), color = colors.textHeaders) }
                    TextButton(onClick = { scope.launch { share() } }) { Text(stringResource(R.string.forward_retry), color = colors.textHeaders) }
                }
            },
        )
    }
}
