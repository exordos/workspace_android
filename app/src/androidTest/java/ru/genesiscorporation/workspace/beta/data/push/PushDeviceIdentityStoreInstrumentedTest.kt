package ru.genesiscorporation.workspace.beta.data.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            TinkPushDeviceIdentityStore.PREFERENCES_FILE,
            0,
        )
        preferences.edit().clear().commit()
        val androidKeystore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        if (androidKeystore.containsAlias(MASTER_KEY_ALIAS)) {
            androidKeystore.deleteEntry(MASTER_KEY_ALIAS)
        }

        val store = TinkPushDeviceIdentityStore(context)
        val first = store.getOrCreateIdentity()
        val second = store.getOrCreateIdentity()

        assertEquals(first, second)
        UUID.fromString(first.registrationUuid)
        UUID.fromString(first.keyUuid)
        assertEquals(43, first.publicKey.length)
        val publicKeyBytes = Base64.getUrlDecoder().decode("${first.publicKey}=")
        assertEquals(32, publicKeyBytes.size)
        assertTrue(androidKeystore.containsAlias(MASTER_KEY_ALIAS))

        val encryptedKeyset = preferences.getString(KEYSET_NAME, null)
        assertNotNull(encryptedKeyset)
        val publicKeyHex = publicKeyBytes.joinToString("") { "%02x".format(it) }
        assertFalse(checkNotNull(encryptedKeyset).contains(publicKeyHex, ignoreCase = true))
    }

    private companion object {
        const val KEYSET_NAME = "hpke_keyset"
        const val MASTER_KEY_ALIAS = "workspace_push_hpke_master_key"
    }
}
