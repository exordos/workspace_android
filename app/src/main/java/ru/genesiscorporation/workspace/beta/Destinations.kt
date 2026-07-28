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
    data class ChatDialog(val title: String, val chatId: String, val topicName: String?, val topicUuid: String, val isDirectMessages: Boolean, val userId: Int?)
    @Serializable
    data class ChatTopic(val channelName: String, val channelId: String)

    @Serializable
    data class ChatUserInfo(val userName: String, val userId: String, val avatarUrl: String, val email: String)
}


object LoginFlow {
    @Serializable
    object ChooseServer

    @Serializable
    object Login
    @Serializable
    data class Otp(val login: String, val password: String)
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