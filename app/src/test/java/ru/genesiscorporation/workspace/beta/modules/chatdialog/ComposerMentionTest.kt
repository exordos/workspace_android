package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class ComposerMentionTest {
    @Test
    fun `maps valid users including profile metadata and removes duplicates`() {
        val users = listOf(
            user(
                uuid = aliceUuid.uppercase(),
                username = "alice",
                firstName = "Alice",
                email = "alice@example.com",
                status = "active",
                avatar = "urn:image:alice-avatar",
            ),
            user(aliceUuid, "alice-copy"),
            user("not-a-uuid", "invalid"),
            user(bobUuid, "bob", status = "inactive"),
        )

        val candidates = composerMentionCandidates(users)

        assertEquals(listOf(aliceUuid, bobUuid), candidates.map { it.userUuid })
        assertEquals("Alice", candidates.first().displayName)
        assertEquals("alice@example.com", candidates.first().email)
        assertEquals("active", candidates.first().status)
        assertEquals("urn:image:alice-avatar", candidates.first().avatarUrn)
    }

    @Test
    fun `orders matches by uuid username display name and email`() {
        val users = listOf(
            user(
                uuid = aliceUuid,
                username = "alpha",
                firstName = "Zed",
                email = "one@example.com",
            ),
            user(
                uuid = bobUuid,
                username = "zed-user",
                firstName = "Bob",
                email = "two@example.com",
            ),
            user(
                uuid = carolUuid,
                username = "charlie",
                firstName = "Zed Display",
                email = "three@example.com",
            ),
            user(
                uuid = daveUuid,
                username = "delta",
                firstName = "Dave",
                email = "zed@example.com",
            ),
        )
        val candidates = composerMentionCandidates(users)

        assertEquals(
            listOf(aliceUuid),
            filterComposerMentionSuggestions(candidates, aliceUuid.take(12))
                .map(ComposerMentionSuggestion::userUuid),
        )
        assertEquals(
            listOf(bobUuid, aliceUuid, carolUuid, daveUuid),
            filterComposerMentionSuggestions(candidates, "zed")
                .map(ComposerMentionSuggestion::userUuid),
        )
    }

    @Test
    fun `respects suggestion limit`() {
        val candidates = composerMentionCandidates(
            listOf(
                user(aliceUuid, "alice"),
                user(bobUuid, "bob"),
                user(carolUuid, "carol"),
            ),
        )

        assertEquals(
            listOf(aliceUuid, bobUuid),
            filterComposerMentionSuggestions(candidates, "", maxResults = 2)
                .map(ComposerMentionSuggestion::userUuid),
        )
        assertEquals(
            emptyList<ComposerMentionSuggestion>(),
            filterComposerMentionSuggestions(candidates, "", maxResults = 0),
        )
    }

    @Test
    fun `detects active query and inserts a mention at the cursor`() {
        val state = MentionTextFieldState("Hi @bo!")
        state.onValueChange(state.value.copy(selection = TextRange(6)))

        assertEquals(3 to "bo", state.activeAtQuery())
        assertTrue(
            state.insertMentionFromAtQuery(
                displayName = "Bob",
                urn = "urn:user:$bobUuid",
            ),
        )
        assertEquals("Hi [Bob](urn:user:$bobUuid) !", state.text)
        assertEquals(state.text.indexOf('!'), state.value.selection.start)
    }

    @Test
    fun `does not treat email or completed text as an active query`() {
        val state = MentionTextFieldState()

        state.setText("mail@example.com")
        assertNull(state.activeAtQuery())

        state.setText("@alice done")
        assertNull(state.activeAtQuery())

        state.setText("Hello (@")
        assertEquals(7 to "", state.activeAtQuery())
    }

    @Test
    fun `rejects insertion when there is no active query`() {
        val state = MentionTextFieldState("Hi there")

        assertFalse(
            state.insertMentionFromAtQuery(
                displayName = "Alice",
                urn = "urn:user:$aliceUuid",
            ),
        )
        assertEquals("Hi there", state.text)
    }

    private fun user(
        uuid: String,
        username: String,
        firstName: String? = username,
        email: String? = "$username@example.com",
        status: String = "active",
        avatar: String = "",
    ) = UserResponseData(
        email = email,
        firstName = firstName,
        lastName = null,
        username = username,
        uuid = uuid,
        statusEmoji = null,
        statusText = null,
        status = status,
        avatar = avatar,
    )

    private companion object {
        const val aliceUuid = "11111111-1111-4111-8111-111111111111"
        const val bobUuid = "22222222-2222-4222-8222-222222222222"
        const val carolUuid = "33333333-3333-4333-8333-333333333333"
        const val daveUuid = "44444444-4444-4444-8444-444444444444"
    }
}
