package com.exordos.workspace.data.uio

data class UserUIO(
    var email: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    val username: String,
    val uuid: String,
    var statusEmoji: String? = null,
    var statusText: String? = null,
    var status: String,
    var avatar: String
) {
    fun displayableName(): String {
        var displayName = ""
        if (firstName != null) {
            displayName += firstName
        }

        if (lastName != null) {
            if (!displayName.isEmpty()) {
                displayName += " "
            }
            displayName += lastName
        }

        if (displayName.isEmpty()) {
            displayName += username
        }

        return  displayName
    }

    fun statusDescription(): String {
        if (status == "active") {
            return "В сети"
        } else {
            return "Не в сети"
        }
    }
}
