package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

val EncryptedChatAccent = Color(0xFFFF8438)
val EncryptedChatLinkColor = Color(0xFFFF8900)
val EncryptedChatBackground = Color(0xFF1B1B1D)
val EncryptedChatServiceColor = Color(0xFF707070)

@Composable
fun EncryptedChatLock(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(painterResource(R.drawable.ic_chat_encrypted), contentDescription,
        modifier.size(18.dp), tint = EncryptedChatAccent)
}

/** The design places the dark lock on the channel avatar, not beside its title. */
@Composable
fun EncryptedChatAvatarBadge(modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_protected_avatar_badge), stringResource(R.string.encrypted_chat_title),
        modifier.size(20.dp), tint = Color.Unspecified)
}

@Composable
fun EncryptedChatTypeField(encrypted: Boolean, onEncryptedChange: (Boolean) -> Unit) {
    val palette = LocalWorkspaceColorsPalette.current
    var choosing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { choosing = true }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.encrypted_chat_type), fontFamily = InterFontFamily,
            fontSize = 14.sp, color = palette.textAdditional50, modifier = Modifier.weight(1f))
        if (encrypted) EncryptedChatLock()
        Text(stringResource(if (encrypted) R.string.encrypted_chat_title else R.string.encrypted_chat_type_regular),
            fontFamily = InterFontFamily, fontSize = 14.sp, color = palette.textHeaders)
        Text("›", color = palette.iconBase, fontSize = 20.sp)
    }
    if (choosing) {
        AlertDialog(onDismissRequest = { choosing = false }, containerColor = palette.surface,
            title = { Text(stringResource(R.string.encrypted_chat_type), color = palette.textHeaders, fontFamily = InterFontFamily) },
            text = { EncryptedChatTypeSelector(encrypted) { value -> onEncryptedChange(value); choosing = false } },
            confirmButton = { TextButton(onClick = { choosing = false }) {
                Text(stringResource(R.string.encrypted_chat_close), color = palette.textAdditional50)
            } })
    }
}

@Composable
fun EncryptedChatTypeSelector(encrypted: Boolean, onEncryptedChange: (Boolean) -> Unit) {
    val palette = LocalWorkspaceColorsPalette.current
    Column(Modifier.fillMaxWidth().testTag("encrypted-chat-type")) {
        Text(stringResource(R.string.encrypted_chat_type), fontFamily = InterFontFamily,
            fontSize = 14.sp, color = palette.textAdditional50, modifier = Modifier.padding(bottom = 4.dp))
        listOf(false, true).forEach { protected ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onEncryptedChange(protected) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = encrypted == protected,
                    onClick = { onEncryptedChange(protected) },
                    colors = RadioButtonDefaults.colors(selectedColor = EncryptedChatAccent),
                    modifier = Modifier.testTag(if (protected) "chat-type-protected" else "chat-type-regular"),
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (protected) EncryptedChatLock()
                        Text(stringResource(if (protected) R.string.encrypted_chat_title else R.string.encrypted_chat_type_regular),
                            fontFamily = InterFontFamily, fontSize = 14.sp, color = palette.textHeaders)
                    }
                    Text(stringResource(if (protected) R.string.encrypted_chat_type_protected_description else R.string.encrypted_chat_type_regular_description),
                        fontFamily = InterFontFamily, fontSize = 12.sp, color = palette.textAdditional50)
                }
            }
        }
    }
}

/** Inline links retain the paragraph's baseline and wrapping, as in the mobile frames. */
private fun AnnotatedString.Builder.encryptedLink(tag: String, label: String, enabled: Boolean, action: () -> Unit) {
    if (enabled) withLink(LinkAnnotation.Clickable(tag,
        styles = TextLinkStyles(style = SpanStyle(color = EncryptedChatLinkColor, textDecoration = TextDecoration.Underline)),
        linkInteractionListener = { action() })) { append(label) }
    else withStyle(SpanStyle(color = EncryptedChatServiceColor, textDecoration = TextDecoration.Underline)) { append(label) }
}

@Composable
private fun EncryptedServiceText(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth(), color = EncryptedChatServiceColor,
        fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 17.sp,
        letterSpacing = 0.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
}

@Composable
fun EncryptedChatRequestAccess(keyUuid: String, status: EncryptedChatRequestStatus, pending: Boolean, onRequestKey: (String) -> Unit) {
    val request = stringResource(R.string.encrypted_chat_request_inline)
    val suffix = stringResource(R.string.encrypted_chat_request_suffix)
    val waiting = stringResource(R.string.encrypted_chat_requested)
    val rejectedPrefix = stringResource(R.string.encrypted_chat_rejected_prefix)
    val retry = stringResource(R.string.encrypted_chat_request_retry)
    val rejectedSuffix = stringResource(R.string.encrypted_chat_rejected_suffix)
    val received = stringResource(R.string.encrypted_chat_received)
    val content = buildAnnotatedString {
        when (status) {
            EncryptedChatRequestStatus.REQUESTED -> append(waiting)
            EncryptedChatRequestStatus.RECEIVED -> append("✓  $received")
            EncryptedChatRequestStatus.REJECTED -> {
                append(rejectedPrefix)
                encryptedLink("retry-key-$keyUuid", retry, !pending && keyUuid.isNotBlank()) { onRequestKey(keyUuid) }
                append(rejectedSuffix)
            }
            EncryptedChatRequestStatus.IDLE -> {
                encryptedLink("request-key-$keyUuid", request, !pending && keyUuid.isNotBlank()) { onRequestKey(keyUuid) }
                append(suffix)
            }
        }
    }
    EncryptedServiceText(content, if (status == EncryptedChatRequestStatus.IDLE || status == EncryptedChatRequestStatus.REJECTED)
        Modifier.testTag("encrypted-request-key-$keyUuid") else Modifier.testTag("encrypted-request-status-$keyUuid"))
}

@Composable
fun EncryptedChatLockedMessage(keyUuid: String, status: EncryptedChatRequestStatus,
    invalidCiphertext: Boolean, pending: Boolean, showRequest: Boolean = true, onRequestKey: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("encrypted-locked-message"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EncryptedServiceText(AnnotatedString(stringResource(if (invalidCiphertext)
            R.string.encrypted_chat_cannot_decrypt else R.string.encrypted_chat_locked)))
        if (showRequest) EncryptedChatRequestAccess(keyUuid, status, pending, onRequestKey)
    }
}

@Composable
fun EncryptedChatIntroduction(owner: Boolean, state: EncryptedChatSessionState,
    showInitialRequest: Boolean, onRequestKey: (String) -> Unit) {
    val createdChannel = stringResource(R.string.encrypted_chat_created_channel)
    val joinedPrefix = stringResource(R.string.encrypted_chat_you)
    val joinedSuffix = stringResource(R.string.encrypted_chat_joined_suffix)
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp).testTag("encrypted-chat-introduction"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        EncryptedServiceText(buildAnnotatedString {
            if (owner) append(createdChannel)
            else { withStyle(SpanStyle(color = EncryptedChatLinkColor)) { append(joinedPrefix) }; append(joinedSuffix) }
        })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EncryptedServiceText(AnnotatedString(stringResource(R.string.encrypted_chat_introduction)))
            if (owner) EncryptedServiceText(AnnotatedString(stringResource(R.string.encrypted_chat_owner_guidance)))
            if (!state.hasCurrentKey && showInitialRequest) state.currentKeyUuid?.let { key ->
                EncryptedChatRequestAccess(key, state.requestStatus[key] ?: EncryptedChatRequestStatus.IDLE, state.pending, onRequestKey)
            }
        }
    }
}

@Composable
fun EncryptedChatServiceMessage(payload: EncryptedChatPayload, authorName: String,
    requestOutcome: EncryptedChatRequestOutcome?, canManage: Boolean, pending: Boolean,
    onGrant: () -> Unit, onReject: () -> Unit, canGrant: Boolean = canManage,
    isOwnDeviceRequest: Boolean = false, isRecipientDevice: Boolean = false,
    hasGenerationKey: Boolean = false, isOwnerAnnouncement: Boolean = false,
    hasOwnGenerationRequest: Boolean = false, requestStatus: EncryptedChatRequestStatus = EncryptedChatRequestStatus.IDLE,
    onRequestKey: (String) -> Unit = {},
    isLatestOwnGenerationRequest: Boolean = true,
) {
    val ownerRequestSuffix = stringResource(if (canManage) R.string.encrypted_chat_owner_request_suffix else R.string.encrypted_chat_request_notice)
    val confirm = stringResource(R.string.encrypted_chat_confirm_request)
    val or = stringResource(R.string.encrypted_chat_or)
    val reject = stringResource(R.string.encrypted_chat_reject_inline)
    when (payload.kind) {
        "key_request" -> {
            if (isOwnDeviceRequest) {
                if (!isLatestOwnGenerationRequest) return
                val status = when (requestOutcome) {
                    EncryptedChatRequestOutcome.GRANTED -> EncryptedChatRequestStatus.RECEIVED
                    EncryptedChatRequestOutcome.REJECTED -> EncryptedChatRequestStatus.REJECTED
                    else -> EncryptedChatRequestStatus.REQUESTED
                }
                EncryptedChatRequestAccess(payload.keyUuid, status, pending, onRequestKey)
            } else if (requestOutcome == EncryptedChatRequestOutcome.PENDING) {
                EncryptedServiceText(buildAnnotatedString {
                    withStyle(SpanStyle(color = EncryptedChatLinkColor)) { append(authorName) }
                    append(ownerRequestSuffix)
                    if (canManage) {
                        encryptedLink("confirm-request", confirm, !pending && canGrant, onGrant)
                        append(or)
                        encryptedLink("reject-request", reject, !pending, onReject)
                    }
                }, Modifier.padding(vertical = 12.dp).testTag("encrypted-service-message"))
            }
        }
        // The correlated request row presents pending/rejected/received in place;
        // grant and rejection envelopes must not duplicate that recipient status.
        "key_grant", "key_reject" -> if (isRecipientDevice && !hasOwnGenerationRequest) {
            val verifiedStatus = when {
                payload.kind == "key_grant" && hasGenerationKey && requestStatus == EncryptedChatRequestStatus.RECEIVED -> EncryptedChatRequestStatus.RECEIVED
                payload.kind == "key_reject" && requestStatus == EncryptedChatRequestStatus.REJECTED -> EncryptedChatRequestStatus.REJECTED
                else -> null
            }
            if (verifiedStatus != null) EncryptedChatRequestAccess(payload.keyUuid, verifiedStatus, pending, onRequestKey)
        }
        "key_announced" -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("encrypted-service-message"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EncryptedServiceText(AnnotatedString(stringResource(if (isOwnerAnnouncement && hasGenerationKey)
                R.string.encrypted_chat_new_owner_key else R.string.encrypted_chat_security_key_changed)))
            if (!hasGenerationKey && !hasOwnGenerationRequest) {
                EncryptedChatRequestAccess(payload.keyUuid, requestStatus, pending, onRequestKey)
            }
        }
    }
}
