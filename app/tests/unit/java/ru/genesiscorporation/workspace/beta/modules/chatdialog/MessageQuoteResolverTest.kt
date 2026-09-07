package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class MessageQuoteResolverTest {
    @Test
    fun `adjacent references share a batched request and duplicate loads use cache`() = runBlocking {
        val requests = mutableListOf<List<String>>()
        val resolver = MessageQuoteResolver(this) { ids ->
            requests += ids
            ApiResult.Success(ids.map(::message))
        }

        resolver.load(listOf(FIRST_UUID))
        resolver.load(listOf(SECOND_UUID, FIRST_UUID))
        resolver.awaitSettled(FIRST_UUID, SECOND_UUID)
        resolver.load(listOf(FIRST_UUID, SECOND_UUID))
        yield()

        assertEquals(listOf(listOf(FIRST_UUID, SECOND_UUID)), requests)
        val ready = resolver.states.value[FIRST_UUID] as MessageQuoteState.Ready
        assertEquals("source-stream", ready.message.streamUuid)
    }

    @Test
    fun `loading references are not requested twice`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<ApiResult<List<MessageResponse>, ApiError>>()
        var requests = 0
        val resolver = MessageQuoteResolver(this) {
            requests++
            started.complete(Unit)
            result.await()
        }
        resolver.load(listOf(FIRST_UUID))
        started.await()

        resolver.load(listOf(FIRST_UUID))
        yield()
        assertEquals(1, requests)
        assertEquals(MessageQuoteState.Loading, resolver.states.value[FIRST_UUID])

        result.complete(ApiResult.Success(listOf(message(FIRST_UUID))))
        resolver.awaitSettled(FIRST_UUID)
    }

    @Test
    fun `missing and forbidden references are unavailable without automatic retries`() = runBlocking {
        val missing = MessageQuoteResolver(this) { ApiResult.Success(emptyList()) }
        val forbidden = MessageQuoteResolver(this) { ApiResult.Error(ApiError("Forbidden", "403")) }
        missing.load(listOf(FIRST_UUID))
        forbidden.load(listOf(FIRST_UUID))

        missing.awaitSettled(FIRST_UUID)
        forbidden.awaitSettled(FIRST_UUID)

        assertEquals(MessageQuoteState.Unavailable, missing.states.value[FIRST_UUID])
        assertEquals(MessageQuoteState.Unavailable, forbidden.states.value[FIRST_UUID])
    }

    @Test
    fun `transient errors wait for explicit retry and recover`() = runBlocking {
        var requests = 0
        val resolver = MessageQuoteResolver(this) {
            requests++
            if (requests == 1) ApiResult.Error(ApiError("Server error", "500"))
            else ApiResult.Success(listOf(message(FIRST_UUID)))
        }
        resolver.load(listOf(FIRST_UUID))
        resolver.awaitSettled(FIRST_UUID)
        assertEquals(MessageQuoteState.Error, resolver.states.value[FIRST_UUID])
        resolver.load(listOf(FIRST_UUID))
        yield()
        assertEquals(1, requests)

        resolver.retry(FIRST_UUID)
        resolver.awaitSettled(FIRST_UUID)

        assertEquals(2, requests)
        assertTrue(resolver.states.value[FIRST_UUID] is MessageQuoteState.Ready)
    }

    @Test
    fun `deleted reference cannot be restored by a late response or retry`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ApiResult<List<MessageResponse>, ApiError>>()
        val resolver = MessageQuoteResolver(this) {
            started.complete(Unit)
            response.await()
        }
        resolver.load(listOf(FIRST_UUID))
        started.await()
        resolver.invalidate(FIRST_UUID)
        response.complete(ApiResult.Success(listOf(message(FIRST_UUID))))
        yield()
        resolver.retry(FIRST_UUID)
        resolver.load(listOf(FIRST_UUID))

        assertEquals(MessageQuoteState.Unavailable, resolver.states.value[FIRST_UUID])
    }

    @Test
    fun `reset discards old account response without overriding a new account lookup`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val firstResponse = CompletableDeferred<ApiResult<List<MessageResponse>, ApiError>>()
        var requests = 0
        val resolver = MessageQuoteResolver(this) {
            requests++
            if (requests == 1) {
                firstStarted.complete(Unit)
                firstResponse.await()
            } else {
                ApiResult.Success(listOf(message(FIRST_UUID).copy(payload = MessageResponsePayload("markdown", "new account"))))
            }
        }
        resolver.load(listOf(FIRST_UUID))
        firstStarted.await()
        resolver.reset()
        resolver.load(listOf(FIRST_UUID))
        resolver.awaitSettled(FIRST_UUID)
        firstResponse.complete(ApiResult.Success(listOf(message(FIRST_UUID))))
        yield()

        assertEquals("new account", (resolver.states.value[FIRST_UUID] as MessageQuoteState.Ready).message.payload.content)
    }

    @Test
    fun `large reference groups are split into bounded requests and extra rows ignored`() = runBlocking {
        val ids = (1..51).map { "00000000-0000-4000-8000-${it.toString().padStart(12, '0')}" }
        val batches = mutableListOf<List<String>>()
        val resolver = MessageQuoteResolver(this) {
            batches += it
            ApiResult.Success(it.map(::message) + message(FIRST_UUID))
        }

        resolver.load(ids)
        resolver.awaitSettled(*ids.toTypedArray())

        assertEquals(listOf(50, 1), batches.map { it.size })
        assertEquals(ids.toSet(), resolver.states.value.keys)
    }

    @Test
    fun `invalid identifier stays unavailable and cache owns its message payload`() = runBlocking {
        val source = message(FIRST_UUID)
        val requests = mutableListOf<List<String>>()
        val resolver = MessageQuoteResolver(this) {
            requests += it
            ApiResult.Success(listOf(source))
        }
        resolver.load(listOf("invalid", FIRST_UUID))
        resolver.awaitSettled("invalid", FIRST_UUID)
        source.payload.content = "Changed outside resolver"

        assertEquals(listOf(listOf(FIRST_UUID)), requests)
        assertEquals(MessageQuoteState.Unavailable, resolver.states.value["invalid"])
        assertEquals("Source message", (resolver.states.value[FIRST_UUID] as MessageQuoteState.Ready).message.payload.content)
    }

    @Test
    fun `realtime source edit replaces cached content without another request`() = runBlocking {
        var requests = 0
        val resolver = MessageQuoteResolver(this) {
            requests++
            ApiResult.Success(listOf(message(FIRST_UUID)))
        }
        resolver.load(listOf(FIRST_UUID))
        resolver.awaitSettled(FIRST_UUID)
        val update = message(FIRST_UUID).copy(payload = MessageResponsePayload("markdown", "Edited source"))

        resolver.update(update)
        update.payload.content = "Changed outside cache"
        resolver.load(listOf(FIRST_UUID))
        yield()

        assertEquals(1, requests)
        assertEquals("Edited source", (resolver.states.value[FIRST_UUID] as MessageQuoteState.Ready).message.payload.content)
    }

    @Test
    fun `realtime edit cannot be overwritten by a preceding HTTP lookup`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ApiResult<List<MessageResponse>, ApiError>>()
        val resolver = MessageQuoteResolver(this) {
            started.complete(Unit)
            response.await()
        }
        resolver.load(listOf(FIRST_UUID))
        started.await()
        resolver.update(message(FIRST_UUID).copy(payload = MessageResponsePayload("markdown", "Edited source")))
        response.complete(ApiResult.Success(listOf(message(FIRST_UUID))))
        yield()

        assertEquals("Edited source", (resolver.states.value[FIRST_UUID] as MessageQuoteState.Ready).message.payload.content)
        resolver.invalidate(FIRST_UUID)
        resolver.update(message(FIRST_UUID))
        assertEquals(MessageQuoteState.Unavailable, resolver.states.value[FIRST_UUID])
    }

    private suspend fun MessageQuoteResolver.awaitSettled(vararg ids: String) = withTimeout(5_000) {
        states.first { current -> ids.all { current[it] != null && current[it] != MessageQuoteState.Loading } }
        Unit
    }

    private fun message(uuid: String) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-09-07T12:00:00Z",
        createdAt = "2026-09-07T12:00:00Z",
        streamUuid = "source-stream",
        topicUuid = "source-topic",
        userUuid = "current-user",
        authorUuid = "source-author",
        payload = MessageResponsePayload("markdown", "Source message"),
        isOwn = false,
        reactions = emptyMap(),
        read = true,
    )

    private companion object {
        const val FIRST_UUID = "ac0819d1-a91f-4b87-a1bc-e4b0d353f162"
        const val SECOND_UUID = "2ff4fdd2-cb41-43a9-8532-262b8b36b858"
    }
}
