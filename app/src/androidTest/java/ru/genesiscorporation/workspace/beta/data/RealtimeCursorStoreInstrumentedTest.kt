package ru.genesiscorporation.workspace.beta.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeCursorStoreInstrumentedTest {
    private lateinit var context: IsolatedAndroidTestContext

    @Before
    fun setUp() {
        context = IsolatedAndroidTestContext(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "realtime-cursor",
        )
    }

    @After
    fun cleanUp() {
        context.cleanUp()
    }

    @Test
    fun cursorIsEncryptedAccountScopedAndSelectivelyCleared() = runBlocking {
        val store = TinkRealtimeCursorStore(context)
        val first = PersistedRealtimeCursor(
            epochVersion = 42,
            epochGeneration = GENERATION_SENTINEL,
        )
        val second = PersistedRealtimeCursor(
            epochVersion = 99,
            epochGeneration = "other-generation",
        )

        store.write(ACCOUNT_A, first)
        store.write(ACCOUNT_B, second)

        assertEquals(first, store.read(ACCOUNT_A))
        assertEquals(second, store.read(ACCOUNT_B))

        val persistedValues = context.getSharedPreferences(
            TinkRealtimeCursorStore.PREFERENCES_FILE,
            0,
        ).all.values.joinToString()
        assertFalse(persistedValues.contains(GENERATION_SENTINEL))
        assertFalse(persistedValues.contains(ACCOUNT_A))
        assertFalse(persistedValues.contains(ACCOUNT_B))

        store.clearAccount(ACCOUNT_A)

        assertNull(store.read(ACCOUNT_A))
        assertEquals(second, store.read(ACCOUNT_B))
    }

    private companion object {
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        const val GENERATION_SENTINEL =
            "generation-that-must-not-appear-in-plaintext"
    }
}
