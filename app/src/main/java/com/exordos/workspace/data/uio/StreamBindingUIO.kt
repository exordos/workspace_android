package com.exordos.workspace.data.uio

data class StreamBindingUIO(
    val uuid: String,
    var role: String,
    val streamUuid: String,
    var userUuid: String
)
