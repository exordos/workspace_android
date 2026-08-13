package ru.genesiscorporation.workspace.beta.modules.chatchannels

import ru.genesiscorporation.workspace.beta.ChatFlow

internal fun CreatedTopicResult.toChatDialog(): ChatFlow.ChatDialog? {
    if (
        streamName.isBlank() ||
        streamUuid.isBlank() ||
        name.isBlank() ||
        uuid.isBlank()
    ) {
        return null
    }
    return ChatFlow.ChatDialog(
        title = streamName,
        chatId = streamUuid,
        topicName = name,
        topicUuid = uuid,
        isDirectMessages = false,
        userId = null,
    )
}
