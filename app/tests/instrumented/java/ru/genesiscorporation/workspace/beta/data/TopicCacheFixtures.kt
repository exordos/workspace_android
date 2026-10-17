package ru.genesiscorporation.workspace.beta.data

import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

/** Immediate fixture writes have no intervening IO. Async production code must carry its read scope. */
fun EventsRepository.addStreamTopics(streamUuid: String, topics: List<TopicsResponseData>) =
    addStreamTopics(streamUuid, topics, captureTopicCacheScope())
fun EventsRepository.appendItemsToTopicsPool(topics: List<TopicsResponseData>) =
    appendItemsToTopicsPool(topics, captureTopicCacheScope())
fun EventsRepository.didReceiveTopicEvent(payload: String, action: String) =
    didReceiveTopicEvent(payload, action, captureTopicCacheScope())
