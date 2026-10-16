package ru.genesiscorporation.workspace.beta.data.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedChatCryptoInstrumentedTest {
    @Test fun platformChaChaAndHpkeRoundTripAndAuthenticateMetadata() {
        val crypto = WorkspaceChatCrypto()
        val groupKey = crypto.createGroupKey()
        val keyPair = crypto.createRequestKeyPair()
        val context = ChatMessageContext("cassi-fixture-chat", "cassi-fixture-key")
        val grantContext = ChatGrantContext(context.streamUuid, context.keyUuid,
            "cassi-fixture-request", "cassi-fixture-sender", "cassi-fixture-recipient")
        val plaintext = "Проверка Android/Web E2EE 👋".toByteArray()
        val encrypted = crypto.encryptMessage(groupKey, plaintext, context)
        val grant = crypto.encryptGroupKeyGrant(groupKey, keyPair.publicKey, grantContext)
        assertArrayEquals(plaintext, WorkspaceChatCrypto().decryptMessage(groupKey, encrypted, context))
        assertArrayEquals(groupKey, WorkspaceChatCrypto().decryptGroupKeyGrant(keyPair, grant, grantContext))
        try {
            crypto.decryptGroupKeyGrant(keyPair, grant, grantContext.copy(recipientDeviceId = "other"))
            fail("A grant for another device must fail authentication")
        } catch (error: ChatCryptoException) { assertEquals(ChatCryptoFailure.INVALID_CIPHERTEXT, error.reason) }
    }

    @Test fun keystoreBackedSecretsSurviveReopenAndNeverAppearInPreferenceXml() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val preferencesName = "cassi_chat_crypto_test_$suffix"
        val masterAlias = "cassi_chat_crypto_test_master_$suffix"
        try {
            val store = EncryptedChatKeyStore(context, preferencesName, masterAlias)
            val owner = ChatKeyOwner("https://cassi-fixture.example.test", "cassi-fixture-project", "cassi-fixture-user")
            val identity = store.ensureDeviceIdentity(owner, "CASSI fixture Android")
            val address = ChatKeyAddress(ChatKeyScope(owner, identity.deviceId), "cassi-fixture-chat", "cassi-fixture-key")
            val key = ByteArray(32) { (it + 30).toByte() }
            val pair = WorkspaceChatCrypto().createRequestKeyPair()
            assertTrue(store.writeChatKey(address, key, forSending = true))
            assertTrue(store.writePendingRequest(address, PendingChatKeyRequest("cassi-request", null, pair, 100)))
            val historical = address.copy(keyUuid = "historical")
            assertTrue(store.writePendingRequest(historical, PendingChatKeyRequest("historical-request", "historical-message", pair, 101)))
            val reopened = EncryptedChatKeyStore(context, preferencesName, masterAlias)
            assertEquals(identity, reopened.readDeviceIdentity(owner))
            assertArrayEquals(key, reopened.readChatKey(address))
            assertArrayEquals(pair.privateKey, reopened.listPendingRequests(address).single().keyPair.privateKey)
            assertNull(reopened.readChatKey(historical))
            assertEquals(setOf(address.keyUuid, historical.keyUuid), reopened.listPendingRequestKeyUuids(address.scope, address.streamUuid).toSet())
            assertTrue(reopened.listPendingRequestKeyUuids(address.scope.copy(deviceId = "other-device"), address.streamUuid).isEmpty())
            val xml = File(context.applicationInfo.dataDir, "shared_prefs/$preferencesName.xml").readText()
            listOf(owner.serverUrl, owner.userUuid, identity.deviceId, "CASSI fixture Android",
                WorkspaceChatCrypto.toBase64(key), WorkspaceChatCrypto.toBase64(pair.privateKey)).forEach {
                assertFalse("Preference XML must contain ciphertext only", xml.contains(it))
            }
            assertTrue(reopened.removeChatKey(address))
            assertNull(store.readChatKey(address))
            assertTrue(store.listPendingRequests(address).isEmpty())
            assertEquals(listOf(historical.keyUuid), store.listPendingRequestKeyUuids(address.scope, address.streamUuid))
            assertTrue(reopened.removeChatKey(historical))
            assertTrue(store.listPendingRequestKeyUuids(address.scope, address.streamUuid).isEmpty())
        } finally {
            context.deleteSharedPreferences(preferencesName)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(masterAlias)
        }
    }
}
