package com.exordos.workspace.data.uio

data class MessagePayloadUIO(
    val kind: String,
    var content: String = "",
    val keyUuid: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val publicKey: String? = null,
    val requestMessageUuid: String? = null,
    val senderDeviceId: String? = null,
    val recipientDeviceId: String? = null,
)
