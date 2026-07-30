package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class MessageForwardingTest {
    @Test
    fun `forward markdown matches the desktop quote reference contract`() {
        val message = message().apply {
            user = user(
                uuid = AUTHOR_UUID,
                username = "alice",
                firstName = "Alice [Admin]",
            )
        }

        assertEquals(
            "[Alice \\[Admin\\]](urn:quote:$MESSAGE_UUID)",
            buildWorkspaceForwardMarkdown(message),
        )
    }

    @Test
    fun `non canonical and local messages cannot be forwarded`() {
        assertFalse(canForwardMessage(message().copy(uuid = "local-message")))
        assertFalse(canForwardMessage(message().copy(uuid = "not-a-uuid")))
        assertNull(buildWorkspaceForwardMarkdown(message().copy(uuid = "not-a-uuid")))
    }

    @Test
    fun `quote urn round trips selected unicode without plus ambiguity`() {
        val urn = buildWorkspaceQuoteUrn(
            MESSAGE_UUID,
            "  Привет + (mobile)!  ",
        )

        assertEquals(
            MESSAGE_UUID to "  Привет + (mobile)!  ",
            parseWorkspaceQuoteUrn(urn.orEmpty()),
        )
        assertTrue(urn.orEmpty().contains("%20"))
        assertTrue(urn.orEmpty().contains("%2B"))
        assertTrue(urn.orEmpty().contains("%28"))
        assertTrue(urn.orEmpty().contains("%21"))
    }

    @Test
    fun `quote parser rejects extra query parameters and malformed escapes`() {
        assertNull(parseWorkspaceQuoteUrn("urn:quote:$MESSAGE_UUID?foo=bar"))
        assertNull(parseWorkspaceQuoteUrn("urn:quote:$MESSAGE_UUID?text=a&other=b"))
        assertNull(parseWorkspaceQuoteUrn("urn:quote:$MESSAGE_UUID?text=%GG"))
        assertNull(parseWorkspaceQuoteUrn("urn:quote:$MESSAGE_UUID?text=%FF"))
        assertNull(parseWorkspaceQuoteUrn("urn:quote:not-a-uuid"))
    }

    @Test
    fun `standalone forward references become quote segments but fenced code does not`() {
        val parsed = parseForwardMarkdown(
            """
            Before

            [Alice \[Admin\]](urn:quote:$MESSAGE_UUID)

            ```
            [Not a quote](urn:quote:$OTHER_UUID)
            ```
            """.trimIndent(),
        )

        assertEquals(3, parsed.size)
        assertEquals(
            WorkspaceQuoteReference(
                authorLabel = "Alice [Admin]",
                messageUuid = MESSAGE_UUID,
            ),
            (parsed[1] as ForwardMarkdownSegment.Quote).reference,
        )
        assertTrue((parsed[0] as ForwardMarkdownSegment.Text).markdown.contains("Before"))
        assertTrue(
            (parsed[2] as ForwardMarkdownSegment.Text)
                .markdown
                .contains("urn:quote:$OTHER_UUID"),
        )
    }

    @Test
    fun `stream picker excludes archived and direct streams`() {
        val channel = stream(uuid = STREAM_UUID, name = "Zulu")
        val privateChannel = stream(
            uuid = OTHER_UUID,
            name = "Alpha",
            isPrivate = true,
        )
        val direct = stream(
            uuid = DIRECT_STREAM_UUID,
            name = "Direct",
            isPrivate = true,
            directUserUuid = AUTHOR_UUID,
        )
        val archived = stream(
            uuid = ARCHIVED_STREAM_UUID,
            name = "Archived",
            isArchived = true,
        )

        assertEquals(
            listOf(privateChannel, channel),
            forwardableStreams(listOf(channel, direct, archived, privateChannel)),
        )
    }

    @Test
    fun `default topic is first and direct destination accepts stream metadata fallback`() {
        val regularTopic = topic(TOPIC_UUID, "Alpha")
        val defaultTopic = topic(DEFAULT_TOPIC_UUID, "General", isDefault = true)
        val direct = stream(
            uuid = DIRECT_STREAM_UUID,
            name = "Direct",
            isPrivate = true,
            directUserUuid = AUTHOR_UUID,
            defaultTopicUuid = null,
        )

        assertEquals(
            listOf(defaultTopic, regularTopic),
            forwardTopics(STREAM_UUID, listOf(regularTopic, defaultTopic)),
        )
        assertEquals(
            ForwardDestination(DIRECT_STREAM_UUID, DEFAULT_TOPIC_UUID),
            existingDirectForwardDestination(
                userUuid = AUTHOR_UUID,
                streams = listOf(direct),
                topicsByStream = mapOf(
                    DIRECT_STREAM_UUID to listOf(
                        defaultTopic.copy(streamUuid = DIRECT_STREAM_UUID),
                    ),
                ),
            ),
        )
        assertEquals(
            ForwardDestination(DIRECT_STREAM_UUID, DEFAULT_TOPIC_UUID),
            existingDirectForwardDestination(
                userUuid = AUTHOR_UUID,
                streams = listOf(direct.copy(defaultTopicUuid = DEFAULT_TOPIC_UUID)),
                topicsByStream = emptyMap(),
            ),
        )
    }

    @Test
    fun `current topic wins for the current stream and duplicate labels remain distinct`() {
        val first = topic(TOPIC_UUID, "Same")
        val current = topic(DEFAULT_TOPIC_UUID, "Same")
        val topics = listOf(first, current)

        assertEquals(
            DEFAULT_TOPIC_UUID,
            topics.preferredForwardTopicUuid(
                selectedStreamUuid = STREAM_UUID,
                currentStreamUuid = STREAM_UUID,
                currentTopicUuid = DEFAULT_TOPIC_UUID,
            ),
        )
        assertEquals(
            TOPIC_UUID,
            topics.preferredForwardTopicUuid(
                selectedStreamUuid = STREAM_UUID,
                currentStreamUuid = OTHER_UUID,
                currentTopicUuid = DEFAULT_TOPIC_UUID,
            ),
        )
        assertEquals(
            "Same · ${TOPIC_UUID.take(8)}",
            forwardTopicLabel(first, topics),
        )
        assertEquals(
            "Same · ${DEFAULT_TOPIC_UUID.take(8)}",
            forwardTopicLabel(current, topics),
        )
    }

    @Test
    fun `current user is omitted from direct picker`() {
        val current = user(AUTHOR_UUID, "me", "Me")
        val other = user(OTHER_UUID, "other", "Other")

        assertEquals(
            listOf(other),
            forwardUsers(listOf(current, other), AUTHOR_UUID),
        )
    }

    @Test
    fun `ambiguous forward failures require verification`() {
        assertEquals(
            ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus.UNCERTAIN,
            forwardFailureStatus(
                ApiError("timeout", "TIMEOUT", ApiErrorKind.TIMEOUT),
            ),
        )
        assertEquals(
            ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus.FAILED,
            forwardFailureStatus(
                ApiError("invalid", "400", ApiErrorKind.VALIDATION),
            ),
        )
    }

    @Test
    fun `forward confirmation must be exact and verification must be unique`() {
        val content = buildWorkspaceForwardMarkdown(message()).orEmpty()
        val attempt = ForwardDeliveryAttempt(
            ownerKey = "owner",
            destination = ForwardDestination(STREAM_UUID, TOPIC_UUID),
            content = content,
            knownMatchingMessageUuids = setOf(OTHER_UUID),
        )
        val confirmation = message().copy(
            isOwn = true,
            payload = MessageResponsePayload("markdown", content),
        )

        assertTrue(isExpectedForwardConfirmation(attempt, confirmation))
        assertFalse(
            isExpectedForwardConfirmation(
                attempt,
                confirmation.copy(topicUuid = DEFAULT_TOPIC_UUID),
            ),
        )
        assertEquals(
            confirmation,
            uniqueForwardVerificationMatch(attempt, listOf(confirmation)),
        )
        assertNull(
            uniqueForwardVerificationMatch(
                attempt,
                listOf(
                    confirmation,
                    confirmation.copy(uuid = DIRECT_STREAM_UUID),
                ),
            ),
        )
        assertNull(
            uniqueForwardVerificationMatch(
                attempt.copy(
                    knownMatchingMessageUuids = setOf(MESSAGE_UUID),
                ),
                listOf(confirmation),
            ),
        )
    }

    @Test
    fun `post request fault matrix never blindly retries an ambiguous mutation`() {
        val content = buildWorkspaceForwardMarkdown(message()).orEmpty()
        val attempt = ForwardDeliveryAttempt(
            ownerKey = "owner",
            destination = ForwardDestination(STREAM_UUID, TOPIC_UUID),
            content = content,
            knownMatchingMessageUuids = emptySet(),
        )
        val confirmation = message().copy(
            isOwn = true,
            payload = MessageResponsePayload("markdown", content),
        )

        assertEquals(
            ForwardPostDecision.Completed(confirmation),
            decideForwardPostResult(attempt, ApiResult.Success(confirmation)),
        )
        assertTrue(
            decideForwardPostResult(
                attempt,
                ApiResult.Success(confirmation.copy(topicUuid = DEFAULT_TOPIC_UUID)),
            ) is ForwardPostDecision.Verify,
        )
        listOf(
            ApiErrorKind.TIMEOUT,
            ApiErrorKind.NETWORK,
            ApiErrorKind.SERVER,
            ApiErrorKind.MALFORMED_RESPONSE,
            ApiErrorKind.UNKNOWN,
        ).forEach { kind ->
            assertTrue(
                "Expected $kind to require verification",
                decideForwardPostResult(
                    attempt,
                    ApiResult.Error(ApiError(kind.name, kind.name, kind)),
                ) is ForwardPostDecision.Verify,
            )
        }
        assertTrue(
            decideForwardPostResult(
                attempt,
                ApiResult.Error(
                    ApiError("invalid", "400", ApiErrorKind.VALIDATION),
                ),
            ) is ForwardPostDecision.Failed,
        )
        assertTrue(unexpectedForwardFailureNeedsVerification(true, attempt))
        assertFalse(unexpectedForwardFailureNeedsVerification(false, attempt))
        assertFalse(unexpectedForwardFailureNeedsVerification(true, null))
    }

    @Test
    fun `accepted then timed out delivery converges through one unique new match`() {
        val content = buildWorkspaceForwardMarkdown(message()).orEmpty()
        val existingUuid = "99999999-9999-4999-8999-999999999999"
        val attempt = ForwardDeliveryAttempt(
            ownerKey = "owner",
            destination = ForwardDestination(STREAM_UUID, TOPIC_UUID),
            content = content,
            knownMatchingMessageUuids = setOf(existingUuid),
        )
        val oldIdentical = message().copy(
            uuid = existingUuid,
            isOwn = true,
            payload = MessageResponsePayload("markdown", content),
        )
        val accepted = oldIdentical.copy(uuid = MESSAGE_UUID)
        val timeoutDecision = decideForwardPostResult(
            attempt,
            ApiResult.Error(
                ApiError("timeout", "TIMEOUT", ApiErrorKind.TIMEOUT),
            ),
        )

        assertTrue(timeoutDecision is ForwardPostDecision.Verify)
        assertEquals(
            accepted,
            uniqueForwardVerificationMatch(
                attempt,
                listOf(oldIdentical, accepted),
            ),
        )
        assertNull(
            uniqueForwardVerificationMatch(
                attempt,
                listOf(
                    oldIdentical,
                    accepted,
                    accepted.copy(uuid = DIRECT_STREAM_UUID),
                ),
            ),
        )
    }

    private fun message() = MessageResponse(
        uuid = MESSAGE_UUID,
        updatedAt = "2026-07-30T00:00:00Z",
        createdAt = "2026-07-30T00:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = AUTHOR_UUID,
        authorUuid = AUTHOR_UUID,
        payload = MessageResponsePayload("markdown", "Forward me"),
        isOwn = false,
        reactions = emptyMap(),
    )

    private fun stream(
        uuid: String,
        name: String,
        isPrivate: Boolean = false,
        isArchived: Boolean = false,
        directUserUuid: String? = null,
        defaultTopicUuid: String? = DEFAULT_TOPIC_UUID,
    ) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = "2026-07-30T00:00:00Z",
        name = name,
        isPrivate = isPrivate,
        isArchived = isArchived,
        directUserUuid = directUserUuid,
        defaultTopicUuid = defaultTopicUuid,
    )

    private fun topic(
        uuid: String,
        name: String,
        isDefault: Boolean = false,
    ) = TopicsResponseData(
        uuid = uuid,
        name = name,
        streamUuid = STREAM_UUID,
        updatedAt = "2026-07-30T00:00:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = isDefault,
    )

    private fun user(
        uuid: String,
        username: String,
        firstName: String,
    ) = UserResponseData(
        email = "$username@example.test",
        firstName = firstName,
        username = username,
        uuid = uuid,
        status = "active",
        avatar = "",
    )

    private companion object {
        const val MESSAGE_UUID = "11111111-1111-4111-8111-111111111111"
        const val OTHER_UUID = "22222222-2222-4222-8222-222222222222"
        const val STREAM_UUID = "33333333-3333-4333-8333-333333333333"
        const val TOPIC_UUID = "44444444-4444-4444-8444-444444444444"
        const val AUTHOR_UUID = "55555555-5555-4555-8555-555555555555"
        const val DEFAULT_TOPIC_UUID = "66666666-6666-4666-8666-666666666666"
        const val DIRECT_STREAM_UUID = "77777777-7777-4777-8777-777777777777"
        const val ARCHIVED_STREAM_UUID = "88888888-8888-4888-8888-888888888888"
    }
}
