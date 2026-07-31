package ru.genesiscorporation.workspace.beta.data.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.Base64
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PushDeviceIdentityStoreInstrumentedTest {
    @Test
    fun hpkeIdentityIsStableAndWrappedByAndroidKeystore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            "${TinkPushDeviceIdentityStore.PREFERENCES_FILE}$TEST_NAMESPACE",
            0,
        )
        preferences.edit().clear().commit()
        val androidKeystore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        if (androidKeystore.containsAlias(MASTER_KEY_ALIAS)) {
            androidKeystore.deleteEntry(MASTER_KEY_ALIAS)
        }

        val store = TinkPushDeviceIdentityStore(context, TEST_NAMESPACE)
        val first = store.getOrCreateIdentity("account-a")
        val second = store.getOrCreateIdentity("account-a")
        val otherAccount = store.getOrCreateIdentity("account-b")

        assertEquals(first, second)
        assertNotEquals(first.registrationUuid, otherAccount.registrationUuid)
        assertEquals(first.keyUuid, otherAccount.keyUuid)
        assertEquals(first.publicKey, otherAccount.publicKey)
        UUID.fromString(first.registrationUuid)
        UUID.fromString(otherAccount.registrationUuid)
        UUID.fromString(first.keyUuid)
        assertEquals(43, first.publicKey.length)
        val publicKeyBytes = Base64.getUrlDecoder().decode("${first.publicKey}=")
        assertEquals(32, publicKeyBytes.size)
        assertTrue(androidKeystore.containsAlias(MASTER_KEY_ALIAS))

        val encryptedKeyset = preferences.getString(KEYSET_NAME, null)
        assertNotNull(encryptedKeyset)
        val publicKeyHex = publicKeyBytes.joinToString("") { "%02x".format(it) }
        assertFalse(checkNotNull(encryptedKeyset).contains(publicKeyHex, ignoreCase = true))
        assertFalse(preferences.all.keys.any { "account-a" in it })
        assertFalse(preferences.all.keys.any { "account-b" in it })

        val legacyUuid = UUID.randomUUID().toString()
        preferences.edit().putString("registration_uuid", legacyUuid).commit()
        assertEquals(legacyUuid, store.legacyRegistrationUuid())

        preferences.edit().clear().commit()
        if (androidKeystore.containsAlias(MASTER_KEY_ALIAS)) {
            androidKeystore.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    private companion object {
        const val TEST_NAMESPACE = "_instrumented_test"
        const val KEYSET_NAME = "hpke_keyset$TEST_NAMESPACE"
        const val MASTER_KEY_ALIAS =
            "workspace_push_hpke_master_key$TEST_NAMESPACE"
    }
}
