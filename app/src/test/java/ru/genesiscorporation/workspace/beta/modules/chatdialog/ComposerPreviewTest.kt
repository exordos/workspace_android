package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerPreviewTest {
    @Test
    fun `plain composer preview preserves exact draft markdown`() {
        assertEquals(
            "**Hello**\n\n- one",
            buildComposerPreviewMarkdown(
                messageText = "**Hello**\n\n- one",
                replySession = WorkspaceReplySession(),
            ),
        )
    }

    @Test
    fun `reply composer preview renders every ordered reply section`() {
        val session = WorkspaceReplySession(
            tabs = listOf(
                tab(
                    id = "first",
                    messageUuid = FIRST_MESSAGE_UUID,
                    senderName = "Alice",
                    answer = "First answer",
                ),
                tab(
                    id = "second",
                    messageUuid = SECOND_MESSAGE_UUID,
                    senderName = "Bob",
                    answer = "Second answer",
                ),
            ),
            activeTabId = "second",
        )

        assertEquals(
            "[Alice](urn:quote:$FIRST_MESSAGE_UUID)\n\nFirst answer\n\n" +
                "[Bob](urn:quote:$SECOND_MESSAGE_UUID)\n\nSecond answer",
            buildComposerPreviewMarkdown(
                messageText = "Only the active answer",
                replySession = session,
            ),
        )
    }

    private fun tab(
        id: String,
        messageUuid: String,
        senderName: String,
        answer: String,
    ) = WorkspaceReplyTab(
        id = id,
        messageUuid = messageUuid,
        senderUuid = SENDER_UUID,
        senderName = senderName,
        quotedContent = "Source",
        createdAt = "2026-07-31T10:00:00Z",
        answer = answer,
    )

    private companion object {
        const val FIRST_MESSAGE_UUID =
            "11111111-1111-4111-8111-111111111111"
        const val SECOND_MESSAGE_UUID =
            "22222222-2222-4222-8222-222222222222"
        const val SENDER_UUID =
            "33333333-3333-4333-8333-333333333333"
    }
}
