package ru.genesiscorporation.workspace.beta.data.push

import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.data.navigation.canonicalWorkspaceBaseUrl
import ru.genesiscorporation.workspace.beta.data.navigation.canonicalWorkspaceRealmUrl

/**
 * A validated, content-free navigation target derived from an FCM data payload.
 *
 * Message text and author names are intentionally excluded so notification
 * intents cannot retain chat content longer than the notification itself.
 */
data class PushNavigationRequest(
    val realmUrl: String,
    val providerChatKey: String,
    val topicName: String?,
    val workspaceMessageId: Int,
) {
    fun matches(account: WorkspaceAccount): Boolean =
        canonicalWorkspaceBaseUrl(account.baseUrl) == realmUrl

    fun notificationId(): Int =
        notificationId(realmUrl, workspaceMessageId)

    fun notificationGroupKey(): String =
        "workspace:${"$realmUrl\u0000$providerChatKey\u0000${topicName.orEmpty()}".hashCode()}"

    companion object {
        const val EXTRA_REALM_URL =
            "ru.genesiscorporation.workspace.beta.extra.REALM_URL"
        const val EXTRA_PROVIDER_CHAT_KEY =
            "ru.genesiscorporation.workspace.beta.extra.PROVIDER_CHAT_KEY"
        const val EXTRA_TOPIC_NAME =
            "ru.genesiscorporation.workspace.beta.extra.TOPIC_NAME"
        const val EXTRA_WORKSPACE_MESSAGE_ID =
            "ru.genesiscorporation.workspace.beta.extra.WORKSPACE_MESSAGE_ID"

        private const val MAX_TOPIC_LENGTH = 256
        private val providerChatKeyPattern =
            Regex("^(channel|direct|group_direct):[0-9]+(?:,[0-9]+)*$")

        fun notificationId(
            realmUrl: String,
            workspaceMessageId: Int,
        ): Int = "$realmUrl\u0000$workspaceMessageId".hashCode()

        fun fromMessageData(data: Map<String, String>): PushNavigationRequest? {
            val realmUrl = data["realm_url"]
                ?.let(::canonicalWorkspaceRealmUrl)
                ?: return null
            val messageId = data["workspace_message_id"]
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: return null
            val providerChatKey = when (data["kind"]) {
                "stream_chat_message" -> {
                    val streamId = data["stream_id"]
                        ?.toProviderId()
                        ?: data["steram_id"]?.toProviderId()
                        ?: return null
                    "channel:$streamId"
                }

                "private_chat_message" -> {
                    val participantIds = listOfNotNull(
                        data["sender_id"]?.toProviderId(),
                        data["user_id"]?.toProviderId(),
                    ).distinct().sortedBy { it.toLong() }
                    if (participantIds.size != 2) return null
                    "direct:${participantIds.joinToString(",")}"
                }

                "group_chat_message" -> {
                    val participantIds = buildList {
                        data["pm_users"]
                            ?.split(',')
                            ?.mapNotNullTo(this) { it.trim().toProviderId() }
                        data["sender_id"]?.toProviderId()?.let(::add)
                    }.distinct().sortedBy { it.toLong() }
                    if (participantIds.size < 3) return null
                    "group_direct:${participantIds.joinToString(",")}"
                }

                else -> return null
            }
            val topicName = if (providerChatKey.startsWith("channel:")) {
                data["topic"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= MAX_TOPIC_LENGTH }
                    ?: return null
            } else {
                null
            }
            return fromIntentFields(
                realmUrl = realmUrl,
                providerChatKey = providerChatKey,
                topicName = topicName,
                workspaceMessageId = messageId,
            )
        }

        fun fromIntentFields(
            realmUrl: String?,
            providerChatKey: String?,
            topicName: String?,
            workspaceMessageId: Int,
        ): PushNavigationRequest? {
            val validatedRealmUrl = realmUrl
                ?.let(::canonicalWorkspaceRealmUrl)
                ?: return null
            val validatedKey = providerChatKey
                ?.takeIf { it.length <= 256 && providerChatKeyPattern.matches(it) }
                ?: return null
            if (workspaceMessageId <= 0) return null
            val validatedTopic = topicName
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_TOPIC_LENGTH }
            if (validatedKey.startsWith("channel:") && validatedTopic == null) {
                return null
            }
            if (!validatedKey.startsWith("channel:") && validatedTopic != null) {
                return null
            }
            return PushNavigationRequest(
                realmUrl = validatedRealmUrl,
                providerChatKey = validatedKey,
                topicName = validatedTopic,
                workspaceMessageId = workspaceMessageId,
            )
        }

        private fun String.toProviderId(): String? {
            val numericId = toLongOrNull()?.takeIf { it >= 0 } ?: return null
            return numericId.toString()
        }
    }
}

fun resolvePushAccountTarget(
    request: PushNavigationRequest,
    accounts: List<WorkspaceAccount>,
    selectedAccountId: String?,
): WorkspaceAccount? {
    val matchingAccounts = accounts.filter(request::matches)
    return selectedAccountId
        ?.let { selectedId ->
            matchingAccounts.firstOrNull { it.accountId == selectedId }
        }
        ?: matchingAccounts.singleOrNull()
}
