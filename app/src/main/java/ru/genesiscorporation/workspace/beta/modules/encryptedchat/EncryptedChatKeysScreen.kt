package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.AuthHeader
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val KeyScreenBackground = Color(0xFF1B1B1D)
private val KeyFieldBackground = Color(0xFF262628)
private val KeySecondary = Color(0xFF707070)
private val KeyDivider = Color(0xFF333333)

/** This view can copy public generation identifiers, and never receives key bytes. */
@Composable
fun EncryptedChatKeysScreen(
    state: EncryptedChatSessionState,
    users: List<UserResponseData>,
    onBack: () -> Unit,
    onRequestKey: (String) -> Unit,
    onGrantRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onGrantAll: () -> Unit,
    onRotateKey: () -> Unit,
    onDeleteKeys: (Set<String>) -> Unit,
    currentKeyCreatedAt: Long? = null,
    avatarBaseUrl: String = "",
    avatarAuthHeaders: List<AuthHeader> = emptyList(),
    rolesByUser: Map<String, String> = emptyMap(),
    showLocalActionsEntry: Boolean = false,
    initialLocalKeysVisible: Boolean = false,
) {
    var rotateConfirmation by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf<Set<String>?>(null) }
    var localKeysVisible by remember(initialLocalKeysVisible) { mutableStateOf(initialLocalKeysVisible) }
    var selecting by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var expandedSections by remember { mutableStateOf(emptySet<EncryptedChatRequestOutcome>()) }
    val availableKeys = state.localKeys.filter { it.available }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.encrypted_chat_copy_identifier)
    LaunchedEffect(availableKeys) {
        selectedKeys = selectedKeys.intersect(availableKeys.map { it.keyUuid }.toSet())
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("encrypted-chat-keys-screen"),
        containerColor = KeyScreenBackground,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            KeyScreenHeader(
                title = if (localKeysVisible) stringResource(R.string.encrypted_chat_local_keys, availableKeys.size)
                    else stringResource(R.string.encrypted_chat_key_screen_title),
                onBack = { if (localKeysVisible && !initialLocalKeysVisible) localKeysVisible = false else onBack() },
                onClose = onBack,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (localKeysVisible) {
                item {
                    Text(stringResource(R.string.encrypted_chat_generation_hint), color = KeySecondary,
                        fontSize = 14.sp, lineHeight = 18.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selecting = !selecting; selectedKeys = emptySet() }, enabled = !state.pending,
                            modifier = Modifier.testTag("encrypted-select-keys")) {
                            KeyText(stringResource(if (selecting) R.string.encrypted_chat_cancel else R.string.encrypted_chat_select_keys), EncryptedChatAccent)
                        }
                        if (selecting) TextButton(onClick = { selectedKeys = availableKeys.map { it.keyUuid }.toSet() }, enabled = !state.pending,
                            modifier = Modifier.testTag("encrypted-select-all-keys")) {
                            KeyText(stringResource(R.string.encrypted_chat_select_all), EncryptedChatAccent)
                        }
                    }
                }
                if (availableKeys.isEmpty()) {
                    item { KeyText(stringResource(R.string.encrypted_chat_no_local_keys), KeySecondary, Modifier.padding(20.dp)) }
                }
                items(availableKeys, key = { "local:${it.keyUuid}" }) { key ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(KeyFieldBackground, RoundedCornerShape(8.dp))
                        .then(if (selecting) Modifier.clickable(enabled = !state.pending) {
                            selectedKeys = if (key.keyUuid in selectedKeys) selectedKeys - key.keyUuid else selectedKeys + key.keyUuid
                        } else Modifier).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Checkbox(checked = key.keyUuid in selectedKeys, enabled = !state.pending,
                            onCheckedChange = { checked -> selectedKeys = if (checked) selectedKeys + key.keyUuid else selectedKeys - key.keyUuid },
                            modifier = Modifier.testTag("encrypted-select-key-${key.keyUuid}"))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            KeyText(key.keyUuid)
                            KeyText(stringResource(if (key.keyUuid == state.currentKeyUuid) R.string.encrypted_chat_current else R.string.encrypted_chat_historical),
                                if (key.keyUuid == state.currentKeyUuid) EncryptedChatAccent else KeySecondary)
                            if (key.createdAt > 0) KeyText(stringResource(R.string.encrypted_chat_saved, formatKeyReceipt(key.createdAt)), KeySecondary)
                        }
                    }
                }
                item {
                    TextButton(onClick = { deleteConfirmation = if (selecting) selectedKeys else availableKeys.map { it.keyUuid }.toSet() },
                        enabled = !state.pending && availableKeys.isNotEmpty() && (!selecting || selectedKeys.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth().testTag("encrypted-delete-keys")) {
                        KeyText(if (selecting) stringResource(R.string.encrypted_chat_delete_selected, selectedKeys.size)
                            else stringResource(R.string.encrypted_chat_delete_all), Color(0xFFFF4545))
                    }
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().height(32.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        KeyText(stringResource(R.string.encrypted_chat_current_key), KeySecondary, Modifier.weight(1f))
                        KeyIconAction(copyLabel, !state.currentKeyUuid.isNullOrBlank(), "encrypted-copy-key-identifier", Modifier.size(32.dp), onClick = {
                            val identifier = state.currentKeyUuid ?: return@KeyIconAction
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, identifier))) }
                        }) { CopyIdentifierGlyph() }
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(32.dp)
                        .background(KeyFieldBackground, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                        Text(state.currentKeyUuid?.takeIf { it.isNotBlank() } ?: stringResource(R.string.encrypted_chat_key_unavailable),
                            modifier = Modifier.testTag("encrypted-current-key-identifier"), color = Color.White,
                            fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = InterFontFamily, letterSpacing = 0.sp)
                    }
                    val createdDate = currentKeyCreatedAt?.takeIf { it > 0 }?.let(::formatGenerationDate)
                        ?: stringResource(R.string.encrypted_chat_date_unknown)
                    KeyText(stringResource(R.string.encrypted_chat_created, createdDate), KeySecondary,
                        Modifier.padding(start = 20.dp, top = 4.dp).height(20.dp))
                    Spacer(Modifier.height(12.dp))
                    if (state.canRotate) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(36.dp)
                            .background(if (state.pending) EncryptedChatAccent.copy(alpha = .45f) else EncryptedChatAccent, RoundedCornerShape(8.dp))
                            .clickable(enabled = !state.pending, role = Role.Button) { rotateConfirmation = true }
                            .testTag("encrypted-rotate-key"), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (state.pending) CircularProgressIndicator(Modifier.size(16.dp), color = KeyScreenBackground, strokeWidth = 1.dp)
                                else Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(16.dp), tint = KeyScreenBackground)
                                KeyText(stringResource(R.string.encrypted_chat_rotate), KeyScreenBackground)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                EncryptedChatRequestOutcome.entries.forEach { outcome ->
                    val requests = state.requests.filter { it.outcome == outcome }
                    item(key = "section:$outcome") {
                        val label = when (outcome) {
                            EncryptedChatRequestOutcome.PENDING -> R.string.encrypted_chat_incoming
                            EncryptedChatRequestOutcome.GRANTED -> R.string.encrypted_chat_granted
                            EncryptedChatRequestOutcome.REJECTED -> R.string.encrypted_chat_rejected_requests
                        }
                        Row(Modifier.fillMaxWidth().height(40.dp)
                            .clickable { expandedSections = if (outcome in expandedSections) expandedSections - outcome else expandedSections + outcome }
                            .padding(horizontal = 20.dp).testTag("encrypted-requests-$outcome"), verticalAlignment = Alignment.CenterVertically) {
                            KeyText(stringResource(label, requests.size), modifier = Modifier.weight(1f))
                            KeyChevron(outcome in expandedSections)
                        }
                    }
                    if (outcome in expandedSections) {
                        items(requests, key = { "request:${it.message.uuid}" }) { request ->
                            EncryptedChatRequestRow(request, users.firstOrNull { it.uuid == request.message.authorId },
                                rolesByUser[request.message.authorId], avatarBaseUrl, avatarAuthHeaders,
                                state.canManageKeys, availableKeys.any { it.keyUuid == request.message.payload.keyUuid },
                                state.pending, onGrantRequest, onRejectRequest)
                        }
                        if (outcome == EncryptedChatRequestOutcome.PENDING && requests.isNotEmpty() && state.canManageKeys) {
                            item {
                                TextButton(onClick = onGrantAll, enabled = !state.pending && requests.any { request -> availableKeys.any { it.keyUuid == request.message.payload.keyUuid } },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("encrypted-grant-all")) {
                                    KeyText(stringResource(R.string.encrypted_chat_grant_all), EncryptedChatAccent)
                                }
                            }
                        }
                    }
                }
                item {
                    if (showLocalActionsEntry) {
                        TextButton(onClick = { localKeysVisible = true }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("encrypted-local-keys")) {
                            KeyText(stringResource(R.string.encrypted_chat_more_key_actions), KeySecondary)
                        }
                    }
                }
            }
            item {
                if (!state.hasCurrentKey && state.currentKeyUuid != null) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        EncryptedChatRequestAccess(state.currentKeyUuid,
                            state.requestStatus[state.currentKeyUuid] ?: EncryptedChatRequestStatus.IDLE, state.pending, onRequestKey)
                    }
                }
                if (state.error != null) KeyText(stringResource(R.string.encrypted_chat_error), Color(0xFFFF4545),
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("encrypted-key-error"))
            }
        }
    }
    if (rotateConfirmation) EncryptionConfirmation(
        R.string.encrypted_chat_rotate_title, R.string.encrypted_chat_rotate_description, R.string.encrypted_chat_rotate,
        "encrypted-confirm-rotation", enabled = !state.pending && state.canRotate, onDismiss = { rotateConfirmation = false },
        onConfirm = { rotateConfirmation = false; onRotateKey() },
    )
    deleteConfirmation?.let { keys ->
        EncryptionConfirmation(R.string.encrypted_chat_delete_title, R.string.encrypted_chat_delete_description,
            R.string.encrypted_chat_delete_confirm, "encrypted-confirm-delete-keys", destructive = true, enabled = !state.pending,
            onDismiss = { deleteConfirmation = null }, onConfirm = {
                deleteConfirmation = null; selectedKeys = emptySet(); selecting = false; onDeleteKeys(keys)
            })
    }
}

@Composable
private fun KeyScreenHeader(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(KeyScreenBackground)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .height(56.dp), contentAlignment = Alignment.Center) {
        Text(title, color = Color.White, fontFamily = InterFontFamily, letterSpacing = 0.sp, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 52.dp))
        KeyIconAction(stringResource(R.string.encrypted_chat_back), true, "encrypted-keys-back", Modifier.align(Alignment.CenterStart).size(48.dp), onBack) {
            Icon(painterResource(R.drawable.arrow_back), null, Modifier.width(10.dp).height(20.dp), tint = KeySecondary)
        }
        KeyIconAction(stringResource(R.string.encrypted_chat_close), true, "encrypted-keys-close", Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(48.dp), onClose) {
            Icon(painterResource(R.drawable.ic_close_small), null, Modifier.size(32.dp), tint = KeySecondary)
        }
    }
}

@Composable
private fun EncryptedChatRequestRow(
    request: EncryptedChatKeyRequest, user: UserResponseData?, role: String?, baseUrl: String, authHeaders: List<AuthHeader>,
    canManage: Boolean, hasKey: Boolean, pending: Boolean, onGrant: (String) -> Unit, onReject: (String) -> Unit,
) {
    val name = user?.displayableName() ?: stringResource(R.string.encrypted_chat_member)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(user?.avatar, baseUrl, authHeaders, null, name, Modifier.size(30.dp), initialFontSize = 14.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(R.drawable.ic_business), null, Modifier.size(16.dp), tint = KeySecondary)
                    Text(name, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(request.message.payload.deviceName ?: stringResource(R.string.encrypted_chat_device), color = KeySecondary,
                    fontSize = 12.sp, lineHeight = 16.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (request.outcome == EncryptedChatRequestOutcome.PENDING && canManage) {
                Column(horizontalAlignment = Alignment.End) {
                    if (role in setOf("administrator", "moderator")) {
                        Text(stringResource(R.string.encrypted_chat_moderator), color = KeySecondary, fontFamily = InterFontFamily, letterSpacing = 0.sp,
                            fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                    Row {
                        KeyIconAction(stringResource(R.string.encrypted_chat_grant) + ": " + name, !pending && hasKey,
                            "encrypted-grant-${request.message.uuid}", Modifier.size(36.dp), { onGrant(request.message.uuid) }) {
                            CheckGlyph(if (!pending && hasKey) EncryptedChatAccent else KeySecondary)
                        }
                        KeyIconAction(stringResource(R.string.encrypted_chat_reject) + ": " + name, !pending,
                            "encrypted-reject-${request.message.uuid}", Modifier.size(36.dp), { onReject(request.message.uuid) }) {
                            CloseGlyph(if (!pending) EncryptedChatAccent else KeySecondary)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = KeyDivider, thickness = 1.dp)
    }
}

@Composable
private fun KeyText(text: String, color: Color = Color.White, modifier: Modifier = Modifier) {
    Text(text, color = color, modifier = modifier, fontSize = 14.sp, lineHeight = 18.sp, fontFamily = InterFontFamily, letterSpacing = 0.sp)
}

@Composable
private fun KeyIconAction(label: String, enabled: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    // Foundation expands small clickable touch targets to the platform minimum;
    // visible glyphs and row centers retain the compact Figma geometry.
    Box(modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }.testTag(tag), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun KeyChevron(expanded: Boolean) {
    Canvas(Modifier.size(16.dp)) {
        val y = if (expanded) .65f else .35f
        val middle = if (expanded) .35f else .65f
        drawLine(KeySecondary, Offset(size.width * .15f, size.height * y), Offset(size.width * .5f, size.height * middle), 1.dp.toPx(), StrokeCap.Round)
        drawLine(KeySecondary, Offset(size.width * .5f, size.height * middle), Offset(size.width * .85f, size.height * y), 1.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun CopyIdentifierGlyph() {
    Canvas(Modifier.width(14.dp).height(18.dp)) {
        val stroke = 1.dp.toPx()
        drawRect(KeySecondary, Offset(size.width * .25f, stroke), Size(size.width * .65f, size.height * .74f), style = Stroke(stroke))
        drawLine(KeySecondary, Offset(stroke, size.height * .22f), Offset(stroke, size.height - stroke), stroke)
        drawLine(KeySecondary, Offset(stroke, size.height - stroke), Offset(size.width * .69f, size.height - stroke), stroke)
    }
}

@Composable
private fun CheckGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        drawLine(color, Offset(size.width * .08f, size.height * .52f), Offset(size.width * .37f, size.height * .8f), 1.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width * .37f, size.height * .8f), Offset(size.width * .95f, size.height * .17f), 1.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun CloseGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        drawLine(color, Offset(size.width * .13f, size.height * .13f), Offset(size.width * .87f, size.height * .87f), 1.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width * .13f, size.height * .87f), Offset(size.width * .87f, size.height * .13f), 1.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun EncryptionConfirmation(
    title: Int, description: Int, confirm: Int, tag: String, destructive: Boolean = false,
    enabled: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = KeyScreenBackground,
        title = { Text(stringResource(title), color = Color.White, fontFamily = InterFontFamily, letterSpacing = 0.sp, fontSize = 18.sp) },
        text = { Text(stringResource(description), color = Color(0xFF999999), fontFamily = InterFontFamily, letterSpacing = 0.sp, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled, modifier = Modifier.testTag(tag)) {
            KeyText(stringResource(confirm), if (destructive) Color(0xFFFF4545) else EncryptedChatAccent)
        } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("encrypted-cancel-confirmation")) {
            KeyText(stringResource(R.string.encrypted_chat_cancel), KeySecondary)
        } },
    )
}

private fun formatGenerationDate(timestamp: Long): String = runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
}.getOrDefault("")

private fun formatKeyReceipt(timestamp: Long): String = runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
}.getOrDefault("")
