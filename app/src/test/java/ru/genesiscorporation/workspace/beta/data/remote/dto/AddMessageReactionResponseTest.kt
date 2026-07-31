package ru.genesiscorporation.workspace.beta.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddMessageReactionResponseTest {
    @Test
    fun `valid response becomes exact own reaction`() {
        val validated = validateAddMessageReactionResponse(
            response = response(),
            requestedMessageUuid = MESSAGE_UUID.uppercase(),
            expectedUserUuid = USER_UUID.uppercase(),
        )

        assertEquals(REACTION_UUID, validated?.uuid)
        assertEquals(MESSAGE_UUID, validated?.messageUuid)
        assertEquals(USER_UUID, validated?.userUuid)
        assertEquals("test_tube", validated?.emojiName)
    }

    @Test
    fun `mismatched malformed and unsafe responses fail closed`() {
        assertNull(
            validateAddMessageReactionResponse(
                response = response(messageUuid = OTHER_UUID),
                requestedMessageUuid = MESSAGE_UUID,
                expectedUserUuid = USER_UUID,
            ),
        )
        assertNull(
            validateAddMessageReactionResponse(
                response = response(userUuid = OTHER_UUID),
                requestedMessageUuid = MESSAGE_UUID,
                expectedUserUuid = USER_UUID,
            ),
        )
        assertNull(
            validateAddMessageReactionResponse(
                response = response(uuid = "not-a-uuid"),
                requestedMessageUuid = MESSAGE_UUID,
                expectedUserUuid = USER_UUID,
            ),
        )
        assertNull(
            validateAddMessageReactionResponse(
                response = response(emojiName = "bad\nname"),
                requestedMessageUuid = MESSAGE_UUID,
                expectedUserUuid = USER_UUID,
            ),
        )
    }

    private fun response(
        uuid: String = REACTION_UUID,
        userUuid: String = USER_UUID,
        emojiName: String = " test_tube ",
        messageUuid: String = MESSAGE_UUID,
    ) = AddMessageReactionResponse(
        uuid = uuid,
        userUuid = userUuid,
        emojiName = emojiName,
        messageUuid = messageUuid,
    )

    private companion object {
        const val REACTION_UUID =
            "11111111-1111-4111-8111-111111111111"
        const val USER_UUID =
            "22222222-2222-4222-8222-222222222222"
        const val MESSAGE_UUID =
            "33333333-3333-4333-8333-333333333333"
        const val OTHER_UUID =
            "44444444-4444-4444-8444-444444444444"
    }
}
