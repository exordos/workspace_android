package com.exordos.workspace.data.uio

data class ServerSettingsUIO(
    val organizationUuid: String? = null,
    val pushNotificationsEnabled: Boolean = false,
    val emailAuthEnabled: Boolean,
    val realmName: String,
    val realmUrl: String,
    val realmIcon: String,
    val meetUrl: String
)
