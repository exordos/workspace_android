package com.exordos.workspace.data.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.SecureRandom

class WorkspaceChatCryptoTest {
    private val crypto = WorkspaceChatCrypto()
    private val context = ChatMessageContext("chat-a", "generation-a")
    private val grantContext = ChatGrantContext("chat-a", "generation-a", "request-a", "issuer", "requester")

    @Test fun rfc5869HkdfSha256() {
        assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            HkdfSha256.derive(ByteArray(22) { 11 }, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42))
    }

    @Test fun rfc8439ChaCha20Poly1305() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("070000004041424344454647")
        val aad = hex("50515253c0c1c2c3c4c5c6c7")
        val plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.".toByteArray()
        val expected = hex("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6" +
            "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b36" +
            "92ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc3" +
            "ff4def08e4b7a9de576d26586cec64b61161ae10b594f09e26a7e902ecbd0600691")
        assertArrayEquals(expected, ChaChaAead.encrypt(key, nonce, plaintext, aad))
        assertArrayEquals(plaintext, ChaChaAead.decrypt(key, nonce, expected, aad))
    }

    @Test fun rfc9180HpkeA21BaseModeBothDirections() {
        val info = hex("4f6465206f6e2061204772656369616e2055726e")
        val publicKey = hex("4310ee97d88cc1f088a5576c77ab0cf5c3ac797f3d95139c6c84b5429c59662a")
        val privateKey = hex("8057991eef8f1f1af18f4a9491d16a1ce333f695d4db8e38da75975c4478e0fb")
        val ephemeral = HpkeBase.deriveKeyPair(hex("909a9b35d3dc4713a5e72a4da274b55d3d3821a37e5d099e74a647db583a904b"))
        val enc = hex("1afa08d3dec047a643885163f1180476fa7ddb54c6a8029ea33f95796bf2ac4a")
        val plaintext = hex("4265617574792069732074727574682c20747275746820626561757479")
        val aad = hex("436f756e742d30")
        val ciphertext = hex("1c5250d8034ec2b784ba2cfd69dbdb8af406cfe3ff938e131f0def8c8b60b4db21993c62ce81883d2dd1b51a28")
        val sealed = HpkeBase.seal(publicKey, ephemeral.privateKey, info, aad, plaintext)
        assertArrayEquals(enc, sealed.first)
        assertArrayEquals(ciphertext, sealed.second)
        assertArrayEquals(plaintext, HpkeBase.open(privateKey, publicKey, enc, info, aad, ciphertext))
    }

    @Test fun decryptsMessageAndGrantProducedByActualWebProvider() {
        val fixture = fixture()
        assertArrayEquals(fixture.bytes("plaintext"), crypto.decryptMessage(fixture.bytes("groupKey"),
            fixture.bytes("messageEnvelope"), context))
        assertArrayEquals(fixture.bytes("groupKey"), crypto.decryptGroupKeyGrant(
            ChatRequestKeyPair(fixture.bytes("publicKey"), fixture.bytes("privateKey")),
            fixture.bytes("grantEnvelope"), grantContext))
        // Reusing only the fixture's synthetic salt must reproduce the exact web envelope.
        val salt = fixture.bytes("messageEnvelope").copyOfRange(0, 32)
        val deterministic = WorkspaceChatCrypto(object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) { salt.copyInto(bytes) }
        })
        assertArrayEquals(fixture.bytes("messageEnvelope"), deterministic.encryptMessage(
            fixture.bytes("groupKey"), fixture.bytes("plaintext"), context))
    }

    @Test fun exportsAndroidCiphertextForActualWebProviderWhenRequested() {
        val outputPath = System.getenv("CASSI_CRYPTO_INTEROP_OUTPUT") ?: return
        val fixture = fixture()
        val key = fixture.bytes("groupKey")
        val plaintext = fixture.bytes("plaintext")
        val encrypted = crypto.encryptMessage(key, plaintext, context)
        val grant = crypto.encryptGroupKeyGrant(key, fixture.bytes("publicKey"), grantContext)
        val result = buildJsonObject {
            fixture.forEach { (name, value) -> put(name, value) }
            put("messageEnvelope", WorkspaceChatCrypto.toBase64(encrypted))
            put("grantEnvelope", WorkspaceChatCrypto.toBase64(grant))
            put("source", "Android WorkspaceChatCrypto")
        }
        File(outputPath).writeText(result.toString())
    }

    @Test fun independentSaltsAcrossProvidersAndUnicodeRoundTrip() {
        val key = crypto.createGroupKey()
        val plaintext = "Сообщение 👋\n**Markdown**".toByteArray()
        val envelopes = (1..24).map { WorkspaceChatCrypto().encryptMessage(key, plaintext, context) }
        assertEquals(24, envelopes.map { WorkspaceChatCrypto.toBase64(it.copyOfRange(0, 32)) }.toSet().size)
        envelopes.forEach { assertArrayEquals(plaintext, WorkspaceChatCrypto().decryptMessage(key, it, context)) }
        assertArrayEquals(ByteArray(0), crypto.decryptMessage(key, crypto.encryptMessage(key, ByteArray(0), context), context))
    }

    @Test fun messageAuthenticationRejectsTamperingAndContextSubstitution() {
        val key = crypto.createGroupKey()
        val envelope = crypto.encryptMessage(key, "hello".toByteArray(), context)
        listOf(0, 4, 31, 32, envelope.lastIndex).forEach { offset ->
            val changed = envelope.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptMessage(key, changed, context) }
        }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptMessage(key, envelope.copyOf(20), context) }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptMessage(crypto.createGroupKey(), envelope, context) }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptMessage(key, envelope, context.copy(streamUuid = "other")) }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptMessage(key, envelope, context.copy(keyUuid = "other")) }
    }

    @Test fun grantsBindEveryRoutingFieldAndRejectOtherRecipients() {
        val key = crypto.createGroupKey()
        val pair = crypto.createRequestKeyPair()
        val grant = crypto.encryptGroupKeyGrant(key, pair.publicKey, grantContext)
        assertEquals(85, grant.size)
        assertArrayEquals(key, WorkspaceChatCrypto().decryptGroupKeyGrant(
            ChatRequestKeyPair(pair.publicKey, pair.privateKey), grant, grantContext))
        listOf(grantContext.copy(streamUuid = "other"), grantContext.copy(keyUuid = "other"),
            grantContext.copy(requestMessageUuid = "other"), grantContext.copy(senderDeviceId = "other"),
            grantContext.copy(recipientDeviceId = "other")).forEach {
            fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptGroupKeyGrant(pair, grant, it) }
        }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptGroupKeyGrant(crypto.createRequestKeyPair(), grant, grantContext) }
        listOf(0, 4, 5, 36, 37, grant.lastIndex).forEach { offset ->
            fails(ChatCryptoFailure.INVALID_CIPHERTEXT) {
                crypto.decryptGroupKeyGrant(pair, grant.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }, grantContext)
            }
        }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptGroupKeyGrant(pair, grant + 0, grantContext) }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { crypto.decryptMessage(key, grant, context) }
    }

    @Test fun malformedKeysContextsAndRandomFailuresAreClosed() {
        fails(ChatCryptoFailure.INVALID_KEY) { crypto.encryptMessage(ByteArray(31), ByteArray(0), context) }
        fails(ChatCryptoFailure.CONTEXT_MISMATCH) { crypto.encryptMessage(ByteArray(32), ByteArray(0), context.copy(keyUuid = "")) }
        fails(ChatCryptoFailure.INVALID_KEY) { crypto.encryptGroupKeyGrant(ByteArray(32), ByteArray(32), grantContext) }
        fails(ChatCryptoFailure.INVALID_CIPHERTEXT) { WorkspaceChatCrypto.fromBase64("[not base64]") }
        val unavailable = WorkspaceChatCrypto(object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) { throw IllegalStateException("fixture entropy unavailable") }
        })
        fails(ChatCryptoFailure.UNAVAILABLE) { unavailable.createGroupKey() }
        fails(ChatCryptoFailure.UNAVAILABLE) { unavailable.encryptMessage(ByteArray(32), ByteArray(0), context) }
    }

    private fun fixture(): JsonObject = javaClass.getResourceAsStream("/crypto/web-chat-crypto-v1.json")!!.use {
        Json.parseToJsonElement(it.bufferedReader().readText()).jsonObject
    }
    private fun JsonObject.bytes(name: String): ByteArray = WorkspaceChatCrypto.fromBase64(getValue(name).jsonPrimitive.content)
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun fails(reason: ChatCryptoFailure, block: () -> Any) {
        try { block(); fail("Expected authenticated failure") }
        catch (error: ChatCryptoException) { assertEquals(reason, error.reason) }
    }
}
