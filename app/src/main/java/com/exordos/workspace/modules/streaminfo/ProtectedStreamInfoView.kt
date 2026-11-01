package com.exordos.workspace.modules.streaminfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import com.exordos.workspace.data.WorkspaceDateTime
import com.exordos.workspace.ChatFlow
import com.exordos.workspace.LocalProtectedBottomBarStyle
import com.exordos.workspace.R
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.AuthHeader
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.modules.encryptedchat.EncryptedChatAccent
import com.exordos.workspace.modules.encryptedchat.EncryptedChatAvatarBadge
import com.exordos.workspace.modules.encryptedchat.EncryptedChatBackground
import com.exordos.workspace.modules.encryptedchat.EncryptedChatKeysScreen
import com.exordos.workspace.modules.share.shareWorkspaceLink
import com.exordos.workspace.modules.share.workspaceStreamShareLink
import com.exordos.workspace.ui.Avatar
import com.exordos.workspace.ui.theme.InterFontFamily
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL

private enum class ProtectedInfoPage { INFO, EDIT, TYPE, MEMBERS, KEYS, LOCAL_KEYS }
private val ProtectedMuted = Color(0xFF707070)
private val ProtectedField = Color(0xFF262628)
private val ProtectedDivider = Color(0xFF333333)
private val ProtectedDelete = Color(0xFFFF0000)
private val ProtectedMemberStyle = UserRowStyle(
    name = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp),
    subtitle = TextStyle(color = ProtectedMuted, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp),
    dividerColor = ProtectedDivider,
    height = 64.dp,
    contentPadding = PaddingValues(horizontal = 8.dp),
    textSpacing = 2.dp,
    avatarInitialFontSize = 14.sp,
)

/** Dedicated protected-channel pages; ordinary channel information stays unchanged. */
@Composable
fun ProtectedStreamInfoView(viewModel: StreamInfoViewModel, navController: NavHostController) {
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val details by viewModel.protectedStreamDetails.collectAsStateWithLifecycle()
    val update by viewModel.protectedStreamUpdateState.collectAsStateWithLifecycle()
    val deletion by viewModel.protectedStreamDeleteState.collectAsStateWithLifecycle()
    val callState by viewModel.protectedStreamCallState.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val bindingsByStream by viewModel.streamBindings.collectAsStateWithLifecycle()
    val encryption by viewModel.encryptedChatState.collectAsStateWithLifecycle()
    val rawMessages by viewModel.repo.streamTopicMessages.collectAsStateWithLifecycle()
    val pooledMessages by viewModel.repo.messagesPool.collectAsStateWithLifecycle()
    val selectedServer by viewModel.client.requireUserViewModel().selectedServer.collectAsStateWithLifecycle()
    val memberBaseUrl = selectedServer?.takeIf { it.id == viewModel.repo.serverId }?.baseUrl.orEmpty()
    val memberAuthHeaders = viewModel.client.authHeaders(viewModel.repo.serverId)
    val context = LocalContext.current
    val setProtectedBottomBarStyle = LocalProtectedBottomBarStyle.current
    val palette = LocalWorkspaceColorsPalette.current
    val scope = rememberCoroutineScope()
    var page by remember(viewModel.streamUuid) { mutableStateOf(ProtectedInfoPage.INFO) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    var name by remember(viewModel.streamUuid) { mutableStateOf(stream?.name.orEmpty()) }
    var description by remember(viewModel.streamUuid) { mutableStateOf(stream?.description.orEmpty()) }
    var dirty by remember { mutableStateOf(false) }
    var callFailed by remember { mutableStateOf(false) }
    val bound = bindingsByStream[viewModel.streamUuid].orEmpty().distinctBy { it.userUuid }
    val editable = details?.role in setOf("owner", "administrator") &&
        stream?.role in setOf("owner", "administrator") && stream?.isArchived == false
    val busy = update.busy || deletion.busy || callState.busy
    val isKeyPage = page == ProtectedInfoPage.KEYS || page == ProtectedInfoPage.LOCAL_KEYS
    val keyCreatedAt = remember(rawMessages, pooledMessages, encryption.currentKeyUuid, stream?.uuid, selectedServer?.projectUuid) {
        val targetStream = stream?.uuid
        val currentKey = encryption.currentKeyUuid
        if (targetStream == null || currentKey.isNullOrBlank()) null else (pooledMessages.asSequence() + rawMessages.values.asSequence().flatten()).filter {
            it.streamUuid == targetStream && it.payload.kind == "key_announced" && it.payload.keyUuid == currentKey &&
                (it.projectId == null || it.projectId == selectedServer?.projectUuid)
        }.mapNotNull { runCatching { WorkspaceDateTime.parse(it.createdAt).toEpochMilli() }.getOrNull() }.minOrNull()
    }

    DisposableEffect(page, stream?.encryption == true, setProtectedBottomBarStyle) {
        setProtectedBottomBarStyle(if (stream?.encryption == true)
            page != ProtectedInfoPage.INFO && page != ProtectedInfoPage.MEMBERS else null)
        onDispose { setProtectedBottomBarStyle(null) }
    }

    fun leaveEditor() {
        if (page == ProtectedInfoPage.EDIT && dirty) discardConfirmation = true
        else page = ProtectedInfoPage.INFO
    }
    fun save() { if (editable && !busy) viewModel.updateProtectedStream(name, description) }
    LaunchedEffect(viewModel.streamUuid) { viewModel.loadProtectedStreamDetails() }
    LaunchedEffect(details) {
        if (!dirty) {
            name = details?.name ?: stream?.name.orEmpty()
            description = details?.description ?: stream?.description.orEmpty()
        }
    }
    LaunchedEffect(update.saved) {
        if (update.saved) {
            dirty = false
            page = ProtectedInfoPage.INFO
            discardConfirmation = false
            viewModel.clearProtectedStreamUpdateState()
        }
    }
    LaunchedEffect(deletion.deleted) {
        if (deletion.deleted) navController.popBackStack<ChatFlow.ChatList>(inclusive = false)
    }
    LaunchedEffect(stream) {
        if (stream == null || stream?.encryption != true) {
            page = ProtectedInfoPage.INFO
            deleteConfirmation = false
            discardConfirmation = false
        }
    }
    BackHandler(page != ProtectedInfoPage.INFO) {
        when (page) {
            ProtectedInfoPage.TYPE, ProtectedInfoPage.KEYS -> page = ProtectedInfoPage.EDIT
            ProtectedInfoPage.LOCAL_KEYS -> page = ProtectedInfoPage.TYPE
            else -> leaveEditor()
        }
    }
    Scaffold(
        containerColor = EncryptedChatBackground,
        contentWindowInsets = if (isKeyPage) WindowInsets(0, 0, 0, 0)
            else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            if (!isKeyPage) ProtectedInfoHeader(
                title = when (page) {
                    ProtectedInfoPage.INFO -> null
                    ProtectedInfoPage.EDIT -> stringResource(R.string.protected_stream_edit_title)
                    ProtectedInfoPage.TYPE -> stringResource(R.string.protected_stream_type)
                    ProtectedInfoPage.MEMBERS -> stringResource(R.string.protected_stream_members)
                    ProtectedInfoPage.KEYS, ProtectedInfoPage.LOCAL_KEYS -> null
                },
                onBack = {
                    when (page) {
                        ProtectedInfoPage.INFO -> navController.popBackStack()
                        ProtectedInfoPage.TYPE -> page = ProtectedInfoPage.EDIT
                        else -> leaveEditor()
                    }
                },
                onClose = { leaveEditor() },
            )
        },
    ) { padding ->
        val current = stream
        if (current == null || !current.encryption) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ProtectedText(stringResource(R.string.stream_info_unavailable), ProtectedMuted, Modifier.padding(20.dp))
            }
        } else when (page) {
            ProtectedInfoPage.INFO -> Column(Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).testTag("protected-stream-info")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProtectedAvatar(current, 64, selectedServer?.baseUrl.orEmpty(), viewModel)
                    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(current.name, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
                            fontFamily = InterFontFamily, letterSpacing = 0.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ProtectedText(stringResource(R.string.encrypted_chat_title), EncryptedChatAccent)
                        val online = bound.count { binding -> users.any { it.uuid == binding.userUuid && it.status == "active" } }
                        val participants = context.resources.getQuantityString(R.plurals.participants_count, bound.size, bound.size)
                        ProtectedText(if (online > 0) stringResource(R.string.protected_stream_online_count, participants, online) else participants, ProtectedMuted)
                    }
                }
                Spacer(Modifier.height(32.dp))
                val shareLink = workspaceStreamShareLink(selectedServer?.baseUrl.orEmpty(), selectedServer?.projectUuid.orEmpty(), current.uuid)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtectedActionTile(R.drawable.call, R.string.protected_stream_call, !busy && encryption.sendingKeyUuid != null, "protected-stream-call", Modifier.weight(1f)) {
                        callFailed = false
                        viewModel.clearProtectedStreamCallState()
                        scope.launch {
                            try {
                                val room = viewModel.createProtectedCall()
                                if (room != null) {
                                    val options = JitsiMeetConferenceOptions.Builder().setServerURL(URL(viewModel.repo.jitsiServerUrl))
                                        .setRoom(room).build()
                                    JitsiMeetActivity.launch(context, options)
                                } else callFailed = true
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { callFailed = true }
                        }
                    }
                    ProtectedActionTile(R.drawable.ic_settings, R.string.protected_stream_settings, !busy,
                        "protected-stream-settings", Modifier.weight(1f)) {
                        viewModel.clearProtectedStreamUpdateState()
                        viewModel.clearProtectedStreamDeleteState()
                        viewModel.loadProtectedStreamDetails()
                        page = ProtectedInfoPage.EDIT
                    }
                    ProtectedActionTile(R.drawable.ic_share, R.string.protected_stream_share, shareLink != null,
                        "protected-stream-share", Modifier.weight(1f)) {
                        shareLink?.let { shareWorkspaceLink(context, current.displayableName(), it) }
                    }
                }
                if (callFailed) ProtectedText(stringResource(R.string.protected_stream_call_failed), ProtectedDelete, Modifier.padding(20.dp))
                Spacer(Modifier.height(32.dp))
                CompositionLocalProvider(LocalWorkspaceColorsPalette provides palette.copy(cardBackgroundBase = Color(0xFF373737), divider = ProtectedDivider)) {
                    Box(Modifier.padding(horizontal = 12.dp)) { NotificationButtonsRow(viewModel) }
                }
                Spacer(Modifier.height(24.dp))
                ProtectedMembers(navController, users, bound, current, memberBaseUrl, memberAuthHeaders)
            }
            ProtectedInfoPage.EDIT -> Column(Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).testTag("protected-stream-edit")) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    ProtectedAvatar(current, 80, selectedServer?.baseUrl.orEmpty(), viewModel)
                }
                ProtectedEditField(stringResource(R.string.protected_stream_name), name, editable && !busy, "protected-stream-name", { value -> name = value; dirty = true }, ::save)
                Spacer(Modifier.height(12.dp))
                ProtectedEditField(stringResource(R.string.protected_stream_description), description, editable && !busy, "protected-stream-description", { value -> description = value; dirty = true }, ::save)
                if (update.error != null) ProtectedText(stringResource(protectedUpdateError(update.error)), ProtectedDelete,
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("protected-stream-update-error"))
                if (dirty && editable) {
                    TextButton(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("protected-stream-save")) {
                        if (update.busy) CircularProgressIndicator(Modifier.size(16.dp), color = EncryptedChatAccent, strokeWidth = 1.dp)
                        else ProtectedText(stringResource(R.string.protected_stream_save), EncryptedChatAccent)
                    }
                }
                Spacer(Modifier.height(16.dp))
                ProtectedEditRow(R.drawable.ic_settings, R.string.protected_stream_type, stringResource(R.string.protected_stream_type_selected), "protected-stream-type-row", rotateIcon = true) { page = ProtectedInfoPage.TYPE }
                ProtectedEditRow(null, R.string.protected_stream_key, null, "protected-stream-keys-row", keyGlyph = true) { page = ProtectedInfoPage.KEYS }
                ProtectedEditRow(R.drawable.ic_person_add, R.string.protected_stream_members, bound.size.toString(), "protected-stream-members-row") { page = ProtectedInfoPage.MEMBERS }
                if (editable) ProtectedEditRow(R.drawable.ic_item_delete, R.string.protected_stream_delete, null, "protected-stream-delete-row",
                    destructive = true, showChevron = false, enabled = !busy && !deletion.verificationRequired) {
                    viewModel.clearProtectedStreamDeleteState()
                    deleteConfirmation = true
                }
                if (deletion.error != null) ProtectedText(stringResource(if (deletion.verificationRequired)
                    R.string.protected_stream_delete_uncertain else R.string.protected_stream_delete_failed), ProtectedDelete,
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("protected-stream-delete-error"))
                if (deletion.verificationRequired) TextButton(onClick = viewModel::deleteProtectedStream, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("protected-stream-verify-delete")) {
                    ProtectedText(stringResource(R.string.protected_stream_verify_delete), EncryptedChatAccent)
                }
            }
            ProtectedInfoPage.TYPE -> ProtectedTypePage(Modifier.fillMaxSize().padding(padding)) { page = ProtectedInfoPage.LOCAL_KEYS }
            ProtectedInfoPage.MEMBERS -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                ProtectedMembers(navController, users, bound, current, memberBaseUrl, memberAuthHeaders)
            }
            ProtectedInfoPage.KEYS, ProtectedInfoPage.LOCAL_KEYS -> key(page) {
                val localInventory = page == ProtectedInfoPage.LOCAL_KEYS
                EncryptedChatKeysScreen(encryption, users, { page = if (localInventory) ProtectedInfoPage.TYPE else ProtectedInfoPage.EDIT },
                    viewModel::requestEncryptionKey, viewModel::grantEncryptionRequest, viewModel::rejectEncryptionRequest,
                    viewModel::grantAllEncryptionRequests, viewModel::rotateEncryptionKey, viewModel::deleteEncryptionKeys,
                    currentKeyCreatedAt = keyCreatedAt, avatarBaseUrl = selectedServer?.baseUrl.orEmpty(),
                    avatarAuthHeaders = viewModel.client.authHeaders(), rolesByUser = bound.associate { it.userUuid to it.role },
                    initialLocalKeysVisible = localInventory)
            }
        }
    }
    val current = stream
    if (deleteConfirmation && current?.encryption == true) {
        AlertDialog(onDismissRequest = { if (!deletion.busy) deleteConfirmation = false }, containerColor = EncryptedChatBackground,
            title = { ProtectedText(stringResource(R.string.protected_stream_delete_title)) },
            text = { ProtectedText(stringResource(R.string.protected_stream_delete_body), ProtectedMuted) },
            confirmButton = { TextButton(onClick = { deleteConfirmation = false; viewModel.deleteProtectedStream() }, enabled = editable && !busy,
                modifier = Modifier.testTag("protected-stream-confirm-delete")) { ProtectedText(stringResource(R.string.protected_stream_delete), ProtectedDelete) } },
            dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { ProtectedText(stringResource(R.string.protected_stream_cancel), ProtectedMuted) } })
    }
    if (discardConfirmation) {
        AlertDialog(onDismissRequest = { discardConfirmation = false }, containerColor = EncryptedChatBackground,
            title = { ProtectedText(stringResource(R.string.protected_stream_discard_title)) },
            confirmButton = { TextButton(onClick = ::save, enabled = editable && !busy) { ProtectedText(stringResource(R.string.protected_stream_save), EncryptedChatAccent) } },
            dismissButton = { TextButton(onClick = {
                dirty = false; name = details?.name ?: current?.name.orEmpty(); description = details?.description ?: current?.description.orEmpty()
                discardConfirmation = false; page = ProtectedInfoPage.INFO
            }) { ProtectedText(stringResource(R.string.protected_stream_discard), ProtectedMuted) } })
    }
}

@Composable
private fun ProtectedInfoHeader(title: String?, onBack: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(EncryptedChatBackground)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(56.dp), contentAlignment = Alignment.Center) {
        if (title != null) Text(title, color = Color.White, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
            fontFamily = InterFontFamily, letterSpacing = 0.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 48.dp))
        if (title == null) {
            Row(Modifier.align(Alignment.CenterStart).clickable(role = Role.Button, onClick = onBack).height(48.dp).padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.protected_stream_info_back), Modifier.width(10.dp).height(20.dp), tint = ProtectedMuted)
                ProtectedText(stringResource(R.string.protected_stream_info_back))
            }
        } else {
            Box(Modifier.align(Alignment.CenterStart).size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.protected_stream_info_back), Modifier.width(10.dp).height(20.dp), tint = ProtectedMuted)
            }
        }
        if (title != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(48.dp).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_close_small), stringResource(R.string.encrypted_chat_close), Modifier.size(32.dp), tint = ProtectedMuted)
        }
    }
}

@Composable
private fun ProtectedAvatar(stream: Stream, size: Int, baseUrl: String, viewModel: StreamInfoViewModel) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Avatar(null, baseUrl, viewModel.client.authHeaders(), stream.color, stream.displayableName(), Modifier.fillMaxSize())
        EncryptedChatAvatarBadge(Modifier.size(if (size >= 80) 28.dp else 24.dp))
    }
}

@Composable
private fun ProtectedActionTile(icon: Int, label: Int, enabled: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(64.dp).background(ProtectedDivider, RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = ProtectedMuted)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(label), color = ProtectedMuted, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp)
    }
}

@Composable
private fun ProtectedMembers(navController: NavHostController, users: List<UserResponseData>,
    bindings: List<StreamBindingResponseData>, stream: Stream, baseUrl: String, authHeaders: List<AuthHeader>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProtectedText(stringResource(R.string.protected_stream_members), modifier = Modifier.weight(1f))
            if (stream.role in setOf("owner", "administrator")) IconButton(onClick = { navController.navigate(ChatFlow.AddUsersToStream(stream.uuid)) },
                modifier = Modifier.size(40.dp).testTag("protected-stream-add-members")) {
                Icon(painterResource(R.drawable.ic_person_add), stringResource(R.string.protected_stream_add_members), Modifier.size(24.dp), tint = ProtectedMuted)
            }
        }
        bindings.sortedBy { if (it.role == "owner") 0 else 1 }.forEach { binding ->
            val user = users.firstOrNull { it.uuid == binding.userUuid } ?: return@forEach
            UserRow(user, baseUrl, authHeaders, navController, style = ProtectedMemberStyle,
                nameLeading = { Icon(painterResource(R.drawable.ic_business), null, Modifier.size(16.dp), tint = ProtectedMuted) },
                trailing = {
                    val roleLabel = when (binding.role) { "owner" -> R.string.protected_stream_owner; "administrator" -> R.string.protected_stream_admin; else -> null }
                    if (roleLabel != null) Text(stringResource(roleLabel), color = ProtectedMuted, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp)
                })
        }
    }
}

@Composable
private fun ProtectedEditField(label: String, value: String, enabled: Boolean, tag: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        ProtectedText(label, ProtectedMuted, Modifier.padding(start = 8.dp, bottom = 4.dp))
        BasicTextField(value, onChange, enabled = enabled, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontFamily = InterFontFamily, letterSpacing = 0.sp, fontSize = 14.sp, lineHeight = 18.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().height(32.dp).background(ProtectedField, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp).testTag(tag))
    }
}

@Composable
private fun ProtectedEditRow(icon: Int?, label: Int, value: String?, tag: String, destructive: Boolean = false,
    showChevron: Boolean = true, enabled: Boolean = true, keyGlyph: Boolean = false, rotateIcon: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 20.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        if (keyGlyph) ProtectedKeyGlyph() else if (icon != null) Icon(painterResource(icon), null,
            Modifier.size(24.dp).then(if (rotateIcon) Modifier.rotate(90f) else Modifier), tint = if (destructive) ProtectedDelete else ProtectedMuted)
        Spacer(Modifier.width(16.dp))
        ProtectedText(stringResource(label), if (destructive) ProtectedDelete else Color.White, Modifier.weight(1f))
        if (value != null) ProtectedText(value, ProtectedMuted, Modifier.padding(end = 8.dp))
        if (showChevron) ProtectedChevron(false, sideways = true)
    }
}

@Composable
private fun ProtectedTypePage(modifier: Modifier, onLocalKeys: () -> Unit) {
    var advanced by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).testTag("protected-stream-type")) {
        Column(Modifier.fillMaxWidth().border(1.dp, ProtectedDivider, RoundedCornerShape(8.dp)).padding(16.dp)) {
            listOf(
                R.string.protected_stream_type_public to R.string.protected_stream_type_public_description,
                R.string.protected_stream_type_closed_open to R.string.protected_stream_type_closed_open_description,
                R.string.protected_stream_type_closed_protected to R.string.protected_stream_type_closed_protected_description,
                R.string.protected_stream_type_e2ee to R.string.protected_stream_type_e2ee_description,
            ).forEachIndexed { index, (label, description) ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().semantics { role = Role.RadioButton; selected = index == 3; disabled() },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Canvas(Modifier.size(16.dp)) {
                        if (index == 3) {
                            drawCircle(EncryptedChatAccent)
                            drawLine(Color.White, Offset(size.width * .23f, size.height * .52f), Offset(size.width * .43f, size.height * .71f), 1.4.dp.toPx(), StrokeCap.Round)
                            drawLine(Color.White, Offset(size.width * .43f, size.height * .71f), Offset(size.width * .79f, size.height * .3f), 1.4.dp.toPx(), StrokeCap.Round)
                        } else drawCircle(EncryptedChatAccent, radius = size.minDimension / 2 - .5.dp.toPx(), style = Stroke(1.dp.toPx()))
                    }
                    ProtectedText(stringResource(label))
                }
                Spacer(Modifier.height(8.dp))
                ProtectedText(stringResource(description), ProtectedMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = ProtectedDivider, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(48.dp).clickable { advanced = !advanced }.padding(horizontal = 8.dp)
            .testTag("protected-stream-advanced"), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.protected_stream_advanced), color = Color.White, fontSize = 16.sp, lineHeight = 20.sp,
                fontFamily = InterFontFamily, letterSpacing = 0.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            ProtectedChevron(advanced)
        }
        if (advanced) {
            ProtectedText(stringResource(R.string.protected_stream_type_immutable), ProtectedMuted, Modifier.padding(8.dp))
            TextButton(onClick = onLocalKeys, modifier = Modifier.fillMaxWidth().testTag("protected-stream-local-keys")) {
                ProtectedText(stringResource(R.string.encrypted_chat_more_key_actions), EncryptedChatAccent)
            }
        }
    }
}

@Composable
private fun ProtectedText(text: String, color: Color = Color.White, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, fontFamily = InterFontFamily, letterSpacing = 0.sp, fontSize = 14.sp, lineHeight = 18.sp)
}

@Composable
private fun ProtectedChevron(expanded: Boolean, sideways: Boolean = false) {
    Canvas(Modifier.size(16.dp)) {
        val points = if (sideways) listOf(Offset(.35f, .15f), Offset(.65f, .5f), Offset(.35f, .85f))
            else if (expanded) listOf(Offset(.15f, .65f), Offset(.5f, .35f), Offset(.85f, .65f))
            else listOf(Offset(.15f, .35f), Offset(.5f, .65f), Offset(.85f, .35f))
        drawLine(ProtectedMuted, Offset(points[0].x * size.width, points[0].y * size.height), Offset(points[1].x * size.width, points[1].y * size.height), 1.dp.toPx(), StrokeCap.Round)
        drawLine(ProtectedMuted, Offset(points[1].x * size.width, points[1].y * size.height), Offset(points[2].x * size.width, points[2].y * size.height), 1.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ProtectedKeyGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.dp.toPx()
        drawCircle(ProtectedMuted, size.width * .19f, Offset(size.width * .24f, size.height * .5f), style = Stroke(stroke))
        drawLine(ProtectedMuted, Offset(size.width * .43f, size.height * .5f), Offset(size.width * .95f, size.height * .5f), stroke)
        drawLine(ProtectedMuted, Offset(size.width * .7f, size.height * .5f), Offset(size.width * .7f, size.height * .65f), stroke)
        drawLine(ProtectedMuted, Offset(size.width * .84f, size.height * .5f), Offset(size.width * .84f, size.height * .65f), stroke)
    }
}

private fun protectedUpdateError(error: ProtectedStreamUpdateFailure?): Int = when (error) {
    ProtectedStreamUpdateFailure.INVALID_NAME -> R.string.protected_stream_name_required
    ProtectedStreamUpdateFailure.FORBIDDEN -> R.string.protected_stream_forbidden
    ProtectedStreamUpdateFailure.STALE -> R.string.protected_stream_stale
    else -> R.string.protected_stream_save_failed
}
