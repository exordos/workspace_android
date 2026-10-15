package ru.genesiscorporation.workspace.beta.data.crypto

import com.google.crypto.tink.subtle.X25519
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ChatMessageContext(val streamUuid: String, val keyUuid: String)

data class ChatGrantContext(
    val streamUuid: String,
    val keyUuid: String,
    val requestMessageUuid: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
)

/** Raw RFC 7748 X25519 keys, rather than X.509/PKCS#8 encodings. */
class ChatRequestKeyPair(publicKey: ByteArray, privateKey: ByteArray) {
    val publicKey: ByteArray = publicKey.copyOf()
    val privateKey: ByteArray = privateKey.copyOf()

    fun destroy() = privateKey.fill(0)
}

enum class ChatCryptoFailure { UNAVAILABLE, INVALID_KEY, INVALID_CIPHERTEXT, CONTEXT_MISMATCH }

class ChatCryptoException(val reason: ChatCryptoFailure) : GeneralSecurityException(reason.name)

/**
 * The web/desktop workspace-chat-crypto-v1 wire protocol.
 * Messages: salt[32] || ChaCha20-Poly1305(ciphertext || tag[16]).
 * Grants: "WSCG" || 0x01 || HPKE enc[32] || ciphertext[32] || tag[16].
 * Only Base64-encoded envelopes and request public keys belong in API payloads.
 */
class WorkspaceChatCrypto internal constructor(private val random: SecureRandom) {
    constructor() : this(SecureRandom())

    fun createGroupKey(): ByteArray = randomBytes(KEY_BYTES)

    fun createRequestKeyPair(): ChatRequestKeyPair {
        val privateKey = randomBytes(KEY_BYTES)
        try {
            return ChatRequestKeyPair(X25519.publicFromPrivate(privateKey), privateKey)
        } catch (_: GeneralSecurityException) {
            throw ChatCryptoException(ChatCryptoFailure.UNAVAILABLE)
        } finally {
            privateKey.fill(0)
        }
    }

    fun encryptMessage(groupKey: ByteArray, plaintext: ByteArray, context: ChatMessageContext): ByteArray {
        validKey(groupKey)
        val info = messageInfo(context)
        val salt = randomBytes(KEY_BYTES)
        val messageKey = HkdfSha256.derive(groupKey, salt, info, KEY_BYTES)
        return try {
            salt + ChaChaAead.encrypt(messageKey, ByteArray(12), plaintext, info + salt)
        } catch (_: GeneralSecurityException) {
            throw ChatCryptoException(ChatCryptoFailure.UNAVAILABLE)
        } finally {
            messageKey.fill(0)
        }
    }

    fun decryptMessage(groupKey: ByteArray, envelope: ByteArray, context: ChatMessageContext): ByteArray {
        validKey(groupKey)
        val info = messageInfo(context)
        if (envelope.size < KEY_BYTES + TAG_BYTES) invalidCiphertext()
        val salt = envelope.copyOfRange(0, KEY_BYTES)
        val messageKey = HkdfSha256.derive(groupKey, salt, info, KEY_BYTES)
        return try {
            ChaChaAead.decrypt(messageKey, ByteArray(12), envelope.copyOfRange(KEY_BYTES, envelope.size), info + salt)
        } catch (_: GeneralSecurityException) {
            invalidCiphertext()
        } finally {
            messageKey.fill(0)
        }
    }

    fun encryptGroupKeyGrant(
        groupKey: ByteArray,
        recipientPublicKey: ByteArray,
        context: ChatGrantContext,
    ): ByteArray {
        validKey(groupKey)
        validKey(recipientPublicKey)
        val info = grantInfo(context)
        val ephemeralPrivateKey = randomBytes(KEY_BYTES)
        return try {
            val (enc, ciphertext) = HpkeBase.seal(
                recipientPublicKey, ephemeralPrivateKey, info,
                info + GRANT_MAGIC + recipientPublicKey, groupKey,
            )
            GRANT_MAGIC + enc + ciphertext
        } catch (_: GeneralSecurityException) {
            throw ChatCryptoException(ChatCryptoFailure.INVALID_KEY)
        } finally {
            ephemeralPrivateKey.fill(0)
        }
    }

    fun decryptGroupKeyGrant(
        requestKeyPair: ChatRequestKeyPair,
        envelope: ByteArray,
        context: ChatGrantContext,
    ): ByteArray {
        validKey(requestKeyPair.privateKey)
        validKey(requestKeyPair.publicKey)
        val info = grantInfo(context)
        if (envelope.size != GRANT_MAGIC.size + KEY_BYTES * 2 + TAG_BYTES ||
            !MessageDigest.isEqual(envelope.copyOfRange(0, GRANT_MAGIC.size), GRANT_MAGIC)
        ) invalidCiphertext()
        return try {
            if (!MessageDigest.isEqual(X25519.publicFromPrivate(requestKeyPair.privateKey), requestKeyPair.publicKey)) {
                throw GeneralSecurityException("Invalid request key pair")
            }
            HpkeBase.open(
                requestKeyPair.privateKey, requestKeyPair.publicKey,
                envelope.copyOfRange(GRANT_MAGIC.size, GRANT_MAGIC.size + KEY_BYTES), info,
                info + GRANT_MAGIC + requestKeyPair.publicKey,
                envelope.copyOfRange(GRANT_MAGIC.size + KEY_BYTES, envelope.size),
            )
        } catch (_: GeneralSecurityException) {
            invalidCiphertext()
        }
    }

    private fun randomBytes(size: Int): ByteArray = try {
        ByteArray(size).also(random::nextBytes)
    } catch (_: RuntimeException) {
        throw ChatCryptoException(ChatCryptoFailure.UNAVAILABLE)
    }

    companion object {
        private const val KEY_BYTES = 32
        private const val TAG_BYTES = 16
        private val GRANT_MAGIC = byteArrayOf(87, 83, 67, 71, 1)

        fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun fromBase64(value: String): ByteArray = try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            invalidCiphertext()
        }

        internal fun messageInfo(context: ChatMessageContext): ByteArray {
            if (context.streamUuid.isEmpty() || context.keyUuid.isEmpty()) invalidContext()
            return contextJson(listOf("workspace-chat-crypto-v1", "message", context.streamUuid, context.keyUuid))
        }

        internal fun grantInfo(context: ChatGrantContext): ByteArray {
            if (listOf(context.streamUuid, context.keyUuid, context.requestMessageUuid,
                    context.senderDeviceId, context.recipientDeviceId).any(String::isEmpty)) invalidContext()
            return contextJson(listOf("workspace-chat-crypto-v1", "grant", context.streamUuid,
                context.keyUuid, context.requestMessageUuid, context.senderDeviceId, context.recipientDeviceId))
        }

        private fun contextJson(parts: List<String>): ByteArray =
            Json.encodeToString(ListSerializer(String.serializer()), parts).toByteArray(Charsets.UTF_8)

        private fun validKey(bytes: ByteArray) {
            if (bytes.size != KEY_BYTES) throw ChatCryptoException(ChatCryptoFailure.INVALID_KEY)
        }

        private fun invalidContext(): Nothing = throw ChatCryptoException(ChatCryptoFailure.CONTEXT_MISMATCH)
        private fun invalidCiphertext(): Nothing = throw ChatCryptoException(ChatCryptoFailure.INVALID_CIPHERTEXT)
    }
}

/** The platform's RFC 8439 primitive is available throughout the supported API 30+ range. */
internal object ChaChaAead {
    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(ciphertext)

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("ChaCha20-Poly1305").apply {
            init(mode, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
            updateAAD(aad)
        }
}

internal object HkdfSha256 {
    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32))
        val result = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val next = hmac(prk, previous + info + counter.toByte())
            previous.fill(0)
            previous = next
            val count = minOf(previous.size, length - offset)
            previous.copyInto(result, offset, 0, count)
            offset += count
            counter++
        }
        previous.fill(0)
        return result
    }

    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return try { expand(prk, info, length) } finally { prk.fill(0) }
    }

    private fun hmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value)
    }
}

/** RFC 9180 base mode, DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, ChaCha20Poly1305. */
internal object HpkeBase {
    private val version = "HPKE-v1".toByteArray(Charsets.US_ASCII)
    private val kemSuite = "KEM".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0x20)
    private val hpkeSuite = "HPKE".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0x20, 0, 1, 0, 3)

    fun seal(recipientPublic: ByteArray, ephemeralPrivate: ByteArray, info: ByteArray,
             aad: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val enc = X25519.publicFromPrivate(ephemeralPrivate)
        val sharedSecret = sharedSecret(ephemeralPrivate, recipientPublic, enc, recipientPublic)
        return try {
            enc to crypt(sharedSecret, info) { key, nonce -> ChaChaAead.encrypt(key, nonce, plaintext, aad) }
        } finally { sharedSecret.fill(0) }
    }

    fun open(recipientPrivate: ByteArray, recipientPublic: ByteArray, enc: ByteArray,
             info: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val sharedSecret = sharedSecret(recipientPrivate, enc, enc, recipientPublic)
        return try {
            crypt(sharedSecret, info) { key, nonce -> ChaChaAead.decrypt(key, nonce, ciphertext, aad) }
        } finally { sharedSecret.fill(0) }
    }

    internal fun deriveKeyPair(ikm: ByteArray): ChatRequestKeyPair {
        val prk = labeledExtract(kemSuite, ByteArray(0), "dkp_prk", ikm)
        val privateKey = try { labeledExpand(kemSuite, prk, "sk", ByteArray(0), 32) }
            finally { prk.fill(0) }
        return try { ChatRequestKeyPair(X25519.publicFromPrivate(privateKey), privateKey) }
            finally { privateKey.fill(0) }
    }

    private fun sharedSecret(privateKey: ByteArray, publicKey: ByteArray,
                             enc: ByteArray, recipientPublic: ByteArray): ByteArray {
        val dh = X25519.computeSharedSecret(privateKey, publicKey)
        val prk = try { labeledExtract(kemSuite, ByteArray(0), "eae_prk", dh) } finally { dh.fill(0) }
        return try { labeledExpand(kemSuite, prk, "shared_secret", enc + recipientPublic, 32) }
            finally { prk.fill(0) }
    }

    private inline fun crypt(sharedSecret: ByteArray, info: ByteArray,
                             operation: (ByteArray, ByteArray) -> ByteArray): ByteArray {
        val empty = ByteArray(0)
        val context = byteArrayOf(0) + labeledExtract(hpkeSuite, empty, "psk_id_hash", empty) +
            labeledExtract(hpkeSuite, empty, "info_hash", info)
        val secret = labeledExtract(hpkeSuite, sharedSecret, "secret", empty)
        var key: ByteArray? = null
        var nonce: ByteArray? = null
        return try {
            key = labeledExpand(hpkeSuite, secret, "key", context, 32)
            nonce = labeledExpand(hpkeSuite, secret, "base_nonce", context, 12)
            operation(key, nonce)
        } finally {
            secret.fill(0)
            key?.fill(0)
            nonce?.fill(0)
        }
    }

    private fun labeledExtract(suite: ByteArray, salt: ByteArray, label: String, ikm: ByteArray): ByteArray =
        HkdfSha256.extract(salt, version + suite + label.toByteArray(Charsets.US_ASCII) + ikm)

    private fun labeledExpand(suite: ByteArray, prk: ByteArray, label: String,
                              info: ByteArray, length: Int): ByteArray =
        HkdfSha256.expand(prk, byteArrayOf((length ushr 8).toByte(), length.toByte()) +
            version + suite + label.toByteArray(Charsets.US_ASCII) + info, length)
}
