package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.PersistedWorkspaceReplySession
import ru.genesiscorporation.workspace.beta.data.PersistedWorkspaceReplyTab
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class WorkspaceReplyTest {
    @Test
    fun `reply replaces active quote while preserving its answer and identity`() {
        val first = tab(
            id = "first",
            messageUuid = MESSAGE_A,
            answer = "answer A",
        )
        val second = tab(id = "second", messageUuid = MESSAGE_B)
        val session = WorkspaceReplySession(
            tabs = listOf(first, second),
            activeTabId = first.id,
        )

        val replaced = replyToWorkspaceMessage(
            session,
            tab(id = "unused", messageUuid = MESSAGE_C),
        )

        assertEquals(listOf(MESSAGE_C, MESSAGE_B), replaced.tabs.map {
            it.messageUuid
        })
        assertEquals("answer A", replaced.tabs.first().answer)
        assertEquals("first", replaced.tabs.first().id)
        assertEquals("first", replaced.activeTabId)
    }

    @Test
    fun `answers stay isolated across selection and removal`() {
        val first = tab(id = "first", messageUuid = MESSAGE_A)
        val second = tab(id = "second", messageUuid = MESSAGE_B)
        var session = addWorkspaceReplyTab(
            replyToWorkspaceMessage(WorkspaceReplySession(), first),
            second,
        )
        session = selectWorkspaceReplyTab(session, first.id)
        session = setWorkspaceReplyAnswer(session, "answer A")
        session = selectWorkspaceReplyTab(session, second.id)
        session = setWorkspaceReplyAnswer(session, "answer B")
        session = removeWorkspaceReplyTab(session, second.id)

        assertEquals("first", session.activeTabId)
        assertEquals("answer A", session.activeTab?.answer)
    }

    @Test
    fun `markdown follows tab order and desktop quote urn contract`() {
        val session = WorkspaceReplySession(
            tabs = listOf(
                tab(
                    id = "second",
                    messageUuid = MESSAGE_B,
                    senderName = "Bob [QA]",
                    selectedText = " mobile + desktop ",
                    answer = "answer B",
                ),
                tab(
                    id = "first",
                    messageUuid = MESSAGE_A,
                    senderName = "Alice",
                    answer = " answer A ",
                ),
            ),
            activeTabId = "first",
        )

        assertEquals(
            "[Bob \\[QA\\]]" +
                "(urn:quote:$MESSAGE_B?text=%20mobile%20%2B%20desktop%20)\n\n" +
                "answer B\n\n" +
                "[Alice](urn:quote:$MESSAGE_A)\n\nanswer A",
            buildWorkspaceReplyMarkdown(session),
        )
    }

    @Test
    fun `quote only tabs are preserved but do not count as an answer`() {
        val session = replyToWorkspaceMessage(
            WorkspaceReplySession(),
            tab(id = "first", messageUuid = MESSAGE_A),
        )

        assertTrue(!session.hasAnswer)
        assertEquals(
            "[Alice](urn:quote:$MESSAGE_A)",
            buildWorkspaceReplyMarkdown(session),
        )
        assertNull(buildWorkspaceReplyMarkdown(WorkspaceReplySession()))
    }

    @Test
    fun `persistence rejects invalid duplicate and oversized tabs`() {
        val valid = PersistedWorkspaceReplyTab(
            id = "valid",
            messageUuid = MESSAGE_A,
            senderUuid = SENDER,
            senderName = "Alice",
            quotedContent = "quoted",
            createdAt = CREATED_AT,
            answer = "answer",
        )
        val restored = PersistedWorkspaceReplySession(
            tabs = listOf(
                valid,
                valid.copy(messageUuid = MESSAGE_B),
                valid.copy(id = "bad", messageUuid = "not-a-uuid"),
            ) + List(MAX_WORKSPACE_REPLY_TABS + 5) { index ->
                valid.copy(id = "extra-$index", messageUuid = MESSAGE_B)
            },
            activeTabId = "missing",
        ).toWorkspaceReplySession()

        assertEquals(MAX_WORKSPACE_REPLY_TABS, restored.tabs.size)
        assertEquals("valid", restored.activeTabId)
        assertEquals("answer", restored.activeTab?.answer)
    }

    @Test
    fun `session bounds aggregate answers and selected fragments`() {
        val selected = "s".repeat(30_000)
        val normalized = normalizeWorkspaceReplySession(
            WorkspaceReplySession(
                tabs = listOf(
                    tab(
                        id = "first",
                        messageUuid = MESSAGE_A,
                        selectedText = selected,
                        answer = "a".repeat(20_000),
                    ),
                    tab(
                        id = "second",
                        messageUuid = MESSAGE_B,
                        answer = "b",
                    ),
                ),
                activeTabId = "first",
            ),
        )

        assertEquals(MAX_WORKSPACE_REPLY_INPUT_CHARS, workspaceReplyInputChars(normalized))
        assertEquals(10_000, normalized.tabs.first().answer.length)
        assertEquals(1, normalized.tabs.size)
    }

    @Test
    fun `adding a tab cannot exceed aggregate reply input limit`() {
        val existing = WorkspaceReplySession(
            tabs = listOf(
                tab(
                    id = "first",
                    messageUuid = MESSAGE_A,
                    answer = "a".repeat(MAX_WORKSPACE_REPLY_INPUT_CHARS),
                ),
            ),
            activeTabId = "first",
        )

        val result = addWorkspaceReplyTab(
            existing,
            tab(
                id = "second",
                messageUuid = MESSAGE_B,
                selectedText = "fragment",
            ),
        )

        assertEquals(normalizeWorkspaceReplySession(existing), result)
    }

    @Test
    fun `move clamps at edges and retains active tab`() {
        val session = WorkspaceReplySession(
            tabs = listOf(
                tab(id = "a", messageUuid = MESSAGE_A),
                tab(id = "b", messageUuid = MESSAGE_B),
                tab(id = "c", messageUuid = MESSAGE_C),
            ),
            activeTabId = "b",
        )

        val moved = moveWorkspaceReplyTab(session, "b", 1)
        val unchanged = moveWorkspaceReplyTab(moved, "b", 1)

        assertEquals(listOf("a", "c", "b"), moved.tabs.map { it.id })
        assertEquals("b", moved.activeTabId)
        assertEquals(moved, unchanged)
    }

    @Test
    fun `structured edit restores answers and canonical selected text`() {
        val markdown =
            "[Alice](urn:quote:$MESSAGE_A?text=%20fragment%20)\n\n" +
                "answer A\n\n" +
                "[Bob](urn:quote:$MESSAGE_B)\n\nanswer B"
        val messages = mapOf(
            MESSAGE_A to message(MESSAGE_A, SENDER, "Alice", "source A"),
            MESSAGE_B to message(MESSAGE_B, SENDER_B, "Bob", "source B"),
        )

        val restored = restoreWorkspaceReplySessionFromMarkdown(
            markdown = markdown,
            resolveMessage = messages::get,
            createIdentity = { index ->
                "restored-$index" to "2026-07-31T12:0${index}:00Z"
            },
        )

        assertEquals("answer A", restored?.activeAnswer)
        assertEquals(listOf("answer A", "answer B"), restored?.session?.tabs?.map {
            it.answer
        })
        assertEquals(" fragment ", restored?.session?.tabs?.first()?.selectedText)
        assertEquals(markdown, restored?.session?.let(::buildWorkspaceReplyMarkdown))
    }

    @Test
    fun `structured edit remains raw when a source is unavailable`() {
        val restored = restoreWorkspaceReplySessionFromMarkdown(
            markdown = "[Alice](urn:quote:$MESSAGE_A)\n\nanswer",
            resolveMessage = { null },
            createIdentity = { "restored" to CREATED_AT },
        )

        assertNull(restored)
    }

    @Test
    fun `structured reply source ids are discovered without loading messages`() {
        val markdown =
            "[Alice](urn:quote:$MESSAGE_A)\n\nanswer A\n\n" +
                "[Bob](urn:quote:$MESSAGE_B)\n\nanswer B"

        assertEquals(
            listOf(MESSAGE_A, MESSAGE_B),
            workspaceReplyMessageUuidsFromMarkdown(markdown),
        )
        assertNull(workspaceReplyMessageUuidsFromMarkdown("ordinary text"))
    }

    @Test
    fun `selection text renders readable markdown labels without destinations`() {
        assertEquals(
            "Alice\n\nBold and code",
            workspaceMarkdownPlainText(
                "[Alice](urn:quote:$MESSAGE_A)\n\n**Bold** and `code`",
            ),
        )
    }

    @Test
    fun `fragment selection preserves exact whitespace and rejects empty ranges`() {
        assertEquals(
            " beta ",
            selectedReplyFragment(
                TextFieldValue(
                    text = "alpha beta gamma",
                    selection = TextRange(5, 11),
                ),
            ),
        )
        assertNull(
            selectedReplyFragment(
                TextFieldValue(
                    text = "alpha",
                    selection = TextRange(2, 2),
                ),
            ),
        )
        assertNull(
            selectedReplyFragment(
                TextFieldValue(
                    text = "a   b",
                    selection = TextRange(1, 4),
                ),
            ),
        )
    }

    private fun tab(
        id: String,
        messageUuid: String,
        senderName: String = "Alice",
        selectedText: String? = null,
        answer: String = "",
    ) = WorkspaceReplyTab(
        id = id,
        messageUuid = messageUuid,
        senderUuid = SENDER,
        senderName = senderName,
        quotedContent = "quoted",
        selectedText = selectedText,
        createdAt = CREATED_AT,
        answer = answer,
    )

    private fun message(
        uuid: String,
        senderUuid: String,
        senderName: String,
        content: String,
    ) = MessageResponse(
        uuid = uuid,
        updatedAt = CREATED_AT,
        createdAt = CREATED_AT,
        streamUuid = STREAM,
        topicUuid = TOPIC,
        userUuid = senderUuid,
        authorUuid = senderUuid,
        payload = MessageResponsePayload("markdown", content),
        isOwn = false,
        reactions = emptyMap(),
        user = UserResponseData(
            email = null,
            firstName = senderName,
            lastName = null,
            username = senderName.lowercase(),
            uuid = senderUuid,
            status = "active",
            avatar = "",
        ),
    )

    private companion object {
        const val MESSAGE_A = "00000000-0000-4000-8000-000000000001"
        const val MESSAGE_B = "00000000-0000-4000-8000-000000000002"
        const val MESSAGE_C = "00000000-0000-4000-8000-000000000003"
        const val SENDER = "00000000-0000-4000-8000-000000000010"
        const val SENDER_B = "00000000-0000-4000-8000-000000000011"
        const val STREAM = "00000000-0000-4000-8000-000000000020"
        const val TOPIC = "00000000-0000-4000-8000-000000000021"
        const val CREATED_AT = "2026-07-31T12:00:00Z"
    }
}
