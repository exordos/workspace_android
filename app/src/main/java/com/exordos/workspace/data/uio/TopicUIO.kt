package com.exordos.workspace.data.uio

data class TopicUIO(
    val uuid: String,
    var name: String,
    val color: Int,
    val streamUuid: String,
    var updatedAt: String,
    var unreadCount: Int,
    var activeUnreadCount: Int,
    var isDone: Boolean,
    val isDefault: Boolean,
    var lastMessageUuid: String? = null,
    var notificationMode: String,
    var lastMessage: MessageUIO? = null,
    var summary: String? = null
)
