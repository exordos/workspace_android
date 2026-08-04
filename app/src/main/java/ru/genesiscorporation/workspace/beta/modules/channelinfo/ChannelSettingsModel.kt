package ru.genesiscorporation.workspace.beta.modules.channelinfo

internal enum class ChannelVisibility {
    OPEN,
    CLOSED_OPEN_HISTORY,
    CLOSED_PROTECTED_HISTORY,
}

internal data class ChannelVisibilityFlags(
    val inviteOnly: Boolean,
    val isPrivate: Boolean,
)

internal val EDITABLE_CHANNEL_MEMBER_ROLES = listOf(
    "administrator",
    "moderator",
    "member",
)

internal fun channelVisibility(
    inviteOnly: Boolean,
    isPrivate: Boolean,
): ChannelVisibility = when {
    !inviteOnly && !isPrivate -> ChannelVisibility.OPEN
    inviteOnly && !isPrivate -> ChannelVisibility.CLOSED_OPEN_HISTORY
    else -> ChannelVisibility.CLOSED_PROTECTED_HISTORY
}

internal fun ChannelVisibility.flags(): ChannelVisibilityFlags = when (this) {
    ChannelVisibility.OPEN -> ChannelVisibilityFlags(
        inviteOnly = false,
        isPrivate = false,
    )

    ChannelVisibility.CLOSED_OPEN_HISTORY -> ChannelVisibilityFlags(
        inviteOnly = true,
        isPrivate = false,
    )

    ChannelVisibility.CLOSED_PROTECTED_HISTORY -> ChannelVisibilityFlags(
        inviteOnly = true,
        isPrivate = true,
    )
}

internal fun canManageChannel(currentUserRole: String?): Boolean =
    currentUserRole == "owner" || currentUserRole == "administrator"

internal fun canDeleteChannel(
    currentUserUuid: String?,
    ownerUuid: String?,
): Boolean =
    currentUserUuid != null && currentUserUuid == ownerUuid

internal fun canManageChannelMember(
    currentUserRole: String?,
    currentUserUuid: String?,
    memberUserUuid: String,
    memberRole: String,
    bindingsAuthoritative: Boolean = true,
): Boolean =
    bindingsAuthoritative &&
        canManageChannel(currentUserRole) &&
        currentUserUuid != null &&
        memberUserUuid != currentUserUuid &&
        memberRole != "owner"

internal fun canRemoveChannelMember(
    currentUserRole: String?,
    currentUserUuid: String?,
    memberUserUuid: String,
    memberRole: String,
    bindingsAuthoritative: Boolean = true,
): Boolean =
    bindingsAuthoritative &&
        currentUserUuid != null &&
        (
            memberUserUuid == currentUserUuid ||
                canManageChannelMember(
                    currentUserRole = currentUserRole,
                    currentUserUuid = currentUserUuid,
                    memberUserUuid = memberUserUuid,
                    memberRole = memberRole,
                    bindingsAuthoritative = bindingsAuthoritative,
                )
        )

internal fun isEditableChannelMemberRole(role: String): Boolean =
    role in EDITABLE_CHANNEL_MEMBER_ROLES
