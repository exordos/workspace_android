package ru.genesiscorporation.workspace.beta.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureCredentialStoreInstrumentedTest {
    private lateinit var context: IsolatedAndroidTestContext

    @Before
    fun setUp() {
        context = IsolatedAndroidTestContext(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "credential-store",
        )
    }

    @After
    fun cleanUp() {
        TinkCredentialStore(context).clear()
        context.cleanUp()
    }

    @Test
    fun credentialsAreEncryptedAndBoundToAccountAndCredentialType() {
        val store = TinkCredentialStore(context)
        store.clear()

        store.write(ACCOUNT_A, Credential.ACCESS_TOKEN, ACCESS_TOKEN)
        store.write(ACCOUNT_A, Credential.REFRESH_TOKEN, REFRESH_TOKEN)

        assertEquals(ACCESS_TOKEN, store.read(ACCOUNT_A, Credential.ACCESS_TOKEN))
        assertEquals(REFRESH_TOKEN, store.read(ACCOUNT_A, Credential.REFRESH_TOKEN))
        assertNull(store.read(ACCOUNT_B, Credential.ACCESS_TOKEN))

        val persistedValues = context.getSharedPreferences(
            TinkCredentialStore.PREFERENCES_FILE,
            0,
        ).all.values.joinToString()
        assertFalse(persistedValues.contains(ACCESS_TOKEN))
        assertFalse(persistedValues.contains(REFRESH_TOKEN))
        assertFalse(persistedValues.contains(ACCOUNT_A))
    }

    private companion object {
        const val ACCOUNT_A = "https://workspace-a.example"
        const val ACCOUNT_B = "https://workspace-b.example"
        const val ACCESS_TOKEN = "access-token-plaintext-sentinel"
        const val REFRESH_TOKEN = "refresh-token-plaintext-sentinel"
    }
}
