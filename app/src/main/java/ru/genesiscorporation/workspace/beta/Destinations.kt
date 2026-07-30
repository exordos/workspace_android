package ru.genesiscorporation.workspace.beta

import kotlinx.serialization.Serializable

interface Destinations {
    val route: String
    val icon: Int
    val title: String
}

object Chat: Destinations {
    override val route = "Chat"
    override val icon = R.drawable.chat_bubble
    override val title = "Chat"
}

object ChatFlow {
    @Serializable
    object ChatList

    @Serializable
    object Inbox

    @Serializable
    object Feed

    @Serializable
    object Starred

    @Serializable
    object Drafts

    @Serializable
    data class ChatDialog(
        val title: String,
        val chatId: String,
        val topicName: String?,
        val topicUuid: String,
        val isDirectMessages: Boolean,
        val userId: Int?,
        val focusProviderMessageId: String? = null,
        val focusMessageUuid: String? = null,
        val beginForwardMessageUuid: String? = null,
        val draftStorageSlot: String? = null,
    )
    @Serializable
    data class ChatTopic(val channelName: String, val channelId: String)

    @Serializable
    data class ChatUserInfo(val userName: String, val userId: String, val avatarUrl: String, val email: String)

    @Serializable
    data class ChannelInfo(val channelId: String)
}


object LoginFlow {
    @Serializable
    object ChooseServer

    @Serializable
    object Login
}

object ProfileFlow {
    @Serializable
    object Main

    @Serializable
    object Login
}

object Calls: Destinations {
    override val route = "Calls"
    override val icon = R.drawable.call
    override val title = "Calls"
}

object Profile: Destinations {
    override val route = "Profile"
    override val icon = R.drawable.ic_profile
    override val title = "Profile"
}
