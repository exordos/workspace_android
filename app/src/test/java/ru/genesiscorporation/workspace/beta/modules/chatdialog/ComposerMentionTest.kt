package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class ComposerMentionTest {
    @Test
    fun `detects the active mention at the cursor`() {
        assertEquals(
            ComposerMentionQuery(start = 3, end = 6, query = "bo"),
            detectComposerMentionQuery(
                TextFieldValue(
                    text = "Hi @bo!",
                    selection = TextRange(6),
                ),
            ),
        )
        assertEquals(
            ComposerMentionQuery(start = 7, end = 8, query = ""),
            detectComposerMentionQuery(
                TextFieldValue(
                    text = "Hello (@",
                    selection = TextRange(8),
                ),
            ),
        )
    }

    @Test
    fun `does not detect email text selection or a cursor after the query`() {
        assertNull(
            detectComposerMentionQuery(
                TextFieldValue(
                    text = "mail@example.com",
                    selection = TextRange(16),
                ),
            ),
        )
        assertNull(
            detectComposerMentionQuery(
                TextFieldValue(
                    text = "@alice done",
                    selection = TextRange(11),
                ),
            ),
        )
        assertNull(
            detectComposerMentionQuery(
                TextFieldValue(
                    text = "@alice",
                    selection = TextRange(0, 6),
                ),
            ),
        )
    }

    @Test
    fun `matches UUID username display name and email in desktop order`() {
        val users = listOf(
            user(
                uuid = ALICE_UUID,
                username = "alpha",
                firstName = "Zed",
                email = "one@example.com",
            ),
            user(
                uuid = BOB_UUID,
                username = "zed-user",
                firstName = "Bob",
                email = "two@example.com",
            ),
            user(
                uuid = CAROL_UUID,
                username = "charlie",
                firstName = "Zed Display",
                email = "three@example.com",
            ),
            user(
                uuid = DAVE_UUID,
                username = "delta",
                firstName = "Dave",
                email = "zed@example.com",
            ),
        )
        val candidates = composerMentionCandidates(users)

        assertEquals(
            listOf(ALICE_UUID),
            filterComposerMentionSuggestions(
                candidates,
                ALICE_UUID.take(12),
            )
                .map(ComposerMentionSuggestion::userUuid),
        )
        assertEquals(
            listOf(BOB_UUID, ALICE_UUID, CAROL_UUID, DAVE_UUID),
            filterComposerMentionSuggestions(candidates, "zed")
                .map(ComposerMentionSuggestion::userUuid),
        )
    }

    @Test
    fun `filters malformed duplicate and system users and respects the limit`() {
        val users = listOf(
            user(ALICE_UUID, "alice"),
            user(ALICE_UUID.uppercase(), "alice-copy"),
            user(SYSTEM_UUID, "system"),
            user("not-a-uuid", "invalid"),
            user(BOB_UUID, "bob"),
        )
        val candidates = composerMentionCandidates(users)

        assertEquals(
            listOf(ALICE_UUID, BOB_UUID),
            filterComposerMentionSuggestions(
                candidates,
                "",
                maxResults = 2,
            )
                .map(ComposerMentionSuggestion::userUuid),
        )
        assertEquals(
            emptyList<ComposerMentionSuggestion>(),
            filterComposerMentionSuggestions(
                candidates,
                "",
                maxResults = 0,
            ),
        )
    }

    @Test
    fun `inserts an escaped canonical mention and keeps the suffix`() {
        val value = TextFieldValue(
            text = "Hi @al!",
            selection = TextRange(6),
        )
        val result = insertComposerMention(
            value = value,
            query = ComposerMentionQuery(
                start = 3,
                end = 6,
                query = "al",
            ),
            suggestion = ComposerMentionSuggestion(
                userUuid = ALICE_UUID,
                displayName = "A]lice * Ops",
                username = "alice",
                email = "",
                status = "active",
            ),
        )
        val expected =
            "Hi [A\\]lice \\* Ops](urn:user:$ALICE_UUID) !"

        assertEquals(expected, result.text)
        assertEquals(expected.indexOf('!'), result.selection.start)
        assertEquals(result.selection.start, result.selection.end)
    }

    @Test
    fun `rejects a stale query or invalid suggestion without changing text`() {
        val value = TextFieldValue(
            text = "Hi @al",
            selection = TextRange(6),
        )
        val suggestion = ComposerMentionSuggestion(
            userUuid = "not-a-uuid",
            displayName = "Alice",
            username = "alice",
            email = "",
            status = "active",
        )

        assertEquals(
            value,
            insertComposerMention(
                value = value,
                query = ComposerMentionQuery(
                    start = 0,
                    end = 6,
                    query = "al",
                ),
                suggestion = suggestion.copy(userUuid = ALICE_UUID),
            ),
        )
        assertEquals(
            value,
            insertComposerMention(
                value = value,
                query = ComposerMentionQuery(
                    start = 3,
                    end = 6,
                    query = "al",
                ),
                suggestion = suggestion,
            ),
        )
    }

    private fun user(
        uuid: String,
        username: String,
        firstName: String? = username,
        email: String? = "$username@example.com",
    ) = UserResponseData(
        email = email,
        firstName = firstName,
        lastName = null,
        username = username,
        uuid = uuid,
        statusEmoji = null,
        statusText = null,
        status = "active",
        avatar = "",
    )

    private companion object {
        const val ALICE_UUID = "11111111-1111-4111-8111-111111111111"
        const val BOB_UUID = "22222222-2222-4222-8222-222222222222"
        const val CAROL_UUID = "33333333-3333-4333-8333-333333333333"
        const val DAVE_UUID = "44444444-4444-4444-8444-444444444444"
        const val SYSTEM_UUID = "00000000-0000-0000-0000-000000000000"
    }
}
