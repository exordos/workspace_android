package com.exordos.workspace.data.uio

data class FolderItemUIO(
    val uuid: String,
    val streamUuid: String,
    val chatType: String,
    var unreadCount: Int,
)
