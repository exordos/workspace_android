package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.first
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

class ChatUserInfoViewModel(
    val userName: String,
    val userId: String,
    val avatarUrl: String,
    val email: String,
    val client: WorkspaceAPIClient,
    val repo: EventsRepository
): ViewModel() {

    suspend fun callButtonTapped(roomName: String) {
        val messageText = "${repo.jitsiServerUrl}/${roomName}"
        val currentUserId = client.userViewModel.repo.userIdFlow.first()
//        var newMessage = Message(null,
//            client.userViewModel.userData?.full_name ?: "",
//            currentUserId?.toInt() ?: 0,
//            messageText,
//            (System.currentTimeMillis() / 1000),
//            client.userViewModel.userData?.avatar_url ?: "",
//            "",
//            true,
//            emptyList()
//        )
//        val sendMessageRequest = SendMessageRequest(
//            type = "direct",
//            to = "[${userId}, ${currentUserId}]",
//            content = messageText,
//            topic = null
//        )
//        val response = client.performRequest(sendMessageRequest)
//        when(response) {
//            is ApiResult.Success -> {
//                newMessage.id = response.value.id
//            }
//            is ApiResult.Error -> {
//
//            }
//        }
    }

    fun imageId(customProfileFieldId: Int): Int {
        when(customProfileFieldId) {
            1 -> return R.drawable.ic_business
            2 -> return R.drawable.ic_handshake
            else -> return R.drawable.ic_userid
        }
    }
}