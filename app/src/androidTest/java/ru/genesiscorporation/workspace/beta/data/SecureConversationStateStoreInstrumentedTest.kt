package ru.genesiscorporation.workspace.beta.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureConversationStateStoreInstrumentedTest {
    private lateinit var context: IsolatedAndroidTestContext

    @Before
    fun setUp() {
        context = IsolatedAndroidTestContext(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "conversation-state",
        )
    }

    @After
    fun cleanUp() {
        context.cleanUp()
    }

    @Test
    fun draftsAndOutboxAreEncryptedAndAccountScoped() = runBlocking {
        val store = TinkConversationStateStore(context)
        val state = PersistedConversationState(
            route = PersistedConversationRoute(
                streamUuid = STREAM,
                topicUuid = TOPIC,
                chatTitle = ROUTE_SENTINEL,
                topicName = "Sandbox",
                isDirectMessages = false,
            ),
            draftText = DRAFT_SENTINEL,
            outbox = listOf(
                PersistedOutboxEntry(
                    localMessageUuid = "local-message",
                    streamUuid = STREAM,
                    topicUuid = TOPIC,
                    content = OUTBOX_SENTINEL,
                    createdAt = "2026-07-30T10:00:00Z",
                    status = PersistedOutboxStatus.UNCERTAIN,
                ),
            ),
            pendingReadBoundary = PersistedReadBoundary(
                messageUuid = READ_BOUNDARY_SENTINEL,
                createdAt = "2026-07-30T10:00:00Z",
            ),
        )

        store.write(ACCOUNT_A, STREAM, TOPIC, state)

        assertEquals(state, store.read(ACCOUNT_A, STREAM, TOPIC))
        assertEquals(listOf(state), store.list(ACCOUNT_A))
        assertTrue(store.list(ACCOUNT_B).isEmpty())
        assertNull(store.read(ACCOUNT_B, STREAM, TOPIC))
        assertNull(store.read(ACCOUNT_A, STREAM, "other-topic"))

        val persistedValues = context.getSharedPreferences(
            TinkConversationStateStore.PREFERENCES_FILE,
            0,
        ).all.values.joinToString()
        assertFalse(persistedValues.contains(DRAFT_SENTINEL))
        assertFalse(persistedValues.contains(OUTBOX_SENTINEL))
        assertFalse(persistedValues.contains(ROUTE_SENTINEL))
        assertFalse(persistedValues.contains(READ_BOUNDARY_SENTINEL))
        assertFalse(persistedValues.contains(ACCOUNT_A))

        store.clearAccount(ACCOUNT_A)
        assertNull(store.read(ACCOUNT_A, STREAM, TOPIC))
        assertTrue(store.list(ACCOUNT_A).isEmpty())
    }

    @Test
    fun indexTracksOverwriteAndRemovalWithoutLeakingRoutes() = runBlocking {
        val store = TinkConversationStateStore(context)
        val first = PersistedConversationState(
            route = PersistedConversationRoute(
                streamUuid = STREAM,
                topicUuid = TOPIC,
                chatTitle = "First",
                topicName = "One",
                isDirectMessages = false,
            ),
            draftText = "first",
        )
        val second = PersistedConversationState(
            route = PersistedConversationRoute(
                streamUuid = STREAM,
                topicUuid = "topic-two",
                chatTitle = "Second",
                topicName = "Two",
                isDirectMessages = false,
            ),
            draftText = "second",
        )

        store.write(ACCOUNT_A, STREAM, TOPIC, first)
        store.write(ACCOUNT_A, STREAM, TOPIC, first.copy(draftText = "updated"))
        store.write(ACCOUNT_A, STREAM, "topic-two", second)

        assertEquals(
            listOf("updated", "second"),
            store.list(ACCOUNT_A).map(PersistedConversationState::draftText),
        )
        store.remove(ACCOUNT_A, STREAM, TOPIC)
        assertEquals(
            listOf("second"),
            store.list(ACCOUNT_A).map(PersistedConversationState::draftText),
        )

        val persistedValues = context.getSharedPreferences(
            TinkConversationStateStore.PREFERENCES_FILE,
            0,
        ).all.values.joinToString()
        assertFalse(persistedValues.contains("topic-two"))
        assertFalse(persistedValues.contains("updated"))
    }

    @Test
    fun corruptedStateIsReportedInsteadOfSilentlyDiscarded() = runBlocking {
        val store = TinkConversationStateStore(context)
        store.write(
            ACCOUNT_A,
            STREAM,
            TOPIC,
            PersistedConversationState(draftText = DRAFT_SENTINEL),
        )
        val preferences = context.getSharedPreferences(
            TinkConversationStateStore.PREFERENCES_FILE,
            0,
        )
        val stateKeys = preferences.all.keys
            .filter { it != "message_state_keyset" }
        val editor = preferences.edit()
        stateKeys.forEach { editor.putString(it, "not-valid-ciphertext") }
        assertTrue(editor.commit())

        assertTrue(
            runCatching {
                store.read(ACCOUNT_A, STREAM, TOPIC)
            }.isFailure,
        )
    }

    @Test
    fun sameConversationKeepsIndependentDraftSlots() = runBlocking {
        val store = TinkConversationStateStore(context)
        val route = PersistedConversationRoute(
            streamUuid = STREAM,
            topicUuid = TOPIC,
            chatTitle = "Draft slots",
            topicName = "Sandbox",
            isDirectMessages = false,
        )
        val base = PersistedConversationState(
            route = route,
            draftText = "base draft",
        )
        val first = PersistedConversationState(
            route = route,
            draftStorageSlot = DRAFT_SLOT_A,
            draftText = "first server draft",
        )
        val second = PersistedConversationState(
            route = route,
            draftStorageSlot = DRAFT_SLOT_B,
            draftText = "second server draft",
        )

        store.write(ACCOUNT_A, STREAM, TOPIC, base)
        store.write(
            ACCOUNT_A,
            STREAM,
            TOPIC,
            first,
            DRAFT_SLOT_A,
        )
        store.write(
            ACCOUNT_A,
            STREAM,
            TOPIC,
            second,
            DRAFT_SLOT_B,
        )

        assertEquals(base, store.read(ACCOUNT_A, STREAM, TOPIC))
        assertEquals(
            first,
            store.read(ACCOUNT_A, STREAM, TOPIC, DRAFT_SLOT_A),
        )
        assertEquals(
            second,
            store.read(ACCOUNT_A, STREAM, TOPIC, DRAFT_SLOT_B),
        )
        assertEquals(
            setOf("base draft", "first server draft", "second server draft"),
            store.list(ACCOUNT_A).map(PersistedConversationState::draftText)
                .toSet(),
        )

        store.remove(ACCOUNT_A, STREAM, TOPIC, DRAFT_SLOT_A)

        assertNull(store.read(ACCOUNT_A, STREAM, TOPIC, DRAFT_SLOT_A))
        assertEquals(base, store.read(ACCOUNT_A, STREAM, TOPIC))
        assertEquals(
            second,
            store.read(ACCOUNT_A, STREAM, TOPIC, DRAFT_SLOT_B),
        )
    }

    private companion object {
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        const val STREAM = "stream"
        const val TOPIC = "topic"
        const val DRAFT_SENTINEL = "draft-plaintext-sentinel"
        const val OUTBOX_SENTINEL = "outbox-plaintext-sentinel"
        const val ROUTE_SENTINEL = "route-plaintext-sentinel"
        const val READ_BOUNDARY_SENTINEL =
            "33333333-3333-4333-8333-333333333333"
        const val DRAFT_SLOT_A = "11111111-1111-4111-8111-111111111111"
        const val DRAFT_SLOT_B = "22222222-2222-4222-8222-222222222222"
    }
}
