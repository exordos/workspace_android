package com.exordos.workspace.data.uio

data class FolderUIO(
    val uuid: String,
    var title: String,
    var unreadCount: Int,
    val systemType: String,
    val creationDate: String,
    var items: List<FolderItemUIO> = emptyList()

)
