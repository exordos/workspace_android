package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageResponse
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.encryptedMessagePlacementUuid

class EncryptedMessageForwardingTest {
    @Test fun `encrypted readback verifies decrypted snapshot and commits only original ciphertext`() = runBlocking {
        val source = projectedSource()
        val expected = requireNotNull(buildWorkspaceForwardMarkdown(source))
        val raw = message(RETURNED, MessageResponsePayload("e2ee", "destination-ciphertext", keyUuid = KEY))
        val confirmed = mutableListOf<MessageResponse>()
        var checked = 0
        val forwarding = MessageForwarding(listOf(source), AUTHOR,
            post = { _, _, markdown -> assertEquals(expected, markdown); ApiResult.Success(SendMessageResponse(RETURNED, TOPIC)) },
            read = { ApiResult.Success(listOf(raw)) }, onConfirmed = { confirmed += it },
            verifyContent = { wire, _, captured ->
                checked++
                assertSame(raw, wire)
                assertEquals(expected, captured)
                wire.payload.kind == "e2ee" && captured == expected
            })
        forwarding.send(TARGET)
        assertEquals(ForwardDeliveryStatus.COMPLETED, forwarding.state.value.status)
        assertEquals(1, checked)
        assertSame(raw, confirmed.single())
        assertEquals("e2ee", confirmed.single().payload.kind)
        assertEquals("destination-ciphertext", confirmed.single().payload.content)
        assertEquals("source-ciphertext", source.originalEncryptedPayload!!.content)
        assertNull(raw.originalEncryptedPayload)
    }

    @Test fun `lost encrypted ACK reads UUIDv5 placement and never sends a second POST`() = runBlocking {
        val placement = encryptedMessagePlacementUuid(TOPIC, CANONICAL)
        val reads = mutableListOf<String>()
        var posts = 0
        var readable = false
        val raw = message(placement, MessageResponsePayload("e2ee", "opaque", keyUuid = KEY))
        val forwarding = MessageForwarding(listOf(projectedSource()), AUTHOR,
            post = { _, _, _ -> posts++; ApiResult.Error(ApiError("lost", "REQUEST_FAILED")) },
            read = { reads += it; ApiResult.Success(if (readable) listOf(raw) else emptyList()) },
            onConfirmed = { assertSame(raw, it) }, newUuid = { CANONICAL },
            verificationUuid = { canonical, target -> encryptedMessagePlacementUuid(target.topicUuid, canonical) },
            verifyContent = { wire, _, _ -> wire.payload.kind == "e2ee" })
        forwarding.send(TARGET)
        assertEquals(ForwardDeliveryStatus.UNCERTAIN, forwarding.state.value.status)
        forwarding.send(TARGET)
        readable = true
        forwarding.verify()
        assertEquals(1, posts)
        assertEquals(listOf(placement, placement), reads)
        assertEquals(ForwardDeliveryStatus.COMPLETED, forwarding.state.value.status)
    }

    @Test fun `missing verification key remains uncertain and cannot unlock another POST`() = runBlocking {
        var posts = 0
        val forwarding = MessageForwarding(listOf(projectedSource()), AUTHOR,
            post = { _, _, _ -> posts++; ApiResult.Success(SendMessageResponse(RETURNED, TOPIC)) },
            read = { ApiResult.Success(listOf(message(RETURNED, MessageResponsePayload("e2ee", "opaque", keyUuid = KEY)))) },
            onConfirmed = { fail("No key means no confirmed delivery") },
            verifyContent = { _, _, _ -> throw ForwardPreparationFailure(R.string.forward_quoted_message_unavailable) })
        forwarding.send(TARGET)
        forwarding.verify()
        forwarding.send(TARGET)
        assertEquals(1, posts)
        assertEquals(ForwardDeliveryStatus.UNCERTAIN, forwarding.state.value.status)
    }

    @Test fun `encrypted verification cannot bypass destination and author checks`() = runBlocking {
        var decryptions = 0
        val forwarding = MessageForwarding(listOf(projectedSource()), AUTHOR,
            post = { _, _, _ -> ApiResult.Success(SendMessageResponse(RETURNED, TOPIC)) },
            read = { ApiResult.Success(listOf(message(RETURNED, MessageResponsePayload("e2ee", "opaque", keyUuid = KEY)).copy(authorUuid = SOURCE))) },
            onConfirmed = { fail("Foreign author cannot confirm a forward") },
            verifyContent = { _, _, _ -> decryptions++; true })
        forwarding.send(TARGET)
        assertEquals(0, decryptions)
        assertEquals(ForwardDeliveryStatus.UNCERTAIN, forwarding.state.value.status)
    }

    @Test fun `wire ciphertext and service messages cannot be forwarded as visible text`() {
        val raw = message(SOURCE, MessageResponsePayload("e2ee", "opaque", keyUuid = KEY))
        assertFalse(canForwardMessage(raw))
        assertNull(buildWorkspaceForwardMarkdown(raw))
        assertNull(buildWorkspaceForwardMarkdown(raw.copy(payload = MessageResponsePayload("key_request"))))
        assertTrue(canForwardMessage(projectedSource()))
        assertTrue(requireNotNull(buildWorkspaceForwardMarkdown(projectedSource())).contains("visible source"))
    }

    @Test fun `preparation decrypts original projection and nested source stream before snapshotting files`() = runBlocking {
        val source = projectedSource()
        val nested = message(NESTED, MessageResponsePayload("e2ee", "nested-ciphertext", keyUuid = KEY))
            .copy(streamUuid = NESTED_STREAM)
        val decryptedStreams = mutableListOf<String>()
        var copies = 0
        val preparation = ForwardContentPreparation(listOf(source), resolve = { nested },
            copyFile = { uuid, name, stream ->
                assertEquals(FILE, uuid); assertEquals("photo.png", name); assertEquals(STREAM, stream)
                copies++; COPIED_FILE
            },
            presentMessage = { input -> presentForwardMessage(input) { wire ->
                decryptedStreams += wire.streamUuid
                assertEquals("e2ee", wire.payload.kind)
                assertNull(wire.originalEncryptedPayload)
                if (wire.uuid == SOURCE) "Fresh source\n[Other](urn:quote:$NESTED)\n![photo.png](urn:image:$FILE)"
                else "Nested plaintext"
            } })
        val prepared = preparation.prepare(TARGET)
        assertTrue(prepared.contains("Fresh source"))
        assertTrue(prepared.contains("Nested plaintext"))
        assertTrue(prepared.contains("urn:image:$COPIED_FILE"))
        assertFalse(prepared.contains("ciphertext"))
        assertFalse(prepared.contains("visible source"))
        assertEquals(listOf(STREAM, NESTED_STREAM), decryptedStreams)
        assertEquals(1, copies)
        assertEquals("source-ciphertext", source.originalEncryptedPayload!!.content)
        assertEquals("nested-ciphertext", nested.payload.content)
    }

    @Test fun `cached forward cannot reuse plaintext after its generation key is deleted`() = runBlocking {
        var hasKey = true
        val preparation = ForwardContentPreparation(listOf(projectedSource()), resolve = { null },
            copyFile = { _, _, _ -> error("No attachments") },
            presentMessage = { input -> presentForwardMessage(input) { if (hasKey) "authorized plaintext" else null } })
        assertTrue(preparation.prepare(TARGET).contains("authorized plaintext"))
        hasKey = false
        assertTrue(runCatching { preparation.prepare(TARGET) }.exceptionOrNull() is ForwardPreparationFailure)
    }

    @Test fun `external share resolves encrypted nested quotes without exporting envelopes`() = runBlocking {
        val source = projectedSource()
        val nested = message(NESTED, MessageResponsePayload("e2ee", "nested-ciphertext", keyUuid = KEY))
        val result = materializeExternalMessages(listOf(source), presentMessage = { input ->
            presentForwardMessage(input) { wire ->
                if (wire.uuid == SOURCE) "Source plaintext\n[Other](urn:quote:$NESTED)\n[file.pdf](urn:file:$FILE)"
                else "Nested plaintext"
            }
        }) { nested }
        assertTrue(result.text.contains("Source plaintext"))
        assertTrue(result.text.contains("Nested plaintext"))
        assertFalse(result.text.contains("ciphertext"))
        assertFalse(result.text.contains("urn:"))
        assertEquals(listOf(ExternalShareFile(FILE, "file.pdf")), result.files)
        assertEquals("source-ciphertext", source.originalEncryptedPayload!!.content)
    }

    @Test fun `external share fails closed when source key is unavailable`() = runBlocking {
        assertTrue(runCatching {
            materializeExternalMessages(listOf(projectedSource()), presentMessage = { input ->
                presentForwardMessage(input) { null }
            }) { null }
        }.exceptionOrNull() is ForwardPreparationFailure)
    }

    private fun projectedSource(): MessageResponse = message(SOURCE, MessageResponsePayload("markdown", "visible source"))
        .copy(originalEncryptedPayload = MessageResponsePayload("e2ee", "source-ciphertext", keyUuid = KEY))

    private fun message(uuid: String, payload: MessageResponsePayload) = MessageResponse(uuid,
        "2026-10-15T12:00:00Z", "2026-10-15T12:00:00Z", STREAM, TOPIC, AUTHOR, AUTHOR,
        payload, true, emptyMap(), true)

    private companion object {
        const val SOURCE = "00000000-0000-0000-0000-000000000001"
        const val NESTED = "00000000-0000-0000-0000-000000000002"
        const val CANONICAL = "00000000-0000-0000-0000-000000000003"
        const val RETURNED = "00000000-0000-0000-0000-000000000004"
        const val STREAM = "00000000-0000-0000-0000-000000000005"
        const val TOPIC = "00000000-0000-0000-0000-000000000006"
        const val AUTHOR = "00000000-0000-0000-0000-000000000007"
        const val KEY = "00000000-0000-0000-0000-000000000008"
        const val NESTED_STREAM = "00000000-0000-0000-0000-000000000009"
        const val FILE = "00000000-0000-0000-0000-000000000010"
        const val COPIED_FILE = "00000000-0000-0000-0000-000000000011"
        val TARGET = ForwardDestination(STREAM, TOPIC)
    }
}
