package com.exordos.workspace.data.uio

import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageResponsePayload
import com.exordos.workspace.data.remote.dto.MessageReaction
import com.exordos.workspace.data.remote.dto.FolderResponseData
import com.exordos.workspace.data.remote.dto.FolderItem
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import com.exordos.workspace.data.remote.dto.Draft
import com.exordos.workspace.data.remote.dto.ProjectResponseData
import com.exordos.workspace.data.remote.dto.ServerSettingsResponseData

fun UserResponseData.toUio(): UserUIO = UserUIO(
    email = email,
    firstName = firstName,
    lastName = lastName,
    username = username,
    uuid = uuid,
    statusEmoji = statusEmoji,
    statusText = statusText,
    status = status,
    avatar = avatar,
)

fun Stream.toUio(): StreamUIO = StreamUIO(
    uuid = uuid,
    unreadCount = unreadCount,
    activeUnreadCount = activeUnreadCount,
    passiveUnreadCount = passiveUnreadCount,
    updatedAt = updatedAt,
    name = name,
    isPrivate = isPrivate,
    color = color,
    lastMessageUuid = lastMessageUuid,
    defaultTopicUuid = defaultTopicUuid,
    directUserUuid = directUserUuid,
    notificationMode = notificationMode,
    isArchived = isArchived,
    encryption = encryption,
    currentEncryptionKeyUuid = currentEncryptionKeyUuid,
    ownerUuid = ownerUuid,
    projectId = projectId,
    userUuid = userUuid,
    role = role,
    description = description,
    inviteOnly = inviteOnly,
    announce = announce,
)

fun TopicsResponseData.toUio(): TopicUIO = TopicUIO(
    uuid = uuid,
    name = name,
    color = color,
    streamUuid = streamUuid,
    updatedAt = updatedAt,
    unreadCount = unreadCount,
    activeUnreadCount = activeUnreadCount,
    isDone = isDone,
    isDefault = isDefault,
    lastMessageUuid = lastMessageUuid,
    notificationMode = notificationMode,
    summary = summary,
)

fun MessageResponse.toUio(): MessageUIO = MessageUIO(
    uuid = uuid,
    updatedAt = updatedAt,
    createdAt = createdAt,
    streamUuid = streamUuid,
    topicUuid = topicUuid,
    userUuid = userUuid,
    authorUuid = authorUuid,
    payload = payload.toUio(),
    isOwn = isOwn,
    reactions = reactions.toMap(),
    read = read,
    projectId = projectId,
    mentioned = mentioned,
)

fun MessageResponsePayload.toUio(): MessagePayloadUIO = MessagePayloadUIO(
    kind = kind,
    content = content,
    keyUuid = keyUuid,
    deviceId = deviceId,
    deviceName = deviceName,
    publicKey = publicKey,
    requestMessageUuid = requestMessageUuid,
    senderDeviceId = senderDeviceId,
    recipientDeviceId = recipientDeviceId,
)

fun MessageReaction.toUio(): MessageReactionUIO = MessageReactionUIO(
    uuid = uuid,
    userUuid = userUuid,
    emojiName = emojiName,
    messageUuid = messageUuid,
)

fun FolderResponseData.toUio(): FolderUIO = FolderUIO(
    uuid = uuid,
    title = title,
    unreadCount = unreadCount,
    systemType = systemType,
    creationDate = creationDate
)

fun FolderItem.toUio(): FolderItemUIO = FolderItemUIO(
    uuid = uuid,
    streamUuid = streamUuid,
    chatType = chatType,
    unreadCount = unreadCount,
    folder = folder
)

fun StreamBindingResponseData.toUio(): StreamBindingUIO = StreamBindingUIO(
    uuid = uuid,
    role = role,
    streamUuid = streamUuid,
    userUuid = userUuid,
)

fun Draft.toUio(): DraftUIO = DraftUIO(
    uuid = uuid,
    streamUuid = streamUuid,
    topicUuid = topicUuid,
    updatedAt = updatedAt,
    payload = payload.toUio(),
    revision = revision,
)

fun ProjectResponseData.toUio(): ProjectUIO = ProjectUIO(
    uuid = uuid,
    name = name,
    description = description,
)

fun ServerSettingsResponseData.toUio(): ServerSettingsUIO = ServerSettingsUIO(
    organizationUuid = organizationUuid,
    pushNotificationsEnabled = pushNotificationsEnabled,
    emailAuthEnabled = email_auth_enabled,
    realmName = realmName,
    realmUrl = realmUrl,
    realmIcon = realmIcon,
    meetUrl = meetUrl,
)

fun MessagePayloadUIO.toDto(): MessageResponsePayload = MessageResponsePayload(
    kind = kind,
    content = content,
    keyUuid = keyUuid,
    deviceId = deviceId,
    deviceName = deviceName,
    publicKey = publicKey,
    requestMessageUuid = requestMessageUuid,
    senderDeviceId = senderDeviceId,
    recipientDeviceId = recipientDeviceId,
)
