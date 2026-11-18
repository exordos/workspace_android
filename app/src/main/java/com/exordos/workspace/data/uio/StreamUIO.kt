package com.exordos.workspace.data.uio

data class StreamUIO(
    val uuid: String,
    var unreadCount: Int,
    var activeUnreadCount: Int,
    var passiveUnreadCount: Int,
    var updatedAt: String,
    var name: String,
    val isPrivate: Boolean,
    val color: Int,
    var lastMessageUuid: String? = null,
    var defaultTopicUuid: String? = null,
    val directUserUuid: String? = null,
    var notificationMode: String,
    var directUser: UserUIO? = null,
    var lastMessage: MessageUIO? = null,
    val isArchived: Boolean = false,
    val encryption: Boolean = false,
    var currentEncryptionKeyUuid: String? = null,
    val ownerUuid: String? = null,
    val projectId: String? = null,
    val userUuid: String? = null,
    val role: String? = null,
    val description: String = "",
    val inviteOnly: Boolean = false,
    val announce: Boolean = false,
) {
    fun displayableName(participant: UserUIO? = directUser): String =
        participant?.takeIf { it.uuid == directUserUuid }
            ?.displayableName()?.trim()?.takeIf { it.isNotEmpty() }
            ?: name
}
